package com.nordinary.cyclus.lock

import android.os.SystemClock
import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.security.Pin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LockConfig(
    val enabled: Boolean,
    val pinSalt: String?,
    val pinHash: String?,
    val pinOnly: Boolean,
    /** 0 = immediately. */
    val afterMillis: Long,
    val flagSecure: Boolean,
    val failures: Int,
) {
    val hasPin get() = pinSalt != null && pinHash != null

    companion object {
        fun from(s: Map<String, String>) = LockConfig(
            enabled = s[SettingKeys.LOCK_ENABLED] == "1",
            pinSalt = s[SettingKeys.LOCK_PIN_SALT], pinHash = s[SettingKeys.LOCK_PIN_HASH],
            pinOnly = s[SettingKeys.LOCK_PIN_ONLY] == "1",
            afterMillis = s[SettingKeys.LOCK_AFTER]?.toLongOrNull() ?: 0L,
            flagSecure = s[SettingKeys.FLAG_SECURE] == "1",
            failures = s[SettingKeys.LOCK_BACKOFF]?.toIntOrNull() ?: 0,
        )
    }
}

/**
 * App-lock state (spec §7.12). Locks on cold start and when the app was in the background longer than the chosen
 * delay. App-initiated system screens (credential prompt, file pickers, share sheet) don't count as leaving the app.
 * A lock that can't be opened (no PIN, no screen lock/biometrics) is paused instead of locking the person out.
 */
class AppLock {
    /** null until the configuration is known (nothing is shown meanwhile). */
    private val _locked = MutableStateFlow<Boolean?>(null)
    val locked: StateFlow<Boolean?> = _locked
    private val _config = MutableStateFlow<LockConfig?>(null)
    val config: StateFlow<LockConfig?> = _config

    @Volatile private var suspendedForExternal = false
    @Volatile private var stoppedAt: Long? = null
    /** elapsedRealtime until which PIN entry is blocked. */
    @Volatile var lockoutUntil: Long = 0L
        private set

    fun onConfig(c: LockConfig, canAuthenticate: Boolean) {
        val first = _config.value == null
        _config.value = c
        val usable = c.enabled && (c.hasPin || (canAuthenticate && !c.pinOnly))
        if (first) {
            _locked.value = usable
            if (c.failures >= 5) lockoutUntil = SystemClock.elapsedRealtime() + Pin.backoffMillis(c.failures)
        } else if (!usable) {
            _locked.value = false
        }
    }

    /** Lock enabled but nothing can open it: paused with a visible notice. */
    fun isPaused(canAuthenticate: Boolean): Boolean {
        val c = _config.value ?: return false
        return c.enabled && !c.hasPin && (!canAuthenticate || c.pinOnly)
    }

    fun suspendForExternalActivity() { suspendedForExternal = true }

    fun onAppStop() { if (!suspendedForExternal) stoppedAt = SystemClock.elapsedRealtime() }

    fun onAppStart(canAuthenticate: Boolean) {
        if (suspendedForExternal) { suspendedForExternal = false; return }
        val c = _config.value ?: return
        val since = stoppedAt ?: return
        stoppedAt = null
        val usable = c.enabled && (c.hasPin || (canAuthenticate && !c.pinOnly))
        if (usable && SystemClock.elapsedRealtime() - since >= c.afterMillis) _locked.value = true
    }

    fun unlock() { _locked.value = false; lockoutUntil = 0 }

    /** Records a wrong PIN; returns the new failure count. */
    fun registerFailure(failures: Int): Int {
        val n = failures + 1
        val wait = Pin.backoffMillis(n)
        if (wait > 0) lockoutUntil = SystemClock.elapsedRealtime() + wait
        return n
    }

    fun forget() { _locked.value = null; _config.value = null; stoppedAt = null; suspendedForExternal = false }
}
