package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.engine.Mode

/**
 * The person's preferences the engine and presenters read, parsed once from the settings table (raw keys and their
 * storage format live only here and in [SettingKeys]).
 */
data class UserPrefs(
    val mode: Mode = Mode.TRACK,
    val showFertility: Boolean = false,
    val muted: Set<String> = emptySet(),
) {
    companion object {
        fun from(settings: Map<String, String>) = UserPrefs(
            mode = if (settings[SettingKeys.MODE] == "conceive") Mode.CONCEIVE else Mode.TRACK,
            showFertility = settings[SettingKeys.SHOW_FERT] == "1",
            muted = settings[SettingKeys.MUTED]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
        )

        /** Stored values (null = default, row deleted). */
        fun modeValue(mode: Mode): String? = if (mode == Mode.CONCEIVE) "conceive" else null
        fun showFertilityValue(on: Boolean): String? = if (on) "1" else null
        fun mutedValue(muted: Set<String>): String? = muted.sorted().joinToString(",").ifEmpty { null }
    }
}

/** Device bookkeeping shown in the Privacy Center. */
data class ExportStats(val count: Int = 0, val lastDay: Int? = null) {
    companion object {
        fun from(settings: Map<String, String>) = ExportStats(
            count = settings[SettingKeys.EXPORT_COUNT]?.toIntOrNull() ?: 0,
            lastDay = settings[SettingKeys.EXPORT_LAST]?.toIntOrNull(),
        )
    }
}
