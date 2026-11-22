package com.nordinary.cyclus

import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nordinary.cyclus.i18n.TxRes
import com.nordinary.cyclus.i18n.rememberFmt
import com.nordinary.cyclus.lock.Authn
import com.nordinary.cyclus.lock.LockConfig
import com.nordinary.cyclus.lock.LockScreen
import com.nordinary.cyclus.store.DbKeyException
import com.nordinary.cyclus.store.StoreState
import com.nordinary.cyclus.ui.AppViewModel
import com.nordinary.cyclus.ui.CyclusApp
import com.nordinary.cyclus.ui.onboarding.OnboardingScreen
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.CyclusTheme
import com.nordinary.cyclus.ui.today.PrimaryButton
import com.nordinary.cyclus.ui.today.SecondaryButton

class MainActivity : FragmentActivity() {
    private val app get() = application as CyclusApplication

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.nordinary.cyclus.ui.theme.AppearanceStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Recents thumbnail hidden (ANDROID_PLAN §2.4). Before Android 13 only FLAG_SECURE can do that, so it is always on there.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        else window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        app.store.ensureOpen()
        if (savedInstanceState == null) takeAction(intent) // not again after rotation / recreation
        if (com.nordinary.cyclus.ui.theme.AppearanceStore.needsRecreateOnModeChange) {
            val startMode = com.nordinary.cyclus.ui.theme.AppearanceStore.state.value.mode
            lifecycleScope.launch {
                com.nordinary.cyclus.ui.theme.AppearanceStore.state.collect { if (it.mode != startMode) recreate() }
            }
        }
        setContent {
            CyclusTheme {
                Surface(color = Cyclus.colors.bg) {
                    val state by app.store.state.collectAsStateWithLifecycle()
                    when (val s = state) {
                        StoreState.Opening -> {}
                        StoreState.Erasing -> Busy(stringResource(R.string.erasing))
                        is StoreState.RestoreFailed -> RestoreFailed(onRetry = app.store::retryRestore, onFresh = app.store::discardRestore)
                        is StoreState.Failed -> StoreError(s) { app.store.ensureOpen() }
                        is StoreState.Ready -> Ready(s)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeAction(intent)
    }

    /** Moves a widget/tile/shortcut action (only via the non-exported alias) to the app and strips it from the intent. */
    private fun takeAction(i: Intent?) {
        ActionEntry.actionOf(i)?.let { app.pendingAction.value = it }
        i?.removeExtra(ActionEntry.EXTRA)
        if (i != null) setIntent(i)
    }

    override fun onResume() {
        super.onResume()
        app.store.ensureOpen() // retries after "device locked"
    }

    @Composable
    private fun Ready(s: StoreState.Ready) {
        val repo = s.repo
        val vm = viewModel(key = "app-${System.identityHashCode(repo)}") { AppViewModel(repo, app, restored = s.restored) }
        val settings by vm.settings.collectAsStateWithLifecycle(null)
        LaunchedEffect(settings) {
            settings?.let { st ->
                val cfg = LockConfig.from(st)
                app.lock.onConfig(cfg, Authn.available(this@MainActivity))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (cfg.flagSecure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        val locked by app.lock.locked.collectAsStateWithLifecycle()
        val config by app.lock.config.collectAsStateWithLifecycle()
        val fmt = rememberFmt()
        // the app's UI state (tab, pushed screens, sheet, selected day, scroll) survives the lock screen: the content
        // leaves composition while locked (nothing of it is drawn or readable behind the lock), its saved state stays
        val unlockedState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
        when (locked) {
            null -> {}
            true -> config?.let { cfg ->
                LockScreen(
                    fmt, cfg, biometricAvailable = Authn.available(this), lockoutUntil = app.lock.lockoutUntil,
                    onBiometric = ::authenticate, onPin = { vm.privacy.checkPin(it, cfg).also { ok -> if (ok) app.lock.unlock() } },
                )
            }
            false -> unlockedState.SaveableStateProvider("app") {
                // onboarding for a truly empty store; nothing is shown until the model is known (no flash)
                val model by vm.model.collectAsStateWithLifecycle()
                val active by vm.onboardingActive.collectAsStateWithLifecycle()
                val m = model
                when {
                    m == null -> {}
                    m.showOnboarding || active -> {
                        LaunchedEffect(Unit) { vm.onboardingActive.value = true; app.pendingAction.value = null } // nothing to log into yet
                        // tablets / landscape: a readable column, not full width
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            androidx.compose.foundation.layout.Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                                OnboardingScreen(vm, fmt)
                            }
                        }
                    }
                    else -> CyclusApp(vm)
                }
            }
        }
    }

    /** Biometrics or device credential; the system screen doesn't count as leaving the app. */
    fun authenticate() {
        app.lock.suspendForExternalActivity()
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { app.lock.unlock() }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(TxRes.strings.getValue("lockedT")))
            .setAllowedAuthenticators(Authn.authenticators)
            .build()
        prompt.authenticate(info)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Busy(text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = Cyclus.colors.ink, fontWeight = FontWeight.SemiBold)
        LinearWavyProgressIndicator()
    }
}

@Composable
private fun RestoreFailed(onRetry: () -> Unit, onFresh: () -> Unit) {
    val c = Cyclus.colors
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.restore_failed_t), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center)
        Text(stringResource(R.string.restore_failed_b), color = c.ink2, textAlign = TextAlign.Center)
        PrimaryButton(stringResource(R.string.store_retry), onRetry)
        SecondaryButton(stringResource(R.string.restore_fresh), onFresh)
    }
}

/** Blocking error: never offers to reset, because that would delete data (trust rule 5). */
@Composable
private fun StoreError(s: StoreState.Failed, onRetry: () -> Unit) {
    val c = Cyclus.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.store_error_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center)
        val why = when (s.reason) {
            DbKeyException.Reason.DEVICE_LOCKED -> R.string.store_error_locked
            DbKeyException.Reason.KEY_FILE_MISSING, DbKeyException.Reason.KEY_INVALIDATED -> R.string.store_error_key
            else -> R.string.store_error_other
        }
        Text(stringResource(why), color = c.ink2, textAlign = TextAlign.Center)
        Text(stringResource(R.string.store_error_safe), color = c.ink, textAlign = TextAlign.Center)
        if (s.reason == null || s.reason == DbKeyException.Reason.DEVICE_LOCKED) PrimaryButton(stringResource(R.string.store_retry), onRetry)
    }
}
