package com.nordinary.cyclus.backup

import com.nordinary.cyclus.core.EpisodeKind
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.core.Mucus
import com.nordinary.cyclus.core.OverrideKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON payload of a `.cyclus` file, schema v1 (docs/export/cyclus-export.schema.json). Dates are ISO `YYYY-MM-DD`. */
@Serializable
data class ExportFile(
    val schema: Int = SCHEMA,
    val exportedAt: String,
    val app: String,
    val logs: List<ExportLog> = emptyList(),
    val overrides: List<ExportOverride> = emptyList(),
    val episodes: List<ExportEpisode> = emptyList(),
    val tags: List<ExportTag> = emptyList(),
    /** User preferences only ([SettingKeys.USER_PREFS]). */
    val prefs: Map<String, String> = emptyMap(),
    /** Device snapshot only: app-lock configuration ([SettingKeys.SECURITY]). Never present in user exports. */
    val security: Map<String, String>? = null,
) {
    companion object {
        const val SCHEMA = 1
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

        fun decode(bytes: ByteArray): ExportFile {
            val text = bytes.decodeToString()
            val f = try {
                json.decodeFromString(serializer(), text)
            } catch (e: Exception) {
                // a newer schema may not even parse; check the version field first for a clearer error
                if (Regex("\"schema\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { it != SCHEMA } == true) {
                    throw CyclusFileException.UnsupportedVersion("schema not supported")
                }
                throw CyclusFileException.Malformed("invalid export JSON")
            }
            if (f.schema != SCHEMA) throw CyclusFileException.UnsupportedVersion("schema ${f.schema} not supported")
            f.validate()
            return f
        }

        const val MAX_LOGS = 100_000
        const val MAX_ITEMS = 200
        const val MAX_KEY = 200
        const val MAX_NOTE = 20_000
    }

    /**
     * Everything an import writes is checked before the preview (schema v1 ranges, docs/export/cyclus-export.schema.json):
     * a value this version doesn't know means the file came from a newer app ([CyclusFileException.UnsupportedVersion]),
     * anything else out of range is a damaged or hand-made file ([CyclusFileException.Malformed]). Nothing half-valid is
     * ever imported.
     */
    fun validate() {
        fun bad(what: String): Nothing = throw CyclusFileException.Malformed("invalid $what")
        fun newer(what: String): Nothing = throw CyclusFileException.UnsupportedVersion("unknown $what")
        fun day(s: String, what: String): Int {
            val d = runCatching { kotlinx.datetime.LocalDate.parse(s) }.getOrNull() ?: bad(what)
            if (d.year !in 1900..2200) bad(what)
            return d.toEpochDays().toInt()
        }
        fun key(s: String, what: String) { if (s.isEmpty() || s.length > MAX_KEY) bad(what) }
        fun keys(l: Collection<String>, what: String) { if (l.size > MAX_ITEMS) bad(what); l.forEach { key(it, what) } }
        fun scale(v: Int?, what: String) { if (v != null && v !in 1..5) bad(what) }

        if (logs.size > MAX_LOGS || overrides.size > MAX_LOGS || episodes.size > MAX_ITEMS * 10 || tags.size > MAX_ITEMS * 10) bad("size")
        for (l in logs) {
            day(l.day, "log day")
            l.flow?.let { com.nordinary.cyclus.core.Flow.ofOrNull(it) ?: newer("flow") }
            l.mucus?.let { Mucus.ofOrNull(it) ?: newer("mucus") }
            l.lh?.let { LhResult.ofOrNull(it) ?: newer("lh") }
            keys(l.symptoms, "symptoms"); keys(l.emotions, "emotions"); keys(l.tags, "tags")
            keys(l.symptomSeverity.keys, "severity"); if (l.symptomSeverity.values.any { it !in 1..3 }) bad("severity")
            keys(l.pain.keys, "pain"); if (l.pain.values.any { it !in 0..10 }) bad("pain")
            scale(l.mood, "mood"); scale(l.energy, "energy"); scale(l.sleep, "sleep"); scale(l.libido, "libido")
            l.bbt?.let { if (it.isNaN() || it !in 30.0..45.0) bad("bbt") }
            l.note?.let { if (it.length > MAX_NOTE) bad("note") }
            if (l.updatedAt < 0) bad("updatedAt")
        }
        for (o in overrides) {
            day(o.day, "override day")
            OverrideKind.ofOrNull(o.kind) ?: newer("override")
            o.reason?.let { if (it.length > MAX_NOTE) bad("reason") }
        }
        for (e in episodes) {
            key(e.id, "episode id")
            EpisodeKind.ofOrNull(e.kind) ?: newer("episode")
            val s = day(e.start, "episode start")
            e.end?.let { if (day(it, "episode end") < s) bad("episode end") }
        }
        for (t in tags) {
            key(t.id, "tag id")
            t.name?.let { if (it.length > MAX_KEY) bad("tag name") }
            t.symbol?.let { if (it.length > MAX_KEY) bad("tag symbol") }
        }
        if (prefs.size > MAX_ITEMS || prefs.values.any { it.length > MAX_NOTE }) bad("prefs")
        security?.let { if (it.size > MAX_ITEMS || it.values.any { v -> v.length > MAX_KEY }) bad("security") }
    }

    fun encode(): ByteArray = json.encodeToString(serializer(), this).encodeToByteArray()
}

@Serializable
data class ExportLog(
    val day: String,
    val flow: String? = null,
    val symptoms: List<String> = emptyList(),
    val symptomSeverity: Map<String, Int> = emptyMap(),
    val pain: Map<String, Int> = emptyMap(),
    val emotions: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val mood: Int? = null,
    val energy: Int? = null,
    val sleep: Int? = null,
    val libido: Int? = null,
    val mucus: String? = null,
    val lh: String? = null,
    val bbt: Double? = null,
    val sex: Boolean = false,
    val sexProtected: Boolean? = null,
    val note: String? = null,
    val nothingElse: Boolean = false,
    /** Epoch millis of the last change (merge: newer wins). */
    val updatedAt: Long = 0,
)

@Serializable
data class ExportOverride(val day: String, val kind: String, val reason: String? = null)

@Serializable
data class ExportEpisode(val id: String, val kind: String, val start: String, val end: String? = null)

@Serializable
data class ExportTag(val id: String, val name: String? = null, val symbol: String? = null, val archived: Boolean = false)

/** Which settings travel where. Anything not listed stays on the device. */
object SettingKeys {
    const val MODE = "mode"
    const val SHOW_FERT = "showFertTrack"
    const val MUTED = "mutedPatterns"
    /** In `.cyclus` exports and device snapshots. */
    val USER_PREFS = setOf(MODE, SHOW_FERT, MUTED)

    const val LOCK_ENABLED = "lock.enabled"
    const val LOCK_PIN_HASH = "lock.pin.hash"
    const val LOCK_PIN_SALT = "lock.pin.salt"
    const val LOCK_PIN_ONLY = "lock.pinOnly"
    const val LOCK_AFTER = "lock.after"
    const val FLAG_SECURE = "privacy.flagSecure"
    /** Device snapshot only (same person, new phone); never in user exports; kept by every import. */
    val SECURITY = setOf(LOCK_ENABLED, LOCK_PIN_HASH, LOCK_PIN_SALT, LOCK_PIN_ONLY, LOCK_AFTER, FLAG_SECURE)

    /** Device bookkeeping, never exported: PIN back-off, export counters. */
    const val LOCK_BACKOFF = "lock.backoff"
    const val EXPORT_COUNT = "export.count"
    const val EXPORT_LAST = "export.last"
    /** Name printed on the Doctor Visit Pack; remembered on this device only. */
    const val REPORT_NAME = "report.name"
    /** Agreement to the „Bevor du startest“ notice ([com.nordinary.cyclus.presenter.Consent]); this device only, never exported. */
    const val CONSENT = "consent.notice"
}

/** What an import will bring in (shown before Merge/Replace). */
data class ImportPreview(val days: Int, val periodDays: Int, val firstDay: String?, val lastDay: String?, val episodes: Int)

fun ExportFile.preview() = ImportPreview(
    days = logs.size,
    periodDays = logs.count { it.flow != null && it.flow != "none" && it.flow != "spotting" },
    firstDay = logs.minOfOrNull { it.day }, lastDay = logs.maxOfOrNull { it.day }, episodes = episodes.size,
)
