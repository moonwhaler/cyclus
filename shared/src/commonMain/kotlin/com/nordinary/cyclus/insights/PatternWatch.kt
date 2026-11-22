package com.nordinary.cyclus.insights

import com.nordinary.cyclus.core.Catalog
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Js
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.Learning
import com.nordinary.cyclus.engine.StudentT
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

enum class Area(val key: String) { BODY("body"), MOOD("mood"), LIFE("life"), FERT("fert"), HEALTH("health") }

enum class PatternStatus(val key: String) {
    CONSISTENT("consistent"), EARLY("early"), STABLE("stable"), NEEDS("needs"), NONE("none");

    /** A found pattern (as opposed to "watching" rows). */
    val isPattern: Boolean get() = this == CONSISTENT || this == EARLY
}

/** Cycle window: period days, 7 days before the next period, mid-cycle, rest. */
enum class Window(val key: String) { P("P"), PRE("PRE"), MIDC("MIDC"), REST("REST") }

enum class Direction(val key: String) { LO("lo"), HI("hi") }

/** One „Deine Muster“ entry (spec §11.12). */
data class Pattern(
    val id: String,
    val area: Area,
    val status: PatternStatus,
    val title: Txt,
    val body: Txt,
    val why: Txt? = null,
    val item: String? = null,
    val win: Window? = null,
    val scale: String? = null,
    val dir: Direction? = null,
    val tag: String? = null,
    /** Rate of the item on days after the tag (tag patterns). */
    val pe: Double? = null,
    /** Included in the Doctor Visit Pack. */
    val report: Boolean? = null,
)

internal data class CycleDayRef(val d: Day, val k: Int, val l: DayLog?, val rel: Int, val obs: Boolean, val win: Window)
internal data class PastCycle(val s: Day, val e: Day, val length: Int, val days: List<CycleDayRef>)

/** Finished cycles of plausible length with each day classified into a window (prototype `pastCycles`). */
internal fun CycleData.pastCycles(starts: List<Day>): List<PastCycle> {
    val out = mutableListOf<PastCycle>()
    for (i in 0 until starts.size - 1) {
        val s = starts[i]
        val e = starts[i + 1]
        val len = e - s
        if (len < 18 || len > 60) continue
        val pd = periodDays(s).toSet()
        val days = (s until e).map { d ->
            val k = d - s
            val l = logs[d]
            val win = when {
                d in pd -> Window.P
                d >= e - 7 -> Window.PRE
                k >= len - 16 && k <= len - 11 -> Window.MIDC
                else -> Window.REST
            }
            CycleDayRef(d, k, l, d - e, l?.isObserved == true, win)
        }
        out += PastCycle(s, e, len, days)
    }
    return out
}

private fun DayLog?.hasItem(item: String) = this != null && has(item)
private fun pct(x: Double) = Js.roundInt(x * 100)
private fun winTxt(w: Window) = txt("win_${w.key}")
private val WINDOWS = listOf(Window.P, Window.PRE, Window.MIDC)

/**
 * „Deine Muster“: per-person pattern detection, cycle as the unit, logged days only (spec §11.12, prototype
 * `patternWatch`). [learning] is the result of `learnFromHistory` for the same data (the prototype's `LEARN`).
 */
