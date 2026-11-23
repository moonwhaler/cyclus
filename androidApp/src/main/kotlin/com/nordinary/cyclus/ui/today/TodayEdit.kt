package com.nordinary.cyclus.ui.today

import androidx.compose.foundation.background
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nordinary.cyclus.core.Catalog
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.presenter.CalendarCell
import com.nordinary.cyclus.presenter.QuickPicks
import com.nordinary.cyclus.presenter.TodayCard
import com.nordinary.cyclus.presenter.TodayLayout
import com.nordinary.cyclus.presenter.UpcomingItem
import com.nordinary.cyclus.presenter.UpcomingKind
import com.nordinary.cyclus.ui.calendar.DayCell
import com.nordinary.cyclus.ui.common.CyclusCard
import com.nordinary.cyclus.ui.common.CyclusChip
import com.nordinary.cyclus.ui.common.CyclusIcons
import com.nordinary.cyclus.ui.settings.Segments
import com.nordinary.cyclus.ui.theme.Cyclus

/** Name of a card in edit mode and in the library. */
fun cardTitle(card: TodayCard, fmt: Fmt): String = fmt.s(
    when (card) {
        TodayCard.LOG -> "todaysLog"; TodayCard.TEST -> "testTitle"; TodayCard.BEST_DAYS -> "bestDays"
        TodayCard.LH_PLAN -> "lhTests"; TodayCard.LH_TODAY -> "lhToday"; TodayCard.FORECAST -> "fcT"
        TodayCard.FERTILE -> "card_fertile"; TodayCard.PRE -> "lgPre"; TodayCard.UPCOMING -> "card_upcoming"
        TodayCard.PATTERNS -> "card_patterns"; TodayCard.WEEK -> "card_week"
        TodayCard.USUAL, TodayCard.TEMP, TodayCard.MOOD, TodayCard.DOCTOR, TodayCard.PHASE -> "card_${card.key}"
    },
)

/**
 * „Ansicht anpassen“ (spec §7.2): every card that isn't hidden, in stored order (whether or not it applies today, so
 * positions always match the stored order), with a drag handle and an eye; hidden cards wait in „Karte hinzufügen“.
 * Hide, add and reset save at once; a drag is kept locally and saved on drop (no database round trip per step).
 */
