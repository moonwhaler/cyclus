package com.nordinary.cyclus.lock

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordinary.cyclus.R
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.ui.common.CyclusIcons
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.Nunito
import com.nordinary.cyclus.ui.today.PrimaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Full-screen lock (prototype `renderLock`). Nothing from the store is composed behind it. */
@Composable
fun LockScreen(
    fmt: Fmt,
    config: LockConfig,
    biometricAvailable: Boolean,
    lockoutUntil: Long,
    onBiometric: () -> Unit,
    onPin: suspend (String) -> Boolean,
) {
    val c = Cyclus.colors
    val biometric = biometricAvailable && !config.pinOnly
    LaunchedEffect(Unit) { if (biometric && !config.hasPin) onBiometric() }
    Column(
        Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Icon(CyclusIcons.Today, null, Modifier.size(56.dp), tint = c.period)
        Text(fmt.s("lockedT"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink)
        Text(fmt.s("lockedB"), color = c.ink2, textAlign = TextAlign.Center)
        if (config.hasPin) PinPad(lockoutUntil, onPin)
        if (biometric) PrimaryButton(fmt.s("unlockBtn"), onBiometric)
    }
}

@Composable
fun PinPad(lockoutUntil: Long, onPin: suspend (String) -> Boolean, modifier: Modifier = Modifier) {
    val c = Cyclus.colors
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    // the check (PBKDF2) runs off the main thread; the pad waits meanwhile
    var checking by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(lockoutUntil) { while (SystemClock.elapsedRealtime() < lockoutUntil) { now = SystemClock.elapsedRealtime(); delay(500) }; now = SystemClock.elapsedRealtime() }
    val waitS = ((lockoutUntil - now) / 1000).coerceAtLeast(0)
    val deleteLabel = stringResource(R.string.pin_delete)
    Column(modifier.widthIn(max = 320.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics { contentDescription = "${pin.length}/6" }) {
            repeat(6) { i -> Box(Modifier.size(14.dp).clip(CircleShape).then(if (i < pin.length) Modifier.background(c.ink) else Modifier.border(2.dp, c.ink2, CircleShape))) }
        }
        Text(
            when { waitS > 0 -> stringResource(R.string.pin_wait, waitS.toInt()); wrong -> stringResource(R.string.pin_wrong); else -> " " },
            color = c.period, fontSize = 13.sp,
        )
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        for (row in keys.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (k in row) {
                Box(
                    Modifier.size(68.dp).clip(CircleShape).then(if (k.isNotEmpty()) Modifier.background(c.surface) else Modifier)
                        .clickable(enabled = k.isNotEmpty() && waitS == 0L && !checking, role = Role.Button) {
                            wrong = false
                            if (k == "⌫") pin = pin.dropLast(1)
                            else if (pin.length < 6) {
                                pin += k
                                if (pin.length == 6) {
                                    val entered = pin
                                    checking = true
                                    scope.launch {
                                        try { if (!onPin(entered)) wrong = true } finally { pin = ""; checking = false }
                                    }
                                }
                            }
                        }
                        .semantics { if (k == "⌫") contentDescription = deleteLabel },
                    contentAlignment = Alignment.Center,
                ) { Text(k, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = c.ink) }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
