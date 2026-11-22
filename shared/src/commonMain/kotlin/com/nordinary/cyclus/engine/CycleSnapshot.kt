package com.nordinary.cyclus.engine

import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.insights.Forecast
import com.nordinary.cyclus.insights.Pattern
import com.nordinary.cyclus.insights.forecast
import com.nordinary.cyclus.insights.patternWatch

enum class CycleStatus(val key: String) {
    PAUSED("paused"), IN_PERIOD("inPeriod"), BETWEEN("between"), PRE("pre"), EXPECTED("expected"), LATER("later")
}

enum class Mode { TRACK, CONCEIVE }

/**
 * Everything the screens need, computed once per data change (the prototype's `vm()` plus the derived engines).
 * Pure and deterministic: same inputs → same snapshot on every platform.
 */
class CycleSnapshot private constructor(
    val data: CycleData,
    val mode: Mode,
    val showFertilityInTrack: Boolean,
    val paused: Boolean,
) {
    val today: Day get() = data.today
    val prediction: Prediction? = data.predict()
    val starts: List<Day> get() = prediction?.starts ?: emptyList()
    val learning: Learning? = prediction?.let { data.learnFromHistory(it) }
    val lut: LutealPrior get() = learning?.lut ?: LutealPrior.POPULATION

    /** Cycle day of today (1 = first period day). */
    val cycleDay: Int? = prediction?.let { today - it.last + 1 }

    val inPeriod: Boolean = prediction?.let { p ->
        today in data.periodDays(p.last) || (data.log(today)?.isBleed == true && today - p.last < 8)
    } ?: false

    val periodDay: Int? = if (inPeriod && prediction != null) {
        val idx = data.periodDays(prediction.last).indexOf(today) + 1
        if (idx != 0) idx else cycleDay
    } else null

    val status: CycleStatus? = prediction?.let { p ->
        when {
            paused -> CycleStatus.PAUSED
            inPeriod -> CycleStatus.IN_PERIOD
            today < p.lo - 7 -> CycleStatus.BETWEEN
            today < p.lo -> CycleStatus.PRE
            today <= p.hi -> CycleStatus.EXPECTED
            else -> CycleStatus.LATER
        }
    }

    val fertilityOn: Boolean = prediction != null && !paused && (mode == Mode.CONCEIVE || showFertilityInTrack)

    /** Current-cycle fertility estimate; computed for every snapshot, shown only when [fertilityOn]. */
    val fertility: FertilityResult? by lazy { prediction?.let { data.fertility(it, lut) } }

    val patterns: List<Pattern> by lazy {
        if (prediction == null) emptyList() else data.patternWatch(starts, hasPrediction = true, learning = learning)
    }

    val forecast: Forecast? by lazy { data.forecast(prediction, paused, inPeriod, patterns) }

    fun trail(): List<TrailStep> = prediction?.let { data.trail(it, lut) } ?: emptyList()

    companion object {
        fun compute(data: CycleData, mode: Mode = Mode.TRACK, showFertilityInTrack: Boolean = false, paused: Boolean = false) =
            CycleSnapshot(data, mode, showFertilityInTrack, paused)
    }
}
