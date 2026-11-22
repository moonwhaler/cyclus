package com.nordinary.cyclus.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nordinary.cyclus.BuildConfig
import com.nordinary.cyclus.R
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.presenter.NotifyKind
import com.nordinary.cyclus.presenter.NotifyPrefs
import com.nordinary.cyclus.ui.common.CyclusIcons
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.today.PrimaryButton

/** Einstellungen › Mitteilungen (spec §8.3, ANDROID_PLAN §6.3). Nothing is planned before [NotifyPrefs.consent]. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationsScreen(
    fmt: Fmt,
    prefs: NotifyPrefs,
    conceive: Boolean,
    systemAllows: Boolean,
    debug: Boolean,
    onBack: () -> Unit,
    onConsent: (Boolean) -> Unit,
    onKind: (NotifyKind, Boolean) -> Unit,
    onDailyTime: (Int) -> Unit,
    onOpenSystem: () -> Unit,
    onTest: () -> Unit,
) {
    val c = Cyclus.colors
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var timeDialog by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = c.bg,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(fmt.s("notifT"), fontWeight = FontWeight.Bold) },
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
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!prefs.consent) {
                    Note(stringResource(R.string.nf_intro))
                    PrimaryButton(stringResource(R.string.nf_allow), { onConsent(true) }, Modifier.fillMaxWidth())
                    return@Column
                }
                Group { SwitchRow(stringResource(R.string.nf_master), stringResource(R.string.nf_master_s), true) { onConsent(false) } }
                if (!systemAllows) {
                    Note(stringResource(R.string.nf_blocked), color = c.warning)
                    Group { Link(stringResource(R.string.nf_open_system), null, onOpenSystem) }
                }
                fun on(k: NotifyKind) = k in prefs.enabled
                Section(stringResource(R.string.nf_sec_cycle)) {
                    Group {
                        SwitchRow(stringResource(R.string.nf_period_soon), stringResource(R.string.nf_period_soon_s), on(NotifyKind.PERIOD_SOON)) { onKind(NotifyKind.PERIOD_SOON, it) }
                        Divider()
                        SwitchRow(stringResource(R.string.nf_later), stringResource(R.string.nf_later_s), on(NotifyKind.LATER)) { onKind(NotifyKind.LATER, it) }
                    }
                }
                if (conceive) Section(stringResource(R.string.nf_sec_conceive)) {
                    Group {
                        SwitchRow(stringResource(R.string.nf_window), stringResource(R.string.nf_window_s), on(NotifyKind.WINDOW_OPENS)) { onKind(NotifyKind.WINDOW_OPENS, it) }
                        Divider()
                        SwitchRow(stringResource(R.string.nf_lh), stringResource(R.string.nf_lh_s), on(NotifyKind.LH_TEST)) { onKind(NotifyKind.LH_TEST, it) }
                        Divider()
                        SwitchRow(stringResource(R.string.nf_bbt), stringResource(R.string.nf_bbt_s), on(NotifyKind.BBT_MORNING)) { onKind(NotifyKind.BBT_MORNING, it) }
                    }
                }
                Section(stringResource(R.string.nf_sec_routine)) {
                    Group {
                        val time = "%02d:%02d".format(prefs.dailyMinute / 60, prefs.dailyMinute % 60)
                        SwitchRow(stringResource(R.string.nf_daily), null, on(NotifyKind.DAILY_LOG)) { onKind(NotifyKind.DAILY_LOG, it) }
                        if (on(NotifyKind.DAILY_LOG)) { Divider(); Link(stringResource(R.string.nf_daily_time, time), null, { timeDialog = true }) }
                        Divider()
                        SwitchRow(stringResource(R.string.nf_backup), stringResource(R.string.nf_backup_s), on(NotifyKind.BACKUP)) { onKind(NotifyKind.BACKUP, it) }
                    }
                }
                Group { Link(stringResource(R.string.nf_channels), null, onOpenSystem) }
                Note(stringResource(R.string.nf_visibility_note))
                if (BuildConfig.DEBUG && debug) Group { Link(stringResource(R.string.nf_test), null, onTest) }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    if (timeDialog) {
        val state = rememberTimePickerState(prefs.dailyMinute / 60, prefs.dailyMinute % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timeDialog = false },
            confirmButton = { TextButton({ onDailyTime(state.hour * 60 + state.minute); timeDialog = false }) { Text(stringResource(R.string.nf_ok)) } },
            dismissButton = { TextButton({ timeDialog = false }) { Text(stringResource(R.string.nf_cancel)) } },
            text = { TimePicker(state) },
            containerColor = c.surface,
        )
    }
}
