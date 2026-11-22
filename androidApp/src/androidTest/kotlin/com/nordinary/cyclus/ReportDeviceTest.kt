package com.nordinary.cyclus

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nordinary.cyclus.backup.BackupCoordinator
import com.nordinary.cyclus.backup.BackupKeyStore
import com.nordinary.cyclus.engine.CycleData
import com.nordinary.cyclus.engine.CycleSnapshot
import com.nordinary.cyclus.presenter.ReportOptions
import com.nordinary.cyclus.presenter.ReportPresenter
import com.nordinary.cyclus.presenter.ReportSection
import com.nordinary.cyclus.report.ReportFiles
import com.nordinary.cyclus.report.ReportPdf
import com.nordinary.cyclus.sample.SampleScenario
import com.nordinary.cyclus.sample.SampleScenarios
import com.nordinary.cyclus.store.AndroidDatabase
import com.nordinary.cyclus.store.StoreHolder
import com.nordinary.cyclus.store.StoreState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ReportDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = java.time.LocalDate.of(2026, 11, 22).toEpochDay().toInt()

    private fun model(include: Set<ReportSection> = ReportSection.entries.toSet()) = run {
        val d = SampleScenarios.generate(SampleScenario.DAY12, today)
        val snap = CycleSnapshot.compute(CycleData(d.logs, emptyMap(), today))
        ReportPresenter.build(snap, ReportOptions(cycles = 6, include = include, questions = "Frage?"), emptySet(), emptyList())!!
    }

    private fun pages(bytes: ByteArray): Pair<Int, Pair<Int, Int>> {
        val f = File(ctx.cacheDir, "report-test.pdf").apply { writeBytes(bytes) }
        PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
            val size = r.openPage(0).use { it.width to it.height }
            return r.pageCount to size
        }.also { f.delete() }
    }

    @Test fun pdfInBothLanguagesWithoutMetadata() {
        for (loc in listOf(Locale.GERMAN, Locale.ENGLISH)) {
            val bytes = ReportPdf.render(ctx, model(), loc, ReportPdf.Paper.A4)
            val (n, size) = pages(bytes)
            assertEquals(4, n, "summary, calendar, symptoms, notes/questions")
            assertEquals(595 to 842, size)
            val text = String(bytes, Charsets.ISO_8859_1)
            for (key in listOf("/Author", "/Title", "/Subject", "/Keywords", "/CreationDate", "/ModDate")) {
                val m = Regex("$key\\s*\\(([^)]*)\\)").find(text)
                assertTrue(m == null || m.groupValues[1].isBlank(), "$key leaks: ${m?.value}")
            }
        }
    }

    @Test fun letterSizeAndSectionsDecidePages() {
        assertEquals(612 to 792, pages(ReportPdf.render(ctx, model(), Locale.ENGLISH, ReportPdf.Paper.LETTER)).second)
        assertEquals(ReportPdf.Paper.LETTER, ReportPdf.paperFor(Locale.US))
        assertEquals(ReportPdf.Paper.A4, ReportPdf.paperFor(Locale.GERMANY))
        // lengths only (+ the questions page) → 2 pages
        assertEquals(2, pages(ReportPdf.render(ctx, model(setOf(ReportSection.LENGTHS)), Locale.GERMAN, ReportPdf.Paper.A4)).first)
    }

    @Test fun eraseRemovesSharedReports(): Unit = runBlocking {
        val keys = BackupKeyStore("cyclus_test_report")
        val coord = BackupCoordinator(ctx, File(ctx.cacheDir, "report-backup"), keys).also { it.files.ensureDir() }
        val holder = StoreHolder(ctx, coord, "rt-erase.db")
        holder.ensureOpen()
        withTimeout(20_000) { holder.state.first { it is StoreState.Ready } }
        val file = ReportFiles.write(ctx, "Zyklusbericht-test.pdf", ByteArray(10))
        assertTrue(file.exists())
        holder.erase()
        val ready = withTimeout(20_000) { holder.state.first { it is StoreState.Ready } } as StoreState.Ready
        assertFalse(file.exists(), "report PDF survived erase")
        ready.repo.close()
        AndroidDatabase.destroy(ctx, "rt-erase.db"); keys.destroy(); File(ctx.cacheDir, "report-backup").deleteRecursively()
    }
}
