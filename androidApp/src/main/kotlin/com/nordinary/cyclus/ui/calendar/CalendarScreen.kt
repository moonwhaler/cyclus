package com.nordinary.cyclus.ui.calendar

import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.presenter.CalendarCell
import com.nordinary.cyclus.presenter.CalendarPresenter
import com.nordinary.cyclus.presenter.DayMarker
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.Nunito
import java.time.LocalDate

/** Kalender tab (prototype `renderCalendar`). */
@Composable
fun CalendarContent(
    snap: CycleSnapshot,
    fmt: Fmt,
    contentPadding: PaddingValues,
    onMarkStart: (Int) -> Unit,
    /** The selected day's detail, shown under the grid (a tap selects; today is selected at first). */
    detail: @Composable (day: Int) -> Unit,
) {
    val c = Cyclus.colors
    val todayDate = LocalDate.ofEpochDay(snap.today.toLong())
    var ym by rememberSaveable { mutableStateOf(todayDate.year * 12 + todayDate.monthValue - 1) }
    val year = ym / 12
    val month = ym % 12 + 1
    val model = remember(snap, ym) { CalendarPresenter.month(snap, year, month) }
    var dragged by remember { mutableFloatStateOf(0f) }
    var selected by rememberSaveable { mutableStateOf(snap.today) }
    val onDay: (Int) -> Unit = { selected = it }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged < -80f) ym++ else if (dragged > 80f) ym--; dragged = 0f },
                ) { _, d -> dragged += d }
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            NavArrow("‹", fmt.s("prevMonth")) { ym-- }
            Text(fmt.monthYear(year, month), Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center)
            NavArrow("›", fmt.s("nextMonth")) { ym++ }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            for (w in fmt.array("wk")) Text(w, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.ink2, textAlign = TextAlign.Center)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (week in model.cells.chunked(7)) Row(Modifier.fillMaxWidth()) {
                for (cell in week) DayCell(cell, fmt, Modifier.weight(1f), cell.day == selected, onDay, onMarkStart)
            }
        }
        Spacer(Modifier.height(14.dp))
        detail(selected)
        Text(fmt.s("calTip"), Modifier.padding(horizontal = 4.dp, vertical = 10.dp), fontSize = 12.sp, color = c.ink2)
        Legend(model.fertilityLegend, fmt)
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun NavArrow(symbol: String, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, Modifier.clearAndSetSemantics { contentDescription = label; onClick(label) { onClick(); true } }) {
        Text(symbol, fontSize = 24.sp, color = Cyclus.colors.ink)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(cell: CalendarCell, fmt: Fmt, modifier: Modifier, selected: Boolean, onDay: (Int) -> Unit, onMarkStart: (Int) -> Unit) {
    val c = Cyclus.colors
    var menu by remember { mutableStateOf(false) }
    val markLabel = fmt.s("markStart")
    val label = buildString {
        append(fmt.long(cell.day))
        if (cell.bleed) append(fmt.s("aPeriod"))
        if (cell.predicted) append(fmt.s("aPred"))
        if (cell.logged) append(fmt.s("aLogged"))
    }
    Box(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(c.period.copy(alpha = .10f)) else Modifier)
            .combinedClickable(onClick = { onDay(cell.day) }, onLongClick = { if (cell.canMarkStart) menu = true })
            .clearAndSetSemantics {
                contentDescription = label
                this.selected = selected
                onClick(null) { onDay(cell.day); true }
                if (cell.canMarkStart) customActions = listOf(CustomAccessibilityAction(markLabel) { onMarkStart(cell.day); true })
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        // fertile tint (+ dot pattern for high/peak)
        if (cell.fertTier > 0) {
            Box(
                Modifier.fertileArea().clip(RoundedCornerShape(12.dp))
                    .background(c.fertile.copy(alpha = floatArrayOf(0f, .16f, .32f, .5f)[cell.fertTier]))
                    .drawBehind {
                        if (cell.fertTier >= 2) {
                            val step = 6.dp.toPx()
                            var y = step / 2
                            while (y < size.height) { var x = step / 2; while (x < size.width) { drawCircle(c.fertile.copy(alpha = .55f), 1.dp.toPx(), Offset(x, y)); x += step }; y += step }
                        }
                    },
            )
        }
        Column(Modifier.padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // today's ring is drawn before the clip, so it may extend outside the 34dp circle
            val outline = if (cell.today) Modifier.drawBehind {
                drawCircle(c.ink, size.minDimension / 2 + 3.dp.toPx(), style = Stroke(2.dp.toPx()))
            } else Modifier
            val circle = Modifier.size(34.dp).then(outline).clip(CircleShape)
            Box(
                when {
                    cell.bleed -> circle.background(c.period)
                    cell.spotting -> circle.border(2.dp, c.period, CircleShape)
                    else -> circle
                }.then(if (cell.predicted) Modifier.drawBehind {
                    drawCircle(c.period, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${cell.dayOfMonth}", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = if (cell.bleed) c.onAccent else if (cell.inMonth) c.ink else c.ink2, modifier = Modifier.alpha(if (cell.inMonth) 1f else .45f),
                )
            }
            if (cell.logged) Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(c.ink2))
        }
        if (cell.prePeriod) Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).fillMaxWidth(.56f).height(2.dp).drawBehind {
                drawLine(c.pre, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
            },
        )
        if (cell.marker != DayMarker.NONE) Text(
            if (cell.marker == DayMarker.OVULATION) "◎" else "✦", Modifier.align(Alignment.TopEnd).padding(end = 3.dp),
            fontSize = 11.sp, color = c.fertile, lineHeight = 11.sp,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(markLabel) }, onClick = { menu = false; onMarkStart(cell.day) })
        }
    }
}

/** Prototype `.day .f`: inset 2px top, 1px sides, 12px bottom of the 50px cell. */
private fun Modifier.fertileArea() = this.then(Modifier.fillMaxWidth().padding(horizontal = 1.dp).padding(top = 2.dp).height(36.dp))

@Composable
private fun Legend(fertility: Boolean, fmt: Fmt) {
    val c = Cyclus.colors
    @Composable
    fun Item(label: String, modifier: Modifier = Modifier, swatch: @Composable () -> Unit) =
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { swatch() }
            Text(label, fontSize = 12.sp, color = c.ink2)
        }
    val items: List<Pair<String, @Composable () -> Unit>> = buildList {
        add(fmt.s("lgPeriod") to { Box(Modifier.size(18.dp).clip(CircleShape).background(c.period)) })
        add(fmt.s("lgPred") to {
            Box(Modifier.size(18.dp).drawBehind {
                drawCircle(c.period, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
            })
        })
        add(fmt.s("lgPre") to {
            Box(Modifier.width(18.dp).height(2.dp).drawBehind {
                drawLine(c.pre, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
            })
        })
        add(fmt.s("lgLogged") to { Box(Modifier.size(5.dp).clip(CircleShape).background(c.ink2)) })
        if (fertility) {
            add(fmt.s("lgFert") to { Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(c.fertile.copy(alpha = .45f))) })
            add(fmt.s("lgOv") to { Text("◎✦", color = c.fertile, fontSize = 10.sp, fontWeight = FontWeight.Bold) })
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in items.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((label, sw) in row) Item(label, Modifier.weight(1f), sw)
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

