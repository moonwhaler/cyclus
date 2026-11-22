package com.nordinary.cyclus.presenter

import com.nordinary.cyclus.core.EpisodeKind

/**
 * Life stages the person can pick in Settings (prototype `renderSettings`). Each one pauses predictions as an
 * episode. Conditions (perimenopause, PCOS, endometriosis, testosterone) are not offered until the engine
 * really widens ranges for them (trust rule: no claims the app doesn't keep).
 */
enum class LifeStage(val key: String, val episode: EpisodeKind) {
    HBC("hbc", EpisodeKind.HORMONAL_CONTRACEPTION),
    PREGNANT("preg", EpisodeKind.PREGNANCY),
    POSTPARTUM("pp", EpisodeKind.POSTPARTUM);

    /** Translation key (`ep_<key>`). */
    val labelKey: String get() = "ep_$key"

    companion object {
        fun of(kind: EpisodeKind?): LifeStage? = entries.firstOrNull { it.episode == kind }
        const val NONE_LABEL = "ep_none"
    }
}