fun CycleData.patternWatch(starts: List<Day>, hasPrediction: Boolean, learning: Learning?): List<Pattern> {
    val out = mutableListOf<Pattern>()
    var seed = 7
    val cyc = if (hasPrediction) pastCycles(starts) else emptyList()
    if (cyc.size < 2) {
        out += Pattern("needs", Area.BODY, PatternStatus.NEEDS, txt("pwNeedsT"), txt("pwNeedsB", cyc.size, 2))
        return out
    }
    // 1) symptoms and feelings by cycle window
    val present = (Catalog.SYMPTOMS + Catalog.EMOTIONS).filter { x -> cyc.any { c -> c.days.any { it.l.hasItem(x) } } }
    data class Best(val x: String, val w: Window, val m: Int, val status: PatternStatus, val post: RiskRatio, val pin: Double, val pout: Double)
    val best = LinkedHashMap<String, Best>()
    for (x in present) for (w in WINDOWS) {
        var kin = 0; var nin = 0; var kout = 0; var nout = 0; var m = 0; var dir = 0; var sumN = 0
        for (c in cyc) {
            var a = 0; var na = 0; var b = 0; var nb = 0
            for (y in c.days) {
                if (!y.obs) continue
                val h = y.l.hasItem(x)
                if (y.win == w) { na++; if (h) a++ } else { nb++; if (h) b++ }
            }
            if (na >= 2 && nb >= 4) {
                m++; kin += a; nin += na; kout += b; nout += nb; sumN += na
                if (a.toDouble() / na > b.toDouble() / nb) dir++
            }
        }
        if (m < 2) continue
        val post = rrPost(kin, nin, kout, nout, 1 + (sumN.toDouble() / m - 1) * 0.3, seed++)
        val frac = dir.toDouble() / m
        val status = if (m >= 4 && post.p15 >= 0.9 && frac >= 0.75) PatternStatus.CONSISTENT
        else if (post.p125 >= 0.85 && frac >= 0.75) PatternStatus.EARLY else null
        val prev = best[x]
        if (status != null && (prev == null || post.q10 > prev.post.q10)) {
            best[x] = Best(x, w, m, status, post, kin.toDouble() / nin, kout.toDouble() / nout)
        }
    }
    best.values.sortedByDescending { it.post.q10 }.take(6).forEach { r ->
        out += Pattern(
            id = "it-${r.x}", area = if (Catalog.isEmotion(r.x)) Area.MOOD else Area.BODY, status = r.status,
            title = txt("itT", r.x, winTxt(r.w)), body = txt("itB", pct(r.pin), pct(r.pout), r.m),
            why = txt("itWhy", pct(r.post.p125)), item = r.x, win = r.w,
        )
    }
    // 2) mood, energy, sleep, libido by window (per-cycle mean differences)
    for (k in Catalog.SCALES) {
        data class B0(val w: Window, val dbar: Double, val status: PatternStatus, val a: Double, val b: Double, val n: Int, val same: Double)
        var b0: B0? = null
        for (w in WINDOWS) {
            val diffs = mutableListOf<Double>(); val aa = mutableListOf<Double>(); val bb = mutableListOf<Double>()
            for (c in cyc) {
                val a = mutableListOf<Double>(); val b = mutableListOf<Double>()
                for (y in c.days) {
                    val v = y.l?.scale(k) ?: continue
                    if (v == 0) continue
                    (if (y.win == w) a else b) += v.toDouble()
                }
                if (a.size >= 2 && b.size >= 4) { diffs += a.avg() - b.avg(); aa += a.avg(); bb += b.avg() }
            }
            if (diffs.size < 2) continue
            val dbar = diffs.avg()
            val same = diffs.count { Js.sign(it) == Js.sign(dbar) }.toDouble() / diffs.size
            val status = if (diffs.size >= 4 && abs(dbar) >= 0.5 && same >= 0.75) PatternStatus.CONSISTENT
            else if (abs(dbar) >= 0.5 && same >= 0.75) PatternStatus.EARLY else null
            if (status != null && (b0 == null || abs(dbar) > abs(b0.dbar))) b0 = B0(w, dbar, status, aa.avg(), bb.avg(), diffs.size, same)
        }
        out += if (b0 != null) {
            val lowHigh = txt(if (b0.dbar < 0) "lower" else "higher").copy(lowercase = true)
            Pattern(
                id = "sc-$k", area = Area.MOOD, status = b0.status,
                title = txt("scT", txt(k), lowHigh, winTxt(b0.w)), body = txt("scB", Num(b0.a, 1), Num(b0.b, 1), b0.n),
                why = txt("scWhy", Js.roundInt(b0.same * b0.n), b0.n),
                scale = k, win = b0.w, dir = if (b0.dbar < 0) Direction.LO else Direction.HI,
            )
        } else Pattern("sc-$k", Area.MOOD, PatternStatus.NONE, txt(k), txt("noPattern"))
    }
    // 3) lifestyle tags → symptoms/feelings on the next day. Exposures are rare, so the pattern must survive
    //    leaving out any single cycle; stricter threshold.
    data class Counts(val a: Int, val na: Int, val b: Int, val nb: Int)
    fun sum(arr: List<Counts>) = arr.fold(Counts(0, 0, 0, 0)) { o, q -> Counts(o.a + q.a, o.na + q.na, o.b + q.b, o.nb + q.nb) }
    data class TagRes(val tag: String, val x: String, val status: PatternStatus, val post: RiskRatio, val pe: Double, val pn: Double, val ne: Int, val m: Int)
    val tagRes = mutableListOf<TagRes>()
    for (tag in Catalog.TAGS) for (x in present) {
        val per = cyc.map { c ->
            var a = 0; var na = 0; var b = 0; var nb = 0
            for (y in c.days) {
                if (!y.obs) continue
                val pl = logs[y.d - 1]
                val h = y.l.hasItem(x)
                if (pl != null && tag in pl.tags) { na++; if (h) a++ } else { nb++; if (h) b++ }
            }
            Counts(a, na, b, nb)
        }
        val all = sum(per)
        val m = per.count { it.na > 0 }
        if (all.na < 6 || m < 3) continue
        val post = rrPost(all.a, all.na, all.b, all.nb, 1.2, seed++)
        if (post.p15 < 0.9) continue
        var robust = true
        for (i in per.indices) {
            if (per[i].na == 0) continue
            val o = sum(per.filterIndexed { j, _ -> j != i })
            if (rrPost(o.a, o.na, o.b, o.nb, 1.2, seed++).p125 < 0.8) { robust = false; break }
        }
        if (!robust) continue
        tagRes += TagRes(
            tag, x, if (m >= 4 && post.p15 >= 0.95) PatternStatus.CONSISTENT else PatternStatus.EARLY, post,
            all.a.toDouble() / all.na, all.b.toDouble() / all.nb, all.na, m,
        )
    }
    tagRes.sortedByDescending { it.post.q10 }.take(2).forEach { r ->
        out += Pattern(
            id = "tag-${r.tag}-${r.x}", area = Area.LIFE, status = r.status,
            title = txt("tagT", r.x, r.tag), body = txt("tagB", pct(r.pe), pct(r.pn), r.ne, r.m),
            why = txt("tagWhy", pct(r.post.p125)), item = r.x, tag = r.tag, pe = r.pe,
        )
    }
    // 4) stress → cycle length
    fun stressed(c: PastCycle) = c.days.count { it.k < 14 && it.l != null && Catalog.STRESS in it.l.tags } >= 3
    val ex = cyc.filter { stressed(it) }.map { it.length.toDouble() }
    val un = cyc.filter { !stressed(it) }.map { it.length.toDouble() }
    if (ex.size >= 2 && un.size >= 2 && ex.avg() - un.avg() >= 2) {
        out += Pattern(
            "stress-len", Area.LIFE,
            if (ex.size >= 3 && ex.min() > un.max()) PatternStatus.CONSISTENT else PatternStatus.EARLY,
            txt("stLenT"), txt("stLenB", Num(ex.avg(), 1), Num(un.avg(), 1), ex.size), txt("stWhy"),
        )
    }
    // 5) fertility, from retrospective per-cycle ovulation estimates (shown only with the fertility flag on)
    val g = learning?.good ?: emptyList()
    if (learning != null && g.size >= 2) {
        val os = g.map { it.ovulationDay }.sorted()
        val need = ceil(2.0 * os.size / 3).toInt()
        var wa = os.first(); var wb = os.last()
        var i = 0
        while (i + need - 1 < os.size) {
            if (os[i + need - 1] - os[i] < wb - wa) { wa = os[i]; wb = os[i + need - 1] }
            i++
        }
        out += Pattern(
            "f-ov", Area.FERT, if (g.size >= 4 && wb - wa <= 2) PatternStatus.CONSISTENT else PatternStatus.EARLY,
            txt("fovT", wa, wb), txt("fovB", os.count { it in wa..wb }, g.size), txt("fovWhy"),
        )
        val luts = g.map { it.length - it.ovulationDay }
        val lmin = luts.min(); val lmax = luts.max()
        out += Pattern(
            "f-lut", Area.FERT, if (lmax - lmin <= 2 && lmin >= 10) PatternStatus.STABLE else PatternStatus.EARLY,
            txt("flutT", lmin, lmax),
            if (lmin < 10) txt("flutShort", luts.count { it < 10 }) else txt("flutB", g.size),
            report = lmin < 10,
        )
        val lags = g.mapNotNull { it.lag }.sorted()
        if (lags.size >= 2) out += Pattern(
            "f-lh", Area.FERT, if (lags.size >= 4) PatternStatus.CONSISTENT else PatternStatus.EARLY,
            txt("flhT", lags[lags.size / 2]), txt("flhB", lags.size),
        )
        val mus = g.mapNotNull { it.ewFirst }.map { -it }.sorted()
        if (mus.size >= 2) out += Pattern(
            "f-mucus", Area.FERT, if (mus.size >= 4) PatternStatus.CONSISTENT else PatternStatus.EARLY,
            txt("fmuT", mus[mus.size / 2]), txt("fmuB"),
        )
        val gx = g.filter { it.stress }.map { it.ovulationDay.toDouble() }
        val gu = g.filter { !it.stress }.map { it.ovulationDay.toDouble() }
        if (gx.size >= 2 && gu.size >= 2 && gx.avg() - gu.avg() >= 2) out += Pattern(
            "f-stress", Area.FERT, PatternStatus.EARLY, txt("fstT"), txt("fstB", Num(gx.avg(), 1), Num(gu.avg(), 1), gx.size), txt("stWhy"),
        )
        val tr = learning.cycles.filter { it.tracked }
        if (tr.size >= 2) {
            val k = tr.count { it.shift }
            out += Pattern(
                "f-temp", Area.FERT, if (k == tr.size) PatternStatus.STABLE else PatternStatus.EARLY,
                txt("ftempT", k, tr.size), txt("ftempB"), report = k < tr.size,
            )
        }
    } else out += Pattern("f-needs", Area.FERT, PatternStatus.NEEDS, txt("fneedsT"), txt("fneedsB"))
    // 6) cycle health trends: neutral wording, never pushed, included in the Doctor Visit Pack
    val lens = cyc.map { it.length.toDouble() }
    if (lens.size >= 6) {
        val r = ols(lens)
        val q = StudentT.q90(max(3, lens.size - 2).toDouble())
        out += if (r.b - q * r.se > 0 || r.b + q * r.se < 0) Pattern(
            "c-len", Area.HEALTH, PatternStatus.EARLY, txt(if (r.b < 0) "clenShort" else "clenLong"),
            txt("clenTB", Js.roundInt(r.first), Js.roundInt(r.last), lens.size), txt("trendWhy"), report = true,
        ) else Pattern(
            "c-len", Area.HEALTH, PatternStatus.STABLE, txt("clenST", lens.min().toInt(), lens.max().toInt()), txt("clenSB", lens.size),
        )
    }
    val pains = cyc.map { c ->
        c.days.filter { it.win == Window.P && it.l != null }
            .maxOfOrNull { y -> max(0, y.l!!.pain.values.maxOrNull() ?: 0) }?.let { max(0, it) } ?: 0
    }.filter { it > 0 }.map { it.toDouble() }
    if (pains.size >= 6) {
        val r = ols(pains)
        val q = StudentT.q90(max(3, pains.size - 2).toDouble())
        out += if (r.b - q * r.se > 0) Pattern(
            "c-pain", Area.HEALTH, if (pains.size >= 8) PatternStatus.CONSISTENT else PatternStatus.EARLY, txt("painUpT"),
            txt("painUpB", Num(r.first, 0), Num(r.last, 0), pains.size), txt("trendWhy"), report = true,
        ) else Pattern("c-pain", Area.HEALTH, PatternStatus.STABLE, txt("painST"), txt("painSB", pains.size))
    }
    val pl = cyc.map { periodDays(it.s).size }
    out += Pattern("c-plen", Area.HEALTH, PatternStatus.STABLE, txt("plenT", pl.min(), pl.max()), txt("plenB", pl.size))
    return out
}
