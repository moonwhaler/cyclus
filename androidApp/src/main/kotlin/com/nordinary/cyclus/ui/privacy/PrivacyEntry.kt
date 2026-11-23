package com.nordinary.cyclus.ui.privacy

import com.nordinary.cyclus.presenter.ExportStats
import com.nordinary.cyclus.presenter.NotifyPrefs
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordinary.cyclus.CyclusApplication
import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.health.HealthAvailability
import com.nordinary.cyclus.health.HealthKeys
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.lock.Authn
import com.nordinary.cyclus.lock.LockConfig
import com.nordinary.cyclus.presenter.NotifyKeys
import com.nordinary.cyclus.presenter.Consent
import com.nordinary.cyclus.ui.onboarding.ConsentHistorySheet
import com.nordinary.cyclus.ui.AppViewModel
import com.nordinary.cyclus.ui.HealthActions
import java.time.LocalDate

private enum class PrivacySheet { NONE, PIN, PIN_THEN_LOCK, EXPORT, IMPORT, ERASE, NOTICE }

/** Privacy Center route: state, file pickers and sheets. */
@Composable
fun PrivacyEntry(vm: AppViewModel, fmt: Fmt, snackbar: SnackbarHostState, onBack: () -> Unit, onErased: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CyclusApplication
    val model by vm.model.collectAsStateWithLifecycle()
    val deviceOnly by vm.privacy.deviceOnly.collectAsStateWithLifecycle()
    val exporting by vm.privacy.exporting.collectAsStateWithLifecycle()
    val importStage by vm.privacy.importStage.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(PrivacySheet.NONE) }
    var pendingPass by remember { mutableStateOf<CharArray?>(null) }
    val m = model ?: return
    val lock = LockConfig.from(m.settings)
    val canAuth = Authn.available(ctx)

    // Health Connect state: reloaded when settings change, when coming back from Health Connect, and after an action
    var healthRefresh by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val res = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumed by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val healthUi by androidx.compose.runtime.produceState<HealthActions.HealthUi?>(null, m.settings, healthRefresh, resumed) {
        value = vm.health.ui(m.settings)
    }

    // App-initiated pickers don't count as leaving the app (no relock on return). Results after process death are dropped.
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pass = pendingPass
        pendingPass = null
        sheet = PrivacySheet.NONE
        if (uri != null && pass != null) vm.privacy.export(uri, pass) else pass?.fill(' ')
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.privacy.startImport(uri); sheet = PrivacySheet.IMPORT }
    }

    val notify = NotifyPrefs.from(m.settings)
    val exports = ExportStats.from(m.settings)
    PrivacyScreen(
        fmt = fmt, snackbar = snackbar,
        state = PrivacyUiState(
            deviceOnly = deviceOnly,
            backupStatus = remember(m, deviceOnly) { vm.privacy.backupStatus() },
            deviceSecure = (ctx.getSystemService(KeyguardManager::class.java)).isDeviceSecure,
            exportCount = exports.count,
            lastExport = exports.lastDay,
            discreet = notify.discreet,
            widgetDetails = notify.widgetDetails,
            shortcut = notify.shortcut,
            lock = lock, lockPaused = app.lock.isPaused(canAuth),
            consent = Consent.current(m.settings),
        ),
        onBack = onBack,
        onLockEnabled = { on ->
            when {
                !on -> vm.setSetting(SettingKeys.LOCK_ENABLED, null)
                canAuth || lock.hasPin -> vm.setSetting(SettingKeys.LOCK_ENABLED, "1")
                else -> sheet = PrivacySheet.PIN_THEN_LOCK // nothing could open the lock yet: set a PIN first
            }
        },
        onPinToggle = { on -> if (on) sheet = PrivacySheet.PIN else vm.privacy.removePin() },
        onPinOnly = { vm.setSetting(SettingKeys.LOCK_PIN_ONLY, if (it) "1" else null) },
        onLockAfter = { vm.setSetting(SettingKeys.LOCK_AFTER, if (it == 0L) null else it.toString()) },
        onFlagSecure = { vm.setSetting(SettingKeys.FLAG_SECURE, if (it) "1" else null) },
        onDeviceOnly = vm.privacy::setDeviceOnly,
        onExport = { sheet = PrivacySheet.EXPORT },
        onImport = { app.lock.suspendForExternalActivity(); openDoc.launch(arrayOf("*/*")) },
        onErase = { sheet = PrivacySheet.ERASE },
        onDiscreet = { vm.setSetting(NotifyKeys.DISCREET, if (it) null else "0") },
        onWidgetDetails = { vm.setSetting(NotifyKeys.WIDGET_DETAILS, if (it) "1" else null) },
        healthContent = { healthUi?.let { HealthSection(vm, fmt, it) { healthRefresh++ } } },
        healthLine = healthUi?.takeIf { it.availability == HealthAvailability.AVAILABLE }?.let { h ->
            h.enabled.takeIf { it.isNotEmpty() }?.joinToString(", ") { res.getString(categoryLabel(it)) } ?: fmt.s("nothingW")
        },
        onShortcut = { vm.setSetting(NotifyKeys.SHORTCUT, if (it) "1" else null) },
        onNotice = { sheet = PrivacySheet.NOTICE },
    )

    when (sheet) {
        PrivacySheet.PIN, PrivacySheet.PIN_THEN_LOCK -> PinSetupSheet(fmt, onSet = { pin ->
            vm.privacy.setPin(pin)
            if (sheet == PrivacySheet.PIN_THEN_LOCK) vm.setSetting(SettingKeys.LOCK_ENABLED, "1")
            sheet = PrivacySheet.NONE
        }, onDismiss = { sheet = PrivacySheet.NONE })
        PrivacySheet.EXPORT -> ExportSheet(fmt, exporting, onSave = { pass ->
            pendingPass = pass
            app.lock.suspendForExternalActivity()
            createDoc.launch("Cyclus-${LocalDate.now()}.cyclus")
        }, onDismiss = { pendingPass?.fill(' '); pendingPass = null; sheet = PrivacySheet.NONE })
        PrivacySheet.IMPORT -> ImportSheet(
            fmt, importStage, onOpen = vm.privacy::openImport,
            onApply = { replace -> vm.privacy.applyImport(replace) { sheet = PrivacySheet.NONE } },
            onDismiss = { vm.privacy.cancelImport(); sheet = PrivacySheet.NONE },
        )
        PrivacySheet.ERASE -> EraseSheet(
            fmt, healthWritten = m.settings[HealthKeys.WROTE] == "1",
            onErase = { deleteHealth -> sheet = PrivacySheet.NONE; onErased(); vm.privacy.eraseEverything(deleteHealth) },
            onDismiss = { sheet = PrivacySheet.NONE },
        )
        PrivacySheet.NOTICE -> ConsentHistorySheet(
            fmt, Consent.history(m.settings), needed = Consent.needed(m.settings, System.currentTimeMillis()),
            onAgree = { vm.recordConsent(); sheet = PrivacySheet.NONE }, onDismiss = { sheet = PrivacySheet.NONE },
        )
        PrivacySheet.NONE -> {}
    }
}

