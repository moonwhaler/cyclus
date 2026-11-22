package com.nordinary.cyclus

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nordinary.cyclus.store.DbKeyException
import com.nordinary.cyclus.store.StoreState
import com.nordinary.cyclus.ui.AppViewModel
import com.nordinary.cyclus.ui.CyclusApp
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.CyclusTheme
import com.nordinary.cyclus.ui.today.PrimaryButton

class MainActivity : ComponentActivity() {
    private val store get() = (application as CyclusApplication).store

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Recents thumbnail hidden by default (ANDROID_PLAN §2.4); FLAG_SECURE fallback before API 33.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        else window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        store.ensureOpen()
        setContent {
            CyclusTheme {
                Surface(color = Cyclus.colors.bg) {
                    val state by store.state.collectAsStateWithLifecycle()
                    when (val s = state) {
                        StoreState.Opening -> {}
                        is StoreState.Failed -> StoreError(s) { store.ensureOpen() }
                        is StoreState.Ready -> {
                            val vm = viewModel(key = "app") { AppViewModel(s.repo) }
                            CyclusApp(vm, debug = BuildConfig.DEBUG)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        store.ensureOpen() // retries after "device locked"
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
