package com.nordinary.cyclus.backup

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Files are the only state the backup agent reads (it runs in a restricted process, often while the phone is locked,
 * when the encrypted database can't be opened). Layout under `files/backup/`:
 * - `snapshot.cyclus`  mode-2 `.cyclus` of all data (raw backup key)
 * - `key.wrapped`      backup key wrapped by the Keystore key [BackupKeyStore.ALIAS]
 * - `key.plain`        transient copy of the backup key, exists only while the agent streams it into a backup
 * - `.device-only`     „Nur auf dem Gerät“ is on
 * - `snapshot.absent`  there is intentionally no snapshot (fresh install / erased)
 * - `status.json`      last agent run (for the Privacy Center)
 * - `restore/`         restored snapshot + key (wrapped for this device) until imported; `restore/pending` marker
 */
class BackupFiles(filesDir: File) {
    val dir = File(filesDir, "backup")
    val snapshot = File(dir, "snapshot.cyclus")
    val keyWrapped = File(dir, "key.wrapped")
    val keyPlain = File(dir, "key.plain")
    val deviceOnly = File(dir, ".device-only")
    val snapshotAbsent = File(dir, "snapshot.absent")
    val status = File(dir, "status.json")
    val restoreDir = File(dir, "restore")
    val restoreSnapshot = File(restoreDir, "snapshot.cyclus")
    val restoreKey = File(restoreDir, "key.wrapped")
    val restorePending = File(restoreDir, "pending")

    fun ensureDir() { dir.mkdirs() }

    /** Atomic write: tmp file, fsync, rename. */
    fun writeAtomic(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out -> out.write(bytes); out.fd.sync() }
        if (!tmp.renameTo(target)) { tmp.delete(); throw IOException("rename failed: ${target.name}") }
    }

    fun touch(f: File) { if (!f.exists()) writeAtomic(f, ByteArray(0)) }
}
