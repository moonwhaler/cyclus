package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.core.Catalog

/**
 * The cards of Today (spec §7.2), in their default order. The ring stays on top and is not a card; the one-time
 * reminder offer and the fertility footer („Warum dieses Fenster?“, „Nicht zur Verhütung geeignet“) are not cards
 * either, so they can never be hidden. A timed card only appears when it has something to say.
 */
enum class TodayCard(val key: String, val defaultOn: Boolean = true, val conceiveOnly: Boolean = false, val timed: Boolean = false) {
    LOG("log"),
    TEST("test", conceiveOnly = true, timed = true),
    BEST_DAYS("best", conceiveOnly = true, timed = true),
    LH_PLAN("lhPlan", conceiveOnly = true, timed = true),
    LH_TODAY("lhToday", conceiveOnly = true, timed = true),
    FORECAST("forecast", timed = true),
    FERTILE("fertile", timed = true),
    PRE("pre", timed = true),
    UPCOMING("upcoming", timed = true),
    PATTERNS("patterns", timed = true),
    WEEK("week", defaultOn = false),
    // step 2–3: all wait in the library until the person adds them
    USUAL("usual", defaultOn = false, timed = true),
    TEMP("temp", defaultOn = false, timed = true),
    MOOD("mood", defaultOn = false, timed = true),
    DOCTOR("doctor", defaultOn = false),
    PHASE("phase", defaultOn = false, timed = true),
    ;

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

/**
 * Order and hidden cards in one setting (`log,test,…,!week`), so a merge import never takes the order from one file
 * and the hidden set from another. Missing setting = default layout. Cards a later version adds (missing from a
 * stored order) appear at their default position with their default visibility.
 */
data class TodayLayout(val order: List<TodayCard>, val hidden: Set<TodayCard>, val ringCompact: Boolean = false) {
    /** What edit mode lists, in stored order: every card that isn't hidden, whether or not it applies right now. */
    val shown: List<TodayCard> get() = order.filter { it !in hidden }

    /** What Today renders now. */
    fun visible(available: Set<TodayCard>): List<TodayCard> = shown.filter { it in available }

    /** Move [card] to position [index] of [shown] (edit mode's list). */
    fun move(card: TodayCard, index: Int): TodayLayout {
        val list = shown.toMutableList()
        if (!list.remove(card)) return this
        list.add(index.coerceIn(0, list.size), card)
        return copy(order = list + order.filter { it in hidden })
    }

    fun hide(card: TodayCard) = copy(hidden = hidden + card)

    /** Back from the library: at the end of what is shown. */
    fun show(card: TodayCard) = copy(order = shown + card + order.filter { it in hidden && it != card }, hidden = hidden - card)

    fun reset() = DEFAULT.copy(ringCompact = ringCompact)

    fun layoutValue(): String? = if (order == DEFAULT.order && hidden == DEFAULT.hidden) null
        else order.joinToString(",") { (if (it in hidden) "!" else "") + it.key }

    fun ringValue(): String? = if (ringCompact) "compact" else null

    companion object {
        val DEFAULT = TodayLayout(TodayCard.entries.toList(), TodayCard.entries.filter { !it.defaultOn }.toSet())

        fun from(settings: Map<String, String>): TodayLayout {
            val compact = settings[SettingKeys.TODAY_RING] == "compact"
            val raw = settings[SettingKeys.TODAY_LAYOUT] ?: return DEFAULT.copy(ringCompact = compact)
            val order = mutableListOf<TodayCard>()
            val hidden = mutableSetOf<TodayCard>()
            for (part in raw.split(',')) {
                val off = part.startsWith("!")
                val card = TodayCard.of(part.removePrefix("!").trim()) ?: continue
                if (card in order) continue
                order += card
                if (off) hidden += card
            }
            // cards this file doesn't know yet: default position (after their default predecessor), default visibility
            for (card in TodayCard.entries) if (card !in order) {
                val before = TodayCard.entries.subList(0, card.ordinal).lastOrNull { it in order }
                order.add(before?.let { order.indexOf(it) + 1 } ?: 0, card)
                if (!card.defaultOn) hidden += card
            }
            return TodayLayout(order, hidden, compact)
        }
    }
}

/** One-tap chips on Today and the log sheet's „Schnellauswahl“: symptoms, feelings or tags, at most [MAX]. */
object QuickPicks {
    const val MAX = 8
    private val KNOWN = Catalog.SYMPTOMS + Catalog.EMOTIONS + Catalog.TAGS

    /** Missing setting = default ([Catalog.PINNED]); "" = the person chose none. */
    fun from(settings: Map<String, String>): List<String> {
        val raw = settings[SettingKeys.QUICK_PICKS] ?: return Catalog.PINNED
        return raw.split(',').map { it.trim() }.filter { it in KNOWN }.distinct().take(MAX)
    }

    fun value(picks: List<String>): String? {
        val clean = picks.filter { it in KNOWN }.distinct().take(MAX)
        return if (clean == Catalog.PINNED) null else clean.joinToString(",")
    }

    /** The symptoms among the picks (the log sheet's pinned row). */
    fun symptoms(picks: List<String>) = picks.filter { it in Catalog.SYMPTOMS }

    enum class Kind { SYMPTOM, EMOTION, TAG }
    fun kind(key: String) = when (key) { in Catalog.EMOTIONS -> Kind.EMOTION; in Catalog.TAGS -> Kind.TAG; else -> Kind.SYMPTOM }
}
