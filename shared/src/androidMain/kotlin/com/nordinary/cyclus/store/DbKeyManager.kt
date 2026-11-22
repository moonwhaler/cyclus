package com.nordinary.cyclus.store

import android.content.Context
import android.os.Build
import android.security.KeyStoreException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Why the database key could not be provided. The app shows a blocking error and never deletes data. */
class DbKeyException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** The database exists but its wrapped key file is missing. */
        KEY_FILE_MISSING,
        /** The Keystore key is gone or permanently invalidated (e.g. device reset of credentials). */
        KEY_INVALIDATED,
        /** The device is locked (key requires an unlocked device). Retry after unlock. */
        DEVICE_LOCKED,
        /** Any other Keystore / crypto failure. */
        CRYPTO_FAILURE,
    }
}

/**
 * The 256-bit SQLCipher key is random, stored only wrapped (AES-256-GCM) by an Android Keystore key that never leaves
 * secure hardware (StrongBox when available). The wrapped key lives in `noBackupFilesDir`, so it is never part of a
 * backup or device transfer (ANDROID_PLAN §2.4, §0.1).
 *
 * On Android 15+ the wrapping key also requires an unlocked device ([strict]). Android 10–14 can't be trusted with
 * that rule: it fails to create such a key on a phone without a screen lock, and **deletes** it when the screen lock is
 * removed later, which would make the data unreadable (both reproduced on an Android 12 emulator). There the key is
 * created without it and still protected by the hardware Keystore and file-based encryption. The wrapped file's
 * version byte says which wrapping key it uses; a mismatch with this device's rule is re-wrapped on the next open
 * (also after an OS upgrade), so existing installs on Android 10–14 lose the deletion risk.
 */
internal class DbKeyManager(
    private val context: Context,
    dbName: String = AndroidDatabase.FILE_NAME,
    private val strict: Boolean = Build.VERSION.SDK_INT >= 35,
) {
    // the app's database uses the original names; other databases (tests) get their own key file and Keystore aliases
    private val isMain = dbName == AndroidDatabase.FILE_NAME
    private val keyFile = File(context.noBackupFilesDir, if (isMain) "cyclus-db.key" else "$dbName.key")
    private val strictAlias = if (isMain) "cyclus_db_kek_v1" else "cyclus_db_kek_v1_$dbName"
    private val openAlias = if (isMain) "cyclus_db_kek_v2" else "cyclus_db_kek_v2_$dbName"

    /** Version byte of the wrapped file: which wrapping key (and rule) it uses. */
    private fun version(strict: Boolean): Byte = if (strict) VERSION_STRICT else VERSION_OPEN
    private fun alias(version: Byte) = if (version == VERSION_STRICT) strictAlias else openAlias

    fun obtainKey(databaseExists: Boolean): ByteArray {
        if (keyFile.exists()) {
            val blob = keyFile.readBytes()
            val key = unwrap(blob)
            if (blob[0] != version(strict)) runCatching { rewrap(key, from = blob[0]) } // keeps the old one on failure
            return key
        }
        if (databaseExists) throw DbKeyException(DbKeyException.Reason.KEY_FILE_MISSING)
        val dbKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        writeKeyFile(wrap(dbKey, strict))
        return dbKey
    }

    /** Wraps [key] with this device's rule, replaces the file atomically, then drops the old wrapping key. */
    private fun rewrap(key: ByteArray, from: Byte) {
        writeKeyFile(wrap(key, strict))
        runCatching { keyStore().deleteEntry(alias(from)) }
    }

    private fun writeKeyFile(wrapped: ByteArray) {
        val tmp = File(keyFile.parentFile, keyFile.name + ".tmp")
        // fsync before the rename: a crash right after creation must never leave an empty key file behind
        FileOutputStream(tmp).use { out -> out.write(wrapped); out.fd.sync() }
        if (!tmp.renameTo(keyFile)) throw DbKeyException(DbKeyException.Reason.CRYPTO_FAILURE)
    }

    /** Deletes the wrapped key file and the Keystore key (crypto-shred). */
    fun destroy() {
        keyFile.delete()
        File(keyFile.parentFile, keyFile.name + ".tmp").delete()
        runCatching { keyStore().deleteEntry(strictAlias) }
        runCatching { keyStore().deleteEntry(openAlias) }
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun wrap(dbKey: ByteArray, strictRule: Boolean): ByteArray = guarded {
        val v = version(strictRule)
        val kek = existingKek(alias(v)) ?: createKek(alias(v), strictRule)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, kek)
        val iv = cipher.iv
        byteArrayOf(v, iv.size.toByte()) + iv + cipher.doFinal(dbKey)
    }

    private fun unwrap(blob: ByteArray): ByteArray = guarded {
        if (blob.size < 2 || (blob[0] != VERSION_STRICT && blob[0] != VERSION_OPEN)) throw DbKeyException(DbKeyException.Reason.CRYPTO_FAILURE)
        val ivLen = blob[1].toInt()
        val iv = blob.copyOfRange(2, 2 + ivLen)
        val kek = existingKek(alias(blob[0])) ?: throw DbKeyException(DbKeyException.Reason.KEY_INVALIDATED)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(128, iv))
        cipher.doFinal(blob, 2 + ivLen, blob.size - 2 - ivLen)
    }

    private fun existingKek(alias: String): SecretKey? = keyStore().getKey(alias, null) as SecretKey?

    private fun createKek(alias: String, unlockedDeviceRequired: Boolean): SecretKey {
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUnlockedDeviceRequired(unlockedDeviceRequired)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        return try {
            gen.init(spec(strongBox = true)); gen.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            gen.init(spec(strongBox = false)); gen.generateKey()
        }
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: DbKeyException) {
        throw e
    } catch (e: KeyPermanentlyInvalidatedException) {
        throw DbKeyException(DbKeyException.Reason.KEY_INVALIDATED, e)
    } catch (e: UserNotAuthenticatedException) {
        throw DbKeyException(DbKeyException.Reason.DEVICE_LOCKED, e)
    } catch (e: UnrecoverableKeyException) {
        throw DbKeyException(DbKeyException.Reason.KEY_INVALIDATED, e)
    } catch (e: Exception) {
        // Keystore wraps „device is locked“ in InvalidKeyException & co.; the keyguard is the reliable signal
        val keyguardLocked = context.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceLocked == true
        val authRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            generateSequence<Throwable>(e) { it.cause }.take(6).any {
                it is android.security.KeyStoreException && it.numericErrorCode == android.security.KeyStoreException.ERROR_USER_AUTHENTICATION_REQUIRED
            }
        throw DbKeyException(if (keyguardLocked || authRequired) DbKeyException.Reason.DEVICE_LOCKED else DbKeyException.Reason.CRYPTO_FAILURE, e)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        /** Wrapped with the unlocked-device-required key (Android 15+, and every install before this change). */
        const val VERSION_STRICT: Byte = 1
        /** Wrapped with a key without that rule (Android 10–14). */
        const val VERSION_OPEN: Byte = 2
    }
}
