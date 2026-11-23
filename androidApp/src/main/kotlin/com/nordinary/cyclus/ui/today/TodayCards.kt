package com.nordinary.cyclus.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.presenter.DoctorQuestion
import com.nordinary.cyclus.presenter.DoctorQuestions
import com.nordinary.cyclus.presenter.MoodWeek
import com.nordinary.cyclus.presenter.PhaseCard
import com.nordinary.cyclus.presenter.TemperatureChart
import com.nordinary.cyclus.presenter.UsualCard
import com.nordinary.cyclus.ui.common.CappedFontScale
import com.nordinary.cyclus.ui.common.CyclusCard
import com.nordinary.cyclus.ui.insights.TemperatureChartView
import com.nordinary.cyclus.ui.theme.Cyclus
import kotlin.math.max

/** Title row with a chevron, for cards that open another screen. */
@Composable
private fun TitleRow(title: String, chevron: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        CardTitle(title)
        if (chevron) Text("›", color = Cyclus.colors.ink2, fontSize = 18.sp)
    }
}

/** „Zyklus vs. üblich“: today on a track of the usual cycle length, the last cycle and the period against the typical ones. */
@Composable
fun UsualCardView(u: UsualCard, fmt: Fmt, onInsights: () -> Unit) {
    val c = Cyclus.colors
    CyclusCard(onClick = onInsights) {
        TitleRow(fmt.s("card_usual"))
        Text(fmt.s("usCd", u.cycleDay, u.typLo, u.typHi), Modifier.padding(top = 2.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
        val n = max(u.typHi + 3, u.cycleDay)
        val bleed = if (u.inPeriod) u.periodDays ?: 0 else u.periodDays ?: 0
        Canvas(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(14.dp).clearAndSetSemantics { contentDescription = fmt.s("usA") }) {
            val h = size.height
            val y = h / 2
            fun x(day: Float) = (day - 1) / (n - 1).toFloat() * (size.width - h) + h / 2
            drawLine(c.line, Offset(x(1f), y), Offset(x(n.toFloat()), y), 6.dp.toPx(), StrokeCap.Round)
            drawLine(c.accent.copy(alpha = .35f), Offset(x(u.typLo.toFloat()), y), Offset(x(u.typHi.toFloat()), y), 10.dp.toPx(), StrokeCap.Round)
            if (bleed > 0) drawLine(c.period, Offset(x(1f), y), Offset(x(bleed.toFloat().coerceAtMost(n.toFloat())), y), 6.dp.toPx(), StrokeCap.Round)
            drawCircle(c.surface, h / 2, Offset(x(u.cycleDay.toFloat()), y))
            drawCircle(c.ink, h / 2 - 2.dp.toPx(), Offset(x(u.cycleDay.toFloat()), y))
        }
        if (u.lastLength != null && u.lastVerdict != null) {
            CardBody("${fmt.s("usLast", u.lastLength!!)}, ${fmt.s("us_${u.lastVerdict!!.name}")}")
        }
        u.periodDays?.let { pd ->
            val typ = u.periodTypical?.let { " · " + fmt.s("usTyp", it) } ?: ""
            CardBody((if (u.inPeriod) fmt.s("usPeriodDay", pd) else fmt.plural("usPeriodLen", pd)) + typ)
        }
    }
}

/** „Temperaturkurve“: the same chart as in Insights, for the current cycle. */
@Composable
fun TempCardView(t: TemperatureChart, fmt: Fmt, onInsights: () -> Unit) {
    CyclusCard(onClick = onInsights) {
        TitleRow(fmt.s("card_temp"))
        TemperatureChartView(t, fmt)
    }
}

/**
 * „Stimmung & Energie“: seven days, a dot per entry on the 1–5 scale and, where earlier cycles say so, a faint line
 * at the usual value for the same cycle day. A tap shows the day in the calendar.
 */
@Composable
fun MoodCardView(w: MoodWeek, fmt: Fmt, onOpenDay: (Day) -> Unit) {
    val c = Cyclus.colors
    val hasUsual = w.usualMood.any { it != null } || w.usualEnergy.any { it != null }
    CyclusCard {
        CardTitle(fmt.s("card_mood"))
        CappedFontScale {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                // as wide as its longest label (no cut-off at large font sizes)
                Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).padding(end = 8.dp)) {
                    Spacer(Modifier.height(20.dp))
                    for (label in listOf("mood", "energy")) {
                        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(fmt.s(label), fontSize = 12.sp, color = c.ink2, maxLines = 1)
                        }
                    }
                }
                w.days.forEachIndexed { i, d ->
                    val m = w.mood[i]; val e = w.energy[i]
                    val um = w.usualMood[i]; val ue = w.usualEnergy[i]
                    val a11y = fmt.s("mwA11y", fmt.weekday(d), m?.toString() ?: "–", e?.toString() ?: "–") +
                        (if (um != null || ue != null) fmt.s("mwUsualA", um?.let { fmt.num(it, 1) } ?: "–", ue?.let { fmt.num(it, 1) } ?: "–") else "")
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { onOpenDay(d) }
                            .clearAndSetSemantics { contentDescription = a11y },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (d == w.days.last()) fmt.s("todayShort") else fmt.weekday(d), Modifier.height(20.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (d == w.days.last()) c.ink else c.ink2, maxLines = 1,
                        )
                        for ((v, usual) in listOf(m to um, e to ue)) ScaleCell(v, usual, Modifier.fillMaxWidth().height(48.dp))
                    }
                }
            }
        }
        if (hasUsual) Text(fmt.s("mwLegend"), Modifier.padding(top = 6.dp), fontSize = 12.sp, color = c.ink2)
    }
}

