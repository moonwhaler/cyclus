package com.nordinary.cyclus.ui.log

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.core.Catalog
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.Js
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.core.Mucus
import com.nordinary.cyclus.engine.Mode
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.ui.common.CyclusChip
import com.nordinary.cyclus.ui.common.ScaleRow
import com.nordinary.cyclus.ui.common.SectionLabel
import com.nordinary.cyclus.ui.common.dashedBorder
import com.nordinary.cyclus.ui.today.DropTile
import com.nordinary.cyclus.ui.today.PrimaryButton
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.Nunito
import kotlinx.coroutines.delay

/** Log sheet (prototype `renderLogSheet`): every tap saves immediately; stable section order. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogSheet(
    day: Day,
    today: Day,
    log: DayLog,
    mode: Mode,
    suggestions: List<String>,
    fmt: Fmt,
    onDayChange: (Day) -> Unit,
    onUpdate: ((DayLog) -> DayLog) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Cyclus.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val conceive = mode == Mode.CONCEIVE
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.bg) {
        // header: day switcher
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDayChange(day - 1) }, Modifier.semantics { contentDescription = fmt.s("prevDay") }) { Text("‹", fontSize = 24.sp, color = c.ink) }
            Text(if (day == today) fmt.s("today") else fmt.wmd(day), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.ink)
            IconButton(onClick = { onDayChange(minOf(today, day + 1)) }, enabled = day < today, modifier = Modifier.semantics { contentDescription = fmt.s("nextDay") }) {
                Text("›", fontSize = 24.sp, color = if (day < today) c.ink else c.ink2.copy(alpha = .3f))
            }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Group(fmt.s("flowL")) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in Flow.logOrder) {
                        DropTile(f, fmt.item(f.key), log.flow == f, if (f == Flow.NONE) c.ink2 else c.period, Modifier.weight(1f)) {
                            onUpdate { it.copy(flow = if (it.flow == f) null else f) }
                        }
                    }
                }
            }
            if (conceive) LhGroup(log, fmt, onUpdate)
            Group(fmt.s("pinned")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in Catalog.PINNED) SymptomChip(s, log, fmt, onUpdate)
                    if (conceive) CyclusChip(fmt.s("sex"), log.sex, { onUpdate { it.copy(sex = !it.sex) } })
                }
            }
            if (suggestions.isNotEmpty()) Group(fmt.s("often")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in suggestions) SymptomChip(s, log, fmt, onUpdate, suggested = true)
                }
            }
            if (conceive) { MucusGroup(log, fmt, onUpdate); BbtGroup(fmt.s("bbtL"), fmt.s("bbtHint"), log, fmt, onUpdate) }
            Group(fmt.s("moreSym")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in Catalog.SYMPTOMS.filter { it !in Catalog.PINNED && it !in suggestions }) SymptomChip(s, log, fmt, onUpdate)
                }
            }
            PainGroup(log, fmt, onUpdate)
            Group(fmt.s("mood")) { ScaleRow(fmt.s("mood"), log.mood, fmt.s("moodLo"), fmt.s("moodHi")) { v -> onUpdate { it.copy(mood = v) } } }
            Group(fmt.s("feelings")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (e in Catalog.EMOTIONS) CyclusChip(fmt.item(e), e in log.emotions, {
                        onUpdate { l -> l.copy(emotions = if (e in l.emotions) l.emotions - e else l.emotions + e) }
                    })
                }
            }
            Group(fmt.s("energy")) { ScaleRow(fmt.s("energy"), log.energy, fmt.s("enLo"), fmt.s("enHi")) { v -> onUpdate { it.copy(energy = v) } } }
            Group(fmt.s("sleep")) { ScaleRow(fmt.s("sleep"), log.sleep, fmt.s("slLo"), fmt.s("slHi")) { v -> onUpdate { it.copy(sleep = v) } } }
            Group(fmt.s("libido")) { ScaleRow(fmt.s("libido"), log.libido, fmt.s("libLo"), fmt.s("libHi")) { v -> onUpdate { it.copy(libido = v) } } }
            if (!conceive) { MucusGroup(log, fmt, onUpdate); BbtGroup(fmt.s("tempL"), fmt.s("optional"), log, fmt, onUpdate) }
            Group(fmt.s("tags")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in Catalog.TAGS) CyclusChip(fmt.item(t), t in log.tags, {
                        onUpdate { l -> l.copy(tags = if (t in l.tags) l.tags - t else l.tags + t) }
                    })
                    CyclusChip(fmt.s("newTag"), false, {}, suggested = true, enabled = false)
                }
            }
            NoteGroup(day, log, fmt, onUpdate)
            NothingElse(log, fmt, onUpdate)
            PrimaryButton(fmt.s("done"), onDone, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Group(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(label) { if (hint != null) Text(hint, fontSize = 13.sp, color = Cyclus.colors.ink2) }
        content()
    }
}

@Composable
private fun SymptomChip(s: String, log: DayLog, fmt: Fmt, onUpdate: ((DayLog) -> DayLog) -> Unit, suggested: Boolean = false) =
    CyclusChip(fmt.item(s), s in log.symptoms, {
        onUpdate { l -> if (s in l.symptoms) l.copy(symptoms = l.symptoms - s, symptomSeverity = l.symptomSeverity - s) else l.copy(symptoms = l.symptoms + s) }
    }, suggested = suggested)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LhGroup(log: DayLog, fmt: Fmt, onUpdate: ((DayLog) -> DayLog) -> Unit) = Group(fmt.s("lhTest")) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in LhResult.entries) CyclusChip(fmt.s("lh_${r.key}"), log.lh == r, { onUpdate { it.copy(lh = if (it.lh == r) null else r) } }, accent = Cyclus.colors.fertile)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MucusGroup(log: DayLog, fmt: Fmt, onUpdate: ((DayLog) -> DayLog) -> Unit) = Group(fmt.s("discharge")) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in Mucus.entries) CyclusChip(fmt.item(m.key), log.mucus == m, { onUpdate { it.copy(mucus = if (it.mucus == m) null else m) } })
    }
}

/** Temperature stepper (±0,05 °C) with a decimal field (prototype `bbt`). */
@Composable
private fun BbtGroup(label: String, hint: String, log: DayLog, fmt: Fmt, onUpdate: ((DayLog) -> DayLog) -> Unit) = Group(label, hint) {
    val c = Cyclus.colors
    var text by remember(log.bbt) { mutableStateOf(log.bbt?.let { fmt.temp(it) } ?: "") }
    fun commit(t: String) {
        val n = t.replace(',', '.').toDoubleOrNull()
        onUpdate { it.copy(bbt = n?.let { v -> Js.round(v * 100) / 100 }) }
    }
    fun step(d: Double) = onUpdate { val base = it.bbt ?: 36.40; it.copy(bbt = Js.round((base + d) * 100) / 100) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepButton("−", fmt.s("lower")) { step(-0.05) }
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.width(110.dp).semantics { contentDescription = fmt.s("bbtAria") },
            singleLine = true, placeholder = { Text(fmt.temp(36.4), fontFamily = Nunito, color = c.ink2) },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, textAlign = TextAlign.Center, color = c.ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { commit(text) }),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.line, focusedBorderColor = c.period, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface),
        )
        StepButton("+", fmt.s("higher")) { step(0.05) }
        Text("°C", color = c.ink2)
    }
}

