package com.nordinary.cyclus.engine

import com.nordinary.cyclus.core.Js
import kotlin.math.min
import com.nordinary.cyclus.core.CycleOverride
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.OverrideKind
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Everything the engines read: the logs, manual cycle overrides and "today". Ported from the prototype's globals. */
class CycleData(
    val logs: Map<Day, DayLog>,
    val overrides: Map<Day, CycleOverride> = emptyMap(),
    val today: Day,
) {
    fun log(day: Day): DayLog? = logs[day]

    /** Period start days (spec §11.5.1, prototype `cycleStarts`). */
    val cycleStarts: List<Day> by lazy {
        val bleedDays = logs.entries.filter { it.value.isBleed }.map { it.key }.sorted()
        val starts = mutableListOf<Day>()
        var prev: Day? = null
        for (d in bleedDays) {
            if (prev == null || (d - prev >= 5 && (starts.isEmpty() || d - starts.last() >= 14))) starts += d
            prev = d
        }
        for ((d, o) in overrides) {
            if (o.kind == OverrideKind.START && d !in starts) starts += d
            if (o.kind == OverrideKind.NOT_START) starts.removeAll { it == d }
        }
        starts.sorted()
    }

    /** Bleeding days of the period that starts on [start] (gaps of up to one day allowed, max 12 days). */
    fun periodDays(start: Day): List<Day> {
        val out = mutableListOf<Day>()
        var d = start
        var gap = 0
        while (gap < 2 && d < start + 12) {
            if (logs[d]?.isBleed == true) { out += d; gap = 0 } else gap++
            d++
        }
        return out
    }
}

enum class Tier(val key: String) { ROUGH("rough"), GETTING("getting"), HISTORY("history") }

/** Posterior-predictive next-period estimate (spec §11.5.2, prototype `predict`). */
data class Prediction(
    val starts: List<Day>,
    val lens: List<Int>,
    val used: List<Int>,
    val last: Day,
    val lo: Day,
    val hi: Day,
    val neff: Double,
    val mun: Double,
    val scale: Double,
    val df: Double,
    val loL: Double,
    val hiL: Double,
    val tier: Tier,
    val typLo: Int,
    val typHi: Int,
) {
    /** Most likely start (median cycle length), shown first; [lo]…[hi] says how sure that is (prototype `likely`). */
    val likely: Day get() = min(hi, max(lo, last + Js.roundInt(exp(mun))))
}

object StudentT {
    private val T90 = listOf(
        3.0 to 1.638, 4.0 to 1.533, 5.0 to 1.476, 6.0 to 1.440, 8.0 to 1.397, 10.0 to 1.372, 12.0 to 1.356,
        16.0 to 1.337, 20.0 to 1.325, 30.0 to 1.310, 1e9 to 1.2816,
    )

    /** 90% two-sided quantile of Student's t (interpolated table). */
    fun q90(df: Double): Double {
        for (i in 1 until T90.size) {
            val (a, qa) = T90[i - 1]
            val (b, qb) = T90[i]
            if (df <= b) return qa + (qb - qa) * max(0.0, df - a) / (b - a)
        }
        return 1.2816
    }

    /** Unnormalised Student-t density. */
    fun pdf(x: Double, df: Double): Double = (1 + x * x / df).pow(-(df + 1) / 2)
}

fun CycleData.predict(): Prediction? {
    val starts = cycleStarts
    if (starts.isEmpty()) return null
    val lens = (1 until starts.size).map { starts[it] - starts[it - 1] }
    val used = lens.takeLast(12).filter { it in 15..90 }
    val mu0 = ln(29.0)
    val k0 = 1.0
    val a0 = 2.0
    val b0 = 0.09 * 0.09 * (a0 - 1)
    val n = used.size
    val w = used.indices.map { i -> 0.5.pow((n - 1 - i) / 6.0) }
    var neff = 0.0
    var sy = 0.0
    used.forEachIndexed { i, l -> neff += w[i]; sy += w[i] * ln(l.toDouble()) }
    val ybar = if (neff != 0.0) sy / neff else mu0
    var sq = 0.0
    used.forEachIndexed { i, l -> sq += w[i] * (ln(l.toDouble()) - ybar).pow(2) }
    val kn = k0 + neff
    val mun = (k0 * mu0 + neff * ybar) / kn
    val an = a0 + neff / 2
    val bn = b0 + 0.5 * sq + k0 * neff * (ybar - mu0).pow(2) / (2 * kn)
    val df = 2 * an
    val scale = sqrt(bn * (kn + 1) / (an * kn))
    val q = StudentT.q90(df)
    val loL = exp(mun - q * scale)
    val hiL = exp(mun + q * scale)
    val last = starts.last()
    var lo = last + floor(loL).toInt()
    var hi = last + ceil(hiL).toInt()
    if (hi - lo < 2) { lo -= 1; hi += 1 }
    val tier = if (neff < 2) Tier.ROUGH else if (neff < 5) Tier.GETTING else Tier.HISTORY
    return Prediction(starts, lens, used, last, lo, hi, neff, mun, scale, df, loL, hiL, tier, floor(loL).toInt(), ceil(hiL).toInt())
}
