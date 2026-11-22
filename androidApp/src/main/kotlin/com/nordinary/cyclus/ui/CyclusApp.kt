package com.nordinary.cyclus.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.nordinary.cyclus.ActionEntry
import com.nordinary.cyclus.BuildConfig
import com.nordinary.cyclus.CyclusApplication
import com.nordinary.cyclus.R
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.engine.Mode
import com.nordinary.cyclus.health.DebugHealth
import com.nordinary.cyclus.health.HealthMerge
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.i18n.rememberFmt
import com.nordinary.cyclus.notify.Notifier
import com.nordinary.cyclus.presenter.CalendarPresenter
import com.nordinary.cyclus.presenter.InsightsUiState
import com.nordinary.cyclus.presenter.LifeStage
import com.nordinary.cyclus.presenter.NotifyKeys
import com.nordinary.cyclus.presenter.NotifyPrefs
import com.nordinary.cyclus.presenter.ReportPresenter
import com.nordinary.cyclus.presenter.TodayPresenter
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.ui.calendar.CalendarContent
import com.nordinary.cyclus.ui.calendar.DaySheet
import com.nordinary.cyclus.ui.common.CyclusIcons
import com.nordinary.cyclus.ui.insights.InsightsContent
import com.nordinary.cyclus.ui.insights.PatternsScreen
import com.nordinary.cyclus.ui.log.LogSheet
import com.nordinary.cyclus.ui.privacy.NotificationsScreen
import com.nordinary.cyclus.ui.privacy.PrivacyEntry
import com.nordinary.cyclus.ui.report.ReportScreen
import com.nordinary.cyclus.ui.settings.SettingsScreen
import com.nordinary.cyclus.ui.sheets.MethodSheet
import com.nordinary.cyclus.ui.sheets.WhySheet
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.theme.DarkCyclus
import com.nordinary.cyclus.ui.today.TodayContent
import com.nordinary.cyclus.widget.MediumWidgetReceiver
import com.nordinary.cyclus.widget.SmallWidgetReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable private data object MainKey : NavKey
@Serializable private data object SettingsKey : NavKey
@Serializable private data object PrivacyKey : NavKey
@Serializable private data object ReportKey : NavKey
@Serializable private data object NotifyKey : NavKey
@Serializable private data object PatternsKey : NavKey

private enum class Tab(val key: String) { TODAY("tabToday"), CALENDAR("tabCal"), INSIGHTS("tabIns") }
private enum class Sheet { NONE, LOG, DAY, WHY, METHOD }

