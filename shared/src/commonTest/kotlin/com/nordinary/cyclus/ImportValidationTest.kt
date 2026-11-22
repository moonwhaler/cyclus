package com.nordinary.cyclus

import com.nordinary.cyclus.backup.CyclusFileException
import com.nordinary.cyclus.backup.ExportEpisode
import com.nordinary.cyclus.backup.ExportFile
import com.nordinary.cyclus.backup.ExportLog
import com.nordinary.cyclus.backup.ExportOverride
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Import never reaches the store with a value it can't hold: newer-app values and damaged files fail before the preview. */
class ImportValidationTest {
    private fun file(vararg logs: ExportLog, overrides: List<ExportOverride> = emptyList(), episodes: List<ExportEpisode> = emptyList()) =
        ExportFile(exportedAt = "t", app = "test", logs = logs.toList(), overrides = overrides, episodes = episodes)

    private fun decode(f: ExportFile) = ExportFile.decode(f.encode())

    @Test fun validFileDecodes() {
        val f = file(ExportLog("2026-11-01", flow = "heavy", mood = 5, pain = mapOf("lowC" to 10), bbt = 36.5, symptomSeverity = mapOf("Cramps" to 3)))
        assertEquals(1, decode(f).logs.size)
    }

    @Test fun valuesFromANewerAppAreUnsupported() {
        assertFailsWith<CyclusFileException.UnsupportedVersion> { decode(file(ExportLog("2026-11-01", flow = "very_heavy"))) }
        assertFailsWith<CyclusFileException.UnsupportedVersion> { decode(file(ExportLog("2026-11-01", mucus = "spotted"))) }
        assertFailsWith<CyclusFileException.UnsupportedVersion> { decode(file(ExportLog("2026-11-01", lh = "faint"))) }
        assertFailsWith<CyclusFileException.UnsupportedVersion> { decode(file(overrides = listOf(ExportOverride("2026-11-01", "maybe")))) }
        assertFailsWith<CyclusFileException.UnsupportedVersion> { decode(file(episodes = listOf(ExportEpisode("e", "menopause", "2026-01-01")))) }
    }

    @Test fun damagedValuesAreMalformed() {
        for (bad in listOf(
            ExportLog("2026-13-01"), ExportLog("not a date"), ExportLog("1700-01-01"),
            ExportLog("2026-11-01", mood = 0), ExportLog("2026-11-01", energy = 6), ExportLog("2026-11-01", libido = -1),
            ExportLog("2026-11-01", pain = mapOf("head" to 11)), ExportLog("2026-11-01", symptomSeverity = mapOf("Cramps" to 4)),
            ExportLog("2026-11-01", bbt = 99.0),
            ExportLog("2026-11-01", symptoms = listOf("")), ExportLog("2026-11-01", note = "x".repeat(ExportFile.MAX_NOTE + 1)),
            ExportLog("2026-11-01", updatedAt = -5),
        )) assertFailsWith<CyclusFileException.Malformed>(bad.toString()) { decode(file(bad)) }
        assertFailsWith<CyclusFileException.Malformed> { decode(file(episodes = listOf(ExportEpisode("e", "hbc", "2026-02-01", "2026-01-01")))) }
    }
}
