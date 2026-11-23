package com.nordinary.cyclus.ui.sheets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nordinary.cyclus.presenter.RingModel
import com.nordinary.cyclus.presenter.SegmentKind
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.presenter.WhyModel
import com.nordinary.cyclus.ui.common.CyclusCard
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.Nunito

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(title: String, fmt: Fmt, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Cyclus.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            TextButton(onClick = onDismiss) { Text(fmt.s("done"), color = c.accent, fontWeight = FontWeight.SemiBold) }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) { content() }
    }
}

/** „Warum dieses Fenster?“ (prototype `renderWhySheet`): how each logged signal moved the window. */
@Composable
fun WhySheet(m: WhyModel, fmt: Fmt, onDismiss: () -> Unit) = InfoSheet(fmt.s("whyT"), fmt, onDismiss) {
    val c = Cyclus.colors
    Column {
        m.steps.forEachIndexed { i, s ->
            val last = i == m.steps.size - 1
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    if (!last) Box(Modifier.padding(top = 20.dp).width(2.dp).fillMaxHeight().background(c.line))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(c.surface).border(2.dp, c.fertile, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.fertile) }
                }
                Column(Modifier.weight(1f).padding(bottom = 16.dp)) {
                    if (i == 0) {
                        Text(fmt.s("fromCycles", m.cyclesUsed), fontWeight = FontWeight.Bold, color = c.ink, fontSize = 15.sp)
                        Text(
                            if (m.lutealLearned > 0) fmt.s("fromCyclesSub", fmt.num(m.lutealMean, 1), m.lutealLearned) else fmt.s("fromCyclesSubPop", fmt.num(m.lutealMean, 1)),
                            fontSize = 12.sp, color = c.ink2,
                        )
                    } else {
                        Text(
                            s.events.joinToString(" + ") { fmt.s("ev_${it.key}") } + (s.date?.let { " · ${fmt.md(it)}" } ?: ""),
                            fontWeight = FontWeight.Bold, color = c.ink, fontSize = 15.sp,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.window?.let { (a, b) -> fmt.s("windowR", fmt.range(a, b)) } ?: fmt.s("windowUnclear"),
                            fontFamily = Nunito, fontWeight = FontWeight.Bold, color = c.fertile, fontSize = 15.sp,
                        )
                        if (i > 0 && s.changed == false) Text(" " + fmt.s("confirmed"), fontSize = 12.sp, color = c.ink2)
                    }
                }
            }
        }
    }
    CyclusCard {
        Text(
            if (m.occurred) fmt.s("concOcc", fmt.md(m.ovulationDay), fmt.range(m.window.first, m.window.second)) else fmt.s("concSoon", fmt.range(m.window.first, m.window.second)),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink,
        )
        Text(fmt.s("whyBody"), fontSize = 14.sp, color = c.ink2)
    }
    Text(fmt.s("whyFoot"), fontSize = 12.sp, color = c.ink2)
}

/** „So funktionieren Prognosen“ (prototype `renderMethodSheet`). */
@Composable
fun MethodSheet(fmt: Fmt, onDismiss: () -> Unit) = InfoSheet(fmt.s("methodT"), fmt, onDismiss) {
    for (p in fmt.array("method")) CyclusCard { Text(p, fontSize = 14.sp, color = Cyclus.colors.ink) }
}

/**
 * „So liest du den Ring“: a legend for exactly what the ring shows right now, each swatch drawn like the ring
 * itself (same colours, strokes and dashes), so the person can match it at a glance.
 */
