package com.nordinary.cyclus.health

import com.nordinary.cyclus.core.CycleOverride
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.core.Mucus
import com.nordinary.cyclus.engine.CycleData

/** Health Connect / HealthKit record kinds Cyclus maps (spec §11.4.2, ANDROID_PLAN §6.1). */
enum class HealthType(val key: String) {
    FLOW("flow"), PERIOD("period"), SPOTTING("spotting"), BBT("bbt"), MUCUS("mucus"), LH("lh"), SEX("sex");

    companion object { fun of(key: String) = entries.firstOrNull { it.key == key } }
}

/** Write toggles in the Privacy Center; every one is off until the user switches it on (decision 2026-11-22). */
enum class HealthCategory(val key: String, val types: List<HealthType>) {
    BLEEDING("bleeding", listOf(HealthType.FLOW, HealthType.PERIOD)),
    SPOTTING("spotting", listOf(HealthType.SPOTTING)),
    TEMPERATURE("temp", listOf(HealthType.BBT)),
    MUCUS("mucus", listOf(HealthType.MUCUS)),
    LH("lh", listOf(HealthType.LH)),
    SEX("sex", listOf(HealthType.SEX)),
    ;

    companion object {
        fun ofType(t: HealthType) = entries.first { t in it.types }
    }
}

/** Device settings (never exported). */
object HealthKeys {
    /** The user connected (granted reads at least once). */
    const val CONNECTED = "hc.connected"
    fun write(c: HealthCategory) = "hc.w.${c.key}"
    fun token(t: HealthType) = "hc.token.${t.key}"
    const val LAST_SYNC = "hc.lastSync"
    const val ERROR = "hc.error"
    /** Cyclus wrote at least once (erase offers to delete those records). */
    const val WROTE = "hc.wrote"
    /** Only the last 30 days could be read (history permission missing or unsupported). */
    const val NO_HISTORY = "hc.noHistory"
    const val PREFIX = "cyclus:"

    fun enabled(settings: Map<String, String>) = HealthCategory.entries.filter { settings[write(it)] == "1" }.toSet()
}

/** A record from another app (read-only in Cyclus). For [HealthType.PERIOD] the value is the end day. */
data class HealthSample(val recordId: String, val type: HealthType, val day: Day, val value: String, val origin: String, val updatedAt: Long)

/** Precedence among external sources (spec §11.4.1 rule 4; Android equivalents of Apple Health / Watch). */
object HealthOrigins {
    private val trusted = setOf(
        "com.sec.android.app.shealth", "com.google.android.apps.fitness", "com.fitbit.FitbitMobile",
        "com.google.android.apps.healthdata", "com.google.android.healthconnect.controller",
    )
    fun rank(pkg: String) = if (pkg in trusted) 1 else 2
}

/** One external value on a day, for the day sheet (source badge). [used]: counted (no user entry, best source). */
data class ExternalValue(val type: HealthType, val value: String, val origin: String, val used: Boolean)

/**
 * User entries + external samples → what the engine sees. Computed before `CycleData`, so the engine and its golden
 * vectors are unchanged. User entry > trusted sources > other apps, per (day, field); flow is never counted twice.
 * A user `flow = none` hides an external flow; other external values can't be hidden yet (documented).
 */
object HealthMerge {
    private fun best(list: List<HealthSample>) =
        list.minWithOrNull(compareBy<HealthSample> { HealthOrigins.rank(it.origin) }.thenByDescending { it.updatedAt })

    private fun periodDays(samples: List<HealthSample>): Set<Day> = samples.filter { it.type == HealthType.PERIOD }
        .flatMap { s -> val end = s.value.toIntOrNull() ?: s.day; (s.day..maxOf(s.day, minOf(end, s.day + 14))).toList() }.toSet()

    fun effectiveLogs(user: Map<Day, DayLog>, samples: List<HealthSample>): Map<Day, DayLog> {
        if (samples.isEmpty()) return user
        val byDay = samples.filter { it.type != HealthType.PERIOD }.groupBy { it.day }
        val inPeriod = periodDays(samples)
        val out = user.toMutableMap()
        for (d in byDay.keys + inPeriod) {
            val s = byDay[d].orEmpty()
            fun of(t: HealthType) = best(s.filter { it.type == t })
            val base = user[d] ?: DayLog.EMPTY
            val flow = base.flow
                ?: of(HealthType.FLOW)?.let { runCatching { Flow.of(it.value) }.getOrNull() }
                ?: of(HealthType.SPOTTING)?.let { Flow.SPOTTING }
                ?: if (d in inPeriod) Flow.UNSPECIFIED else null
            val sex = of(HealthType.SEX)
            val merged = base.copy(
                flow = flow,
                bbt = base.bbt ?: of(HealthType.BBT)?.value?.toDoubleOrNull(),
                mucus = base.mucus ?: of(HealthType.MUCUS)?.let { runCatching { Mucus.of(it.value) }.getOrNull() },
                lh = base.lh ?: of(HealthType.LH)?.let { runCatching { LhResult.of(it.value) }.getOrNull() },
                sex = base.sex || sex != null,
                sexProtected = if (base.sex) base.sexProtected else sex?.let { protectedOf(it.value) },
            )
            if (merged != base || user.containsKey(d)) out[d] = merged
        }
        return out
    }

