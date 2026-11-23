package com.nordinary.cyclus

import com.nordinary.cyclus.backup.ExportFile
import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.engine.Mode
import com.nordinary.cyclus.insights.Window
import com.nordinary.cyclus.insights.pastCyclesPublic
import com.nordinary.cyclus.presenter.DoctorQuestion
import com.nordinary.cyclus.presenter.DoctorQuestions
import com.nordinary.cyclus.presenter.InsightsPresenter
import com.nordinary.cyclus.presenter.LengthVerdict
import com.nordinary.cyclus.presenter.Phase
import com.nordinary.cyclus.presenter.TodayCard
import com.nordinary.cyclus.presenter.TodayExtras
import com.nordinary.cyclus.presenter.TodayLayout
import com.nordinary.cyclus.presenter.TodayPresenter
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodayExtrasTest {
    private val today = LocalDate(2026, 11, 22).toEpochDays().toInt()

    private fun snap(s: SampleScenario, fertTrack: Boolean = false, day: Int = today): CycleSnapshot {
        val d = SampleScenarios.generate(s, day)
        return CycleSnapshot.compute(CycleData(d.logs, emptyMap(), day), d.mode, fertTrack, paused = d.pausedSince != null)
    }

    // ---- layout

    @Test fun olderLayoutsGetTheNewCardsHiddenAtTheEnd() {
        // a layout saved by 1.0 (no step 2–3 cards)
        val l = TodayLayout.from(mapOf(SettingKeys.TODAY_LAYOUT to "pre,log,test,best,lhPlan,lhToday,forecast,fertile,upcoming,patterns,!week"))
        val added = listOf(TodayCard.USUAL, TodayCard.TEMP, TodayCard.MOOD, TodayCard.DOCTOR, TodayCard.PHASE)
        assertTrue(l.hidden.containsAll(added))
        assertEquals(added, l.order.takeLast(5))
        assertEquals(TodayCard.PRE, l.shown.first()) // the person's order is kept
    }

    // ---- Zyklus vs. üblich

    @Test fun usualComparesWithTheTypicalRange() {
        val s = snap(SampleScenario.DAY12)
        val u = assertNotNull(TodayExtras.usual(s))
        val p = s.prediction!!
        assertEquals(s.cycleDay, u.cycleDay)
        assertEquals(p.typLo to p.typHi, u.typLo to u.typHi)
        assertEquals(p.lens.last(), u.lastLength)
        val expected = when { u.lastLength!! < p.typLo -> LengthVerdict.SHORTER; u.lastLength!! > p.typHi -> LengthVerdict.LONGER; else -> LengthVerdict.IN }
        assertEquals(expected, u.lastVerdict)
        assertEquals(InsightsPresenter.periodTypical(s), u.periodTypical)
        assertNull(TodayExtras.usual(snap(SampleScenario.HBC))) // paused
        assertNull(TodayExtras.usual(snap(SampleScenario.EMPTY)))
    }

    @Test fun usualNeedsTwoCyclesAndIgnoresAnImplausibleLastCycle() {
        // two starts only: one cycle
        val one = mapOf(today - 40 to DayLog(flow = Flow.MEDIUM), today - 12 to DayLog(flow = Flow.MEDIUM))
        assertNull(TodayExtras.usual(CycleSnapshot.compute(CycleData(one, emptyMap(), today))))
        // three regular cycles, then a gap of 120 days (a missed log): no "last cycle: 120 days, longer"
        val logs = listOf(today - 210, today - 182, today - 153, today - 125, today - 5).associateWith { DayLog(flow = Flow.MEDIUM) }
        val u = assertNotNull(TodayExtras.usual(CycleSnapshot.compute(CycleData(logs, emptyMap(), today))))
        assertNull(u.lastLength); assertNull(u.lastVerdict)
    }

    // ---- Stimmung & Energie

    @Test fun moodWeekNeedsThreeLoggedDaysAndTwoCyclesForTheUsual() {
        assertNull(TodayExtras.moodWeek(snap(SampleScenario.EMPTY)))
        val sparse = mapOf(today to DayLog(mood = 3), today - 1 to DayLog(energy = 2))
        assertNull(TodayExtras.moodWeek(CycleSnapshot.compute(CycleData(sparse, emptyMap(), today))))
        // without a prediction it still shows, without a usual
        val three = sparse + (today - 4 to DayLog(mood = 4))
        val w = assertNotNull(TodayExtras.moodWeek(CycleSnapshot.compute(CycleData(three, emptyMap(), today))))
        assertEquals((today - 6..today).toList(), w.days)
        assertEquals(3, w.mood.last()); assertEquals(2, w.energy[5])
        assertTrue(w.usualMood.all { it == null })
        // the sample history has mood on most days: a usual exists and equals the mean over other cycles at the same k
        val s = snap(SampleScenario.DAY12)
        val sw = assertNotNull(TodayExtras.moodWeek(s))
        val k = today - s.prediction!!.last
        val past = s.data.pastCyclesPublic(s.starts)
        val vals = past.filter { it.s != s.prediction!!.last }.mapNotNull { it.days.getOrNull(k)?.l?.mood }
        if (vals.size >= 2) assertEquals(vals.average(), sw.usualMood.last()!!, 1e-9) else assertNull(sw.usualMood.last())
        assertNull(TodayExtras.moodWeek(snap(SampleScenario.HBC)))
    }

    // ---- Phasen-Hinweis

    @Test fun phaseFollowsTheCycle() {
        assertEquals(Phase.PERIOD, TodayExtras.phase(snap(SampleScenario.PERIOD), emptySet())!!.phase)
        val day12 = assertNotNull(TodayExtras.phase(snap(SampleScenario.DAY12), emptySet()))
        assertEquals(Phase.AFTER_PERIOD, day12.phase)
        assertTrue(day12.notForBC) // fertility hidden: the card carries the line itself
        assertEquals(Phase.BEFORE_OV, TodayExtras.phase(snap(SampleScenario.DAY12, fertTrack = true), emptySet())!!.phase)
        assertEquals(Phase.PRE, TodayExtras.phase(snap(SampleScenario.EXPECTED), emptySet())!!.phase)
        assertEquals(Phase.AFTER_OV, TodayExtras.phase(snap(SampleScenario.CONCEIVE_2WW), emptySet())!!.phase)
        assertNull(TodayExtras.phase(snap(SampleScenario.HBC), emptySet()))
        assertNull(TodayExtras.phase(snap(SampleScenario.EMPTY), emptySet()))
    }

    @Test fun ovulationPhaseOnlyWithFertilityShown() {
        // walk a whole cycle: with fertility off there is never an ovulation phase; with it on there is one
        val base = snap(SampleScenario.DAY12)
        val start = base.prediction!!.last
        var sawOv = false
        for (d in start..start + 27) {
            val data = SampleScenarios.generate(SampleScenario.DAY12, today).logs.filterKeys { it <= d }
            val off = CycleSnapshot.compute(CycleData(data, emptyMap(), d), Mode.TRACK, false)
            // spec §9.3: fertility hidden → only period-relative phases, never anything about ovulation
            TodayExtras.phase(off, emptySet())?.let { assertTrue(it.phase in setOf(Phase.PERIOD, Phase.AFTER_PERIOD, Phase.PRE)); assertTrue(it.notForBC) }
            val on = CycleSnapshot.compute(CycleData(data, emptyMap(), d), Mode.TRACK, true)
            TodayExtras.phase(on, emptySet())?.let {
                assertTrue(it.phase in setOf(Phase.PERIOD, Phase.BEFORE_OV, Phase.OVULATION, Phase.AFTER_OV))
                if (it.phase == Phase.OVULATION) sawOv = true; assertFalse(it.notForBC)
            }
        }
        assertTrue(sawOv)
    }

    @Test fun phasePatternsMatchTheWindowAndSkipMuted() {
        // the sample plants pre-period patterns (headache, bloating …): find a pre-period day
        val s = snap(SampleScenario.EXPECTED)
        val p = s.prediction!!
        val pre = (p.likely - 6 until p.likely).first { it > p.last + 10 }
        val logs = SampleScenarios.generate(SampleScenario.EXPECTED, today).logs.filterKeys { it <= pre }
        val snapPre = CycleSnapshot.compute(CycleData(logs, emptyMap(), pre))
        val card = assertNotNull(TodayExtras.phase(snapPre, emptySet()))
        assertTrue(card.patterns.isNotEmpty())
        assertTrue(card.patterns.size <= 2)
        assertTrue(card.patterns.all { it.win == Window.PRE })
        val muted = card.patterns.map { it.id }.toSet()
        assertTrue(TodayExtras.phase(snapPre, muted)!!.patterns.none { it.id in muted })
    }

    // ---- availability (moved from the app; same rules for the existing cards)

    @Test fun availabilityFollowsTheModels() {
        val s = TodayPresenter.present(snap(SampleScenario.DAY12), null)
        val a = s.available()
        assertTrue(TodayCard.LOG in a && TodayCard.WEEK in a && TodayCard.DOCTOR in a)
        assertEquals(s.usual != null, TodayCard.USUAL in a)
        assertEquals(s.mood != null, TodayCard.MOOD in a)
        assertEquals(s.phase != null, TodayCard.PHASE in a)
        assertFalse(TodayCard.TEMP in a) // fertility off
        assertEquals(s.forecast != null, TodayCard.FORECAST in a)
        assertEquals(s.preWindowFrom != null, TodayCard.PRE in a)
        val empty = TodayPresenter.present(snap(SampleScenario.EMPTY), null).available()
        assertEquals(setOf(TodayCard.LOG, TodayCard.WEEK, TodayCard.DOCTOR), empty)
    }

    // ---- Fragen für die Ärztin

    @Test fun doctorQuestionOps() {
        var l = emptyList<DoctorQuestion>()
        l = DoctorQuestions.add(l, "a", "  Erste Frage  ", 1)
        l = DoctorQuestions.add(l, "b", "Zweite", 2)
        l = DoctorQuestions.add(l, "c", "   ", 3) // blank: ignored
        assertEquals(listOf("b", "a"), l.map { it.id }) // newest first
        assertEquals("Erste Frage", l.last().text)
        l = DoctorQuestions.toggle(l, "a", 4)
        assertEquals(listOf("b"), DoctorQuestions.open(l).map { it.id })
        l = DoctorQuestions.edit(l, "b", "Geändert", 5)
        assertEquals("Geändert", l.first().text); assertEquals(5, l.first().at)
        assertEquals(listOf("b"), DoctorQuestions.clearDone(l).map { it.id })
        assertEquals(listOf("a"), DoctorQuestions.edit(l, "b", "", 6).map { it.id }) // emptied = deleted
        val back = DoctorQuestions.parse(DoctorQuestions.encode(l))
        assertEquals(l, back)
        assertNull(DoctorQuestions.encode(emptyList()))
    }

    @Test fun doctorQuestionParseDropsInvalidAndCaps() {
        val raw = """[{"id":"","text":"x"},{"id":"1","text":"  "},{"id":"2","text":"ok","extra":1},{"id":"2","text":"dup"}]"""
        assertEquals(listOf(DoctorQuestion("2", "ok")), DoctorQuestions.parse(raw))
        assertEquals(emptyList(), DoctorQuestions.parse("not json"))
        val many = (1..40).map { DoctorQuestion("$it", "q$it", done = it <= 20, at = it.toLong()) }
        val capped = DoctorQuestions.parse(DoctorQuestions.encode(many))
        assertEquals(DoctorQuestions.MAX, capped.size)
        assertEquals(20, DoctorQuestions.open(capped).size) // open ones kept first
        assertEquals(DoctorQuestions.MAX_LEN, DoctorQuestions.add(emptyList(), "x", "a".repeat(500), 0).single().text.length)
    }

    @Test fun doctorQuestionsAlwaysFitAnExport() {
        // escape-heavy texts: 30 × 300 control characters encode as \u0001 (6 each), far more than 20 000 characters
        val heavy = (1..30).map { DoctorQuestion("$it", "a" + "\u0001".repeat(299), done = it % 2 == 0, at = it.toLong()) }
        val enc = assertNotNull(DoctorQuestions.encode(heavy))
        assertTrue(enc.length <= ExportFile.MAX_NOTE)
        val kept = DoctorQuestions.parse(enc)
        assertTrue(kept.isNotEmpty())
        // done ones go first (oldest first); an open one only once no done one is left
        val dropped = heavy - kept.toSet()
        assertTrue(dropped.isNotEmpty())
        if (dropped.any { !it.done }) assertTrue(kept.none { it.done })
        val droppedOpen = dropped.filter { !it.done }
        if (droppedOpen.isNotEmpty()) assertTrue(droppedOpen.maxOf { it.at } < kept.minOf { it.at })
    }

    @Test fun doctorQuestionMergeCapsAfterUnion() {
        val mine = (1..20).map { DoctorQuestion("m$it", "m", at = 1) }
        val theirs = (1..20).map { DoctorQuestion("t$it", "t", done = true, at = 1) }
        val merged = DoctorQuestions.merge(mine, theirs, 10)
        assertEquals(DoctorQuestions.MAX, merged.size)
        assertEquals(20, DoctorQuestions.open(merged).size)
    }
}
