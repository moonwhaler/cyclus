package com.nordinary.cyclus.backup

import java.io.File
import java.io.InputStream

/** What one backup run will do. The agent turns [Send] into `fullBackupFile` calls; everything else writes nothing. */
sealed interface BackupDecision {
    val outcome: String
    data class Empty(override val outcome: String) : BackupDecision
    data class Failed(override val outcome: String = "failed", val reason: String) : BackupDecision
    class Send(val snapshot: File, val key: ByteArray, val transport: String) : BackupDecision { override val outcome = "sent" }
}

/**
 * Decides a backup run from files only (never the database). Everything is validated **before** the first byte is
 * handed to the transport, so a failing run sends nothing rather than a partial backup.
 */
class BackupPlanner(private val files: BackupFiles, private val unwrap: (ByteArray) -> ByteArray = BackupKeyStore()::unwrap) {
    fun decide(clientSideEncryption: Boolean, deviceTransfer: Boolean): BackupDecision {
        if (files.deviceOnly.exists()) return BackupDecision.Empty("skipped-device-only")
        if (!clientSideEncryption && !deviceTransfer) return BackupDecision.Empty("skipped-no-e2e")
        if (!files.snapshot.exists()) {
            return if (files.snapshotAbsent.exists()) BackupDecision.Empty("skipped-empty")
            else BackupDecision.Failed(reason = "snapshot missing")
        }
        val key = try {
            unwrap(files.keyWrapped.readBytes())
        } catch (e: Exception) {
            return BackupDecision.Failed(reason = "backup key unavailable: ${e.javaClass.simpleName}")
        }
        return try {
            CyclusFile.openWithKey(files.snapshot.readBytes(), key) // full authenticity check
            BackupDecision.Send(files.snapshot, key, if (clientSideEncryption) "e2e" else "d2d")
        } catch (e: Exception) {
            key.fill(0)
            BackupDecision.Failed(reason = "snapshot unreadable: ${e.javaClass.simpleName}")
        }
    }

    fun writeStatus(decision: BackupDecision, nowMillis: Long) {
        val transport = (decision as? BackupDecision.Send)?.transport ?: "none"
        val json = """{"at":$nowMillis,"transport":"$transport","outcome":"${decision.outcome}"}"""
        runCatching { files.writeAtomic(files.status, json.encodeToByteArray()) }
    }
}

/**
 * Receives restored files (agent `onRestoreFile`). The backup key is read from the stream into memory and wrapped
 * for this device straight away: a plaintext key never touches the disk. Nothing touches the live snapshot/key.
 */
class RestoreSink(private val files: BackupFiles, private val wrap: (ByteArray) -> ByteArray = BackupKeyStore()::wrap) {
    /** Returns true if the file was consumed. [name] is the restored file's name inside `files/backup/`. */
    fun accept(name: String, size: Long, input: InputStream): Boolean {
        when (name) {
            files.snapshot.name -> {
                if (size > CyclusFile.MAX_FILE_BYTES) return false
                files.writeAtomic(files.restoreSnapshot, input.readNBytesCompat(size.toInt()))
            }
            files.keyPlain.name -> {
                if (size != 32L) return false
                val key = input.readNBytesCompat(32)
                try { files.writeAtomic(files.restoreKey, wrap(key)) } finally { key.fill(0) }
            }
            else -> return false
        }
        return true
    }

    /** Marks a complete restore (both parts) for import on next launch. */
    fun finish() {
        if (files.restoreSnapshot.exists() && files.restoreKey.exists()) files.touch(files.restorePending)
    }
}

private fun InputStream.readNBytesCompat(n: Int): ByteArray {
    val out = ByteArray(n)
    var off = 0
    while (off < n) {
        val r = read(out, off, n - off)
        if (r < 0) throw java.io.EOFException("restore stream ended early")
        off += r
    }
    return out
}
