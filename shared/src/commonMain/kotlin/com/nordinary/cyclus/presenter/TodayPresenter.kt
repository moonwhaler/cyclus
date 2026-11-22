package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.core.Catalog
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.EpisodeKind
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.engine.CycleStatus
import com.nordinary.cyclus.engine.Mode
import com.nordinary.cyclus.engine.Tier
import com.nordinary.cyclus.insights.Area
import com.nordinary.cyclus.insights.Forecast
import kotlin.math.max
import kotlin.math.min

enum class SegmentKind { NEUTRAL, BLEED, PRE, FERTILE }

/** One day on the ring. Angles in degrees, 0° = 3 o'clock, clockwise (same as the prototype's SVG). */
data class RingSegment(val cd: Int, val kind: SegmentKind, val alpha: Float, val dashed: Boolean, val startDeg: Float, val endDeg: Float)

data class RingModel(
    val segments: List<RingSegment>,
    /** Dashed outer arc for the predicted next-period range. */
    val predicted: Pair<Float, Float>?,
    /** Today's position. */
    val knobDeg: Float?,
    /** Estimated ovulation marker (◎), solid when no longer just predicted. */
    val ovulationDeg: Float?,
    val ovulationSolid: Boolean,
) {
    companion object {
        const val START = 135f
        const val SWEEP = 270f
        val EMPTY = RingModel(emptyList(), null, null, null, false)
    }
}

/** What the ring centre and the area under it show (prototype `renderToday`, track mode). */
sealed interface TodayHero {
    data object Empty : TodayHero
    data class Paused(val cycleDay: Int, val episode: EpisodeKind?) : TodayHero
    data class InPeriod(val periodDay: Int, val todaysFlow: Flow?) : TodayHero
    data class Track(
        val cycleDay: Int,
        val status: CycleStatus,
        val lo: Day,
        val hi: Day,
        val daysToLo: Int,
        val daysToHi: Int,
        val tier: Tier,
        /** Fewer than ~2 cycles of evidence: amber dot instead of green. */
        val rough: Boolean,
        val showPeriodStartButton: Boolean,
    ) : TodayHero

    /** Conceive mode before ovulation is confirmed: today's fertility tier is the big word. */
    data class ConceiveFertile(
        val tier: Int,
        val cycleDay: Int,
        /** Days until the window opens (tier 0 only), or null. */
        val windowOpensIn: Int?,
        /** Tier 0 and the window is behind us. */
        val windowPassed: Boolean,
        val ovulation: OvulationLine,
        val chip: com.nordinary.cyclus.engine.FertilityChip,
    ) : TodayHero

    /** Two-week wait: ovulation likely occurred. */
    data class ConceiveAfter(val daysAfter: Int, val cycleDay: Int, val ovulationDay: Day, val chip: com.nordinary.cyclus.engine.FertilityChip) : TodayHero
}

sealed interface OvulationLine {
    data object TodayOrTomorrow : OvulationLine
    data class Soon(val from: Day, val to: Day) : OvulationLine
    data class Estimated(val from: Day, val to: Day) : OvulationLine
}

/** Conceive-mode cards under the ring (prototype `renderToday`, conceive branch). */
data class ConceiveCards(
    /** Up to 3 best days from today on, in date order. */
    val bestDays: List<Day>,
    /** "Starte am … mit dem Testen" before the planner start day. */
    val lhPlanStart: Day?,
    val cyclesUsed: Int,
    /** One-tap LH card: shown from the planner start until a positive test (or while today's test is positive). */
    val lhToday: Boolean,
    val lhNegativesSoFar: Int,
    val todaysLh: com.nordinary.cyclus.core.LhResult?,
    /** Earliest reliable pregnancy test (after ovulation). */
    val earliestTest: Day?,
)

data class TodayUiState(
    val today: Day,
    val hero: TodayHero,
    val ring: RingModel,
    val ringA11y: RingA11y,
    val todaysLog: DayLog?,
    /** "Tage vor der Periode ab …" card (between status). */
    val preWindowFrom: Day?,
    /** Fertile window shown in track mode when enabled ("not for birth control" footer). */
    val trackFertileWindow: Pair<Day, Day>?,
    val forecast: Forecast?,
    val patternCount: Int,
    val fertilityFooter: Boolean,
    val conceive: ConceiveCards? = null,
)

