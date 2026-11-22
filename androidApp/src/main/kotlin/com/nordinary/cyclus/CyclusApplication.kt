package com.nordinary.cyclus

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nordinary.cyclus.backup.BackupCoordinator
import com.nordinary.cyclus.store.StoreHolder
import com.nordinary.cyclus.store.StoreState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CyclusApplication : Application() {
    val backup: BackupCoordinator by lazy { BackupCoordinator(this) }
    val store: StoreHolder by lazy { StoreHolder(this, backup) }
    val lock = com.nordinary.cyclus.lock.AppLock()
    /** Widget/tile/shortcut action waiting for the unlocked, ready app ([ActionEntry]); consumed once. */
    val pendingAction = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Runs before any backup agent work in a cold process: never leave a plaintext backup key behind.
        backup.startupSweep()
        com.nordinary.cyclus.report.ReportFiles.clear(this) // shared report PDFs never outlive the session
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { lock.onAppStart(com.nordinary.cyclus.lock.Authn.available(this@CyclusApplication)) }

            override fun onStop(owner: LifecycleOwner) {
                lock.onAppStop()
                val repo = (store.state.value as? StoreState.Ready)?.repo ?: return
                scope.launch { runCatching { backup.snapshotIfChanged(repo, appLabel()) } }
            }
        })
    }

    fun appLabel() = "Cyclus Android ${BuildConfig.VERSION_NAME}"
}
