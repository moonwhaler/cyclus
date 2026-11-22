package com.nordinary.cyclus.notify

import android.content.Context
import com.nordinary.cyclus.presenter.NotifyPlan
import com.nordinary.cyclus.presenter.WidgetSnapshot
import java.io.File

/**
 * Side files for the DB-free system surfaces (ANDROID_PLAN §6.3, §7): the reminder plan (kinds + days) and, only while
 * „Details im Widget“ is on, the widget days. They are read by receivers and widgets while the phone may be locked, when
 * the database can't be opened, so they are sealed with their own hardware Keystore key (AES-GCM) that doesn't need an
 * unlocked device — same threat model as the backup key. Never in backups (the backup rules are include-only); erase
 * deletes the files and the key.
 */
object NotifyFiles {
    const val KEY_ALIAS = "cyclus_side_kek_v1"
    private val keys = com.nordinary.cyclus.backup.BackupKeyStore(KEY_ALIAS)

    private fun notifyDir(ctx: Context) = File(ctx.filesDir, "notify")
    private fun widgetDir(ctx: Context) = File(ctx.filesDir, "widget")
    private fun planFile(ctx: Context) = File(notifyDir(ctx), "plan.sealed")
    private fun firedFile(ctx: Context) = File(notifyDir(ctx), "fired")
    private fun widgetFile(ctx: Context) = File(widgetDir(ctx), "snapshot.sealed")
    // plaintext files of earlier versions: still read once after an update, removed on the next write
    private fun legacyPlan(ctx: Context) = File(notifyDir(ctx), "plan.json")
    private fun legacyWidget(ctx: Context) = File(widgetDir(ctx), "snapshot.json")

    private fun open(f: File): String? = runCatching { keys.unwrap(f.readBytes()).decodeToString() }.getOrNull()
    private fun read(f: File, legacy: File): String? = when {
        f.exists() -> open(f)
        legacy.exists() -> runCatching { legacy.readText() }.getOrNull()
        else -> null
    }

    fun readPlan(ctx: Context): NotifyPlan = read(planFile(ctx), legacyPlan(ctx))?.let { NotifyPlan.decode(it) } ?: NotifyPlan.EMPTY

    /** Writes [plan] if it differs; a new plan starts counting from now (nothing before it is shown late). */
    fun writePlan(ctx: Context, plan: NotifyPlan, nowMillis: Long): Boolean {
        val text = plan.encode()
        val f = planFile(ctx)
        if (f.exists() && open(f) == text) return false
        if (plan.items.isEmpty()) { notifyDir(ctx).deleteRecursively(); return true }
        notifyDir(ctx).mkdirs()
        atomicWrite(f, keys.wrap(text.encodeToByteArray()))
        legacyPlan(ctx).delete()
        setLastFired(ctx, nowMillis)
        return true
    }

    fun lastFired(ctx: Context): Long? = firedFile(ctx).takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull()
    fun setLastFired(ctx: Context, millis: Long) { if (notifyDir(ctx).exists()) atomicWrite(firedFile(ctx), millis.toString().encodeToByteArray()) }

    fun readWidget(ctx: Context): WidgetSnapshot? = read(widgetFile(ctx), legacyWidget(ctx))?.let { WidgetSnapshot.decode(it) }

    /** Writes or (null) removes the widget snapshot; true if anything changed. */
    fun writeWidget(ctx: Context, snap: WidgetSnapshot?): Boolean {
        val f = widgetFile(ctx)
        if (snap == null) return widgetDir(ctx).exists().also { widgetDir(ctx).deleteRecursively() }
        val text = snap.encode()
        if (f.exists() && open(f) == text) return false
        widgetDir(ctx).mkdirs()
        atomicWrite(f, keys.wrap(text.encodeToByteArray()))
        legacyWidget(ctx).delete()
        return true
    }

    /** Erase: the files and the key that sealed them. */
    fun deleteAll(ctx: Context) { notifyDir(ctx).deleteRecursively(); widgetDir(ctx).deleteRecursively(); keys.destroy() }

    private fun atomicWrite(f: File, bytes: ByteArray) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        java.io.FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}
