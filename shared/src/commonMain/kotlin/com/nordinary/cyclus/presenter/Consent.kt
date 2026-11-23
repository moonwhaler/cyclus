package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.backup.SettingKeys

/**
 * The „Bevor du startest“ notice (spec §7.1): not for birth control, estimates only, not a medical device, the
 * person decides. Asked in onboarding, again after [MAX_AGE_MILLIS], when the fertile window or Kinderwunsch is
 * switched on, and once more whenever [VERSION] is raised (raise it when the wording changes materially).
 * Every confirmation is kept (newest last, at most [MAX_HISTORY]) so the Privacy screen can show when.
 */
object Consent {
    const val KEY = SettingKeys.CONSENT
    const val VERSION = 1
    const val MAX_AGE_MILLIS = 365L * 86_400_000L
    const val MAX_HISTORY = 20

    data class Record(val version: Int, val atMillis: Long)

    /** Stored as `version|millis;version|millis;…`, oldest first. */
    fun parse(value: String?): List<Record> = value.orEmpty().split(';').mapNotNull { one ->
        val parts = one.split('|')
        if (parts.size != 2) return@mapNotNull null
        val v = parts[0].toIntOrNull()?.takeIf { it >= 1 } ?: return@mapNotNull null
        val at = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        Record(v, at)
    }

    fun history(settings: Map<String, String>): List<Record> = parse(settings[KEY])

    /** The newest confirmation of the current wording, if any. */
    fun current(settings: Map<String, String>): Record? = history(settings).lastOrNull()?.takeIf { it.version == VERSION }

    /**
     * True when the notice must be confirmed (again): never confirmed, older wording, or more than a year ago.
     * A confirmation dated in the future (clock moved back) still counts: nobody is asked twice for a clock change.
     */
    fun needed(settings: Map<String, String>, nowMillis: Long): Boolean {
        val c = current(settings) ?: return true
        return nowMillis - c.atMillis > MAX_AGE_MILLIS
    }

    /** The stored value after confirming the current wording at [nowMillis]. */
    fun confirm(previous: String?, nowMillis: Long): String =
        (parse(previous) + Record(VERSION, nowMillis)).takeLast(MAX_HISTORY).joinToString(";") { "${it.version}|${it.atMillis}" }
}
