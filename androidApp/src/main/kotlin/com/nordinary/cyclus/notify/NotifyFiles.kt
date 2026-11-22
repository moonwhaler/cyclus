package com.nordinary.cyclus.notify

import android.content.Context
import com.nordinary.cyclus.presenter.NotifyPlan
import com.nordinary.cyclus.presenter.WidgetSnapshot
import java.io.File

/**
 * Plaintext side files for the DB-free system surfaces (ANDROID_PLAN §6.3, §7). Credential-encrypted app storage,
 * never in backups (the backup rules are include-only). Reminder kinds + days only; widget days only while
 * „Details im Widget“ is on.
 */
object NotifyFiles {
    private fun notifyDir(ctx: Context) = File(ctx.filesDir, "notify")
    private fun widgetDir(ctx: Context) = File(ctx.filesDir, "widget")
    private fun planFile(ctx: Context) = File(notifyDir(ctx), "plan.json")
    private fun firedFile(ctx: Context) = File(notifyDir(ctx), "fired")
    private fun widgetFile(ctx: Context) = File(widgetDir(ctx), "snapshot.json")

    fun readPlan(ctx: Context): NotifyPlan = planFile(ctx).takeIf { it.exists() }?.let { NotifyPlan.decode(it.readText()) } ?: NotifyPlan.EMPTY

    /** Writes [plan] if it differs; a new plan starts counting from now (nothing before it is shown late). */
    fun writePlan(ctx: Context, plan: NotifyPlan, nowMillis: Long): Boolean {
        val text = plan.encode()
        val f = planFile(ctx)
        if (f.exists() && f.readText() == text) return false
        if (plan.items.isEmpty()) { notifyDir(ctx).deleteRecursively(); return true }
        notifyDir(ctx).mkdirs()
        atomicWrite(f, text)
        setLastFired(ctx, nowMillis)
        return true
    }

    fun lastFired(ctx: Context): Long? = firedFile(ctx).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
    fun setLastFired(ctx: Context, millis: Long) { if (notifyDir(ctx).exists()) atomicWrite(firedFile(ctx), millis.toString()) }

    fun readWidget(ctx: Context): WidgetSnapshot? = widgetFile(ctx).takeIf { it.exists() }?.let { WidgetSnapshot.decode(it.readText()) }

    /** Writes or (null) removes the widget snapshot; true if anything changed. */
    fun writeWidget(ctx: Context, snap: WidgetSnapshot?): Boolean {
        val f = widgetFile(ctx)
        if (snap == null) return widgetDir(ctx).exists().also { widgetDir(ctx).deleteRecursively() }
        val text = snap.encode()
        if (f.exists() && f.readText() == text) return false
        widgetDir(ctx).mkdirs()
        atomicWrite(f, text)
        return true
    }

    fun deleteAll(ctx: Context) { notifyDir(ctx).deleteRecursively(); widgetDir(ctx).deleteRecursively() }

    private fun atomicWrite(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}