@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
    val c = Cyclus.colors
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, fontSize = 20.sp, color = c.ink) }
}

@Composable
private fun NoteGroup(day: Day, log: DayLog, fmt: Fmt, onUpdate: ((DayLog) -> DayLog) -> Unit) = Group(fmt.s("note")) {
    val c = Cyclus.colors
    var text by rememberSaveable(day) { mutableStateOf(log.note ?: "") }
    // save shortly after typing stops
    LaunchedEffect(text) {
        if (text == (log.note ?: "")) return@LaunchedEffect
        delay(600)
        onUpdate { it.copy(note = text.ifBlank { null }) }
    }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
        placeholder = { Text(fmt.s("notePh"), color = c.ink2) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.line, focusedBorderColor = c.period, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface),
    )
}

@Composable
private fun NothingElse(log: DayLog, fmt: Fmt, onUpdate: ((DayLog) -> DayLog) -> Unit) {
    val c = Cyclus.colors
    val on = log.nothingElse
    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape)
                .then(if (on) Modifier.border(1.dp, c.success, shape) else Modifier.dashedBorder(c.ink2, 14.dp))
                .clickable(role = Role.Switch) { onUpdate { it.copy(nothingElse = !it.nothingElse) } }
                .semantics { selected = on }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (on) fmt.s("nothingOn") else fmt.s("nothing"), fontWeight = FontWeight.SemiBold, color = if (on) c.success else c.ink, textAlign = TextAlign.Center) }
        Text(fmt.s("nothingHint"), Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = c.ink2)
    }
}

