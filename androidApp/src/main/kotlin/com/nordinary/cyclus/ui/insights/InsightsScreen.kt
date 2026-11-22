package com.nordinary.cyclus.ui.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.insights.Area
import com.nordinary.cyclus.insights.Pattern
import com.nordinary.cyclus.insights.PatternStatus
import com.nordinary.cyclus.presenter.CycleBar
import com.nordinary.cyclus.presenter.HeatMap
import com.nordinary.cyclus.presenter.InsightsUiState
import com.nordinary.cyclus.presenter.PatternSection
import com.nordinary.cyclus.presenter.TemperatureChart
import com.nordinary.cyclus.ui.common.Badge
import com.nordinary.cyclus.ui.common.CyclusCard
import com.nordinary.cyclus.ui.common.dashedBorder
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.Nunito
import com.nordinary.cyclus.ui.theme.RobotoFlex
import com.nordinary.cyclus.ui.theme.CyclusColors

/** Einblicke tab (prototype `renderInsights`). */
@Composable
fun InsightsContent(
    state: InsightsUiState,
    fmt: Fmt,
    contentPadding: PaddingValues,
    onMethod: () -> Unit,
    onMute: (String) -> Unit,
    onUnmute: () -> Unit,
    onReport: (() -> Unit)?,
) {
    val c = Cyclus.colors
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            InsightsUiState.Empty -> CyclusCard {
                Title(fmt.s("insEmptyT")); Body(fmt.s("insEmptyB"))
            }
            is InsightsUiState.Ready -> {
                // equal heights even when one label wraps (German: „Tage, dein typischer Zyklus“)
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("${state.typLo}–${state.typHi}", fmt.s("statCycle"), Modifier.weight(1f).fillMaxHeight())
                    Stat(state.periodTypical?.toString() ?: "–", fmt.s("statPeriod"), Modifier.weight(1f).fillMaxHeight())
                }
                CyclusCard {
                    Title(fmt.s("varyT", state.spread))
                    Badge(fmt.s("tier_${state.tier.key}"))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt.s("varyB", state.cyclesUsed) + " ", fontSize = 14.sp, color = c.ink2)
                    }
                    Text(
                        fmt.s("howWorks"), Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onMethod).padding(vertical = 4.dp),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.period,
                    )
                }
                Patterns(state.patterns, fmt, onMute, onUnmute)
                CyclusCard {
                    Title(fmt.s("cycleLen"))
                    CycleLengthChart(state.bars, state.typLo, state.typHi, fmt)
                    Text(fmt.s("bandNote"), fontSize = 12.sp, color = c.ink2)
                }
                state.temperature?.let { t ->
                    CyclusCard {
                        Title(fmt.s("tempT"))
                        TemperatureChartView(t, fmt)
                        Text(
                            if (t.shiftCd != null && t.ovulationDay != null) fmt.s("tempShift", t.shiftCd!!, fmt.md(t.ovulationDay!!)) else fmt.s("tempNo"),
                            fontSize = 12.sp, color = c.ink2,
                        )
                    }
                }
                CyclusCard {
                    Title(fmt.s("heatT"))
                    Text(fmt.s("heatB"), fontSize = 12.sp, color = c.ink2, modifier = Modifier.padding(bottom = 6.dp))
                    Heat(state.heat, fmt)
                }
                CyclusCard(onClick = onReport) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Title(fmt.s("reportT")); if (onReport != null) Text("›", color = c.ink2, fontSize = 18.sp) }
                    Body(if (onReport != null) fmt.s("reportB") else androidx.compose.ui.res.stringResource(com.nordinary.cyclus.R.string.rep_unavailable))
                }
                SectionLabel(fmt.s("history"))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)) {
                    state.history.forEachIndexed { i, h ->
                        if (i > 0) HorizontalDivider(color = c.line)
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(fmt.range(h.start, h.end), fontFamily = Nunito, fontWeight = FontWeight.Bold, color = c.ink, fontSize = 15.sp)
                            Text(fmt.s("histRow", h.length, h.periodLength), fontSize = 13.sp, color = c.ink2)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable private fun Title(t: String) = Text(t, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Cyclus.colors.ink)
@Composable private fun Body(t: String) = Text(t, fontSize = 14.sp, color = Cyclus.colors.ink2)

@Composable
private fun SectionLabel(t: String) = Text(
    t.uppercase(), Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = Cyclus.colors.ink2,
)

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val c = Cyclus.colors
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(c.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(value, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = c.ink)
        Text(label, fontSize = 12.sp, color = c.ink2)
    }
}

private fun areaColor(a: Area, c: CyclusColors) = when (a) {
    Area.BODY -> c.period; Area.MOOD -> c.pre; Area.LIFE -> c.warning; Area.FERT -> c.fertile; Area.HEALTH -> c.ink2
}

/** Status pill (prototype `.badge.st-*`). */
@Composable
private fun StatusChip(status: PatternStatus, fmt: Fmt) {
    val c = Cyclus.colors
    val label = fmt.s("st_${status.key}")
    val base = Modifier.clip(CircleShape)
    val m = when (status) {
        PatternStatus.CONSISTENT -> base.background(c.period.copy(alpha = .16f).compositeOver(c.surface))
        PatternStatus.EARLY -> base.dashedBorder(c.ink2, 12.dp)
        else -> base.background(c.surface2)
    }
    val color = when (status) {
        PatternStatus.STABLE -> c.success; PatternStatus.NONE, PatternStatus.NEEDS -> c.ink2; else -> c.ink
    }
    Text(label, m.padding(horizontal = 11.dp, vertical = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
}

/** „Deine Muster“ (prototype `patternSection` / `patCard`). */
@Composable
private fun Patterns(sec: PatternSection, fmt: Fmt, onMute: (String) -> Unit, onUnmute: () -> Unit) {
    val c = Cyclus.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(fmt.s("pwT"))
        Text(fmt.s("pwIntro"), Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = c.ink2)
        for (p in sec.found) PatternCard(p, fmt, onMute)
        if (sec.watching.isNotEmpty()) {
            SectionLabel(fmt.s("watching"))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)) {
                sec.watching.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(color = c.line)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(areaColor(p.area, c)))
                        Column(Modifier.weight(1f)) {
                            Text(fmt.txt(p.title), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink)
                            Text(fmt.txt(p.body), fontSize = 13.sp, color = c.ink2)
                        }
                        StatusChip(p.status, fmt)
                    }
                }
            }
        }
        if (sec.hiddenCount > 0) Text(
            fmt.s("hiddenN", sec.hiddenCount), Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onUnmute).padding(4.dp),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.period,
        )
    }
}

