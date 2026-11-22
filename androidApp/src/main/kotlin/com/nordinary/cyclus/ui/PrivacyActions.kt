package com.nordinary.cyclus.ui

import android.net.Uri
import com.nordinary.cyclus.R
import com.nordinary.cyclus.backup.CyclusFile
import com.nordinary.cyclus.backup.CyclusFileException
import com.nordinary.cyclus.backup.ExportFile
import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.backup.preview
import com.nordinary.cyclus.lock.LockConfig
import com.nordinary.cyclus.security.Pin
import com.nordinary.cyclus.ui.privacy.ImportStage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Privacy Center actions: device backup, app lock / PIN, encrypted export and import, erase. */
class PrivacyActions internal constructor(private val c: VmContext) {
    private val repo = c.repo
    private val app = c.app

    /** „Nur auf dem Gerät“ (file marker, not a DB setting). */
    val deviceOnly = MutableStateFlow(app.backup.deviceOnly)



    fun setDeviceOnly(on: Boolean) {
        deviceOnly.value = on
        c.launch { app.backup.setDeviceOnly(on, repo, app.appLabel()) }
    }

    fun backupStatus() = app.backup.status()

    fun setPin(pin: String) {
        c.launch(Dispatchers.Default) {
            val (salt, hash) = Pin.hash(pin)
            repo.setSetting(SettingKeys.LOCK_PIN_SALT, salt)
            repo.setSetting(SettingKeys.LOCK_PIN_HASH, hash)
        }
    }

    fun removePin() {
        c.launch {
            repo.setSetting(SettingKeys.LOCK_PIN_HASH, null); repo.setSetting(SettingKeys.LOCK_PIN_SALT, null)
            repo.setSetting(SettingKeys.LOCK_PIN_ONLY, null)
        }
    }

    /** Verifies a PIN on the lock screen. PBKDF2 (200k rounds) runs on a background thread, never on the main thread. */
    suspend fun checkPin(pin: String, config: LockConfig): Boolean {
        val ok = withContext(Dispatchers.Default) {
            config.pinSalt != null && config.pinHash != null && Pin.verify(pin, config.pinSalt, config.pinHash)
        }
        // the failure count is read from the store, not the (possibly older) config: fast entries all count
        val failures = repo.setting(SettingKeys.LOCK_BACKOFF)?.toIntOrNull() ?: 0
        if (ok) repo.setSetting(SettingKeys.LOCK_BACKOFF, null)
        else repo.setSetting(SettingKeys.LOCK_BACKOFF, app.lock.registerFailure(failures).toString())
        return ok
    }

    val exporting = MutableStateFlow(false)

    /** Encrypts all data with [passphrase] (mode 1) and writes it to the picked document. */
    fun export(uri: Uri, passphrase: CharArray) {
        exporting.value = true
        c.launch {
            try {
                val model = repo.exportAll(java.time.Instant.now().toString(), app.appLabel(), includeSecurity = false)
                val bytes = withContext(Dispatchers.Default) { CyclusFile.sealWithPassphrase(model.encode(), passphrase) }
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                }
                val count = (repo.setting(SettingKeys.EXPORT_COUNT)?.toIntOrNull() ?: 0) + 1
                repo.setSetting(SettingKeys.EXPORT_COUNT, count.toString())
                repo.setSetting(SettingKeys.EXPORT_LAST, LocalDate.now().toEpochDay().toString())
                c.msg(R.string.export_done)
            } catch (e: Exception) {
                c.msg(R.string.export_failed)
            } finally {
                passphrase.fill(' ')
                exporting.value = false
            }
        }
    }

    val importStage = MutableStateFlow<ImportStage>(ImportStage.Passphrase)
    private var importUri: Uri? = null
    private var pendingImport: ExportFile? = null

    fun startImport(uri: Uri) { importUri = uri; pendingImport = null; importStage.value = ImportStage.Passphrase }

    fun openImport(passphrase: CharArray) {
        val uri = importUri ?: return
        importStage.value = ImportStage.Working
        c.launch {
            val stage = try {
                val bytes = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)!!.use { s -> readCapped(s, CyclusFile.MAX_FILE_BYTES) }
                }
                val file = withContext(Dispatchers.Default) {
                    ExportFile.decode(CyclusFile.openWithPassphrase(bytes, passphrase))
                }
                pendingImport = file
                ImportStage.Preview(file.preview())
            } catch (e: CyclusFileException.WrongPassphraseOrDamaged) {
                ImportStage.Error(R.string.import_wrong)
            } catch (e: CyclusFileException.UnsupportedVersion) {
                ImportStage.Error(R.string.import_unsupported)
            } catch (e: Exception) {
                ImportStage.Error(R.string.import_bad)
            } finally {
                passphrase.fill(' ')
            }
            importStage.value = stage
        }
    }

    fun applyImport(replace: Boolean, onDone: () -> Unit) {
        val file = pendingImport ?: return
        c.launch {
            // the file was validated before the preview; a failure here (storage) rolls the transaction back
            val ok = runCatching { app.backup.exclusive { if (replace) repo.importReplace(file) else repo.importMerge(file) } }.isSuccess
            pendingImport = null; importUri = null
            importStage.value = if (ok) ImportStage.Passphrase
            else ImportStage.Error(R.string.import_bad)
            if (ok) { c.msg(R.string.import_done); onDone() }
        }
    }

    fun cancelImport() { pendingImport = null; importUri = null; importStage.value = ImportStage.Passphrase }

    /** Erase everything (crypto-shred); the store reopens empty. */
    fun eraseEverything(deleteHealth: Boolean = false) {
        app.lock.forget()
        app.store.erase(deleteHealth = deleteHealth)
    }
}

/** Reads at most [max] bytes (API 29-safe; `readNBytes` needs Android 13). Larger input → [CyclusFileException.TooLarge]. */
private fun readCapped(input: InputStream, max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        if (out.size() + n > max) throw CyclusFileException.TooLarge("file too large")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
