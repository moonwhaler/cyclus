package com.nordinary.cyclus.engine

import com.nordinary.cyclus.core.Catalog
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.Js
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Retrospective ovulation estimate for one finished cycle (spec §11.11.5). */
data class LearnedCycle(
    val cs: Day,
    val length: Int,
    val ovulationDay: Int,
    val hpd: Pair<Int, Int>,
    /** Confirmed by LH+ or a temperature shift, with a tight window. Only these teach the personal prior. */
    val good: Boolean,
    /** Days from the first positive LH test to the estimated ovulation. */
    val lag: Int?,
    /** First fertile-mucus day relative to ovulation (negative = before). */
    val ewFirst: Int?,
    val shift: Boolean,
    /** ≥ 10 temperatures logged. */
    val tracked: Boolean,
    /** ≥ 3 stress-tagged days in the first two weeks. */
    val stress: Boolean,
)

data class Learning(val cycles: List<LearnedCycle>, val lut: LutealPrior) {
    val good: List<LearnedCycle> get() = cycles.filter { it.good }
}

fun CycleData.learnFromHistory(pred: Prediction): Learning {
    val st = pred.starts
    val cycles = mutableListOf<LearnedCycle>()
    for (i in 0 until st.size - 1) {
        val cs = st[i]
        val l = st[i + 1] - cs
        if (l < 18 || l > 60) continue
        val f = fertility(pred, LutealPrior.POPULATION, uptoCd = l, cycleStart = cs, knownLength = l)
        val w = f.hpd.second - f.hpd.first + 1
        val lhpos = f.ev.firstOrNull { it.type == EvidenceType.LH_POS }
        val ews = f.ev.filter { it.type == EvidenceType.FERTILE_MUCUS }.map { it.cd }
        var stress = 0
        for (d in cs until cs + 14) if (logs[d]?.tags?.contains(Catalog.STRESS) == true) stress++
        cycles += LearnedCycle(
            cs = cs, length = l, ovulationDay = f.mapD, hpd = f.hpd,
            good = (lhpos != null || f.shift != null) && w <= 3,
            lag = lhpos?.let { f.mapD - it.cd },
            ewFirst = if (ews.isNotEmpty()) ews.min() - f.mapD else null,
            shift = f.shift != null, tracked = f.bbt.size >= 10, stress = stress >= 3,
        )
    }
    val good = cycles.filter { it.good }
    var lut = LutealPrior.POPULATION
    if (good.size >= 2) { // shrink the personal luteal length toward the population prior
        val luts = good.map { (it.length - it.ovulationDay).toDouble() }
        val n = luts.size
        val k0 = 2
        val m = luts.sum() / n
        val pm = (k0 * LutealPrior.POPULATION.mean + n * m) / (k0 + n)
        var ss = 0.0
        for (x in luts) ss += (x - m).pow(2)
        val sd = max(1.0, sqrt((k0 * LutealPrior.POPULATION.sd.pow(2) + ss) / (k0 + n)))
        lut = LutealPrior(
            mean = Js.round(pm * 10) / 10, sd = sd,
            lo = max(7, Js.roundInt(pm - 3 * sd)), hi = min(17, Js.roundInt(pm + 3 * sd)), learned = n,
        )
    }
    return Learning(cycles, lut)
}
