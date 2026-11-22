package com.nordinary.cyclus.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nordinary.cyclus.MainActivity
import com.nordinary.cyclus.R
import com.nordinary.cyclus.presenter.NotifyChannel
import com.nordinary.cyclus.presenter.NotifyKind

/** Posts local notifications (spec §8.3). Texts are fixed sentences: never dates, numbers or symptoms. */
object Notifier {
    private fun channelId(c: NotifyChannel) = "cyclus.${c.name.lowercase()}"
    private fun channelName(c: NotifyChannel) = when (c) {
        NotifyChannel.PERIOD -> R.string.ch_period
        NotifyChannel.FERTILITY -> R.string.ch_fertility
        NotifyChannel.REMINDERS -> R.string.ch_reminders
        NotifyChannel.BACKUP -> R.string.ch_backup
    }

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        for (c in NotifyChannel.entries) {
            val ch = NotificationChannel(channelId(c), ctx.getString(channelName(c)), NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    fun channelSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    fun canPost(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun discreetTitle(k: NotifyKind) = when (k) {
        NotifyKind.PERIOD_SOON, NotifyKind.LATER, NotifyKind.WINDOW_OPENS -> R.string.nt_checkin
        NotifyKind.DAILY_LOG, NotifyKind.LH_TEST, NotifyKind.BBT_MORNING -> R.string.nt_reminder
        NotifyKind.BACKUP -> R.string.nt_backup
    }

    fun detailText(k: NotifyKind) = when (k) {
        NotifyKind.PERIOD_SOON -> R.string.nd_period_soon
        NotifyKind.LATER -> R.string.nd_later
        NotifyKind.DAILY_LOG -> R.string.nd_daily
        NotifyKind.BACKUP -> R.string.nd_backup
        NotifyKind.LH_TEST -> R.string.nd_lh
        NotifyKind.WINDOW_OPENS -> R.string.nd_window
        NotifyKind.BBT_MORNING -> R.string.nd_bbt
    }

    fun build(ctx: Context, k: NotifyKind, discreet: Boolean): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val channel = channelId(k.channel)
        val color = ContextCompat.getColor(ctx, R.color.notify_accent)
        // what the lock screen shows (and every screen in discreet mode)
        val public = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notify).setColor(color)
            .setContentTitle(ctx.getString(discreetTitle(k)))
            .build()
        return NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notify).setColor(color)
            .setContentTitle(ctx.getString(if (discreet) discreetTitle(k) else R.string.nt_detailed_title))
            .setContentText(ctx.getString(if (discreet) R.string.nd_discreet else detailText(k)))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    @SuppressLint("MissingPermission") // checked by canPost
    fun post(ctx: Context, k: NotifyKind, discreet: Boolean) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        NotificationManagerCompat.from(ctx).notify(100 + k.ordinal, build(ctx, k, discreet))
    }

    fun cancelAll(ctx: Context) = NotificationManagerCompat.from(ctx).cancelAll()
}
