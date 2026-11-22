package com.nordinary.cyclus.backup

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Shared by the JVM and Android targets (src/jvmCommon is added to both source sets).
actual object CyclusCrypto {
    private val rng = SecureRandom()

    actual fun random(size: Int): ByteArray = ByteArray(size).also { rng.nextBytes(it) }

    actual fun pbkdf2(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    actual fun sealGcm(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(aad)
            doFinal(plain)
        }

    actual fun openGcm(key: ByteArray, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(aad)
            doFinal(sealed)
        }

    actual fun deflateRaw(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            d.setInput(data); d.finish()
            val out = ByteArrayOutputStream(data.size / 4 + 64)
            val buf = ByteArray(64 * 1024)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            return out.toByteArray()
        } finally {
            d.end()
        }
    }

    actual fun inflateRaw(data: ByteArray, maxOutput: Int): ByteArray {
        val i = Inflater(true)
        try {
            i.setInput(data)
            val out = ByteArrayOutputStream(minOf(maxOutput, data.size * 4 + 64))
            val buf = ByteArray(64 * 1024)
            while (!i.finished()) {
                val n = i.inflate(buf)
                if (n == 0 && (i.needsInput() || i.needsDictionary())) throw CyclusFileException.Malformed("truncated deflate stream")
                if (out.size() + n > maxOutput) throw CyclusFileException.TooLarge("inflated data exceeds $maxOutput bytes")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } catch (e: java.util.zip.DataFormatException) {
            throw CyclusFileException.Malformed("bad deflate stream")
        } finally {
            i.end()
        }
    }
}
