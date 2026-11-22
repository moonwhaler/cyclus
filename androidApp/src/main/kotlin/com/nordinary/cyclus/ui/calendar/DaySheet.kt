package com.nordinary.cyclus.ui.calendar

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.R
import com.nordinary.cyclus.health.ExternalValue
import com.nordinary.cyclus.health.HealthType
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.presenter.DayEntry
import com.nordinary.cyclus.presenter.DaySheetModel
import com.nordinary.cyclus.ui.common.Badge
import com.nordinary.cyclus.ui.common.CyclusCard
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.today.PrimaryButton
import com.nordinary.cyclus.ui.today.SecondaryButton

/**
 * Day detail (prototype `renderDaySheet`), shown under the calendar grid for the selected day: entries, values from
 * other apps, and the actions „Bearbeiten“ / „Als Periodenbeginn markieren“ (no long-press needed).
 */
@Composable
fun DayDetail(
    model: DaySheetModel, fmt: Fmt, onEdit: () -> Unit, onMarkStart: () -> Unit,
    /** Values from other apps via Health Connect, with source; not counted when the person logged the field. */
    external: List<ExternalValue> = emptyList(),
) {
    val c = Cyclus.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // announced when another day is selected
        Text(
            fmt.long(model.day), Modifier.semantics { liveRegion = LiveRegionMode.Polite; heading() },
            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink,
        )
        if (model.predictedPeriod || model.fertTier > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (model.predictedPeriod) Badge(fmt.s("predPeriod"))
            if (model.fertTier > 0) Badge(fmt.s("fertBadge", fmt.s("ftAdj${model.fertTier}")), dot = c.fertile, color = c.fertile)
        }
        if (model.entries.isEmpty()) CyclusCard { Text(fmt.s("dsNothing"), fontSize = 14.sp, color = c.ink2) }
        else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)) {
            model.entries.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(color = c.line)
                val (k, v) = row(e, fmt)
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(k, fontSize = 13.sp, color = c.ink2)
                    Text(v, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink)
                }
            }
        }
        if (external.isNotEmpty()) {
            val pm = LocalContext.current.packageManager
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)) {
                external.forEachIndexed { i, x ->
                    if (i > 0) HorizontalDivider(color = c.line)
                    val app = remember(x.origin) {
                        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(x.origin, 0)).toString() }.getOrDefault(x.origin)
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            androidx.compose.ui.res.stringResource(com.nordinary.cyclus.ui.privacy.typeLabel(x.type)),
                            fontSize = 13.sp, color = c.ink2,
                        )
                        externalValue(x, fmt)?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (x.used) c.ink else c.ink2) }
                        val src = androidx.compose.ui.res.stringResource(R.string.hc_source, app)
                        val nc = androidx.compose.ui.res.stringResource(R.string.hc_not_counted)
                        Text(if (x.used) src else "$src · $nc", fontSize = 12.sp, color = c.ink2)
                    }
                }
            }
        }
        if (model.showSource) Text(fmt.s("dsSource"), fontSize = 12.sp, color = c.ink2)
        if (model.canEdit) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(if (model.entries.isEmpty()) fmt.s("logThisDay") else fmt.s("edit"), onEdit, Modifier.weight(1f))
            SecondaryButton(fmt.s("markStart"), onMarkStart)
        }
    }
}

private fun externalValue(x: ExternalValue, fmt: Fmt): String? = when (x.type) {
    HealthType.FLOW -> fmt.item(x.value).replace("\u00AD", "")
    HealthType.BBT -> x.value.toDoubleOrNull()?.let { "${fmt.temp(it)} °C" }
    HealthType.MUCUS -> fmt.item(x.value)
    HealthType.LH -> fmt.s("lh_${x.value}")
    else -> null
}

private fun row(e: DayEntry, fmt: Fmt): Pair<String, String> = when (e) {
    is DayEntry.FlowEntry -> fmt.s("flowL") to fmt.item(e.flow.key).replace("­", "")
    is DayEntry.Symptoms -> fmt.s("symptoms") to e.keys.joinToString(", ") { fmt.item(it) }
    is DayEntry.Pain -> fmt.s("painL") to e.regions.joinToString(", ") { (r, v) -> "${fmt.item(r)} $v/10" }
    is DayEntry.Feelings -> fmt.s("feelings") to e.keys.joinToString(", ") { fmt.item(it) }
    is DayEntry.Scale -> fmt.s(e.key) to "${e.value} / 5"
    is DayEntry.Discharge -> fmt.s("discharge") to fmt.item(e.mucus.key)
    is DayEntry.Lh -> fmt.s("lhTest") to fmt.s("lh_${e.result.key}")
    is DayEntry.Temperature -> fmt.s("tempL") to "${fmt.temp(e.celsius)} °C"
    is DayEntry.Tags -> fmt.s("tags") to e.keys.joinToString(", ") { fmt.item(it) }
    is DayEntry.Note -> fmt.s("note") to e.text
}
