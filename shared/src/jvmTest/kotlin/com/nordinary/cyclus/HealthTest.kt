package com.nordinary.cyclus

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.health.HealthCategory
import com.nordinary.cyclus.health.HealthLink
import com.nordinary.cyclus.health.HealthMerge
import com.nordinary.cyclus.health.HealthPlanner
import com.nordinary.cyclus.health.HealthSample
import com.nordinary.cyclus.health.HealthType
import com.nordinary.cyclus.health.LinkState
import com.nordinary.cyclus.health.OwnRecord
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import com.nordinary.cyclus.store.CyclusRepository
import com.nordinary.cyclus.store.db.CyclusDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealthTest {
    private val today = LocalDate(2026, 11, 22).toEpochDays().toInt()
    private val fitbit = "com.fitbit.FitbitMobile"
    private val other = "org.example.tracker"
    private fun s(id: String, t: HealthType, day: Int, v: String, origin: String = fitbit, at: Long = 1) = HealthSample(id, t, day, v, origin, at)

    @Test fun userEntryWinsThenTrustedSourceAndFlowNeverCountsTwice() {
        val user = mapOf(today - 1 to DayLog(flow = Flow.NONE), today to DayLog(flow = Flow.HEAVY, mood = 3))
        val samples = listOf(
            s("a", HealthType.FLOW, today, "light"), // user has heavy → not counted
            s("b", HealthType.FLOW, today - 1, "medium"), // user said none → hidden
            s("c", HealthType.FLOW, today - 2, "light", other, at = 9), s("d", HealthType.FLOW, today - 2, "heavy", fitbit, at = 1),
            s("e", HealthType.SPOTTING, today - 2, "1"),
            s("f", HealthType.BBT, today, "36.6"),
            s("g", HealthType.LH, today - 3, "pos"),
        )
        val eff = HealthMerge.effectiveLogs(user, samples)
        assertEquals(Flow.HEAVY, eff[today]!!.flow); assertEquals(3, eff[today]!!.mood); assertEquals(36.6, eff[today]!!.bbt)
        assertEquals(Flow.NONE, eff[today - 1]!!.flow)
        assertEquals(Flow.HEAVY, eff[today - 2]!!.flow) // trusted source beats a newer value from another app; spotting not added
        assertEquals(LhResult.POSITIVE, eff[today - 3]!!.lh)
        val detail = HealthMerge.dayDetail(today - 2, null, samples)
        assertEquals(setOf("d"), detail.filter { it.used }.map { v -> samples.first { it.value == v.value && it.origin == v.origin && it.type == v.type }.recordId }.toSet())
        assertTrue(detail.single { it.type == HealthType.SPOTTING }.used.not())
        // user data untouched when there are no samples
        assertEquals(user, HealthMerge.effectiveLogs(user, emptyList()))
    }

    @Test fun externalPeriodFillsBleedingSoTheEngineFindsTheStart() {
        val start = today - 10
        val eff = HealthMerge.effectiveLogs(emptyMap(), listOf(s("p", HealthType.PERIOD, start, (start + 4).toString())))
        assertEquals((start..start + 4).toSet(), eff.filterValues { it.isBleed }.keys)
        assertTrue(Flow.UNSPECIFIED == eff[start]!!.flow)
        assertEquals(listOf(start), CycleData(eff, emptyMap(), today).cycleStarts)
    }

    @Test fun plannerWritesOnlyEnabledUserDataWithRisingVersions() {
        val d = SampleScenarios.generate(SampleScenario.DAY12, today)
        assertTrue(HealthPlanner.desired(d.logs, emptyMap(), emptySet(), today).isEmpty()) // all off by default
        val desired = HealthPlanner.desired(d.logs, emptyMap(), setOf(HealthCategory.BLEEDING), today)
        assertTrue(desired.all { it.type == HealthType.FLOW || it.type == HealthType.PERIOD })
        assertEquals(CycleData(d.logs, emptyMap(), today).cycleStarts.size, desired.count { it.type == HealthType.PERIOD })
        val first = HealthPlanner.plan(desired, emptyMap(), now = 1_000)
        assertEquals(desired.size, first.upserts.size); assertTrue(first.upserts.all { it.second == 1_000L })

        // written; then one value edited, one deleted locally, one removed in Health Connect
        val links = first.upserts.associate { (r, v) -> r.syncId to HealthLink(r.syncId, r.type, r.day, "id-${r.syncId}", v, r.hash, LinkState.OK) }.toMutableMap()
        val edited = desired.first { it.type == HealthType.FLOW }
        val deleted = desired.last { it.type == HealthType.FLOW }
        val removedInHc = desired.first { it.type == HealthType.PERIOD }
        links[removedInHc.syncId] = links.getValue(removedInHc.syncId).copy(state = LinkState.REMOVED)
        // an import with an older clock must still raise the version
        links[edited.syncId] = links.getValue(edited.syncId).copy(version = 5_000)
        val next = desired.filter { it != deleted }.map { if (it == edited) it.copy(value = "heavy") else it }
        val p = HealthPlanner.plan(next, links, now = 2_000)
        assertEquals(listOf(edited.syncId to 5_001L), p.upserts.map { it.first.syncId to it.second })
        assertEquals(listOf(deleted.syncId), p.deletes.map { it.syncId })
        assertTrue(p.forget.isEmpty()) // removed-in-HC record is still in Cyclus: awaiting review, not re-written
        assertTrue(p.upserts.none { it.first.syncId == removedInHc.syncId })
        // a failed delete stays as tombstone and is retried; it's re-written if the value came back
        links[deleted.syncId] = links.getValue(deleted.syncId).copy(state = LinkState.DELETING)
        assertEquals(listOf(deleted.syncId), HealthPlanner.plan(next, links, 3_000).deletes.filter { it.syncId == deleted.syncId }.map { it.syncId })
        assertTrue(HealthPlanner.plan(desired, links, 3_000).upserts.any { it.first.syncId == deleted.syncId })
    }

    @Test fun reinstallRecoveryOnlyIntoAnEmptyStore() {
        val own = listOf(
            OwnRecord(HealthPlanner.syncId(HealthType.FLOW, today), HealthType.FLOW, today, "medium"),
            OwnRecord(HealthPlanner.syncId(HealthType.BBT, today), HealthType.BBT, today, "36.4"),
            OwnRecord(HealthPlanner.syncId(HealthType.SEX, today - 1), HealthType.SEX, today - 1, "p"),
        )
        val back = HealthPlanner.reconstruct(emptyMap(), own)
        assertEquals(DayLog(flow = Flow.MEDIUM, bbt = 36.4), back[today])
        assertEquals(true, back[today - 1]!!.sexProtected)
        assertTrue(HealthPlanner.reconstruct(mapOf(today - 5 to DayLog(mood = 2)), own).isEmpty())
    }

    @Test fun removingAPeriodTouchesOnlyThatPeriod() {
        val bleed = DayLog(flow = Flow.MEDIUM)
        // period on days 0–3 (with a one-day gap), next period starts on day 12
        val user = mapOf(today to bleed, today + 1 to bleed, today + 3 to bleed, today + 12 to bleed, today + 13 to bleed)
        assertEquals(listOf(today, today + 1, today + 3), HealthPlanner.removedPeriodDays(user, today))
        assertEquals(listOf(today + 20), HealthPlanner.removedPeriodDays(user, today + 20))
    }

    @Test fun repositoryAppliesPagesAtomicallyAndRestoresOnlyWhenEmpty(): Unit = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CyclusDatabase.Schema.create(driver)
        val repo = CyclusRepository(CyclusDatabase(driver), Dispatchers.Unconfined) { 1L }
        repo.applyHealthPage(setOf(HealthType.FLOW), listOf(s("x", HealthType.FLOW, today, "light")), emptyList(), mapOf("hc.token.flow" to "t1"))
        assertEquals(1, repo.observeHealthSamples().first().size)
        assertEquals("t1", repo.setting("hc.token.flow"))
        repo.upsertHealthLink(HealthLink("cyclus:flow:$today", HealthType.FLOW, today, "rid", 1, "light", LinkState.OK))
        repo.applyHealthPage(emptySet(), emptyList(), listOf("rid", "x"), mapOf("hc.token.flow" to "t2"))
        assertEquals(LinkState.REMOVED, repo.healthLinks().getValue("cyclus:flow:$today").state)
        assertTrue(repo.observeHealthSamples().first().isEmpty())
        assertTrue(repo.restoreFromHealth(mapOf(today to DayLog(flow = Flow.LIGHT))))
        assertFalse(repo.restoreFromHealth(mapOf(today - 1 to DayLog(flow = Flow.LIGHT))))
        assertNull(repo.log(today - 1))
        // exports never contain Health Connect data
        assertFalse(repo.exportAll("now", "test", includeSecurity = false).encode().decodeToString().contains("hc.token"))
        repo.clearHealth()
        assertTrue(repo.healthLinks().isEmpty()); assertNull(repo.setting("hc.token.flow"))
    }

    @Test fun v1DatabaseMigratesWithDataIntact() {
        val v1 = File("src/commonMain/sqldelight/databases/1.db")
        val copy = File.createTempFile("cyclus-v1", ".db").also { v1.copyTo(it, overwrite = true) }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${copy.absolutePath}")
        driver.execute(null, "INSERT INTO day_log (day, flow, updated_at) VALUES ($today, 'heavy', 1)", 0)
        CyclusDatabase.Schema.migrate(driver, 1, CyclusDatabase.Schema.version)
        val db = CyclusDatabase(driver)
        assertEquals("heavy", db.cyclusQueries.selectAllLogs().executeAsList().single().flow)
        db.cyclusQueries.upsertSample("r", "flow", today.toLong(), "light", fitbit, 1)
        assertEquals(1, db.cyclusQueries.countSamples().executeAsOne())
        driver.close(); copy.delete()
    }
}
