package com.nordinary.cyclus.store

import android.content.Context
import com.nordinary.cyclus.backup.BackupCoordinator
import com.nordinary.cyclus.backup.CyclusFile
import com.nordinary.cyclus.backup.ExportFile
import com.nordinary.cyclus.health.HealthAvailability
import com.nordinary.cyclus.health.HealthSync
import com.nordinary.cyclus.health.HealthType
import com.nordinary.cyclus.surfaces.SystemSurfaces
import com.nordinary.cyclus.widget.CyclusWidgets
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface StoreState {
    data object Opening : StoreState
    data class Ready(val repo: CyclusRepository, val restored: Boolean = false) : StoreState
    /** Crypto-shred in progress (no store is composed). */
    data object Erasing : StoreState
    /** Blocking error. Data stays untouched on disk (trust rule 5). */
    data class Failed(val reason: DbKeyException.Reason?, val detail: String) : StoreState
    /** A system restore could not be imported. Retry, or start fresh (discards only the restored copy). */
    data class RestoreFailed(val detail: String) : StoreState
}

/** Opens the encrypted store once per process, off the main thread; imports a pending system restore first. */
class StoreHolder(private val context: Context, val backup: BackupCoordinator, private val dbName: String = AndroidDatabase.FILE_NAME) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<StoreState>(StoreState.Opening)
    val state: StateFlow<StoreState> = _state
    @Volatile private var started = false
    /** Open leases on the current repository (its ViewModel); erase waits until they are released. */
    private val users = MutableStateFlow(0)

    /** Taken by whoever queries the open repository; the returned function releases it (idempotent). */
    fun lease(): () -> Unit {
        users.update { it + 1 }
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        return { if (done.compareAndSet(false, true)) users.update { it - 1 } }
    }

    /** Opens if not open yet; retries after a failure (e.g. the device was locked). */
    fun ensureOpen() {
        val s = _state.value
        if (s is StoreState.Ready || s is StoreState.Erasing || s is StoreState.RestoreFailed) return
        if (s is StoreState.Opening && started) return
        if (s is StoreState.Failed && s.reason != DbKeyException.Reason.DEVICE_LOCKED && s.reason != null) return
        started = true
        _state.value = StoreState.Opening
        scope.launch { _state.value = open() }
    }

    /** Phone unlocked / app back in front: a store that failed only because the phone was locked opens now. */
    fun retryIfLocked() {
        val s = _state.value
        if (s is StoreState.Failed && s.reason == DbKeyException.Reason.DEVICE_LOCKED) ensureOpen()
    }

    fun retryRestore() { _state.value = StoreState.Opening; scope.launch { _state.value = open() } }

    /** Gives up on the restored copy (a bad or incomplete restore can never succeed) and starts with an empty store. */
    fun discardRestore() {
        _state.value = StoreState.Opening
        scope.launch {
            backup.exclusive { backup.files.restoreDir.deleteRecursively() }
            _state.value = open()
        }
    }

    /**
     * Right after a fingerprint/face unlock the activity can start a moment before the Keystore knows the phone is
     * unlocked. While the keyguard already reports unlocked, a „locked“ failure is retried briefly instead of shown.
     */
    private suspend fun open(): StoreState {
        val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
        var result = openOnce()
        var tries = 0
        while (result is StoreState.Failed && result.reason == DbKeyException.Reason.DEVICE_LOCKED && keyguard?.isDeviceLocked == false && tries < 10) {
            kotlinx.coroutines.delay(300)
            result = openOnce()
            tries++
        }
        return result
    }

    private suspend fun openOnce(): StoreState = try {
        val f = backup.files
        if (f.restorePending.exists() && !AndroidDatabase.exists(context, dbName)) restore()
        else {
            if (f.restorePending.exists()) backup.exclusive { f.restoreDir.deleteRecursively() } // never touch a live install
            StoreState.Ready(AndroidDatabase.open(context, dbName))
        }
    } catch (e: DbKeyException) {
        StoreState.Failed(e.reason, e.cause?.toString() ?: e.reason.name)
    } catch (e: Throwable) {
        StoreState.Failed(null, e.toString())
    }

    /** Imports the restored snapshot into a fresh database atomically; on failure deletes only that fresh database. */
    private suspend fun restore(): StoreState = backup.exclusive {
        val f = backup.files
        var repo: CyclusRepository? = null
        try {
            val key = backup.keys.unwrap(f.restoreKey.readBytes())
            val model = try { ExportFile.decode(CyclusFile.openWithKey(f.restoreSnapshot.readBytes(), key)) } finally { key.fill(0) }
            repo = AndroidDatabase.open(context, dbName)
            repo.importReplace(model)
            // promote: the restored key becomes this device's backup key; next snapshot is written with it
            f.restoreKey.copyTo(f.keyWrapped, overwrite = true)
            f.restoreDir.deleteRecursively()
            f.snapshotAbsent.delete()
            StoreState.Ready(repo, restored = true)
        } catch (e: Throwable) {
            runCatching { repo?.close() }
            if (repo != null) AndroidDatabase.destroy(context, dbName) // it is the fresh, just-created DB
            StoreState.RestoreFailed(e.javaClass.simpleName)
        }
    }

    /**
     * Erase everything (crypto-shred): database files + their key, snapshot + backup key, settings, reminder/widget
     * files + their key, and the appearance (back to Cyclus Rose, following the system). Keeps only the „Nur auf dem
     * Gerät“ choice. Then opens a fresh, empty store.
     */
    fun erase(deleteHealth: Boolean = false, revokeHealth: Boolean = true, onDone: () -> Unit = {}) {
        val current = (_state.value as? StoreState.Ready)?.repo
        _state.value = StoreState.Erasing
        scope.launch {
            // the old repository's users stop on the state change above; wait for them (bounded) before closing it
            withTimeoutOrNull(3_000) { users.first { it == 0 } }
            backup.exclusive {
                // spec §7.9: what Cyclus wrote to Health Connect (only our own records), then no connection is left.
                // A Health Connect failure never blocks the crypto-shred.
                runCatching {
                    val hc = HealthSync(context)
                    if (hc.availability() == HealthAvailability.AVAILABLE) {
                        if (deleteHealth) runCatching { hc.deleteOwn(HealthType.entries) }
                        if (revokeHealth) hc.revokeAll()
                    }
                }
                runCatching { current?.close() }
                AndroidDatabase.destroy(context, dbName)
                backup.eraseFilesLocked()
                File(context.cacheDir, "share").deleteRecursively() // report PDFs
                SystemSurfaces.clearLocked(context) // reminders, widget days, shortcut
                com.nordinary.cyclus.ui.theme.AppearanceStore.reset(context)
            }
            runCatching { CyclusWidgets.refresh(context) } // back to the neutral state
            _state.value = open()
            onDone()
        }
    }
}
