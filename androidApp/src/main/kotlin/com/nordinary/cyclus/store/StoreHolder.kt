package com.nordinary.cyclus.store

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface StoreState {
    data object Opening : StoreState
    data class Ready(val repo: CyclusRepository) : StoreState
    /** Blocking error. Data stays untouched on disk (trust rule 5). */
    data class Failed(val reason: DbKeyException.Reason?, val detail: String) : StoreState
}

/** Opens the encrypted store once per process, off the main thread. */
class StoreHolder(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<StoreState>(StoreState.Opening)
    val state: StateFlow<StoreState> = _state
    @Volatile private var started = false

    /** Opens if not open yet; retries after a failure (e.g. the device was locked). */
    fun ensureOpen() {
        val s = _state.value
        if (s is StoreState.Ready) return
        if (s is StoreState.Opening && started) return
        if (s is StoreState.Failed && s.reason != DbKeyException.Reason.DEVICE_LOCKED && s.reason != null) return
        started = true
        _state.value = StoreState.Opening
        scope.launch {
            _state.value = try {
                StoreState.Ready(AndroidDatabase.open(context))
            } catch (e: DbKeyException) {
                StoreState.Failed(e.reason, e.cause?.toString() ?: e.reason.name)
            } catch (e: Throwable) {
                StoreState.Failed(null, e.toString())
            }
        }
    }
}
