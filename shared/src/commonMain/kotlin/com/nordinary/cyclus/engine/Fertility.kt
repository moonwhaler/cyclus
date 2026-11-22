package com.nordinary.cyclus.engine

import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.LhResult
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/** Luteal-phase length prior (days from ovulation to the next period). Population default: Bull et al. 2019. */
data class LutealPrior(val mean: Double, val sd: Double, val lo: Int, val hi: Int, val learned: Int) {
    companion object { val POPULATION = LutealPrior(12.4, 2.4, 7, 17, 0) }

    /** Discretised normal over lo…hi (prototype `lutPmf`), index = luteal length. */
    fun pmf(): Map<Int, Double> {
        val p = LinkedHashMap<Int, Double>()
        var z = 0.0
        for (k in lo..hi) { val v = exp(-0.5 * ((k - mean) / sd).pow(2)); p[k] = v; z += v }
        for (k in p.keys) p[k] = p.getValue(k) / z
        return p
    }
}

enum class EvidenceType(val key: String) {
    LH_POS("lhpos"), LH_NEG("lhneg"), SURGE("surge"), FERTILE_MUCUS("ew"), DRY("dry"), PEAK("peak"), SHIFT("shift");

    /** Likelihood of this observation if ovulation did *not* happen in this cycle (anovulatory). */
    val likAnovulatory: Double
        get() = when (this) {
            LH_POS -> .1; LH_NEG -> .95; SURGE -> .3; FERTILE_MUCUS -> .4; DRY -> .6; PEAK -> .3; SHIFT -> .03
        }
}

data class Evidence(val type: EvidenceType, val cd: Int) {
    /** `P(observation | ovulation on cycle day d)` (spec §11.11.3, prototype `likO`). */
    fun likOvulationOn(d: Int): Double {
        val x = cd
        val k = d - x
        return when (type) {
            EvidenceType.LH_POS -> when (k) { 0 -> .15; 1 -> .45; 2 -> .30; 3 -> .07; else -> .003 }
            EvidenceType.LH_NEG -> if (k == 1 || k == 2) .20 else .95
            EvidenceType.SURGE -> if (k in 1..5) .8 else .2
            EvidenceType.FERTILE_MUCUS -> if (x >= d - 5 && x <= d) .8 else if (x == d + 1 || x == d + 2) .4 else .1
            EvidenceType.DRY -> if (x >= d - 2 && x <= d) .2 else .8
            EvidenceType.PEAK -> when (k) { -1 -> .2; 0 -> .35; 1 -> .3; 2 -> .1; else -> .01 }
            EvidenceType.SHIFT -> when (d) { x - 3 -> .1; x - 2 -> .3; x - 1 -> .45; x -> .15; else -> .004 }
        }
    }
}

data class BbtPoint(val cd: Int, val v: Double)
data class TempShift(val cd: Int, val cover: Double)

data class FertilityDay(val cd: Int, val pf: Double, val c: Double, val tier: Int)

enum class OvulationStatus(val key: String) { PREDICTED("predicted"), SOON("soon"), OCCURRED("occurred") }

enum class FertilityChip(val key: String) {
    SHIFT("chip_shift"), LH("chip_lh"), MUCUS("chip_mucus"), LH_NEG("chip_lhs"), CALENDAR("chip_cal")
}

data class FertilityResult(
    val cs: Day,
    val todayCd: Int,
    val dMax: Int,
    /** Posterior of the ovulation day, index = cycle day (0 for days < 6). */
    val post: DoubleArray,
    val pA: Double,
    val days: List<FertilityDay>,
    val band: Pair<Int, Int>?,
    val mapD: Int,
    val hpd: Pair<Int, Int>,
    val status: OvulationStatus,
    val shift: TempShift?,
    val ev: List<Evidence>,
    val bbt: List<BbtPoint>,
    val planStart: Int,
    val chip: FertilityChip,
) {
    fun dateOf(cd: Int): Day = cs + cd - 1
}

private val WILCOX = doubleArrayOf(0.10, 0.16, 0.14, 0.27, 0.31, 0.33)

internal data class EvidenceSet(val ev: List<Evidence>, val bbt: List<BbtPoint>, val shift: TempShift?)

