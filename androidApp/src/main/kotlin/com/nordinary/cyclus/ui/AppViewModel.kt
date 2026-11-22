package com.nordinary.cyclus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nordinary.cyclus.core.CycleOverride
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.core.Episode
import com.nordinary.cyclus.core.EpisodeKind
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.core.OverrideKind
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.engine.Mode
import com.nordinary.cyclus.presenter.InsightsPresenter
import com.nordinary.cyclus.presenter.InsightsUiState
import com.nordinary.cyclus.presenter.LifeStage
import com.nordinary.cyclus.presenter.TodayPresenter
import com.nordinary.cyclus.presenter.WhyModel
import com.nordinary.cyclus.presenter.WhyPresenter
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Everything the screens render for one data state, computed off the main thread. */
data class AppModel(
    val snapshot: CycleSnapshot,
    val today: TodayUiState,
    val insights: InsightsUiState,
    val why: WhyModel?,
    val mode: Mode,
    val showFertility: Boolean,
    val muted: Set<String>,
    val activeEpisode: Episode?,
) {
    val logs: Map<Day, DayLog> get() = snapshot.data.logs
}

/** One-shot UI message (snackbar), optionally undoable. */
data class UiMessage(val key: String, val undo: (suspend () -> Unit)? = null)

@OptIn(ExperimentalUuidApi::class)
class AppViewModel(private val repo: CyclusRepository) : ViewModel() {

    /** Local calendar day; re-checked so the app rolls over at midnight. */
    private val todayFlow = flow {
        while (true) { emit(LocalDate.now().toEpochDay().toInt()); delay(30_000) }
    }.distinctUntilChanged()

    val model: StateFlow<AppModel?> = combine(
        repo.observeLogs(), repo.observeOverrides(), repo.observeEpisodes(), repo.observeSettings(), todayFlow,
    ) { logs, overrides, episodes, settings, today ->
        val mode = if (settings[KEY_MODE] == "conceive") Mode.CONCEIVE else Mode.TRACK
        val showFert = settings[KEY_SHOW_FERT] == "1"
        val muted = settings[KEY_MUTED]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        val episode = episodes.lastOrNull { it.isActiveOn(today) }
        val snap = CycleSnapshot.compute(CycleData(logs, overrides, today), mode, showFertilityInTrack = showFert, paused = episode != null)
        AppModel(
            snapshot = snap,
            today = TodayPresenter.present(snap, episode?.kind, muted),
            insights = InsightsPresenter.present(snap, muted),
            why = WhyPresenter.present(snap),
            mode = mode, showFertility = showFert, muted = muted, activeEpisode = episode,
        )
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

    /** Calendar / day sheet: mark [day] as a period start (bleeding if none logged + START override). Undoable. */
    fun markStart(day: Day) {
        val m = model.value ?: return
        if (day > m.snapshot.today) return
        viewModelScope.launch {
            val before = repo.log(day)
            val overrideBefore = m.snapshot.data.overrides[day]
            if (before?.isBleed != true) repo.saveLog(day, (before ?: DayLog.EMPTY).copy(flow = Flow.MEDIUM))
            repo.setOverride(day, CycleOverride(OverrideKind.START))
            _messages.emit(UiMessage("tMarked") {
                repo.saveLog(day, before ?: DayLog.EMPTY)
                repo.setOverride(day, overrideBefore)
            })
        }
    }

    /** One-tap LH result on Today (conceive). Tapping the same result again clears it. Undoable. */
    fun todayLh(result: LhResult) {
        val today = model.value?.snapshot?.today ?: return
        viewModelScope.launch {
            val before = repo.log(today)
            val cur = before ?: DayLog.EMPTY
            repo.saveLog(today, cur.copy(lh = if (cur.lh == result) null else result))
            _messages.emit(UiMessage(if (result == LhResult.POSITIVE) "tLhPos" else "tLh") { repo.saveLog(today, before ?: DayLog.EMPTY) })
        }
    }

    fun setMode(mode: Mode) { viewModelScope.launch { repo.setSetting(KEY_MODE, if (mode == Mode.CONCEIVE) "conceive" else null) } }

    fun setShowFertility(on: Boolean) { viewModelScope.launch { repo.setSetting(KEY_SHOW_FERT, if (on) "1" else null) } }

    fun mute(patternId: String) {
        val m = model.value ?: return
        viewModelScope.launch { repo.setSetting(KEY_MUTED, (m.muted + patternId).sorted().joinToString(",")) }
    }

    fun unmuteAll() { viewModelScope.launch { repo.setSetting(KEY_MUTED, null) } }

    /**
     * Life stage (pauses predictions). Re-selecting the active stage does nothing, so the real start date is kept.
     * A stage that started today is replaced instead of being ended. Undoable.
     */
    fun setLifeStage(stage: LifeStage?) {
        val m = model.value ?: return
        val today = m.snapshot.today
        val active = m.activeEpisode
        if (LifeStage.of(active?.kind) == stage && (stage != null || active == null)) return
        viewModelScope.launch {
            if (active != null) {
                if (active.start >= today) repo.deleteEpisode(active.id) else repo.saveEpisode(active.copy(end = today - 1))
            }
            val created = stage?.let { Episode(Uuid.random().toString(), it.episode, today) }
            created?.let { repo.saveEpisode(it) }
            _messages.emit(UiMessage("tSaved") {
                created?.let { repo.deleteEpisode(it.id) }
                active?.let { repo.saveEpisode(it) }
            })
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
        const val KEY_MUTED = "mutedPatterns"
    }
}
