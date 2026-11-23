package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.insights.Pattern
import com.nordinary.cyclus.insights.Window
import com.nordinary.cyclus.insights.pastCyclesPublic

/** How the last finished cycle compares with the usual range. */
enum class LengthVerdict { SHORTER, IN, LONGER }

/**
 * „Zyklus vs. üblich“ (spec §7.2): today's cycle day against the usual length, the last finished cycle and the
 * period length against the typical one.
 */
data class UsualCard(
    val cycleDay: Int,
    val typLo: Int,
    val typHi: Int,
    /** Last finished cycle; null when it was implausible (15..90 like the engine) or there is none. */
    val lastLength: Int?,
    val lastVerdict: LengthVerdict?,
    val inPeriod: Boolean,
    /** Period day today (in a period) or the length of this cycle's period. */
    val periodDays: Int?,
    val periodTypical: Int?,
)

/**
 * „Stimmung & Energie“: the last 7 days (today last) with, per day, the person's usual value on the same cycle day
 * of earlier finished cycles (mean, only where at least 2 cycles have one).
 */
data class MoodWeek(
    val days: List<Day>,
    val mood: List<Int?>,
    val energy: List<Int?>,
    val usualMood: List<Double?>,
    val usualEnergy: List<Double?>,
)

/**
 * Spec §9.3: no "follicular"/"luteal" in the app, and ovulation wording only while fertility is shown. So there are
 * two sets: relative to ovulation (fertility shown) and relative to the period (fertility hidden).
 */
enum class Phase(val key: String) {
    PERIOD("period"),
    // fertility shown
    BEFORE_OV("beforeOv"), OVULATION("ov"), AFTER_OV("afterOv"),
    // fertility hidden: no ovulation wording at all
    AFTER_PERIOD("afterPeriod"), PRE("pre"),
}

/**
 * „Phasen-Hinweis“ (Cycle phase): the current phase with one neutral line, plus the person's own patterns for this
 * cycle window. [notForBC]: fertility isn't shown, so the page has no „Nicht zur Verhütung“ footer: the card carries it.
 */
data class PhaseCard(val phase: Phase, val patterns: List<Pattern>, val notForBC: Boolean)

object TodayExtras {
    fun usual(snap: CycleSnapshot): UsualCard? {
        val p = snap.prediction ?: return null
        val cd = snap.cycleDay ?: return null
        if (snap.paused || p.used.size < 2) return null
        val last = p.lens.lastOrNull()?.takeIf { it in 15..90 }
        val verdict = last?.let { if (it < p.typLo) LengthVerdict.SHORTER else if (it > p.typHi) LengthVerdict.LONGER else LengthVerdict.IN }
        val periodDays = if (snap.inPeriod) snap.periodDay else snap.data.periodDays(p.last).size.takeIf { it > 0 }
        return UsualCard(cd, p.typLo, p.typHi, last, verdict, snap.inPeriod, periodDays, InsightsPresenter.periodTypical(snap))
    }

    fun moodWeek(snap: CycleSnapshot): MoodWeek? {
        if (snap.paused) return null
        val today = snap.today
        val days = (today - 6..today).toList()
        val logs = days.map { snap.data.log(it) }
        if (logs.count { it != null && (it.mood != null || it.energy != null) } < 3) return null
        val starts = snap.starts
        val past = if (starts.size >= 2) snap.data.pastCyclesPublic(starts) else emptyList()
        fun usual(d: Day, value: (com.nordinary.cyclus.core.DayLog) -> Int?): Double? {
            // the cycle the day belongs to (counted from its own start); that cycle is not compared with itself
            val s = starts.lastOrNull { it <= d } ?: return null
            val k = d - s
            val vs = past.filter { it.s != s }.mapNotNull { c -> c.days.getOrNull(k)?.l?.let(value) }
            return if (vs.size >= 2) vs.sum().toDouble() / vs.size else null
        }
        return MoodWeek(
            days = days,
            mood = logs.map { it?.mood },
            energy = logs.map { it?.energy },
            usualMood = days.map { usual(it) { l -> l.mood } },
            usualEnergy = days.map { usual(it) { l -> l.energy } },
        )
    }

    fun phase(snap: CycleSnapshot, muted: Set<String>): PhaseCard? {
        val p = snap.prediction ?: return null
        val cd = snap.cycleDay ?: return null
        if (snap.paused) return null
        val today = snap.today
        val f = if (snap.fertilityOn) snap.fertility else null
        val phase = when {
            snap.inPeriod -> Phase.PERIOD
            f != null -> when {
                f.todayCd in f.hpd.first..f.hpd.second -> Phase.OVULATION
                f.todayCd > f.hpd.second -> Phase.AFTER_OV
                else -> Phase.BEFORE_OV
            }
            // fertility hidden: relative to the period only, the same pre-period window as Today's card and the ring
            today >= p.lo - 7 -> Phase.PRE
            else -> Phase.AFTER_PERIOD
        }
        // the same windows as „Deine Muster“ (pastCycles: k = 0-based day, len = cycle length)
        val len = p.likely - p.last
        val win = when {
            snap.inPeriod -> Window.P
            today >= p.likely - 7 && today < p.likely -> Window.PRE
            (cd - 1) in (len - 16)..(len - 11) -> Window.MIDC
            else -> null
        }
        val patterns = if (win == null) emptyList()
            else InsightsPresenter.patternSection(snap, muted).found.filter { it.win == win }.take(2)
        return PhaseCard(phase, patterns, notForBC = !snap.fertilityOn)
    }
}
