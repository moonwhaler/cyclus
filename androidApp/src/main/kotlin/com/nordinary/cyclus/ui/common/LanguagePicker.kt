package com.nordinary.cyclus.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.ui.theme.Cyclus

/** One choice in [LanguagePicker]: [key] null = "follow the system". */
data class LanguageOption(val key: String?, val label: String, val sub: String? = null)

/**
 * A row showing the current language; a tap opens a sheet with one radio row per option (nine languages are too many
 * for a segmented control, and their names don't shrink). Used by Settings and the Doctor Visit Pack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePicker(fmt: Fmt, title: String, options: List<LanguageOption>, selected: String?, onPick: (String?) -> Unit) {
    val c = Cyclus.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val current = options.firstOrNull { it.key == selected } ?: options.first()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
            .clickable(role = Role.Button) { open = true }.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(current.label, Modifier.weight(1f), fontWeight = FontWeight.Medium, color = c.ink)
        Text("›", color = c.ink2, fontSize = 18.sp)
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            TextButton(onClick = { open = false }) { Text(fmt.s("done"), color = c.accent, fontWeight = FontWeight.SemiBold) }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
                .clip(RoundedCornerShape(14.dp)).background(c.surface).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            for (o in options) Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .selectable(selected = o.key == current.key, role = Role.RadioButton) { open = false; if (o.key != current.key) onPick(o.key) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(o.key == current.key, onClick = null, Modifier.padding(horizontal = 8.dp), colors = RadioButtonDefaults.colors(selectedColor = c.accent))
                Column(Modifier.weight(1f)) {
                    Text(o.label, color = c.ink, fontWeight = FontWeight.Medium)
                    if (o.sub != null) Text(o.sub, fontSize = 13.sp, color = c.ink2)
                }
            }
        }
    }
}