internal fun CycleData.evidenceFor(cs: Day, uptoCd: Int): EvidenceSet {
    val ev = mutableListOf<Evidence>()
    val bbt = mutableListOf<BbtPoint>()
    for (cd in 1..uptoCd) {
        val l = logs[cs + cd - 1] ?: continue
        val prev = logs[cs + cd - 2]
        if (l.lh == LhResult.POSITIVE && prev?.lh != LhResult.POSITIVE) ev += Evidence(EvidenceType.LH_POS, cd)
        if (l.lh == LhResult.NEGATIVE) ev += Evidence(EvidenceType.LH_NEG, cd)
        if (l.lh == LhResult.SURGE) ev += Evidence(EvidenceType.SURGE, cd)
        if (l.mucus?.isFertile == true) ev += Evidence(EvidenceType.FERTILE_MUCUS, cd)
        if (l.mucus?.isDry == true) ev += Evidence(EvidenceType.DRY, cd)
        l.bbt?.let { bbt += BbtPoint(cd, it) }
    }
    val ews = ev.filter { it.type == EvidenceType.FERTILE_MUCUS }.map { it.cd }
    if (ews.isNotEmpty()) {
        val p = ews.max()
        val nxt = logs[cs + p]
        if (p < uptoCd && nxt?.mucus != null && !nxt.mucus.isFertile) ev += Evidence(EvidenceType.PEAK, p)
    }
    var shift: TempShift? = null
    var i = 6
    while (i + 2 < bbt.size) {
        val consecutive = bbt[i + 2].cd - bbt[i].cd == 2 && bbt[i].cd - bbt[i - 6].cd == 6
        if (consecutive) {
            val m = bbt.subList(i - 6, i).maxOf { it.v }
            if (bbt[i].v > m && bbt[i + 1].v > m && bbt[i + 2].v >= m + 0.2 - 1e-9) { shift = TempShift(bbt[i].cd, m); break }
        }
        i++
    }
    if (shift != null) ev += Evidence(EvidenceType.SHIFT, shift.cd)
    return EvidenceSet(ev, bbt, shift)
}

/**
 * Daily Bayesian ovulation estimate (spec §11.11, prototype `fertility`).
 * @param uptoCd only evidence up to this cycle day is used (default: today).
 * @param cycleStart retrospective run on a finished cycle starting here, with [knownLength] known.
 */
