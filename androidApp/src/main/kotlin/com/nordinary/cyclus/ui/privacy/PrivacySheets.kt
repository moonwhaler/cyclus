package com.nordinary.cyclus.ui.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.R
import com.nordinary.cyclus.backup.ImportPreview
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.lock.PinPad
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.today.PrimaryButton
import com.nordinary.cyclus.ui.today.SecondaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(title: String, fmt: Fmt, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Cyclus.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            TextButton(onClick = onDismiss) { Text(fmt.s("cancel"), color = c.accent, fontWeight = FontWeight.SemiBold) }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String) {
    val c = Cyclus.colors
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.line, focusedBorderColor = c.accent, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface),
    )
}

/** 0 = weak, 1 = fair, 2 = strong (length and character classes). */
fun passphraseStrength(p: String): Int {
    val classes = listOf(p.any { it.isLowerCase() }, p.any { it.isUpperCase() }, p.any { it.isDigit() }, p.any { !it.isLetterOrDigit() }).count { it }
    return when {
        p.length >= 16 || (p.length >= 12 && classes >= 3) -> 2
        p.length >= 10 && classes >= 2 -> 1
        else -> 0
    }
}

/** Passphrase entry for an encrypted backup; [onSave] is called before the file picker opens. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExportSheet(fmt: Fmt, working: Boolean, onSave: (CharArray) -> Unit, onDismiss: () -> Unit) = Sheet(stringResource(R.string.export_title), fmt, onDismiss) {
    val c = Cyclus.colors
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    Text(stringResource(R.string.export_warn), fontSize = 14.sp, color = c.ink2)
    SecretField(p1, { p1 = it }, stringResource(R.string.export_pass))
    if (p1.isNotEmpty()) {
        val st = passphraseStrength(p1)
        Text(
            stringResource(listOf(R.string.export_weak, R.string.export_fair, R.string.export_strong)[st]),
            color = listOf(c.danger, c.warning, c.success)[st], fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
    }
    SecretField(p2, { p2 = it }, stringResource(R.string.export_repeat))
    if (p2.isNotEmpty() && p1 != p2) Text(stringResource(R.string.export_mismatch), color = c.danger, fontSize = 13.sp)
    if (working) {
        Text(stringResource(R.string.export_working), color = c.ink2)
        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
    } else PrimaryButton(stringResource(R.string.export_save), { if (p1.length >= 10 && p1 == p2) onSave(p1.toCharArray()) }, Modifier.fillMaxWidth())
}

sealed interface ImportStage {
    data object Passphrase : ImportStage
    data object Working : ImportStage
    data class Error(val messageRes: Int) : ImportStage
    data class Preview(val preview: ImportPreview) : ImportStage
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImportSheet(
    fmt: Fmt,
    stage: ImportStage,
    onOpen: (CharArray) -> Unit,
    onApply: (replace: Boolean) -> Unit,
    onDismiss: () -> Unit,
) = Sheet(stringResource(R.string.import_t), fmt, onDismiss) {
    val c = Cyclus.colors
    var pass by remember { mutableStateOf("") }
    var confirmReplace by remember { mutableStateOf(false) }
    when (stage) {
        ImportStage.Passphrase, is ImportStage.Error -> {
            SecretField(pass, { pass = it }, stringResource(R.string.import_pass))
            if (stage is ImportStage.Error) Text(stringResource(stage.messageRes), color = c.danger, fontSize = 13.sp)
            PrimaryButton(stringResource(R.string.import_open), { if (pass.isNotEmpty()) onOpen(pass.toCharArray()) }, Modifier.fillMaxWidth())
        }
        ImportStage.Working -> {
            Text(stringResource(R.string.import_working), color = c.ink2)
            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        }
        is ImportStage.Preview -> {
            val p = stage.preview
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(14.dp)) {
                Text(
                    stringResource(R.string.import_preview, p.days, p.periodDays, p.firstDay?.let { fmt.md(iso(it)) } ?: "–", p.lastDay?.let { fmt.md(iso(it)) } ?: "–"),
                    color = c.ink, fontWeight = FontWeight.Medium,
                )
            }
            Text(stringResource(R.string.import_merge_s), fontSize = 13.sp, color = c.ink2)
            PrimaryButton(stringResource(R.string.import_merge), { onApply(false) }, Modifier.fillMaxWidth())
            Text(stringResource(R.string.import_replace_s), fontSize = 13.sp, color = c.ink2)
            if (!confirmReplace) SecondaryButton(stringResource(R.string.import_replace), { confirmReplace = true }, Modifier.fillMaxWidth())
            else PrimaryButton(stringResource(R.string.import_replace) + " ✓", { onApply(true) }, Modifier.fillMaxWidth())
        }
    }
}

private fun iso(s: String) = java.time.LocalDate.parse(s).toEpochDay().toInt()

/** Erase everything (prototype `renderEraseSheet`, Android copy): type the erase word to confirm. */
@Composable
fun EraseSheet(fmt: Fmt, healthWritten: Boolean, onErase: (deleteHealth: Boolean) -> Unit, onDismiss: () -> Unit) = Sheet(fmt.s("eraseT"), fmt, onDismiss) {
    val c = Cyclus.colors
    var text by remember { mutableStateOf("") }
    var deleteHealth by remember { mutableStateOf(true) } // spec §7.9: on by default
    val word = fmt.s("eraseWord")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)) {
        Column(Modifier.padding(14.dp)) { Text(fmt.s("er1"), fontWeight = FontWeight.Medium, color = c.ink); Text(stringResource(R.string.erase_lock_note), fontSize = 13.sp, color = c.ink2) }
        if (healthWritten) {
            HorizontalDivider(color = c.line)
            SwitchRow(stringResource(R.string.hc_erase_line), null, deleteHealth) { deleteHealth = it }
        }
        HorizontalDivider(color = c.line)
        Column(Modifier.padding(14.dp)) { Text(fmt.s("er3"), fontWeight = FontWeight.Medium, color = c.ink); Text(fmt.s("er3s"), fontSize = 13.sp, color = c.ink2) }
    }
    Text(fmt.s("typeToConfirm", word), fontSize = 13.sp, color = c.ink2)
    OutlinedTextField(
        text, { text = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(word) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.line, focusedBorderColor = c.accent, unfocusedContainerColor = c.surface, focusedContainerColor = c.surface),
    )
    // accents optional ("USUN" for "USUŃ", "IZBRISI" for "IZBRIŠI"): not every keyboard has them at hand
    val plain = { t: String -> java.text.Normalizer.normalize(t.trim().uppercase(), java.text.Normalizer.Form.NFD).replace(MARKS, "") }
    val ok = plain(text) == plain(word)
    PrimaryButton(fmt.s("eraseT"), { if (ok) onErase(healthWritten && deleteHealth) }, Modifier.fillMaxWidth(), enabled = ok)
}

/** Set a 6-digit Cyclus PIN (enter twice). */
@Composable
fun PinSetupSheet(fmt: Fmt, onSet: (String) -> Unit, onDismiss: () -> Unit) = Sheet(stringResource(R.string.pin_setup_title), fmt, onDismiss) {
    val c = Cyclus.colors
    var first by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    Text(stringResource(if (first == null) R.string.pin_setup_enter else R.string.pin_setup_repeat), fontWeight = FontWeight.SemiBold, color = c.ink)
    if (mismatch) Text(stringResource(R.string.pin_setup_mismatch), color = c.danger, fontSize = 13.sp)
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PinPad(lockoutUntil = 0L, onPin = { pin ->
        val f = first
        when {
            f == null -> { first = pin; mismatch = false; true }
            f == pin -> { onSet(pin); true }
            else -> { first = null; mismatch = true; true }
        }
    }) }
    Text(stringResource(R.string.pin_setup_note), fontSize = 12.sp, color = c.ink2)
}

private val MARKS = Regex("\\p{Mn}+")
