package com.nordinary.cyclus

import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.presenter.NotificationPlanner
import com.nordinary.cyclus.presenter.NotifyItem
import com.nordinary.cyclus.presenter.NotifyKeys
import com.nordinary.cyclus.presenter.NotifyKind
import com.nordinary.cyclus.presenter.NotifyPlan
import com.nordinary.cyclus.presenter.NotifyPrefs
import com.nordinary.cyclus.presenter.NotifySchedule
import com.nordinary.cyclus.presenter.WidgetLine
import com.nordinary.cyclus.presenter.WidgetPresenter
import com.nordinary.cyclus.presenter.WidgetSnapshot
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class NotifyWidgetTest {
    private val today = LocalDate(2026, 11, 22).toEpochDays().toInt()
    private val berlin = TimeZone.of("Europe/Berlin")

    private fun snap(s: SampleScenario): CycleSnapshot {
        val d = SampleScenarios.generate(s, today)
        return CycleSnapshot.compute(CycleData(d.logs, emptyMap(), today), d.mode, paused = d.pausedSince != null)
    }

    private fun kinds(p: NotifyPlan) = p.items.map { NotifyKind.of(it.kind) }.toSet()

    @Test fun nothingWithoutConsentAndOnlyPeriodSoonByDefault() {
        val s = snap(SampleScenario.DAY12)
        assertTrue(NotificationPlanner.plan(s, NotifyPrefs(), null).items.isEmpty())
        val prefs = NotifyPrefs.from(mapOf(NotifyKeys.CONSENT to "1"))
        val plan = NotificationPlanner.plan(s, prefs, null)
        assertEquals(setOf(NotifyKind.PERIOD_SOON), kinds(plan))
        assertEquals(s.prediction!!.lo - 2, plan.items.single().day)
        assertTrue(plan.discreet)
        // round trip of the side file
        assertEquals(plan, NotifyPlan.decode(plan.encode()))
        assertEquals(NotifyPlan.EMPTY, NotifyPlan.decode("{broken"))
    }

    @Test fun conceiveKindsOnlyInConceiveAndPausedKeepsOnlyRoutine() {
        val all = NotifyKind.entries.associate { NotifyKeys.kind(it) to "1" } + (NotifyKeys.CONSENT to "1")
        val prefs = NotifyPrefs.from(all)
        val track = kinds(NotificationPlanner.plan(snap(SampleScenario.DAY12), prefs, today - 3))
        assertTrue(NotifyKind.LH_TEST !in track && NotifyKind.WINDOW_OPENS !in track && NotifyKind.BBT_MORNING !in track)
        assertTrue(NotifyKind.PERIOD_SOON in track && NotifyKind.LATER in track && NotifyKind.DAILY_LOG in track && NotifyKind.BACKUP in track)
        val conceive = kinds(NotificationPlanner.plan(snap(SampleScenario.CONCEIVE), prefs, null))
        assertTrue(NotifyKind.BBT_MORNING in conceive)
        val paused = kinds(NotificationPlanner.plan(snap(SampleScenario.HBC), prefs, null))
        assertEquals(setOf(NotifyKind.DAILY_LOG, NotifyKind.BACKUP), paused)
    }

    @Test fun backupMonthlyFromLastExport() {
        val prefs = NotifyPrefs.from(mapOf(NotifyKeys.CONSENT to "1", NotifyKeys.kind(NotifyKind.BACKUP) to "1", NotifyKeys.kind(NotifyKind.PERIOD_SOON) to "0"))
        val item = NotificationPlanner.plan(snap(SampleScenario.DAY12), prefs, today - 40).items.single()
        assertEquals(today - 10, item.day); assertEquals(30, item.every)
        val next = NotifySchedule.nextOf(item, NotifySchedule.instantOf(today, 0, berlin), berlin)!!
        assertEquals(today + 20, next.toLocalDateTime(berlin).date.toEpochDays().toInt())
    }

    @Test fun scheduleMathOneShotsDailyMidnightAndDst() {
        val plan = NotifyPlan(listOf(NotifyItem("periodSoon", today + 3, 9 * 60), NotifyItem("daily", today - 5, 20 * 60, every = 1)))
        val morning = LocalDateTime(2026, 11, 22, 8, 0).toInstant(berlin)
        assertEquals(LocalDateTime(2026, 11, 22, 20, 0).toInstant(berlin), NotifySchedule.nextFire(plan, morning, berlin, midnight = false))
        val evening = LocalDateTime(2026, 11, 22, 21, 0).toInstant(berlin)
        assertEquals(LocalDateTime(2026, 11, 23, 0, 0).toInstant(berlin), NotifySchedule.nextFire(plan, evening, berlin, midnight = true))
        assertEquals(LocalDateTime(2026, 11, 23, 20, 0).toInstant(berlin), NotifySchedule.nextFire(plan, evening, berlin, midnight = false))
        // a daily rule with an end stops
        val lh = NotifyItem("lh", today, 8 * 60, every = 1, until = today + 1)
        assertNull(NotifySchedule.nextOf(lh, LocalDateTime(2026, 11, 23, 9, 0).toInstant(berlin), berlin))
        // spring-forward gap: 02:30 doesn't exist on 2027-03-28 in Berlin, fires at 03:30 local
        val gapDay = LocalDate(2027, 3, 28).toEpochDays().toInt()
        val t = NotifySchedule.instantOf(gapDay, 2 * 60 + 30, berlin)
        assertEquals(3, t.toLocalDateTime(berlin).hour)
        // same wall clock in another zone after travelling
        val ny = TimeZone.of("America/New_York")
        assertEquals(LocalDateTime(2026, 11, 22, 20, 0).toInstant(ny), NotifySchedule.nextFire(plan, LocalDateTime(2026, 11, 22, 8, 0).toInstant(ny), ny, false))
    }

    @Test fun dueDedupesAndDropsStale() {
        val plan = NotifyPlan(listOf(NotifyItem("daily", today - 5, 20 * 60, every = 1), NotifyItem("periodSoon", today, 9 * 60)))
        val at2005 = LocalDateTime(2026, 11, 22, 20, 5).toInstant(berlin)
        assertEquals(setOf(NotifyKind.DAILY_LOG, NotifyKind.PERIOD_SOON), NotifySchedule.due(plan, null, at2005, berlin).toSet())
        // already fired at 20:01 → nothing new
        assertTrue(NotifySchedule.due(plan, at2005 - 4.minutes, at2005, berlin).isEmpty())
        // phone was off for two days: the 09:00 reminder from two days ago is not shown late
        val old = NotifyPlan(listOf(NotifyItem("periodSoon", today - 2, 9 * 60)))
        assertTrue(NotifySchedule.due(old, null, at2005, berlin).isEmpty())
        assertTrue(NotifySchedule.due(plan, null, at2005 - 24.hours + 30.minutes, berlin).isNotEmpty())
    }

    @Test fun widgetRecomputesFromToday() {
        val s = snap(SampleScenario.DAY12)
        val w = assertNotNull(WidgetSnapshot.from(s))
        assertEquals(w, WidgetSnapshot.decode(w.encode()))
        val v = WidgetPresenter.view(w, today)!!
        assertEquals(s.cycleDay, v.cycleDay)
        assertEquals(WidgetLine.In(w.lo - today, w.hi - today), v.line)
        // the next day without the app: cycle day moves on
        assertEquals(s.cycleDay!! + 1, WidgetPresenter.view(w, today + 1)!!.cycleDay)
        assertEquals(WidgetLine.Window, WidgetPresenter.view(w, w.lo)!!.line)
        assertEquals(WidgetLine.Later, WidgetPresenter.view(w, w.hi + 1)!!.line)
        val period = WidgetSnapshot.from(snap(SampleScenario.PERIOD))!!
        assertTrue(WidgetPresenter.view(period, today)!!.line is WidgetLine.InPeriod)
    }
}