fun CycleData.fertility(
    pred: Prediction,
    lut: LutealPrior,
    uptoCd: Int? = null,
    cycleStart: Day? = null,
    knownLength: Int? = null,
): FertilityResult {
    val cs = cycleStart ?: pred.last
    val todayCd = knownLength ?: (today - cs + 1)
    val upto = uptoCd ?: todayCd
    val dMax = max(60, ceil(1.5 * exp(pred.mun)).toInt())

    // P(cycle length = L) from the posterior predictive, given that the cycle has lasted todayCd days
    val lp = LinkedHashMap<Int, Double>()
    var z = 0.0
    if (knownLength != null) { lp[knownLength] = 1.0; z = 1.0 } else {
        for (l in max(18, todayCd)..dMax + 17) {
            val v = StudentT.pdf((ln(l.toDouble()) - pred.mun) / pred.scale, pred.df) / l
            lp[l] = v; z += v
        }
    }
    for (l in lp.keys) lp[l] = lp.getValue(l) / z
    val lutPmf = lut.pmf()
    // prior over the ovulation day O = L − luteal length; JS iterates integer keys in ascending order
    val prior = DoubleArray(dMax + 1)
    val priorSet = BooleanArray(dMax + 1)
    for (l in lp.keys.sorted()) for (k in lutPmf.keys.sorted()) {
        val o = l - k
        if (o in 6..dMax) { prior[o] += lp.getValue(l) * lutPmf.getValue(k); priorSet[o] = true }
    }
    val pA0 = 0.05
    val (ev, bbt, shift) = evidenceFor(cs, upto)
    val post = DoubleArray(dMax + 1)
    var pA = pA0
    var tot = 0.0
    for (d in 6..dMax) {
        var p = prior[d] * (1 - pA0)
        for (e in ev) p *= e.likOvulationOn(d)
        if (shift == null) {
            val lowsAfter = bbt.count { it.cd > d + 2 }
            if (lowsAfter >= 6) p *= 0.1
        }
        post[d] = p
        tot += p
    }
    for (e in ev) pA *= e.type.likAnovulatory
    tot += pA
    for (d in 6..dMax) post[d] /= tot
    pA /= tot

    fun postAt(d: Int): Double = if (d in 6..dMax) post[d] else 0.0
    fun pf(cd: Int): Double { var s = 0.0; for (d in cd..cd + 5) s += postAt(d); return s }
    fun cw(cd: Int): Double { var s = 0.0; for (k in -5..0) s += postAt(cd - k) * WILCOX[k + 5]; return s }

    val raw = (1..dMax).map { Triple(it, pf(it), cw(it)) }
    val topC = raw.sortedByDescending { it.third }.take(2).map { it.first }
    val ds = (6..dMax).sortedByDescending { post[it] }
    val mapD = ds.first()
    // high: likely in the fertile window, or the most likely ovulation day and the 2 days before it (the most fertile
    // span) once they are reasonably likely; so the high days never stop short of the ovulation marker
    val days = raw.map { (cd, pfv, c) ->
        val nearO = cd in mapD - 2..mapD && pfv >= 0.3
        val tier = if (pfv >= 0.6 && cd in topC) 3 else if (pfv >= 0.5 || nearO) 2 else if (pfv >= 0.15) 1 else 0
        FertilityDay(cd, pfv, c, tier)
    }
    val inBand = days.filter { it.pf >= 0.15 }
    val band = if (inBand.isNotEmpty()) inBand.first().cd to inBand.last().cd else null
    var acc = 0.0
    val hp = mutableListOf<Int>()
    val mass = 1 - pA
    for (d in ds) { hp += d; acc += post[d]; if (acc >= 0.8 * mass) break }
    val hpd = hp.min() to hp.max()
    fun has(t: EvidenceType) = ev.any { it.type == t }
    val w = hpd.second - hpd.first + 1
    val status = when {
        shift != null && w <= 3 -> OvulationStatus.OCCURRED
        (has(EvidenceType.LH_POS) || has(EvidenceType.FERTILE_MUCUS) || has(EvidenceType.SURGE)) && w <= 4 &&
            hpd.first >= todayCd - 1 -> OvulationStatus.SOON
        else -> OvulationStatus.PREDICTED
    }
    var pz = 0.0
    for (d in 6..dMax) if (priorSet[d]) pz += prior[d]
    var planStart: Int? = null
    for (x in 6..dMax) {
        var cum = 0.0
        for (d in 6..x + 2) cum += (if (d <= dMax) prior[d] else 0.0) / pz
        if (cum >= 0.10) { planStart = x; break }
    }
    val chip = when {
        shift != null -> FertilityChip.SHIFT
        has(EvidenceType.LH_POS) -> FertilityChip.LH
        has(EvidenceType.FERTILE_MUCUS) || has(EvidenceType.DRY) -> FertilityChip.MUCUS
        has(EvidenceType.LH_NEG) -> FertilityChip.LH_NEG
        else -> FertilityChip.CALENDAR
    }
    return FertilityResult(cs, todayCd, dMax, post, pA, days, band, mapD, hpd, status, shift, ev, bbt, planStart ?: 10, chip)
}

/** How the window moved as evidence arrived ("Why this window?", prototype `trail`). */
data class TrailStep(val cd: Int, val events: List<EvidenceType>, val band: Pair<Int, Int>?, val changed: Boolean?, val result: FertilityResult)

fun CycleData.trail(pred: Prediction, lut: LutealPrior): List<TrailStep> {
    val f0 = fertility(pred, lut, uptoCd = 0)
    val out = mutableListOf(TrailStep(0, emptyList(), f0.band, null, f0))
    val full = fertility(pred, lut)
    val cds = full.ev.map { it.cd }.distinct().sorted()
    var prevBand = f0.band
    for (cd in cds) {
        val f = fertility(pred, lut, uptoCd = cd)
        out += TrailStep(cd, full.ev.filter { it.cd == cd }.map { it.type }.distinct(), f.band, f.band != prevBand, f)
        prevBand = f.band
    }
    return out
}
