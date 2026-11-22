package com.nordinary.cyclus.backup

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
            return f
        }
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
}

/** What an import will bring in (shown before Merge/Replace). */
data class ImportPreview(val days: Int, val periodDays: Int, val firstDay: String?, val lastDay: String?, val episodes: Int)

fun ExportFile.preview() = ImportPreview(
    days = logs.size,
    periodDays = logs.count { it.flow != null && it.flow != "none" && it.flow != "spotting" },
    firstDay = logs.minOfOrNull { it.day }, lastDay = logs.maxOfOrNull { it.day }, episodes = episodes.size,
)
