package com.nordinary.cyclus.ui.theme

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

/** Prototype tokens (DESIGN_SPEC §5.1, contrast-verified). Cycle colours carry meaning and never change. */
@Immutable
data class CyclusColors(
    val bg: Color, val surface: Color, val surface2: Color, val ink: Color, val ink2: Color, val line: Color,
    val period: Color, val pre: Color, val mid: Color, val fertile: Color, val success: Color, val warning: Color,
    val onAccent: Color,
)

val LightCyclus = CyclusColors(
    bg = Color(0xFFFBF8F5), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF3EEEA), ink = Color(0xFF1D1A1C),
    ink2 = Color(0xFF6B6267), line = Color(0xFFE6DED8), period = Color(0xFFB3264E), pre = Color(0xFF6A4C9C),
    mid = Color(0xFF8A7F76), fertile = Color(0xFF13706F), success = Color(0xFF2E7D5B), warning = Color(0xFF8F5600),
    onAccent = Color(0xFFFFFFFF),
)

val DarkCyclus = CyclusColors(
    bg = Color(0xFF141114), surface = Color(0xFF1E1A1E), surface2 = Color(0xFF29242A), ink = Color(0xFFF4EFF1),
    ink2 = Color(0xFFABA1A6), line = Color(0xFF3A3438), period = Color(0xFFFF8FA8), pre = Color(0xFFBBA6EC),
    mid = Color(0xFF8F858A), fertile = Color(0xFF5FD0C8), success = Color(0xFF6FCF9F), warning = Color(0xFFF2B65A),
    onAccent = Color(0xFF141114),
)

val LocalCyclusColors = staticCompositionLocalOf { LightCyclus }

/** ANDROID_PLAN §3.1 colour-role mapping. */
private fun scheme(c: CyclusColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.period, onPrimary = c.onAccent,
        primaryContainer = if (dark) Color(0xFF43202B) else Color(0xFFF4E1E6),
        onPrimaryContainer = if (dark) Color(0xFFFFD9E1) else Color(0xFF5C0F26),
        secondary = c.pre, onSecondary = c.onAccent,
        tertiary = c.fertile, onTertiary = c.onAccent,
        background = c.bg, onBackground = c.ink, surface = c.bg, onSurface = c.ink, onSurfaceVariant = c.ink2,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface2,
        surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface2, surfaceVariant = c.surface2,
        outline = c.mid, outlineVariant = c.line,
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
    val b = Typography()
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CyclusTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkCyclus else LightCyclus
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
