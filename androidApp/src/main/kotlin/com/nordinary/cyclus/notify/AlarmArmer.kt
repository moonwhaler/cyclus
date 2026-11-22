package com.nordinary.cyclus.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nordinary.cyclus.presenter.NotifySchedule
import com.nordinary.cyclus.widget.CyclusWidgets
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Arms the single next alarm from `plan.json` (ANDROID_PLAN §6.3). Never opens the database: it runs from
 * receivers while the phone may be locked. Inexact (`setAndAllowWhileIdle`), no exact-alarm permission.
 */
object AlarmArmer {
    const val ACTION_FIRE = "FIRE"
    private const val REQUEST = 1

    fun pendingIntent(ctx: Context, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(
            ctx, REQUEST, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Next fire time, or null when nothing is planned and no widget needs a midnight refresh. */
    fun next(ctx: Context, now: Instant = Clock.System.now()): Instant? =
        NotifySchedule.nextFire(NotifyFiles.readPlan(ctx), now, TimeZone.currentSystemDefault(), midnight = CyclusWidgets.anyPlaced(ctx))

    fun arm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = next(ctx)
        if (at == null) { cancel(ctx); return }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilliseconds(), pendingIntent(ctx)!!)
    }

    fun cancel(ctx: Context) {
        pendingIntent(ctx, PendingIntent.FLAG_NO_CREATE)?.let { pi ->
            ctx.getSystemService(AlarmManager::class.java).cancel(pi)
            pi.cancel()
        }
    }

    fun isArmed(ctx: Context) = pendingIntent(ctx, PendingIntent.FLAG_NO_CREATE) != null
}