/** Body regions in the prototype's 120×190 coordinate space. */
private enum class Region(val key: String, val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) {
    HEAD("head", 45f, 4f, 30f, 36f, 0f), CHEST("chest", 36f, 48f, 48f, 30f, 10f), UPPER("upper", 38f, 80f, 44f, 22f, 8f),
    LOW_L("lowL", 38f, 104f, 14f, 26f, 6f), LOW_C("lowC", 53f, 104f, 14f, 26f, 6f), LOW_R("lowR", 68f, 104f, 14f, 26f, 6f),
    PELVIS("pelvis", 40f, 132f, 40f, 18f, 0f), THIGHS("thighs", 40f, 152f, 40f, 36f, 10f),
}

/** Pain body map + 0–10 slider (prototype `painwrap`). Each region is its own accessible button. */
@Composable
private fun PainGroup(log: DayLog, fmt: Fmt, onUpdate: ((DayLog) -> DayLog) -> Unit) = Group(fmt.s("painL"), fmt.s("painHint")) {
    val c = Cyclus.colors
    var sel by rememberSaveable { mutableStateOf("lowC") }
    val current = log.pain[sel] ?: 0
    var slider by remember(sel, current) { mutableFloatStateOf(current.toFloat()) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(Modifier.width(110.dp).aspectRatio(120f / 190f).semantics { contentDescription = fmt.s("bodyMap") }) {
            val u = maxWidth / 120f
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / 120f
                for (r in Region.entries) {
                    val on = (log.pain[r.key] ?: 0) > 0
                    val fill = if (on) c.period.copy(alpha = .55f) else c.surface2
                    val stroke = if (sel == r.key) Stroke(2.5f * s) else Stroke(1.5f * s)
                    val strokeColor = if (sel == r.key) c.period else c.line
                    drawRegion(r, s, fill, null); drawRegion(r, s, strokeColor, stroke)
                }
            }
            for (r in Region.entries) {
                Box(
                    Modifier.offset(u * r.x, u * r.y).size(u * r.w, u * r.h)
                        .clickable(role = Role.Button) { sel = r.key }
                        .semantics {
                            contentDescription = "${fmt.item(r.key)}: ${log.pain[r.key] ?: 0}"
                            selected = sel == r.key
                        },
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(fmt.item(sel), fontWeight = FontWeight.Bold, color = c.ink)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(fmt.s("painNone"), fontSize = 12.sp, color = c.ink2)
                Text("${slider.toInt()}", fontFamily = Nunito, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = c.period)
                Text(fmt.s("painMax"), fontSize = 12.sp, color = c.ink2)
            }
            Slider(
                value = slider, onValueChange = { slider = it }, valueRange = 0f..10f, steps = 9,
                onValueChangeFinished = {
                    val v = slider.toInt()
                    onUpdate { l -> l.copy(pain = if (v == 0) l.pain - sel else l.pain + (sel to v)) }
                },
                modifier = Modifier.semantics { stateDescription = "${slider.toInt()}/10"; contentDescription = "${fmt.s("painL")}: ${fmt.item(sel)}" },
                colors = SliderDefaults.colors(thumbColor = c.period, activeTrackColor = c.period, inactiveTrackColor = c.surface2),
            )
            val list = Catalog.PAIN_REGIONS.mapNotNull { k -> log.pain[k]?.takeIf { it > 0 }?.let { "${fmt.item(k)} $it" } }
            Text(if (list.isEmpty()) fmt.s("noPain") else list.joinToString(" · "), fontSize = 12.sp, color = c.ink2)
        }
    }
}

private fun DrawScope.drawRegion(r: Region, s: Float, color: Color, stroke: Stroke?) {
    val style = stroke ?: androidx.compose.ui.graphics.drawscope.Fill
    when (r) {
        Region.HEAD -> drawOval(color, Offset(45f * s, 4f * s), Size(30f * s, 36f * s), style = style)
        Region.PELVIS -> {
            val p = Path().apply { moveTo(40f * s, 132f * s); lineTo(80f * s, 132f * s); lineTo(72f * s, 150f * s); lineTo(48f * s, 150f * s); close() }
            drawPath(p, color, style = style)
        }
        else -> drawRoundRect(color, Offset(r.x * s, r.y * s), Size(r.w * s, r.h * s), CornerRadius(r.r * s), style = style)
    }
}
