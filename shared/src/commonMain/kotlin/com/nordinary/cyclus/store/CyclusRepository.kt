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
    private val nowMillis: () -> Long,
) {
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
    }

    suspend fun setOverride(day: Day, override: CycleOverride?) = withContext(io) {
        if (override == null) q.deleteOverride(day.toLong())
        else q.upsertOverride(day.toLong(), override.kind.key, override.reason)
    }

    suspend fun saveEpisode(episode: Episode) = withContext(io) {
        q.upsertEpisode(episode.id, episode.kind.key, episode.start.toLong(), episode.end?.toLong())
    }

    suspend fun deleteEpisode(id: String) = withContext(io) { q.deleteEpisode(id) }

    suspend fun setSetting(key: String, value: String?) = withContext(io) {
        if (value == null) q.deleteSetting(key) else q.upsertSetting(key, value)
    }

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

    private fun eraseAllInTransaction() {
        q.deleteAllLogs(); q.deleteAllSymptoms(); q.deleteAllEmotions(); q.deleteAllPains(); q.deleteAllDayTags()
        q.deleteAllOverrides(); q.deleteAllEpisodes(); q.deleteUserTags(); q.deleteAllSettings()
    }

    private fun writeLog(day: Day, log: DayLog) {
        val d = day.toLong()
        q.deleteSymptoms(d); q.deleteEmotions(d); q.deletePains(d); q.deleteDayTags(d)
        if (log.isEmpty) { q.deleteLog(d); return }
        q.upsertLog(
            day = d, flow = log.flow?.key,
            mood = log.mood?.toLong(), energy = log.energy?.toLong(), sleep = log.sleep?.toLong(), libido = log.libido?.toLong(),
            mucus = log.mucus?.key, lh = log.lh?.key, bbt = log.bbt,
            sex = if (log.sex) 1 else 0, sex_protected = log.sexProtected?.let { if (it) 1L else 0L },
            note = log.note, nothing_else = if (log.nothingElse) 1 else 0, updated_at = nowMillis(),
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
