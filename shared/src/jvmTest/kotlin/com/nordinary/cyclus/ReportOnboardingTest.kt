package com.nordinary.cyclus

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.engine.Tier
import com.nordinary.cyclus.presenter.LifeStage
import com.nordinary.cyclus.presenter.Onboarding
import com.nordinary.cyclus.presenter.OnboardingChoices
import com.nordinary.cyclus.presenter.ReportOptions
import com.nordinary.cyclus.presenter.ReportPresenter
import com.nordinary.cyclus.presenter.ReportSection
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import com.nordinary.cyclus.store.CyclusRepository
import com.nordinary.cyclus.store.db.CyclusDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportOnboardingTest {
    private val today = LocalDate(2026, 11, 22).toEpochDays().toInt()

    private fun snap(s: SampleScenario): CycleSnapshot {
        val d = SampleScenarios.generate(s, today)
        return CycleSnapshot.compute(CycleData(d.logs, emptyMap(), today), d.mode, paused = d.pausedSince != null)
    }

    @Test fun reportUsesPrototypeRangeAndMedians() {
        val s = snap(SampleScenario.DAY12)
        val r = assertNotNull(ReportPresenter.build(s, ReportOptions(cycles = 6), emptySet(), emptyList()))
        val starts = s.starts
        assertEquals(starts[starts.size - 7], r.from) // starts[max(0, n − 6 − 1)]
        assertEquals(6, r.cyclesUsed)
        val lens = (starts.size - 7 until starts.size - 1).map { starts[it + 1] - starts[it] }.sorted()
        assertEquals(lens[lens.size / 2], r.cycleLengths!!.median)
        assertEquals(lens.first(), r.cycleLengths!!.min); assertEquals(lens.last(), r.cycleLengths!!.max)
        assertEquals(4, r.topSymptoms.size)
        assertTrue(r.topSymptoms.zipWithNext().all { (a, b) -> a.second >= b.second })
        assertNotNull(r.painMedian) // computed, not hard-coded
        assertTrue(r.months.isNotEmpty() && r.heaviness.isNotEmpty())
        assertTrue(r.patterns.size <= 5)
        // sensitive sections are off by default
        assertTrue(r.notes.isEmpty() && r.contraception.isEmpty() && r.sexDays.isEmpty())
    }

    @Test fun reportRespectsTogglesRangeAndMute() {
        val s = snap(SampleScenario.DAY12)
        val bare = ReportPresenter.build(s, ReportOptions(cycles = 3, include = setOf(ReportSection.LENGTHS)), emptySet(), emptyList())!!
        assertEquals(3, bare.cyclesUsed)
        assertTrue(bare.months.isEmpty() && bare.topSymptoms.isEmpty() && bare.painPerCycle.isEmpty() && bare.patterns.isEmpty())
        val all = ReportPresenter.build(s, ReportOptions(cycles = 12), emptySet(), emptyList())!!
        assertEquals(8, all.cyclesUsed) // fewer cycles than requested: uses what exists
        val first = all.patterns.first().id
        assertFalse(ReportPresenter.build(s, ReportOptions(cycles = 12), setOf(first), emptyList())!!.patterns.any { it.id == first })
    }

    @Test fun reportNeedsAFinishedCycle() {
        assertNull(ReportPresenter.build(snap(SampleScenario.EMPTY), ReportOptions(), emptySet(), emptyList()))
        val one = CycleSnapshot.compute(CycleData(mapOf(today - 3 to DayLog(flow = Flow.MEDIUM)), emptyMap(), today))
        assertFalse(ReportPresenter.available(one))
        assertNull(ReportPresenter.build(one, ReportOptions(), emptySet(), emptyList()))
    }

    private fun repo(): CyclusRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CyclusDatabase.Schema.create(driver)
        return CyclusRepository(CyclusDatabase(driver), Dispatchers.Unconfined) { 1L }
    }

    @Test fun onboardingSeedsOnlyWhatWasStated(): Unit = runBlocking {
        assertEquals(listOf(today - 5), Onboarding.seedDays(OnboardingChoices(lastPeriodStart = today - 5), today))
        assertEquals((today - 5..today).toList(), Onboarding.seedDays(OnboardingChoices(today - 5, stillBleeding = true), today))
        assertEquals((today - 20..today - 17).toList(), Onboarding.seedDays(OnboardingChoices(today - 20, durationDays = 4), today))
        assertEquals(10, Onboarding.seedDays(OnboardingChoices(today - 30, stillBleeding = true), today).size)
        assertTrue(Onboarding.seedDays(OnboardingChoices(today + 1), today).isEmpty())

        val r = repo()
        Onboarding.apply(r, OnboardingChoices(today - 12, durationDays = 5, conceive = true, stage = LifeStage.POSTPARTUM), today) { "ep1" }
        val logs = r.observeLogs().first()
        assertEquals((today - 12..today - 8).toSet(), logs.keys)
        assertTrue(logs.values.all { it.flow == Flow.UNSPECIFIED })
        val s = CycleSnapshot.compute(CycleData(logs, emptyMap(), today))
        assertEquals(listOf(today - 12), s.starts)
        assertEquals(Tier.ROUGH, s.prediction!!.tier)
        val settings = r.observeSettings().first()
        assertEquals("conceive", settings[SettingKeys.MODE]); assertEquals("1", settings[Onboarding.DONE])
        assertEquals(1, r.observeEpisodes().first().size)
        assertFalse(Onboarding.shouldShow(logs, emptyMap(), emptyList(), settings))
    }

    @Test fun conceiveExcludesPillAndPregnancy(): Unit = runBlocking {
        val r = repo()
        Onboarding.apply(r, OnboardingChoices(conceive = true, stage = LifeStage.HBC), today) { "e" }
        assertNull(r.observeSettings().first()[SettingKeys.MODE])
        assertTrue(Onboarding.shouldShow(emptyMap(), emptyMap(), emptyList(), emptyMap()))
        assertFalse(Onboarding.shouldShow(emptyMap(), emptyMap(), emptyList(), mapOf(SettingKeys.MUTED to "x")))
    }
}