/** Data for the ring's single accessibility sentence. */
data class RingA11y(val cycleDay: Int?, val lo: Day?, val hi: Day?, val fertile: Pair<Day, Day>?, val paused: EpisodeKind?)

object TodayPresenter {
    fun present(snap: CycleSnapshot, episode: EpisodeKind?, mutedPatterns: Set<String> = emptySet()): TodayUiState {
        val today = snap.today
        val p = snap.prediction
        val todaysLog = snap.data.log(today)
        if (p == null || snap.cycleDay == null || snap.status == null) {
            return TodayUiState(today, TodayHero.Empty, RingModel.EMPTY, RingA11y(null, null, null, null, null), todaysLog, null, null, null, 0, false)
        }
        val cd = snap.cycleDay
        val f = if (snap.fertilityOn) snap.fertility else null
        var conceive: ConceiveCards? = null
        val hero: TodayHero = when {
            snap.status == CycleStatus.PAUSED -> TodayHero.Paused(cd, episode)
            snap.status == CycleStatus.IN_PERIOD -> TodayHero.InPeriod(snap.periodDay ?: cd, todaysLog?.flow)
            f != null && snap.mode == Mode.CONCEIVE -> {
                val (h, cards) = conceive(snap, f, cd, todaysLog)
                conceive = cards
                h
            }
            else -> TodayHero.Track(
                cycleDay = cd, status = snap.status, lo = p.lo, hi = p.hi, daysToLo = p.lo - today, daysToHi = p.hi - today,
                tier = p.tier, rough = p.neff < 2,
                showPeriodStartButton = snap.status in setOf(CycleStatus.PRE, CycleStatus.EXPECTED, CycleStatus.LATER) && p.lo - today <= 3,
            )
        }
        val trackHero = hero is TodayHero.Track
        val trackFertile = if (snap.mode == Mode.TRACK && snap.showFertilityInTrack) f?.band?.let { f.dateOf(it.first) to f.dateOf(it.second) } else null
        val patternCount = if (snap.paused) 0 else snap.patterns.count {
            (it.area != Area.FERT || snap.fertilityOn) && it.id !in mutedPatterns && it.status.isPattern
        }
        return TodayUiState(
            today = today,
            hero = hero,
            ring = ring(snap),
            ringA11y = RingA11y(cd, p.lo, p.hi, f?.band?.let { f.dateOf(it.first) to f.dateOf(it.second) }, if (snap.paused) episode else null),
            todaysLog = todaysLog,
            preWindowFrom = if (trackHero && snap.status == CycleStatus.BETWEEN) p.lo - 7 else null,
            trackFertileWindow = if (trackHero) trackFertile else null,
            forecast = if (snap.paused) null else snap.forecast,
            patternCount = patternCount,
            fertilityFooter = snap.fertilityOn,
            conceive = conceive,
        )
    }

