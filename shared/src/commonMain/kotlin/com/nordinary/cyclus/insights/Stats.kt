package com.nordinary.cyclus.insights

import com.nordinary.cyclus.core.Rng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Seeded beta sampler (Marsaglia–Tsang gamma), identical draw order to the prototype's `sampler`. */
internal class BetaSampler(seed: Int) {
    private val rng = Rng(seed)
    private fun u() = rng.next()

    private fun norm(): Double {
        var a = 0.0
        while (a == 0.0) a = u()
        return sqrt(-2 * ln(a)) * cos(2 * PI * u())
    }

    private fun gamma(k: Double): Double {
        if (k < 1) { val g = gamma(k + 1); return g * u().pow(1 / k) }
        val d = k - 1.0 / 3
        val c = 1 / sqrt(9 * d)
        while (true) {
            var x: Double
            var v: Double
            do { x = norm(); v = 1 + c * x } while (v <= 0)
            v = v * v * v
            val uu = u()
            if (uu < 1 - 0.0331 * x.pow(4) || ln(uu) < 0.5 * x * x + d * (1 - v + ln(v))) return d * v
        }
    }

    fun beta(a: Double, b: Double): Double { val x = gamma(a); val y = gamma(b); return x / (x + y) }
}

/** Posterior of a risk ratio (Jeffreys priors, counts deflated by the design effect). */
data class RiskRatio(val p125: Double, val p15: Double, val q10: Double)

internal fun rrPost(kin: Int, nin: Int, kout: Int, nout: Int, deff: Double, seed: Int): RiskRatio {
    val beta = BetaSampler(seed)
    val n = 1000
    val dr = DoubleArray(n)
    for (i in 0 until n) {
        val a = beta.beta(0.5 + kin / deff, 0.5 + (nin - kin) / deff)
        val b = beta.beta(0.5 + kout / deff, 0.5 + (nout - kout) / deff)
        dr[i] = a / b
    }
    dr.sort()
    fun above(th: Double) = dr.count { it > th }.toDouble() / n
    return RiskRatio(above(1.25), above(1.5), dr[floor(n * 0.1).toInt()])
}

internal data class Ols(val b: Double, val se: Double, val first: Double, val last: Double)

internal fun ols(ys: List<Double>): Ols {
    val n = ys.size
    val mx = (n - 1) / 2.0
    val my = ys.sum() / n
    var sxx = 0.0
    var sxy = 0.0
    ys.forEachIndexed { i, y -> sxx += (i - mx).pow(2); sxy += (i - mx) * (y - my) }
    val b = sxy / sxx
    val a = my - b * mx
    var sse = 0.0
    ys.forEachIndexed { i, y -> sse += (y - (a + b * i)).pow(2) }
    return Ols(b, sqrt(sse / max(1, n - 2) / sxx), a, a + b * (n - 1))
}

internal fun List<Double>.avg(): Double = sum() / size
