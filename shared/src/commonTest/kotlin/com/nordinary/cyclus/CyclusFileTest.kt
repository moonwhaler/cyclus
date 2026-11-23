package com.nordinary.cyclus

import com.nordinary.cyclus.backup.CyclusFile
import com.nordinary.cyclus.backup.CyclusFileException
import com.nordinary.cyclus.backup.ExportFile
import com.nordinary.cyclus.backup.ExportLog
import com.nordinary.cyclus.backup.ExportOverride
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `.cyclus` v1 container: round trips, tamper detection, caps and frozen known-answer vectors (shared/golden/export). */
class CyclusFileTest {
    private val json = """{"schema":1,"exportedAt":"2026-11-22T12:00:00Z","app":"test","logs":[{"day":"2026-11-20","flow":"medium","symptoms":["Cramps"],"updatedAt":1}]}""".encodeToByteArray()
    private val pass = "korrekt pferd batterie".toCharArray()

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun passphraseRoundTrip() {
        val sealed = CyclusFile.sealWithPassphrase(json, pass, iterations = 1000)
        assertEquals(CyclusFile.MODE_PASSPHRASE, CyclusFile.readHeader(sealed).mode)
        assertContentEquals(json, CyclusFile.openWithPassphrase(sealed, pass, minIterations = 1000))
    }

    @Test fun rawKeyRoundTrip() {
        val key = ByteArray(32) { it.toByte() }
        val sealed = CyclusFile.sealWithKey(json, key)
        assertEquals(0, CyclusFile.readHeader(sealed).iterations)
        assertContentEquals(json, CyclusFile.openWithKey(sealed, key))
    }

    @Test fun wrongPassphraseAndTampering() {
        val sealed = CyclusFile.sealWithPassphrase(json, pass, iterations = 1000)
        assertFailsWith<CyclusFileException.WrongPassphraseOrDamaged> { CyclusFile.openWithPassphrase(sealed, "falsch".toCharArray(), 1000) }
        // flip a salt byte (header is authenticated as AAD, and the salt changes the key)
        assertFailsWith<CyclusFileException.WrongPassphraseOrDamaged> {
            CyclusFile.openWithPassphrase(sealed.copyOf().also { it[10] = (it[10] + 1).toByte() }, pass, 1000)
        }
        // flip a nonce byte (authenticated)
        assertFailsWith<CyclusFileException.WrongPassphraseOrDamaged> {
            CyclusFile.openWithPassphrase(sealed.copyOf().also { it[30] = (it[30] + 1).toByte() }, pass, 1000)
        }
        // flip a ciphertext byte
        assertFailsWith<CyclusFileException.WrongPassphraseOrDamaged> {
            CyclusFile.openWithPassphrase(sealed.copyOf().also { it[it.size - 20] = (it[it.size - 20] + 1).toByte() }, pass, 1000)
        }
        // mode byte switched to raw key → rejected before any key work
        val raw = sealed.copyOf().also { it[7] = CyclusFile.MODE_RAW_KEY }
        assertFailsWith<CyclusFileException.Malformed> { CyclusFile.openWithKey(raw, ByteArray(32)) }
    }

    @Test fun capsAndGarbage() {
        val sealed = CyclusFile.sealWithPassphrase(json, pass, iterations = 1000)
        // default minimum rejects weak iteration counts
        assertFailsWith<CyclusFileException.Malformed> { CyclusFile.openWithPassphrase(sealed, pass) }
        // absurd iteration count (DoS) rejected without deriving
        val huge = sealed.copyOf().also { it[24] = 0x7F; it[25] = -1; it[26] = -1; it[27] = -1 }
        assertFailsWith<CyclusFileException.Malformed> { CyclusFile.openWithPassphrase(huge, pass) }
        assertFailsWith<CyclusFileException.Malformed> { CyclusFile.readHeader("hello".encodeToByteArray()) }
        assertFailsWith<CyclusFileException.Malformed> { CyclusFile.readHeader(ByteArray(80)) }
        assertFailsWith<CyclusFileException.UnsupportedVersion> {
            CyclusFile.readHeader(sealed.copyOf().also { it[7] = 9 })
        }
        // inflate bomb: 40 MB of zeros compresses tiny but exceeds the 32 MB cap
        val bomb = CyclusFile.sealWithKey(ByteArray(40 * 1024 * 1024), ByteArray(32))
        assertFailsWith<CyclusFileException.TooLarge> { CyclusFile.openWithKey(bomb, ByteArray(32)) }
    }

