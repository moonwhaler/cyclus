package com.nordinary.cyclus

import com.nordinary.cyclus.core.CycleOverride
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.core.Mucus
import com.nordinary.cyclus.core.OverrideKind
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.engine.Mode
import com.nordinary.cyclus.insights.Num
import com.nordinary.cyclus.insights.Txt
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.fail

/**
 * Golden vectors: the Kotlin engines must reproduce the prototype's engines (tools/golden/export.mjs).
 * Integers, enums, statuses and text keys must match exactly; doubles to a relative 1e-9.
 */
class GoldenTest {
    @Test fun named() = run("named.json")
    @Test fun sweep() = run("sweep.json")
    @Test fun fuzz() = run("fuzz.json")

    /** The demo-data generator (debug builds) reproduces the prototype's scenarios log for log. */
    @Test fun sampleScenariosMatchPrototype() {
        val cases = Json.parseToJsonElement(readGolden("named.json")).jsonObject["cases"]!!.jsonArray
        val errors = mutableListOf<String>()
        for (key in SampleScenario.entries) {
            val case = cases.first { it.jsonObject.str("name") == key.key }.jsonObject
            val today = case["today"]!!.jsonPrimitive.int
            val expected = case["logs"]!!.jsonArray.associate { e -> e.jsonArray[0].jsonPrimitive.int to parseLog(e.jsonArray[1].jsonObject) }
            val got = SampleScenarios.generate(key, today)
            if (got.logs.keys != expected.keys) errors += "${key.key}: days differ (${got.logs.size} vs ${expected.size})"
            for ((d, l) in expected) if (got.logs[d] != l) errors += "${key.key} day $d: expected $l, got ${got.logs[d]}"
            val mode = if (case.str("mode") == "conceive") Mode.CONCEIVE else Mode.TRACK
            if (got.mode != mode) errors += "${key.key}: mode ${got.mode}"
            if ((got.pausedSince != null) != (case["episode"] !is JsonNull)) errors += "${key.key}: episode"
        }
        if (errors.isNotEmpty()) fail(errors.take(20).joinToString("\n"))
    }

    private fun run(file: String) {
        val cases = Json.parseToJsonElement(readGolden(file)).jsonObject["cases"]!!.jsonArray
        val failures = mutableListOf<String>()
        for (c in cases) {
            val case = c.jsonObject
            val name = case.str("name")
            val cmp = Cmp(name)
            try {
                check(case, cmp)
            } catch (e: Throwable) {
                cmp.fail("", "exception: $e")
            }
            failures += cmp.errors
        }
        println("golden $file: ${cases.size} cases, ${failures.size} mismatches")
        if (failures.isNotEmpty()) fail("${failures.size} golden mismatches in $file:\n" + failures.take(40).joinToString("\n"))
    }

