package com.nordinary.cyclus.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Temperature
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.nordinary.cyclus.backup.BackupCoordinator
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.store.CyclusRepository
import com.nordinary.cyclus.store.StoreHolder
import com.nordinary.cyclus.store.StoreState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.reflect.KClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class HealthAvailability { AVAILABLE, NEEDS_INSTALL, UNAVAILABLE }

/**
 * Health Connect import + opt-in write (spec §11.4, ANDROID_PLAN §6.1). Runs only while the app is open; never
 * scheduled. Reads happen outside the erase lock; applying them (page + token in one transaction) and every write
 * happen inside it, for the repository that is still current.
 */
class HealthSync(private val ctx: Context) {
    val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(ctx) }

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(ctx)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_INSTALL
        else -> HealthAvailability.UNAVAILABLE
    }

    suspend fun granted(): Set<String> = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    fun historySupported(): Boolean = runCatching {
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    /** Read set requested at import (plus full history where Health Connect supports it). */
    fun readPermissions(): Set<String> = HealthType.entries.map { HealthPermission.getReadPermission(recordClass(it)) }.toSet() +
        (if (historySupported()) setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY) else emptySet())

    fun writePermissions(c: HealthCategory): Set<String> = c.types.map { HealthPermission.getWritePermission(recordClass(it)) }.toSet()

    data class Result(val imported: Int, val written: Int, val deleted: Int, val error: String? = null)

    /** One sync pass. [full] re-reads all history (also done automatically when a changes token expired). */
    suspend fun sync(backup: BackupCoordinator, store: StoreHolder, repo: CyclusRepository, full: Boolean = false): Result = withContext(Dispatchers.IO) {
        fun current() = (store.state.value as? StoreState.Ready)?.repo === repo
        if (availability() != HealthAvailability.AVAILABLE || !current()) return@withContext Result(0, 0, 0)
        if (repo.setting(HealthKeys.CONNECTED) != "1") return@withContext Result(0, 0, 0)
        val granted = granted()
        var imported = 0
        val own = mutableListOf<Pair<OwnRecord, Long>>()
        try {
            // ---- read (outside the lock); Health Connect only allows reads while the app is in the foreground
            val foreground = withContext(Dispatchers.Main) { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
            if (foreground) for (t in HealthType.entries) {
                if (HealthPermission.getReadPermission(recordClass(t)) !in granted) continue
                val key = HealthKeys.token(t)
                val token = repo.setting(key)
                if (token == null || full) imported += fullRead(backup, repo, t, own, ::current)
                else imported += changes(backup, repo, t, token, own, ::current)
            }
            // ---- reinstall recovery is offered, never automatic (it could bring back what was erased); links for own
            // records of enabled categories, so „lassen“ stays left alone
            if (own.isNotEmpty()) backup.exclusive {
                if (!current()) return@exclusive
                val days = HealthPlanner.reconstruct(emptyMap(), own.map { it.first }).size
                val offer = days > 0 && !repo.hasLogs() && repo.setting(HealthKeys.RESTORE_DECLINED) != "1"
                repo.setSetting(HealthKeys.RESTORE_OFFER, if (offer) days.toString() else null)
                val enabled = HealthKeys.enabled(repo.observeSettings().first()).flatMap { it.types }.toSet()
                val links = repo.healthLinks()
                for ((r, v) in own) if (r.type in enabled && r.syncId !in links) {
                    repo.upsertHealthLink(HealthLink(r.syncId, r.type, r.day, null, v, r.hash, LinkState.OK))
                }
            }
            // ---- write (inside the lock)
            val w = write(backup, repo, granted, ::current)
            repo.setSetting(HealthKeys.LAST_SYNC, System.currentTimeMillis().toString())
            repo.setSetting(HealthKeys.ERROR, null)
            Result(imported, w.first, w.second)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            runCatching { if (current()) repo.setSetting(HealthKeys.ERROR, e.javaClass.simpleName) }
            Result(imported, 0, 0, e.javaClass.simpleName)
        }
    }

    private suspend fun fullRead(
        backup: BackupCoordinator, repo: CyclusRepository, t: HealthType, own: MutableList<Pair<OwnRecord, Long>>, current: () -> Boolean,
    ): Int {
        // token first, so nothing written during the read is missed
        val token = client.getChangesToken(ChangesTokenRequest(setOf(recordClass(t))))
        val samples = mutableListOf<HealthSample>()
        var page: String? = null
        do {
            val res = client.readRecords(
                ReadRecordsRequest(recordClass(t), TimeRangeFilter.after(Instant.EPOCH), pageSize = 1000, pageToken = page),
            )
            for (r in res.records) classify(r)?.let { (s, o) -> if (o != null) own += o else if (s != null) samples += s }
            page = res.pageToken
        } while (page != null)
        backup.exclusive { if (current()) repo.applyHealthPage(setOf(t), samples, emptyList(), mapOf(HealthKeys.token(t) to token)) }
        return samples.size
    }

    private suspend fun changes(
        backup: BackupCoordinator, repo: CyclusRepository, t: HealthType, start: String, own: MutableList<Pair<OwnRecord, Long>>, current: () -> Boolean,
    ): Int {
        var token = start
        var n = 0
        while (true) {
            val res = client.getChanges(token)
            if (res.changesTokenExpired) return fullRead(backup, repo, t, own, current) // older than 30 days: read again
            val ups = mutableListOf<HealthSample>()
            val dels = mutableListOf<String>()
            for (c in res.changes) when (c) {
                is UpsertionChange -> classify(c.record)?.first?.let { ups += it } // own upserts are ours already
                is DeletionChange -> dels += c.recordId
            }
            val next = res.nextChangesToken
            val ok = backup.exclusive {
                if (!current()) false else { repo.applyHealthPage(emptySet(), ups, dels, mapOf(HealthKeys.token(t) to next)); true }
            }
            if (!ok) return n
            n += ups.size
            token = next
            if (!res.hasMore) return n
        }
    }

    /** External sample, or (for our own `cyclus:` records) the own record with its version. */
    private fun classify(r: Record): Pair<HealthSample?, Pair<OwnRecord, Long>?>? {
        val (t, day, value) = toCyclus(r) ?: return null
        val m = r.metadata
        val origin = m.dataOrigin.packageName
        return if (origin == ctx.packageName && m.clientRecordId?.startsWith(HealthKeys.PREFIX) == true) {
            null to (OwnRecord(m.clientRecordId!!, t, day, value) to m.clientRecordVersion)
        } else HealthSample(m.id, t, day, value, origin, m.lastModifiedTime.toEpochMilli()) to null
    }

    /** Returns (written, deleted). */
    private suspend fun write(backup: BackupCoordinator, repo: CyclusRepository, granted: Set<String>, current: () -> Boolean): Pair<Int, Int> =
        backup.exclusive {
            if (!current()) return@exclusive 0 to 0
            val settings = repo.observeSettings().first()
            val canWrite = HealthType.entries.filter { HealthPermission.getWritePermission(recordClass(it)) in granted }.toSet()
            val enabled = HealthKeys.enabled(settings).filter { c -> c.types.all { it in canWrite } }.toSet()
            val user = repo.observeLogs().first()
            val overrides = repo.observeOverrides().first()
            val today = LocalDate.now().toEpochDay().toInt()
            val links = repo.healthLinks()
            // types we may not touch (permission revoked): leave their links alone
            val plan = HealthPlanner.plan(
                HealthPlanner.desired(user, overrides, enabled, today), links.filterValues { it.type in canWrite }, System.currentTimeMillis(),
            )
            var written = 0
            for ((t, list) in plan.upserts.groupBy { it.first.type }) for (chunk in list.chunked(500)) {
                val ids = client.insertRecords(chunk.map { (r, v) -> toRecord(r, v) }).recordIdsList
                chunk.forEachIndexed { i, (r, v) -> repo.upsertHealthLink(HealthLink(r.syncId, t, r.day, ids.getOrNull(i), v, r.hash, LinkState.OK)) }
                written += chunk.size
            }
            var deleted = 0
            for ((t, list) in plan.deletes.groupBy { it.type }) for (chunk in list.chunked(500)) {
                chunk.forEach { repo.upsertHealthLink(it.copy(state = LinkState.DELETING)) } // tombstone until confirmed
                client.deleteRecords(recordClass(t), recordIdsList = emptyList(), clientRecordIdsList = chunk.map { it.syncId })
                chunk.forEach { repo.deleteHealthLink(it.syncId) }
                deleted += chunk.size
            }
            plan.forget.forEach { repo.deleteHealthLink(it.syncId) }
            if (written > 0) repo.setSetting(HealthKeys.WROTE, "1")
            written to deleted
        }

    /**
     * The person accepted the reinstall offer: reads what Cyclus wrote earlier and makes those days Cyclus entries again,
     * only while the store is still empty. Returns the number of restored days (0 if anything changed meanwhile).
     */
    suspend fun restoreOwn(backup: BackupCoordinator, store: StoreHolder, repo: CyclusRepository): Int = withContext(Dispatchers.IO) {
        fun current() = (store.state.value as? StoreState.Ready)?.repo === repo
        val granted = granted()
        val own = mutableListOf<OwnRecord>()
        for (t in HealthType.entries) {
            if (HealthPermission.getReadPermission(recordClass(t)) !in granted) continue
            var page: String? = null
            do {
                val res = client.readRecords(ReadRecordsRequest(recordClass(t), TimeRangeFilter.after(Instant.EPOCH), pageSize = 1000, pageToken = page))
                for (r in res.records) classify(r)?.second?.let { own += it.first }
                page = res.pageToken
            } while (page != null)
        }
        backup.exclusive {
            if (!current()) return@exclusive 0
            val logs = HealthPlanner.reconstruct(emptyMap(), own)
            val n = if (repo.restoreFromHealth(logs)) logs.size else 0
            repo.setSetting(HealthKeys.RESTORE_OFFER, null)
            n
        }
    }

    /** Erase / „Dort löschen“: every record Cyclus wrote of these types (Health Connect limits this to our own). */
    suspend fun deleteOwn(types: Collection<HealthType>) {
        val granted = granted()
        for (t in types) if (HealthPermission.getWritePermission(recordClass(t)) in granted) {
            client.deleteRecords(recordClass(t), TimeRangeFilter.after(Instant.EPOCH))
        }
    }

    suspend fun revokeAll() { runCatching { client.permissionController.revokeAllPermissions() } }

    companion object {
        fun recordClass(t: HealthType): KClass<out Record> = when (t) {
            HealthType.FLOW -> MenstruationFlowRecord::class
            HealthType.PERIOD -> MenstruationPeriodRecord::class
            HealthType.SPOTTING -> IntermenstrualBleedingRecord::class
            HealthType.BBT -> BasalBodyTemperatureRecord::class
            HealthType.MUCUS -> CervicalMucusRecord::class
            HealthType.LH -> OvulationTestRecord::class
            HealthType.SEX -> SexualActivityRecord::class
        }

        private fun dayOf(t: Instant, offset: ZoneOffset?): Day =
            t.atOffset(offset ?: ZoneId.systemDefault().rules.getOffset(t)).toLocalDate().toEpochDay().toInt()

        /** Record → (type, civil day in the record's offset, Cyclus value); null for values Cyclus has no field for. */
        fun toCyclus(r: Record): Triple<HealthType, Day, String>? = when (r) {
            is MenstruationFlowRecord -> Triple(HealthType.FLOW, dayOf(r.time, r.zoneOffset), when (r.flow) {
                MenstruationFlowRecord.FLOW_LIGHT -> "light"; MenstruationFlowRecord.FLOW_MEDIUM -> "medium"
                MenstruationFlowRecord.FLOW_HEAVY -> "heavy"; else -> "unspecified"
            })
            is MenstruationPeriodRecord -> {
                val start = dayOf(r.startTime, r.startZoneOffset)
                val end = maxOf(start, dayOf(r.endTime.minusMillis(1), r.endZoneOffset)) // end is exclusive
                Triple(HealthType.PERIOD, start, end.toString())
            }
            is IntermenstrualBleedingRecord -> Triple(HealthType.SPOTTING, dayOf(r.time, r.zoneOffset), "1")
            is BasalBodyTemperatureRecord -> Triple(HealthType.BBT, dayOf(r.time, r.zoneOffset), r.temperature.inCelsius.toString())
            is CervicalMucusRecord -> when (r.appearance) {
                CervicalMucusRecord.APPEARANCE_DRY -> "dry"; CervicalMucusRecord.APPEARANCE_STICKY -> "sticky"
                CervicalMucusRecord.APPEARANCE_CREAMY -> "creamy"; CervicalMucusRecord.APPEARANCE_WATERY -> "watery"
                CervicalMucusRecord.APPEARANCE_EGG_WHITE -> "eggwhite"; CervicalMucusRecord.APPEARANCE_UNUSUAL -> "unusual"
                else -> null
            }?.let { Triple(HealthType.MUCUS, dayOf(r.time, r.zoneOffset), it) }
            is OvulationTestRecord -> when (r.result) {
                OvulationTestRecord.RESULT_POSITIVE -> "pos"; OvulationTestRecord.RESULT_HIGH -> "surge"
                OvulationTestRecord.RESULT_NEGATIVE -> "neg"; else -> null
            }?.let { Triple(HealthType.LH, dayOf(r.time, r.zoneOffset), it) }
            is SexualActivityRecord -> Triple(HealthType.SEX, dayOf(r.time, r.zoneOffset), when (r.protectionUsed) {
                SexualActivityRecord.PROTECTION_USED_PROTECTED -> "p"; SexualActivityRecord.PROTECTION_USED_UNPROTECTED -> "u"; else -> "?"
            })
            else -> null
        }

        /** Own record → Health Connect record: a day value at local noon (rule 3), a period from start to end + 1 day. */
        fun toRecord(r: OwnRecord, version: Long, clientId: String = r.syncId): Record {
            val zone = ZoneId.systemDefault()
            val noon = LocalDate.ofEpochDay(r.day.toLong()).atTime(12, 0).atZone(zone)
            val t = noon.toInstant(); val off = noon.offset
            val meta = Metadata.manualEntry(clientId, version)
            return when (r.type) {
                HealthType.FLOW -> MenstruationFlowRecord(t, off, meta, when (r.value) {
                    "light" -> MenstruationFlowRecord.FLOW_LIGHT; "medium" -> MenstruationFlowRecord.FLOW_MEDIUM
                    "heavy" -> MenstruationFlowRecord.FLOW_HEAVY; else -> MenstruationFlowRecord.FLOW_UNKNOWN
                })
                HealthType.PERIOD -> {
                    val s = LocalDate.ofEpochDay(r.day.toLong()).atStartOfDay(zone)
                    val e = LocalDate.ofEpochDay((r.value.toIntOrNull() ?: r.day).toLong() + 1).atStartOfDay(zone)
                    MenstruationPeriodRecord(s.toInstant(), s.offset, e.toInstant(), e.offset, meta)
                }
                HealthType.SPOTTING -> IntermenstrualBleedingRecord(t, off, meta)
                HealthType.BBT -> BasalBodyTemperatureRecord(t, off, meta, Temperature.celsius(r.value.toDouble()))
                HealthType.MUCUS -> CervicalMucusRecord(t, off, meta, appearance = when (r.value) {
                    "dry" -> CervicalMucusRecord.APPEARANCE_DRY; "sticky" -> CervicalMucusRecord.APPEARANCE_STICKY
                    "creamy" -> CervicalMucusRecord.APPEARANCE_CREAMY; "watery" -> CervicalMucusRecord.APPEARANCE_WATERY
                    "eggwhite" -> CervicalMucusRecord.APPEARANCE_EGG_WHITE; else -> CervicalMucusRecord.APPEARANCE_UNUSUAL
                })
                HealthType.LH -> OvulationTestRecord(t, off, metadata = meta, result = when (r.value) {
                    "pos" -> OvulationTestRecord.RESULT_POSITIVE; "surge" -> OvulationTestRecord.RESULT_HIGH
                    else -> OvulationTestRecord.RESULT_NEGATIVE
                })
                HealthType.SEX -> SexualActivityRecord(t, off, meta, when (r.value) {
                    "p" -> SexualActivityRecord.PROTECTION_USED_PROTECTED; "u" -> SexualActivityRecord.PROTECTION_USED_UNPROTECTED
                    else -> SexualActivityRecord.PROTECTION_USED_UNKNOWN
                })
            }
        }
    }
}
