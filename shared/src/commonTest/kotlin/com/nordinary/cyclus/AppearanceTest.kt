package com.nordinary.cyclus

import com.nordinary.cyclus.presenter.Appearance
import com.nordinary.cyclus.presenter.AppearanceMode
import com.nordinary.cyclus.presenter.CycleColors
import com.nordinary.cyclus.presenter.Oklch
import com.nordinary.cyclus.presenter.Palette
import com.nordinary.cyclus.presenter.ThemeChoice
import com.nordinary.cyclus.presenter.ThemeTokens
import com.nordinary.cyclus.presenter.Themes
import com.nordinary.cyclus.presenter.seedColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every theme stays readable, and the cycle colours keep their meaning and contrast on every theme's surfaces. */
class AppearanceTest {
    private fun hex(c: Long) = Appearance.hex(c)

    private fun check(t: ThemeTokens, dark: Boolean, name: String) {
        val surfaces = listOf(t.bg, t.surface, t.surface2)
        for (s in surfaces) {
            fun need(fg: Long, min: Double, what: String) =
                assertTrue(Themes.contrast(fg, s) >= min, "$name ${if (dark) "dark" else "light"}: $what ${hex(fg)} on ${hex(s)} = ${Themes.contrast(fg, s)}")
            need(t.ink, 7.0, "ink"); need(t.ink2, 4.5, "ink2")
            need(t.accent, 4.5, "accent"); need(t.accent2, 4.5, "accent2")
            val cyc = CycleColors.of(dark)
            need(cyc.period, 3.0, "period"); need(cyc.pre, 3.0, "pre"); need(cyc.fertile, 3.0, "fertile")
            need(cyc.danger, 4.5, "danger"); need(t.mid, 3.0, "mid (ring segments)")
        }
        assertTrue(Themes.contrast(t.inverseAccent, t.ink) >= 4.5, "$name: inverseAccent on ink (snackbar) ${hex(t.inverseAccent)} on ${hex(t.ink)}")
        assertTrue(Themes.contrast(t.ink2, t.accentContainer) >= 4.5, "$name: ink2 on accentContainer")
        assertTrue(Themes.contrast(t.onAccent, t.accent) >= 4.5, "$name: onAccent on accent")
        assertTrue(Themes.contrast(t.onAccent2, t.accent2) >= 4.5, "$name: onAccent2 on accent2")
        assertTrue(Themes.contrast(t.onAccentContainer, t.accentContainer) >= 4.5, "$name: onAccentContainer")
        assertTrue(Themes.contrast(t.onAccent2Container, t.accent2Container) >= 4.5, "$name: onAccent2Container")
        assertTrue(Themes.contrast(t.ink, t.accentContainer) >= 4.5, "$name: ink on accentContainer (selected chips)")
    }

    @Test fun everyPaletteIsReadableInLightAndDark() {
        for (p in Palette.entries) for (dark in listOf(false, true)) check(Themes.tokens(p, dark), dark, p.key)
    }

    @Test fun accentsKeepDistanceFromFixedMeanings() {
        // a theme's accent must not pass for a cycle meaning (hue distance in OKLCH, skipping Rose = the brand/period red)
        val meanings = listOf(0xFF6A4C9CL, 0xFF13706FL, 0xFF2E7D5BL).map { Oklch.of(it).h }
        for (p in Palette.entries - Palette.ROSE - Palette.GRAPHITE) for (dark in listOf(false, true)) {
            val t = Themes.tokens(p, dark)
            for (accent in listOf(t.accent, t.accent2)) for (m in meanings) {
                val d = kotlin.math.abs(((Oklch.of(accent).h - m + 540) % 360) - 180)
                assertTrue(d >= 25, "${p.key}: ${hex(accent)} is ${d.toInt()}° from a meaning colour")
            }
        }
    }

    @Test fun roseIsTodaysLook() {
        assertEquals(0xFFB3264EL, Themes.tokens(Palette.ROSE, false).accent)
        assertEquals(0xFFFBF8F5L, Themes.tokens(Palette.ROSE, false).bg)
        assertEquals(0xFFFF8FA8L, Themes.tokens(Palette.ROSE, true).accent)
    }

    @Test fun customColoursAreAlwaysCorrectedToReadable() {
        val seeds = buildList {
            for (h in 0 until 360 step 20) for (i in listOf(0.0, 0.5, 1.0)) add(seedColor(h.toDouble(), i))
            addAll(listOf(0xFFFFFF00L, 0xFFFFFFFFL, 0xFF000000L, 0xFF111111L, 0xFFF0F0F0L, 0xFF00FF00L, 0xFF0000FFL, 0xFFFF0000L))
        }
        seeds.forEachIndexed { i, a ->
            val b = seeds[(i + 7) % seeds.size]
            for (dark in listOf(false, true)) check(Themes.custom(a, b, dark), dark, "custom ${hex(a)}/${hex(b)}")
        }
        // a colour that is already fine stays exactly as chosen
        val fine = Themes.custom(0xFF1F6FB2L, 0xFF1B5E40L, false)
        assertEquals(0xFF1F6FB2L, fine.accent); assertTrue(!fine.adjusted)
        assertTrue(Themes.custom(0xFFFFFF00L, 0xFF2E7D5BL, false).adjusted)
    }

    @Test fun oklchRoundTrips() {
        for (c in listOf(0xFFB3264EL, 0xFF13706FL, 0xFFFBF8F5L, 0xFF141114L, 0xFF5B5BD6L)) {
            assertEquals(c, Oklch.of(c).toArgb(), hex(c))
        }
    }

    @Test fun storedValueRoundTripsAndBadInputFallsBack() {
        val all = listOf(
            Appearance(), Appearance(AppearanceMode.DARK, ThemeChoice.Dynamic),
            Appearance(AppearanceMode.LIGHT, ThemeChoice.Preset(Palette.SAGE)),
            Appearance(AppearanceMode.SYSTEM, ThemeChoice.Custom(0xFF3F51B5L, 0xFF00897BL)),
        )
        for (a in all) assertEquals(a, Appearance.decode(a.encode()))
        for (bad in listOf(null, "", "x", "dark|nope", "light|custom:#12:#zz", "|||")) {
            val d = Appearance.decode(bad)
            assertEquals(ThemeChoice.Preset(Palette.ROSE), d.choice, bad.toString())
        }
        assertEquals(AppearanceMode.DARK, Appearance.decode("dark|nope").mode)
    }
}
