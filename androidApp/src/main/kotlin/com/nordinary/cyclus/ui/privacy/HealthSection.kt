package com.nordinary.cyclus.ui.privacy

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.nordinary.cyclus.CyclusApplication
import com.nordinary.cyclus.R
import com.nordinary.cyclus.health.HealthAvailability
import com.nordinary.cyclus.health.HealthCategory
import com.nordinary.cyclus.health.HealthType
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.ui.AppViewModel
import com.nordinary.cyclus.ui.HealthActions
import com.nordinary.cyclus.ui.theme.Cyclus
import com.nordinary.cyclus.ui.today.PrimaryButton

fun categoryLabel(c: HealthCategory) = when (c) {
    HealthCategory.BLEEDING -> R.string.hc_cat_bleeding
    HealthCategory.SPOTTING -> R.string.hc_cat_spotting
    HealthCategory.TEMPERATURE -> R.string.hc_cat_temp
    HealthCategory.MUCUS -> R.string.hc_cat_mucus
    HealthCategory.LH -> R.string.hc_cat_lh
    HealthCategory.SEX -> R.string.hc_cat_sex
}

fun typeLabel(t: HealthType) = when (t) {
    HealthType.FLOW, HealthType.PERIOD -> R.string.hc_type_flow
    HealthType.SPOTTING -> R.string.hc_type_spotting
    HealthType.BBT -> R.string.hc_type_bbt
    HealthType.MUCUS -> R.string.hc_type_mucus
    HealthType.LH -> R.string.hc_type_lh
    HealthType.SEX -> R.string.hc_type_sex
}

/** Privacy Center › Health Connect (spec §7.10, ANDROID_PLAN §6.1). Every write switch starts off. */
@Composable
fun HealthSection(vm: AppViewModel, fmt: Fmt, ui: HealthActions.HealthUi, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CyclusApplication
    val c = Cyclus.colors
    var pendingCategory by remember { mutableStateOf<HealthCategory?>(null) }
    var offDialog by remember { mutableStateOf<HealthCategory?>(null) }
    var review by remember { mutableStateOf(false) }

    val readLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        vm.health.connected(granted); onChanged()
    }
    val writeLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        pendingCategory?.let { cat -> if (granted.containsAll(vm.health.writePermissions(cat))) vm.health.setWrite(cat, true) }
        pendingCategory = null; onChanged()
    }
    fun external(block: () -> Unit) { app.lock.suspendForExternalActivity(); block() }

    Section(stringResource(R.string.hc_section)) {
        when {
            ui.availability == HealthAvailability.UNAVAILABLE -> Note(stringResource(R.string.hc_unavailable))
            ui.availability == HealthAvailability.NEEDS_INSTALL -> Group {
                Link(stringResource(R.string.hc_install), stringResource(R.string.hc_install_s), {
                    external {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding".toUri()).setPackage("com.android.vending"))
                        }
                    }
                })
            }
            !ui.connected -> {
                Note(stringResource(R.string.hc_intro))
                PrimaryButton(stringResource(R.string.hc_connect), { external { readLauncher.launch(vm.health.readPermissions()) } }, Modifier.fillMaxWidth())
            }
            else -> {
                if (ui.restoreOffer > 0) {
                    Group {
                        Info(stringResource(R.string.hc_restore_t), stringResource(R.string.hc_restore_b, ui.restoreOffer))
                        Divider()
                        Link(stringResource(R.string.hc_restore_yes), null, { vm.health.answerRestore(true); onChanged() })
                        Divider()
                        Link(stringResource(R.string.hc_restore_no), null, { vm.health.answerRestore(false); onChanged() })
                    }
                }
                Group {
                    val status = when {
                        ui.error -> stringResource(R.string.hc_status_error)
                        ui.lastSync == null -> stringResource(R.string.hc_status_never)
                        else -> stringResource(
                            R.string.hc_status,
                            DateUtils.getRelativeTimeSpanString(ui.lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                            ui.imported.toInt(),
                        )
                    }
                    Info(status, if (ui.noHistory) stringResource(R.string.hc_no_history) else null)
                    Divider()
                    Link(stringResource(R.string.hc_sync_now), null, { vm.health.syncNow(); onChanged() })
                    if (ui.removed.isNotEmpty()) {
                        Divider()
                        Link(stringResource(R.string.hc_removed, ui.removed.size), stringResource(R.string.hc_review), { review = true })
                    }
                }
                Text(stringResource(R.string.hc_write_head).uppercase(), Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = c.ink2)
                Group {
                    HealthCategory.entries.forEachIndexed { i, cat ->
                        if (i > 0) Divider()
                        val on = cat in ui.enabled && ui.granted.containsAll(vm.health.writePermissions(cat))
                        SwitchRow(stringResource(categoryLabel(cat)), null, on) { want ->
                            if (want) {
                                if (ui.granted.containsAll(vm.health.writePermissions(cat))) { vm.health.setWrite(cat, true); onChanged() }
                                else { pendingCategory = cat; external { writeLauncher.launch(vm.health.writePermissions(cat)) } }
                            } else offDialog = cat
                        }
                    }
                }
                Note(stringResource(R.string.hc_write_note))
                Note(stringResource(R.string.hc_only_cyclus))
                Group {
                    Link(stringResource(R.string.hc_manage), null, {
                        external { runCatching { ctx.startActivity(HealthConnectClient.getHealthConnectManageDataIntent(ctx)) } }
                    })
                    Divider()
                    Link(stringResource(R.string.hc_disconnect), stringResource(R.string.hc_disconnect_s), { vm.health.disconnect(); onChanged() }, danger = true)
                }
            }
        }
    }

    offDialog?.let { cat ->
        AlertDialog(
            onDismissRequest = { offDialog = null },
            title = { Text(stringResource(R.string.hc_off_t)) },
            text = { Text(stringResource(R.string.hc_off_b)) },
            confirmButton = { TextButton({ vm.health.setWrite(cat, false, deleteThere = true); vm.health.syncNow(); offDialog = null; onChanged() }) { Text(stringResource(R.string.hc_off_delete)) } },
            dismissButton = { TextButton({ vm.health.setWrite(cat, false); offDialog = null; onChanged() }) { Text(stringResource(R.string.hc_off_keep)) } },
            containerColor = c.surface,
        )
    }
    if (review) {
        AlertDialog(
            onDismissRequest = { review = false },
            title = { Text(stringResource(R.string.hc_review_t)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.hc_review_b))
                    for (l in ui.removed.take(12)) Text("• " + stringResource(typeLabel(l.type)) + " · " + fmt.md(l.day), fontSize = 14.sp, color = c.ink2)
                    if (ui.removed.size > 12) Text("…", color = c.ink2)
                }
            },
            confirmButton = { TextButton({ vm.health.reviewRemoved(ui.removed, keep = true); review = false; onChanged() }) { Text(stringResource(R.string.hc_keep)) } },
            dismissButton = { TextButton({ vm.health.reviewRemoved(ui.removed, keep = false); review = false; onChanged() }) { Text(stringResource(R.string.hc_remove)) } },
            containerColor = c.surface,
        )
    }
}
