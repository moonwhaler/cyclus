package com.nordinary.cyclus.ui.sheets

import androidx.compose.foundation.background
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
