package com.nordinary.cyclus.ui.theme

import com.nordinary.cyclus.presenter.Themes
import com.nordinary.cyclus.presenter.ThemeTokens
import com.nordinary.cyclus.presenter.ThemeChoice
import com.nordinary.cyclus.presenter.Palette
import com.nordinary.cyclus.presenter.CycleColors
import com.nordinary.cyclus.presenter.AppearanceMode
import com.nordinary.cyclus.presenter.Appearance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.R

/**
 * Colour roles (DESIGN_SPEC §5.1). The theme decides the neutrals and the accent roles (Settings › Erscheinungsbild);
 * [period], [pre], [fertile], [success], [warning] carry meaning and are the same in every theme.
 * Use [accent] for interaction (buttons, links, selection) and [period] only where something means bleeding.
 */
@Immutable
data class CyclusColors(
    val bg: Color, val surface: Color, val surface2: Color, val ink: Color, val ink2: Color, val line: Color, val mid: Color,
    val accent: Color, val onAccent: Color, val accentContainer: Color, val onAccentContainer: Color,
    val accent2: Color, val onAccent2: Color, val accent2Container: Color, val onAccent2Container: Color,
    /** Accent on inverse surfaces (snackbar action on [ink]). */
    val inverseAccent: Color,
    val period: Color, val onPeriod: Color, val pre: Color, val fertile: Color, val success: Color, val warning: Color,
    /** Errors and destructive actions; fixed like the cycle colours. */
    val danger: Color, val onDanger: Color,
    val dark: Boolean,
)

private fun Long.c() = Color(this.toInt())

fun cyclusColors(t: ThemeTokens, dark: Boolean): CyclusColors {
    val cyc = CycleColors.of(dark)
    return CyclusColors(
        bg = t.bg.c(), surface = t.surface.c(), surface2 = t.surface2.c(), ink = t.ink.c(), ink2 = t.ink2.c(), line = t.line.c(), mid = t.mid.c(),
        accent = t.accent.c(), onAccent = t.onAccent.c(), accentContainer = t.accentContainer.c(), onAccentContainer = t.onAccentContainer.c(),
        accent2 = t.accent2.c(), onAccent2 = t.onAccent2.c(), accent2Container = t.accent2Container.c(), onAccent2Container = t.onAccent2Container.c(),
        inverseAccent = t.inverseAccent.c(),
        period = cyc.period.c(), onPeriod = cyc.onPeriod.c(), pre = cyc.pre.c(), fertile = cyc.fertile.c(),
        success = cyc.success.c(), warning = cyc.warning.c(), danger = cyc.danger.c(), onDanger = cyc.onDanger.c(), dark = dark,
    )
}

val LightCyclus = cyclusColors(Themes.ROSE_LIGHT, dark = false)
val DarkCyclus = cyclusColors(Themes.ROSE_DARK, dark = true)

val LocalCyclusColors = staticCompositionLocalOf { LightCyclus }

/**
 * Material You (ANDROID_PLAN D13): accents and surfaces from the wallpaper's scheme — bg = surface, cards =
 * surfaceContainerLowest, surface2 = surfaceContainer, ink = onSurface, ink2 = onSurfaceVariant, line = outlineVariant,
 * mid = outline, inverse accent = inversePrimary. Cycle colours and danger stay fixed.
 */
private fun fromDynamic(m: ColorScheme, dark: Boolean): CyclusColors {
    val cyc = CycleColors.of(dark)
    return CyclusColors(
        bg = m.surface, surface = m.surfaceContainerLowest.let { if (dark) m.surfaceContainerLow else it },
        surface2 = m.surfaceContainer, ink = m.onSurface, ink2 = m.onSurfaceVariant, line = m.outlineVariant, mid = m.outline,
        accent = m.primary, onAccent = m.onPrimary, accentContainer = m.primaryContainer, onAccentContainer = m.onPrimaryContainer,
        accent2 = m.secondary, onAccent2 = m.onSecondary, accent2Container = m.secondaryContainer, onAccent2Container = m.onSecondaryContainer,
        inverseAccent = m.inversePrimary,
        period = cyc.period.c(), onPeriod = cyc.onPeriod.c(), pre = cyc.pre.c(), fertile = cyc.fertile.c(),
        success = cyc.success.c(), warning = cyc.warning.c(), danger = cyc.danger.c(), onDanger = cyc.onDanger.c(), dark = dark,
    )
}

/** ANDROID_PLAN §3.1 colour-role mapping. */
private fun scheme(c: CyclusColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = c.accentContainer, onPrimaryContainer = c.onAccentContainer,
        secondary = c.accent2, onSecondary = c.onAccent2, secondaryContainer = c.accent2Container, onSecondaryContainer = c.onAccent2Container,
        tertiary = c.fertile, onTertiary = c.onPeriod,
        background = c.bg, onBackground = c.ink, surface = c.bg, onSurface = c.ink, onSurfaceVariant = c.ink2,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface2,
        surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface2, surfaceVariant = c.surface2,
        outline = c.mid, outlineVariant = c.line,
        inverseSurface = c.ink, inverseOnSurface = c.bg, inversePrimary = c.inverseAccent,
        error = c.danger, onError = c.onDanger,
    )
}

private fun flex(weight: Int) = Font(
    R.font.roboto_flex, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)
