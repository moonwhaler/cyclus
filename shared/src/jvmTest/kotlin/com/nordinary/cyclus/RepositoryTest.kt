package com.nordinary.cyclus

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.nordinary.cyclus.core.CycleOverride
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Episode
import com.nordinary.cyclus.core.EpisodeKind
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.core.Mucus
import com.nordinary.cyclus.core.OverrideKind
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import com.nordinary.cyclus.store.CyclusRepository
import com.nordinary.cyclus.store.db.CyclusDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryTest {
    private fun repo(): CyclusRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CyclusDatabase.Schema.create(driver)
        return CyclusRepository(CyclusDatabase(driver), Dispatchers.Unconfined) { 1_000L }
    }

    private val full = DayLog(
        flow = Flow.UNSPECIFIED, symptoms = setOf("Cramps", "Breast tenderness"), symptomSeverity = mapOf("Cramps" to 3),
        pain = mapOf("lowC" to 7, "head" to 0), emotions = setOf("Calm", "Sad"), tags = setOf("Stress", "user-tag-uuid"),
        mood = 2, energy = 5, sleep = 1, libido = 4, mucus = Mucus.EGGWHITE, lh = LhResult.SURGE, bbt = 36.47,
        sex = true, sexProtected = false, note = "Notiz mit Umlauten: äöü ß", nothingElse = true,
    )

    @Test fun roundTripsEveryField(): Unit = runBlocking {
        val r = repo()
        r.saveLog(20_000, full)
        r.saveLog(20_001, DayLog(flow = Flow.SPOTTING))
        val logs = r.observeLogs().first()
        assertEquals(full, logs[20_000])
        assertEquals(DayLog(flow = Flow.SPOTTING), logs[20_001])
    }

    @Test fun overwritingReplacesChildren(): Unit = runBlocking {
        val r = repo()
        r.saveLog(20_000, full)
        val smaller = full.copy(symptoms = setOf("Headache"), symptomSeverity = emptyMap(), pain = emptyMap(), emotions = emptySet(), tags = emptySet())
        r.saveLog(20_000, smaller)
        assertEquals(smaller, r.log(20_000))
    }

    @Test fun emptyLogIsDeleted(): Unit = runBlocking {
        val r = repo()
        r.saveLog(20_000, full)
        r.saveLog(20_000, DayLog())
        assertNull(r.log(20_000))
        assertTrue(r.observeLogs().first().isEmpty())
    }

    @Test fun overridesEpisodesSettingsAndErase(): Unit = runBlocking {
        val r = repo()
        r.setOverride(20_000, CycleOverride(OverrideKind.START))
        r.setOverride(20_010, CycleOverride(OverrideKind.EXCLUDE, "illness"))
        r.saveEpisode(Episode("e1", EpisodeKind.HORMONAL_CONTRACEPTION, 19_900, null))
        r.setSetting("mode", "conceive")
        assertEquals(
            mapOf(20_000 to CycleOverride(OverrideKind.START), 20_010 to CycleOverride(OverrideKind.EXCLUDE, "illness")),
            r.observeOverrides().first(),
        )
        assertEquals(listOf(Episode("e1", EpisodeKind.HORMONAL_CONTRACEPTION, 19_900, null)), r.observeEpisodes().first())
        assertEquals(mapOf("mode" to "conceive"), r.observeSettings().first())
        r.setOverride(20_000, null)
        assertEquals(setOf(20_010), r.observeOverrides().first().keys)
        r.saveLog(20_000, full)
        r.eraseAll()
        assertTrue(r.observeLogs().first().isEmpty())
        assertTrue(r.observeOverrides().first().isEmpty())
        assertTrue(r.observeEpisodes().first().isEmpty())
        assertTrue(r.observeSettings().first().isEmpty())
    }

    @Test fun sampleScenarioSurvivesStorage(): Unit = runBlocking {
        val r = repo()
        val sample = SampleScenarios.generate(SampleScenario.CONCEIVE_2WW, 20_779)
        r.replaceAll(sample.logs)
        assertEquals(sample.logs.filterValues { !it.isEmpty }, r.observeLogs().first())
    }
}