@Composable
private fun ScaleCell(value: Int?, usual: Double?, modifier: Modifier) {
    val c = Cyclus.colors
    Canvas(modifier) {
        val pad = 6.dp.toPx()
        fun y(v: Double) = size.height - pad - ((v - 1) / 4.0 * (size.height - 2 * pad)).toFloat()
        val cx = size.width / 2
        drawLine(c.line, Offset(cx, pad), Offset(cx, size.height - pad), 1.dp.toPx())
        usual?.let { u -> drawLine(c.ink2.copy(alpha = .45f), Offset(cx - 9.dp.toPx(), y(u)), Offset(cx + 9.dp.toPx(), y(u)), 2.dp.toPx(), StrokeCap.Round) }
        value?.let { v -> drawCircle(c.accent, 5.dp.toPx(), Offset(cx, y(v.toDouble()))) }
    }
}

/** „Fragen für die Ärztin“: the first open questions, tick one off in place; the sheet holds the whole list. */
@Composable
fun DoctorCardView(questions: List<DoctorQuestion>, fmt: Fmt, onToggle: (String) -> Unit, onOpenSheet: (add: Boolean) -> Unit) {
    val c = Cyclus.colors
    val open = DoctorQuestions.open(questions)
    CyclusCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CardTitle(fmt.s("card_doctor"))
            if (questions.isNotEmpty()) TextButton(onClick = { onOpenSheet(false) }) {
                Text(fmt.s("dqAll", questions.size), color = c.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        if (questions.isEmpty()) CardBody(fmt.s("dqEmpty"))
        else if (open.isEmpty()) CardBody(fmt.s("dqDone", questions.size))
        for (q in open.take(3)) QuestionRow(q, onToggle)
        LinkRow("+ " + fmt.s("dqAdd"), { onOpenSheet(true) })
    }
}

@Composable
private fun QuestionRow(q: DoctorQuestion, onToggle: (String) -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val c = Cyclus.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Checkbox) { onToggle(q.id) }.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(q.done, onCheckedChange = null, Modifier.padding(horizontal = 10.dp), colors = CheckboxDefaults.colors(checkedColor = c.accent))
        Text(
            q.text, Modifier.weight(1f), fontSize = 14.sp, color = if (q.done) c.ink2 else c.ink,
            textDecoration = if (q.done) TextDecoration.LineThrough else null,
        )
        trailing?.invoke()
    }
}

