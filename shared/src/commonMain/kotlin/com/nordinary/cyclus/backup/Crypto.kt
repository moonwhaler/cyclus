package com.nordinary.cyclus.backup

/** Platform crypto + compression used by the `.cyclus` format (JVM/Android: javax.crypto + java.util.zip; iOS later: CommonCrypto + Compression). */
expect object CyclusCrypto {
    fun random(size: Int): ByteArray
    /**
     * Unicode NFC form of a passphrase, so the same visible passphrase gives the same key on every platform and
     * keyboard („ä“ precomposed or as „a“ + combining diaeresis). Returns [passphrase] itself when it already is NFC.
     */
    fun nfc(passphrase: CharArray): CharArray
    /** PBKDF2-HMAC-SHA256, 32-byte output. */
    fun pbkdf2(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray
    /** AES-256-GCM with a 128-bit tag appended to the ciphertext. */
    fun sealGcm(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray
    /** Throws on authentication failure. */
    fun openGcm(key: ByteArray, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray
    /** Raw DEFLATE (no zlib header), as Apple's COMPRESSION_ZLIB. */
    fun deflateRaw(data: ByteArray): ByteArray
    /** Raw INFLATE; throws [CyclusFileException.TooLarge] beyond [maxOutput] bytes. */
    fun inflateRaw(data: ByteArray, maxOutput: Int): ByteArray
}