    fun dayDetail(day: Day, user: DayLog?, samples: List<HealthSample>): List<ExternalValue> {
        val onDay = samples.filter { it.day == day && it.type != HealthType.PERIOD }
        val u = user ?: DayLog.EMPTY
        fun userHas(t: HealthType) = when (t) {
            HealthType.FLOW, HealthType.SPOTTING, HealthType.PERIOD -> u.flow != null
            HealthType.BBT -> u.bbt != null
            HealthType.MUCUS -> u.mucus != null
            HealthType.LH -> u.lh != null
            HealthType.SEX -> u.sex
        }
        return onDay.groupBy { it.type }.flatMap { (t, list) ->
            val b = best(list)
            val flowTaken = t == HealthType.SPOTTING && onDay.any { it.type == HealthType.FLOW }
            list.map { ExternalValue(t, it.value, it.origin, used = it === b && !userHas(t) && !flowTaken) }
        }
    }

    fun protectedOf(v: String): Boolean? = when (v) { "p" -> true; "u" -> false; else -> null }
}

/** A record Cyclus wants in Health Connect. [hash] is the content; a change means an upsert. */
data class OwnRecord(val syncId: String, val type: HealthType, val day: Day, val value: String) {
    val hash: String get() = value
}

enum class LinkState(val key: String) {
    OK("ok"), DELETING("deleting"), REMOVED("removed"),
    /** Reviewed: deleted in Health Connect, kept only in Cyclus; never written again. */
    KEPT("kept");

    companion object { fun of(k: String) = entries.firstOrNull { it.key == k } ?: OK }
}

/** A record Cyclus wrote (`health_link`). */
data class HealthLink(val syncId: String, val type: HealthType, val day: Day, val recordId: String?, val version: Long, val hash: String, val state: LinkState)

data class WritePlan(
    /** Record + clientRecordVersion to write (always above what was written before). */
    val upserts: List<Pair<OwnRecord, Long>>,
    /** Delete in Health Connect first, then drop the link (a failed delete keeps a `deleting` tombstone). */
    val deletes: List<HealthLink>,
    /** Drop locally only (removed in Health Connect and no longer in Cyclus either). */
    val forget: List<HealthLink>,
) {
    val isEmpty get() = upserts.isEmpty() && deletes.isEmpty() && forget.isEmpty()
}

object HealthPlanner {
    fun syncId(t: HealthType, day: Day) = "${HealthKeys.PREFIX}${t.key}:$day"

    /** What Cyclus should have in Health Connect: from the **user's own** entries only, enabled categories only. */
    fun desired(user: Map<Day, DayLog>, overrides: Map<Day, CycleOverride>, enabled: Set<HealthCategory>, today: Day): List<OwnRecord> {
        if (enabled.isEmpty()) return emptyList()
        val types = enabled.flatMap { it.types }.toSet()
        val out = mutableListOf<OwnRecord>()
        fun add(t: HealthType, d: Day, v: String) { if (t in types && d <= today) out += OwnRecord(syncId(t, d), t, d, v) }
        for ((d, l) in user) {
            l.flow?.let { f ->
                if (f.isBleed) add(HealthType.FLOW, d, f.key)
                if (f == Flow.SPOTTING) add(HealthType.SPOTTING, d, "1")
            }
            l.bbt?.let { add(HealthType.BBT, d, it.toString()) }
            l.mucus?.let { add(HealthType.MUCUS, d, it.key) }
            l.lh?.let { add(HealthType.LH, d, it.key) }
            if (l.sex) add(HealthType.SEX, d, when (l.sexProtected) { true -> "p"; false -> "u"; null -> "?" })
        }
        if (HealthType.PERIOD in types) {
            val data = CycleData(user, overrides, today)
            for (s in data.cycleStarts) {
                val days = data.periodDays(s)
                if (days.isNotEmpty()) add(HealthType.PERIOD, s, days.max().toString())
            }
        }
        return out
    }

    fun plan(desired: List<OwnRecord>, links: Map<String, HealthLink>, now: Long): WritePlan {
        val want = desired.associateBy { it.syncId }
        val upserts = desired.mapNotNull { r ->
            val l = links[r.syncId]
            when {
                l?.state == LinkState.REMOVED || l?.state == LinkState.KEPT -> null // review pending / kept in Cyclus only
                l == null || l.hash != r.hash || l.state == LinkState.DELETING -> r to maxOf((l?.version ?: 0L) + 1, now)
                else -> null
            }
        }
        val gone = links.values.filter { it.syncId !in want }
        val localOnly = setOf(LinkState.REMOVED, LinkState.KEPT)
        return WritePlan(upserts, gone.filter { it.state !in localOnly }, gone.filter { it.state in localOnly })
    }

    /**
     * Reinstall recovery (rule 7): Cyclus' own records become user entries — **only into an empty store**, so nothing
     * the user deleted (or kept out of Health Connect) can come back.
     */
    fun reconstruct(user: Map<Day, DayLog>, own: List<OwnRecord>): Map<Day, DayLog> {
        if (user.isNotEmpty()) return emptyMap()
        val out = mutableMapOf<Day, DayLog>()
        for (r in own.sortedBy { it.type.ordinal }) {
            val l = out[r.day] ?: DayLog.EMPTY
            out[r.day] = when (r.type) {
                HealthType.FLOW -> runCatching { l.copy(flow = Flow.of(r.value)) }.getOrDefault(l)
                HealthType.SPOTTING -> if (l.flow == null) l.copy(flow = Flow.SPOTTING) else l
                HealthType.BBT -> l.copy(bbt = r.value.toDoubleOrNull())
                HealthType.MUCUS -> runCatching { l.copy(mucus = Mucus.of(r.value)) }.getOrDefault(l)
                HealthType.LH -> runCatching { l.copy(lh = LhResult.of(r.value)) }.getOrDefault(l)
                HealthType.SEX -> l.copy(sex = true, sexProtected = HealthMerge.protectedOf(r.value))
                HealthType.PERIOD -> l
            }
        }
        return out.filterValues { it != DayLog.EMPTY }
    }
}
