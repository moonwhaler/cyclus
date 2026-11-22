package com.nordinary.cyclus.ui

import com.nordinary.cyclus.presenter.UserPrefs
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nordinary.cyclus.BuildConfig
import com.nordinary.cyclus.CyclusApplication
import com.nordinary.cyclus.R
import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.backup.preview
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
import com.nordinary.cyclus.health.HealthMerge
import com.nordinary.cyclus.health.HealthSample
import com.nordinary.cyclus.notify.Notifier
import com.nordinary.cyclus.presenter.InsightsPresenter
import com.nordinary.cyclus.presenter.InsightsUiState
import com.nordinary.cyclus.presenter.LifeStage
import com.nordinary.cyclus.presenter.NotifyKeys
import com.nordinary.cyclus.presenter.NotifyKind
import com.nordinary.cyclus.presenter.Onboarding
import com.nordinary.cyclus.presenter.OnboardingChoices
import com.nordinary.cyclus.presenter.TodayPresenter
import com.nordinary.cyclus.presenter.TodayUiState
import com.nordinary.cyclus.presenter.WhyModel
import com.nordinary.cyclus.presenter.WhyPresenter
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import com.nordinary.cyclus.store.CyclusRepository
import com.nordinary.cyclus.surfaces.SystemSurfaces
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import com.nordinary.cyclus.store.StoreState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val settings: Map<String, String>,
    val episodes: List<Episode>,
    /** Truly empty store and onboarding not done (fresh install / after erase). */
    val showOnboarding: Boolean,
    /** The person's own entries: what the Log sheet edits and exports contain (no Health Connect values). */
    val userLogs: Map<Day, DayLog> = snapshot.data.logs,
    /** Records from other apps via Health Connect (day sheet source badges). */
    val healthSamples: List<HealthSample> = emptyList(),
) {
    val logs: Map<Day, DayLog> get() = snapshot.data.logs
}

/** One-shot UI message (snackbar): a prototype text key or an app string resource, optionally undoable. */
data class UiMessage(val key: String, val res: Int? = null, val undo: (suspend () -> Unit)? = null)

