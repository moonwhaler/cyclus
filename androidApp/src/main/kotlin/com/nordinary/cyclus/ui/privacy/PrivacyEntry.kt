package com.nordinary.cyclus.ui.privacy

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordinary.cyclus.CyclusApplication
import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.lock.Authn
import com.nordinary.cyclus.lock.LockConfig
import com.nordinary.cyclus.ui.AppViewModel
import java.time.LocalDate

private enum class PrivacySheet { NONE, PIN, PIN_THEN_LOCK, EXPORT, IMPORT, ERASE }

/** Privacy Center route: state, file pickers and sheets. */
@Composable
fun PrivacyEntry(vm: AppViewModel, fmt: Fmt, snackbar: SnackbarHostState, onBack: () -> Unit, onErased: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CyclusApplication
    val model by vm.model.collectAsStateWithLifecycle()
    val deviceOnly by vm.deviceOnly.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val importStage by vm.importStage.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(PrivacySheet.NONE) }
    var pendingPass by remember { mutableStateOf<CharArray?>(null) }
    val m = model ?: return
    val lock = LockConfig.from(m.settings)
    val canAuth = Authn.available(ctx)

    // App-initiated pickers don't count as leaving the app (no relock on return). Results after process death are dropped.
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pass = pendingPass
        pendingPass = null
        sheet = PrivacySheet.NONE
        if (uri != null && pass != null) vm.export(uri, pass) else pass?.fill(' ')
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.startImport(uri); sheet = PrivacySheet.IMPORT }
    }

    PrivacyScreen(
        fmt = fmt, snackbar = snackbar,
        state = PrivacyUiState(
            deviceOnly = deviceOnly,
            backupStatus = remember(m, deviceOnly) { vm.backupStatus() },
            deviceSecure = (ctx.getSystemService(KeyguardManager::class.java)).isDeviceSecure,
            exportCount = m.settings[SettingKeys.EXPORT_COUNT]?.toIntOrNull() ?: 0,
            lastExport = m.settings[SettingKeys.EXPORT_LAST]?.toIntOrNull(),
            lock = lock, lockPaused = app.lock.isPaused(canAuth),
        ),
        onBack = onBack,
        onLockEnabled = { on ->
            when {
                !on -> vm.setSetting(SettingKeys.LOCK_ENABLED, null)
                canAuth || lock.hasPin -> vm.setSetting(SettingKeys.LOCK_ENABLED, "1")
                else -> sheet = PrivacySheet.PIN_THEN_LOCK // nothing could open the lock yet: set a PIN first
            }
        },
        onPinToggle = { on -> if (on) sheet = PrivacySheet.PIN else vm.removePin() },
        onPinOnly = { vm.setSetting(SettingKeys.LOCK_PIN_ONLY, if (it) "1" else null) },
        onLockAfter = { vm.setSetting(SettingKeys.LOCK_AFTER, if (it == 0L) null else it.toString()) },
        onFlagSecure = { vm.setSetting(SettingKeys.FLAG_SECURE, if (it) "1" else null) },
        onDeviceOnly = vm::setDeviceOnly,
        onExport = { sheet = PrivacySheet.EXPORT },
        onImport = { app.lock.suspendForExternalActivity(); openDoc.launch(arrayOf("*/*")) },
        onErase = { sheet = PrivacySheet.ERASE },
    )

    when (sheet) {
        PrivacySheet.PIN, PrivacySheet.PIN_THEN_LOCK -> PinSetupSheet(fmt, onSet = { pin ->
            vm.setPin(pin)
            if (sheet == PrivacySheet.PIN_THEN_LOCK) vm.setSetting(SettingKeys.LOCK_ENABLED, "1")
            sheet = PrivacySheet.NONE
        }, onDismiss = { sheet = PrivacySheet.NONE })
        PrivacySheet.EXPORT -> ExportSheet(fmt, exporting, onSave = { pass ->
            pendingPass = pass
            app.lock.suspendForExternalActivity()
            createDoc.launch("Cyclus-${LocalDate.now()}.cyclus")
        }, onDismiss = { pendingPass?.fill(' '); pendingPass = null; sheet = PrivacySheet.NONE })
        PrivacySheet.IMPORT -> ImportSheet(
            fmt, importStage, onOpen = vm::openImport,
            onApply = { replace -> vm.applyImport(replace) { sheet = PrivacySheet.NONE } },
            onDismiss = { vm.cancelImport(); sheet = PrivacySheet.NONE },
        )
        PrivacySheet.ERASE -> EraseSheet(fmt, onErase = { sheet = PrivacySheet.NONE; onErased(); vm.eraseEverything() }, onDismiss = { sheet = PrivacySheet.NONE })
        PrivacySheet.NONE -> {}
    }
}

