package com.nordinary.cyclus.i18n

import java.util.Locale

/**
 * The languages Cyclus ships (resource folders values/ = English, values-de, -fr, -es, -pt, -it). Names are endonyms
 * and never translated. Spanish and Portuguese are the European variants; phones set to other variants (es-419,
 * pt-BR) get the same texts with their own date formats.
 */
object AppLanguages {
    data class Lang(val tag: String, val endonym: String) {
        val locale: Locale get() = Locale.forLanguageTag(tag)
    }

    val ALL = listOf(
        Lang("de", "Deutsch"), Lang("en", "English"), Lang("fr", "Français"),
        Lang("es-ES", "Español"), Lang("pt-PT", "Português"), Lang("it", "Italiano"),
    )
    private val ENGLISH = ALL[1]

    /** Matched by language only, so "pt" (system list) and "pt-PT" (our picker) are the same choice. */
    fun of(locale: Locale?): Lang? = locale?.let { l -> ALL.firstOrNull { it.locale.language == l.language } }

    /** The Doctor Visit Pack starts in the app's language when Cyclus has it, else in English. */
    fun pdfDefault(ui: Locale): Locale = (of(ui) ?: ENGLISH).locale
}