    private fun check(case: JsonObject, cmp: Cmp) {
        val logs = case["logs"]!!.jsonArray.associate { e -> e.jsonArray[0].jsonPrimitive.int to parseLog(e.jsonArray[1].jsonObject) }
        val overrides = case["overrides"]!!.jsonArray.associate { e ->
            e.jsonArray[0].jsonPrimitive.int to CycleOverride(OverrideKind.of(e.jsonArray[1].jsonPrimitive.content))
        }
        val data = CycleData(logs, overrides, case["today"]!!.jsonPrimitive.int)
        val snap = CycleSnapshot.compute(
            data,
            mode = if (case.str("mode") == "conceive") Mode.CONCEIVE else Mode.TRACK,
            showFertilityInTrack = case["showFertTrack"]!!.jsonPrimitive.booleanOrNull == true,
            paused = case["episode"] !is JsonNull,
        )
        val out = case["out"]!!.jsonObject
        cmp.eq("cycleStarts", out["cycleStarts"], data.cycleStarts)

        val p = snap.prediction
        val jp = out["predict"]!!
        if (jp is JsonNull) { if (p != null) cmp.fail("predict", "expected null"); return }
        jp as JsonObject
        if (p == null) { cmp.fail("predict", "got null"); return }
        cmp.eq("predict.starts", jp["starts"], p.starts); cmp.eq("predict.lens", jp["lens"], p.lens)
        cmp.eq("predict.used", jp["used"], p.used); cmp.eq("predict.last", jp["last"], p.last)
        cmp.eq("predict.lo", jp["lo"], p.lo); cmp.eq("predict.hi", jp["hi"], p.hi)
        cmp.eq("predict.neff", jp["neff"], p.neff); cmp.eq("predict.mun", jp["mun"], p.mun)
        cmp.eq("predict.scale", jp["scale"], p.scale); cmp.eq("predict.df", jp["df"], p.df)
        cmp.eq("predict.loL", jp["loL"], p.loL); cmp.eq("predict.hiL", jp["hiL"], p.hiL)
        cmp.eq("predict.tier", jp["tier"], p.tier.key); cmp.eq("predict.typLo", jp["typLo"], p.typLo); cmp.eq("predict.typHi", jp["typHi"], p.typHi)
        cmp.eq("predict.likely", jp["likely"], p.likely)

        val vm = out["vm"]!!.jsonObject
        cmp.eq("vm.paused", vm["paused"], snap.paused); cmp.eq("vm.cd", vm["cd"], snap.cycleDay)
        cmp.eq("vm.inPeriod", vm["inPeriod"], snap.inPeriod); cmp.eq("vm.periodDay", vm["periodDay"], snap.periodDay)
        cmp.eq("vm.status", vm["status"], snap.status?.key); cmp.eq("vm.fertOn", vm["fertOn"], snap.fertilityOn)
        cmp.eq("periodDaysLast", out["periodDaysLast"], data.periodDays(p.last))

        val jl = out["learn"]!!.jsonObject
        val learn = snap.learning!!
        cmp.eq("learn.n", JsonPrimitive(jl["cycles"]!!.jsonArray.size), learn.cycles.size)
        jl["cycles"]!!.jsonArray.zip(learn.cycles).forEachIndexed { i, (j, c) ->
            val o = j.jsonObject; val pre = "learn.cycles[$i]"
            cmp.eq("$pre.cs", o["cs"], c.cs); cmp.eq("$pre.L", o["L"], c.length); cmp.eq("$pre.O", o["O"], c.ovulationDay)
            cmp.eq("$pre.hpd", o["hpd"], listOf(c.hpd.first, c.hpd.second)); cmp.eq("$pre.good", o["good"], c.good)
            cmp.eq("$pre.lag", o["lag"], c.lag); cmp.eq("$pre.ewFirst", o["ewFirst"], c.ewFirst)
            cmp.eq("$pre.shift", o["shift"], c.shift); cmp.eq("$pre.tracked", o["tracked"], c.tracked); cmp.eq("$pre.stress", o["stress"], c.stress)
        }
        cmp.eq("learn.good", jl["good"], learn.good.map { it.cs })
        val lut = jl["lut"]!!.jsonObject
        cmp.eq("learn.lut.mean", lut["mean"], learn.lut.mean); cmp.eq("learn.lut.sd", lut["sd"], learn.lut.sd)
        cmp.eq("learn.lut.lo", lut["lo"], learn.lut.lo); cmp.eq("learn.lut.hi", lut["hi"], learn.lut.hi); cmp.eq("learn.lut.learned", lut["learned"], learn.lut.learned)

        val jf = out["fertility"]!!.jsonObject
        val f = snap.fertility!!
        cmp.eq("fert.cs", jf["cs"], f.cs); cmp.eq("fert.todayCd", jf["todayCd"], f.todayCd); cmp.eq("fert.Dmax", jf["Dmax"], f.dMax)
        for (e in jf["post"]!!.jsonArray) { val d = e.jsonArray[0].jsonPrimitive.int; cmp.eq("fert.post[$d]", e.jsonArray[1], f.post[d]) }
        cmp.eq("fert.pA", jf["pA"], f.pA)
        jf["days"]!!.jsonArray.zip(f.days).forEach { (j, d) ->
            val o = j.jsonObject
            cmp.eq("fert.days[${d.cd}].cd", o["cd"], d.cd); cmp.eq("fert.days[${d.cd}].pf", o["pf"], d.pf)
            cmp.eq("fert.days[${d.cd}].c", o["c"], d.c); cmp.eq("fert.days[${d.cd}].tier", o["tier"], d.tier)
        }
        cmp.eq("fert.days.size", JsonPrimitive(jf["days"]!!.jsonArray.size), f.days.size)
        cmp.eq("fert.band", jf["band"], f.band?.let { listOf(it.first, it.second) })
        cmp.eq("fert.mapD", jf["mapD"], f.mapD); cmp.eq("fert.hpd", jf["hpd"], listOf(f.hpd.first, f.hpd.second))
        cmp.eq("fert.status", jf["status"], f.status.key)
        val js = jf["shift"]!!
        if (js is JsonObject) { cmp.eq("fert.shift.cd", js["cd"], f.shift?.cd); cmp.eq("fert.shift.cover", js["cover"], f.shift?.cover) }
        else if (f.shift != null) cmp.fail("fert.shift", "expected null, got ${f.shift}")
        cmp.eq("fert.ev", jf["ev"], f.ev.map { mapOf("type" to it.type.key, "cd" to it.cd) })
        cmp.eq("fert.bbt", jf["bbt"], f.bbt.map { mapOf("cd" to it.cd, "v" to it.v) })
        cmp.eq("fert.planStart", jf["planStart"], f.planStart); cmp.eq("fert.chip", jf["chip"], f.chip.key)

        val trail = snap.trail()
        cmp.eq("trail.size", JsonPrimitive(out["trail"]!!.jsonArray.size), trail.size)
        out["trail"]!!.jsonArray.zip(trail).forEachIndexed { i, (j, t) ->
            val o = j.jsonObject
            cmp.eq("trail[$i].cd", o["cd"], t.cd); cmp.eq("trail[$i].band", o["band"], t.band?.let { listOf(it.first, it.second) })
            cmp.eq("trail[$i].changed", o["changed"], t.changed); cmp.eq("trail[$i].events", o["events"], t.events.map { it.key })
        }

        val jpats = out["patterns"]!!.jsonArray
        val pats = snap.patterns
        cmp.eq("patterns.ids", JsonArray(jpats.map { it.jsonObject["id"]!! }), pats.map { it.id })
        jpats.zip(pats).forEach { (j, pt) ->
            val o = j.jsonObject; val pre = "pattern[${pt.id}]"
            cmp.eq("$pre.area", o["area"], pt.area.key); cmp.eq("$pre.status", o["status"], pt.status.key)
            cmp.eq("$pre.item", o["item"], pt.item); cmp.eq("$pre.win", o["win"], pt.win?.key)
            cmp.eq("$pre.scale", o["scale"], pt.scale); cmp.eq("$pre.dir", o["dir"], pt.dir?.key)
            cmp.eq("$pre.tag", o["tag"], pt.tag); cmp.eq("$pre.pe", o["pe"], pt.pe); cmp.eq("$pre.report", o["report"], pt.report)
            cmp.eq("$pre.title", o["title"], pt.title); cmp.eq("$pre.body", o["body"], pt.body); cmp.eq("$pre.why", o["why"], pt.why)
        }

        val jfc = out["forecast"]
        val fc = snap.forecast
        if (jfc == null || jfc is JsonNull) { if (fc != null) cmp.fail("forecast", "expected null, got $fc") }
        else if (fc == null) cmp.fail("forecast", "got null")
        else {
            val o = jfc.jsonObject
            cmp.eq("forecast.days", o["days"], fc.days); cmp.eq("forecast.n", o["n"], fc.cycles)
            cmp.eq("forecast.rows", o["rows"], fc.rows.map { r ->
                buildMap<String, Any?> {
                    put("k", r.key); put("kind", r.kind.name.lowercase()); r.dir?.let { put("dir", it.key) }
                    put("first", r.first); put("cells", r.cells); r.reason?.let { put("reason", it) }
                }
            })
        }
    }

}

