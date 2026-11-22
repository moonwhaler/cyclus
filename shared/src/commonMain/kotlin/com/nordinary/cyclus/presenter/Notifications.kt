package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.engine.EvidenceType
import com.nordinary.cyclus.engine.Mode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Channels (ANDROID_PLAN §6.3); iOS has no channels and ignores this. */
enum class NotifyChannel { PERIOD, FERTILITY, REMINDERS, BACKUP }

/** Local notifications (spec §8.3). [defaultOn] applies once the user has opted in. */
enum class NotifyKind(val key: String, val channel: NotifyChannel, val defaultOn: Boolean, val conceiveOnly: Boolean, val defaultMinute: Int) {
    PERIOD_SOON("periodSoon", NotifyChannel.PERIOD, true, false, 9 * 60),
    LATER("later", NotifyChannel.PERIOD, false, false, 9 * 60),
    DAILY_LOG("daily", NotifyChannel.REMINDERS, false, false, 20 * 60),
    BACKUP("backup", NotifyChannel.BACKUP, false, false, 10 * 60),
    LH_TEST("lh", NotifyChannel.FERTILITY, true, true, 8 * 60),
    WINDOW_OPENS("window", NotifyChannel.FERTILITY, true, true, 9 * 60),
    BBT_MORNING("bbt", NotifyChannel.REMINDERS, false, true, 6 * 60 + 30),
    ;

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

/** Device settings (never exported, not in device snapshots: a restore starts without reminders until the user opts in again). */
object NotifyKeys {
    const val CONSENT = "notify.consent"
    /** The Today offer was answered (yes or no); it isn't shown again. */
    const val ASKED = "notify.asked"
    const val DISCREET = "notify.discreet"
    const val DAILY_TIME = "notify.dailyTime"
    /** Day the backup reminder was switched on; base for the monthly reminder when nothing was exported yet. */
    const val BACKUP_SINCE = "notify.backupSince"
    fun kind(k: NotifyKind) = "notify.k.${k.key}"
    const val WIDGET_DETAILS = "widget.details"
    const val SHORTCUT = "shortcut.log"
}

data class NotifyPrefs(
    val consent: Boolean = false,
    val enabled: Set<NotifyKind> = NotifyKind.entries.filter { it.defaultOn }.toSet(),
    val dailyMinute: Int = NotifyKind.DAILY_LOG.defaultMinute,
    val discreet: Boolean = true,
    val backupSince: Day? = null,
) {
    companion object {
        fun from(settings: Map<String, String>) = NotifyPrefs(
            consent = settings[NotifyKeys.CONSENT] == "1",
            enabled = NotifyKind.entries.filter { k -> settings[NotifyKeys.kind(k)]?.let { it == "1" } ?: k.defaultOn }.toSet(),
            dailyMinute = settings[NotifyKeys.DAILY_TIME]?.toIntOrNull()?.takeIf { it in 0 until 24 * 60 } ?: NotifyKind.DAILY_LOG.defaultMinute,
            discreet = settings[NotifyKeys.DISCREET] != "0",
            backupSince = settings[NotifyKeys.BACKUP_SINCE]?.toIntOrNull(),
        )
    }
}

/**
 * One planned reminder. `every == 0`: once on [day]. Otherwise repeats every [every] days from [day] up to [until]
 * (inclusive, open-ended when null). [minute] is local wall-clock time, resolved in the zone current at arming time.
 */
@Serializable
data class NotifyItem(val kind: String, val day: Int, val minute: Int, val every: Int = 0, val until: Int? = null)

/** What the DB-free alarm side needs: no symptoms, no notes, only reminder kinds and days. */
@Serializable
data class NotifyPlan(val items: List<NotifyItem> = emptyList(), val discreet: Boolean = true) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val EMPTY = NotifyPlan()
        fun decode(text: String): NotifyPlan = runCatching { json.decodeFromString(serializer(), text) }.getOrDefault(EMPTY)
    }
}

