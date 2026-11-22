package com.nordinary.cyclus.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nordinary.cyclus.CyclusApplication
import com.nordinary.cyclus.R
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.lock.Authn
import com.nordinary.cyclus.presenter.LifeStage
import com.nordinary.cyclus.presenter.Onboarding
import com.nordinary.cyclus.ui.AppViewModel
import com.nordinary.cyclus.ui.common.CyclusChip
import com.nordinary.cyclus.ui.common.CyclusIcons
import com.nordinary.cyclus.ui.privacy.ImportSheet
import com.nordinary.cyclus.ui.privacy.PinSetupSheet
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.today.PrimaryButton
import com.nordinary.cyclus.ui.today.SecondaryButton
import java.time.LocalDate
import java.time.ZoneOffset

/** First run (spec §7.1, prototype `renderOnb`): ≤ 60 s, every step skippable, nothing invented. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(vm: AppViewModel, fmt: Fmt) {
    val c = Cyclus.colors
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CyclusApplication
    val choices by vm.onboardingChoices.collectAsStateWithLifecycle()
    val importStage by vm.importStage.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var pinSheet by remember { mutableStateOf(false) }
    var askDuration by remember { mutableStateOf(false) }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { vm.startImport(uri); importing = true }
    }
    // Health Connect import (reinstall recovery happens in the first sync, into this still-empty store)
    val hcAvailable = remember { vm.health.availability() == com.nordinary.cyclus.health.HealthAvailability.AVAILABLE }
    val hcLauncher = rememberLauncherForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { granted ->
        vm.healthConnected(granted)
        if (granted.isNotEmpty()) step = 4 // cycles come from Health Connect: no need to ask for the last period
    }

    Column(
        Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (step) {
            0 -> {
                Spacer(Modifier.height(48.dp))
                Icon(CyclusIcons.Today, null, Modifier.size(72.dp).align(Alignment.CenterHorizontally), tint = c.period)
                Title(fmt.s("onb0T"), center = true)
                Body(fmt.s("onb0B"), center = true)
                Spacer(Modifier.height(32.dp))
                PrimaryButton(fmt.s("getStarted"), { step = 1 }, Modifier.fillMaxWidth())
            }
            1 -> {
                Title(fmt.s("onb1T"))
                Promise(fmt.s("pr1"), fmt.s("pr1s")); Promise(fmt.s("pr2"), fmt.s("pr2s")); Promise(fmt.s("pr3"), fmt.s("pr3s"))
                Body(fmt.s("onb1N"), small = true)
                Dots(1); PrimaryButton(fmt.s("cont"), { step = 2 }, Modifier.fillMaxWidth())
            }
            2 -> {
                Title(fmt.s("onb2T")); Body(fmt.s("onb2B"))
                Dots(2)
                PrimaryButton(fmt.s("importBtn"), { app.lock.suspendForExternalActivity(); openDoc.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
                if (hcAvailable) SecondaryButton(
                    stringResource(com.nordinary.cyclus.R.string.onb_hc),
                    { app.lock.suspendForExternalActivity(); hcLauncher.launch(vm.health.readPermissions()) }, Modifier.fillMaxWidth(),
                )
                SecondaryButton(fmt.s("skip"), { step = 3 }, Modifier.fillMaxWidth())
            }
            3 -> {
                Title(fmt.s("onb3T"))
                val todayMs = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                val picker = rememberDatePickerState(
                    initialSelectedDateMillis = choices.lastPeriodStart?.let { it.toLong() * 86_400_000L },
                    selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMs },
                )
                DatePicker(picker, title = null, headline = null, showModeToggle = false, colors = DatePickerDefaults.colors(containerColor = c.bg))
                val picked = picker.selectedDateMillis?.let { (it / 86_400_000L).toInt() }
                androidx.compose.runtime.LaunchedEffect(picked) {
                    if (picked != null && picked != vm.onboardingChoices.value.lastPeriodStart) {
                        vm.onboardingChoices.value = vm.onboardingChoices.value.copy(lastPeriodStart = picked, stillBleeding = false, durationDays = null)
                    }
                }
                if (picked != null) {
                    Text(stringResource(R.string.onb_still), fontWeight = FontWeight.SemiBold, color = c.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CyclusChip(stringResource(R.string.onb_still_yes), choices.stillBleeding, { vm.onboardingChoices.value = choices.copy(stillBleeding = !choices.stillBleeding, durationDays = null); askDuration = false })
                        CyclusChip(stringResource(R.string.onb_still_no), askDuration, { askDuration = !askDuration; vm.onboardingChoices.value = choices.copy(stillBleeding = false) })
                    }
                    if (askDuration) {
                        Text(stringResource(R.string.onb_duration), color = c.ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (d in 1..Onboarding.MAX_SEED_DAYS) CyclusChip("$d", choices.durationDays == d, {
                                vm.onboardingChoices.value = choices.copy(durationDays = if (choices.durationDays == d) null else d)
                            })
                            CyclusChip(stringResource(R.string.onb_unknown), choices.durationDays == null, { vm.onboardingChoices.value = choices.copy(durationDays = null) })
                        }
                    }
                }
                Body(fmt.s("onb3B"), small = true)
                Dots(3)
                PrimaryButton(fmt.s("cont"), { step = 4 }, Modifier.fillMaxWidth())
                SecondaryButton(fmt.s("dontRemember"), { vm.onboardingChoices.value = choices.copy(lastPeriodStart = null, stillBleeding = false, durationDays = null); step = 4 }, Modifier.fillMaxWidth())
            }
            4 -> {
                Title(fmt.s("onb4T")); Body(fmt.s("onb4B"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CyclusChip(fmt.s("f_conceive"), choices.conceive, {
                        val on = !choices.conceive
                        vm.onboardingChoices.value = choices.copy(conceive = on, stage = if (on && !Onboarding.conceiveAllowed(choices.stage)) null else choices.stage)
                    })
                    for (st in LifeStage.entries) CyclusChip(fmt.s(st.labelKey), choices.stage == st, {
                        val next = if (choices.stage == st) null else st
                        vm.onboardingChoices.value = choices.copy(stage = next, conceive = choices.conceive && Onboarding.conceiveAllowed(next))
                    })
                    CyclusChip(fmt.s(LifeStage.NONE_LABEL), choices.stage == null && !choices.conceive, {
                        vm.onboardingChoices.value = choices.copy(stage = null, conceive = false)
                    })
                }
                Body(stringResource(R.string.onb_conceive_note), small = true)
                Dots(4); PrimaryButton(fmt.s("cont"), { step = 5 }, Modifier.fillMaxWidth())
            }
            else -> {
                Spacer(Modifier.height(32.dp))
                Icon(CyclusIcons.Shield, null, Modifier.size(64.dp).align(Alignment.CenterHorizontally), tint = c.period)
                Title(fmt.s("onb5T"), center = true); Body(fmt.s("onb5B"), center = true)
                Dots(5)
                PrimaryButton(fmt.s("turnOn"), { if (Authn.available(ctx)) vm.finishOnboarding(enableLock = true) else pinSheet = true }, Modifier.fillMaxWidth())
                SecondaryButton(fmt.s("notNow"), { vm.finishOnboarding(enableLock = false) }, Modifier.fillMaxWidth())
            }
        }
    }

    if (importing) ImportSheet(
        fmt, importStage, onOpen = vm::openImport,
        onApply = { replace ->
            vm.applyImport(replace) {
                importing = false
                // continue to the life-stage and lock steps; an import never sets up the app lock
                val m = vm.model.value
                step = if (m != null && (m.activeEpisode != null || m.settings.containsKey(com.nordinary.cyclus.backup.SettingKeys.MODE))) 5 else 4
            }
        },
        onDismiss = { vm.cancelImport(); importing = false },
    )
    if (pinSheet) PinSetupSheet(fmt, onSet = { pin -> pinSheet = false; vm.setPin(pin); vm.finishOnboarding(enableLock = true) }, onDismiss = { pinSheet = false })
}

@Composable
private fun Title(t: String, center: Boolean = false) =
    Text(t, Modifier.fillMaxWidth(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Cyclus.colors.ink, textAlign = if (center) TextAlign.Center else TextAlign.Start, lineHeight = 32.sp)

@Composable
private fun Body(t: String, center: Boolean = false, small: Boolean = false) =
    Text(t, Modifier.fillMaxWidth(), fontSize = if (small) 13.sp else 15.sp, color = Cyclus.colors.ink2, textAlign = if (center) TextAlign.Center else TextAlign.Start)

@Composable
private fun Promise(t: String, sub: String) {
    val c = Cyclus.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(c.success))
        Column { Text(t, fontWeight = FontWeight.SemiBold, color = c.ink); Text(sub, fontSize = 13.sp, color = c.ink2) }
    }
}

@Composable
private fun Dots(current: Int) {
    val c = Cyclus.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        for (i in 0..5) Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == current) c.ink else c.line))
    }
}