internal fun parseLog(o: JsonObject): DayLog = DayLog(
        flow = o.strOrNull("flow")?.let { Flow.of(it) },
        symptoms = o["symptoms"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet(),
        pain = o["pain"]?.jsonObject?.mapValues { it.value.jsonPrimitive.int } ?: emptyMap(),
        emotions = o["emo"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet(),
        tags = o["tags"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet(),
        mood = o["mood"]?.jsonPrimitive?.intOrNull, energy = o["energy"]?.jsonPrimitive?.intOrNull,
        sleep = o["sleep"]?.jsonPrimitive?.intOrNull, libido = o["libido"]?.jsonPrimitive?.intOrNull,
        mucus = o.strOrNull("mucus")?.let { Mucus.of(it) }, lh = o.strOrNull("lh")?.let { LhResult.of(it) },
        bbt = o["bbt"]?.jsonPrimitive?.doubleOrNull, sex = o["sex"]?.jsonPrimitive?.booleanOrNull == true,
        note = o.strOrNull("note"), nothingElse = o["nothingElse"]?.jsonPrimitive?.booleanOrNull == true,
    )

private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
private fun JsonObject.strOrNull(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Structural comparison of a golden JSON value with a Kotlin value. */
private class Cmp(val case: String) {
    val errors = mutableListOf<String>()
    fun fail(path: String, msg: String) { errors += "[$case] $path: $msg" }

    fun eq(path: String, j: JsonElement?, k: Any?) {
        val je = j ?: JsonNull
        when {
            k == null -> if (je !is JsonNull) fail(path, "expected $je, got null")
            je is JsonNull -> fail(path, "expected null, got $k")
            k is Txt -> {
                if (je !is JsonObject) return fail(path, "expected $je, got $k")
                eq("$path.k", je["k"], k.key)
                eq("$path.lower", je["lower"] ?: JsonPrimitive(false), k.lowercase)
                eq("$path.a", je["a"], k.args)
            }
            k is Num -> {
                if (je !is JsonObject) return fail(path, "expected $je, got $k")
                eq("$path.num", je["num"], k.value); eq("$path.d", je["d"], k.decimals)
            }
            k is Map<*, *> -> {
                if (je !is JsonObject) return fail(path, "expected $je, got $k")
                val keys = je.keys + k.keys.map { it.toString() }
                for (key in keys) eq("$path.$key", je[key], k[key])
            }
            k is List<*> || k is DoubleArray || k is IntArray -> {
                val list = when (k) { is DoubleArray -> k.toList(); is IntArray -> k.toList(); else -> k as List<*> }
                if (je !is JsonArray) return fail(path, "expected $je, got $list")
                if (je.size != list.size) return fail(path, "size ${je.size} vs ${list.size}: expected $je, got $list")
                je.zip(list).forEachIndexed { i, (a, b) -> eq("$path[$i]", a, b) }
            }
            k is Boolean -> if ((je as? JsonPrimitive)?.booleanOrNull != k) fail(path, "expected $je, got $k")
            k is String -> if ((je as? JsonPrimitive)?.content != k || !je.isString) fail(path, "expected $je, got \"$k\"")
            k is Number -> {
                val expected = (je as? JsonPrimitive)?.let { p ->
                    when (p.content) { "NaN" -> Double.NaN; "Inf" -> Double.POSITIVE_INFINITY; "-Inf" -> Double.NEGATIVE_INFINITY; else -> p.doubleOrNull }
                } ?: return fail(path, "expected $je, got $k")
                val got = k.toDouble()
                val ok = if (k is Int || k is Long) expected == got
                else (expected.isNaN() && got.isNaN()) || expected == got || abs(expected - got) <= 1e-9 * max(1.0, max(abs(expected), abs(got)))
                if (!ok) fail(path, "expected $expected, got $got")
            }
            else -> fail(path, "unsupported type ${k::class}")
        }
    }
}
