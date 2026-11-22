package com.nordinary.cyclus.insights

import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.Prediction
import com.nordinary.cyclus.engine.StudentT
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

enum class ForecastKind { ITEM, SCALE }

/** One row of the 7-day „Ausblick“ grid: an item or scale with a 0…1 intensity per day. */
data class ForecastRow(
    val key: String,
    val kind: ForecastKind,
    val dir: Direction?,
    /** First day index (0 = today) with a non-zero cell. */
    val first: Int,
    val cells: List<Double>,
    /** Tag that triggered this row (e.g. "Alcohol" yesterday), if any. */
    val reason: String?,
)

data class Forecast(val days: List<Day>, val rows: List<ForecastRow>, val cycles: Int)

/** P(next period starts on day s), s ≥ fromDay (prototype `startPmf`). Keys ascending. */
internal fun startPmf(pred: Prediction, fromDay: Day): LinkedHashMap<Day, Double> {
    val out = LinkedHashMap<Day, Double>()
    var z = 0.0
    for (lc in 15..70) {
        val s = pred.last + lc
        if (s < fromDay) continue
        val w = StudentT.pdf((ln(lc.toDouble()) - pred.mun) / pred.scale, pred.df) / lc
        out[s] = w; z += w
    }
    for (s in out.keys) out[s] = out.getValue(s) / z
    return out
}

/**
 * „Ausblick“: turns found patterns into a 7-day outlook (spec §11.13, prototype `forecast`).
 * [patterns] = `patternWatch` for the same data.
 */
fun CycleData.forecast(
    pred: Prediction?,
    paused: Boolean,
    inPeriod: Boolean,
    patterns: List<Pattern>,
): Forecast? {
    if (pred == null || paused) return null
    val cyc = pastCycles(pred.starts)
    if (cyc.size < 2) return null
    val pats = patterns.filter { it.status.isPattern }
    val items = pats.filter { it.item != null && it.tag == null }.map { it.item!! }.distinct()
    val scales = pats.filter { it.scale != null }
    if (items.isEmpty() && scales.isEmpty()) return null

    // learned day profiles: P0..P6 = period day, R-14..R-1 = days before the next start, X = earlier in the cycle
    class Acc(var sum: Double = 0.0, var n: Int = 0)
    val prof = HashMap<String, LinkedHashMap<String, Acc>>()
    fun add(k: String, b: String, v: Double) {
        val e = prof.getOrPut(k) { LinkedHashMap() }.getOrPut(b) { Acc() }
        e.sum += v; e.n++
    }
    for (c in cyc) for (y in c.days) {
        if (!y.obs) continue
        val b = if (y.win == Window.P) "P${min(y.k, 6)}" else if (y.rel >= -14) "R${y.rel}" else "X"
        items.forEach { x -> add(x, b, if (y.l?.has(x) == true) 1.0 else 0.0) }
        scales.forEach { p -> val v = y.l?.scale(p.scale!!); if (v != null && v != 0) add("s:${p.scale}", b, v.toDouble()) }
    }
    fun base(k: String): Double {
        var s = 0.0; var n = 0
        prof[k]?.values?.forEach { s += it.sum; n += it.n }
        return if (n != 0) s / n else 0.0
    }
    fun at(k: String, b: String): Double { // shrink to the person's overall rate
        val e = prof[k]?.get(b); val m = base(k)
        return if (e != null) (e.sum + 2 * m) / (e.n + 2) else m
    }
    fun rate(k: String, b: String): Double {
        if (b == "X") return at(k, b)
        val n = b.substring(1).toInt()
        val nb = if (b[0] == 'R') listOf("R${n - 1}", "R${n + 1}") else listOf("P${max(0, n - 1)}", "P${min(6, n + 1)}")
        return 0.5 * at(k, b) + 0.25 * at(k, nb[0]) + 0.25 * at(k, nb[1])
    }
    val plMed = cyc.map { periodDays(it.s).size }.sorted().let { it[it.size / 2] }
    val sp = startPmf(pred, today + 1)

    class Row(val key: String, val kind: ForecastKind, val dir: Direction?, var first: Int) {
        val cells = DoubleArray(7)
        var reason: String? = null
    }
    val rows = LinkedHashMap<String, Row>()
    fun put(id: String, key: String, kind: ForecastKind, dir: Direction?, i: Int, p: Double): Row {
        val r = rows.getOrPut(id) { Row(key, kind, dir, i) }
        r.cells[i] = max(r.cells[i], p)
        return r
    }
    for (i in 0 until 7) {
        val d = today + i
        // mixture over when the next period starts: timing uncertainty flattens the outlook further ahead
        fun mix(fn: (String) -> Double): Double {
            if (inPeriod && d - pred.last < plMed) return fn("P${min(d - pred.last, 6)}")
            var acc = 0.0
            for ((s, w) in sp) acc += w * fn(if (d >= s) "P${min(d - s, 6)}" else if (d - s >= -14) "R${d - s}" else "X")
            return acc
        }
        items.forEach { x ->
            val p = mix { b -> rate(x, b) }
            val m = base(x)
            if (p >= 0.3 && p >= 1.5 * m) put("i:$x", x, ForecastKind.ITEM, null, i, p)
        }
        scales.forEach { sc ->
            val key = "s:${sc.scale}"
            val e = mix { b -> rate(key, b) }
            val dl = e - base(key)
            val want = if (sc.dir == Direction.LO) -1.0 else 1.0
            if (abs(dl) >= 0.5 && kotlin.math.sign(dl) == want) put(key, sc.scale!!, ForecastKind.SCALE, sc.dir, i, min(1.0, abs(dl) / 1.5))
        }
        if (i <= 1) pats.filter { it.tag != null }.forEach { p ->
            val y = logs[d - 1]
            val td = logs[d]
            if ((y != null && p.tag in y.tags) || (i == 0 && td != null && p.tag in td.tags)) {
                val r = put("i:${p.item}", p.item!!, ForecastKind.ITEM, null, i, p.pe!!)
                r.reason = p.tag
                r.first = min(r.first, i)
            }
        }
    }
    val list = rows.values
        .sortedWith(compareBy<Row> { it.first }.thenByDescending { it.cells.max() })
        .take(5)
        .map { ForecastRow(it.key, it.kind, it.dir, it.first, it.cells.toList(), it.reason) }
    return Forecast((0 until 7).map { today + it }, list, cyc.size)
}
