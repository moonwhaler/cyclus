package com.nordinary.cyclus

import com.nordinary.cyclus.core.CycleOverride
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.presenter.CalendarPresenter
import com.nordinary.cyclus.presenter.DayEntry
import com.nordinary.cyclus.presenter.DayMarker
import com.nordinary.cyclus.presenter.InsightsPresenter
import com.nordinary.cyclus.presenter.InsightsUiState
import com.nordinary.cyclus.presenter.TodayHero
import com.nordinary.cyclus.presenter.TodayPresenter
import com.nordinary.cyclus.presenter.WhyPresenter
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresenterTest {
    private val today = LocalDate(2026, 11, 22).toEpochDays().toInt()

    private fun snap(s: SampleScenario, fertTrack: Boolean = false): CycleSnapshot {
        val d = SampleScenarios.generate(s, today)
        return CycleSnapshot.compute(CycleData(d.logs, emptyMap(), today), d.mode, fertTrack, paused = d.pausedSince != null)
    }

    @Test fun calendarMonthLayoutIsMondayFirst() {
        val m = CalendarPresenter.month(snap(SampleScenario.DAY12), 2026, 11)
        // 1 Nov 2026 is a Sunday → grid starts Mon 26 Oct; Mon 30 Nov needs a sixth row
        assertEquals(LocalDate(2026, 10, 26).toEpochDays().toInt(), m.cells.first().day)
        assertEquals(42, m.cells.size)
        assertEquals(30, m.cells.count { it.inMonth })
        assertEquals(1, m.cells.count { it.today })
        // day12: period started 11 days ago, 5 bleeding days
        val bleed = m.cells.filter { it.bleed }.map { it.day }
        assertTrue((today - 11..today - 7).all { it in bleed })
        assertTrue(m.cells.none { it.day > today && !it.canMarkStart.not() })
    }

    @Test fun calendarShowsPredictedAndPrePeriodDays() {
        val s = snap(SampleScenario.DAY12)
        val p = s.prediction!!
        val dec = CalendarPresenter.month(s, 2026, 12)
        assertEquals((p.lo..p.hi).toList(), dec.cells.filter { it.predicted }.map { it.day })
        assertEquals((p.lo - 7 until p.lo).toList(), dec.cells.filter { it.prePeriod }.map { it.day })
        assertTrue(dec.cells.none { it.fertTier > 0 }) // fertility off in track mode
    }

    @Test fun calendarShowsFertilityInConceiveMode() {
        val s = snap(SampleScenario.CONCEIVE)
        val f = s.fertility!!
        val nov = CalendarPresenter.month(s, 2026, 11)
        val dec = CalendarPresenter.month(s, 2026, 12)
        val cells = nov.cells.filter { it.inMonth } + dec.cells.filter { it.inMonth }
        assertTrue(nov.fertilityLegend)
        assertTrue(cells.any { it.fertTier > 0 })
        assertEquals(listOf(f.dateOf(f.mapD)), cells.filter { it.marker == DayMarker.OVULATION }.map { it.day })
    }

    @Test fun daySheetOrdersEntriesLikePrototype() {
        val log = DayLog(
            flow = Flow.LIGHT, symptoms = setOf("Tired", "Cramps"), pain = mapOf("lowC" to 4, "head" to 0), emotions = setOf("Calm"),
            mood = 3, libido = 2, lh = LhResult.NEGATIVE, bbt = 36.5, tags = setOf("Stress"), note = "x",
        )
        val s = CycleSnapshot.compute(CycleData(mapOf(today to log), emptyMap(), today))
        val m = CalendarPresenter.daySheet(s, today)
        assertEquals(
            listOf("FlowEntry", "Symptoms", "Pain", "Feelings", "Scale", "Scale", "Lh", "Temperature", "Tags", "Note"),
            m.entries.map { it::class.simpleName },
        )
        assertEquals(listOf("Cramps", "Tired"), (m.entries[1] as DayEntry.Symptoms).keys)
        assertEquals(listOf("lowC" to 4), (m.entries[2] as DayEntry.Pain).regions)
        assertEquals("libido", (m.entries[4] as DayEntry.Scale).key)
        assertTrue(m.showSource); assertTrue(m.canEdit)
        assertTrue(!CalendarPresenter.daySheet(s, today + 1).canEdit)
    }

    @Test fun insightsMatchPrototypeFormulas() {
        val s = snap(SampleScenario.DAY12)
        val p = s.prediction!!
        val ins = assertIs<InsightsUiState.Ready>(InsightsPresenter.present(s, emptySet()))
        assertEquals(p.typLo, ins.typLo); assertEquals(p.typHi, ins.typHi)
        assertEquals(maxOf(1, com.nordinary.cyclus.core.Js.roundInt((p.hiL - p.loL) / 2)), ins.spread)
        val plens = s.starts.dropLast(1).map { s.data.periodDays(it).size }.sorted()
        assertEquals(plens[plens.size / 2], ins.periodTypical)
        assertEquals(8, ins.bars.size); assertTrue(ins.bars.last().latest)
        assertEquals(6, ins.history.size)
        assertEquals(s.starts[s.starts.size - 2], ins.history.first().start)
        assertTrue(ins.heat.rates.flatten().all { it in 0.0..1.0 })
        // planted pattern: cramps on period day 1 (rel 0) far more often than a week before (rel −7)
        val cramps = ins.heat.rates[0]
        assertTrue(cramps[7] > 0.6 && cramps[0] < 0.2, "cramps row $cramps")
        assertNull(ins.temperature)
    }

    @Test fun insightsEmptyWithoutHistory() {
        assertIs<InsightsUiState.Empty>(InsightsPresenter.present(snap(SampleScenario.EMPTY), emptySet()))
    }

    @Test fun mutedPatternsAreHiddenAndCounted() {
        val s = snap(SampleScenario.DAY12)
        val first = InsightsPresenter.patternSection(s, emptySet())
        assertTrue(first.found.isNotEmpty())
        val muted = InsightsPresenter.patternSection(s, setOf(first.found.first().id))
        assertEquals(1, muted.hiddenCount)
        assertEquals(first.found.size - 1, muted.found.size)
        assertTrue(first.found.zipWithNext().all { (a, b) -> !(a.status.key == "early" && b.status.key == "consistent") })
    }

    @Test fun conceiveBeforeOvulation() {
        val s = snap(SampleScenario.CONCEIVE)
        val st = TodayPresenter.present(s, null)
        assertIs<TodayHero.ConceiveFertile>(st.hero)
        val c = assertNotNull(st.conceive)
        assertTrue(c.bestDays.size in 1..3 && c.bestDays == c.bestDays.sorted())
        assertTrue(c.lhToday || c.lhPlanStart != null)
        assertNull(st.preWindowFrom)
        assertNotNull(WhyPresenter.present(s))
    }

    @Test fun conceiveTwoWeekWait() {
        val s = snap(SampleScenario.CONCEIVE_2WW)
        val f = s.fertility!!
        val st = TodayPresenter.present(s, null)
        val hero = assertIs<TodayHero.ConceiveAfter>(st.hero)
        assertEquals(f.todayCd - f.mapD, hero.daysAfter)
        assertEquals(maxOf(f.dateOf(f.mapD) + 14, s.prediction!!.lo), st.conceive!!.earliestTest)
        val why = WhyPresenter.present(s)!!
        assertTrue(why.occurred)
        assertEquals(s.trail().size, why.steps.size)
        assertNull(why.steps.first().changed)
    }

    @Test fun trackModeHasNoConceiveCards() {
        val st = TodayPresenter.present(snap(SampleScenario.DAY12, fertTrack = true), null)
        assertIs<TodayHero.Track>(st.hero)
        assertNull(st.conceive)
        assertNotNull(st.trackFertileWindow)
    }

    @Test fun startOverrideMakesAStart() {
        val logs = mapOf(today - 30 to DayLog(flow = Flow.MEDIUM))
        val d = CycleData(logs, mapOf(today - 3 to CycleOverride(com.nordinary.cyclus.core.OverrideKind.START)), today)
        assertEquals(listOf(today - 30, today - 3), d.cycleStarts)
    }
}

class LogGroupsTest {
    @kotlin.test.Test fun usedSectionsComeFromRecentDaysOnly() {
        val today = 20_000
        val logs = mapOf(
            today to com.nordinary.cyclus.core.DayLog(symptoms = setOf("Cramps")), // pinned: no section
            today - 3 to com.nordinary.cyclus.core.DayLog(symptoms = setOf("Acne"), energy = 2, note = " "),
            today - 400 to com.nordinary.cyclus.core.DayLog(tags = setOf("Stress")), // too long ago
        )
        kotlin.test.assertEquals(
            setOf(com.nordinary.cyclus.presenter.LogGroup.MORE_SYMPTOMS, com.nordinary.cyclus.presenter.LogGroup.ENERGY),
            com.nordinary.cyclus.presenter.LogGroup.used(logs, today),
        )
    }
}
