package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.core.Catalog
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.core.Mucus
import com.nordinary.cyclus.engine.CycleSnapshot
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

enum class DayMarker { NONE, OVULATION, PEAK }

data class CalendarCell(
    val day: Day,
    val dayOfMonth: Int,
    val inMonth: Boolean,
    val bleed: Boolean,
    val spotting: Boolean,
    val predicted: Boolean,
    val prePeriod: Boolean,
    /** 0 = none, 1–3 = fertility intensity. */
    val fertTier: Int,
    val marker: DayMarker,
    val today: Boolean,
    val logged: Boolean,
    /** Period start can be marked here (not in the future). */
    val canMarkStart: Boolean,
)

data class CalendarMonth(val year: Int, val month: Int, val firstDay: Day, val cells: List<CalendarCell>, val fertilityLegend: Boolean)

/** One row of the Day sheet, in the prototype's order. */
sealed interface DayEntry {
    data class FlowEntry(val flow: Flow) : DayEntry
    data class Symptoms(val keys: List<String>) : DayEntry
    data class Pain(val regions: List<Pair<String, Int>>) : DayEntry
    data class Feelings(val keys: List<String>) : DayEntry
    /** mood / energy / sleep / libido, value 1–5. */
    data class Scale(val key: String, val value: Int) : DayEntry
    data class Discharge(val mucus: Mucus) : DayEntry
    data class Lh(val result: LhResult) : DayEntry
    data class Temperature(val celsius: Double) : DayEntry
    data class Tags(val keys: List<String>) : DayEntry
    data class Note(val text: String) : DayEntry
}

data class DaySheetModel(
    val day: Day,
    val predictedPeriod: Boolean,
    /** Fertility tier (1–3) shown as a badge, 0 = none. */
    val fertTier: Int,
    val entries: List<DayEntry>,
    /** "Quelle: in Cyclus eingetragen" (shown when bleeding is logged). */
    val showSource: Boolean,
    val canEdit: Boolean,
)

object CalendarPresenter {
    private fun epoch(d: LocalDate): Day = d.toEpochDays().toInt()

    /** Month grid, Monday first, 5 or 6 weeks (prototype `renderCalendar`). */
    fun month(snap: CycleSnapshot, year: Int, month: Int): CalendarMonth {
        val today = snap.today
        val first = epoch(LocalDate(year, month, 1))
        val wd = (LocalDate.fromEpochDays(first).dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7
        val bleed = snap.starts.flatMap { snap.data.periodDays(it) }.toSet()
        val p = snap.prediction
        val f = if (snap.fertilityOn) snap.fertility else null
        val cells = mutableListOf<CalendarCell>()
        for (i in 0 until 42) {
            val day = first - wd + i
            val date = LocalDate.fromEpochDays(day)
            if (i >= 35 && LocalDate.fromEpochDays(first - wd + 35).month.number != month) break
            val l = snap.data.log(day)
            val isBleed = day in bleed
            val predicted = p != null && !snap.paused && day >= p.lo && day <= p.hi && day > today && !isBleed
            val pre = p != null && !snap.paused && day >= p.lo - 7 && day < p.lo && day >= today
            var tier = 0
            var marker = DayMarker.NONE
            if (f != null) {
                val cd = day - f.cs + 1
                val fd = if (cd >= 1) f.days.getOrNull(cd - 1) else null
                if (fd != null && fd.tier > 0 && !isBleed) tier = fd.tier
                if (cd == f.mapD) marker = DayMarker.OVULATION else if (fd != null && fd.tier == 3) marker = DayMarker.PEAK
            }
            cells += CalendarCell(
                day = day, dayOfMonth = date.day, inMonth = date.month.number == month,
                bleed = isBleed, spotting = !isBleed && l?.flow == Flow.SPOTTING, predicted = predicted, prePeriod = pre,
                fertTier = tier, marker = marker, today = day == today, logged = isLogged(l), canMarkStart = day <= today,
            )
        }
        return CalendarMonth(year, month, first, cells, f != null)
    }

    private fun isLogged(l: DayLog?) = l != null && (l.symptoms.isNotEmpty() || l.mood != null || l.lh != null || l.mucus != null || l.note != null)

    /** Day detail (prototype `renderDaySheet`). */
    /** [userLog]: the person's own entry (values from Health Connect are listed separately with their source). */
    fun daySheet(snap: CycleSnapshot, day: Day, userLog: DayLog? = snap.data.log(day)): DaySheetModel {
        val l = userLog
        val entries = mutableListOf<DayEntry>()
        if (l != null) {
            l.flow?.let { entries += DayEntry.FlowEntry(it) }
            if (l.symptoms.isNotEmpty()) entries += DayEntry.Symptoms(ordered(l.symptoms, Catalog.SYMPTOMS))
            val pain = Catalog.PAIN_REGIONS.mapNotNull { r -> l.pain[r]?.takeIf { it > 0 }?.let { r to it } }
            if (pain.isNotEmpty()) entries += DayEntry.Pain(pain)
            if (l.emotions.isNotEmpty()) entries += DayEntry.Feelings(ordered(l.emotions, Catalog.EMOTIONS))
            l.libido?.let { entries += DayEntry.Scale("libido", it) }
            l.mood?.let { entries += DayEntry.Scale("mood", it) }
            l.energy?.let { entries += DayEntry.Scale("energy", it) }
            l.sleep?.let { entries += DayEntry.Scale("sleep", it) }
            l.mucus?.let { entries += DayEntry.Discharge(it) }
            l.lh?.let { entries += DayEntry.Lh(it) }
            l.bbt?.let { entries += DayEntry.Temperature(it) }
            if (l.tags.isNotEmpty()) entries += DayEntry.Tags(ordered(l.tags, Catalog.TAGS))
            l.note?.takeIf { it.isNotBlank() }?.let { entries += DayEntry.Note(it) }
        }
        val p = snap.prediction
        val predicted = p != null && day > snap.today && !snap.paused && day >= p.lo && day <= p.hi
        var tier = 0
        val f = if (snap.fertilityOn) snap.fertility else null
        if (f != null) {
            val cd = day - f.cs + 1
            tier = (if (cd >= 1) f.days.getOrNull(cd - 1)?.tier else null) ?: 0
        }
        return DaySheetModel(day, predicted, tier, entries, l?.flow != null, day <= snap.today)
    }

    /** Catalog order first, unknown (user) keys after. */
    private fun ordered(keys: Set<String>, catalog: List<String>) = catalog.filter { it in keys } + keys.filter { it !in catalog }
}
