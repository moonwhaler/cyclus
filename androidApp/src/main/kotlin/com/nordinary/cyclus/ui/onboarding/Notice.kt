package com.nordinary.cyclus.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.presenter.Consent
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.today.PrimaryButton

/** The four points of the „Bevor du startest“ notice (spec §7.1); the same text everywhere it is shown. */
@Composable
fun NoticeContent(fmt: Fmt) {
    val c = Cyclus.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (i in 1..4) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(if (i == 1) c.danger else c.warning))
            Column {
                Text(fmt.s("nt$i"), fontWeight = FontWeight.Bold, color = c.ink)
                Text(fmt.s("nt${i}s"), fontSize = 14.sp, color = c.ink2)
            }
        }
    }
}

/** Checkbox + button: nothing is agreed until the box is ticked. */
@Composable
fun NoticeConfirm(fmt: Fmt, button: String, initiallyRead: Boolean = false, onAgree: () -> Unit) {
    val c = Cyclus.colors
    var read by rememberSaveable { mutableStateOf(initiallyRead) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
            .toggleable(read, role = Role.Checkbox, onValueChange = { read = it }).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Checkbox(read, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent, uncheckedColor = c.ink2))
        Text(fmt.s("noticeChk"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = c.ink)
    }
    PrimaryButton(button, onAgree, Modifier.fillMaxWidth(), enabled = read)
}

/** Full screen when the notice must be confirmed again (a year later, new wording, a restored phone). */
@Composable
fun ConsentScreen(fmt: Fmt, onAgree: () -> Unit, onPrivacy: () -> Unit) {
    val c = Cyclus.colors
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(fmt.s("noticeRow"), Modifier.semantics { heading() }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = c.ink, lineHeight = 32.sp)
            Text(fmt.s("noticeAgain"), fontSize = 15.sp, color = c.ink2)
            NoticeContent(fmt)
            NoticeConfirm(fmt, fmt.s("agreeCont"), onAgree = onAgree)
            // without agreeing, the person can still take their data with them or erase it (trust rule: never locked out)
            TextButton(onClick = onPrivacy, Modifier.align(Alignment.CenterHorizontally)) {
                Text(fmt.s("aPrivacy"), color = c.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Before the fertile window or Kinderwunsch is switched on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeSheet(fmt: Fmt, onAgree: () -> Unit, onDismiss: () -> Unit) = NoticeSheetFrame(fmt, fmt.s("noticeRow"), onDismiss) {
    Text(fmt.s("noticeFert"), fontSize = 15.sp, color = Cyclus.colors.ink2)
    NoticeContent(fmt)
    NoticeConfirm(fmt, fmt.s("agree"), onAgree = onAgree)
}

/** Privacy → „Wichtiger Hinweis“: the text, every confirmation with its time, and the confirm button when due. */
@Composable
fun ConsentHistorySheet(fmt: Fmt, history: List<Consent.Record>, needed: Boolean, onAgree: () -> Unit, onDismiss: () -> Unit) =
    NoticeSheetFrame(fmt, fmt.s("noticeRow"), onDismiss, dismissLabel = fmt.s("close")) {
        val c = Cyclus.colors
        NoticeContent(fmt)
        if (needed) NoticeConfirm(fmt, fmt.s("agree"), onAgree = onAgree)
        Text(fmt.s("consentHist"), Modifier.padding(top = 6.dp).semantics { heading() }, fontWeight = FontWeight.SemiBold, color = c.ink)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)) {
            if (history.isEmpty()) Text(fmt.s("consentNone"), Modifier.padding(14.dp), color = c.ink2)
            history.asReversed().forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = c.line)
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(fmt.dateTime(r.atMillis), fontWeight = FontWeight.Medium, color = c.ink)
                    Text(fmt.s("consentVer", r.version), fontSize = 13.sp, color = c.ink2)
                }
            }
        }
        Text(fmt.s("consentFoot"), fontSize = 12.sp, color = c.ink2)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeSheetFrame(fmt: Fmt, title: String, onDismiss: () -> Unit, dismissLabel: String = fmt.s("cancel"), content: @Composable () -> Unit) {
    val c = Cyclus.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.bg) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            TextButton(onClick = onDismiss) { Text(dismissLabel, color = c.accent, fontWeight = FontWeight.SemiBold) }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}
