package com.nordinary.cyclus

import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.core.Catalog
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.presenter.CalendarPresenter
import com.nordinary.cyclus.presenter.NotifyKeys
import com.nordinary.cyclus.presenter.NotifyKind
import com.nordinary.cyclus.presenter.QuickPicks
import com.nordinary.cyclus.presenter.TodayCard
import com.nordinary.cyclus.presenter.TodayLayout
import com.nordinary.cyclus.presenter.Upcoming
import com.nordinary.cyclus.presenter.UpcomingKind
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodayLayoutTest {
    private val today = LocalDate(2026, 11, 22).toEpochDays().toInt()

    private fun snap(s: SampleScenario, fertTrack: Boolean = false, day: Int = today): CycleSnapshot {
        val d = SampleScenarios.generate(s, day)
        return CycleSnapshot.compute(CycleData(d.logs, emptyMap(), day), d.mode, fertTrack, paused = d.pausedSince != null)
    }

    // ---- layout

    @Test fun defaultIsTodaysSetPlusUpcomingWithWeekInTheLibrary() {
        val l = TodayLayout.from(emptyMap())
        assertEquals(TodayLayout.DEFAULT, l)
        assertTrue(TodayCard.UPCOMING in l.shown)
        assertEquals(setOf(TodayCard.WEEK, TodayCard.USUAL, TodayCard.TEMP, TodayCard.MOOD, TodayCard.DOCTOR, TodayCard.PHASE), l.hidden)
        assertNull(l.layoutValue())
        assertFalse(l.ringCompact)
    }

    @Test fun roundTripKeepsAnEmptyHiddenSet() {
        // showing every library card empties the hidden set: it must not fall back to the default (library hidden)
        val shown = TodayLayout.DEFAULT.hidden.fold(TodayLayout.DEFAULT) { l, c -> l.show(c) }
        val back = TodayLayout.from(mapOf(SettingKeys.TODAY_LAYOUT to shown.layoutValue()!!))
        assertEquals(emptySet(), back.hidden)
        assertEquals(TodayLayout.DEFAULT.hidden.last(), back.shown.last())
    }

    @Test fun unknownKeysDroppedAndNewCardsGetTheirDefaultPlace() {
        // an older file without UPCOMING/PATTERNS/WEEK and with a card from the future
        val l = TodayLayout.from(mapOf(SettingKeys.TODAY_LAYOUT to "pre,log,!forecast,futureCard,log"))
        // the stored order is kept (PRE before LOG); cards added later are slotted in
        assertTrue(l.order.indexOf(TodayCard.PRE) < l.order.indexOf(TodayCard.LOG))
        assertTrue(TodayCard.FORECAST in l.hidden)
        assertEquals(TodayCard.entries.toSet(), l.order.toSet())
        assertEquals(l.order.size, l.order.distinct().size)
        // WEEK keeps its default visibility (hidden); UPCOMING appears (default on)
        assertTrue(TodayCard.WEEK in l.hidden)
        assertTrue(TodayCard.UPCOMING in l.shown)
        // a new card sits after its default predecessor
        assertEquals(l.order.indexOf(TodayCard.PRE) + 1, l.order.indexOf(TodayCard.UPCOMING))
    }

    @Test fun moveHideShowReset() {
        var l = TodayLayout.DEFAULT
        l = l.move(TodayCard.UPCOMING, 0)
        assertEquals(TodayCard.UPCOMING, l.shown.first())
        l = l.move(TodayCard.LOG, 999)
        assertEquals(TodayCard.LOG, l.shown.last())
        l = l.move(TodayCard.LOG, -5)
        assertEquals(TodayCard.LOG, l.shown.first())
        l = l.hide(TodayCard.FORECAST)
        assertFalse(TodayCard.FORECAST in l.shown)
        assertEquals(l, l.move(TodayCard.FORECAST, 0)) // hidden cards don't move
        l = l.show(TodayCard.FORECAST)
        assertEquals(TodayCard.FORECAST, l.shown.last())
        val compact = l.copy(ringCompact = true)
        assertEquals(TodayLayout.DEFAULT.copy(ringCompact = true), compact.reset())
        assertEquals("compact", compact.ringValue())
        assertTrue(TodayLayout.from(mapOf(SettingKeys.TODAY_RING to "compact")).ringCompact)
    }

    @Test fun visibleSkipsCardsWithoutContent() {
        val l = TodayLayout.DEFAULT
        assertEquals(listOf(TodayCard.LOG, TodayCard.PRE), l.visible(setOf(TodayCard.PRE, TodayCard.LOG, TodayCard.WEEK)))
    }

    // ---- quick picks

    @Test fun quickPicksDefaultsCapsAndEmpty() {
        assertEquals(Catalog.PINNED, QuickPicks.from(emptyMap()))
        assertNull(QuickPicks.value(Catalog.PINNED))
        // "" = the person chose none (not the default)
        assertEquals("", QuickPicks.value(emptyList()))
        assertEquals(emptyList(), QuickPicks.from(mapOf(SettingKeys.QUICK_PICKS to "")))
        val many = (Catalog.SYMPTOMS + Catalog.EMOTIONS).joinToString(",")
        assertEquals(QuickPicks.MAX, QuickPicks.from(mapOf(SettingKeys.QUICK_PICKS to many)).size)
        assertEquals(listOf("Sad", "Stress", "Acne"), QuickPicks.from(mapOf(SettingKeys.QUICK_PICKS to "Sad,nope,Stress,Sad,Acne")))
        assertEquals(listOf("Acne"), QuickPicks.symptoms(listOf("Sad", "Stress", "Acne")))
        assertEquals(QuickPicks.Kind.EMOTION, QuickPicks.kind("Sad"))
        assertEquals(QuickPicks.Kind.TAG, QuickPicks.kind("Stress"))
        assertEquals(QuickPicks.Kind.SYMPTOM, QuickPicks.kind("Acne"))
    }

    @Test fun newKeysTravelWithPrefsButNotIntoTheEngine() {
        for (k in listOf(SettingKeys.TODAY_LAYOUT, SettingKeys.TODAY_RING, SettingKeys.QUICK_PICKS)) {
            assertTrue(k in SettingKeys.USER_PREFS); assertFalse(k in SettingKeys.ENGINE)
        }
    }

    // ---- upcoming

    @Test fun upcomingInDateOrderAndOnlyWhatIsAhead() {
        val s = snap(SampleScenario.DAY12, fertTrack = true)
        val items = Upcoming.items(s)
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it.end >= s.today })
        assertEquals(items.sortedBy { maxOf(it.from, s.today) }, items)
        val p = s.prediction!!
        val period = items.single { it.kind == UpcomingKind.PERIOD }
        assertEquals(p.lo, period.from); assertEquals(p.hi, period.to); assertEquals(p.likely, period.likely)
        assertTrue(items.any { it.kind == UpcomingKind.FERTILE } || items.none { it.kind == UpcomingKind.OVULATION })
        // fertility off: no fertile or ovulation rows
        assertTrue(Upcoming.items(snap(SampleScenario.DAY12)).none { it.kind == UpcomingKind.FERTILE || it.kind == UpcomingKind.OVULATION })
    }

    @Test fun upcomingEmptyWhilePausedOrWithoutData() {
        assertTrue(Upcoming.items(snap(SampleScenario.HBC)).isEmpty())
        assertTrue(Upcoming.items(snap(SampleScenario.EMPTY)).isEmpty())
    }

    @Test fun upcomingOngoingAndPast() {
        // expected scenario: the period range has started, so it is ongoing; the pre-period span is over
        val s = snap(SampleScenario.EXPECTED)
        val items = Upcoming.items(s)
        val period = items.single { it.kind == UpcomingKind.PERIOD }
        assertTrue(period.from <= s.today && period.ongoing(s.today))
        assertTrue(items.none { it.kind == UpcomingKind.PRE && it.end < s.today })
        // after ovulation (2ww) no ovulation row
        assertTrue(Upcoming.items(snap(SampleScenario.CONCEIVE_2WW)).none { it.kind == UpcomingKind.OVULATION })
    }

    @Test fun nextReminderOnlyWithConsentAndOnlyListedKinds() {
        val s = snap(SampleScenario.DAY12)
        val tz = TimeZone.of("Europe/Berlin")
        val now = LocalDate.fromEpochDays(s.today).atStartOfDayIn(tz)
        assertNull(Upcoming.nextReminder(s, emptyMap(), now, tz))
        // consent with defaults: period-soon only (echoes the period row) → nothing listed
        val consent = mapOf(NotifyKeys.CONSENT to "1")
        assertNull(Upcoming.nextReminder(s, consent, now, tz))
        // daily log reminder on: still not listed (daily); backup on: listed, 30 days after the start
        val withDaily = consent + (NotifyKeys.kind(NotifyKind.DAILY_LOG) to "1")
        assertNull(Upcoming.nextReminder(s, withDaily, now, tz))
        val withBackup = consent + (NotifyKeys.kind(NotifyKind.BACKUP) to "1") + (NotifyKeys.BACKUP_SINCE to "${s.today - 40}")
        val r = assertNotNull(Upcoming.nextReminder(s, withBackup, now, tz))
        assertEquals(UpcomingKind.REMINDER, r.kind); assertEquals(NotifyKind.BACKUP, r.reminder)
        assertEquals(s.today - 40 + 60, r.from) // a repeating reminder whose first day is past still counts
        assertTrue(Upcoming.merge(Upcoming.items(s), r, s.today).size <= Upcoming.MAX)
    }

    // ---- week strip

    @Test fun weekCellsEqualTheMonthCells() {
        for (sc in listOf(SampleScenario.DAY12, SampleScenario.CONCEIVE, SampleScenario.EXPECTED)) {
            val s = snap(sc, fertTrack = true)
            val week = CalendarPresenter.week(s)
            assertEquals(7, week.size); assertTrue(week[3].today)
            for (cell in week) {
                val d = LocalDate.fromEpochDays(cell.day)
                val m = CalendarPresenter.month(s, d.year, d.month.ordinal + 1).cells.single { it.day == cell.day }
                assertEquals(m.copy(inMonth = true), cell)
            }
        }
    }
}
