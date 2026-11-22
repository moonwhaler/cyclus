package com.nordinary.cyclus.store

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
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
 * The 256-bit SQLCipher key is random, stored only wrapped (AES-256-GCM) by an Android Keystore key that
 * requires an unlocked device and never leaves secure hardware (StrongBox when available). The wrapped key lives
 * in `noBackupFilesDir`, so it is never part of a backup or device transfer (ANDROID_PLAN §2.4, §0.1).
 */
internal class DbKeyManager(private val context: Context) {
    private val keyFile = File(context.noBackupFilesDir, "cyclus-db.key")

    fun obtainKey(databaseExists: Boolean): ByteArray {
        if (keyFile.exists()) return unwrap(keyFile.readBytes())
        if (databaseExists) throw DbKeyException(DbKeyException.Reason.KEY_FILE_MISSING)
        val dbKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = wrap(dbKey)
        val tmp = File(keyFile.parentFile, keyFile.name + ".tmp")
        tmp.writeBytes(wrapped)
        if (!tmp.renameTo(keyFile)) throw DbKeyException(DbKeyException.Reason.CRYPTO_FAILURE)
        return dbKey
    }

    private fun wrap(dbKey: ByteArray): ByteArray = guarded {
        val kek = existingKek() ?: createKek()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, kek)
        val iv = cipher.iv
        byteArrayOf(VERSION, iv.size.toByte()) + iv + cipher.doFinal(dbKey)
    }

    private fun unwrap(blob: ByteArray): ByteArray = guarded {
        if (blob.size < 2 || blob[0] != VERSION) throw DbKeyException(DbKeyException.Reason.CRYPTO_FAILURE)
        val ivLen = blob[1].toInt()
        val iv = blob.copyOfRange(2, 2 + ivLen)
        val kek = existingKek() ?: throw DbKeyException(DbKeyException.Reason.KEY_INVALIDATED)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(128, iv))
        cipher.doFinal(blob, 2 + ivLen, blob.size - 2 - ivLen)
    }

    private fun existingKek(): SecretKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return ks.getKey(ALIAS, null) as SecretKey?
    }

    private fun createKek(): SecretKey {
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUnlockedDeviceRequired(true)
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
    } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
        throw DbKeyException(DbKeyException.Reason.KEY_INVALIDATED, e)
    } catch (e: android.security.keystore.UserNotAuthenticatedException) {
        throw DbKeyException(DbKeyException.Reason.DEVICE_LOCKED, e)
    } catch (e: java.security.UnrecoverableKeyException) {
        throw DbKeyException(DbKeyException.Reason.KEY_INVALIDATED, e)
    } catch (e: Exception) {
        val locked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            e is android.security.KeyStoreException && e.numericErrorCode == android.security.KeyStoreException.ERROR_USER_AUTHENTICATION_REQUIRED
        throw DbKeyException(if (locked) DbKeyException.Reason.DEVICE_LOCKED else DbKeyException.Reason.CRYPTO_FAILURE, e)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "cyclus_db_kek_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
    }
}