@OptIn(ExperimentalUuidApi::class)
class AppViewModel(
    private val repo: CyclusRepository,
    private val app: CyclusApplication,
    restored: Boolean = false,
) : ViewModel() {
    /**
     * Everything that reads this repository runs in [scope]. When the store moves on (erase, failure) the work is
     * cancelled and the lease released, so the store can close the database without queries in flight.
     */
    private val work = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val scope = CoroutineScope(viewModelScope.coroutineContext + work)
    private val release = app.store.lease()

    init {
        viewModelScope.launch {
            app.store.state.first { !(it is StoreState.Ready && it.repo === repo) && it !is StoreState.Opening }
            work.cancel()
            release()
        }
    }

    override fun onCleared() { release() }

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    /**
     * A failed write (storage full, I/O error) shows a message instead of crashing the app; nothing is half-written
     * because every repository write is one transaction.
     */
    private val errors = CoroutineExceptionHandler { _, _ ->
        _messages.tryEmit(UiMessage("", res = R.string.error_save))
    }

    init {
        // shown once the UI listens (after the lock screen), not into the void
        if (restored) scope.launch(errors) {
            _messages.subscriptionCount.first { it > 0 }
            _messages.emit(UiMessage("", res = R.string.restored))
        }
    }

    // ---- the store, read once and shared by the model, the Health Connect trigger and the lock (no duplicate queries)
    private val logs = repo.observeLogs().shareIn(scope, SharingStarted.Eagerly, 1)
    private val overrides = repo.observeOverrides().shareIn(scope, SharingStarted.Eagerly, 1)
    /** All settings (the lock configuration in MainActivity reads these too). */
    val settings: SharedFlow<Map<String, String>> = repo.observeSettings().shareIn(scope, SharingStarted.Eagerly, 1)

    /**
     * Local calendar day. Re-read at the next midnight (a coroutine delay doesn't count deep sleep, so also), when the
     * app comes to the foreground and when the date, time or time zone changes. No polling.
     */
    private val todayFlow = kotlinx.coroutines.flow.merge(
        flow {
            while (true) {
                emit(Unit)
                val now = ZonedDateTime.now()
                delay(java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis() + 1_000)
            }
        },
        kotlinx.coroutines.flow.callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) { trySend(Unit) }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            val owner = ProcessLifecycleOwner.get()
            val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_START) trySend(Unit) }
            owner.lifecycle.addObserver(observer)
            awaitClose { app.unregisterReceiver(receiver); owner.lifecycle.removeObserver(observer) }
        }.flowOn(Dispatchers.Main),
    ).map { LocalDate.now().toEpochDay().toInt() }.distinctUntilChanged()

    /** The expensive part (engine + presenters): recomputed only when data, the engine settings or the day change. */
    private class Computed(
        val snapshot: CycleSnapshot, val today: TodayUiState, val insights: InsightsUiState, val why: WhyModel?,
        val mode: Mode, val showFertility: Boolean, val muted: Set<String>, val episode: Episode?,
        val logs: Map<Day, DayLog>, val samples: List<HealthSample>,
        val overrides: Map<Day, CycleOverride>, val episodes: List<Episode>,
    )

    private val computed = combine(
        combine(logs, repo.observeHealthSamples()) { l, h -> l to h },
        overrides, repo.observeEpisodes(),
        // bookkeeping (Health Connect tokens, last sync, PIN back-off, export counters …) never recomputes the engine
        settings.map { st -> st.filterKeys { it in ENGINE_KEYS } }.distinctUntilChanged(),
        todayFlow,
    ) { (logs, samples), overrides, episodes, engine, today ->
        val prefs = UserPrefs.from(engine)
        val mode = prefs.mode
        val showFert = prefs.showFertility
        val muted = prefs.muted
        val episode = episodes.lastOrNull { it.isActiveOn(today) }
        // Health Connect values fill fields the person left empty (spec §11.4 rule 4); the engine is unchanged
        val effective = HealthMerge.effectiveLogs(logs, samples)
        val snap = CycleSnapshot.compute(CycleData(effective, overrides, today), mode, showFertilityInTrack = showFert, paused = episode != null)
        Computed(
            snap, TodayPresenter.present(snap, episode?.kind, muted), InsightsPresenter.present(snap, muted), WhyPresenter.present(snap),
            mode, showFert, muted, episode, logs, samples, overrides, episodes,
        )
    }.flowOn(Dispatchers.Default)

    val model: StateFlow<AppModel?> = combine(computed, settings) { c, settings ->
        AppModel(
            snapshot = c.snapshot, today = c.today, insights = c.insights, why = c.why,
            mode = c.mode, showFertility = c.showFertility, muted = c.muted, activeEpisode = c.episode, settings = settings,
            episodes = c.episodes,
            showOnboarding = Onboarding.shouldShow(c.logs, c.overrides, c.episodes, settings),
            userLogs = c.logs, healthSamples = c.samples,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private val context = VmContext(scope, repo, app, errors, _messages)
    val privacy = PrivacyActions(context)
    val report = ReportActions(context) { model.value }
    val health = HealthActions(context, logs, overrides, settings) { model.value }

    init {
        // reminders, widget and shortcut follow the data and settings (planning only here, with the DB open)
        @OptIn(FlowPreview::class)
        scope.launch(errors) {
            model.filterNotNull()
                .map { m -> m.snapshot to m.settings.filterKeys { !isBookkeeping(it) } }
                .distinctUntilChanged { a, b -> a.first === b.first && a.second == b.second }
                .debounce(1_000)
                .collectLatest { (snap, st) -> runCatching { SystemSurfaces.sync(app, repo, snap, st) } }
        }
    }


    /** Every change to a day is one read-modify-write transaction: quick taps never overwrite each other. */
    fun updateLog(day: Day, transform: (DayLog) -> DayLog) {
        scope.launch(errors) { repo.updateLog(day, transform) }
    }

    /** „Periode hat heute begonnen“: medium flow today + start override if detection wouldn't see it. Undoable. */
    fun periodStartedToday() {
        val m = model.value ?: return
        val today = m.snapshot.today
        scope.launch(errors) {
            var after = DayLog.EMPTY
            // already logged bleeding: never downgrade a logged level
            val before = repo.updateLog(today) { l -> (if (l.isBleed) l else l.copy(flow = Flow.MEDIUM)).also { after = it } }
            if (before?.isBleed == true) return@launch
            val overrideBefore = m.snapshot.data.overrides[today]
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
        scope.launch(errors) {
            val before = repo.updateLog(today) { it.copy(flow = flow) }
            _messages.emit(UiMessage(if (flow == Flow.NONE) "tEnded" else "tFlow") { repo.saveLog(today, before ?: DayLog.EMPTY) })
        }
    }

    /** Calendar / day sheet: mark [day] as a period start (bleeding if none logged + START override). Undoable. */
    fun markStart(day: Day) {
        val m = model.value ?: return
        if (day > m.snapshot.today) return
        scope.launch(errors) {
            val overrideBefore = m.snapshot.data.overrides[day]
            val before = repo.updateLog(day) { if (it.isBleed) it else it.copy(flow = Flow.MEDIUM) }
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
        scope.launch(errors) {
            val before = repo.updateLog(today) { it.copy(lh = if (it.lh == result) null else result) }
            _messages.emit(UiMessage(if (result == LhResult.POSITIVE) "tLhPos" else "tLh") { repo.saveLog(today, before ?: DayLog.EMPTY) })
        }
    }

    fun setMode(mode: Mode) { scope.launch(errors) { repo.setSetting(SettingKeys.MODE, UserPrefs.modeValue(mode)) } }

    fun setShowFertility(on: Boolean) { scope.launch(errors) { repo.setSetting(SettingKeys.SHOW_FERT, UserPrefs.showFertilityValue(on)) } }

    fun mute(patternId: String) {
        val m = model.value ?: return
        scope.launch(errors) { repo.setSetting(SettingKeys.MUTED, UserPrefs.mutedValue(m.muted + patternId)) }
    }

    fun unmuteAll() { scope.launch(errors) { repo.setSetting(SettingKeys.MUTED, null) } }

    /**
     * Life stage (pauses predictions). Re-selecting the active stage does nothing, so the real start date is kept.
     * A stage that started today is replaced instead of being ended. Undoable.
     */
    fun setLifeStage(stage: LifeStage?) {
        val m = model.value ?: return
        val today = m.snapshot.today
        val active = m.activeEpisode
        if (LifeStage.of(active?.kind) == stage && (stage != null || active == null)) return
        scope.launch(errors) {
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

    fun setSetting(key: String, value: String?) { scope.launch(errors) { repo.setSetting(key, value) } }

    /** Opt-in for reminders (also answers the Today offer). Off removes every planned reminder. */
    fun setNotifyConsent(on: Boolean) {
        scope.launch(errors) {
            repo.setSetting(NotifyKeys.CONSENT, if (on) "1" else null)
            repo.setSetting(NotifyKeys.ASKED, "1")
        }
    }

    fun declineReminderOffer() { setSetting(NotifyKeys.ASKED, "1") }

    fun setNotifyKind(k: NotifyKind, on: Boolean) {
        scope.launch(errors) {
            repo.setSetting(NotifyKeys.kind(k), if (on == k.defaultOn) null else if (on) "1" else "0")
            if (k == NotifyKind.BACKUP) {
                repo.setSetting(NotifyKeys.BACKUP_SINCE, if (on) LocalDate.now().toEpochDay().toString() else null)
            }
        }
    }

    /** Debug: a real notification through the normal path, 10 s later (lock the phone to see the lock-screen version). */
    fun testNotification() {
        if (!BuildConfig.DEBUG) return
        val discreet = model.value?.settings?.get(NotifyKeys.DISCREET) != "0"
        scope.launch(errors) {
            delay(10_000)
            Notifier.post(app, NotifyKind.PERIOD_SOON, discreet)
        }
    }

    // ---------------- onboarding (slice 4) ----------------

    /** Stays true from the first onboarding screen until it is finished, even if an import fills the store meanwhile. */
    val onboardingActive = MutableStateFlow(false)
    val onboardingChoices = MutableStateFlow(OnboardingChoices())

    fun finishOnboarding(enableLock: Boolean) {
        val today = LocalDate.now().toEpochDay().toInt()
        val choices = onboardingChoices.value
        scope.launch(errors) {
            Onboarding.apply(repo, choices, today) { Uuid.random().toString() }
            if (enableLock) repo.setSetting(SettingKeys.LOCK_ENABLED, "1")
            onboardingActive.value = false
            onboardingChoices.value = OnboardingChoices()
        }
    }

    fun saved() { scope.launch(errors) { _messages.emit(UiMessage("tSaved")) } }

    fun undo(message: UiMessage) { message.undo?.let { u -> scope.launch(errors) { u() } } }

    /** Debug builds only: replace all data with a prototype scenario anchored on today. */
    fun loadDemo(scenario: SampleScenario) {
        if (!BuildConfig.DEBUG) return
        val today = LocalDate.now().toEpochDay().toInt()
        scope.launch(errors) {
            val s = SampleScenarios.generate(scenario, today)
            repo.replaceAll(
                logs = s.logs,
                episodes = s.pausedSince?.let { listOf(Episode("demo-hbc", EpisodeKind.HORMONAL_CONTRACEPTION, it)) } ?: emptyList(),
                // onboarding stays done: otherwise the empty moment inside the replace brings it back
                settings = mapOf(Onboarding.DONE to "1") +
                    (if (s.mode == Mode.CONCEIVE) mapOf(SettingKeys.MODE to "conceive") else emptyMap()),
            )
        }
    }

    /** Debug builds only. */
    fun eraseAll() {
        if (!BuildConfig.DEBUG) return
        scope.launch(errors) { repo.eraseAll() }
    }

    companion object {
        /** Settings the engine and presenters read; any other change only refreshes the cheap part of the model. */
        private val ENGINE_KEYS = SettingKeys.USER_PREFS

        /** Device bookkeeping no system surface (reminders, widget, shortcut) depends on. */
        private fun isBookkeeping(k: String) = k.startsWith("hc.") || k == SettingKeys.LOCK_BACKOFF ||
            k == SettingKeys.EXPORT_COUNT || k == SettingKeys.REPORT_NAME
    }
}
