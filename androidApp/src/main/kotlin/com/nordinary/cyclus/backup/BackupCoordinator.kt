package com.nordinary.cyclus.backup

import android.app.backup.BackupManager
import android.content.Context
import com.nordinary.cyclus.store.CyclusRepository
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single writer for everything that touches the device backup: snapshot writes, the „Nur auf dem Gerät“ toggle,
 * erase and imports run one at a time. An erase bumps [generation], so a snapshot started before it can never
 * resurrect erased data.
 */
class BackupCoordinator(
    private val context: Context,
    filesDir: File = context.filesDir,
    val keys: BackupKeyStore = BackupKeyStore(),
) {
    val files = BackupFiles(filesDir)
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "cyclus-backup") }.asCoroutineDispatcher()
    private val mutex = Mutex()
    @Volatile private var generation = 0L
    @Volatile private var snapshotAt = -1L

    val deviceOnly: Boolean get() = files.deviceOnly.exists()

    /** Runs [block] exclusively (erase / import use this). */
    suspend fun <T> exclusive(block: suspend () -> T): T = withContext(dispatcher) { mutex.withLock { block() } }

    /** Writes a fresh snapshot if data changed since the last one (called when the app goes to the background). */
    suspend fun snapshotIfChanged(repo: CyclusRepository, appLabel: String) = exclusive {
        if (deviceOnly) return@exclusive
        val changes = repo.changes
        if (changes == snapshotAt && files.snapshot.exists()) return@exclusive
        val gen = generation
        val model = repo.exportAll(java.time.Instant.now().toString(), appLabel, includeSecurity = true)
        val key = loadOrCreateKey()
        val sealed = try { CyclusFile.sealWithKey(model.encode(), key) } finally { key.fill(0) }
        if (gen != generation || deviceOnly) return@exclusive // erased or switched off meanwhile
        files.writeAtomic(files.snapshot, sealed)
        files.snapshotAbsent.delete()
        snapshotAt = changes
        BackupManager(context).dataChanged()
    }

    /** „Nur auf dem Gerät“: on removes snapshot + backup key so the next backup is empty. */
    suspend fun setDeviceOnly(on: Boolean, repo: CyclusRepository?, appLabel: String) {
        exclusive {
            if (on) {
                files.ensureDir(); files.touch(files.deviceOnly)
                files.snapshot.delete(); files.keyWrapped.delete(); keys.destroy()
                files.touch(files.snapshotAbsent)
            } else {
                files.deviceOnly.delete()
                snapshotAt = -1
            }
            BackupManager(context).dataChanged()
        }
        if (!on && repo != null) snapshotIfChanged(repo, appLabel)
    }

    /** Erase part of the crypto-shred: snapshot, keys and any pending restore. Keeps the „Nur auf dem Gerät“ choice. */
    fun eraseFilesLocked() {
        generation++
        snapshotAt = -1
        files.snapshot.delete(); files.keyWrapped.delete(); files.keyPlain.delete(); keys.destroy()
        files.restoreDir.deleteRecursively()
        files.ensureDir(); files.touch(files.snapshotAbsent)
        BackupManager(context).dataChanged()
    }

    private fun loadOrCreateKey(): ByteArray {
        if (files.keyWrapped.exists()) {
            runCatching { return keys.unwrap(files.keyWrapped.readBytes()) }
            files.keyWrapped.delete() // Keystore key gone (e.g. erased): start a new backup key; the next snapshot uses it
        }
        val key = CyclusCrypto.random(32)
        files.writeAtomic(files.keyWrapped, keys.wrap(key))
        return key
    }

    /** First start / process start: never leave a plaintext key behind; mark "no snapshot yet" on fresh installs. */
    fun startupSweep() {
        files.keyPlain.delete()
        files.ensureDir()
        if (!files.snapshot.exists() && !files.restorePending.exists()) files.touch(files.snapshotAbsent)
    }

    /** Last agent run, for the Privacy Center. */
    fun status(): BackupStatus? = runCatching {
        val t = files.status.readText()
        BackupStatus(
            at = Regex("\"at\":(\\d+)").find(t)!!.groupValues[1].toLong(),
            transport = Regex("\"transport\":\"(\\w+)\"").find(t)!!.groupValues[1],
            outcome = Regex("\"outcome\":\"([\\w-]+)\"").find(t)!!.groupValues[1],
        )
    }.getOrNull()
}

data class BackupStatus(val at: Long, val transport: String, val outcome: String)
