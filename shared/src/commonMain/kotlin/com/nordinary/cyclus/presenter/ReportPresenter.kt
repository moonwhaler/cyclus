package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.Episode
import com.nordinary.cyclus.core.EpisodeKind
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.insights.Area
import com.nordinary.cyclus.insights.Pattern
import com.nordinary.cyclus.insights.Window
import com.nordinary.cyclus.insights.pastCyclesPublic
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.math.max

/** Sections of the Doctor Visit Pack (spec §7.7). Sensitive ones are off by default. */
enum class ReportSection(val key: String, val defaultOn: Boolean) {
    BLEED("bleed", true), LENGTHS("lengths", true), SYMPTOMS("sym", true), PAIN("pain", true), PATTERNS("patterns", true),
    MOOD("mood", true), TEMP("temp", false), NOTES("notes", false), CONTRA("contra", false), SEX("sex", false);

    val sensitive get() = this == NOTES || this == CONTRA || this == SEX
}

data class ReportOptions(
    val cycles: Int = 6,
    val include: Set<ReportSection> = ReportSection.entries.filter { it.defaultOn }.toSet(),
    val questions: String = "",
    /** Optional, printed on the summary page instead of the blank „Name: ____“ line. */
    val name: String = "",
)

data class Spread(val count: Int, val median: Int, val min: Int, val max: Int)
data class MonthGrid(val year: Int, val month: Int, val firstDay: Day, val days: Int, val flow: List<Flow?>)
data class CyclePain(val start: Day, val maxPain: Int)
data class WindowMeans(val period: Double?, val pre: Double?, val rest: Double?)
data class DatedNote(val day: Day, val text: String)
data class CycleCount(val start: Day, val days: Int)

/** Everything that goes into the PDF; rendering is per platform. Null = no data (row omitted). */
data class ReportModel(
    val from: Day,
    val to: Day,
    /** Finished cycles used (may be fewer than requested). */
    val cyclesUsed: Int,
    val cyclesRequested: Int,
    val include: Set<ReportSection>,
    val cycleLengths: Spread?,
    val periodLengths: Spread?,
    /** Bleeding days by level (LIGHT, MEDIUM, HEAVY, UNSPECIFIED). */
    val heaviness: Map<Flow, Int>,
    val months: List<MonthGrid>,
    /** Top 4 symptoms by logged days since [from], with counts. */
    val topSymptoms: List<Pair<String, Int>>,
    val heat: HeatMap?,
    val painPerCycle: List<CyclePain>,
    val painMedian: Int?,
    val mood: WindowMeans?,
    val energy: WindowMeans?,
    val patterns: List<Pattern>,
    /** Cycles with ≥ 3 temperatures, and how many of them show a temperature rise. */
    val tempCycles: Int,
    val tempShifts: Int,
    val notes: List<DatedNote>,
    val contraception: List<Episode>,
    val sexDays: List<CycleCount>,
    val questions: String,
    val name: String = "",
)

object ReportPresenter {
    /** At least one finished cycle is needed for a meaningful report. */
    fun available(snap: CycleSnapshot) = snap.starts.size >= 2

