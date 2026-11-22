package com.nordinary.cyclus.sample

import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.Js
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.core.Mucus
import com.nordinary.cyclus.core.Rng
import com.nordinary.cyclus.engine.Mode
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Demo data (debug builds only): the prototype's scenarios, generated relative to [today]. */
enum class SampleScenario(val key: String) {
    EMPTY("empty"), DAY12("day12"), EXPECTED("expected"), PERIOD("period"), HBC("hbc"),
    CONCEIVE("conceive"), CONCEIVE_2WW("conceive2ww");
}

data class SampleData(val logs: Map<Day, DayLog>, val mode: Mode, val pausedSince: Day?)

/**
 * Port of the prototype's `genScenario` with the same seeded RNG, so the generated logs equal the golden logs.
 * Planted patterns: cramps on period days 1–2 with slowly rising pain; headache before the period and after
 * alcohol; bloating, breast tenderness, irritability before the period; worse sleep and less energy before the
 * period; more energy, calm and libido mid-cycle; stressful first halves lengthen the cycle.
 */
object SampleScenarios {
    private val STRESS_CYCLES = listOf(3, 6)
    private val FLOW_PATTERN = listOf(Flow.MEDIUM, Flow.HEAVY, Flow.MEDIUM, Flow.LIGHT, Flow.LIGHT, Flow.SPOTTING)

