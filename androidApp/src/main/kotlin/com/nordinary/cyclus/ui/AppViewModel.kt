package com.nordinary.cyclus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nordinary.cyclus.core.CycleOverride
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Episode
import com.nordinary.cyclus.core.EpisodeKind
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.OverrideKind
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.engine.Mode
import com.nordinary.cyclus.presenter.TodayPresenter
import com.nordinary.cyclus.presenter.TodayUiState
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import com.nordinary.cyclus.store.CyclusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Everything the screens render for one data state. */
data class AppModel(
    val snapshot: CycleSnapshot,
    val today: TodayUiState,
    val mode: Mode,
) {
    val logs: Map<Day, DayLog> get() = snapshot.data.logs
}

/** One-shot UI message (snackbar), optionally undoable. */
data class UiMessage(val key: String, val undo: (suspend () -> Unit)? = null)

class AppViewModel(private val repo: CyclusRepository) : ViewModel() {

    /** Local calendar day; re-checked so the app rolls over at midnight. */
    private val todayFlow = flow {
        while (true) { emit(LocalDate.now().toEpochDay().toInt()); delay(30_000) }
    }.distinctUntilChanged()

    val model: StateFlow<AppModel?> = combine(
        repo.observeLogs(), repo.observeOverrides(), repo.observeEpisodes(), repo.observeSettings(), todayFlow,
    ) { logs, overrides, episodes, settings, today ->
        val mode = if (settings[KEY_MODE] == "conceive") Mode.CONCEIVE else Mode.TRACK
        val episode = episodes.lastOrNull { it.isActiveOn(today) }
        val snap = CycleSnapshot.compute(
            CycleData(logs, overrides, today), mode,
            showFertilityInTrack = settings[KEY_SHOW_FERT] == "1", paused = episode != null,
        )
        AppModel(snap, TodayPresenter.present(snap, episode?.kind), mode)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    fun updateLog(day: Day, transform: (DayLog) -> DayLog) {
        viewModelScope.launch {
            val current = repo.log(day) ?: DayLog.EMPTY
            repo.saveLog(day, transform(current))
        }
    }

    /** „Periode hat heute begonnen“: medium flow today + start override if detection wouldn't see it. Undoable. */
    fun periodStartedToday() {
        val m = model.value ?: return
        val today = m.snapshot.today
        viewModelScope.launch {
            val before = repo.log(today)
            val overrideBefore = m.snapshot.data.overrides[today]
            val after = (before ?: DayLog.EMPTY).copy(flow = Flow.MEDIUM)
            repo.saveLog(today, after)
            val starts = CycleData(m.logs + (today to after), m.snapshot.data.overrides, today).cycleStarts
            if (today !in starts) repo.setOverride(today, CycleOverride(OverrideKind.START))
            _messages.emit(UiMessage("tPeriod") {
                repo.saveLog(today, before ?: DayLog.EMPTY)
                repo.setOverride(today, overrideBefore)
            })
        }
    }

    /** Quick flow buttons on Today while in period (incl. "Vorbei" = none). Undoable. */
    fun quickFlow(flow: Flow) {
        val today = model.value?.snapshot?.today ?: return
        viewModelScope.launch {
            val before = repo.log(today)
            repo.saveLog(today, (before ?: DayLog.EMPTY).copy(flow = flow))
            _messages.emit(UiMessage(if (flow == Flow.NONE) "tEnded" else "tFlow") { repo.saveLog(today, before ?: DayLog.EMPTY) })
        }
    }

    fun saved() { viewModelScope.launch { _messages.emit(UiMessage("tSaved")) } }

    fun undo(message: UiMessage) { message.undo?.let { u -> viewModelScope.launch { u() } } }

    /** Debug builds only: replace all data with a prototype scenario anchored on today. */
    fun loadDemo(scenario: SampleScenario) {
        val today = LocalDate.now().toEpochDay().toInt()
        viewModelScope.launch {
            val s = SampleScenarios.generate(scenario, today)
            repo.replaceAll(
                logs = s.logs,
                episodes = s.pausedSince?.let { listOf(Episode("demo-hbc", EpisodeKind.HORMONAL_CONTRACEPTION, it)) } ?: emptyList(),
                settings = if (s.mode == Mode.CONCEIVE) mapOf(KEY_MODE to "conceive") else emptyMap(),
            )
        }
    }

    fun eraseAll() { viewModelScope.launch { repo.eraseAll() } }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_SHOW_FERT = "showFertTrack"
    }
}
