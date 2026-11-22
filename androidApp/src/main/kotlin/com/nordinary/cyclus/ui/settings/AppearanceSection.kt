package com.nordinary.cyclus.ui.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.R
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.presenter.Appearance
import com.nordinary.cyclus.presenter.AppearanceMode
import com.nordinary.cyclus.presenter.CycleColors
import com.nordinary.cyclus.presenter.Palette
import com.nordinary.cyclus.presenter.ThemeChoice
import com.nordinary.cyclus.presenter.Themes
import com.nordinary.cyclus.presenter.seedColor
import com.nordinary.cyclus.presenter.seedPosition
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.isCyclusDark
import com.nordinary.cyclus.ui.today.PrimaryButton

private fun Long.c() = Color(this.toInt())

/** What a swatch shows: card surface, accent, accent container, secondary. */
private data class Swatch(val surface: Color, val accent: Color, val container: Color, val accent2: Color)

private fun paletteName(p: Palette) = when (p) {
    Palette.ROSE -> R.string.pal_rose; Palette.LAVENDER -> R.string.pal_lavender; Palette.SAGE -> R.string.pal_sage
    Palette.OCEAN -> R.string.pal_ocean; Palette.TERRACOTTA -> R.string.pal_terracotta; Palette.GRAPHITE -> R.string.pal_graphite
}

/**
 * Settings › Erscheinungsbild: light/dark/system, curated palettes, Material You (Android 12+) and custom colours.
 * Every change applies at once (the whole app cross-fades); the preview strip shows it in place.
 */
@Composable
fun AppearanceSection(fmt: Fmt, appearance: Appearance, onChange: (Appearance) -> Unit) {
    val c = Cyclus.colors
    val dark = isCyclusDark(appearance)
    var customOpen by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    Section(stringResource(R.string.ap_title)) {
        val modes = listOf(AppearanceMode.SYSTEM, AppearanceMode.LIGHT, AppearanceMode.DARK)
        Segments(
            listOf(stringResource(R.string.ap_mode_system), stringResource(R.string.ap_mode_light), stringResource(R.string.ap_mode_dark)),
            modes.indexOf(appearance.mode),
        ) { onChange(appearance.copy(mode = modes[it])) }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (p in Palette.entries) {
                val t = Themes.tokens(p, dark)
                SwatchCard(
                    stringResource(paletteName(p)), Swatch(t.surface.c(), t.accent.c(), t.accentContainer.c(), t.accent2.c()),
                    selected = appearance.choice == ThemeChoice.Preset(p),
                ) { onChange(appearance.copy(choice = ThemeChoice.Preset(p))) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val m = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                SwatchCard(
                    stringResource(R.string.pal_dynamic), Swatch(m.surfaceContainerLowest, m.primary, m.primaryContainer, m.secondary),
                    selected = appearance.choice == ThemeChoice.Dynamic,
                ) { onChange(appearance.copy(choice = ThemeChoice.Dynamic)) }
            }
            val custom = appearance.choice as? ThemeChoice.Custom
            val ct = custom?.let { Themes.custom(it.primary, it.secondary, dark) }
            SwatchCard(
                stringResource(R.string.pal_custom),
                ct?.let { Swatch(it.surface.c(), it.accent.c(), it.accentContainer.c(), it.accent2.c()) },
                selected = custom != null,
            ) { customOpen = true }
        }
        Preview(fmt)
        Text(stringResource(R.string.ap_note), Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = c.ink2)
    }
    if (customOpen) CustomColorsSheet(
        fmt, initial = appearance.choice as? ThemeChoice.Custom, dark = dark,
        onApply = { onChange(appearance.copy(choice = it)); customOpen = false },
        onDismiss = { customOpen = false },
    )
}

