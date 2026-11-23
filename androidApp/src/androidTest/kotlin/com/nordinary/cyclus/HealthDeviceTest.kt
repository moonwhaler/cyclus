package com.nordinary.cyclus

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nordinary.cyclus.backup.BackupCoordinator
import com.nordinary.cyclus.backup.BackupKeyStore
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.health.HealthAvailability
import com.nordinary.cyclus.health.HealthCategory
import com.nordinary.cyclus.health.HealthKeys
import com.nordinary.cyclus.health.HealthSync
import com.nordinary.cyclus.health.HealthType
import com.nordinary.cyclus.health.LinkState
import com.nordinary.cyclus.health.OwnRecord
import com.nordinary.cyclus.store.AndroidDatabase
import com.nordinary.cyclus.store.CyclusRepository
import com.nordinary.cyclus.store.StoreHolder
import com.nordinary.cyclus.store.StoreState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Health Connect on the emulator (API 36 has it built in). Never run on a personal phone. */
@RunWith(AndroidJUnit4::class)
class HealthDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.now().toEpochDay().toInt()
    private val hc = HealthSync(ctx)

    @Before fun grant() {
        assumeTrue(hc.availability() == HealthAvailability.AVAILABLE)
        val ua = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (t in HealthType.entries) {
            ua.grantRuntimePermission(ctx.packageName, HealthPermission.getReadPermission(HealthSync.recordClass(t)))
            ua.grantRuntimePermission(ctx.packageName, HealthPermission.getWritePermission(HealthSync.recordClass(t)))
        }
        runBlocking { hc.deleteOwn(HealthType.entries) } // clean slate: only our own records
    }

    private suspend fun <T : Record> ours(c: KClass<T>): List<T> = hc.client.readRecords(
        ReadRecordsRequest(c, TimeRangeFilter.after(Instant.EPOCH)),
    ).records.filter { it.metadata.dataOrigin.packageName == ctx.packageName && it.metadata.clientRecordId?.startsWith("cyclus:") == true }

    private class Env(val holder: StoreHolder, val coord: BackupCoordinator, val keys: BackupKeyStore, val repo: CyclusRepository)

    private suspend fun env(name: String): Env {
        val keys = BackupKeyStore("cyclus_test_hc_$name")
        val coord = BackupCoordinator(ctx, File(ctx.cacheDir, "hc-backup-$name"), keys).also { it.files.ensureDir() }
        val holder = StoreHolder(ctx, coord, "hc-$name.db")
        holder.ensureOpen()
        val ready = withTimeout(20_000) { holder.state.first { it is StoreState.Ready } } as StoreState.Ready
        return Env(holder, coord, keys, ready.repo)
    }

    private fun Env.close(name: String) {
        (holder.state.value as? StoreState.Ready)?.repo?.close()
        AndroidDatabase.destroy(ctx, "hc-$name.db"); keys.destroy(); File(ctx.cacheDir, "hc-backup-$name").deleteRecursively()
    }

    // Health Connect reads need the app in the foreground (no activity here), so the read path is covered by
    // applyHealthPage (what getChanges delivers) and the manual emulator run; writes go through the real sync
    @Test fun writesOnlyEnabledCategoriesEditsWithHigherVersionAndDeletes(): Unit = runBlocking {
        val e = env("write")
        val r = e.repo
        r.setSetting(HealthKeys.CONNECTED, "1")
        r.saveLog(today - 1, DayLog(flow = Flow.MEDIUM, bbt = 36.5, mood = 3))
        r.saveLog(today, DayLog(bbt = 36.6))
        r.setSetting(HealthKeys.write(HealthCategory.TEMPERATURE), "1")
        hc.sync(e.coord, e.holder, r)
        var temps = ours(BasalBodyTemperatureRecord::class)
        assertEquals(2, temps.size) // flow not enabled → not written
        assertTrue(ours(androidx.health.connect.client.records.MenstruationFlowRecord::class).isEmpty())
        assertEquals("1", r.setting(HealthKeys.WROTE))
        val v1 = temps.first { it.metadata.clientRecordId == "cyclus:bbt:$today" }.metadata.clientRecordVersion

        r.saveLog(today, DayLog(bbt = 36.9)) // edit
        hc.sync(e.coord, e.holder, r)
        temps = ours(BasalBodyTemperatureRecord::class)
        val edited = temps.single { it.metadata.clientRecordId == "cyclus:bbt:$today" }
        assertEquals(36.9, edited.temperature.inCelsius, 0.001)
        assertTrue(edited.metadata.clientRecordVersion > v1)

        r.saveLog(today - 1, DayLog(flow = Flow.MEDIUM, mood = 3)) // temperature removed locally
        hc.sync(e.coord, e.holder, r)
        assertEquals(listOf("cyclus:bbt:$today"), ours(BasalBodyTemperatureRecord::class).map { it.metadata.clientRecordId })
        assertTrue(r.healthLinks().keys == setOf("cyclus:bbt:$today"))

        // deleted in Health Connect by someone else: marked for review, never re-written silently
        val rid = r.healthLinks().getValue("cyclus:bbt:$today").recordId!!
        r.applyHealthPage(emptySet(), emptyList(), listOf(rid), emptyMap()) // what getChanges delivers
        assertEquals(LinkState.REMOVED, r.healthLinks().getValue("cyclus:bbt:$today").state)
        hc.client.deleteRecords(BasalBodyTemperatureRecord::class, listOf(rid), emptyList())
        hc.sync(e.coord, e.holder, r)
        assertTrue(ours(BasalBodyTemperatureRecord::class).isEmpty())
        e.close("write")
    }

    @Test fun eraseDeletesWhatCyclusWroteButNotOtherRecords(): Unit = runBlocking {
        val e = env("erase")
        e.repo.setSetting(HealthKeys.CONNECTED, "1")
        e.repo.saveLog(today, DayLog(flow = Flow.LIGHT, bbt = 36.4))
        e.repo.setSetting(HealthKeys.write(HealthCategory.TEMPERATURE), "1")
        e.repo.setSetting(HealthKeys.write(HealthCategory.BLEEDING), "1")
        hc.sync(e.coord, e.holder, e.repo)
        assertTrue(ours(BasalBodyTemperatureRecord::class).isNotEmpty())
        e.holder.erase(deleteHealth = true, revokeHealth = false)
        withTimeout(20_000) { e.holder.state.first { it is StoreState.Ready && it.repo !== e.repo } }
        assertTrue(ours(BasalBodyTemperatureRecord::class).isEmpty())
        assertTrue(ours(androidx.health.connect.client.records.MenstruationFlowRecord::class).isEmpty())
        e.close("erase")
    }

    @Test fun recordsMapBothWays() {
        for (r in listOf(
            OwnRecord("cyclus:flow:$today", HealthType.FLOW, today, "heavy"),
            OwnRecord("cyclus:period:${today - 4}", HealthType.PERIOD, today - 4, today.toString()), // ongoing: ends today
            OwnRecord("cyclus:bbt:$today", HealthType.BBT, today, "36.55"),
            OwnRecord("cyclus:mucus:$today", HealthType.MUCUS, today, "eggwhite"),
            OwnRecord("cyclus:lh:$today", HealthType.LH, today, "surge"),
            OwnRecord("cyclus:sex:$today", HealthType.SEX, today, "p"),
            OwnRecord("cyclus:spotting:$today", HealthType.SPOTTING, today, "1"),
        )) {
            val (t, d, v) = HealthSync.toCyclus(HealthSync.toRecord(r, 1))!!
            assertEquals(Triple(r.type, r.day, r.value), Triple(t, d, v))
        }
    }

}
