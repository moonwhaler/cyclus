package com.nordinary.cyclus.ui

import com.nordinary.cyclus.CyclusApplication
import com.nordinary.cyclus.store.CyclusRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * What the feature parts of [AppViewModel] share: its scope (so everything is cancelled with the ViewModel), the store,
 * the application, and the one message channel. Every launch reports failures as a message instead of crashing.
 */
class VmContext internal constructor(
    val scope: CoroutineScope,
    val repo: CyclusRepository,
    val app: CyclusApplication,
    private val errors: CoroutineExceptionHandler,
    private val messages: MutableSharedFlow<UiMessage>,
) {
    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(errors, block = block)
    fun launch(dispatcher: CoroutineDispatcher, block: suspend CoroutineScope.() -> Unit): Job = scope.launch(errors + dispatcher, block = block)
    suspend fun emit(message: UiMessage) = messages.emit(message)
    fun msg(res: Int) { launch { messages.emit(UiMessage("", res = res)) } }
}