/** Palette card: the theme's surface with an accent / container disc and a secondary dot; [s] null = colour wheel. */
@Composable
private fun SwatchCard(name: String, s: Swatch?, selected: Boolean, onClick: () -> Unit) {
    val c = Cyclus.colors
    val ring by animateColorAsState(if (selected) c.accent else c.line, label = "swatch")
    val a11y = stringResource(R.string.ap_swatch, name)
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(18.dp))
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = a11y }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(s?.surface ?: c.surface)
                .border(if (selected) 2.5.dp else 1.dp, ring, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(40.dp)) {
                if (s == null) {
                    // custom: a hue wheel
                    drawCircle(Brush.sweepGradient((0..12).map { seedColor(it * 30.0, 1.0).c() }), radius = size.minDimension / 2)
                    drawCircle(Color.White.copy(alpha = .85f), radius = size.minDimension / 5)
                } else {
                    drawArc(s.accent, 135f, 180f, true)
                    drawArc(s.container, -45f, 180f, true)
                    drawCircle(s.accent2, radius = size.minDimension / 6, center = Offset(size.width * .78f, size.height * .78f))
                }
            }
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { Text("✓", color = c.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
        Text(name, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = c.ink, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** A tiny Today: ring arc with the fixed cycle colours, a selected chip, a link and the main button, in the current theme. */
@Composable
private fun Preview(fmt: Fmt) {
    val c = Cyclus.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(14.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Canvas(Modifier.size(56.dp)) {
            val st = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            val inset = 4.dp.toPx(); val sz = Size(size.width - 2 * inset, size.height - 2 * inset); val o = Offset(inset, inset)
            drawArc(c.line, 0f, 360f, false, o, sz, style = st)
            drawArc(c.period, 140f, 55f, false, o, sz, style = st)
            drawArc(c.fertile, 250f, 60f, false, o, sz, style = st)
            drawArc(c.pre, 20f, 40f, false, o, sz, style = st)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(CircleShape).background(c.accentContainer).border(1.dp, c.accent, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("✓ " + fmt.item("Cramps"), fontSize = 12.sp, color = c.ink, fontWeight = FontWeight.Medium)
                }
                Text(fmt.s("howWorks"), fontSize = 12.sp, color = c.accent, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(c.accent).padding(horizontal = 16.dp, vertical = 7.dp)) {
                Text(fmt.s("fab"), color = c.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Curated starting points for custom colours (hue, intensity). */
private val SEEDS = listOf(350.0, 20.0, 50.0, 85.0, 125.0, 165.0, 205.0, 240.0, 270.0, 305.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomColorsSheet(fmt: Fmt, initial: ThemeChoice.Custom?, dark: Boolean, onApply: (ThemeChoice.Custom) -> Unit, onDismiss: () -> Unit) {
    val c = Cyclus.colors
    val start1 = seedPosition(initial?.primary ?: seedColor(240.0, .8))
    val start2 = seedPosition(initial?.secondary ?: seedColor(300.0, .5))
    var which by remember { mutableIntStateOf(0) }
    var h1 by remember { mutableDoubleStateOf(start1.first) }; var i1 by remember { mutableDoubleStateOf(start1.second) }
    var h2 by remember { mutableDoubleStateOf(start2.first) }; var i2 by remember { mutableDoubleStateOf(start2.second) }
    val primary = seedColor(h1, i1); val secondary = seedColor(h2, i2)
    val light = Themes.custom(primary, secondary, false); val night = Themes.custom(primary, secondary, true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.cu_title), Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            TextButton(onClick = onDismiss) { Text(fmt.s("cancel"), color = c.accent, fontWeight = FontWeight.SemiBold) }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // light and dark side by side: both must work, whatever the phone shows now
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniPreview(stringResource(R.string.ap_mode_light), light, CycleColors.LIGHT, Modifier.weight(1f))
                MiniPreview(stringResource(R.string.ap_mode_dark), night, CycleColors.DARK, Modifier.weight(1f))
            }
            if (light.adjusted || night.adjusted) Text(stringResource(R.string.cu_adjusted), fontSize = 12.sp, color = c.ink2)
            Segments(listOf(stringResource(R.string.cu_primary), stringResource(R.string.cu_secondary)), which) { which = it }
            val (h, i) = if (which == 0) h1 to i1 else h2 to i2
            fun set(hue: Double, intensity: Double) { if (which == 0) { h1 = hue; i1 = intensity } else { h2 = hue; i2 = intensity } }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (hue in SEEDS) {
                    val col = seedColor(hue, .8)
                    val on = kotlin.math.abs(hue - h) < 1 && i > .75
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(col.c())
                            .border(if (on) 3.dp else 0.dp, c.ink, CircleShape)
                            .selectable(on, role = Role.RadioButton) { set(hue, .8) }
                            .semantics { contentDescription = "${hue.toInt()}°" },
                    )
                }
            }
            Text(stringResource(R.string.cu_hue), fontSize = 13.sp, color = c.ink2)
            GradientSlider(h.toFloat(), 0f..360f, Brush.horizontalGradient((0..12).map { seedColor(it * 30.0, i).c() }), "${h.toInt()}°") { set(it.toDouble(), i) }
            Text(stringResource(R.string.cu_intensity), fontSize = 13.sp, color = c.ink2)
            GradientSlider(i.toFloat(), 0f..1f, Brush.horizontalGradient(listOf(seedColor(h, 0.0).c(), seedColor(h, 1.0).c())), "${(i * 100).toInt()} %") { set(h, it.toDouble()) }
            Spacer(Modifier.height(4.dp))
        }
        PrimaryButton(stringResource(R.string.cu_apply), { onApply(ThemeChoice.Custom(primary, secondary)) }, Modifier.fillMaxWidth().padding(16.dp))
    }
}

@Composable
private fun MiniPreview(label: String, t: com.nordinary.cyclus.presenter.ThemeTokens, cyc: CycleColors, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(t.bg.c()).border(1.dp, t.line.c(), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, fontSize = 11.sp, color = t.ink2.c(), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(cyc.period.c()))
            Box(Modifier.size(10.dp).clip(CircleShape).background(cyc.fertile.c()))
            Box(Modifier.size(10.dp).clip(CircleShape).background(cyc.pre.c()))
            Spacer(Modifier.width(2.dp))
            Box(Modifier.clip(CircleShape).background(t.accentContainer.c()).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("Aa", fontSize = 11.sp, color = t.onAccentContainer.c(), fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(t.accent.c()).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text("Aa", fontSize = 12.sp, color = t.onAccent.c(), fontWeight = FontWeight.Bold)
        }
        Text("Link", fontSize = 12.sp, color = t.accent2.c(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GradientSlider(value: Float, range: ClosedFloatingPointRange<Float>, track: Brush, state: String, onChange: (Float) -> Unit) {
    val c = Cyclus.colors
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(track))
        Slider(
            value, onChange, valueRange = range, modifier = Modifier.fillMaxWidth().semantics { stateDescription = state },
            colors = SliderDefaults.colors(thumbColor = c.ink, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
        )
    }
}