object NotificationPlanner {
    fun plan(snap: CycleSnapshot, prefs: NotifyPrefs, lastExport: Day?): NotifyPlan {
        if (!prefs.consent) return NotifyPlan.EMPTY.copy(discreet = prefs.discreet)
        val today = snap.today
        val items = mutableListOf<NotifyItem>()
        fun on(k: NotifyKind) = k in prefs.enabled && (!k.conceiveOnly || snap.mode == Mode.CONCEIVE)
        fun once(k: NotifyKind, day: Day) { if (day >= today) items += NotifyItem(k.key, day, k.defaultMinute) }

        val p = snap.prediction
        if (p != null && !snap.paused) {
            if (on(NotifyKind.PERIOD_SOON)) once(NotifyKind.PERIOD_SOON, p.lo - 2)
            if (on(NotifyKind.LATER)) once(NotifyKind.LATER, p.hi + 1)
            val f = if (snap.fertilityOn) snap.fertility else null
            if (f != null) {
                if (on(NotifyKind.WINDOW_OPENS)) f.band?.let { once(NotifyKind.WINDOW_OPENS, f.dateOf(it.first) - 1) }
                // daily LH tests from the planner start until a positive test (replanned on every log) or ovulation + 2
                if (on(NotifyKind.LH_TEST) && f.ev.none { it.type == EvidenceType.LH_POS }) {
                    val from = maxOf(today, f.dateOf(f.planStart))
                    val until = f.dateOf(f.hpd.second) + 2
                    if (from <= until) items += NotifyItem(NotifyKind.LH_TEST.key, from, NotifyKind.LH_TEST.defaultMinute, every = 1, until = until)
                }
            }
            if (on(NotifyKind.BBT_MORNING)) items += NotifyItem(NotifyKind.BBT_MORNING.key, today, NotifyKind.BBT_MORNING.defaultMinute, every = 1)
        }
        if (on(NotifyKind.DAILY_LOG)) items += NotifyItem(NotifyKind.DAILY_LOG.key, today, prefs.dailyMinute, every = 1)
        if (on(NotifyKind.BACKUP)) {
            val base = lastExport ?: prefs.backupSince ?: today
            items += NotifyItem(NotifyKind.BACKUP.key, base + 30, NotifyKind.BACKUP.defaultMinute, every = 30)
        }
        return NotifyPlan(items, prefs.discreet)
    }
}

/** Fire-time math for the alarm side (DST-safe: a wall-clock time in a gap moves forward, like the system clock). */
object NotifySchedule {
    /** Missed reminders older than this are dropped instead of shown late (e.g. phone was off). */
    val STALE = 12.hours

    fun instantOf(day: Int, minute: Int, tz: TimeZone): Instant =
        LocalDateTime(LocalDate.fromEpochDays(day), LocalTime(minute / 60, minute % 60)).toInstant(tz)

    private fun localDay(t: Instant, tz: TimeZone) = t.toLocalDateTime(tz).date.toEpochDays().toInt()

    /** First occurrence of [item] strictly after [after]. */
    fun nextOf(item: NotifyItem, after: Instant, tz: TimeZone): Instant? {
        if (item.every <= 0) return instantOf(item.day, item.minute, tz).takeIf { it > after }
        val d0 = localDay(after, tz)
        var k = maxOf(0, (d0 - 1 - item.day) / item.every)
        repeat(4) {
            val d = item.day + k * item.every
            if (item.until != null && d > item.until) return null
            val t = instantOf(d, item.minute, tz)
            if (t > after) return t
            k++
        }
        return null
    }

    /** Next alarm: earliest reminder, or the next local midnight when widgets need a refresh. */
    fun nextFire(plan: NotifyPlan, after: Instant, tz: TimeZone, midnight: Boolean): Instant? {
        val times = plan.items.mapNotNull { nextOf(it, after, tz) }.toMutableList()
        if (midnight) times += LocalDate.fromEpochDays(localDay(after, tz) + 1).atStartOfDayIn(tz)
        return times.minOrNull()
    }

    /** Kinds due in (lastFired, now], ignoring occurrences older than [STALE]; one notification per kind. */
    fun due(plan: NotifyPlan, lastFired: Instant?, now: Instant, tz: TimeZone): List<NotifyKind> {
        val from = listOfNotNull(lastFired, now - STALE).max()
        return plan.items.filter { item -> nextOf(item, from, tz)?.let { it <= now } == true }
            .mapNotNull { NotifyKind.of(it.kind) }.distinct()
    }
}