@Composable
fun RingHelpSheet(ring: RingModel, fmt: Fmt, onMethod: () -> Unit, onDismiss: () -> Unit) = InfoSheet(fmt.s("ringHelpT"), fmt, onDismiss) {
    val c = Cyclus.colors
    val kinds = ring.segments.map { it.kind }.toSet()
    Text(fmt.s("ringIntro"), fontSize = 15.sp, color = c.ink2)
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(c.surface).padding(vertical = 6.dp),
    ) {
        if (ring.knobDeg != null) RingLegendRow(fmt.s("rhToday"), fmt.s("rhTodayS")) { RingSwatch.Knob }
        if (SegmentKind.BLEED in kinds) RingLegendRow(fmt.s("lgPeriod"), fmt.s("rhPeriodS")) { RingSwatch.Arc(listOf(c.period)) }
        if (SegmentKind.PRE in kinds) RingLegendRow(fmt.s("lgPre"), fmt.s("rhPreS")) { RingSwatch.Arc(listOf(c.pre.copy(alpha = .85f)), dashed = true) }
        if (ring.predicted != null) RingLegendRow(fmt.s("lgPred"), fmt.s("rhPredS")) { RingSwatch.Outer }
        if (SegmentKind.FERTILE in kinds) RingLegendRow(fmt.s("lgFert"), fmt.s("rhFertS")) {
            RingSwatch.Arc(listOf(.35f, .65f, 1f).map { c.fertile.copy(alpha = it) })
        }
        if (ring.ovulationDeg != null) RingLegendRow(fmt.s("rhOv"), fmt.s("rhOvS")) { RingSwatch.Ovulation(ring.ovulationSolid) }
        if (SegmentKind.NEUTRAL in kinds) RingLegendRow(fmt.s("rhRest"), fmt.s("rhRestS")) { RingSwatch.Arc(listOf(c.mid.copy(alpha = .32f))) }
    }
    Text(
        fmt.s("methodT"),
        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onMethod).padding(vertical = 6.dp, horizontal = 2.dp),
        color = c.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
    )
}

private sealed interface RingSwatch {
    data class Arc(val colors: List<Color>, val dashed: Boolean = false) : RingSwatch
    data object Outer : RingSwatch
    data object Knob : RingSwatch
    data class Ovulation(val solid: Boolean) : RingSwatch
}

@Composable
private fun RingLegendRow(title: String, sub: String, swatch: () -> RingSwatch) {
    val c = Cyclus.colors
    val sw = swatch()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Canvas(Modifier.size(width = 52.dp, height = 34.dp)) {
            // a slice of the ring's top: radius in ring units (118 inner, 137 outer) scaled to fit
            val u = size.width / 70f
            val r = 118f * u
            val center = Offset(size.width / 2, 147f * u) // outer marks (r 137) stay inside the top
            fun arc(radius: Float, a0: Float, a1: Float, col: Color, w: Float, dash: FloatArray? = null, cap: StrokeCap = StrokeCap.Butt) = drawArc(
                col, a0, a1 - a0, false, Offset(center.x - radius, center.y - radius), Size(2 * radius, 2 * radius),
                style = Stroke(w * u, cap = cap, pathEffect = dash?.let { d -> PathEffect.dashPathEffect(FloatArray(d.size) { d[it] * u }) }),
            )
            fun pol(radius: Float, deg: Float) = Offset(
                center.x + radius * kotlin.math.cos(Math.toRadians(deg.toDouble())).toFloat(),
                center.y + radius * kotlin.math.sin(Math.toRadians(deg.toDouble())).toFloat(),
            )
            val span = 28f // degrees shown
            val a0 = 270f - span / 2
            when (sw) {
                is RingSwatch.Arc -> {
                    val per = span / sw.colors.size.coerceAtLeast(1)
                    // one ring day per colour, with the ring's 0.7° gaps between days (wider here, for legibility)
                    sw.colors.forEachIndexed { i, col ->
                        arc(r, a0 + i * per + .9f, a0 + (i + 1) * per - .9f, col, 18f, if (sw.dashed) floatArrayOf(2f, 3.2f) else null)
                    }
                }
                RingSwatch.Outer -> {
                    arc(r, a0, a0 + span, c.mid.copy(alpha = .32f), 18f)
                    arc(137f * u, a0 + 3, a0 + span - 3, c.period, 3f, floatArrayOf(5f, 4f), StrokeCap.Round)
                }
                RingSwatch.Knob -> {
                    arc(r, a0, a0 + span, c.mid.copy(alpha = .32f), 18f)
                    val k = pol(r, 270f)
                    drawCircle(c.bg, 15f * u, k); drawCircle(c.ink, 11f * u, k)
                }
                is RingSwatch.Ovulation -> {
                    arc(r, a0, a0 + span, c.fertile.copy(alpha = .65f), 18f)
                    val o = pol(137f * u, 270f)
                    drawCircle(c.bg, 7f * u, o)
                    drawCircle(c.fertile, 7f * u, o, style = Stroke(2f * u, pathEffect = if (sw.solid) null else PathEffect.dashPathEffect(floatArrayOf(2.5f * u, 2f * u))))
                    drawCircle(c.fertile, 2.6f * u, o)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(sub, fontSize = 13.sp, color = c.ink2)
        }
    }
}
