package com.nordinary.cyclus.backup

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the 256-bit backup key with a hardware Keystore key. Unlike the database key-encryption key this one does
 * **not** require an unlocked device, so a nightly backup can read the snapshot while the phone is locked.
 * Consequence (threat model): code running as Cyclus on a locked phone after first unlock can decrypt the snapshot;
 * a disk image cannot. „Nur auf dem Gerät“ removes the snapshot and this key.
 */
class BackupKeyStore(private val alias: String = ALIAS) {
    companion object {
        const val ALIAS = "cyclus_backup_kek_v1"
        private const val VERSION: Byte = 1
    }

    fun wrap(key: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, existing() ?: create())
        val iv = c.iv
        return byteArrayOf(VERSION, iv.size.toByte()) + iv + c.doFinal(key)
    }

    fun unwrap(blob: ByteArray): ByteArray {
        require(blob.size > 2 && blob[0] == VERSION) { "bad wrapped key" }
        val ivLen = blob[1].toInt()
        val kek = existing() ?: throw IllegalStateException("backup key-encryption key missing")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(128, blob, 2, ivLen))
        return c.doFinal(blob, 2 + ivLen, blob.size - 2 - ivLen)
    }

    fun destroy() { runCatching { ks().deleteEntry(alias) } }

    private fun ks() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun existing(): SecretKey? = ks().getKey(alias, null) as SecretKey?

    private fun create(): SecretKey {
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        return try { gen.init(spec(true)); gen.generateKey() } catch (_: StrongBoxUnavailableException) { gen.init(spec(false)); gen.generateKey() }
    }
}
