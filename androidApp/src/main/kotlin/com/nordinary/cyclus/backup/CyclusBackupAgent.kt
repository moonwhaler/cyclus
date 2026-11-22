package com.nordinary.cyclus.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream

/**
 * Full-backup agent (ANDROID_PLAN §6.4). Sends the encrypted snapshot + backup key **only** when the transport is
 * end-to-end encrypted (client-side encryption, Android 9+) or a device-to-device transfer, and never the database,
 * its key or preferences. Runs in a restricted process: no Application, no database — files only ([BackupFiles]).
 */
class CyclusBackupAgent : BackupAgent() {
    private val files by lazy { BackupFiles(filesDir) }

    override fun onFullBackup(data: FullBackupDataOutput) {
        val flags = data.transportFlags
        val planner = BackupPlanner(files)
        val decision = planner.decide(
            clientSideEncryption = flags and FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED != 0,
            deviceTransfer = flags and FLAG_DEVICE_TO_DEVICE_TRANSFER != 0,
        )
        if (decision is BackupDecision.Send) {
            try {
                files.writeAtomic(files.keyPlain, decision.key)
                fullBackupFile(decision.snapshot, data)
                fullBackupFile(files.keyPlain, data)
            } finally {
                files.keyPlain.delete()
                decision.key.fill(0)
            }
        }
        planner.writeStatus(decision, System.currentTimeMillis())
    }

    override fun onRestoreFile(data: ParcelFileDescriptor, size: Long, destination: File, type: Int, mode: Long, mtime: Long) {
        val inBackupDir = destination.parentFile?.canonicalPath == files.dir.canonicalPath
        FileInputStream(data.fileDescriptor).use { input ->
            val consumed = inBackupDir && runCatching { RestoreSink(files).accept(destination.name, size, input) }.getOrDefault(false)
            if (!consumed) drain(input, size) // keep the restore stream aligned; unknown files are ignored
        }
    }

    override fun onRestoreFinished() { RestoreSink(files).finish() }

    // Key/value backup is not used (fullBackupOnly).
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) {}
    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {}

    private fun drain(input: FileInputStream, size: Long) {
        val buf = ByteArray(8192)
        var left = size
        while (left > 0) { val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (r < 0) break; left -= r }
    }
}