    @Test fun exportJsonSchemaChecks() {
        val f = ExportFile(
            exportedAt = "2026-11-22T12:00:00Z", app = "test",
            logs = listOf(ExportLog(day = "2026-11-20", flow = "heavy", pain = mapOf("lowC" to 7), bbt = 36.47, note = "äöü", updatedAt = 5)),
            overrides = listOf(ExportOverride("2026-11-01", "start")),
            prefs = mapOf("mode" to "conceive"),
        )
        assertEquals(f, ExportFile.decode(f.encode()))
        assertFailsWith<CyclusFileException.UnsupportedVersion> { ExportFile.decode("""{"schema":2,"exportedAt":"x","app":"y"}""".encodeToByteArray()) }
        assertFailsWith<CyclusFileException.Malformed> { ExportFile.decode("not json".encodeToByteArray()) }
    }

    /** Frozen vectors: byte-identical re-encoding and decoding (also checked by tools/export/cyclus_decode.py). */
    @Test fun knownAnswerVectors() {
        val plain = readGoldenBytes("export/vector.json")
        val v1 = readGoldenBytes("export/vector-mode1.cyclus")
        val v2 = readGoldenBytes("export/vector-mode2.cyclus")
        val salt = hex("000102030405060708090a0b0c0d0e0f")
        val nonce = hex("a0a1a2a3a4a5a6a7a8a9aaab")
        val key = hex("101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f")
        assertContentEquals(plain, CyclusFile.openWithPassphrase(v1, "Cyclus-Testvektor 1".toCharArray(), minIterations = 1000))
        assertContentEquals(plain, CyclusFile.openWithKey(v2, key))
        assertContentEquals(v1, CyclusFile.sealWithPassphrase(plain, "Cyclus-Testvektor 1".toCharArray(), 1000, salt, nonce))
        assertContentEquals(v2, CyclusFile.sealWithKey(plain, key, nonce))
        assertTrue(ExportFile.decode(plain).logs.isNotEmpty())
    }

    /** Cross-platform: the same visible passphrase opens the file whatever Unicode form a keyboard produced (FORMAT.md: NFC). */
    @Test fun passphraseIsNormalizedToNfc() {
        val nfc = "Zyklus-Pr\u00fcfung \u00e4".toCharArray()
        val nfd = "Zyklus-Pru\u0308fung a\u0308".toCharArray()
        val fromNfd = CyclusFile.sealWithPassphrase(json, nfd.copyOf(), iterations = 1000)
        assertContentEquals(json, CyclusFile.openWithPassphrase(fromNfd, nfc.copyOf(), minIterations = 1000))
        val fromNfc = CyclusFile.sealWithPassphrase(json, nfc.copyOf(), iterations = 1000)
        assertContentEquals(json, CyclusFile.openWithPassphrase(fromNfc, nfd.copyOf(), minIterations = 1000))
        // frozen vector: other platforms must produce exactly these bytes from either form
        val plain = readGoldenBytes("export/vector.json")
        val v = readGoldenBytes("export/vector-mode1-umlaut.cyclus")
        val salt = hex("000102030405060708090a0b0c0d0e0f"); val nonce = hex("a0a1a2a3a4a5a6a7a8a9aaab")
        assertContentEquals(v, CyclusFile.sealWithPassphrase(plain, nfc.copyOf(), 1000, salt, nonce))
        assertContentEquals(v, CyclusFile.sealWithPassphrase(plain, nfd.copyOf(), 1000, salt, nonce))
        assertContentEquals(plain, CyclusFile.openWithPassphrase(v, nfd.copyOf(), minIterations = 1000))
    }

    /** Files written before NFC was required (passphrase used as typed, here decomposed) still open. */
    @Test fun olderFilesWithUnnormalizedPassphraseStillOpen() {
        val nfd = "Pru\u0308fung".toCharArray()
        val legacy = CyclusFile.sealWithPassphrase(json, nfd.copyOf(), iterations = 1000, normalize = false)
        assertContentEquals(json, CyclusFile.openWithPassphrase(legacy, nfd.copyOf(), minIterations = 1000))
        assertFailsWith<CyclusFileException.WrongPassphraseOrDamaged> {
            CyclusFile.openWithPassphrase(legacy, "Pruefung".toCharArray(), minIterations = 1000)
        }
    }
}
