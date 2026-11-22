package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.core.Catalog
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog

/**
 * Optional sections of the Log sheet. Flow, pinned and suggested symptoms and mood are always open; these open by
 * themselves once the person has used them (or the day has a value), the rest wait under „Weitere Angaben“, so the
 * sheet stays short for people who log little and complete for people who log a lot.
 */
enum class LogGroup {
    MORE_SYMPTOMS, PAIN, FEELINGS, ENERGY, SLEEP, LIBIDO, DISCHARGE, TEMPERATURE, TAGS, NOTE;

    fun has(l: DayLog): Boolean = when (this) {
        MORE_SYMPTOMS -> l.symptoms.any { it !in Catalog.PINNED }
        PAIN -> l.pain.values.any { it > 0 }
        FEELINGS -> l.emotions.isNotEmpty()
        ENERGY -> l.energy != null
        SLEEP -> l.sleep != null
        LIBIDO -> l.libido != null
        DISCHARGE -> l.mucus != null
        TEMPERATURE -> l.bbt != null
        TAGS -> l.tags.isNotEmpty()
        NOTE -> !l.note.isNullOrBlank()
    }

    companion object {
        /** Sections used on any day of the last [window] days. */
        fun used(logs: Map<Day, DayLog>, today: Day, window: Int = 120): Set<LogGroup> {
            val recent = logs.filterKeys { it in today - window + 1..today }.values
            return entries.filter { g -> recent.any { g.has(it) } }.toSet()
        }
    }
}
