package com.nordinary.cyclus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nordinary.cyclus.backup.BackupCoordinator
import com.nordinary.cyclus.backup.BackupDecision
import com.nordinary.cyclus.backup.BackupKeyStore
import com.nordinary.cyclus.backup.BackupPlanner
import com.nordinary.cyclus.backup.RestoreSink
import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.store.AndroidDatabase
import com.nordinary.cyclus.store.CyclusRepository
import com.nordinary.cyclus.store.StoreHolder
import com.nordinary.cyclus.store.StoreState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Device backup, restore and erase with isolated directories, database names and Keystore aliases
 * (never the app's real data).
 */
@RunWith(AndroidJUnit4::class)
class BackupDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(ctx.cacheDir, "backup-test")
    private val keysA = BackupKeyStore("cyclus_test_backup_a")
    private val keysB = BackupKeyStore("cyclus_test_backup_b")

    @Before fun clean() { root.deleteRecursively(); listOf("bt-src.db", "bt-dst.db", "bt-erase.db").forEach { AndroidDatabase.destroy(ctx, it) } }
    @After fun cleanup() { clean(); keysA.destroy(); keysB.destroy() }

    private fun coordinator(name: String, keys: BackupKeyStore) = BackupCoordinator(ctx, File(root, name), keys).also { it.files.ensureDir() }

    private suspend fun seed(repo: CyclusRepository) {
        repo.saveLog(20_000, DayLog(flow = Flow.HEAVY, symptoms = setOf("Cramps"), note = "geheim"))
        repo.saveLog(20_001, DayLog(flow = Flow.MEDIUM))
        repo.setSetting(SettingKeys.MODE, "conceive")
        repo.setSetting(SettingKeys.LOCK_ENABLED, "1")
    }

    @Test fun plannerDecidesFromFilesOnly(): Unit = runBlocking {
        val a = coordinator("a", keysA)
        val planner = BackupPlanner(a.files, keysA::unwrap)
        // fresh: nothing to send, intentionally
        a.startupSweep()
        assertEquals("skipped-empty", planner.decide(true, false).outcome)
        assertEquals("skipped-no-e2e", planner.decide(false, false).outcome)
        // snapshot expected but missing → failure (sends nothing)
        a.files.snapshotAbsent.delete()
        assertIs<BackupDecision.Failed>(planner.decide(true, false))
        // real snapshot → send over e2e and device transfer
        val repo = AndroidDatabase.open(ctx, "bt-src.db"); seed(repo)
        a.snapshotIfChanged(repo, "test")
        val send = assertIs<BackupDecision.Send>(planner.decide(clientSideEncryption = true, deviceTransfer = false))
        assertEquals("e2e", send.transport)
        assertEquals("d2d", assertIs<BackupDecision.Send>(planner.decide(false, true)).transport)
        // corrupted snapshot → failure, never a partial send
        a.files.snapshot.writeBytes(a.files.snapshot.readBytes().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() })
        assertIs<BackupDecision.Failed>(planner.decide(true, false))
        // device only → empty, snapshot and key removed
        a.setDeviceOnly(true, null, "test")
        assertEquals("skipped-device-only", planner.decide(true, false).outcome)
        assertFalse(a.files.snapshot.exists() || a.files.keyWrapped.exists())
        repo.close()
    }

    @Test fun backupThenRestoreOnANewDevice(): Unit = runBlocking {
        // device A: data + snapshot
        val a = coordinator("a", keysA)
        val src = AndroidDatabase.open(ctx, "bt-src.db"); seed(src)
        a.snapshotIfChanged(src, "test")
        val send = assertIs<BackupDecision.Send>(BackupPlanner(a.files, keysA::unwrap).decide(true, false))
        // transport → device B (agent onRestoreFile): the key is wrapped for B straight from the stream
        val b = coordinator("b", keysB)
        val sink = RestoreSink(b.files, keysB::wrap)
        assertTrue(sink.accept("snapshot.cyclus", send.snapshot.length(), send.snapshot.inputStream()))
        assertTrue(sink.accept("key.plain", 32, send.key.inputStream()))
        sink.finish()
        assertTrue(b.files.restorePending.exists())
        assertFalse(b.files.dir.walk().any { it.name == "key.plain" }, "a plaintext key reached the disk")
        assertContentEquals(send.key, keysB.unwrap(b.files.restoreKey.readBytes()))
        // first launch on B imports into a fresh encrypted DB
        val holder = StoreHolder(ctx, b, "bt-dst.db")
        holder.ensureOpen()
        val ready = withTimeout(20_000) { holder.state.first { it is StoreState.Ready || it is StoreState.RestoreFailed } }
        val restored = assertIs<StoreState.Ready>(ready)
        assertTrue(restored.restored)
        assertEquals(src.observeLogs().first(), restored.repo.observeLogs().first())
        assertEquals("1", restored.repo.observeSettings().first()[SettingKeys.LOCK_ENABLED]) // lock config travels with the snapshot
        assertFalse(b.files.restoreDir.exists())
        assertTrue(b.files.keyWrapped.exists()) // promoted
        restored.repo.close(); src.close()
    }

    @Test fun failedRestoreDeletesOnlyTheFreshDatabase(): Unit = runBlocking {
        val b = coordinator("b", keysB)
        val sink = RestoreSink(b.files, keysB::wrap)
        val junk = ByteArray(200) { 7 }
        sink.accept("snapshot.cyclus", junk.size.toLong(), junk.inputStream())
        sink.accept("key.plain", 32, ByteArray(32).inputStream())
        sink.finish()
        val holder = StoreHolder(ctx, b, "bt-dst.db")
        holder.ensureOpen()
        assertIs<StoreState.RestoreFailed>(withTimeout(20_000) { holder.state.first { it !is StoreState.Opening } })
        assertFalse(AndroidDatabase.exists(ctx, "bt-dst.db"))
        assertTrue(b.files.restorePending.exists()) // kept for retry
        holder.discardRestore()
        assertIs<StoreState.Ready>(withTimeout(20_000) { holder.state.first { it is StoreState.Ready } }).repo.close()
        assertFalse(b.files.restoreDir.exists())
    }

    @Test fun eraseIsACryptoShredAndBeatsAPendingSnapshot(): Unit = runBlocking {
        val a = coordinator("a", keysA)
        val holder = StoreHolder(ctx, a, "bt-erase.db")
        holder.ensureOpen()
        val repo = assertIs<StoreState.Ready>(withTimeout(20_000) { holder.state.first { it is StoreState.Ready } }).repo
        seed(repo)
        a.snapshotIfChanged(repo, "test")
        assertTrue(a.files.snapshot.exists())
        val dbKey = File(ctx.noBackupFilesDir, "bt-erase.db.key").readBytes()
        holder.erase()
        val fresh = assertIs<StoreState.Ready>(withTimeout(20_000) { holder.state.first { it is StoreState.Ready && it.repo !== repo } }).repo
        assertTrue(fresh.observeLogs().first().isEmpty())
        assertTrue(fresh.observeSettings().first().isEmpty())
        assertFalse(a.files.snapshot.exists() || a.files.keyWrapped.exists())
        assertTrue(a.files.snapshotAbsent.exists())
        assertFalse(dbKey.contentEquals(File(ctx.noBackupFilesDir, "bt-erase.db.key").readBytes()), "database key was not replaced")
        // a snapshot job holding the old repository can no longer write a snapshot (its driver is closed)
        runCatching { a.snapshotIfChanged(repo, "test") }
        assertFalse(a.files.snapshot.exists())
        fresh.close()
    }
}
