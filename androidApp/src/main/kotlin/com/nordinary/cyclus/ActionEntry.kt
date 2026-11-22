package com.nordinary.cyclus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Entry for widget, Quick Settings tile and shortcut actions: the non-exported `<activity-alias>` `.ActionEntry`
 * of [MainActivity]. Only intents addressed to it carry actions; the exported launcher entry ignores them, so other
 * apps can't trigger writes (ANDROID_PLAN §7). Actions run after the app lock, exactly once.
 */
object ActionEntry {
    /** Alias name, resolved against the namespace (not the debug applicationId). */
    const val CLASS = "com.nordinary.cyclus.ActionEntry"
    const val EXTRA = "cyclus.action"
    const val LOG = "log"
    const val PERIOD = "period"

    fun intent(ctx: Context, action: String): Intent =
        Intent(Intent.ACTION_VIEW).setComponent(ComponentName(ctx.packageName, CLASS))
            .setData("cyclus://action/$action".toUri()) // distinct PendingIntents per action
            .putExtra(EXTRA, action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** The action of [i] if it came through the alias, else null. */
    fun actionOf(i: Intent?): String? {
        if (i?.component?.className != CLASS) return null
        return i.getStringExtra(EXTRA)?.takeIf { it == LOG || it == PERIOD }
    }
}
