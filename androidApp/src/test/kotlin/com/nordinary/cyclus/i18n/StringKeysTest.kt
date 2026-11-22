package com.nordinary.cyclus.i18n

import com.nordinary.cyclus.core.Catalog
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.core.LhResult
import com.nordinary.cyclus.core.Mucus
import com.nordinary.cyclus.engine.EvidenceType
import com.nordinary.cyclus.engine.Tier
import com.nordinary.cyclus.insights.Area
import com.nordinary.cyclus.insights.Direction
import com.nordinary.cyclus.insights.PatternStatus
import com.nordinary.cyclus.presenter.ReportSection
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `Fmt.s` shows the raw key when a string is missing, so a gap would ship silently. Every key the UI builds from an
 * enum, and every text key the engine emits, must resolve.
 */
class StringKeysTest {
    private fun missing(keys: Collection<String>) = keys.filter { it !in TxRes.strings && it !in TxRes.plurals }

    @Test fun keysBuiltFromEnumsResolve() {
        val keys = PatternStatus.entries.map { "st_${it.key}" } + Area.entries.map { "area_${it.key}" } +
            LhResult.entries.map { "lh_${it.key}" } + EvidenceType.entries.map { "ev_${it.key}" } +
            Tier.entries.map { "tier_${it.key}" } + ReportSection.entries.map { "inc_${it.key}" } +
            (1..3).map { "ft$it" } + (1..3).map { "ftAdj$it" } +
            Catalog.SCALES.flatMap { s -> Direction.entries.map { "fcS_${s}_${it.key}" } }
        val gaps = missing(keys)
        assertTrue(gaps.isEmpty(), "missing strings: $gaps")
    }

    @Test fun everyItemHasALabel() {
        val items = Catalog.SYMPTOMS + Catalog.EMOTIONS + Catalog.TAGS + Catalog.PAIN_REGIONS +
            Flow.entries.filter { it != Flow.UNSPECIFIED }.map { it.key } + Mucus.entries.map { it.key }
        val all = TxRes.sym.keys + TxRes.emo.keys + TxRes.tag.keys + TxRes.flow.keys + TxRes.mucus.keys + TxRes.region.keys
        val gaps = items.filter { it !in all }
        assertTrue(gaps.isEmpty(), "missing item labels: $gaps")
    }

    @Test fun everyEngineTextKeyResolves() {
        val src = File("../shared/src/commonMain/kotlin").walkTopDown().filter { it.extension == "kt" }
        val keys = src.flatMap { f -> Regex("""\b(?:txt|Txt)\("([A-Za-z0-9_]+)"""").findAll(f.readText()).map { it.groupValues[1] } }.toSet()
        assertTrue(keys.size > 20, "engine keys not found (${keys.size})")
        val gaps = missing(keys)
        assertTrue(gaps.isEmpty(), "missing strings: $gaps")
    }
}
