package com.nordinary.cyclus.core

/** Days are epoch days (days since 1970-01-01, civil date, no time zone), the same unit as the prototype's `D()`. */
typealias Day = Int

enum class Flow(val key: String) {
    NONE("none"), SPOTTING("spotting"), LIGHT("light"), MEDIUM("medium"), HEAVY("heavy"),
    /** Bleeding without a level, e.g. imported from a health store. Counts as bleeding (spec §11.5.1, m4). */
    UNSPECIFIED("unspecified");

    /** Bleeding that can start or continue a period (spotting and "none" do not). */
    val isBleed: Boolean get() = this != NONE && this != SPOTTING

    companion object {
        fun of(key: String): Flow = entries.first { it.key == key }
        fun ofOrNull(key: String): Flow? = entries.firstOrNull { it.key == key }
        /** Order shown in the log sheet (prototype `FLOWS`). */
        val logOrder = listOf(NONE, SPOTTING, LIGHT, MEDIUM, HEAVY)
    }
}

enum class Mucus(val key: String) {
    DRY("dry"), STICKY("sticky"), CREAMY("creamy"), WATERY("watery"), EGGWHITE("eggwhite"), UNUSUAL("unusual");

    val isFertile: Boolean get() = this == EGGWHITE || this == WATERY
    val isDry: Boolean get() = this == DRY || this == STICKY

    companion object {
        fun of(key: String): Mucus = entries.first { it.key == key }
        fun ofOrNull(key: String): Mucus? = entries.firstOrNull { it.key == key }
    }
}

enum class LhResult(val key: String) {
    NEGATIVE("neg"), POSITIVE("pos"), SURGE("surge");

    companion object {
        fun of(key: String): LhResult = entries.first { it.key == key }
        fun ofOrNull(key: String): LhResult? = entries.firstOrNull { it.key == key }
    }
}

enum class OverrideKind(val key: String) {
    /** "This day is a period start." */
    START("start"),
    /** "This day is not a period start." */
    NOT_START("not"),
    /** Exclude the cycle starting here from statistics (reason in [CycleOverride.reason]). Not used by the engines yet. */
    EXCLUDE("exclude");

    companion object {
        fun of(key: String): OverrideKind = entries.first { it.key == key }
        fun ofOrNull(key: String): OverrideKind? = entries.firstOrNull { it.key == key }
    }
}

data class CycleOverride(val kind: OverrideKind, val reason: String? = null)

enum class EpisodeKind(val key: String) {
    HORMONAL_CONTRACEPTION("hbc"), PREGNANCY("pregnancy"), POSTPARTUM("postpartum"), BREASTFEEDING("breastfeeding"),
    PERIMENOPAUSE("perimenopause"), GENDER_AFFIRMING_HORMONES("gah"), OTHER("other");

    companion object {
        fun of(key: String): EpisodeKind = entries.first { it.key == key }
        fun ofOrNull(key: String): EpisodeKind? = entries.firstOrNull { it.key == key }
    }
}

data class Episode(val id: String, val kind: EpisodeKind, val start: Day, val end: Day? = null) {
    fun isActiveOn(day: Day): Boolean = start <= day && (end == null || day <= end)
}

/**
 * One day's log. Item keys (symptoms, emotions, tags, pain regions) are the stable keys of [Catalog].
 * Mirrors DESIGN_SPEC §11.3 and the prototype's log object.
 */
data class DayLog(
    val flow: Flow? = null,
    val symptoms: Set<String> = emptySet(),
    /** Symptom key → severity 1…3 (optional, spec §11.3 `SymptomEntry.severity`). */
    val symptomSeverity: Map<String, Int> = emptyMap(),
    /** Pain region key → score 0…10. */
    val pain: Map<String, Int> = emptyMap(),
    val emotions: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val mood: Int? = null,
    val energy: Int? = null,
    val sleep: Int? = null,
    val libido: Int? = null,
    val mucus: Mucus? = null,
    val lh: LhResult? = null,
    /** Basal body temperature in °C. */
    val bbt: Double? = null,
    val sex: Boolean = false,
    val sexProtected: Boolean? = null,
    val note: String? = null,
    /** "Nothing else today": the person logged everything there was. */
    val nothingElse: Boolean = false,
) {
    val isBleed: Boolean get() = flow?.isBleed == true

    fun has(item: String): Boolean = item in symptoms || item in emotions

    fun scale(key: String): Int? = when (key) {
        "mood" -> mood; "energy" -> energy; "sleep" -> sleep; "libido" -> libido
        else -> error("unknown scale $key")
    }

    /** A day that counts as observed for pattern statistics (prototype `observed`). */
    val isObserved: Boolean
        get() = nothingElse || symptoms.isNotEmpty() || emotions.isNotEmpty() ||
            (mood ?: 0) != 0 || (energy ?: 0) != 0 || (sleep ?: 0) != 0

    val isEmpty: Boolean get() = this == EMPTY

    companion object { val EMPTY = DayLog() }
}

/** Stable item keys. Order matters: the pattern engine iterates in this order (it seeds its sampler per item). */
object Catalog {
    val SYMPTOMS = listOf(
        "Cramps", "Headache", "Tired", "Bloating", "Breast tenderness", "Acne", "Back pain", "Nausea", "Cravings",
        "Insomnia", "Hot flashes", "Night sweats", "Dizziness", "Diarrhea", "Constipation",
    )
    val EMOTIONS = listOf("Irritable", "Sad", "Anxious", "Sensitive", "Calm", "Happy")
    /** Built-in tags. User-defined tags (later) get UUID keys. */
    val TAGS = listOf("Stress", "Travel", "Alcohol", "Sport", "Sick")
    val PINNED = listOf("Cramps", "Headache", "Tired", "Bloating")
    val PAIN_REGIONS = listOf("head", "chest", "upper", "lowL", "lowC", "lowR", "pelvis", "thighs")
    val SCALES = listOf("mood", "energy", "sleep", "libido")
    const val STRESS = "Stress"

    fun isEmotion(key: String): Boolean = key in EMOTIONS
}
