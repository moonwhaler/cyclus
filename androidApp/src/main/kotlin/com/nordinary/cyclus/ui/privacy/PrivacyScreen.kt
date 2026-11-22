package com.nordinary.cyclus.ui.privacy

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.R
import com.nordinary.cyclus.backup.BackupStatus
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.lock.LockConfig
import com.nordinary.cyclus.ui.CyclusSnackbarHost
import com.nordinary.cyclus.ui.common.CyclusIcons
import com.nordinary.cyclus.ui.theme.Cyclus

/** Everything the Privacy Center shows; built by the caller from settings, backup files and the device. */
data class PrivacyUiState(
    val deviceOnly: Boolean,
    val backupStatus: BackupStatus?,
    val deviceSecure: Boolean,
    val exportCount: Int,
    val lastExport: Int?,
    val lock: LockConfig,
    val lockPaused: Boolean,
    val discreet: Boolean = true,
    val widgetDetails: Boolean = false,
    val shortcut: Boolean = false,
)

/** Datenschutz (prototype `renderPrivacy`), honest per platform (spec §7.8, ANDROID_PLAN §2.4, §6.4). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrivacyScreen(
    fmt: Fmt,
    snackbar: SnackbarHostState,
    state: PrivacyUiState,
    onBack: () -> Unit,
    onLockEnabled: (Boolean) -> Unit,
    onPinToggle: (Boolean) -> Unit,
    onPinOnly: (Boolean) -> Unit,
    onLockAfter: (Long) -> Unit,
    onFlagSecure: (Boolean) -> Unit,
    onDeviceOnly: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onErase: () -> Unit,
    onDiscreet: (Boolean) -> Unit = {},
    onWidgetDetails: (Boolean) -> Unit = {},
    onShortcut: (Boolean) -> Unit = {},
) {
    val c = Cyclus.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = c.bg,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(fmt.s("privT"), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(CyclusIcons.Back, fmt.s("back"), tint = c.period) } },
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
            Section(fmt.s("whereData")) {
                Group {
                    Info(fmt.s("onPhone"), fmt.s("onPhoneS"))
                    Divider()
                    Info(fmt.s("inBackups"), backupLine(fmt, state))
                    Divider()
                    Info(
                        fmt.s("exportsT"),
                        if (state.exportCount == 0 || state.lastExport == null) stringResource(R.string.exports_none)
                        else stringResource(R.string.exports_some, state.exportCount, fmt.md(state.lastExport)),
                    )
                }
                Note(fmt.s("noServers"))
            }
            Section(fmt.s("protection")) {
                if (state.lockPaused) Note(stringResource(R.string.lock_paused), color = c.warning)
                Group {
                    SwitchRow(fmt.s("faceLock"), stringResource(R.string.lock_sub), state.lock.enabled, onLockEnabled)
                    Divider()
                    SwitchRow(fmt.s("pinT"), fmt.s("pinS"), state.lock.hasPin, onPinToggle)
                    if (state.lock.hasPin) { Divider(); SwitchRow(fmt.s("pinOnlyT"), fmt.s("pinOnlyS"), state.lock.pinOnly, onPinOnly) }
                    if (state.lock.enabled) {
                        Divider()
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.lock_after), fontWeight = FontWeight.Medium, color = c.ink)
                            val opts = listOf(0L to R.string.lock_after_now, 60_000L to R.string.lock_after_1, 300_000L to R.string.lock_after_5)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                opts.forEachIndexed { i, (ms, label) ->
                                    SegmentedButton(
                                        selected = state.lock.afterMillis == ms, onClick = { onLockAfter(ms) },
                                        shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                                        colors = SegmentedButtonDefaults.colors(activeContainerColor = c.period.copy(alpha = .14f), activeContentColor = c.ink, inactiveContainerColor = c.surface),
                                    ) { Text(stringResource(label)) }
                                }
                            }
                        }
                    }
                    Divider()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        SwitchRow(stringResource(R.string.flag_secure_t), stringResource(R.string.flag_secure_s), state.lock.flagSecure, onFlagSecure)
                        Divider()
                        Info(stringResource(R.string.recents_new), null)
                    } else Info(stringResource(R.string.recents_old), null)
                    if (Build.VERSION.SDK_INT >= 35) { Divider(); Info(stringResource(R.string.private_space_t), stringResource(R.string.private_space_s)) }
                }
            }
            Section(fmt.s("visibility")) {
                Group {
                    SwitchRow(fmt.s("discreetT"), if (state.discreet) fmt.s("discreetOn") else fmt.s("discreetOff"), state.discreet, onDiscreet)
                    Divider()
                    SwitchRow(
                        stringResource(R.string.vis_widget_t),
                        stringResource(if (state.widgetDetails) R.string.vis_widget_on else R.string.vis_widget_off), state.widgetDetails, onWidgetDetails,
                    )
                    Divider()
                    SwitchRow(stringResource(R.string.vis_shortcut_t), stringResource(R.string.vis_shortcut_s), state.shortcut, onShortcut)
                }
                Note(stringResource(R.string.vis_files_note))
            }
            Section(fmt.s("backups")) {
                Group {
                    SwitchRow(fmt.s("devOnlyT"), fmt.s("devOnlyS"), state.deviceOnly, onDeviceOnly)
                    Divider()
                    Link(fmt.s("encBk"), stringResource(R.string.export_s), onExport)
                    Divider()
                    Link(stringResource(R.string.import_t), stringResource(R.string.import_s), onImport)
                }
                if (state.deviceOnly) Note(stringResource(R.string.device_only_note))
            }
            Group { Link(fmt.s("eraseT"), null, onErase, danger = true) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun backupLine(fmt: Fmt, s: PrivacyUiState): String = when {
    s.deviceOnly -> fmt.s("bkDevice")
    !s.deviceSecure -> stringResource(R.string.bk_status_noe2e)
    s.backupStatus?.outcome == "sent" -> stringResource(
        R.string.bk_status_sent, fmt.md(java.time.Instant.ofEpochMilli(s.backupStatus.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay().toInt()),
    )
    s.backupStatus?.outcome == "failed" -> stringResource(R.string.bk_status_failed)
    s.backupStatus?.outcome == "skipped-no-e2e" -> stringResource(R.string.bk_status_noe2e)
    else -> stringResource(R.string.bk_status_unknown)
}

@Composable
internal fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = Cyclus.colors.ink2)
        content()
    }
}

@Composable
internal fun Group(content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cyclus.colors.surface)) { content() }

@Composable internal fun Divider() = HorizontalDivider(color = Cyclus.colors.line)

@Composable
internal fun Note(text: String, color: androidx.compose.ui.graphics.Color = Cyclus.colors.ink2) =
    Text(text, Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = color)

@Composable
internal fun Info(title: String, sub: String?) {
    val c = Cyclus.colors
    Column(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(title, fontWeight = FontWeight.Medium, color = c.ink)
        if (sub != null) Text(sub, fontSize = 13.sp, color = c.ink2)
    }
}

@Composable
internal fun SwitchRow(title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Cyclus.colors
    Row(
        Modifier.fillMaxWidth().toggleable(on, role = Role.Switch, onValueChange = onChange).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = c.ink)
            if (sub != null) Text(sub, fontSize = 13.sp, color = c.ink2)
        }
        Switch(on, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = c.success))
    }
}

@Composable
internal fun Link(title: String, sub: String?, onClick: () -> Unit, danger: Boolean = false) {
    val c = Cyclus.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = if (danger) FontWeight.SemiBold else FontWeight.Medium, color = if (danger) c.period else c.ink)
            if (sub != null) Text(sub, fontSize = 13.sp, color = c.ink2)
        }
        Text("›", color = c.ink2, fontSize = 18.sp)
    }
}
