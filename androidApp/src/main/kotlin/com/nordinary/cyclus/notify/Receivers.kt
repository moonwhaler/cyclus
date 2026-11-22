package com.nordinary.cyclus.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nordinary.cyclus.presenter.NotifySchedule
import com.nordinary.cyclus.widget.CyclusWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/** Rule: receivers never open the database (its key needs an unlocked device); they only read the side files. */
private fun BroadcastReceiver.async(ctx: Context, block: suspend (Context) -> Unit) {
    val pending = goAsync()
    val app = ctx.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
        try { block(app) } finally { pending.finish() }
    }
}

/** The single armed alarm: posts what is due, refreshes widgets (midnight), arms the next one. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmArmer.ACTION_FIRE) return
        async(context) { ctx ->
            val now = Clock.System.now()
            val plan = NotifyFiles.readPlan(ctx)
            val last = NotifyFiles.lastFired(ctx)?.let { Instant.fromEpochMilliseconds(it) }
            for (k in NotifySchedule.due(plan, last, now, TimeZone.currentSystemDefault())) Notifier.post(ctx, k, plan.discreet)
            NotifyFiles.setLastFired(ctx, now.toEpochMilliseconds())
            CyclusWidgets.refresh(ctx)
            AlarmArmer.arm(ctx)
        }
    }
}

/** Boot, update, clock and time-zone changes: re-arm from the file in the current zone. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        async(context) { ctx ->
            CyclusWidgets.refresh(ctx)
            AlarmArmer.arm(ctx)
        }
    }

    companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED,
        )
    }
}
