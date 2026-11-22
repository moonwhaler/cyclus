package com.nordinary.cyclus.ui

import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.R
import com.nordinary.cyclus.presenter.ReportOptions
import com.nordinary.cyclus.presenter.ReportPresenter
import com.nordinary.cyclus.report.ReportFiles
import com.nordinary.cyclus.report.ReportPdf
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** Doctor Visit Pack builder. State lives in memory only (not saved state): the questions text never leaves it. */
class ReportActions internal constructor(private val c: VmContext, private val model: () -> AppModel?) {
    private val repo = c.repo
    private val app = c.app


    /** Builder state lives here only (not saved state): the questions text never leaves memory. */
    val options = MutableStateFlow<ReportOptions?>(null)
    val file = MutableStateFlow<File?>(null)
    val working = MutableStateFlow(false)
    /** PDF language: the single source for the toggle and the renderer. */
    val locale = MutableStateFlow(java.util.Locale.GERMAN)

    fun start(defaultQuestions: String, uiLocale: java.util.Locale) {
        if (options.value == null) {
            options.value = ReportOptions(questions = defaultQuestions)
            locale.value = if (uiLocale.language == "en") java.util.Locale.ENGLISH else java.util.Locale.GERMAN
            // the name is remembered on this device only (encrypted DB, never exported)
            c.launch {
                repo.setting(SettingKeys.REPORT_NAME)?.let { n -> update { if (it.name.isEmpty()) it.copy(name = n) else it } }
            }
        }
    }

    fun update(change: (ReportOptions) -> ReportOptions) {
        options.value = options.value?.let(change)
    }

    /** Builds the PDF (same file is previewed and shared). */
    fun build(regionLocale: java.util.Locale) {
        options.value?.name?.trim()?.let { n -> c.launch { repo.setSetting(SettingKeys.REPORT_NAME, n.ifEmpty { null }) } }
        val m = model() ?: return
        val opts = options.value ?: return
        val locale = locale.value
        working.value = true
        c.launch {
            try {
                val pdf = withContext(Dispatchers.Default) {
                    val r = ReportPresenter.build(m.snapshot, opts, m.muted, m.episodes) ?: return@withContext null
                    val bytes = ReportPdf.render(app, r, locale, ReportPdf.paperFor(regionLocale))
                    val name = (if (locale.language == "de") "Zyklusbericht-" else "Cycle-summary-") + LocalDate.now() + ".pdf"
                    ReportFiles.write(app, name, bytes)
                }
                file.value = pdf
            } catch (e: Exception) {
                c.msg(R.string.export_failed)
            } finally {
                working.value = false
            }
        }
    }

    fun close() { file.value = null; options.value = null; ReportFiles.clear(app) }
}