/** The whole list: add, tick off, edit or delete a question, and clear the done ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorQuestionsSheet(
    questions: List<DoctorQuestion>, fmt: Fmt,
    onAdd: (String) -> Unit, onEdit: (String, String) -> Unit, onToggle: (String) -> Unit, onDelete: (String) -> Unit,
    onClearDone: () -> Unit, onDismiss: () -> Unit,
    /** Opened from „+ Frage hinzufügen“: the input has the focus (keyboard up). */
    focusInput: Boolean = false,
) {
    val focus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    if (focusInput) androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val c = Cyclus.colors
    var text by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val open = DoctorQuestions.open(questions)
    val done = DoctorQuestions.done(questions)
    val full = open.size >= DoctorQuestions.MAX
    fun add() { if (text.isNotBlank() && !full) { onAdd(text); text = "" } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fmt.s("card_doctor"), Modifier.weight(1f).semantics { heading() }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            TextButton(onClick = onDismiss) { Text(fmt.s("done"), color = c.accent, fontWeight = FontWeight.SemiBold) }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                text, { text = it.take(DoctorQuestions.MAX_LEN) }, Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text(fmt.s("dqPh")) }, enabled = !full,
                supportingText = { Text(if (full) fmt.s("dqFull") else "${text.length}/${DoctorQuestions.MAX_LEN}") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(fmt.s("dqHint"), Modifier.weight(1f), fontSize = 12.sp, color = c.ink2)
                PrimaryButton(fmt.s("dqAdd"), { add() }, enabled = text.isNotBlank() && !full)
            }
            if (questions.isEmpty()) CardBody(fmt.s("dqEmpty"))
            for (q in open) {
                if (editing == q.id) EditRow(editText, { editText = it.take(DoctorQuestions.MAX_LEN) }, fmt,
                    onSave = { onEdit(q.id, editText); editing = null }, onDelete = { onDelete(q.id); editing = null })
                else QuestionRow(q, onToggle) { RowMenu(fmt, onEdit = { editing = q.id; editText = q.text }, onDelete = { onDelete(q.id) }) }
            }
            if (done.isNotEmpty()) {
                TextButton(onClick = { showDone = !showDone }) {
                    Text((if (showDone) "▾ " else "▸ ") + fmt.s("dqDone", done.size), color = c.ink2, fontWeight = FontWeight.SemiBold)
                }
                if (showDone) {
                    for (q in done) QuestionRow(q, onToggle) { RowMenu(fmt, onEdit = null, onDelete = { onDelete(q.id) }) }
                    TextButton(onClick = onClearDone) { Text(fmt.s("dqClear"), color = c.danger, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun RowMenu(fmt: Fmt, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Text("⋯", fontSize = 20.sp, color = Cyclus.colors.ink2) }
    DropdownMenu(open, onDismissRequest = { open = false }) {
        if (onEdit != null) DropdownMenuItem(text = { Text(fmt.s("dqEdit")) }, onClick = { open = false; onEdit() })
        DropdownMenuItem(text = { Text(fmt.s("dqDel")) }, onClick = { open = false; onDelete() })
    }
}

@Composable
private fun EditRow(value: String, onChange: (String) -> Unit, fmt: Fmt, onSave: () -> Unit, onDelete: () -> Unit) {
    val c = Cyclus.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(
            value, onChange, Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDelete) { Text(fmt.s("dqDel"), color = c.danger) }
            TextButton(onClick = onSave) { Text(fmt.s("dqSave"), color = c.accent, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** „Phasen-Hinweis“: the phase, one neutral line and the person's own patterns for this part of the cycle. */
@Composable
fun PhaseCardView(pc: PhaseCard, fmt: Fmt, onPatterns: () -> Unit) {
    val c = Cyclus.colors
    CyclusCard {
        Text(fmt.s("card_phase").uppercase(fmt.locale), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .7.sp, color = c.ink2)
        Text(fmt.s("ph_${pc.phase.key}"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
        CardBody(fmt.s("phL_${pc.phase.key}"))
        if (pc.patterns.isNotEmpty()) {
            Text(fmt.s("phPat"), Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            for (p in pc.patterns) Text("• " + fmt.txt(p.title), fontSize = 14.sp, color = c.ink)
        }
        Text(
            fmt.s("phInfo") + if (pc.notForBC) " " + fmt.s("notBC") else "",
            Modifier.padding(top = 8.dp), fontSize = 12.sp, color = c.ink2,
        )
        if (pc.patterns.isNotEmpty()) LinkRow(fmt.s("phMore"), onPatterns)
    }
}
