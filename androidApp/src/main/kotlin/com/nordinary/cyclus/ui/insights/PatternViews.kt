package com.nordinary.cyclus.ui.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.R
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.insights.Area
import com.nordinary.cyclus.insights.Pattern
import com.nordinary.cyclus.insights.PatternStatus
import com.nordinary.cyclus.presenter.PatternSection
import com.nordinary.cyclus.ui.common.CyclusCard
import com.nordinary.cyclus.ui.common.dashedBorder
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.CyclusColors

internal fun areaColor(a: Area, c: CyclusColors) = when (a) {
    Area.BODY -> c.period; Area.MOOD -> c.pre; Area.LIFE -> c.warning; Area.FERT -> c.fertile; Area.HEALTH -> c.ink2
}

/** Status pill (prototype `.badge.st-*`). */
@Composable
internal fun StatusChip(status: PatternStatus, fmt: Fmt) {
    val c = Cyclus.colors
    val label = fmt.s("st_${status.key}")
    val base = Modifier.clip(CircleShape)
    val m = when (status) {
        PatternStatus.CONSISTENT -> base.background(c.accent.copy(alpha = .16f).compositeOver(c.surface))
        PatternStatus.EARLY -> base.dashedBorder(c.ink2, 12.dp)
        else -> base.background(c.surface2)
    }
    val color = when (status) {
        PatternStatus.STABLE -> c.success; PatternStatus.NONE, PatternStatus.NEEDS -> c.ink2; else -> c.ink
    }
    Text(label, m.padding(horizontal = 11.dp, vertical = 5.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
}

@Composable
private fun AreaLabel(a: Area, fmt: Fmt) {
    val c = Cyclus.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(areaColor(a, c)))
        Text(fmt.s("area_${a.key}").uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = c.ink2)
    }
}

/** A found pattern in full (Muster screen, sheet). [onMute] null: no „Ausblenden“ (watched patterns). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PatternCard(p: Pattern, fmt: Fmt, onMute: ((String) -> Unit)?, whyOpen: Boolean = false) {
    val c = Cyclus.colors
    var open by rememberSaveable(p.id) { mutableStateOf(whyOpen) }
    CyclusCard {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AreaLabel(p.area, fmt)
            StatusChip(p.status, fmt)
        }
        Text(fmt.txt(p.title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
        Text(fmt.txt(p.body), fontSize = 14.sp, color = c.ink2)
        if (p.report == true) Text(fmt.s("inReport"), fontSize = 12.sp, color = c.ink)
        if (p.why != null || onMute != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            if (p.why != null) Column(Modifier.weight(1f)) {
                Text(
                    fmt.s("why"), Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { open = !open }.padding(vertical = 4.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                )
                AnimatedVisibility(open) { Text(fmt.txt(p.why!!), fontSize = 12.sp, color = c.ink2) }
            } else Spacer(Modifier.weight(1f))
            if (onMute != null) Text(
                fmt.s("hide"), Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { onMute(p.id) }.padding(4.dp),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.ink2,
            )
        }
    }
}

/**
 * „Deine Muster“ on the Insights overview: counter, the top three found patterns as one-line rows (tap → sheet),
 * and the way to the full Muster screen. Keeps Insights short however many patterns there are.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PatternSummaryCard(sec: PatternSection, fmt: Fmt, onOpen: (Pattern) -> Unit, onAll: () -> Unit) {
    val c = Cyclus.colors
    val found = sec.found.size
    val watching = sec.watching.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // wraps instead of colliding at large font sizes
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(fmt.s("pwT").uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = c.ink2)
            val res = LocalResources.current
            val counter = listOfNotNull(
                found.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.pat_found_n, it, it) },
                watching.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.pat_watch_n, it, it) },
            ).joinToString(" · ")
            val spoken = counter.replace(" · ", ", ")
            if (counter.isNotEmpty()) Text(counter, Modifier.semantics { contentDescription = spoken }, fontSize = 12.sp, color = c.ink2)
        }
        if (found == 0 && watching == 0 && sec.hiddenCount == 0) {
            Text(fmt.s("pwIntro"), Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = c.ink2)
            return@Column
        }
        val large = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)) {
            for ((i, p) in sec.found.take(3).withIndex()) {
                if (i > 0) HorizontalDivider(color = c.line)
                val title = fmt.txt(p.title)
                val a11y = "$title, ${fmt.s("area_${p.area.key}")}, ${fmt.s("st_${p.status.key}")}"
                val openLabel = stringResource(R.string.pat_open)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clickable(role = Role.Button, onClickLabel = openLabel) { onOpen(p) }
                        .clearAndSetSemantics { contentDescription = a11y; role = Role.Button; onClick(openLabel) { onOpen(p); true } }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(areaColor(p.area, c)))
                    // „Beständiges Muster“ is the normal case here and isn't repeated on every row; only the exception
                    // (early pattern) gets a chip, and at large font sizes it moves under the title
                    val chip = p.status != PatternStatus.CONSISTENT
                    if (large) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (chip) StatusChip(p.status, fmt)
                    } else {
                        Text(title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (chip) StatusChip(p.status, fmt)
                    }
                }
            }
            if (found > 0) HorizontalDivider(color = c.line)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onAll).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val res = LocalResources.current
                Text(
                    when {
                        found > 0 -> res.getQuantityString(R.plurals.pat_all, found + watching, found + watching)
                        watching > 0 -> res.getQuantityString(R.plurals.pat_none_watch, watching, watching)
                        else -> fmt.s("hiddenN", sec.hiddenCount) // everything hidden: the way back stays reachable
                    },
                    Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                )
                Text("›", color = c.ink2, fontSize = 18.sp)
            }
        }
    }
}

/** One pattern in full from the overview: Why open, Hide closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PatternSheet(p: Pattern, fmt: Fmt, onMute: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Cyclus.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fmt.s("pwT"), Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            TextButton(onClick = onDismiss) { Text(fmt.s("close"), color = c.accent, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
            PatternCard(p, fmt, onMute = { onMute(it); onDismiss() }, whyOpen = true)
        }
    }
}