    private fun conceive(snap: CycleSnapshot, f: com.nordinary.cyclus.engine.FertilityResult, cd: Int, todaysLog: DayLog?): Pair<TodayHero, ConceiveCards> {
        val p = snap.prediction!!
        val today = snap.today
        if (f.status == com.nordinary.cyclus.engine.OvulationStatus.OCCURRED) {
            val test = max(f.dateOf(f.mapD) + 14, p.lo)
            return TodayHero.ConceiveAfter(f.todayCd - f.mapD, cd, f.dateOf(f.mapD), f.chip) to
                ConceiveCards(emptyList(), null, p.used.size, false, 0, todaysLog?.lh, test)
        }
        val tier = f.days.getOrNull(f.todayCd - 1)?.tier ?: 0
        var opensIn: Int? = null
        var passed = false
        if (tier == 0 && f.band != null) {
            val n = f.band.first - f.todayCd
            if (n > 0) opensIn = n else passed = true
        }
        val ov = when {
            f.status == com.nordinary.cyclus.engine.OvulationStatus.SOON && f.hpd.first <= f.todayCd -> OvulationLine.TodayOrTomorrow
            f.status == com.nordinary.cyclus.engine.OvulationStatus.SOON -> OvulationLine.Soon(f.dateOf(f.hpd.first), f.dateOf(f.hpd.second))
            else -> OvulationLine.Estimated(f.dateOf(f.hpd.first), f.dateOf(f.hpd.second))
        }
        val best = f.days.filter { it.cd >= f.todayCd && it.pf >= 0.15 }.sortedByDescending { it.c }.take(3).sortedBy { it.cd }.map { f.dateOf(it.cd) }
        val lhPos = f.ev.any { it.type == com.nordinary.cyclus.engine.EvidenceType.LH_POS }
        val tl = todaysLog?.lh
        val startDate = f.dateOf(f.planStart)
        var plan: Day? = null
        var lhToday = false
        if (!lhPos || tl == com.nordinary.cyclus.core.LhResult.POSITIVE) {
            if (today < startDate) plan = startDate else lhToday = true
        }
        val negs = f.ev.count { it.type == com.nordinary.cyclus.engine.EvidenceType.LH_NEG }
        return TodayHero.ConceiveFertile(tier, cd, opensIn, passed, ov, f.chip) to
            ConceiveCards(best, plan, p.used.size, lhToday, negs, tl, null)
    }

    /** Ring geometry (prototype `ringSVG`). */
    fun ring(snap: CycleSnapshot): RingModel {
        val p = snap.prediction ?: return RingModel.EMPTY
        val cd = snap.cycleDay ?: return RingModel.EMPTY
        val n = max(max(p.hi - p.last + 1, cd + 2), 28)
        val per = RingModel.SWEEP / n
        val bleed = snap.data.periodDays(p.last).toSet()
        val f = if (snap.fertilityOn) snap.fertility else null
        val segments = (1..n).map { c ->
            val day = p.last + c - 1
            var kind = SegmentKind.NEUTRAL; var alpha = .32f; var dashed = false
            if (day in bleed) { kind = SegmentKind.BLEED; alpha = 1f }
            else if (!snap.paused && day >= p.lo - 7 && day < p.lo) { kind = SegmentKind.PRE; alpha = .85f; dashed = true }
            val fd = f?.days?.getOrNull(c - 1)
            if (fd != null && fd.tier > 0 && day !in bleed) { kind = SegmentKind.FERTILE; alpha = floatArrayOf(0f, .35f, .65f, 1f)[fd.tier]; dashed = false }
            RingSegment(c, kind, alpha, dashed, RingModel.START + (c - 1) * per + 0.7f, RingModel.START + c * per - 0.7f)
        }
        var predicted: Pair<Float, Float>? = null
        var ov: Float? = null
        if (!snap.paused) {
            val a0 = RingModel.START + (p.lo - p.last) * per
            val a1 = RingModel.START + (p.hi - p.last + 1) * per
            predicted = a0 to min(a1, RingModel.START + RingModel.SWEEP)
            if (f != null) ov = RingModel.START + (f.mapD - 0.5f) * per
        }
        val knob = min(RingModel.START + (cd - 0.5f) * per, 404f)
        return RingModel(segments, predicted, knob, ov, f != null && f.status.key != "predicted")
    }

    /** "Often around this time" symptom suggestions for the log sheet (prototype `renderLogSheet`). */
    fun suggestions(snap: CycleSnapshot, day: Day): List<String> {
        val p = snap.prediction ?: return emptyList()
        if (snap.starts.size <= 2) return emptyList()
        val cand = LinkedHashMap<String, Int>()
        val off = day - p.last
        for (s in snap.starts.dropLast(1)) for (k in -1..1) {
            snap.data.log(s + off + k)?.symptoms?.forEach { y -> if (y !in Catalog.PINNED) cand[y] = (cand[y] ?: 0) + 1 }
        }
        return cand.entries.sortedByDescending { it.value }.take(3).filter { it.value >= 2 }.map { it.key }
    }
}