private fun nunito(weight: Int) = Font(
    R.font.nunito, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Bundled so OEM system fonts don't change the look (ANDROID_PLAN §3.3). */
val RobotoFlex = FontFamily(flex(400), flex(500), flex(600), flex(700), flex(800))
/** Rounded numerals, as in the prototype. */
val Nunito = FontFamily(nunito(600), nunito(700), nunito(800))

private fun typography(): Typography {
    // Line breaking for six languages: headings balanced (no lone last word), body text high quality with
    // hyphenation (long German, French, Italian … words), short UI labels high quality without hyphens.
    val b0 = Typography()
    fun TextStyle.heading() = copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading)
    fun TextStyle.para() = copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph, hyphens = androidx.compose.ui.text.style.Hyphens.Auto)
    fun TextStyle.label() = copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph)
    val b = Typography(
        displayLarge = b0.displayLarge.heading(), displayMedium = b0.displayMedium.heading(), displaySmall = b0.displaySmall.heading(),
        headlineLarge = b0.headlineLarge.heading(), headlineMedium = b0.headlineMedium.heading(), headlineSmall = b0.headlineSmall.heading(),
        titleLarge = b0.titleLarge.heading(), titleMedium = b0.titleMedium.label(), titleSmall = b0.titleSmall.label(),
        bodyLarge = b0.bodyLarge.para(), bodyMedium = b0.bodyMedium.para(), bodySmall = b0.bodySmall.para(),
        labelLarge = b0.labelLarge.label(), labelMedium = b0.labelMedium.label(), labelSmall = b0.labelSmall.label(),
    )
    fun TextStyle.f() = copy(fontFamily = RobotoFlex)
    return Typography(
        displayLarge = b.displayLarge.f(), displayMedium = b.displayMedium.f(), displaySmall = b.displaySmall.f(),
        headlineLarge = b.headlineLarge.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.3).sp),
        headlineMedium = b.headlineMedium.f(), headlineSmall = b.headlineSmall.f(),
        titleLarge = b.titleLarge.f(),
        titleMedium = b.titleMedium.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        titleSmall = b.titleSmall.f(),
        bodyLarge = b.bodyLarge.copy(fontFamily = RobotoFlex, fontSize = 15.sp, lineHeight = 1.4.em),
        bodyMedium = b.bodyMedium.copy(fontFamily = RobotoFlex, fontSize = 14.sp, lineHeight = 1.4.em),
        bodySmall = b.bodySmall.copy(fontFamily = RobotoFlex, fontSize = 12.sp, lineHeight = 1.4.em),
        labelLarge = b.labelLarge.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = b.labelMedium.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
        labelSmall = b.labelSmall.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.6.sp),
    )
}

/** Prototype radii: cards 16, sheets 24, chips full. */
private val CyclusShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
)

/** Whether the chosen appearance is dark right now (the system setting for „System“). */
@Composable
fun isCyclusDark(appearance: Appearance): Boolean = when (appearance.mode) {
    AppearanceMode.LIGHT -> false
    AppearanceMode.DARK -> true
    AppearanceMode.SYSTEM -> isSystemInDarkTheme()
}

/** Resolves an appearance to colours (dynamic falls back to Rose before Android 12). */
@Composable
fun resolveColors(appearance: Appearance, dark: Boolean): CyclusColors {
    val ctx = LocalContext.current
    return when (val choice = appearance.choice) {
        is ThemeChoice.Preset -> cyclusColors(Themes.tokens(choice.palette, dark), dark)
        is ThemeChoice.Custom -> cyclusColors(Themes.custom(choice.primary, choice.secondary, dark), dark)
        ThemeChoice.Dynamic ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) fromDynamic(if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx), dark)
            else cyclusColors(Themes.tokens(Palette.ROSE, dark), dark)
    }
}

/** Theme switches cross-fade every role instead of jumping. */
@Composable
private fun animated(c: CyclusColors): CyclusColors {
    val spec = tween<Color>(300)
    @Composable fun a(x: Color) = animateColorAsState(x, spec, label = "theme").value
    return CyclusColors(
        bg = a(c.bg), surface = a(c.surface), surface2 = a(c.surface2), ink = a(c.ink), ink2 = a(c.ink2), line = a(c.line), mid = a(c.mid),
        accent = a(c.accent), onAccent = a(c.onAccent), accentContainer = a(c.accentContainer), onAccentContainer = a(c.onAccentContainer),
        accent2 = a(c.accent2), onAccent2 = a(c.onAccent2), accent2Container = a(c.accent2Container), onAccent2Container = a(c.onAccent2Container),
        inverseAccent = a(c.inverseAccent),
        period = a(c.period), onPeriod = a(c.onPeriod), pre = a(c.pre), fertile = a(c.fertile), success = a(c.success), warning = a(c.warning),
        danger = a(c.danger), onDanger = a(c.onDanger), dark = c.dark,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CyclusTheme(appearance: Appearance = AppearanceStore.state.collectAsState().value, content: @Composable () -> Unit) {
    val dark = isCyclusDark(appearance)
    val c = animated(resolveColors(appearance, dark))
    CompositionLocalProvider(LocalCyclusColors provides c) {
        MaterialExpressiveTheme(
            colorScheme = scheme(c, dark),
            typography = typography(),
            shapes = CyclusShapes,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

object Cyclus {
    val colors: CyclusColors @Composable get() = LocalCyclusColors.current
}
