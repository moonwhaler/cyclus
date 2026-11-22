package com.nordinary.cyclus.store

import android.content.Context
import com.nordinary.cyclus.backup.BackupCoordinator
import com.nordinary.cyclus.backup.CyclusFile
import com.nordinary.cyclus.backup.ExportFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

    fun retryRestore() { _state.value = StoreState.Opening; scope.launch { _state.value = open() } }

    /** Gives up on the restored copy (a bad or incomplete restore can never succeed) and starts with an empty store. */
    fun discardRestore() {
        _state.value = StoreState.Opening
        scope.launch {
            backup.exclusive { backup.files.restoreDir.deleteRecursively() }
            _state.value = open()
        }
    }

    private suspend fun open(): StoreState = try {
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
     * Erase everything (crypto-shred): database files + their key, snapshot + backup key, settings. Keeps only the
     * „Nur auf dem Gerät“ choice. Then opens a fresh, empty store.
     */
    fun erase(deleteHealth: Boolean = false, revokeHealth: Boolean = true, onDone: () -> Unit = {}) {
        val current = (_state.value as? StoreState.Ready)?.repo
        _state.value = StoreState.Erasing
        scope.launch {
            kotlinx.coroutines.delay(300) // let collectors of the old repository stop (UI left the screens)
            backup.exclusive {
                // spec §7.9: what Cyclus wrote to Health Connect (only our own records), then no connection is left.
                // A Health Connect failure never blocks the crypto-shred.
                runCatching {
                    val hc = com.nordinary.cyclus.health.HealthSync(context)
                    if (hc.availability() == com.nordinary.cyclus.health.HealthAvailability.AVAILABLE) {
                        if (deleteHealth) runCatching { hc.deleteOwn(com.nordinary.cyclus.health.HealthType.entries) }
                        if (revokeHealth) hc.revokeAll()
                    }
                }
                runCatching { current?.close() }
                AndroidDatabase.destroy(context, dbName)
                backup.eraseFilesLocked()
                java.io.File(context.cacheDir, "share").deleteRecursively() // report PDFs
                com.nordinary.cyclus.surfaces.SystemSurfaces.clearLocked(context) // reminders, widget days, shortcut
            }
            runCatching { com.nordinary.cyclus.widget.CyclusWidgets.refresh(context) } // back to the neutral state
            _state.value = open()
            onDone()
        }
    }
}
