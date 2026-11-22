package com.nordinary.cyclus.presenter

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Colour themes (Settings › Erscheinungsbild). A theme changes the accent roles and the tint of the neutral surfaces;
 * the cycle colours (period, pre-period, fertile, success, warning) keep their meaning and contrast in every theme
 * ([CycleColors]). Colours are ARGB `Long`s so the logic stays platform-free (iOS reuses it).
 */
enum class AppearanceMode(val key: String) { SYSTEM("system"), LIGHT("light"), DARK("dark") }

enum class Palette(val key: String) { ROSE("rose"), LAVENDER("lavender"), SAGE("sage"), OCEAN("ocean"), TERRACOTTA("terracotta"), GRAPHITE("graphite") }

sealed interface ThemeChoice {
    data class Preset(val palette: Palette) : ThemeChoice
    /** Accent and surfaces from the wallpaper (Android 12+); the platform supplies them. */
    data object Dynamic : ThemeChoice
    data class Custom(val primary: Long, val secondary: Long) : ThemeChoice
}

/** The stored appearance: one line, e.g. `system|rose`, `dark|dynamic`, `light|custom:#3F51B5:#00897B`. */
data class Appearance(val mode: AppearanceMode = AppearanceMode.SYSTEM, val choice: ThemeChoice = ThemeChoice.Preset(Palette.ROSE)) {
    fun encode(): String = mode.key + "|" + when (choice) {
        is ThemeChoice.Preset -> choice.palette.key
        ThemeChoice.Dynamic -> "dynamic"
        is ThemeChoice.Custom -> "custom:${hex(choice.primary)}:${hex(choice.secondary)}"
    }

    companion object {
        val DEFAULT = Appearance()

        /** Anything unreadable falls back to the default (never crashes the first frame). */
        fun decode(s: String?): Appearance {
            if (s.isNullOrBlank()) return DEFAULT
            val parts = s.split('|')
            val mode = AppearanceMode.entries.firstOrNull { it.key == parts.getOrNull(0) } ?: AppearanceMode.SYSTEM
            val c = parts.getOrNull(1).orEmpty()
            val choice = when {
                c == "dynamic" -> ThemeChoice.Dynamic
                c.startsWith("custom:") -> c.removePrefix("custom:").split(':').let { p ->
                    val a = p.getOrNull(0)?.let(::parseHex); val b = p.getOrNull(1)?.let(::parseHex)
                    if (a != null && b != null) ThemeChoice.Custom(a, b) else null
                }
                else -> Palette.entries.firstOrNull { it.key == c }?.let { ThemeChoice.Preset(it) }
            } ?: ThemeChoice.Preset(Palette.ROSE)
            return Appearance(mode, choice)
        }

        fun hex(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
        fun parseHex(s: String): Long? = s.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000L or it }
    }
}

/** Every role a theme decides. `on*` = text/icons on that colour. */
data class ThemeTokens(
    val bg: Long, val surface: Long, val surface2: Long, val ink: Long, val ink2: Long, val line: Long, val mid: Long,
    val accent: Long, val onAccent: Long, val accentContainer: Long, val onAccentContainer: Long,
    val accent2: Long, val onAccent2: Long, val accent2Container: Long, val onAccent2Container: Long,
    /** Accent on an inverse surface (snackbar action on ink): the other mode's accent. */
    val inverseAccent: Long = accent,
    /** The accent had to be made lighter/darker than the chosen colour to stay readable (custom colours). */
    val adjusted: Boolean = false,
)

/**
 * Colours that carry meaning; identical in every theme (DESIGN_SPEC §5.1, contrast-verified). [danger] marks errors
 * and destructive actions (Material `error`), so a green theme never turns „Alles löschen“ green.
 */
data class CycleColors(
    val period: Long, val onPeriod: Long, val pre: Long, val fertile: Long, val success: Long, val warning: Long,
    val danger: Long, val onDanger: Long,
) {
    companion object {
        val LIGHT = CycleColors(0xFFB3264EL, 0xFFFFFFFFL, 0xFF6A4C9CL, 0xFF13706FL, 0xFF2E7D5BL, 0xFF8F5600L, 0xFFB3264EL, 0xFFFFFFFFL)
        val DARK = CycleColors(0xFFFF8FA8L, 0xFF141114L, 0xFFBBA6ECL, 0xFF5FD0C8L, 0xFF6FCF9FL, 0xFFF2B65AL, 0xFFFF8FA8L, 0xFF141114L)
        fun of(dark: Boolean) = if (dark) DARK else LIGHT
    }
}

