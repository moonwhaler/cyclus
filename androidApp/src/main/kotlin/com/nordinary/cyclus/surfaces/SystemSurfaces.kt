package com.nordinary.cyclus.surfaces

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.nordinary.cyclus.ActionEntry
import com.nordinary.cyclus.CyclusApplication
import com.nordinary.cyclus.R
import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.notify.AlarmArmer
import com.nordinary.cyclus.notify.Notifier
import com.nordinary.cyclus.notify.NotifyFiles
import com.nordinary.cyclus.presenter.NotificationPlanner
import com.nordinary.cyclus.presenter.NotifyKeys
import com.nordinary.cyclus.presenter.NotifyPrefs
import com.nordinary.cyclus.presenter.WidgetSnapshot
import com.nordinary.cyclus.backup.BackupCoordinator
import com.nordinary.cyclus.store.CyclusRepository
import com.nordinary.cyclus.store.StoreHolder
import com.nordinary.cyclus.store.StoreState
import com.nordinary.cyclus.widget.CyclusWidgets

/**
 * Keeps notifications, widgets and the shortcut in line with the data (ANDROID_PLAN §6.2/§6.3). Planning happens
 * only here, in the app with the database open; the alarm side reads the result from side files.
 */
object SystemSurfaces {
    /**
     * Runs under the same lock as erase/import and only for the repository that is still current, so a sync
     * queued before an erase can never re-create the erased person's side files.
     */
    suspend fun sync(app: CyclusApplication, repo: CyclusRepository, snap: CycleSnapshot, settings: Map<String, String>) =
        sync(app, app.backup, app.store, repo, snap, settings)

    suspend fun sync(
        app: Context, backup: BackupCoordinator, store: StoreHolder,
        repo: CyclusRepository, snap: CycleSnapshot, settings: Map<String, String>,
    ): Boolean {
        var widgetChanged = false
        var ran = false
        backup.exclusive {
            if ((store.state.value as? StoreState.Ready)?.repo !== repo) return@exclusive
            ran = true
            val prefs = NotifyPrefs.from(settings)
            val plan = NotificationPlanner.plan(snap, prefs, settings[SettingKeys.EXPORT_LAST]?.toIntOrNull())
            NotifyFiles.writePlan(app, plan, System.currentTimeMillis())
            widgetChanged = NotifyFiles.writeWidget(app, if (settings[NotifyKeys.WIDGET_DETAILS] == "1") WidgetSnapshot.from(snap) else null)
            AlarmArmer.arm(app)
            Shortcuts.apply(app, settings[NotifyKeys.SHORTCUT] == "1")
        }
        if (widgetChanged) CyclusWidgets.refresh(app)
        return ran
    }

    /** Erase (crypto-shred): no reminder, widget day or shortcut survives. Call inside `backup.exclusive`. */
    fun clearLocked(ctx: Context) {
        AlarmArmer.cancel(ctx)
        NotifyFiles.deleteAll(ctx)
        Shortcuts.apply(ctx, false)
        Notifier.cancelAll(ctx)
    }
}

/** Opt-in launcher shortcut „Eintragen“ (ANDROID_PLAN §6.2: none by default). */
object Shortcuts {
    private const val ID = "log"

    fun apply(ctx: Context, on: Boolean) {
        val present = ShortcutManagerCompat.getDynamicShortcuts(ctx).any { it.id == ID }
        if (on && !present) {
            ShortcutManagerCompat.pushDynamicShortcut(
                ctx,
                ShortcutInfoCompat.Builder(ctx, ID)
                    .setShortLabel(ctx.getString(R.string.sc_log))
                    .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                    .setIntent(ActionEntry.intent(ctx, ActionEntry.LOG))
                    .build(),
            )
        } else if (!on && present) ShortcutManagerCompat.removeDynamicShortcuts(ctx, listOf(ID))
    }
}

/** Quick Settings tile „Cyclus · Eintragen“: device unlock (system), then the Log sheet behind the app lock. */
class LogTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            label = getString(R.string.app_name)
            subtitle = getString(R.string.tile_sub)
            icon = Icon.createWithResource(this@LogTileService, R.drawable.ic_notify)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    /**
     * `startActivityAndCollapse` asks for the device unlock itself when the tile is used on the lock screen; wrapping it
     * in `unlockAndRun` makes SystemUI drop the start after unlocking (seen on API 36). The app lock still applies.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = ActionEntry.intent(this, ActionEntry.LOG)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(this, 2, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