@Composable
fun TodayEditor(
    layout: TodayLayout, fmt: Fmt, onLayout: (TodayLayout) -> Unit, onEditQuickPicks: () -> Unit, onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Today's scroll: a drag near the top or bottom edge scrolls the list, so every position can be reached. */
    scroll: ScrollState? = null,
    /** The visible part of that scroll (root coordinates, already without the top bar and bottom insets). */
    viewport: () -> Rect? = { null },
) {
    val c = Cyclus.colors
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.toPx() }
    val edge = with(density) { 88.dp.toPx() }
    val maxStep = with(density) { 18.dp.toPx() }
    val scope = rememberCoroutineScope()
    // gesture callbacks live as long as the row: they must always see the current layout, never the one at creation
    val current by rememberUpdatedState(layout)
    val save by rememberUpdatedState(onLayout)

    // the order shown while dragging and until the saved order comes back from the store (no snap back on drop)
    var working by remember { mutableStateOf(layout.shown) }
    var dragging by remember { mutableStateOf<TodayCard?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    /** Auto-scroll starts only once the finger has actually moved (grabbing a card near an edge doesn't scroll). */
    var moved by remember { mutableStateOf(false) }
    val heights = remember { mutableStateMapOf<TodayCard, Int>() }
    val handles = remember { mutableMapOf<TodayCard, LayoutCoordinates>() }
    // neighbours glide into their new slot instead of jumping
    val settle = remember { mutableStateMapOf<TodayCard, Float>() }
    LaunchedEffect(layout.shown) { if (dragging == null) working = layout.shown }

    fun commit(list: List<TodayCard>) = save(current.copy(order = list + current.order.filter { it in current.hidden }))

    fun glide(card: TodayCard, from: Float) {
        settle[card] = from
        scope.launch { animate(from, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> settle[card] = v } }
    }

    /** Swap the dragged card past every neighbour it has crossed by half (fast drags cross several). */
    fun reorder(card: TodayCard) {
        while (true) {
            val idx = working.indexOf(card)
            val own = (heights[card] ?: 0) + gap
            if (offset > 0 && idx < working.lastIndex) {
                val next = working[idx + 1]
                val h = (heights[next] ?: 0) + gap
                if (offset <= h / 2) return
                working = working.toMutableList().also { it[idx] = next; it[idx + 1] = card }
                offset -= h; glide(next, own)
            } else if (offset < 0 && idx > 0) {
                val prev = working[idx - 1]
                val h = (heights[prev] ?: 0) + gap
                if (offset >= -h / 2) return
                working = working.toMutableList().also { it[idx] = prev; it[idx - 1] = card }
                offset += h; glide(prev, -own)
            } else return
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // auto-scroll while the finger rests near an edge; the row stays under the finger
    LaunchedEffect(dragging) {
        val card = dragging ?: return@LaunchedEffect
        val sc = scroll ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            if (!moved) continue
            val vp = viewport() ?: continue
            val step = when {
                fingerY < vp.top + edge -> -maxStep * ((vp.top + edge - fingerY) / edge).coerceIn(0f, 1f)
                fingerY > vp.bottom - edge -> maxStep * ((fingerY - (vp.bottom - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (step != 0f) {
                val used = sc.scrollBy(step)
                if (used != 0f) { offset += used; reorder(card) }
            }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmt.s("customize"), Modifier.weight(1f).semantics { heading() }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink)
            TextButton(onClick = onDone) { Text(fmt.s("done"), color = c.accent, fontWeight = FontWeight.Bold) }
        }
        Text(fmt.s("ringSize").uppercase(fmt.locale), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .7.sp, color = c.ink2)
        Segments(listOf(fmt.s("ringLarge"), fmt.s("ringCompact")), if (layout.ringCompact) 1 else 0) { onLayout(layout.copy(ringCompact = it == 1)) }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(fmt.s("cardsL").uppercase(fmt.locale), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .7.sp, color = c.ink2)
            Text(fmt.s("cardsHint"), fontSize = 13.sp, color = c.ink2)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((i, card) in working.withIndex()) androidx.compose.runtime.key(card) {
                val dragged = dragging == card
                val title = cardTitle(card, fmt)
                val actions = buildList {
                    if (i > 0) add(CustomAccessibilityAction(fmt.s("moveUp")) { commit(current.move(card, i - 1).shown); true })
                    if (i < working.lastIndex) add(CustomAccessibilityAction(fmt.s("moveDown")) { commit(current.move(card, i + 1).shown); true })
                    add(CustomAccessibilityAction(fmt.s("hideCard")) { save(current.hide(card)); true })
                }
                Row(
                    Modifier.fillMaxWidth()
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragged) offset else settle[card] ?: 0f
                            shadowElevation = if (dragged) 10.dp.toPx() else 0f
                            scaleX = if (dragged) 1.02f else 1f; scaleY = scaleX
                            shape = RoundedCornerShape(14.dp); clip = true
                        }
                        .background(c.surface)
                        .onSizeChanged { heights[card] = it.height }
                        .semantics(mergeDescendants = true) { customActions = actions }
                        .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(title, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(fmt.s("ch_${card.key}"), fontSize = 13.sp, color = c.ink2)
                        if (card == TodayCard.LOG) Text(
                            fmt.s("editQuick"),
                            Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onEditQuickPicks).padding(vertical = 4.dp),
                            color = c.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        )
                    }
                    IconButton(onClick = { onLayout(layout.hide(card)) }) {
                        Icon(CyclusIcons.EyeOff, "${fmt.s("hideCard")}: $title", Modifier.size(20.dp), tint = c.ink2)
                    }
                    Box(
                        Modifier.size(48.dp)
                            .onGloballyPositioned { handles[card] = it }
                            .semantics { contentDescription = fmt.s("dragCard") }
                            .pointerInput(card) {
                                fun root(p: Offset) = handles[card]?.takeIf { it.isAttached }?.localToRoot(p)?.y ?: fingerY
                                detectDragGestures(
                                    onDragStart = { p ->
                                        working = current.shown; dragging = card; offset = 0f; moved = false; fingerY = root(p)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = {
                                        val order = working
                                        commit(order)
                                        // settle into the slot, then let go
                                        scope.launch { animate(offset, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> offset = v }; if (dragging == card) dragging = null }
                                    },
                                    onDragCancel = { dragging = null; offset = 0f; working = current.shown },
                                ) { change, amount ->
                                    change.consume()
                                    fingerY = root(change.position)
                                    moved = true
                                    offset += amount.y
                                    reorder(card)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(CyclusIcons.DragHandle, null, Modifier.size(22.dp), tint = c.ink2) }
                }
            }
        }

        Text(fmt.s("addCard").uppercase(fmt.locale), Modifier.padding(top = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .7.sp, color = c.ink2)
        val hidden = layout.order.filter { it in layout.hidden }
        if (hidden.isEmpty()) Text(fmt.s("addCardNone"), fontSize = 13.sp, color = c.ink2)
        else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)) {
            for (card in hidden) {
                val title = cardTitle(card, fmt)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onLayout(layout.show(card)) }.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.SemiBold, color = c.ink)
                        Text(fmt.s("ch_${card.key}"), fontSize = 13.sp, color = c.ink2)
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(CyclusIcons.Plus, "${fmt.s("showCard")}: $title", Modifier.size(20.dp), tint = c.accent)
                    }
                }
            }
        }
        if (layout.layoutValue() != null) Text(
            fmt.s("resetLayout"),
            Modifier.clip(RoundedCornerShape(6.dp)).clickable { onLayout(layout.reset()) }.padding(vertical = 8.dp, horizontal = 2.dp),
            color = c.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        )
    }
}

