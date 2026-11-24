package com.nordinary.cyclus.ui.settings

import com.nordinary.cyclus.ui.theme.BarTitleStyle
import android.app.LocaleManager
import com.nordinary.cyclus.ui.common.LanguageOption
import com.nordinary.cyclus.ui.common.LanguagePicker
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.engine.Mode
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.presenter.LifeStage
import com.nordinary.cyclus.ui.CyclusSnackbarHost
import com.nordinary.cyclus.ui.common.CyclusIcons
import com.nordinary.cyclus.ui.theme.Cyclus

/** Einstellungen (prototype `renderSettings`), a pushed screen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    fmt: Fmt,
    snackbar: SnackbarHostState,
    mode: Mode,
    showFertility: Boolean,
    lifeStage: LifeStage?,
    onBack: () -> Unit,
    onMode: (Mode) -> Unit,
    onShowFertility: (Boolean) -> Unit,
    onLifeStage: (LifeStage?) -> Unit,
    onComingSoon: () -> Unit,
    onPrivacy: () -> Unit = onComingSoon,
    onNotifications: () -> Unit = onComingSoon,
    onAbout: () -> Unit = onComingSoon,
    appearance: com.nordinary.cyclus.presenter.Appearance = com.nordinary.cyclus.presenter.Appearance.DEFAULT,
    onAppearance: (com.nordinary.cyclus.presenter.Appearance) -> Unit = {},
) {
    val c = Cyclus.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = c.bg,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(fmt.s("setT"), style = BarTitleStyle) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(CyclusIcons.Back, fmt.s("back"), tint = c.accent) } },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, scrolledContainerColor = c.bg),
            )
        },
        snackbarHost = { CyclusSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppearanceSection(fmt, appearance, onAppearance)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LanguageSection(fmt)
            Section(fmt.s("goal")) {
                Segments(listOf(fmt.s("goalTrack"), fmt.s("goalConceive")), if (mode == Mode.CONCEIVE) 1 else 0) {
                    onMode(if (it == 1) Mode.CONCEIVE else Mode.TRACK)
                }
                if (mode == Mode.TRACK) Group {
                    Row(
                        Modifier.fillMaxWidth().toggleable(showFertility, role = Role.Switch, onValueChange = onShowFertility)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(fmt.s("showFertT"), fontWeight = FontWeight.Medium, color = c.ink)
                            Text(fmt.s("showFertS"), fontSize = 13.sp, color = c.ink2)
                        }
                        Switch(showFertility, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = c.success))
                    }
                }
            }
            Section(fmt.s("affecting")) {
                Group {
                    val options = listOf<LifeStage?>(null) + LifeStage.entries
                    options.forEachIndexed { i, st ->
                        if (i > 0) HorizontalDivider(color = c.line)
                        val selected = lifeStage == st
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 50.dp).selectable(selected, role = Role.RadioButton) { onLifeStage(st) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(fmt.s(st?.labelKey ?: LifeStage.NONE_LABEL), Modifier.weight(1f), fontWeight = FontWeight.Medium, color = c.ink)
                            if (selected) Text("✓", color = c.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(fmt.s("epNote"), Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = c.ink2)
            }
            Section(fmt.s("more")) {
                Group {
                    LinkItem(fmt.s("notifT"), fmt.s("notifS"), onNotifications)
                    HorizontalDivider(color = c.line)
                    LinkItem(fmt.s("privT"), null, onPrivacy)
                    HorizontalDivider(color = c.line)
                    LinkItem(fmt.s("aboutT"), fmt.s("aboutS"), onAbout)
                }
            }
            Text(fmt.s("purpose"), Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = c.ink2)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** In-app language (Android 13+ per-app locale; the system shows the same choice under Apps › Cyclus). */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageSection(fmt: Fmt) {
    val ctx = LocalContext.current
    val lm = ctx.getSystemService(LocaleManager::class.java)
    // empty = follows the phone; otherwise matched by language only (the system list stores "pt", the picker "pt-PT")
    val chosen = lm.applicationLocales.takeIf { !it.isEmpty }?.get(0)
    val options = listOf(LanguageOption(null, fmt.s("langSys"), fmt.s("langSysS"))) +
        com.nordinary.cyclus.i18n.AppLanguages.ALL.map { LanguageOption(it.tag, it.endonym) }
    Section(fmt.s("langT")) {
        LanguagePicker(fmt, fmt.s("langT"), options, com.nordinary.cyclus.i18n.AppLanguages.of(chosen)?.tag) { tag ->
            lm.applicationLocales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
    }
}

@Composable
internal fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = Cyclus.colors.ink2)
        content()
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cyclus.colors.surface)) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Segments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Cyclus.colors
    // a label that wraps (longer languages) makes every segment as tall as the tallest, text centred
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        labels.forEachIndexed { i, l ->
            SegmentedButton(
                selected = selected == i, onClick = { onSelect(i) }, shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                modifier = Modifier.fillMaxHeight(),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = c.accent.copy(alpha = .14f), activeContentColor = c.ink, inactiveContainerColor = c.surface),
            ) { Text(l, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2) }
        }
    }
}

@Composable
private fun LinkItem(title: String, sub: String?, onClick: () -> Unit) {
    val c = Cyclus.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = c.ink)
            if (sub != null) Text(sub, fontSize = 13.sp, color = c.ink2)
        }
        Text("›", color = c.ink2, fontSize = 18.sp)
    }
}
