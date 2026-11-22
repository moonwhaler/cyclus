package com.nordinary.cyclus.ui

import androidx.health.connect.client.permission.HealthPermission
import com.nordinary.cyclus.R
import com.nordinary.cyclus.core.CycleOverride
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.health.HealthAvailability
import com.nordinary.cyclus.health.HealthCategory
import com.nordinary.cyclus.health.HealthKeys
import com.nordinary.cyclus.health.HealthLink
import com.nordinary.cyclus.health.HealthPlanner
import com.nordinary.cyclus.health.HealthSync
import com.nordinary.cyclus.health.HealthType
import com.nordinary.cyclus.health.LinkState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Health Connect (spec §11.4): sync while the app is open, triggered by the person's own data and the Health Connect
 * switches only (its own writes never retrigger it), plus the Privacy Center actions.
 */
class HealthActions internal constructor(
    private val c: VmContext,
    logs: Flow<Map<Day, DayLog>>,
    overrides: Flow<Map<Day, CycleOverride>>,
    settings: Flow<Map<String, String>>,
    private val model: () -> AppModel?,
) {
    private val repo = c.repo
    private val app = c.app
    val sync = HealthSync(app)

    fun availability() = sync.availability()
    fun readPermissions() = sync.readPermissions()
    fun writePermissions(category: HealthCategory) = sync.writePermissions(category)


    init {
        // Health Connect: triggered by the person's own data and the HC toggles only (its own writes never retrigger it)
        @OptIn(FlowPreview::class)
        c.launch {
            val userData = combine(
                logs, overrides,
                settings.map { st ->
                    st.filterKeys { it.startsWith("hc.w.") || it == HealthKeys.CONNECTED }
                }.distinctUntilChanged(),
            ) { l, o, s -> Triple(l, o, s) }.distinctUntilChanged()
            merge(userData.debounce(3_000).map { false }, app.healthKick).collect { full ->
                runCatching { sync.sync(app.backup, app.store, repo, full) }
            }
        }
    }


    data class HealthUi(
        val availability: HealthAvailability,
        val connected: Boolean,
        val granted: Set<String>,
        val enabled: Set<HealthCategory>,
        val lastSync: Long?,
        val error: Boolean,
        val noHistory: Boolean,
        val imported: Long,
        val removed: List<HealthLink>,
        val wrote: Boolean,
        /** Days Cyclus wrote earlier that could come back into this empty Cyclus (0 = no offer). */
        val restoreOffer: Int = 0,
    )

    suspend fun ui(settings: Map<String, String>): HealthUi {
        val av = sync.availability()
        val ok = av == HealthAvailability.AVAILABLE
        return HealthUi(
            availability = av,
            connected = settings[HealthKeys.CONNECTED] == "1",
            granted = if (ok) sync.granted() else emptySet(),
            enabled = HealthKeys.enabled(settings),
            lastSync = settings[HealthKeys.LAST_SYNC]?.toLongOrNull(),
            error = settings[HealthKeys.ERROR] != null,
            noHistory = settings[HealthKeys.NO_HISTORY] == "1",
            imported = repo.healthSampleCount(),
            removed = repo.healthLinks().values.filter { it.state == LinkState.REMOVED },
            wrote = settings[HealthKeys.WROTE] == "1",
            restoreOffer = settings[HealthKeys.RESTORE_OFFER]?.toIntOrNull() ?: 0,
        )
    }

    /** After the read-permission screen: connected once anything readable was granted; first sync reads everything. */
    fun connected(granted: Set<String>) {
        val reads = HealthType.entries.map {
            HealthPermission.getReadPermission(HealthSync.recordClass(it))
        }
        if (granted.none { it in reads }) return
        c.launch {
            repo.setSetting(HealthKeys.CONNECTED, "1")
            val history = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
            repo.setSetting(HealthKeys.NO_HISTORY, if (history in granted) null else "1")
            app.healthKick.emit(true)
        }
    }

    fun syncNow() { app.healthKick.tryEmit(false) }

    /** Answer to „Frühere Einträge übernehmen?“; no means not asked again until Health Connect is disconnected. */
    fun answerRestore(accept: Boolean) {
        c.launch {
            if (accept) {
                val n = sync.restoreOwn(app.backup, app.store, repo)
                if (n > 0) c.msg(R.string.hc_restore_done)
            } else {
                repo.setSetting(HealthKeys.RESTORE_DECLINED, "1")
                repo.setSetting(HealthKeys.RESTORE_OFFER, null)
            }
        }
    }

    /** Switch writing of [category]. Off: [deleteThere] deletes what Cyclus wrote, else the records stay and are forgotten. */
    fun setWrite(category: HealthCategory, on: Boolean, deleteThere: Boolean = false) {
        c.launch {
            if (!on && !deleteThere) app.backup.exclusive { category.types.forEach { repo.deleteHealthLinksOfType(it) } }
            repo.setSetting(HealthKeys.write(category), if (on) "1" else null)
        }
    }

    /** Rule 6 review: keep only in Cyclus (never written again) or remove the values from Cyclus too. */
    fun reviewRemoved(links: List<HealthLink>, keep: Boolean) {
        c.launch {
            app.backup.exclusive {
                for (l in links) {
                    if (keep) { repo.upsertHealthLink(l.copy(state = LinkState.KEPT)); continue }
                    val days = if (l.type == HealthType.PERIOD) {
                        HealthPlanner.removedPeriodDays(model()?.userLogs ?: emptyMap(), l.day)
                    } else listOf(l.day)
                    for (d in days) {
                        val before = repo.log(d) ?: continue
                        val after = when (l.type) {
                            HealthType.FLOW, HealthType.SPOTTING ->
                                before.copy(flow = null)
                            HealthType.PERIOD -> if (before.isBleed) before.copy(flow = null) else before
                            HealthType.BBT -> before.copy(bbt = null)
                            HealthType.MUCUS -> before.copy(mucus = null)
                            HealthType.LH -> before.copy(lh = null)
                            HealthType.SEX -> before.copy(sex = false, sexProtected = null)
                        }
                        repo.saveLog(d, after)
                    }
                    repo.deleteHealthLink(l.syncId)
                }
            }
        }
    }

    /** Revokes access and drops imported samples, links and tokens; Cyclus' own entries and what it wrote stay. */
    fun disconnect() {
        c.launch {
            sync.revokeAll()
            app.backup.exclusive { repo.clearHealth() }
        }
    }
}