/** Root: pushed screens via Navigation 3 (predictive back, survives recreation), shared snackbar, sheets on top. */
@Composable
fun CyclusApp(vm: AppViewModel) {
    // compile-time constant: R8 removes the demo data, wipe and test tools from release builds
    val debug = BuildConfig.DEBUG
    val fmt = rememberFmt()
    val model by vm.model.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(MainKey)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.undo_label)
    val soon = stringResource(R.string.soon_title)

    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    var sheetDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var logChanged by rememberSaveable { mutableStateOf(false) }

    val res = LocalResources.current
    fun text(msg: UiMessage) = msg.res?.let { res.getString(it) } ?: fmt.s(msg.key)
    LaunchedEffect(vm, fmt) {
        vm.messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            launch {
                if (msg.undo != null) {
                    launch { delay(5_000); snackbar.currentSnackbarData?.dismiss() }
                    val r = snackbar.showSnackbar(text(msg), actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
                    if (r == SnackbarResult.ActionPerformed) vm.undo(msg)
                } else snackbar.showSnackbar(text(msg), duration = SnackbarDuration.Short)
            }
        }
    }
    val comingSoon: () -> Unit = { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(soon) } }
    fun openLog(day: Int) { sheetDay = day; logChanged = false; sheet = Sheet.LOG }

    // widget / tile / shortcut action (ActionEntry): runs once, here = unlocked, ready, not onboarding
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CyclusApplication
    val pending by app.pendingAction.collectAsStateWithLifecycle()
    LaunchedEffect(pending, model != null) {
        val a = pending ?: return@LaunchedEffect
        val m = model ?: return@LaunchedEffect
        app.pendingAction.value = null
        while (backStack.size > 1) backStack.removeLastOrNull()
        val today = m.snapshot.today
        when (a) {
            ActionEntry.PERIOD ->
                if (m.logs[today]?.flow?.isBleed == true) openLog(today) else { sheet = Sheet.NONE; vm.periodStartedToday() }
            else -> openLog(today)
        }
    }
    // notification permission (Android 13+) is asked only right after the user opted in
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    fun consent(on: Boolean) {
        vm.setNotifyConsent(on)
        if (on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    NavDisplay(
        backStack = backStack,
        onBack = {
            // leaving the report builder by gesture resets it like the back arrow (and drops the PDF file)
            if (backStack.lastOrNull() == ReportKey) vm.report.close()
            backStack.removeLastOrNull()
        },
        entryProvider = entryProvider {
            entry<MainKey> {
                MainScreen(
                    vm = vm, fmt = fmt, snackbar = snackbar, debug = debug,
                    onSettings = { backStack.add(SettingsKey) }, onPrivacy = { backStack.add(PrivacyKey) }, onComingSoon = comingSoon,
                    onReport = { backStack.add(ReportKey) },
                    onPatterns = { backStack.add(PatternsKey) },
                    onOpenLog = ::openLog,
                    onReminderOffer = { yes -> if (yes) consent(true) else vm.declineReminderOffer() },
                    onDay = { sheetDay = it; sheet = Sheet.DAY },
                    onWhy = { sheet = Sheet.WHY }, onMethod = { sheet = Sheet.METHOD },
                )
            }
            entry<SettingsKey> {
                Centered {
                    val m = model
                    SettingsScreen(
                        fmt = fmt, snackbar = snackbar,
                        mode = m?.mode ?: Mode.TRACK, showFertility = m?.showFertility ?: false,
                        lifeStage = LifeStage.of(m?.activeEpisode?.kind),
                        onBack = { backStack.removeLastOrNull() },
                        onMode = vm::setMode, onShowFertility = vm::setShowFertility, onLifeStage = vm::setLifeStage,
                        onComingSoon = comingSoon, onPrivacy = { backStack.add(PrivacyKey) },
                        onNotifications = { backStack.add(NotifyKey) },
                    )
                }
            }
            entry<PatternsKey> {
                val ins = model?.insights as? InsightsUiState.Ready
                if (ins != null) PatternsScreen(
                    ins.patterns, fmt, onBack = { backStack.removeLastOrNull() }, onMute = vm::mute, onUnmute = vm::unmuteAll,
                )
                else LaunchedEffect(Unit) { backStack.removeLastOrNull() }
            }
            entry<NotifyKey> {
                val m = model
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                // re-read after returning from system settings
                val resumed by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
                val allows = remember(resumed) { Notifier.canPost(ctx) }
                if (m != null) NotificationsScreen(
                    fmt = fmt, prefs = NotifyPrefs.from(m.settings),
                    conceive = m.mode == Mode.CONCEIVE, systemAllows = allows, debug = debug,
                    onBack = { backStack.removeLastOrNull() }, onConsent = ::consent, onKind = vm::setNotifyKind,
                    onDailyTime = { vm.setSetting(NotifyKeys.DAILY_TIME, it.toString()) },
                    onOpenSystem = {
                        app.lock.suspendForExternalActivity()
                        ctx.startActivity(Notifier.channelSettingsIntent(ctx))
                    },
                    onTest = { if (BuildConfig.DEBUG) vm.testNotification() },
                )
            }
            entry<ReportKey> {
                Centered {
                    ReportScreen(vm, fmt, snackbar, onBack = { backStack.removeLastOrNull() })
                }
            }
            entry<PrivacyKey> {
                Centered {
                    PrivacyEntry(vm, fmt, snackbar, onBack = { backStack.removeLastOrNull() }, onErased = {
                        while (backStack.size > 1) backStack.removeLastOrNull()
                    })
                }
            }
        },
    )

    val m = model ?: return
    val day = sheetDay
    when (sheet) {
        Sheet.LOG -> if (day != null) {
            // a tap shows at once; the stored value takes over again once the write and recompute have landed
            val stored = m.userLogs[day] ?: DayLog.EMPTY
            var shown by remember(day) { mutableStateOf<DayLog?>(null) }
            LaunchedEffect(stored) { if (stored == shown) shown = null }
            LaunchedEffect(shown) { if (shown != null) { delay(2_000); shown = null } }
            val log = shown ?: stored
            LogSheet(
            day = day, today = m.snapshot.today, log = log, mode = m.mode,
            suggestions = remember(m, day) { TodayPresenter.suggestions(m.snapshot, day) }, fmt = fmt,
            onDayChange = { sheetDay = it },
            onUpdate = { t -> logChanged = true; shown = t(shown ?: stored); vm.updateLog(day, t) },
            onDone = { sheet = Sheet.NONE; if (logChanged) vm.saved(); logChanged = false },
            onDismiss = { sheet = Sheet.NONE; if (logChanged) vm.saved(); logChanged = false },
            )
        }
        Sheet.DAY -> if (day != null) DaySheet(
            model = remember(m, day) { CalendarPresenter.daySheet(m.snapshot, day, m.userLogs[day]) }, fmt = fmt,
            onEdit = { openLog(day) },
            onMarkStart = { sheet = Sheet.NONE; vm.markStart(day) },
            onDismiss = { sheet = Sheet.NONE },
            external = remember(m, day) { HealthMerge.dayDetail(day, m.userLogs[day], m.healthSamples) },
        )
        Sheet.WHY -> m.why?.let { WhySheet(it, fmt) { sheet = Sheet.NONE } }
        Sheet.METHOD -> MethodSheet(fmt) { sheet = Sheet.NONE }
        Sheet.NONE -> {}
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MainScreen(
    vm: AppViewModel,
    fmt: Fmt,
    snackbar: SnackbarHostState,
    debug: Boolean,
    onSettings: () -> Unit,
    onPrivacy: () -> Unit,
    onComingSoon: () -> Unit,
    onReport: () -> Unit,
    onPatterns: () -> Unit,
    onOpenLog: (Int) -> Unit,
    onReminderOffer: (Boolean) -> Unit,
    onDay: (Int) -> Unit,
    onWhy: () -> Unit,
    onMethod: () -> Unit,
) {
    val c = Cyclus.colors
    val model by vm.model.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var debugMenu by remember { mutableStateOf(false) }
    // window size: rail from 600 dp (NavigationSuiteScaffold); from 840 dp Today and Calendar side by side
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val expanded = windowWidth >= 840.dp
    val shownTab = if (expanded && tab == Tab.CALENDAR) Tab.TODAY else tab

    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = c.period, selectedTextColor = c.period, indicatorColor = c.period.copy(alpha = .14f),
            unselectedIconColor = c.ink2, unselectedTextColor = c.ink2,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = c.period, selectedTextColor = c.period, indicatorColor = c.period.copy(alpha = .14f),
            unselectedIconColor = c.ink2, unselectedTextColor = c.ink2,
        ),
    )
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            for (t in Tab.entries) if (!(expanded && t == Tab.CALENDAR)) item(
                selected = shownTab == t, onClick = { tab = t },
                icon = { Icon(when (t) { Tab.TODAY -> CyclusIcons.Today; Tab.CALENDAR -> CyclusIcons.Calendar; Tab.INSIGHTS -> CyclusIcons.Insights }, null) },
                label = { Text(fmt.s(t.key), fontWeight = FontWeight.SemiBold) },
                colors = itemColors,
            )
        },
        containerColor = c.bg,
        navigationSuiteColors = NavigationSuiteDefaults.colors(navigationBarContainerColor = c.surface),
    ) {
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
            containerColor = c.bg,
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(fmt.s(shownTab.key), Modifier.combinedClickable(onClick = {}, onLongClick = { if (BuildConfig.DEBUG) debugMenu = true }), fontWeight = FontWeight.Bold)
                    },
                    subtitle = { Text(fmt.wmd(model?.snapshot?.today ?: java.time.LocalDate.now().toEpochDay().toInt()), color = c.ink2) },
                    actions = {
                        IconButton(onClick = onPrivacy) { Icon(CyclusIcons.Shield, fmt.s("aPrivacy"), tint = c.ink) }
                        IconButton(onClick = onSettings) { Icon(CyclusIcons.Gear, fmt.s("aSettings"), tint = c.ink) }
                    },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, scrolledContainerColor = c.bg),
                )
            },
            floatingActionButton = {
                model?.let { m ->
                    ExtendedFloatingActionButton(
                        onClick = { onOpenLog(m.snapshot.today) },
                        icon = { Icon(CyclusIcons.Plus, null) },
                        text = { Text(fmt.s("fab"), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                        containerColor = c.surface, contentColor = c.period,
                    )
                }
            },
            snackbarHost = { CyclusSnackbarHost(snackbar) },
        ) { padding ->
            val m = model ?: return@Scaffold
            val today: @Composable () -> Unit = {
                TodayContent(
                    state = m.today, fmt = fmt, contentPadding = padding,
                    debugNote = if (BuildConfig.DEBUG) stringResource(R.string.debug_note) else null,
                    onOpenLog = { onOpenLog(m.snapshot.today) },
                    onPeriodStarted = vm::periodStartedToday,
                    onQuickFlow = vm::quickFlow,
                    onPatterns = onPatterns, // the Muster screen directly: the count promises the list
                    onChangeStage = onSettings,
                    onLh = vm::todayLh,
                    onWhy = onWhy,
                    reminderOffer = NotifyPrefs.from(m.settings).let { !it.consent && !it.asked } &&
                        m.snapshot.prediction != null && !m.snapshot.paused,
                    onReminderOffer = onReminderOffer,
                )
            }
            when (shownTab) {
                Tab.TODAY -> if (expanded) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { today() }
                    Box(Modifier.weight(1f)) { CalendarContent(m.snapshot, fmt, padding, onDay = onDay, onMarkStart = vm::markStart) }
                } else Centered { today() }
                Tab.CALENDAR -> Centered { CalendarContent(m.snapshot, fmt, padding, onDay = onDay, onMarkStart = vm::markStart) }
                Tab.INSIGHTS -> Centered(if (expanded) 1100.dp else 640.dp) {
                    InsightsContent(
                        m.insights, fmt, padding, onMethod = onMethod, onMute = vm::mute, onAllPatterns = onPatterns,
                        twoColumns = expanded,
                        onReport = if (ReportPresenter.available(m.snapshot)) onReport else null,
                    )
                }
            }
        }
    }
    if (BuildConfig.DEBUG && debugMenu) DebugMenu(onPick = { vm.loadDemo(it); debugMenu = false }, onWipe = { vm.eraseAll(); debugMenu = false }, onClose = { debugMenu = false })
}

