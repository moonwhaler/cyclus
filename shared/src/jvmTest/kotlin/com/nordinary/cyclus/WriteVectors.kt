package com.nordinary.cyclus

import com.nordinary.cyclus.backup.CyclusFile
import java.io.File
import kotlin.test.Test

/** Regenerates the .cyclus vectors in shared/golden/export when CYCLUS_WRITE_VECTORS=1 (format changes only). */
class WriteVectors {
    @Test fun write() {
        if (System.getenv("CYCLUS_WRITE_VECTORS") != "1") return
        fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val dir = File("golden/export")
        val plain = File(dir, "vector.json").readBytes()
        val nonce = hex("a0a1a2a3a4a5a6a7a8a9aaab")
        File(dir, "vector-mode1.cyclus").writeBytes(
            CyclusFile.sealWithPassphrase(plain, "Cyclus-Testvektor 1".toCharArray(), 1000, hex("000102030405060708090a0b0c0d0e0f"), nonce),
        )
        // non-ASCII passphrase, given decomposed (NFD): the file must equal the one from the precomposed (NFC) form
        File(dir, "vector-mode1-umlaut.cyclus").writeBytes(
            CyclusFile.sealWithPassphrase(plain, "Zyklus-Pru\u0308fung a\u0308".toCharArray(), 1000, hex("000102030405060708090a0b0c0d0e0f"), nonce),
        )
        File(dir, "vector-mode2.cyclus").writeBytes(
            CyclusFile.sealWithKey(plain, hex("101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f"), nonce),
        )
    }
}