object Themes {
    /** Today's look, unchanged (light and dark). */
    val ROSE_LIGHT = ThemeTokens(
        bg = 0xFFFBF8F5L, surface = 0xFFFFFFFFL, surface2 = 0xFFF3EEEAL, ink = 0xFF1D1A1CL, ink2 = 0xFF6B6267L, line = 0xFFE6DED8L, mid = 0xFF8A7F76L,
        accent = 0xFFB3264EL, onAccent = 0xFFFFFFFFL, accentContainer = 0xFFF4E1E6L, onAccentContainer = 0xFF5C0F26L,
        accent2 = 0xFF7D4F67L, onAccent2 = 0xFFFFFFFFL, accent2Container = 0xFFF2E3EBL, onAccent2Container = 0xFF3F1F31L,
    )
    val ROSE_DARK = ThemeTokens(
        bg = 0xFF141114L, surface = 0xFF1E1A1EL, surface2 = 0xFF29242AL, ink = 0xFFF4EFF1L, ink2 = 0xFFABA1A6L, line = 0xFF3A3438L, mid = 0xFF8F858AL,
        accent = 0xFFFF8FA8L, onAccent = 0xFF141114L, accentContainer = 0xFF43202BL, onAccentContainer = 0xFFFFD9E1L,
        accent2 = 0xFFE3B5CBL, onAccent2 = 0xFF141114L, accent2Container = 0xFF3B2632L, onAccent2Container = 0xFFF6DDEAL,
    )

    /** Seeds for the generated palettes: accent, secondary, neutral tint hue (OKLCH degrees). */
    private class Seed(val accent: Long, val accent2: Long, val neutralHue: Double, val neutralChroma: Double = 0.012)
    private val SEEDS = mapOf(
        // accents keep a clear distance from the fixed meanings (period rose, pre-period violet, fertile teal, success green)
        Palette.LAVENDER to Seed(0xFF4F63D2L, 0xFF8C5E7EL, 275.0),
        Palette.SAGE to Seed(0xFF5A7A3AL, 0xFF7A6A4FL, 125.0),
        Palette.OCEAN to Seed(0xFF1F6FB2L, 0xFF33507AL, 240.0),
        Palette.TERRACOTTA to Seed(0xFFB4532AL, 0xFF9A6B2EL, 50.0),
        Palette.GRAPHITE to Seed(0xFF3F4650L, 0xFF5E6672L, 250.0, neutralChroma = 0.006),
    )

    fun tokens(palette: Palette, dark: Boolean): ThemeTokens = base(palette, dark).copy(inverseAccent = base(palette, !dark).accent)

    private fun base(palette: Palette, dark: Boolean): ThemeTokens =
        if (palette == Palette.ROSE) (if (dark) ROSE_DARK else ROSE_LIGHT)
        else SEEDS.getValue(palette).let { generate(it.accent, it.accent2, it.neutralHue, it.neutralChroma, dark) }

    /** Custom colours: neutrals tinted towards the primary's hue, accents corrected for contrast if needed. */
    fun custom(primary: Long, secondary: Long, dark: Boolean): ThemeTokens =
        generate(primary, secondary, Oklch.of(primary).h, 0.010, dark)
            .copy(inverseAccent = generate(primary, secondary, Oklch.of(primary).h, 0.010, !dark).accent)

    // Lightness steps of today's Rose neutrals, so every theme keeps the same rhythm and contrast.
    private val LIGHT_N = doubleArrayOf(0.983, 1.0, 0.951, 0.215, 0.505, 0.905, 0.60)
    private val DARK_N = doubleArrayOf(0.170, 0.210, 0.250, 0.955, 0.725, 0.320, 0.62)

    private fun generate(accentSeed: Long, accent2Seed: Long, nh: Double, nc: Double, dark: Boolean): ThemeTokens {
        val n = if (dark) DARK_N else LIGHT_N
        fun neutral(l: Double, c: Double = nc) = Oklch(l, if (l >= 0.999) 0.0 else c, nh).toArgb()
        val bg = neutral(n[0]); val surface = neutral(n[1]); val surface2 = neutral(n[2])
        val ink = neutral(n[3], nc * 0.8); val ink2 = neutral(n[4], nc * 1.2); val line = neutral(n[5]); val mid = neutral(n[6])
        val onAccent = if (dark) bg else 0xFFFFFFFFL
        val a1 = accentFor(accentSeed, dark, listOf(bg, surface, surface2), onAccent)
        val a2 = accentFor(accent2Seed, dark, listOf(bg, surface, surface2), onAccent)
        val c1 = container(a1.color, dark); val c2 = container(a2.color, dark)
        return ThemeTokens(
            bg, surface, surface2, ink, ink2, line, mid,
            a1.color, onAccent, c1.first, c1.second, a2.color, onAccent, c2.first, c2.second,
            adjusted = a1.adjusted || a2.adjusted,
        )
    }

