package com.nordinary.cyclus.backup

/** Typed decode errors. Nothing is written to the store on any of them. */
sealed class CyclusFileException(message: String) : Exception(message) {
    /** Wrong passphrase, wrong key or modified file (GCM authentication failed). */
    class WrongPassphraseOrDamaged : CyclusFileException("wrong passphrase or damaged file")
    class UnsupportedVersion(message: String) : CyclusFileException(message)
    class TooLarge(message: String) : CyclusFileException(message)
    class Malformed(message: String) : CyclusFileException(message)
}

/**
 * `.cyclus` v1 container (docs/export/FORMAT.md):
 * `"CYCLUS1"(7) | mode(1) | salt(16) | iterations(u32 BE) | nonce(12) | AES-256-GCM(raw DEFLATE(JSON)) + tag(16)`.
 * Mode 1 = passphrase (PBKDF2-HMAC-SHA256), mode 2 = raw 256-bit key (device snapshot). AAD = the 40 header bytes.
 */
object CyclusFile {
    private val MAGIC = "CYCLUS1".encodeToByteArray()
    const val MODE_PASSPHRASE: Byte = 1
    const val MODE_RAW_KEY: Byte = 2
    const val HEADER_SIZE = 7 + 1 + 16 + 4 + 12
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_ITERATIONS = 100_000
    const val MAX_ITERATIONS = 10_000_000
    const val MAX_FILE_BYTES = 64 * 1024 * 1024
    const val MAX_JSON_BYTES = 32 * 1024 * 1024

    data class Header(val mode: Byte, val salt: ByteArray, val iterations: Int, val nonce: ByteArray)

    /** Key = PBKDF2(UTF-8(NFC(passphrase))) (FORMAT.md), so files open on any platform. [normalize] = false only in tests (files of earlier versions). */
    fun sealWithPassphrase(
        json: ByteArray,
        passphrase: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        salt: ByteArray = CyclusCrypto.random(16),
        nonce: ByteArray = CyclusCrypto.random(12),
        normalize: Boolean = true,
    ): ByteArray {
        val header = header(MODE_PASSPHRASE, salt, iterations, nonce)
        val pass = if (normalize) CyclusCrypto.nfc(passphrase) else passphrase
        val key = try { CyclusCrypto.pbkdf2(pass, salt, iterations) } finally { if (pass !== passphrase) pass.fill(' ') }
        try {
            return header + CyclusCrypto.sealGcm(key, nonce, header, CyclusCrypto.deflateRaw(json))
        } finally {
            key.fill(0)
        }
    }

    fun sealWithKey(json: ByteArray, key: ByteArray, nonce: ByteArray = CyclusCrypto.random(12)): ByteArray {
        require(key.size == 32) { "raw key must be 256 bits" }
        val header = header(MODE_RAW_KEY, ByteArray(16), 0, nonce)
        return header + CyclusCrypto.sealGcm(key, nonce, header, CyclusCrypto.deflateRaw(json))
    }

    fun readHeader(bytes: ByteArray): Header {
        if (bytes.size > MAX_FILE_BYTES) throw CyclusFileException.TooLarge("file exceeds $MAX_FILE_BYTES bytes")
        if (bytes.size < HEADER_SIZE + 16) throw CyclusFileException.Malformed("file too short")
        if (!bytes.copyOfRange(0, 7).contentEquals(MAGIC)) throw CyclusFileException.Malformed("not a .cyclus file")
        val mode = bytes[7]
        if (mode != MODE_PASSPHRASE && mode != MODE_RAW_KEY) throw CyclusFileException.UnsupportedVersion("unknown mode $mode")
        val salt = bytes.copyOfRange(8, 24)
        val it = ((bytes[24].toInt() and 0xFF) shl 24) or ((bytes[25].toInt() and 0xFF) shl 16) or
            ((bytes[26].toInt() and 0xFF) shl 8) or (bytes[27].toInt() and 0xFF)
        val nonce = bytes.copyOfRange(28, 40)
        return Header(mode, salt, it, nonce)
    }

    /** Decrypts a mode-1 file. [minIterations] is lowered only by format tests. */
    fun openWithPassphrase(bytes: ByteArray, passphrase: CharArray, minIterations: Int = MIN_ITERATIONS): ByteArray {
        val h = readHeader(bytes)
        if (h.mode != MODE_PASSPHRASE) throw CyclusFileException.Malformed("not a passphrase file")
        if (h.iterations !in minIterations..MAX_ITERATIONS) throw CyclusFileException.Malformed("iterations out of range: ${h.iterations}")
        val nfc = CyclusCrypto.nfc(passphrase)
        try {
            return openWith(bytes, nfc, h)
        } catch (e: CyclusFileException.WrongPassphraseOrDamaged) {
            // files written before NFC was required (2026-11-23) used the passphrase as typed
            if (nfc === passphrase) throw e
            return openWith(bytes, passphrase, h)
        } finally {
            if (nfc !== passphrase) nfc.fill(' ')
        }
    }

    private fun openWith(bytes: ByteArray, passphrase: CharArray, h: Header): ByteArray {
        val key = CyclusCrypto.pbkdf2(passphrase, h.salt, h.iterations)
        try {
            return open(bytes, key, h)
        } finally {
            key.fill(0)
        }
    }

    fun openWithKey(bytes: ByteArray, key: ByteArray): ByteArray {
        val h = readHeader(bytes)
        if (h.mode != MODE_RAW_KEY || h.iterations != 0) throw CyclusFileException.Malformed("not a raw-key file")
        return open(bytes, key, h)
    }

    private fun open(bytes: ByteArray, key: ByteArray, h: Header): ByteArray {
        val aad = bytes.copyOfRange(0, HEADER_SIZE)
        val deflated = try {
            CyclusCrypto.openGcm(key, h.nonce, aad, bytes.copyOfRange(HEADER_SIZE, bytes.size))
        } catch (e: CyclusFileException) {
            throw e
        } catch (e: Exception) {
            throw CyclusFileException.WrongPassphraseOrDamaged()
        }
        return CyclusCrypto.inflateRaw(deflated, MAX_JSON_BYTES)
    }

    private fun header(mode: Byte, salt: ByteArray, iterations: Int, nonce: ByteArray): ByteArray {
        require(salt.size == 16 && nonce.size == 12)
        val it = byteArrayOf((iterations ushr 24).toByte(), (iterations ushr 16).toByte(), (iterations ushr 8).toByte(), iterations.toByte())
        return MAGIC + byteArrayOf(mode) + salt + it + nonce
    }
}
