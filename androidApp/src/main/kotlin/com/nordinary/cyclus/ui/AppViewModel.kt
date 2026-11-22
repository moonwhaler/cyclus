package com.nordinary.cyclus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
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
    val settings: Map<String, String>,
    val episodes: List<Episode>,
    /** Truly empty store and onboarding not done (fresh install / after erase). */
    val showOnboarding: Boolean,
    /** The person's own entries: what the Log sheet edits and exports contain (no Health Connect values). */
    val userLogs: Map<Day, DayLog> = snapshot.data.logs,
    /** Records from other apps via Health Connect (day sheet source badges). */
    val healthSamples: List<com.nordinary.cyclus.health.HealthSample> = emptyList(),
) {
    val logs: Map<Day, DayLog> get() = snapshot.data.logs
}

/** One-shot UI message (snackbar): a prototype text key or an app string resource, optionally undoable. */
data class UiMessage(val key: String, val res: Int? = null, val undo: (suspend () -> Unit)? = null)

@OptIn(ExperimentalUuidApi::class)
class AppViewModel(
    private val repo: CyclusRepository,
    private val app: com.nordinary.cyclus.CyclusApplication,
    restored: Boolean = false,
) : ViewModel() {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    /**
     * A failed write (storage full, I/O error) shows a message instead of crashing the app; nothing is half-written
     * because every repository write is one transaction.
     */
    private val errors = kotlinx.coroutines.CoroutineExceptionHandler { _, _ ->
        _messages.tryEmit(UiMessage("", res = com.nordinary.cyclus.R.string.error_save))
    }

    init {
        // shown once the UI listens (after the lock screen), not into the void
        if (restored) viewModelScope.launch(errors) {
            _messages.subscriptionCount.first { it > 0 }
            _messages.emit(UiMessage("", res = com.nordinary.cyclus.R.string.restored))
        }
    }

    /** „Nur auf dem Gerät“ (file marker, not a DB setting). */
    val deviceOnly = kotlinx.coroutines.flow.MutableStateFlow(app.backup.deviceOnly)

    // ---- the store, read once and shared by the model, the Health Connect trigger and the lock (no duplicate queries)
    private val logs = repo.observeLogs().shareIn(viewModelScope, SharingStarted.Eagerly, 1)
    private val overrides = repo.observeOverrides().shareIn(viewModelScope, SharingStarted.Eagerly, 1)
    /** All settings (the lock configuration in MainActivity reads these too). */
    val settings: kotlinx.coroutines.flow.SharedFlow<Map<String, String>> = repo.observeSettings().shareIn(viewModelScope, SharingStarted.Eagerly, 1)

    /**
     * Local calendar day. Re-read at the next midnight (a coroutine delay doesn't count deep sleep, so also), when the
     * app comes to the foreground and when the date, time or time zone changes. No polling.
     */
    private val todayFlow = kotlinx.coroutines.flow.merge(
        flow {
            while (true) {
                emit(Unit)
                val now = java.time.ZonedDateTime.now()
                delay(java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis() + 1_000)
            }
        },
        kotlinx.coroutines.flow.callbackFlow {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) { trySend(Unit) }
            }
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_DATE_CHANGED); addAction(android.content.Intent.ACTION_TIME_CHANGED)
                addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            }
            androidx.core.content.ContextCompat.registerReceiver(app, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
            val owner = androidx.lifecycle.ProcessLifecycleOwner.get()
            val observer = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_START) trySend(Unit) }
            owner.lifecycle.addObserver(observer)
            awaitClose { app.unregisterReceiver(receiver); owner.lifecycle.removeObserver(observer) }
        }.flowOn(Dispatchers.Main),
    ).map { LocalDate.now().toEpochDay().toInt() }.distinctUntilChanged()

    /** The expensive part (engine + presenters): recomputed only when data, the engine settings or the day change. */
    private class Computed(
        val snapshot: CycleSnapshot, val today: TodayUiState, val insights: InsightsUiState, val why: WhyModel?,
        val mode: Mode, val showFertility: Boolean, val muted: Set<String>, val episode: Episode?,
        val logs: Map<Day, DayLog>, val samples: List<com.nordinary.cyclus.health.HealthSample>,
        val overrides: Map<Day, CycleOverride>, val episodes: List<Episode>,
    )

    private val computed = combine(
        combine(logs, repo.observeHealthSamples()) { l, h -> l to h },
        overrides, repo.observeEpisodes(),
        // bookkeeping (Health Connect tokens, last sync, PIN back-off, export counters …) never recomputes the engine
        settings.map { st -> st.filterKeys { it in ENGINE_KEYS } }.distinctUntilChanged(),
        todayFlow,
    ) { (logs, samples), overrides, episodes, engine, today ->
        val mode = if (engine[KEY_MODE] == "conceive") Mode.CONCEIVE else Mode.TRACK
        val showFert = engine[KEY_SHOW_FERT] == "1"
        val muted = engine[KEY_MUTED]?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        val episode = episodes.lastOrNull { it.isActiveOn(today) }
        // Health Connect values fill fields the person left empty (spec §11.4 rule 4); the engine is unchanged
        val effective = com.nordinary.cyclus.health.HealthMerge.effectiveLogs(logs, samples)
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
            showOnboarding = com.nordinary.cyclus.presenter.Onboarding.shouldShow(c.logs, c.overrides, c.episodes, settings),
            userLogs = c.logs, healthSamples = c.samples,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val health = com.nordinary.cyclus.health.HealthSync(app)

    init {
        // Health Connect: triggered by the person's own data and the HC toggles only (its own writes never retrigger it)
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        viewModelScope.launch(errors) {
            val userData = combine(
                logs, overrides,
                settings.map { st ->
                    st.filterKeys { it.startsWith("hc.w.") || it == com.nordinary.cyclus.health.HealthKeys.CONNECTED }
                }.distinctUntilChanged(),
            ) { a, b, c -> Triple(a, b, c) }.distinctUntilChanged()
            kotlinx.coroutines.flow.merge(userData.debounce(3_000).map { false }, app.healthKick).collect { full ->
                runCatching { health.sync(app.backup, app.store, repo, full) }
            }
        }
    }

    init {
        // reminders, widget and shortcut follow the data and settings (planning only here, with the DB open)
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        viewModelScope.launch(errors) {
            model.filterNotNull()
                .map { m -> m.snapshot to m.settings.filterKeys { !isBookkeeping(it) } }
                .distinctUntilChanged { a, b -> a.first === b.first && a.second == b.second }
                .debounce(1_000)
                .collectLatest { (snap, st) -> runCatching { com.nordinary.cyclus.surfaces.SystemSurfaces.sync(app, repo, snap, st) } }
        }
    }


    /** Every change to a day is one read-modify-write transaction: quick taps never overwrite each other. */
    fun updateLog(day: Day, transform: (DayLog) -> DayLog) {
        viewModelScope.launch(errors) { repo.updateLog(day, transform) }
    }

    /** „Periode hat heute begonnen“: medium flow today + start override if detection wouldn't see it. Undoable. */
    fun periodStartedToday() {
        val m = model.value ?: return
        val today = m.snapshot.today
        viewModelScope.launch(errors) {
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
        viewModelScope.launch(errors) {
            val before = repo.updateLog(today) { it.copy(flow = flow) }
            _messages.emit(UiMessage(if (flow == Flow.NONE) "tEnded" else "tFlow") { repo.saveLog(today, before ?: DayLog.EMPTY) })
        }
    }

    /** Calendar / day sheet: mark [day] as a period start (bleeding if none logged + START override). Undoable. */
    fun markStart(day: Day) {
        val m = model.value ?: return
        if (day > m.snapshot.today) return
        viewModelScope.launch(errors) {
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
        viewModelScope.launch(errors) {
            val before = repo.updateLog(today) { it.copy(lh = if (it.lh == result) null else result) }
            _messages.emit(UiMessage(if (result == LhResult.POSITIVE) "tLhPos" else "tLh") { repo.saveLog(today, before ?: DayLog.EMPTY) })
        }
    }

    fun setMode(mode: Mode) { viewModelScope.launch(errors) { repo.setSetting(KEY_MODE, if (mode == Mode.CONCEIVE) "conceive" else null) } }

    fun setShowFertility(on: Boolean) { viewModelScope.launch(errors) { repo.setSetting(KEY_SHOW_FERT, if (on) "1" else null) } }

    fun mute(patternId: String) {
        val m = model.value ?: return
        viewModelScope.launch(errors) { repo.setSetting(KEY_MUTED, (m.muted + patternId).sorted().joinToString(",")) }
    }

    fun unmuteAll() { viewModelScope.launch(errors) { repo.setSetting(KEY_MUTED, null) } }

    /**
     * Life stage (pauses predictions). Re-selecting the active stage does nothing, so the real start date is kept.
     * A stage that started today is replaced instead of being ended. Undoable.
     */
    fun setLifeStage(stage: LifeStage?) {
        val m = model.value ?: return
        val today = m.snapshot.today
        val active = m.activeEpisode
        if (LifeStage.of(active?.kind) == stage && (stage != null || active == null)) return
        viewModelScope.launch(errors) {
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

    // ---------------- privacy, backup, export / import, erase (slice 3) ----------------

    private fun msg(res: Int) { viewModelScope.launch(errors) { _messages.emit(UiMessage("", res = res)) } }

    fun setDeviceOnly(on: Boolean) {
        deviceOnly.value = on
        viewModelScope.launch(errors) { app.backup.setDeviceOnly(on, repo, app.appLabel()) }
    }

    fun backupStatus() = app.backup.status()

    fun setSetting(key: String, value: String?) { viewModelScope.launch(errors) { repo.setSetting(key, value) } }

    /** Opt-in for reminders (also answers the Today offer). Off removes every planned reminder. */
    fun setNotifyConsent(on: Boolean) {
        viewModelScope.launch(errors) {
            repo.setSetting(com.nordinary.cyclus.presenter.NotifyKeys.CONSENT, if (on) "1" else null)
            repo.setSetting(com.nordinary.cyclus.presenter.NotifyKeys.ASKED, "1")
        }
    }

    fun declineReminderOffer() { setSetting(com.nordinary.cyclus.presenter.NotifyKeys.ASKED, "1") }

    fun setNotifyKind(k: com.nordinary.cyclus.presenter.NotifyKind, on: Boolean) {
        viewModelScope.launch(errors) {
            repo.setSetting(com.nordinary.cyclus.presenter.NotifyKeys.kind(k), if (on == k.defaultOn) null else if (on) "1" else "0")
            if (k == com.nordinary.cyclus.presenter.NotifyKind.BACKUP) {
                repo.setSetting(com.nordinary.cyclus.presenter.NotifyKeys.BACKUP_SINCE, if (on) LocalDate.now().toEpochDay().toString() else null)
            }
        }
    }

    /** Debug: a real notification through the normal path, 10 s later (lock the phone to see the lock-screen version). */
    fun testNotification() {
        if (!com.nordinary.cyclus.BuildConfig.DEBUG) return
        val discreet = model.value?.settings?.get(com.nordinary.cyclus.presenter.NotifyKeys.DISCREET) != "0"
        viewModelScope.launch(errors) {
            delay(10_000)
            com.nordinary.cyclus.notify.Notifier.post(app, com.nordinary.cyclus.presenter.NotifyKind.PERIOD_SOON, discreet)
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch(errors + Dispatchers.Default) {
            val (salt, hash) = com.nordinary.cyclus.security.Pin.hash(pin)
            repo.setSetting(SettingKeys.LOCK_PIN_SALT, salt)
            repo.setSetting(SettingKeys.LOCK_PIN_HASH, hash)
        }
    }

    fun removePin() {
        viewModelScope.launch(errors) {
            repo.setSetting(SettingKeys.LOCK_PIN_HASH, null); repo.setSetting(SettingKeys.LOCK_PIN_SALT, null)
            repo.setSetting(SettingKeys.LOCK_PIN_ONLY, null)
        }
    }

    /** Verifies a PIN on the lock screen. PBKDF2 (200k rounds) runs on a background thread, never on the main thread. */
    suspend fun checkPin(pin: String, config: com.nordinary.cyclus.lock.LockConfig): Boolean {
        val ok = kotlinx.coroutines.withContext(Dispatchers.Default) {
            config.pinSalt != null && config.pinHash != null && com.nordinary.cyclus.security.Pin.verify(pin, config.pinSalt, config.pinHash)
        }
        // the failure count is read from the store, not the (possibly older) config: fast entries all count
        val failures = repo.setting(SettingKeys.LOCK_BACKOFF)?.toIntOrNull() ?: 0
        if (ok) repo.setSetting(SettingKeys.LOCK_BACKOFF, null)
        else repo.setSetting(SettingKeys.LOCK_BACKOFF, app.lock.registerFailure(failures).toString())
        return ok
    }

    val exporting = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Encrypts all data with [passphrase] (mode 1) and writes it to the picked document. */
    fun export(uri: android.net.Uri, passphrase: CharArray) {
        exporting.value = true
        viewModelScope.launch(errors) {
            try {
                val model = repo.exportAll(java.time.Instant.now().toString(), app.appLabel(), includeSecurity = false)
                val bytes = kotlinx.coroutines.withContext(Dispatchers.Default) { com.nordinary.cyclus.backup.CyclusFile.sealWithPassphrase(model.encode(), passphrase) }
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                }
                val count = (repo.setting(SettingKeys.EXPORT_COUNT)?.toIntOrNull() ?: 0) + 1
                repo.setSetting(SettingKeys.EXPORT_COUNT, count.toString())
                repo.setSetting(SettingKeys.EXPORT_LAST, LocalDate.now().toEpochDay().toString())
                msg(com.nordinary.cyclus.R.string.export_done)
            } catch (e: Exception) {
                msg(com.nordinary.cyclus.R.string.export_failed)
            } finally {
                passphrase.fill(' ')
                exporting.value = false
            }
        }
    }

    val importStage = kotlinx.coroutines.flow.MutableStateFlow<com.nordinary.cyclus.ui.privacy.ImportStage>(com.nordinary.cyclus.ui.privacy.ImportStage.Passphrase)
    private var importUri: android.net.Uri? = null
    private var pendingImport: com.nordinary.cyclus.backup.ExportFile? = null

    fun startImport(uri: android.net.Uri) { importUri = uri; pendingImport = null; importStage.value = com.nordinary.cyclus.ui.privacy.ImportStage.Passphrase }

    fun openImport(passphrase: CharArray) {
        val uri = importUri ?: return
        importStage.value = com.nordinary.cyclus.ui.privacy.ImportStage.Working
        viewModelScope.launch(errors) {
            val stage = try {
                val bytes = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)!!.use { s -> readCapped(s, com.nordinary.cyclus.backup.CyclusFile.MAX_FILE_BYTES) }
                }
                val file = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    com.nordinary.cyclus.backup.ExportFile.decode(com.nordinary.cyclus.backup.CyclusFile.openWithPassphrase(bytes, passphrase))
                }
                pendingImport = file
                com.nordinary.cyclus.ui.privacy.ImportStage.Preview(file.preview())
            } catch (e: com.nordinary.cyclus.backup.CyclusFileException.WrongPassphraseOrDamaged) {
                com.nordinary.cyclus.ui.privacy.ImportStage.Error(com.nordinary.cyclus.R.string.import_wrong)
            } catch (e: com.nordinary.cyclus.backup.CyclusFileException.UnsupportedVersion) {
                com.nordinary.cyclus.ui.privacy.ImportStage.Error(com.nordinary.cyclus.R.string.import_unsupported)
            } catch (e: Exception) {
                com.nordinary.cyclus.ui.privacy.ImportStage.Error(com.nordinary.cyclus.R.string.import_bad)
            } finally {
                passphrase.fill(' ')
            }
            importStage.value = stage
        }
    }

    fun applyImport(replace: Boolean, onDone: () -> Unit) {
        val file = pendingImport ?: return
        viewModelScope.launch(errors) {
            // the file was validated before the preview; a failure here (storage) rolls the transaction back
            val ok = runCatching { app.backup.exclusive { if (replace) repo.importReplace(file) else repo.importMerge(file) } }.isSuccess
            pendingImport = null; importUri = null
            importStage.value = if (ok) com.nordinary.cyclus.ui.privacy.ImportStage.Passphrase
            else com.nordinary.cyclus.ui.privacy.ImportStage.Error(com.nordinary.cyclus.R.string.import_bad)
            if (ok) { msg(com.nordinary.cyclus.R.string.import_done); onDone() }
        }
    }

    fun cancelImport() { pendingImport = null; importUri = null; importStage.value = com.nordinary.cyclus.ui.privacy.ImportStage.Passphrase }

    // ---------------- Doctor Visit Pack (slice 4) ----------------

    /** Builder state lives here only (not saved state): the questions text never leaves memory. */
    val reportOptions = kotlinx.coroutines.flow.MutableStateFlow<com.nordinary.cyclus.presenter.ReportOptions?>(null)
    val reportFile = kotlinx.coroutines.flow.MutableStateFlow<java.io.File?>(null)
    val reportWorking = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** PDF language: the single source for the toggle and the renderer. */
    val reportLocale = kotlinx.coroutines.flow.MutableStateFlow(java.util.Locale.GERMAN)

    fun initReport(defaultQuestions: String, uiLocale: java.util.Locale) {
        if (reportOptions.value == null) {
            reportOptions.value = com.nordinary.cyclus.presenter.ReportOptions(questions = defaultQuestions)
            reportLocale.value = if (uiLocale.language == "en") java.util.Locale.ENGLISH else java.util.Locale.GERMAN
            // the name is remembered on this device only (encrypted DB, never exported)
            viewModelScope.launch(errors) {
                repo.setting(KEY_REPORT_NAME)?.let { n -> updateReport { if (it.name.isEmpty()) it.copy(name = n) else it } }
            }
        }
    }

    fun updateReport(change: (com.nordinary.cyclus.presenter.ReportOptions) -> com.nordinary.cyclus.presenter.ReportOptions) {
        reportOptions.value = reportOptions.value?.let(change)
    }

    /** Builds the PDF (same file is previewed and shared). */
    fun buildReport(regionLocale: java.util.Locale) {
        reportOptions.value?.name?.trim()?.let { n -> viewModelScope.launch(errors) { repo.setSetting(KEY_REPORT_NAME, n.ifEmpty { null }) } }
        val m = model.value ?: return
        val opts = reportOptions.value ?: return
        val locale = reportLocale.value
        reportWorking.value = true
        viewModelScope.launch(errors) {
            try {
                val file = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    val r = com.nordinary.cyclus.presenter.ReportPresenter.build(m.snapshot, opts, m.muted, m.episodes) ?: return@withContext null
                    val bytes = com.nordinary.cyclus.report.ReportPdf.render(app, r, locale, com.nordinary.cyclus.report.ReportPdf.paperFor(regionLocale))
                    val name = (if (locale.language == "de") "Zyklusbericht-" else "Cycle-summary-") + LocalDate.now() + ".pdf"
                    com.nordinary.cyclus.report.ReportFiles.write(app, name, bytes)
                }
                reportFile.value = file
            } catch (e: Exception) {
                msg(com.nordinary.cyclus.R.string.export_failed)
            } finally {
                reportWorking.value = false
            }
        }
    }

    fun closeReport() { reportFile.value = null; reportOptions.value = null; com.nordinary.cyclus.report.ReportFiles.clear(app) }

    // ---------------- onboarding (slice 4) ----------------

    /** Stays true from the first onboarding screen until it is finished, even if an import fills the store meanwhile. */
    val onboardingActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val onboardingChoices = kotlinx.coroutines.flow.MutableStateFlow(com.nordinary.cyclus.presenter.OnboardingChoices())

    fun finishOnboarding(enableLock: Boolean) {
        val today = LocalDate.now().toEpochDay().toInt()
        val choices = onboardingChoices.value
        viewModelScope.launch(errors) {
            com.nordinary.cyclus.presenter.Onboarding.apply(repo, choices, today) { Uuid.random().toString() }
            if (enableLock) repo.setSetting(SettingKeys.LOCK_ENABLED, "1")
            onboardingActive.value = false
            onboardingChoices.value = com.nordinary.cyclus.presenter.OnboardingChoices()
        }
    }

    /** Erase everything (crypto-shred); the store reopens empty. */
    fun eraseEverything(deleteHealth: Boolean = false) {
        app.lock.forget()
        app.store.erase(deleteHealth = deleteHealth)
    }

    // ---- Health Connect (Privacy Center)

    data class HealthUi(
        val availability: com.nordinary.cyclus.health.HealthAvailability,
        val connected: Boolean,
        val granted: Set<String>,
        val enabled: Set<com.nordinary.cyclus.health.HealthCategory>,
        val lastSync: Long?,
        val error: Boolean,
        val noHistory: Boolean,
        val imported: Long,
        val removed: List<com.nordinary.cyclus.health.HealthLink>,
        val wrote: Boolean,
        /** Days Cyclus wrote earlier that could come back into this empty Cyclus (0 = no offer). */
        val restoreOffer: Int = 0,
    )

    suspend fun healthUi(settings: Map<String, String>): HealthUi {
        val av = health.availability()
        val ok = av == com.nordinary.cyclus.health.HealthAvailability.AVAILABLE
        return HealthUi(
            availability = av,
            connected = settings[com.nordinary.cyclus.health.HealthKeys.CONNECTED] == "1",
            granted = if (ok) health.granted() else emptySet(),
            enabled = com.nordinary.cyclus.health.HealthKeys.enabled(settings),
            lastSync = settings[com.nordinary.cyclus.health.HealthKeys.LAST_SYNC]?.toLongOrNull(),
            error = settings[com.nordinary.cyclus.health.HealthKeys.ERROR] != null,
            noHistory = settings[com.nordinary.cyclus.health.HealthKeys.NO_HISTORY] == "1",
            imported = repo.healthSampleCount(),
            removed = repo.healthLinks().values.filter { it.state == com.nordinary.cyclus.health.LinkState.REMOVED },
            wrote = settings[com.nordinary.cyclus.health.HealthKeys.WROTE] == "1",
            restoreOffer = settings[com.nordinary.cyclus.health.HealthKeys.RESTORE_OFFER]?.toIntOrNull() ?: 0,
        )
    }

    /** After the read-permission screen: connected once anything readable was granted; first sync reads everything. */
    fun healthConnected(granted: Set<String>) {
        val reads = com.nordinary.cyclus.health.HealthType.entries.map {
            androidx.health.connect.client.permission.HealthPermission.getReadPermission(com.nordinary.cyclus.health.HealthSync.recordClass(it))
        }
        if (granted.none { it in reads }) return
        viewModelScope.launch(errors) {
            repo.setSetting(com.nordinary.cyclus.health.HealthKeys.CONNECTED, "1")
            val history = androidx.health.connect.client.permission.HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
            repo.setSetting(com.nordinary.cyclus.health.HealthKeys.NO_HISTORY, if (history in granted) null else "1")
            app.healthKick.emit(true)
        }
    }

    fun healthSyncNow() { app.healthKick.tryEmit(false) }

    /** Answer to „Frühere Einträge übernehmen?“; no means not asked again until Health Connect is disconnected. */
    fun answerHealthRestore(accept: Boolean) {
        viewModelScope.launch(errors) {
            if (accept) {
                val n = health.restoreOwn(app.backup, app.store, repo)
                if (n > 0) msg(com.nordinary.cyclus.R.string.hc_restore_done)
            } else {
                repo.setSetting(com.nordinary.cyclus.health.HealthKeys.RESTORE_DECLINED, "1")
                repo.setSetting(com.nordinary.cyclus.health.HealthKeys.RESTORE_OFFER, null)
            }
        }
    }

    /** Switch writing of [c]. Off: [deleteThere] deletes what Cyclus wrote, else the records stay and are forgotten. */
    fun setHealthWrite(c: com.nordinary.cyclus.health.HealthCategory, on: Boolean, deleteThere: Boolean = false) {
        viewModelScope.launch(errors) {
            if (!on && !deleteThere) app.backup.exclusive { c.types.forEach { repo.deleteHealthLinksOfType(it) } }
            repo.setSetting(com.nordinary.cyclus.health.HealthKeys.write(c), if (on) "1" else null)
        }
    }

    /** Rule 6 review: keep only in Cyclus (never written again) or remove the values from Cyclus too. */
    fun reviewRemoved(links: List<com.nordinary.cyclus.health.HealthLink>, keep: Boolean) {
        viewModelScope.launch(errors) {
            app.backup.exclusive {
                for (l in links) {
                    if (keep) { repo.upsertHealthLink(l.copy(state = com.nordinary.cyclus.health.LinkState.KEPT)); continue }
                    val days = if (l.type == com.nordinary.cyclus.health.HealthType.PERIOD) {
                        com.nordinary.cyclus.health.HealthPlanner.removedPeriodDays(model.value?.userLogs ?: emptyMap(), l.day)
                    } else listOf(l.day)
                    for (d in days) {
                        val before = repo.log(d) ?: continue
                        val after = when (l.type) {
                            com.nordinary.cyclus.health.HealthType.FLOW, com.nordinary.cyclus.health.HealthType.SPOTTING ->
                                before.copy(flow = null)
                            com.nordinary.cyclus.health.HealthType.PERIOD -> if (before.isBleed) before.copy(flow = null) else before
                            com.nordinary.cyclus.health.HealthType.BBT -> before.copy(bbt = null)
                            com.nordinary.cyclus.health.HealthType.MUCUS -> before.copy(mucus = null)
                            com.nordinary.cyclus.health.HealthType.LH -> before.copy(lh = null)
                            com.nordinary.cyclus.health.HealthType.SEX -> before.copy(sex = false, sexProtected = null)
                        }
                        repo.saveLog(d, after)
                    }
                    repo.deleteHealthLink(l.syncId)
                }
            }
        }
    }

    /** Revokes access and drops imported samples, links and tokens; Cyclus' own entries and what it wrote stay. */
    fun disconnectHealth() {
        viewModelScope.launch(errors) {
            health.revokeAll()
            app.backup.exclusive { repo.clearHealth() }
        }
    }

    fun saved() { viewModelScope.launch(errors) { _messages.emit(UiMessage("tSaved")) } }

    fun undo(message: UiMessage) { message.undo?.let { u -> viewModelScope.launch(errors) { u() } } }

    /** Debug builds only: replace all data with a prototype scenario anchored on today. */
    fun loadDemo(scenario: SampleScenario) {
        if (!com.nordinary.cyclus.BuildConfig.DEBUG) return
        val today = LocalDate.now().toEpochDay().toInt()
        viewModelScope.launch(errors) {
            val s = SampleScenarios.generate(scenario, today)
            repo.replaceAll(
                logs = s.logs,
                episodes = s.pausedSince?.let { listOf(Episode("demo-hbc", EpisodeKind.HORMONAL_CONTRACEPTION, it)) } ?: emptyList(),
                // onboarding stays done: otherwise the empty moment inside the replace brings it back
                settings = mapOf(com.nordinary.cyclus.presenter.Onboarding.DONE to "1") +
                    (if (s.mode == Mode.CONCEIVE) mapOf(KEY_MODE to "conceive") else emptyMap()),
            )
        }
    }

    /** Debug builds only. */
    fun eraseAll() {
        if (!com.nordinary.cyclus.BuildConfig.DEBUG) return
        viewModelScope.launch(errors) { repo.eraseAll() }
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_SHOW_FERT = "showFertTrack"
        const val KEY_MUTED = "mutedPatterns"
        /** Device setting: name for the Arztbericht (not in exports). */
        const val KEY_REPORT_NAME = "report.name"

        /** Settings the engine and presenters read; any other change only refreshes the cheap part of the model. */
        private val ENGINE_KEYS = setOf(KEY_MODE, KEY_SHOW_FERT, KEY_MUTED)

        /** Device bookkeeping no system surface (reminders, widget, shortcut) depends on. */
        private fun isBookkeeping(k: String) = k.startsWith("hc.") || k == SettingKeys.LOCK_BACKOFF ||
            k == SettingKeys.EXPORT_COUNT || k == KEY_REPORT_NAME
    }
}

/** Reads at most [max] bytes (API 29-safe; `readNBytes` needs Android 13). Larger input → [CyclusFileException.TooLarge]. */
private fun readCapped(input: java.io.InputStream, max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        if (out.size() + n > max) throw com.nordinary.cyclus.backup.CyclusFileException.TooLarge("file too large")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