@Composable
private fun PatternCard(p: Pattern, fmt: Fmt, onMute: (String) -> Unit) {
    val c = Cyclus.colors
    var open by rememberSaveable(p.id) { mutableStateOf(false) }
    CyclusCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(areaColor(p.area, c)))
                Text(fmt.s("area_${p.area.key}").uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = c.ink2)
            }
            StatusChip(p.status, fmt)
        }
        Text(fmt.txt(p.title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
        Text(fmt.txt(p.body), fontSize = 14.sp, color = c.ink2)
        if (p.report == true) Text(fmt.s("inReport"), fontSize = 12.sp, color = c.ink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            if (p.why != null) Column(Modifier.weight(1f)) {
                Text(
                    fmt.s("why"), Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { open = !open }.padding(vertical = 4.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.period,
                )
                AnimatedVisibility(open) { Text(fmt.txt(p.why!!), fontSize = 12.sp, color = c.ink2) }
            } else Spacer(Modifier.weight(1f))
            Text(
                fmt.s("hide"), Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { onMute(p.id) }.padding(4.dp),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.ink2,
            )
        }
    }
}

/** Cycle lengths (prototype SVG 320×150: bars, typical band, value labels, month labels). */
@Composable
private fun CycleLengthChart(bars: List<CycleBar>, typLo: Int, typHi: Int, fmt: Fmt) {
    val c = Cyclus.colors
    val density = LocalDensity.current
    val a11y = fmt.s("cycAria", bars.size, typLo, typHi, bars.minOf { it.length }, bars.maxOf { it.length })
    Canvas(Modifier.fillMaxWidth().aspectRatio(320f / 150f).clearAndSetSemantics { contentDescription = a11y }) {
        val u = size.width / 320f
        val l0 = 20f; val l1 = 36f; val x0 = 28f; val x1 = 312f; val y0 = 12f; val y1 = 128f
        fun sy(l: Float) = y1 - (l - l0) / (l1 - l0) * (y1 - y0)
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = with(density) { 10.sp.toPx() }; color = c.ink2.toArgb() }
        drawRect(c.period.copy(alpha = .09f), Offset(x0 * u, sy(typHi.toFloat()) * u), Size((x1 - x0) * u, (sy(typLo.toFloat()) - sy(typHi.toFloat())) * u))
        for (l in listOf(20, 24, 28, 32, 36)) {
            drawLine(c.line, Offset(x0 * u, sy(l.toFloat()) * u), Offset(x1 * u, sy(l.toFloat()) * u), 1f * u)
            text.textAlign = android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText("$l", (x0 - 6) * u, (sy(l.toFloat()) + 3) * u, text)
        }
        val bw = (x1 - x0) / bars.size
        bars.forEachIndexed { i, b ->
            val x = x0 + i * bw + bw * 0.22f; val w = bw * 0.56f
            val top = sy(b.length.toFloat())
            drawRoundRect(c.period.copy(alpha = if (b.latest) 1f else .6f), Offset(x * u, top * u), Size(w * u, (y1 - top) * u), CornerRadius(4f * u))
            text.textAlign = android.graphics.Paint.Align.CENTER
            text.color = c.ink.toArgb(); text.isFakeBoldText = true
            drawContext.canvas.nativeCanvas.drawText("${b.length}", (x + w / 2) * u, (top - 4) * u, text)
            text.color = c.ink2.toArgb(); text.isFakeBoldText = false
            drawContext.canvas.nativeCanvas.drawText(fmt.monthShort(b.start), (x + w / 2) * u, (150 - 8) * u, text)
        }
    }
}

