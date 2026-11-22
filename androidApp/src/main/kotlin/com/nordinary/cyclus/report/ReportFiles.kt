package com.nordinary.cyclus.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * The one place a report PDF exists as a file: `cacheDir/share/` (inside the app container, never backed up).
 * The same file is previewed and shared. It is removed at the next build, at app start and on erase — not when the
 * share sheet returns, because the target app may read it later.
 */
object ReportFiles {
    fun dir(context: Context) = File(context.cacheDir, "share")

    fun clear(context: Context) { dir(context).deleteRecursively() }

    fun write(context: Context, name: String, bytes: ByteArray): File {
        clear(context)
        val d = dir(context).apply { mkdirs() }
        return File(d, name).apply { writeBytes(bytes) }
    }

    fun shareIntent(context: Context, file: File, title: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
