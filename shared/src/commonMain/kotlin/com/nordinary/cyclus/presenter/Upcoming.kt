package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.engine.OvulationStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class UpcomingKind { PRE, FERTILE, OVULATION, PERIOD, REMINDER }

/**
 * One row of „Demnächst“. [from]–[to] is the span (a single day when [to] is null); for the period [likely] is the
 * most likely start inside the range. An item stays while its end is today or later; once [from] has passed it is
 * „ongoing“ (shown as „jetzt bis …“).
 */
data class UpcomingItem(
    val kind: UpcomingKind,
    val from: Day,
    val to: Day? = null,
    val likely: Day? = null,
    val reminder: NotifyKind? = null,
) {
    val end: Day get() = to ?: from
    fun ongoing(today: Day) = from <= today && end >= today && to != null
}

object Upcoming {
    const val MAX = 4

    /** Reminders worth listing: the others repeat daily or only echo a row that is already there. */
    private val LISTED = setOf(NotifyKind.LATER, NotifyKind.BACKUP)

    /** From the engine (no settings beyond the snapshot's): nothing while paused or without a prediction. */
    fun items(snap: CycleSnapshot): List<UpcomingItem> {
        val p = snap.prediction ?: return emptyList()
        if (snap.paused) return emptyList()
        val today = snap.today
        val out = mutableListOf<UpcomingItem>()
        out += UpcomingItem(UpcomingKind.PRE, p.lo - 7, p.lo - 1)
        val f = if (snap.fertilityOn) snap.fertility else null
        if (f != null) {
            f.band?.let { (a, b) -> out += UpcomingItem(UpcomingKind.FERTILE, f.dateOf(a), f.dateOf(b)) }
            if (f.status != OvulationStatus.OCCURRED) out += UpcomingItem(UpcomingKind.OVULATION, f.dateOf(f.mapD))
        }
        out += UpcomingItem(UpcomingKind.PERIOD, p.lo, p.hi, likely = p.likely)
        return out.filter { it.end >= today }.sortedBy { maxOf(it.from, today) }
    }

    /** The next listed reminder (local day), only once the person allowed notifications. */
    fun nextReminder(snap: CycleSnapshot, settings: Map<String, String>, now: Instant, tz: TimeZone): UpcomingItem? {
        val prefs = NotifyPrefs.from(settings)
        if (!prefs.consent) return null
        val plan = NotificationPlanner.plan(snap, prefs, ExportStats.from(settings).lastDay)
        return plan.items.mapNotNull { item ->
            val kind = NotifyKind.of(item.kind)?.takeIf { it in LISTED } ?: return@mapNotNull null
            NotifySchedule.nextOf(item, now, tz)?.let { t -> kind to t.toLocalDateTime(tz).date.toEpochDays().toInt() }
        }.minByOrNull { it.second }?.let { (kind, day) -> UpcomingItem(UpcomingKind.REMINDER, day, reminder = kind) }
    }

    /** Engine items plus the reminder, in date order, at most [MAX]. */
    fun merge(items: List<UpcomingItem>, reminder: UpcomingItem?, today: Day): List<UpcomingItem> =
        (items + listOfNotNull(reminder)).sortedBy { maxOf(it.from, today) }.take(MAX)
}
