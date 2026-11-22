package com.nordinary.cyclus.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.Nunito

private val DROP = PathParser().parsePathString("M12 2C12 2 3 13 3 19a9 9 0 0 0 18 0C21 13 12 2 12 2z").toPath()

/** Flow droplet (prototype `drop`): fill level per flow, dotted outline for spotting, slash for none. 24×30 viewBox. */
@Composable
fun Droplet(flow: Flow, color: Color, modifier: Modifier = Modifier) {
    // unspecified = bleeding without a level: outline only, never drawn like a heavy day
    val fill = when (flow) { Flow.LIGHT -> .33f; Flow.MEDIUM -> .62f; Flow.HEAVY -> 1f; else -> 0f }
    Canvas(modifier.size(28.dp)) {
        val s = minOf(size.width / 24f, size.height / 30f)
        val dx = (size.width - 24f * s) / 2
        translate(left = dx) {
            scale(s, s, pivot = Offset.Zero) {
                if (fill > 0f) clipPath(DROP) { drawRect(color, topLeft = Offset(0f, 28f - 22f * fill), size = Size(24f, 30f)) }
                drawPath(DROP, color, style = Stroke(width = if (flow == Flow.UNSPECIFIED) 3.2f else 2f, pathEffect = if (flow == Flow.SPOTTING) PathEffect.dashPathEffect(floatArrayOf(2.5f, 2.5f)) else null))
                if (flow == Flow.NONE) drawLine(color, Offset(6f, 24f), Offset(18f, 12f), strokeWidth = 2f)
            }
        }
    }
}

/** Draws a dashed rounded border (prototype `border-style: dashed`). */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.dp, dash: Float = 6f, gap: Float = 4f): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()), style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * density / 2, gap * density / 2))),
    )
}

/** Prototype chip: ✓ + tinted fill when selected; dashed outline for suggestions; fertile tint for LH chips. */
@Composable
fun CyclusChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    suggested: Boolean = false,
    accent: Color = Cyclus.colors.period,
    enabled: Boolean = true,
) {
    val c = Cyclus.colors
    val shape = CircleShape
    val bg = if (selected) accent.copy(alpha = .14f).compositeOver(c.surface) else c.surface
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(bg)
            .then(
                if (suggested && !selected) Modifier.dashedBorder(c.line.copy(alpha = 1f).compositeOver(c.ink2.copy(alpha = .5f)), 20.dp)
                else Modifier.border(BorderStroke(1.dp, if (selected) accent else c.line), shape),
            )
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Text("✓", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (enabled) c.ink else c.ink2)
    }
}

/** Single-select 1…5 scale (prototype `.scale`), toggling off when tapped again. */
@Composable
fun ScaleRow(label: String, value: Int?, lowLabel: String, highLabel: String, onChange: (Int?) -> Unit) {
    val c = Cyclus.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (n in 1..5) {
                val sel = value == n
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) c.ink else c.surface)
                        .border(1.dp, if (sel) c.ink else c.line, RoundedCornerShape(10.dp))
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { onChange(if (sel) null else n) })
                        .semanticsLabel("$label $n/5"),
                    contentAlignment = Alignment.Center,
                ) { Text("$n", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (sel) c.bg else c.ink) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(lowLabel, style = MaterialTheme.typography.bodySmall, color = c.ink2)
            Text(highLabel, style = MaterialTheme.typography.bodySmall, color = c.ink2, textAlign = TextAlign.End)
        }
    }
}

fun Modifier.semanticsLabel(label: String): Modifier =
    semantics { contentDescription = label }

/** Prototype card: surface, radius 16, padding 14×16. */
@Composable
fun CyclusCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Cyclus.colors
    val shape: Shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape, color = c.surface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/** Non-interactive pill (prototype `.badge`) with an optional status dot. */
@Composable
fun Badge(text: String, dot: Color? = null, color: Color = Cyclus.colors.ink) {
    val c = Cyclus.colors
    Row(
        Modifier.clip(CircleShape).background(c.surface2).padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Cyclus.colors.ink2)
        trailing()
    }
}

/**
 * Compact graphics (outlook grid, heatmap, calendar grid) keep their layout at very large font sizes: text inside them
 * scales up to [max] only. Their content is also exposed as one semantic description, so nothing is lost for TalkBack.
 */
@Composable
fun CappedFontScale(max: Float = 1.3f, content: @Composable () -> Unit) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    if (d.fontScale <= max) content()
    else androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, max),
    ) { content() }
}