/** BBT chart (prototype SVG 320×150: 36.1–36.9 °C, coverline, filled points after the shift). */
@Composable
private fun TemperatureChartView(t: TemperatureChart, fmt: Fmt) {
    val c = Cyclus.colors
    val density = LocalDensity.current
    val a11y = fmt.s("tempT") + ". " + (if (t.shiftCd != null && t.ovulationDay != null) fmt.s("tempShift", t.shiftCd!!, fmt.md(t.ovulationDay!!)) else fmt.s("tempNo"))
    Canvas(Modifier.fillMaxWidth().aspectRatio(320f / 150f).clearAndSetSemantics { contentDescription = a11y }) {
        val u = size.width / 320f
        val tx0 = 34f; val tx1 = 312f; val ty0 = 14f; val ty1 = 128f; val t0 = 36.1f; val t1 = 36.9f; val n = t.maxCd
        fun sx(cd: Int) = tx0 + (cd - 1).toFloat() / (n - 1) * (tx1 - tx0)
        fun syT(v: Double) = (ty1 - (v.toFloat() - t0) / (t1 - t0) * (ty1 - ty0))
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = with(density) { 10.sp.toPx() }; color = c.ink2.toArgb() }
        for (v in listOf(36.2, 36.4, 36.6, 36.8)) {
            drawLine(c.line, Offset(tx0 * u, syT(v) * u), Offset(tx1 * u, syT(v) * u), 1f * u)
            text.textAlign = android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(fmt.num(v, 1), (tx0 - 5) * u, (syT(v) + 3) * u, text)
        }
        t.cover?.let { cv ->
            drawLine(c.fertile, Offset(tx0 * u, syT(cv) * u), Offset(tx1 * u, syT(cv) * u), 1f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * u, 3f * u)))
            text.textAlign = android.graphics.Paint.Align.RIGHT; text.color = c.fertile.toArgb()
            drawContext.canvas.nativeCanvas.drawText(fmt.s("coverline"), tx1 * u, (syT(cv) - 4) * u, text)
            text.color = c.ink2.toArgb()
        }
        val path = Path()
        t.points.forEachIndexed { i, p -> val x = sx(p.cd) * u; val y = syT(p.celsius) * u; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        drawPath(path, c.ink, style = Stroke(1.6f * u))
        for (p in t.points) {
            val o = Offset(sx(p.cd) * u, syT(p.celsius) * u)
            drawCircle(if (p.afterShift) c.fertile else c.surface, 3f * u, o)
            drawCircle(if (p.afterShift) c.fertile else c.ink, 3f * u, o, style = Stroke(1.5f * u))
        }
        text.textAlign = android.graphics.Paint.Align.CENTER
        for (cd in listOf(1, 7, 14, 21, 28).filter { it <= n }) {
            drawContext.canvas.nativeCanvas.drawText("${if (fmt.isDe) "ZT" else "CD"} $cd", sx(cd) * u, (150 - 8) * u, text)
        }
    }
}

/** Symptom heatmap around the period start (prototype `.heat`). */
@Composable
private fun Heat(h: HeatMap, fmt: Fmt) {
    val c = Cyclus.colors
    Column(Modifier.clearAndSetSemantics { contentDescription = fmt.s("heatT") }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.width(104.dp))
            for (r in h.rel) Text(
                when { r == 0 -> "P1"; r > 0 -> "+$r"; else -> "$r" }, Modifier.weight(1f),
                fontSize = 11.sp, color = c.ink2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontFamily = RobotoFlex, maxLines = 1,
            )
        }
        h.symptoms.forEachIndexed { i, s ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fmt.item(s), Modifier.width(104.dp).padding(end = 4.dp), fontSize = 11.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                for (x in h.rates[i]) Box(Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(c.period.copy(alpha = (0.06 + x * 0.94).toFloat())))
            }
        }
    }
}