    fun generate(scenario: SampleScenario, today: Day): SampleData {
        val logs = LinkedHashMap<Day, DayLog>()
        if (scenario == SampleScenario.EMPTY) return SampleData(logs, Mode.TRACK, null)
        val lens = listOf(29, 28, 30, 31, 28, 29, 32, 29)
        val plen = listOf(5, 4, 5, 6, 5, 4, 5, 5)
        val cur = today + when (scenario) {
            SampleScenario.DAY12 -> -11; SampleScenario.EXPECTED -> -27; SampleScenario.PERIOD -> -1
            SampleScenario.HBC -> -11; SampleScenario.CONCEIVE -> -13; SampleScenario.CONCEIVE_2WW -> -17
            SampleScenario.EMPTY -> 0
        }
        val conceive = scenario == SampleScenario.CONCEIVE || scenario == SampleScenario.CONCEIVE_2WW
        val starts = ArrayDeque<Day>()
        var s = cur
        for (i in lens.indices.reversed()) { s -= lens[i]; starts.addFirst(s) }
        val rng = Rng(11)
        fun r() = rng.next()
        fun p(x: Double) = r() < x
        fun rint(base: Int, span: Int) = base + floor(r() * span).toInt()
        fun set(day: Day, fn: (DayLog) -> DayLog) { logs[day] = fn(logs[day] ?: DayLog.EMPTY) }

        fun addDay(day: Day, k: Int, len: Int, pl: Int, ci: Int) {
            val toNext = len - k
            val mid = k >= len - 16 && k <= len - 11
            val obs = k < pl || r() < 0.6
            val flow = if (k < pl) FLOW_PATTERN[k] else null
            if (!obs) { if (flow != null) logs[day] = DayLog(flow = flow); return }
            val prev = logs[day - 1]
            val tags = mutableListOf<String>(); val symptoms = mutableListOf<String>(); val emo = mutableListOf<String>()
            val pain = LinkedHashMap<String, Int>()
            if (p(0.16)) tags += "Alcohol"
            if (ci in STRESS_CYCLES && k >= 5 && k <= 13 && p(0.6)) tags += "Stress"
            if (p(0.1)) tags += "Sport"
            if (p(if (k <= 1) 0.85 else if (toNext <= 1) 0.4 else 0.04)) {
                symptoms += "Cramps"
                pain["lowC"] = min(10, max(1, Js.roundInt(2 + ci * 0.55 + r() * 2)))
            }
            val afterAlc = prev != null && "Alcohol" in prev.tags
            if (p(if (afterAlc) 0.65 else if (toNext in 1..2) 0.45 else 0.06)) symptoms += "Headache"
            if (p(if (k <= 2 || toNext <= 3) 0.55 else 0.14)) symptoms += "Tired"
            if (p(if (toNext in 1..5) 0.5 else 0.07)) symptoms += "Bloating"
            if (p(if (toNext in 2..6) 0.38 else 0.03)) symptoms += "Breast tenderness"
            if (p(if (toNext in 1..4) 0.3 else 0.04)) symptoms += "Acne"
            if (p(if (k <= 1) 0.35 else 0.03)) symptoms += "Back pain"
            if (p(if (toNext in 1..5) 0.45 else 0.07)) emo += "Irritable"
            if (p(if (toNext in 1..3) 0.28 else 0.05)) emo += "Sad"
            if (p(if (mid) 0.5 else 0.15)) emo += "Calm"
            if (p(if (mid) 0.38 else 0.12)) emo += "Happy"
            val mood = if (toNext <= 3) rint(2, 2) else if (mid) rint(4, 2) else rint(3, 2)
            val energy = if (k <= 1 || toNext <= 3) rint(2, 2) else if (mid) rint(4, 2) else rint(3, 2)
            val sleep = if (r() < 0.75) (if (toNext in 1..7) rint(1, 3) else rint(3, 3)) else null
            val libido = if (r() < 0.6) (if (mid) rint(4, 2) else if (k < pl) rint(1, 2) else rint(2, 2)) else null
            val nothingElse = r() < 0.35
            logs[day] = DayLog(
                flow = flow, symptoms = symptoms.toSet(), pain = pain, emotions = emo.toSet(), tags = tags.toSet(),
                mood = mood, energy = energy, sleep = sleep, libido = libido, nothingElse = nothingElse,
            )
        }

        starts.forEachIndexed { i, st -> for (k in 0 until lens[i]) addDay(st + k, k, lens[i], plen[i], i) }
        if (conceive) { // past cycles with LH tests, mucus and basal temperature (ovulation = L − luteal length)
            val luts = listOf(2 to 13, 3 to 13, 4 to 13, 5 to 14, 6 to 13, 7 to 12)
            for ((ci, lut) in luts) {
                val st = starts[ci]; val len = lens[ci]; val o = len - lut
                for (cd in 3..len) {
                    val hi = cd > o
                    set(st + cd - 1) { it.copy(bbt = Js.round(((if (hi) 36.68 else 36.30) + r() * (if (hi) 0.12 else 0.15)) * 100) / 100) }
                }
                for (cd in o - 4..o) set(st + cd - 1) { it.copy(lh = if (cd == o - 1) LhResult.POSITIVE else LhResult.NEGATIVE) }
                for (cd in o - 5..o + 3) set(st + cd - 1) {
                    it.copy(mucus = if (cd <= o - 4) Mucus.CREAMY else if (cd <= o - 1) Mucus.EGGWHITE else Mucus.STICKY)
                }
            }
        }
        for (day in cur..today) {
            val k = day - cur
            if (conceive) {
                val flow = if (k < 5) FLOW_PATTERN[k] else null
                logs[day] = if (k == 0 || k == 1) DayLog(flow = flow, symptoms = setOf("Cramps"), pain = mapOf("lowC" to 6))
                else DayLog(flow = flow)
            } else addDay(day, k, 29, 5, 8)
        }
        if (conceive) {
            fun cd(n: Int) = cur + n - 1
            val bbt = if (scenario == SampleScenario.CONCEIVE)
                listOf(36.38, 36.31, 36.42, 36.35, 36.30, 36.44, 36.36, 36.33, 36.41, 36.37, 36.29, 36.40)
            else listOf(36.38, 36.31, 36.42, 36.35, 36.30, 36.44, 36.36, 36.33, 36.41, 36.37, 36.29, 36.40, 36.38, 36.62, 36.70, 36.72)
            bbt.forEachIndexed { i, v -> set(cd(3 + i)) { it.copy(bbt = v) } }
            for (n in 11..13) set(cd(n)) { it.copy(lh = LhResult.NEGATIVE) }
            set(cd(11)) { it.copy(mucus = Mucus.CREAMY) }; set(cd(12)) { it.copy(mucus = Mucus.CREAMY) }; set(cd(13)) { it.copy(mucus = Mucus.EGGWHITE) }
            if (scenario == SampleScenario.CONCEIVE_2WW) {
                set(cd(14)) { it.copy(lh = LhResult.POSITIVE, mucus = Mucus.EGGWHITE, sex = true) }
                set(cd(15)) { it.copy(lh = LhResult.NEGATIVE, mucus = Mucus.CREAMY) }
                set(cd(16)) { it.copy(mucus = Mucus.STICKY) }; set(cd(17)) { it.copy(mucus = Mucus.STICKY) }
                set(cd(12)) { it.copy(sex = true) }; set(cd(13)) { it.copy(sex = true) }
            }
        }
        return SampleData(
            logs,
            if (conceive) Mode.CONCEIVE else Mode.TRACK,
            if (scenario == SampleScenario.HBC) today - 70 else null,
        )
    }
}
