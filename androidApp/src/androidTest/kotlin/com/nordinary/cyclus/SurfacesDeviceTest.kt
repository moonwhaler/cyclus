package com.nordinary.cyclus

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nordinary.cyclus.backup.BackupCoordinator
import com.nordinary.cyclus.backup.BackupKeyStore
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.notify.AlarmArmer
import com.nordinary.cyclus.notify.AlarmReceiver
import com.nordinary.cyclus.notify.NotifyFiles
import com.nordinary.cyclus.presenter.NotifyItem
import com.nordinary.cyclus.presenter.NotifyKeys
import com.nordinary.cyclus.presenter.NotifyKind
import com.nordinary.cyclus.presenter.NotifyPlan
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import com.nordinary.cyclus.store.AndroidDatabase
import com.nordinary.cyclus.store.StoreHolder
import com.nordinary.cyclus.store.StoreState
import com.nordinary.cyclus.surfaces.Shortcuts
import com.nordinary.cyclus.surfaces.SystemSurfaces
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Notifications, widget side file, shortcut and the action entry (slice 5). Emulator only. */
@RunWith(AndroidJUnit4::class)
class SurfacesDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.now().toEpochDay().toInt()

    @After fun cleanUp() {
        AlarmArmer.cancel(ctx)
        NotifyFiles.deleteAll(ctx)
        ctx.getSystemService(NotificationManager::class.java).cancelAll()
    }

    @Test fun armerArmsTheSingleAlarmFromTheFileAndCancels() {
        NotifyFiles.writePlan(ctx, NotifyPlan(listOf(NotifyItem(NotifyKind.PERIOD_SOON.key, today + 3, 9 * 60))), System.currentTimeMillis())
        AlarmArmer.arm(ctx)
        assertTrue(AlarmArmer.isArmed(ctx)) // same request code / action / flags as the armer
        val next = assertNotNull(AlarmArmer.next(ctx))
        val local = java.time.Instant.ofEpochMilli(next.toEpochMilliseconds()).atZone(java.time.ZoneId.systemDefault())
        // with widgets placed the next local midnight may come first; otherwise 09:00 three days ahead
        assertTrue(local.toLocalTime() == LocalTime.MIDNIGHT || (local.toLocalDate().toEpochDay().toInt() == today + 3 && local.hour == 9))
        AlarmArmer.cancel(ctx)
        assertFalse(AlarmArmer.isArmed(ctx))
    }

    @Test fun receiverPostsDiscreetPrivateNotificationOnce(): Unit = runBlocking {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val now = java.time.LocalTime.now()
        val minute = (now.hour * 60 + now.minute - 1).coerceAtLeast(0)
        NotifyFiles.writePlan(ctx, NotifyPlan(listOf(NotifyItem(NotifyKind.PERIOD_SOON.key, today, minute)), discreet = true), System.currentTimeMillis())
        NotifyFiles.setLastFired(ctx, System.currentTimeMillis() - 10 * 60_000L)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val id = 100 + NotifyKind.PERIOD_SOON.ordinal
        ctx.sendBroadcast(Intent(ctx, AlarmReceiver::class.java).setAction(AlarmArmer.ACTION_FIRE))
        val n = withTimeout(10_000) {
            var found: Notification? = null
            while (found == null) { found = nm.activeNotifications.firstOrNull { it.id == id }?.notification; kotlinx.coroutines.delay(200) }
            found!!
        }
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        val pub = assertNotNull(n.publicVersion)
        assertEquals(ctx.getString(R.string.nt_checkin), pub.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNull(pub.extras.getCharSequence(Notification.EXTRA_TEXT)) // nothing else on the lock screen
        assertEquals(ctx.getString(R.string.nt_checkin), n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()) // discreet
        // fired once: a second alarm right after shows nothing new
        nm.cancel(id)
        ctx.sendBroadcast(Intent(ctx, AlarmReceiver::class.java).setAction(AlarmArmer.ACTION_FIRE))
        kotlinx.coroutines.delay(2_000)
        assertTrue(nm.activeNotifications.none { it.id == id })
    }

    @Test fun actionsOnlyThroughTheNonExportedAlias() {
        val viaAlias = ActionEntry.intent(ctx, ActionEntry.PERIOD)
        assertEquals(ActionEntry.PERIOD, ActionEntry.actionOf(viaAlias))
        val viaLauncher = Intent().setComponent(ComponentName(ctx, MainActivity::class.java)).putExtra(ActionEntry.EXTRA, ActionEntry.PERIOD)
        assertNull(ActionEntry.actionOf(viaLauncher))
        assertNull(ActionEntry.actionOf(Intent(viaAlias).putExtra(ActionEntry.EXTRA, "erase")))
        val info = ctx.packageManager.getActivityInfo(ComponentName(ctx.packageName, ActionEntry.CLASS), 0)
        assertFalse(info.exported)
        // trust rule: no network permission in the installed app (WorkManager's is removed)
        val requested = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.INTERNET !in requested && Manifest.permission.ACCESS_NETWORK_STATE !in requested, requested.toString())
    }

    @Test fun sideFilesAreSealedAndLegacyPlaintextIsReadOnceThenRemoved() {
        NotifyFiles.deleteAll(ctx)
        val plan = NotifyPlan(listOf(NotifyItem(NotifyKind.PERIOD_SOON.key, today + 3, 9 * 60)))
        // an update from a version with plaintext files: still read, replaced by the sealed file on the next write
        val legacy = File(ctx.filesDir, "notify/plan.json").also { it.parentFile!!.mkdirs(); it.writeText(plan.encode()) }
        assertEquals(plan, NotifyFiles.readPlan(ctx))
        NotifyFiles.writePlan(ctx, plan, System.currentTimeMillis())
        assertFalse(legacy.exists())
        val sealed = File(ctx.filesDir, "notify/plan.sealed").readBytes().decodeToString()
        assertFalse(NotifyKind.PERIOD_SOON.key in sealed, "plan must not be readable on disk")
        assertEquals(plan, NotifyFiles.readPlan(ctx))
        NotifyFiles.deleteAll(ctx)
        assertEquals(NotifyPlan.EMPTY, NotifyFiles.readPlan(ctx))
    }

    @Test fun eraseRemovesSideFilesAlarmAndShortcutAndALateSyncCannotBringThemBack(): Unit = runBlocking {
        val keys = BackupKeyStore("cyclus_test_surfaces")
        val coord = BackupCoordinator(ctx, File(ctx.cacheDir, "surfaces-backup"), keys).also { it.files.ensureDir() }
        val holder = StoreHolder(ctx, coord, "st-erase.db")
        holder.ensureOpen()
        val first = withTimeout(20_000) { holder.state.first { it is StoreState.Ready } } as StoreState.Ready
        val d = SampleScenarios.generate(SampleScenario.DAY12, today)
        val snap = CycleSnapshot.compute(CycleData(d.logs, emptyMap(), today))
        val settings = mapOf(NotifyKeys.CONSENT to "1", NotifyKeys.WIDGET_DETAILS to "1", NotifyKeys.SHORTCUT to "1")

        assertTrue(SystemSurfaces.sync(ctx, coord, holder, first.repo, snap, settings))
        assertTrue(NotifyFiles.readPlan(ctx).items.isNotEmpty())
        assertNotNull(NotifyFiles.readWidget(ctx))
        assertTrue(AlarmArmer.isArmed(ctx))
        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(ctx).any { it.id == "log" })

        holder.erase()
        // a sync queued from the old model while erasing, and one after the new store opened: both dropped
        assertFalse(SystemSurfaces.sync(ctx, coord, holder, first.repo, snap, settings))
        val second = withTimeout(20_000) { holder.state.first { it is StoreState.Ready } } as StoreState.Ready
        assertFalse(SystemSurfaces.sync(ctx, coord, holder, first.repo, snap, settings))

        assertEquals(NotifyPlan.EMPTY, NotifyFiles.readPlan(ctx))
        assertNull(NotifyFiles.readWidget(ctx))
        assertFalse(AlarmArmer.isArmed(ctx))
        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(ctx).none { it.id == "log" })

        Shortcuts.apply(ctx, false)
        second.repo.close()
        AndroidDatabase.destroy(ctx, "st-erase.db"); keys.destroy(); File(ctx.cacheDir, "surfaces-backup").deleteRecursively()
    }
}
