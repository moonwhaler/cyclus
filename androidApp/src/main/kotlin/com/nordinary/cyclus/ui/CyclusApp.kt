package com.nordinary.cyclus.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordinary.cyclus.R
import com.nordinary.cyclus.core.DayLog
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.i18n.rememberFmt
import com.nordinary.cyclus.presenter.TodayPresenter
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.ui.common.CyclusCard
import com.nordinary.cyclus.ui.common.CyclusIcons
import com.nordinary.cyclus.ui.log.LogSheet
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.today.TodayContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Tab(val key: String) { TODAY("tabToday"), CALENDAR("tabCal"), INSIGHTS("tabIns") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun CyclusApp(vm: AppViewModel, debug: Boolean) {
    val c = Cyclus.colors
    val fmt = rememberFmt()
    val model by vm.model.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var logDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var logChanged by rememberSaveable { mutableStateOf(false) }
    var debugMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.undo_label)
    val soon = stringResource(R.string.soon_title)

    LaunchedEffect(vm, fmt) {
        vm.messages.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            launch {
                if (msg.undo != null) {
                    launch { delay(5_000); snackbar.currentSnackbarData?.dismiss() }
                    val r = snackbar.showSnackbar(fmt.s(msg.key), actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
                    if (r == SnackbarResult.ActionPerformed) vm.undo(msg)
                } else snackbar.showSnackbar(fmt.s(msg.key), duration = SnackbarDuration.Short)
            }
        }
    }
    fun comingSoon() { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(soon) } }

    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = androidx.compose.material3.NavigationBarItemDefaults.colors(
            selectedIconColor = c.period, selectedTextColor = c.period, indicatorColor = c.period.copy(alpha = .14f),
            unselectedIconColor = c.ink2, unselectedTextColor = c.ink2,
        ),
        navigationRailItemColors = androidx.compose.material3.NavigationRailItemDefaults.colors(
            selectedIconColor = c.period, selectedTextColor = c.period, indicatorColor = c.period.copy(alpha = .14f),
            unselectedIconColor = c.ink2, unselectedTextColor = c.ink2,
        ),
    )
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            for (t in Tab.entries) item(
                selected = tab == t, onClick = { tab = t },
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
                        Text(
                            fmt.s(tab.key),
                            Modifier.combinedClickable(onClick = {}, onLongClick = { if (debug) debugMenu = true }),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    subtitle = { Text(fmt.wmd(model?.snapshot?.today ?: java.time.LocalDate.now().toEpochDay().toInt()), color = c.ink2) },
                    actions = {
                        IconButton(onClick = ::comingSoon) { Icon(CyclusIcons.Shield, fmt.s("aPrivacy"), tint = c.ink) }
                        IconButton(onClick = ::comingSoon) { Icon(CyclusIcons.Gear, fmt.s("aSettings"), tint = c.ink) }
                    },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, scrolledContainerColor = c.bg),
                )
            },
            floatingActionButton = {
                if (model != null) ExtendedFloatingActionButton(
                    onClick = { logDay = model!!.snapshot.today; logChanged = false },
                    icon = { Icon(CyclusIcons.Plus, null) },
                    text = { Text(fmt.s("fab"), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                    containerColor = c.surface, contentColor = c.period,
                )
            },
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    androidx.compose.material3.Snackbar(
                        data, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        containerColor = c.ink, contentColor = c.bg,
                        actionColor = if (c == com.nordinary.cyclus.ui.theme.DarkCyclus) androidx.compose.ui.graphics.Color(0xFFB3264E) else androidx.compose.ui.graphics.Color(0xFFFF9DB3),
                    )
                }
            },
        ) { padding ->
            val m = model
            when {
                m == null -> {}
                tab == Tab.TODAY -> TodayContent(
                    state = m.today, fmt = fmt, contentPadding = padding,
                    debugNote = if (debug) stringResource(R.string.debug_note) else null,
                    onOpenLog = { logDay = m.snapshot.today; logChanged = false },
                    onPeriodStarted = vm::periodStartedToday,
                    onQuickFlow = vm::quickFlow,
                    onPatterns = { tab = Tab.INSIGHTS },
                    onChangeStage = ::comingSoon,
                )
                else -> ComingSoon(padding)
            }
        }
    }

    val m = model
    val day = logDay
    if (m != null && day != null) {
        LogSheet(
            day = day, today = m.snapshot.today, log = m.logs[day] ?: DayLog.EMPTY, mode = m.mode,
            suggestions = remember(m, day) { TodayPresenter.suggestions(m.snapshot, day) }, fmt = fmt,
            onDayChange = { logDay = it },
            onUpdate = { t -> logChanged = true; vm.updateLog(day, t) },
            onDone = { logDay = null; if (logChanged) vm.saved(); logChanged = false },
            onDismiss = { logDay = null; if (logChanged) vm.saved(); logChanged = false },
        )
    }
    if (debugMenu) DebugMenu(fmt, onPick = { vm.loadDemo(it); debugMenu = false }, onWipe = { vm.eraseAll(); debugMenu = false }, onClose = { debugMenu = false })
}

@Composable
private fun ComingSoon(padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        CyclusCard {
            Text(stringResource(R.string.soon_title), fontWeight = FontWeight.SemiBold, color = Cyclus.colors.ink)
            Text(stringResource(R.string.soon_body), color = Cyclus.colors.ink2)
        }
    }
}

@Composable
private fun DebugMenu(fmt: Fmt, onPick: (SampleScenario) -> Unit, onWipe: () -> Unit, onClose: () -> Unit) {
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
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.debug_close)) } },
    )
}