/** Content no wider than [max], centred (medium/expanded widths: tablets, foldables, landscape). */
@Composable
private fun Centered(max: Dp = 640.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Box(Modifier.widthIn(max = max).fillMaxSize()) { content() } }
}

@Composable
fun CyclusSnackbarHost(state: SnackbarHostState) {
    val c = Cyclus.colors
    SnackbarHost(state) { data ->
        Snackbar(
            data, shape = RoundedCornerShape(16.dp), containerColor = c.ink, contentColor = c.bg,
            actionColor = if (c == DarkCyclus) Color(0xFFB3264E) else Color(0xFFFF9DB3),
        )
    }
}

@Composable
private fun DebugMenu(onPick: (SampleScenario) -> Unit, onWipe: () -> Unit, onClose: () -> Unit) {
    val labels = mapOf(
        SampleScenario.DAY12 to R.string.debug_sc_day12, SampleScenario.EXPECTED to R.string.debug_sc_expected,
        SampleScenario.PERIOD to R.string.debug_sc_period, SampleScenario.HBC to R.string.debug_sc_hbc,
        SampleScenario.CONCEIVE to R.string.debug_sc_conceive, SampleScenario.CONCEIVE_2WW to R.string.debug_sc_conceive2ww,
        SampleScenario.EMPTY to R.string.debug_sc_empty,
    )
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.debug_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.debug_body), color = Cyclus.colors.ink2, fontSize = 14.sp)
                for ((s, id) in labels) TextButton(onClick = { onPick(s) }, Modifier.fillMaxWidth()) { Text(stringResource(id)) }
                TextButton(onClick = onWipe, Modifier.fillMaxWidth()) { Text(stringResource(R.string.debug_wipe), color = Cyclus.colors.period) }
                val ctx = LocalContext.current
                val scope = rememberCoroutineScope()
                // records without the cyclus: prefix count as another app's (ownership rule 1): test data for the import path
                TextButton(onClick = {
                    scope.launch { runCatching { DebugHealth.writeExternal(ctx) } }
                    onClose()
                }, Modifier.fillMaxWidth()) { Text("Health Connect: externe Testdaten") }
                for (r in listOf(SmallWidgetReceiver::class.java, MediumWidgetReceiver::class.java)) {
                    TextButton(onClick = {
                        AppWidgetManager.getInstance(ctx).requestPinAppWidget(ComponentName(ctx, r), null, null)
                        onClose()
                    }, Modifier.fillMaxWidth()) { Text("Widget: ${r.simpleName.removeSuffix("WidgetReceiver")}") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.debug_close)) } },
    )
}
