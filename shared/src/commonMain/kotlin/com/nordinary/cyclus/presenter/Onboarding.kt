package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.core.CycleOverride
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Episode
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.store.CyclusRepository
import kotlin.math.min

/** What the person told the onboarding (spec §7.1). Nothing is guessed: unknown stays unknown. */
data class OnboardingChoices(
    val lastPeriodStart: Day? = null,
    /** „Blutest du noch?“ → every day from the start until today counts as bleeding (max 10 days). */
    val stillBleeding: Boolean = false,
    /** „Wie viele Tage ungefähr?“ when it is over. Null = not answered → only the start day. */
    val durationDays: Int? = null,
    val conceive: Boolean = false,
    val stage: LifeStage? = null,
)

object Onboarding {
    const val DONE = "onboarding.done"
    const val MAX_SEED_DAYS = 10

    /** Show onboarding only for a truly empty store (fresh install or after erase). */
    fun shouldShow(logs: Map<Day, DayLog>, overrides: Map<Day, CycleOverride>, episodes: List<Episode>, settings: Map<String, String>) =
        settings[DONE] != "1" && logs.isEmpty() && overrides.isEmpty() && episodes.isEmpty() && settings.keys.none { it in SettingKeys.USER_PREFS }

    /** Kinderwunsch can't be combined with hormonal contraception or pregnancy. */
    fun conceiveAllowed(stage: LifeStage?) = stage != LifeStage.HBC && stage != LifeStage.PREGNANT

    /** Bleeding days the answers state, as unspecified flow (no invented levels). */
    fun seedDays(c: OnboardingChoices, today: Day): List<Day> {
        val start = c.lastPeriodStart ?: return emptyList()
        if (start > today) return emptyList()
        val end = when {
            c.stillBleeding -> today
            c.durationDays != null -> start + c.durationDays.coerceIn(1, MAX_SEED_DAYS) - 1
            else -> start
        }
        return (start..min(min(end, today), start + MAX_SEED_DAYS - 1)).toList()
    }

    suspend fun apply(repo: CyclusRepository, c: OnboardingChoices, today: Day, newId: () -> String) {
        for (d in seedDays(c, today)) {
            val existing = repo.log(d) ?: DayLog.EMPTY
            if (existing.flow == null) repo.saveLog(d, existing.copy(flow = Flow.UNSPECIFIED))
        }
        if (c.conceive && conceiveAllowed(c.stage)) repo.setSetting(SettingKeys.MODE, "conceive")
        c.stage?.let { repo.saveEpisode(Episode(newId(), it.episode, today)) }
        repo.setSetting(DONE, "1")
    }
}
