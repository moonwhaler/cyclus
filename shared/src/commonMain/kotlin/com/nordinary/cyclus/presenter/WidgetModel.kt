package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.engine.CycleSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the home-screen widget may show (spec §8.1), written only while „Details im Widget“ is on.
 * Days, not texts: the widget recomputes everything from *today* so it stays right after midnight without the DB.
 */
@Serializable
data class WidgetSnapshot(
    val last: Int,
    val lo: Int,
    val hi: Int,
    /** Last logged bleeding day of the current period (for „Periode · Tag N“). */
    val bleedThrough: Int? = null,
    val paused: Boolean = false,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun decode(text: String): WidgetSnapshot? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()

        fun from(snap: CycleSnapshot): WidgetSnapshot? {
            val p = snap.prediction ?: return null
            return WidgetSnapshot(p.last, p.lo, p.hi, snap.data.periodDays(p.last).maxOrNull(), snap.paused)
        }
    }
}

sealed interface WidgetLine {
    data class InPeriod(val periodDay: Int) : WidgetLine
    data class In(val from: Int, val to: Int) : WidgetLine
    data object Window : WidgetLine
    data object Later : WidgetLine
    data object Paused : WidgetLine
}

data class WidgetView(val cycleDay: Int, val line: WidgetLine, val progress: Float)

object WidgetPresenter {
    fun view(s: WidgetSnapshot, today: Day): WidgetView? {
        val cd = today - s.last + 1
        if (cd < 1) return null
        val line = when {
            s.paused -> WidgetLine.Paused
            s.bleedThrough != null && today <= s.bleedThrough -> WidgetLine.InPeriod(cd)
            today < s.lo -> WidgetLine.In(s.lo - today, s.hi - today)
            today <= s.hi -> WidgetLine.Window
            else -> WidgetLine.Later
        }
        val len = maxOf(s.hi - s.last + 1, cd, 28)
        return WidgetView(cd, line, (cd.toFloat() / len).coerceIn(0f, 1f))
    }
}
