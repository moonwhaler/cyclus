package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Js
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.engine.Mode
import com.nordinary.cyclus.engine.OvulationStatus
import com.nordinary.cyclus.engine.Tier
import com.nordinary.cyclus.insights.Area
import com.nordinary.cyclus.insights.Pattern
import com.nordinary.cyclus.insights.PatternStatus
import kotlin.math.max

data class CycleBar(val length: Int, val start: Day, val latest: Boolean)
data class TempPoint(val cd: Int, val celsius: Double, val afterShift: Boolean)
data class TemperatureChart(val points: List<TempPoint>, val shiftCd: Int?, val cover: Double?, val ovulationDay: Day?, val maxCd: Int)
data class HeatMap(val symptoms: List<String>, val rel: List<Int>, val rates: List<List<Double>>)
data class HistoryRow(val start: Day, val end: Day, val length: Int, val periodLength: Int)

/** „Deine Muster“ section (prototype `patternSection`). */
data class PatternSection(val found: List<Pattern>, val watching: List<Pattern>, val hiddenCount: Int)

sealed interface InsightsUiState {
    data object Empty : InsightsUiState
    data class Ready(
        val typLo: Int,
        val typHi: Int,
        /** Typical period length (prototype: sorted[floor(n/2)]), null before the first finished cycle. */
        val periodTypical: Int?,
        /** "Your cycles vary by about N days". */
        val spread: Int,
        val tier: Tier,
        val cyclesUsed: Int,
        val patterns: PatternSection,
        val bars: List<CycleBar>,
        val temperature: TemperatureChart?,
        val heat: HeatMap,
        val history: List<HistoryRow>,
    ) : InsightsUiState
}

object InsightsPresenter {
    val HEAT_SYMPTOMS = listOf("Cramps", "Headache", "Bloating", "Tired", "Breast tenderness", "Acne", "Back pain")
    val HEAT_REL = (-7..4).toList()

    fun present(snap: CycleSnapshot, muted: Set<String>): InsightsUiState {
        val p = snap.prediction ?: return InsightsUiState.Empty
        if (p.used.isEmpty()) return InsightsUiState.Empty
        val starts = snap.starts
        val data = snap.data

        val plens = starts.dropLast(1).map { data.periodDays(it).size }.sorted()
        val pmed = plens.getOrNull(plens.size / 2)
        val spread = max(1, Js.roundInt((p.hiL - p.loL) / 2))

        val bars = p.lens.takeLast(8)
        val startsUsed = starts.takeLast(bars.size + 1)
        val barModels = bars.mapIndexed { i, l -> CycleBar(l, startsUsed[i], i == bars.size - 1) }

        val f = if (snap.fertilityOn) snap.fertility else null
        val temperature = if (f != null && f.bbt.size >= 3) {
            TemperatureChart(
                points = f.bbt.map { TempPoint(it.cd, it.v, f.shift != null && it.cd >= f.shift.cd) },
                shiftCd = f.shift?.cd, cover = f.shift?.cover, ovulationDay = if (f.shift != null) f.dateOf(f.mapD) else null,
                maxCd = max(28, f.todayCd),
            )
        } else null

        val rates = heatRates(snap, starts.drop(1))

        val history = starts.takeLast(7).dropLast(1).reversed().map { s ->
            val nx = starts[starts.indexOf(s) + 1]
            HistoryRow(s, nx - 1, nx - s, data.periodDays(s).size)
        }

        return InsightsUiState.Ready(
            typLo = p.typLo, typHi = p.typHi, periodTypical = pmed, spread = spread, tier = p.tier, cyclesUsed = p.used.size,
            patterns = patternSection(snap, muted), bars = barModels, temperature = temperature,
            heat = HeatMap(HEAT_SYMPTOMS, HEAT_REL, rates), history = history,
        )
    }

    /**
     * Symptom heatmap: share of observed days (nothing-else, any symptom or any flow) with the symptom, by day
     * relative to each period start in [periodStarts] (prototype `renderInsights`).
     */
    fun heatRates(snap: CycleSnapshot, periodStarts: List<Day>): List<List<Double>> {
        fun observedForHeat(l: DayLog?) = l != null && (l.nothingElse || l.symptoms.isNotEmpty() || l.flow != null)
        return HEAT_SYMPTOMS.map { s ->
            HEAT_REL.map { r ->
                var h = 0; var n = 0
                for (st in periodStarts) {
                    val l = snap.data.log(st + r)
                    if (observedForHeat(l)) { n++; if (s in l!!.symptoms) h++ }
                }
                if (n > 0) h.toDouble() / n else 0.0
            }
        }
    }

    fun patternSection(snap: CycleSnapshot, muted: Set<String>): PatternSection {
        val all = snap.patterns.filter { it.area != Area.FERT || snap.fertilityOn }
        val vis = all.filter { it.id !in muted }
        val order = mapOf(
            Area.FERT to if (snap.mode == Mode.CONCEIVE) 0 else 3,
            Area.BODY to 1, Area.MOOD to 2, Area.LIFE to 4, Area.HEALTH to 5,
        )
        val found = vis.filter { it.status.isPattern }
            .sortedWith(compareBy<Pattern> { if (it.status == PatternStatus.CONSISTENT) 0 else 1 }.thenBy { order.getValue(it.area) })
        return PatternSection(found, vis.filter { !it.status.isPattern }, all.size - vis.size)
    }
}

/** One step of „Warum dieses Fenster?“ (prototype `trail` + `renderWhySheet`). */
data class WhyStep(
    /** 0 for the calendar prior, else the cycle day of the new evidence. */
    val cd: Int,
    val date: Day?,
    val events: List<com.nordinary.cyclus.engine.EvidenceType>,
    val window: Pair<Day, Day>?,
    /** For later steps: false means the step confirmed the previous window. */
    val changed: Boolean?,
)

data class WhyModel(
    val cyclesUsed: Int,
    val lutealMean: Double,
    /** Number of own cycles the luteal length was learned from (0 = population value). */
    val lutealLearned: Int,
    val steps: List<WhyStep>,
    val occurred: Boolean,
    val ovulationDay: Day,
    val window: Pair<Day, Day>,
)

object WhyPresenter {
    fun present(snap: CycleSnapshot): WhyModel? {
        val p = snap.prediction ?: return null
        val f = (if (snap.fertilityOn) snap.fertility else null) ?: return null
        val steps = snap.trail().map { s ->
            WhyStep(
                cd = s.cd, date = if (s.cd > 0) f.dateOf(s.cd) else null, events = s.events,
                window = s.band?.let { s.result.dateOf(it.first) to s.result.dateOf(it.second) }, changed = s.changed,
            )
        }
        return WhyModel(
            cyclesUsed = p.used.size, lutealMean = snap.lut.mean, lutealLearned = snap.lut.learned, steps = steps,
            occurred = f.status == OvulationStatus.OCCURRED, ovulationDay = f.dateOf(f.mapD),
            window = f.dateOf(f.hpd.first) to f.dateOf(f.hpd.second),
        )
    }
}
