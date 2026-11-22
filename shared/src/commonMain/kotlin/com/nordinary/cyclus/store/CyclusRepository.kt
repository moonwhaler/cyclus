package com.nordinary.cyclus.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.nordinary.cyclus.core.CycleOverride
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Episode
import com.nordinary.cyclus.core.EpisodeKind
import com.nordinary.cyclus.core.Flow as LogFlow
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.core.Mucus
import com.nordinary.cyclus.core.OverrideKind
import com.nordinary.cyclus.backup.ExportEpisode
import com.nordinary.cyclus.backup.ExportFile
import com.nordinary.cyclus.backup.ExportLog
import com.nordinary.cyclus.backup.ExportOverride
import com.nordinary.cyclus.backup.ExportTag
import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.store.db.CyclusDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Single writer for all personal data (ANDROID_PLAN §2.4). Platform code provides the (encrypted) driver.
 * Empty day logs are deleted rather than stored.
 */
class CyclusRepository(
    private val db: CyclusDatabase,
    private val io: CoroutineDispatcher,
    private val onClose: () -> Unit = {},
    private val nowMillis: () -> Long,
) {
    /** Closes the underlying driver. Stop all collectors first. */
    fun close() = onClose()

    private val q get() = db.cyclusQueries

    /** All day logs; re-emits after every committed change. */
    fun observeLogs(): Flow<Map<Day, DayLog>> =
        q.selectAllLogs().asFlow().mapToList(io).map { readAllLogs() }

    fun observeOverrides(): Flow<Map<Day, CycleOverride>> =
        q.selectAllOverrides().asFlow().mapToList(io).map { rows ->
            rows.associate { it.day.toInt() to CycleOverride(OverrideKind.of(it.kind), it.reason) }
        }

    fun observeEpisodes(): Flow<List<Episode>> =
        q.selectAllEpisodes().asFlow().mapToList(io).map { rows ->
            rows.map { Episode(it.id, EpisodeKind.of(it.kind), it.start_day.toInt(), it.end_day?.toInt()) }
        }

    fun observeSettings(): Flow<Map<String, String>> =
        q.selectAllSettings().asFlow().mapToList(io).map { rows -> rows.associate { it.key to it.value_ } }

    suspend fun log(day: Day): DayLog? = withContext(io) { readAllLogs()[day] }

    suspend fun saveLog(day: Day, log: DayLog) = withContext(io) {
        db.transaction { writeLog(day, log) }
        changes++
    }

    suspend fun setOverride(day: Day, override: CycleOverride?) = withContext(io) {
        if (override == null) q.deleteOverride(day.toLong())
        else q.upsertOverride(day.toLong(), override.kind.key, override.reason)
        changes++
    }

    suspend fun saveEpisode(episode: Episode) = withContext(io) {
        q.upsertEpisode(episode.id, episode.kind.key, episode.start.toLong(), episode.end?.toLong())
        changes++
    }

    suspend fun deleteEpisode(id: String) = withContext(io) { q.deleteEpisode(id); changes++ }

    suspend fun setSetting(key: String, value: String?) = withContext(io) {
        if (value == null) q.deleteSetting(key) else q.upsertSetting(key, value)
        if (key in SettingKeys.USER_PREFS || key in SettingKeys.SECURITY) changes++
    }

    suspend fun setting(key: String): String? = withContext(io) { q.selectSetting(key).executeAsOneOrNull() }

    /** Erase all personal data (logs, overrides, episodes, user tags, settings). */
    suspend fun eraseAll() = withContext(io) { db.transaction { eraseAllInTransaction() } }

    /** Replace everything with [logs] (debug demo data). */
    suspend fun replaceAll(logs: Map<Day, DayLog>, episodes: List<Episode> = emptyList(), settings: Map<String, String> = emptyMap()) =
        withContext(io) {
            db.transaction {
                eraseAllInTransaction()
                for ((d, l) in logs) writeLog(d, l)
                for (e in episodes) q.upsertEpisode(e.id, e.kind.key, e.start.toLong(), e.end?.toLong())
                for ((k, v) in settings) q.upsertSetting(k, v)
            }
        }

    // ---------------- export / import (.cyclus) ----------------

    /** Everything personal as an [ExportFile]. Security settings only for the device snapshot. */
    suspend fun exportAll(exportedAt: String, app: String, includeSecurity: Boolean): ExportFile = withContext(io) {
        val settings = q.selectAllSettings().executeAsList().associate { it.key to it.value_ }
        val logs = readAllLogs()
        val updated = q.selectAllLogs().executeAsList().associate { it.day.toInt() to it.updated_at }
        ExportFile(
            exportedAt = exportedAt, app = app,
            logs = logs.entries.sortedBy { it.key }.map { (d, l) -> l.toExport(d, updated[d] ?: 0) },
            overrides = q.selectAllOverrides().executeAsList().map { ExportOverride(iso(it.day.toInt()), it.kind, it.reason) },
            episodes = q.selectAllEpisodes().executeAsList().map { ExportEpisode(it.id, it.kind, iso(it.start_day.toInt()), it.end_day?.let { e -> iso(e.toInt()) }) },
            tags = q.selectAllTags().executeAsList().filter { it.builtin == 0L }.map { ExportTag(it.id, it.name, it.symbol, it.archived != 0L) },
            prefs = settings.filterKeys { it in SettingKeys.USER_PREFS },
            security = if (includeSecurity) settings.filterKeys { it in SettingKeys.SECURITY } else null,
        )
    }

    /** Replace all personal data with [file]; security and device settings stay unless the file carries them (device restore). */
    suspend fun importReplace(file: ExportFile) = withContext(io) {
        db.transaction {
            q.deleteAllLogs(); q.deleteAllSymptoms(); q.deleteAllEmotions(); q.deleteAllPains(); q.deleteAllDayTags()
            q.deleteAllOverrides(); q.deleteAllEpisodes(); q.deleteUserTags()
            SettingKeys.USER_PREFS.forEach { q.deleteSetting(it) }
            insertAll(file, onlyIfNewer = false)
            file.prefs.filterKeys { it in SettingKeys.USER_PREFS }.forEach { (k, v) -> q.upsertSetting(k, v) }
            file.security?.filterKeys { it in SettingKeys.SECURITY }?.forEach { (k, v) -> q.upsertSetting(k, v) }
        }
        changes++
    }

    /** Merge [file] into the store: per day the newer `updatedAt` wins; overrides/episodes/tags by key; prefs only if absent. */
    suspend fun importMerge(file: ExportFile) = withContext(io) {
        db.transaction {
            insertAll(file, onlyIfNewer = true)
            val existing = q.selectAllSettings().executeAsList().map { it.key }.toSet()
            file.prefs.filterKeys { it in SettingKeys.USER_PREFS && it !in existing }.forEach { (k, v) -> q.upsertSetting(k, v) }
        }
        changes++
    }

    /** Incremented on every write (device snapshot trigger). */
    @kotlin.concurrent.Volatile var changes: Long = 0
        private set

    private fun insertAll(file: ExportFile, onlyIfNewer: Boolean) {
        val current = if (onlyIfNewer) q.selectAllLogs().executeAsList().associate { it.day.toInt() to it.updated_at } else emptyMap()
        for (e in file.logs) {
            val d = epoch(e.day)
            val mine = current[d]
            if (mine != null && mine >= e.updatedAt) continue
            writeLog(d, e.toDayLog(), e.updatedAt)
        }
        for (o in file.overrides) q.upsertOverride(epoch(o.day).toLong(), OverrideKind.of(o.kind).key, o.reason)
        for (ep in file.episodes) q.upsertEpisode(ep.id, EpisodeKind.of(ep.kind).key, epoch(ep.start).toLong(), ep.end?.let { epoch(it).toLong() })
        for (t in file.tags) q.upsertTag(t.id, t.name, t.symbol, if (t.archived) 1 else 0)
    }

    private fun iso(day: Day) = kotlinx.datetime.LocalDate.fromEpochDays(day).toString()
    private fun epoch(iso: String): Day = kotlinx.datetime.LocalDate.parse(iso).toEpochDays().toInt()

    private fun eraseAllInTransaction() {
        q.deleteAllLogs(); q.deleteAllSymptoms(); q.deleteAllEmotions(); q.deleteAllPains(); q.deleteAllDayTags()
        q.deleteAllOverrides(); q.deleteAllEpisodes(); q.deleteUserTags(); q.deleteAllSettings()
    }

    private fun writeLog(day: Day, log: DayLog, updatedAt: Long = nowMillis()) {
        val d = day.toLong()
        q.deleteSymptoms(d); q.deleteEmotions(d); q.deletePains(d); q.deleteDayTags(d)
        if (log.isEmpty) { q.deleteLog(d); return }
        q.upsertLog(
            day = d, flow = log.flow?.key,
            mood = log.mood?.toLong(), energy = log.energy?.toLong(), sleep = log.sleep?.toLong(), libido = log.libido?.toLong(),
            mucus = log.mucus?.key, lh = log.lh?.key, bbt = log.bbt,
            sex = if (log.sex) 1 else 0, sex_protected = log.sexProtected?.let { if (it) 1L else 0L },
            note = log.note, nothing_else = if (log.nothingElse) 1 else 0, updated_at = updatedAt,
        )
        for (s in log.symptoms) q.insertSymptom(d, s, log.symptomSeverity[s]?.toLong())
        for (e in log.emotions) q.insertEmotion(d, e)
        for ((r, v) in log.pain) q.insertPain(d, r, v.toLong())
        for (t in log.tags) q.insertDayTag(d, t)
    }

    private fun readAllLogs(): Map<Day, DayLog> {
        val symptoms = q.selectAllSymptoms().executeAsList().groupBy { it.day.toInt() }
        val emotions = q.selectAllEmotions().executeAsList().groupBy({ it.day.toInt() }, { it.emotion })
        val pains = q.selectAllPains().executeAsList().groupBy { it.day.toInt() }
        val tags = q.selectAllDayTags().executeAsList().groupBy({ it.day.toInt() }, { it.tag_id })
        return q.selectAllLogs().executeAsList().associate { r ->
            val d = r.day.toInt()
            val sym = symptoms[d].orEmpty()
            d to DayLog(
                flow = r.flow?.let { LogFlow.of(it) },
                symptoms = sym.map { it.symptom }.toSet(),
                symptomSeverity = sym.mapNotNull { s -> s.severity?.let { s.symptom to it.toInt() } }.toMap(),
                pain = pains[d].orEmpty().associate { it.region to it.score.toInt() },
                emotions = emotions[d].orEmpty().toSet(),
                tags = tags[d].orEmpty().toSet(),
                mood = r.mood?.toInt(), energy = r.energy?.toInt(), sleep = r.sleep?.toInt(), libido = r.libido?.toInt(),
                mucus = r.mucus?.let { Mucus.of(it) }, lh = r.lh?.let { LhResult.of(it) }, bbt = r.bbt,
                sex = r.sex != 0L, sexProtected = r.sex_protected?.let { it != 0L },
                note = r.note, nothingElse = r.nothing_else != 0L,
            )
        }
    }
}

internal fun DayLog.toExport(day: Day, updatedAt: Long) = ExportLog(
    day = kotlinx.datetime.LocalDate.fromEpochDays(day).toString(), flow = flow?.key,
    symptoms = symptoms.toList(), symptomSeverity = symptomSeverity, pain = pain, emotions = emotions.toList(), tags = tags.toList(),
    mood = mood, energy = energy, sleep = sleep, libido = libido, mucus = mucus?.key, lh = lh?.key, bbt = bbt,
    sex = sex, sexProtected = sexProtected, note = note, nothingElse = nothingElse, updatedAt = updatedAt,
)

internal fun ExportLog.toDayLog() = DayLog(
    flow = flow?.let { LogFlow.of(it) }, symptoms = symptoms.toSet(), symptomSeverity = symptomSeverity, pain = pain,
    emotions = emotions.toSet(), tags = tags.toSet(), mood = mood, energy = energy, sleep = sleep, libido = libido,
    mucus = mucus?.let { Mucus.of(it) }, lh = lh?.let { LhResult.of(it) }, bbt = bbt, sex = sex, sexProtected = sexProtected,
    note = note, nothingElse = nothingElse,
)