    data class Accent(val color: Long, val adjusted: Boolean)

    /**
     * Keeps the seed's hue (and chroma where the gamut allows) and moves only lightness until the accent reads as text
     * on every surface (≥ 4.5:1) and [onAccent] reads on it (≥ 4.5:1). [adjusted] tells the UI it had to move.
     */
    fun accentFor(seed: Long, dark: Boolean, surfaces: List<Long>, onAccent: Long): Accent {
        val s = Oklch.of(seed)
        fun ok(c: Long) = surfaces.all { contrast(c, it) >= 4.5 } && contrast(onAccent, c) >= 4.5
        if (ok(seed)) return Accent(seed, false)
        // walk lightness towards the readable side (darker on light themes, lighter on dark ones)
        val step = if (dark) 0.01 else -0.01
        var l = s.l
        repeat(100) {
            l = (l + step).coerceIn(0.0, 1.0)
            val c = Oklch(l, s.c, s.h).toArgb()
            if (ok(c)) return Accent(c, true)
        }
        return Accent(if (dark) 0xFFFFFFFFL else 0xFF000000L, true)
    }

    /** Tinted container (chips, selected states) and its text colour. */
    private fun container(accent: Long, dark: Boolean): Pair<Long, Long> {
        val a = Oklch.of(accent)
        val c = min(a.c * 0.35, 0.05)
        return if (dark) Oklch(0.31, c, a.h).toArgb() to Oklch(0.90, min(a.c * 0.4, 0.06), a.h).toArgb()
        else Oklch(0.935, c, a.h).toArgb() to Oklch(0.32, min(a.c, 0.12), a.h).toArgb()
    }

    /** WCAG 2 contrast ratio. */
    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    fun luminance(argb: Long): Double {
        val (r, g, b) = Oklch.linear(argb)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}

/** OKLCH (Björn Ottosson's OKLab in polar form): perceptually even lightness, so steps look alike across hues. */
data class Oklch(val l: Double, val c: Double, val h: Double) {
    /** sRGB, reducing chroma until the colour fits the gamut (hue and lightness are kept). */
    fun toArgb(): Long {
        var chroma = c
        repeat(40) {
            val rgb = toLinear(l, chroma, h)
            if (rgb.all { it in -0.0001..1.0001 }) return pack(rgb)
            chroma *= 0.9
        }
        return pack(toLinear(l, 0.0, h))
    }

    companion object {
        fun of(argb: Long): Oklch {
            val (r, g, b) = linear(argb)
            val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            val L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
            val A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
            val B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
            val h = (atan2(B, A) * 180 / kotlin.math.PI).let { if (it < 0) it + 360 else it }
            return Oklch(L, sqrt(A * A + B * B), h)
        }

        internal fun linear(argb: Long): Triple<Double, Double, Double> {
            fun ch(shift: Int) = ((argb shr shift) and 0xFF).toDouble() / 255.0
            fun lin(v: Double) = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            return Triple(lin(ch(16)), lin(ch(8)), lin(ch(0)))
        }

        private fun toLinear(L: Double, c: Double, h: Double): DoubleArray {
            val rad = h * kotlin.math.PI / 180
            val a = c * cos(rad); val b = c * sin(rad)
            val l = (L + 0.3963377774 * a + 0.2158037573 * b).let { it * it * it }
            val m = (L - 0.1055613458 * a - 0.0638541728 * b).let { it * it * it }
            val s = (L - 0.0894841775 * a - 1.2914855480 * b).let { it * it * it }
            return doubleArrayOf(
                4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
            )
        }

        private fun pack(rgb: DoubleArray): Long {
            fun enc(v: Double): Int {
                val x = v.coerceIn(0.0, 1.0)
                val s = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
                return (s * 255).roundToInt().coerceIn(0, 255)
            }
            return 0xFF000000L or (enc(rgb[0]).toLong() shl 16) or (enc(rgb[1]).toLong() shl 8) or enc(rgb[2]).toLong()
        }
    }
}

/** Hue/intensity picker helpers for custom colours: a seed from hue (0–360) and intensity (0–1). */
fun seedColor(hue: Double, intensity: Double): Long = Oklch(0.55, 0.02 + 0.16 * intensity.coerceIn(0.0, 1.0), ((hue % 360) + 360) % 360).toArgb()

/** Inverse of [seedColor] for an existing colour (slider positions). */
fun seedPosition(argb: Long): Pair<Double, Double> = Oklch.of(argb).let { it.h to ((it.c - 0.02) / 0.16).coerceIn(0.0, 1.0) }