    fun build(snap: CycleSnapshot, options: ReportOptions, muted: Set<String>, episodes: List<Episode>): ReportModel? {
        val starts = snap.starts
        if (starts.size < 2) return null
        val data = snap.data
        val inc = options.include
        val n = starts.size
        // prototype: from = starts[max(0, n − range − 1)]
        val from = starts[max(0, n - options.cycles - 1)]
        val to = snap.today
        val finished = (0 until n - 1).map { starts[it] to starts[it + 1] }.filter { it.first >= from }
        fun spread(values: List<Int>) = if (values.isEmpty()) null else values.sorted().let { Spread(it.size, it[it.size / 2], it.first(), it.last()) }
        val lens = finished.map { (s, e) -> e - s }
        val periodLens = finished.map { (s, _) -> data.periodDays(s).size }.filter { it > 0 }

        val heaviness = LinkedHashMap<Flow, Int>()
        var months = emptyList<MonthGrid>()
        if (ReportSection.BLEED in inc) {
            for (d in from..to) data.log(d)?.flow?.takeIf { it.isBleed }?.let { heaviness[it] = (heaviness[it] ?: 0) + 1 }
            months = monthsBetween(from, to).map { (y, m) ->
                val first = LocalDate(y, m, 1).toEpochDays().toInt()
                val nextFirst = (if (m == 12) LocalDate(y + 1, 1, 1) else LocalDate(y, m + 1, 1)).toEpochDays().toInt()
                MonthGrid(y, m, first, nextFirst - first, (first until nextFirst).map { d -> data.log(d)?.flow?.takeIf { it != Flow.NONE } })
            }
        }

        val counts = LinkedHashMap<String, Int>()
        for (d in from..to) data.log(d)?.symptoms?.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val top = if (ReportSection.SYMPTOMS in inc) counts.entries.sortedByDescending { it.value }.take(4).map { it.key to it.value } else emptyList()
        val periodStartsInRange = starts.filter { it > from && it <= to }
        val heat = if (ReportSection.SYMPTOMS in inc && periodStartsInRange.isNotEmpty())
            HeatMap(InsightsPresenter.HEAT_SYMPTOMS, InsightsPresenter.HEAT_REL, InsightsPresenter.heatRates(snap, periodStartsInRange)) else null

        val pain = if (ReportSection.PAIN in inc) starts.filter { it in from..to }.mapNotNull { s ->
            val m = data.periodDays(s).maxOfOrNull { d -> data.log(d)?.pain?.values?.maxOrNull() ?: 0 } ?: 0
            if (m > 0) CyclePain(s, m) else null
        } else emptyList()

        var mood: WindowMeans? = null; var energy: WindowMeans? = null
        if (ReportSection.MOOD in inc) {
            val cyc = data.pastCyclesPublic(starts).filter { it.s >= from }
            fun means(key: String): WindowMeans? {
                val by = mutableMapOf<Window, MutableList<Int>>()
                for (c in cyc) for (y in c.days) y.l?.scale(key)?.takeIf { it > 0 }?.let { by.getOrPut(if (y.win == Window.MIDC) Window.REST else y.win) { mutableListOf() } += it }
                if (by.isEmpty()) return null
                fun avg(w: Window) = by[w]?.takeIf { it.isNotEmpty() }?.average()
                return WindowMeans(avg(Window.P), avg(Window.PRE), avg(Window.REST))
            }
            mood = means("mood"); energy = means("energy")
        }

        val patterns = if (ReportSection.PATTERNS in inc) snap.patterns
            .filter { it.status.isPattern && (it.area != Area.FERT || snap.fertilityOn) && it.id !in muted }
            .sortedByDescending { it.report == true }.take(5) else emptyList()

        var tempCycles = 0; var tempShifts = 0
        if (ReportSection.TEMP in inc) snap.learning?.cycles?.filter { it.cs >= from }?.forEach { c ->
            if (c.tracked) { tempCycles++; if (c.shift) tempShifts++ }
        }

        val notes = if (ReportSection.NOTES in inc) (from..to).mapNotNull { d -> data.log(d)?.note?.takeIf { it.isNotBlank() }?.let { DatedNote(d, it) } } else emptyList()
        val contra = if (ReportSection.CONTRA in inc) episodes.filter {
            it.kind == EpisodeKind.HORMONAL_CONTRACEPTION && it.start <= to && (it.end ?: to) >= from
        } else emptyList()
        val sex = if (ReportSection.SEX in inc) finished.map { (s, e) -> CycleCount(s, (s until e).count { data.log(it)?.sex == true }) } else emptyList()

        return ReportModel(
            from = from, to = to, cyclesUsed = finished.size, cyclesRequested = options.cycles, include = inc,
            cycleLengths = if (ReportSection.LENGTHS in inc) spread(lens) else null,
            periodLengths = if (ReportSection.LENGTHS in inc) spread(periodLens) else null,
            heaviness = heaviness, months = months, topSymptoms = top, heat = heat,
            painPerCycle = pain, painMedian = pain.map { it.maxPain }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] },
            mood = mood, energy = energy, patterns = patterns, tempCycles = tempCycles, tempShifts = tempShifts,
            notes = notes, contraception = contra, sexDays = sex, questions = options.questions.trim(), name = options.name.trim().take(80),
        )
    }

    private fun monthsBetween(from: Day, to: Day): List<Pair<Int, Int>> {
        val a = LocalDate.fromEpochDays(from); val b = LocalDate.fromEpochDays(to)
        val out = mutableListOf<Pair<Int, Int>>()
        var y = a.year; var m = a.month.number
        while (y < b.year || (y == b.year && m <= b.month.number)) {
            out += y to m
            if (m == 12) { m = 1; y++ } else m++
        }
        return out
    }

}
