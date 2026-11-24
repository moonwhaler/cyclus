package com.nordinary.cyclus.ui.insights

import com.nordinary.cyclus.ui.theme.BarTitleStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.R
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.insights.Area
import com.nordinary.cyclus.presenter.PatternSection
import com.nordinary.cyclus.ui.common.CyclusCard
import com.nordinary.cyclus.ui.common.CyclusIcons
import com.nordinary.cyclus.ui.theme.Cyclus

/**
 * All patterns („Deine Muster“): found / being watched, filtered by area. The Insights overview shows only the top
 * three, so this list can grow without pushing the charts and the Arztbericht down.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PatternsScreen(sec: PatternSection, fmt: Fmt, onBack: () -> Unit, onMute: (String) -> Unit, onUnmute: () -> Unit) {
    val c = Cyclus.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var watchingChoice by rememberSaveable { mutableStateOf(sec.found.isEmpty() && sec.watching.isNotEmpty()) }
    var areaKey by rememberSaveable { mutableStateOf<String?>(null) }
    // a segment that became empty (last pattern hidden) falls back to the other one
    val watchingTab = when {
        watchingChoice && sec.watching.isEmpty() -> false
        !watchingChoice && sec.found.isEmpty() && sec.watching.isNotEmpty() -> true
        else -> watchingChoice
    }
    val list = if (watchingTab) sec.watching else sec.found
    val areas = Area.entries.filter { a -> list.any { it.area == a } }
    val area = areas.firstOrNull { it.key == areaKey } // a filter whose area left the list falls back to „Alle“
    val shown = if (area == null) list else list.filter { it.area == area }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = c.bg,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(fmt.s("pwT"), style = BarTitleStyle) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(CyclusIcons.Back, fmt.s("back"), tint = c.accent) } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, scrolledContainerColor = c.bg),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(fmt.s("pwIntro"), Modifier.padding(horizontal = 4.dp), fontSize = 13.sp, color = c.ink2)
                if (sec.found.isNotEmpty() && sec.watching.isNotEmpty()) {
                    val labels = listOf(stringResource(R.string.pat_seg_found, sec.found.size), stringResource(R.string.pat_seg_watch, sec.watching.size))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        labels.forEachIndexed { i, l ->
                            SegmentedButton(
                                selected = watchingTab == (i == 1), onClick = { watchingChoice = i == 1 },
                                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                                colors = SegmentedButtonDefaults.colors(activeContainerColor = c.accent.copy(alpha = .14f), activeContentColor = c.ink, inactiveContainerColor = c.surface),
                            ) { Text(l, maxLines = 1) }
                        }
                    }
                }
                if (areas.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.accent.copy(alpha = .14f), containerColor = c.surface)
                    FilterChip(area == null, { areaKey = null }, label = { Text(stringResource(R.string.pat_filter_all)) }, colors = chipColors)
                    for (a in areas) FilterChip(
                        area == a, { areaKey = if (area == a) null else a.key },
                        label = { Text(fmt.s("area_${a.key}")) },
                        leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(areaColor(a, c))) },
                        colors = chipColors,
                    )
                }
                if (shown.isEmpty()) CyclusCard { Text(stringResource(R.string.pat_empty_filter), fontSize = 14.sp, color = c.ink2) }
                for (p in shown) androidx.compose.runtime.key(p.id) { PatternCard(p, fmt, onMute = if (watchingTab) null else onMute) }
                if (sec.hiddenCount > 0) Text(
                    fmt.s("hiddenN", sec.hiddenCount),
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onUnmute).padding(4.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