/** „Deine Schnellauswahl“: up to 8 symptoms, feelings or tags, in the order picked; saved on every tap. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickPicksSheet(current: List<String>, fmt: Fmt, onChange: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val c = Cyclus.colors
    var picks by remember { mutableStateOf(current) }
    fun toggle(k: String) {
        picks = if (k in picks) picks - k else if (picks.size < QuickPicks.MAX) picks + k else return
        onChange(picks)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fmt.s("quickT"), Modifier.weight(1f).semantics { heading() }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            TextButton(onClick = onDismiss) { Text(fmt.s("done"), color = c.accent, fontWeight = FontWeight.SemiBold) }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(fmt.s("quickB"), fontSize = 14.sp, color = c.ink2)
            Text(fmt.s("quickN", picks.size, QuickPicks.MAX), fontWeight = FontWeight.SemiBold, color = c.ink)
            // the current picks, in order; a tap removes one
            if (picks.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in picks) CyclusChip(fmt.item(k), true, { toggle(k) })
            }
            for ((label, keys) in listOf("symptoms" to Catalog.SYMPTOMS, "feelings" to Catalog.EMOTIONS, "tags" to Catalog.TAGS)) {
                Text(fmt.s(label).uppercase(fmt.locale), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .7.sp, color = c.ink2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in keys) CyclusChip(fmt.item(k), k in picks, { toggle(k) })
                }
            }
        }
    }
}

/** „Demnächst“: what's next, in date order; a tap shows that day in the calendar. */
@Composable
fun UpcomingCard(items: List<UpcomingItem>, today: Day, fmt: Fmt, onOpenDay: (Day) -> Unit) {
    val c = Cyclus.colors
    CyclusCard {
        CardTitle(fmt.s("card_upcoming"))
        Column(Modifier.padding(top = 4.dp)) {
            for (it in items) {
                val target = maxOf(it.likely ?: it.from, today)
                val (label, color) = when (it.kind) {
                    UpcomingKind.PRE -> fmt.s("lgPre") to c.pre
                    UpcomingKind.FERTILE -> fmt.s("card_fertile") to c.fertile
                    UpcomingKind.OVULATION -> fmt.s("rhOv") to c.fertile
                    UpcomingKind.PERIOD -> fmt.s("nextPeriod") to c.period
                    UpcomingKind.REMINDER -> fmt.s(if (it.reminder == com.nordinary.cyclus.presenter.NotifyKind.BACKUP) "upRemBackup" else "upRemLater") to c.ink2
                }
                val ongoing = it.ongoing(today)
                val whenText = when {
                    ongoing -> fmt.s("upNow", fmt.md(it.end))
                    it.kind == UpcomingKind.PERIOD && it.likely != null -> "${fmt.s("around", fmt.md(it.likely!!))} · ${fmt.s("possibleR", fmt.range(it.from, it.end))}"
                    it.to != null -> fmt.range(it.from, it.end)
                    else -> fmt.wmd(it.from)
                }
                val d = (it.likely ?: it.from) - today
                val rel = when {
                    ongoing -> null
                    d <= 0 -> fmt.s("todayShort")
                    d == 1 -> fmt.s("tomorrow")
                    else -> fmt.s("upInDays", d)
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpenDay(target) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Column(Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.SemiBold, color = c.ink, fontSize = 14.sp)
                        Text(whenText, fontSize = 13.sp, color = c.ink2)
                    }
                    rel?.let { r -> Text(r, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink2) }
                }
            }
        }
    }
}

/** „Woche“: three days back, today, three ahead, drawn like the calendar; a tap shows the day in the calendar. */
@Composable
fun WeekCard(cells: List<CalendarCell>, fmt: Fmt, onOpenDay: (Day) -> Unit, onMarkStart: (Day) -> Unit) {
    val c = Cyclus.colors
    CyclusCard {
        CardTitle(fmt.s("card_week"))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            for (cell in cells) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmt.weekday(cell.day), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.ink2)
                DayCell(cell, fmt, Modifier.fillMaxWidth(), selected = false, onDay = onOpenDay, onMarkStart = onMarkStart)
            }
        }
    }
}
