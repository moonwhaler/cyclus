package com.nordinary.cyclus.report

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.nordinary.cyclus.R
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.core.Flow
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.presenter.MonthGrid
import com.nordinary.cyclus.presenter.ReportModel
import com.nordinary.cyclus.presenter.ReportSection
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Doctor Visit Pack PDF (spec §7.7): grayscale-printable, in the chosen language (independent of the UI), A4 or US
 * Letter. Rendered in memory; document metadata is blanked in place (spec m11).
 */
object ReportPdf {
    enum class Paper(val w: Int, val h: Int) { A4(595, 842), LETTER(612, 792) }

    fun paperFor(locale: Locale) = if (locale.country in setOf("US", "CA")) Paper.LETTER else Paper.A4

    fun render(context: Context, model: ReportModel, pdfLocale: Locale, paper: Paper): ByteArray {
        val cfg = Configuration(context.resources.configuration).apply { setLocale(pdfLocale) }
        val ctx = context.createConfigurationContext(cfg)
        val fmt = Fmt(ctx.resources, pdfLocale)
        val pages = Writer(ctx, fmt, paper, total = 0).also { it.draw(model); it.close() }.pageCount // pass 1: count
        val w = Writer(ctx, fmt, paper, total = pages)
        w.draw(model)
        return blankMetadata(w.finish())
    }

    /** Overwrites document-info values with spaces of the same length (keeps every byte offset of the xref valid). */
    fun blankMetadata(pdf: ByteArray): ByteArray {
        val s = String(pdf, Charsets.ISO_8859_1)
        val re = Regex("/(Title|Author|Subject|Keywords|Creator|CreationDate|ModDate)\\s*\\(((?:\\\\.|[^\\\\)])*)\\)")
        val out = pdf.copyOf()
        for (m in re.findAll(s)) {
            val g = m.groups[2]!!
            for (i in g.range) out[i] = ' '.code.toByte()
        }
        return out
    }

    private class Writer(private val ctx: Context, private val fmt: Fmt, private val paper: Paper, private val total: Int) {
        private val doc = PdfDocument()
        private var page: PdfDocument.Page? = null
        private lateinit var c: Canvas
        var pageCount = 0
            private set
        private val m = 48f
        private val contentW = paper.w - 2 * m
        private val bottom = paper.h - 56f
        private var y = m

        private val regular: Typeface = ResourcesCompat.getFont(ctx, R.font.roboto_flex) ?: Typeface.DEFAULT
        private val bold: Typeface = Typeface.create(regular, 700, false)
        private val numbers: Typeface = Typeface.create(ResourcesCompat.getFont(ctx, R.font.nunito) ?: Typeface.DEFAULT, 800, false)
        private fun paint(size: Float, tf: Typeface = regular, color: Int = INK) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; this.color = color }
        private fun s(id: Int, vararg args: Any) = ctx.getString(id, *args)
        private fun date(d: Day) = android.icu.text.DateFormat.getInstanceForSkeleton("dMMMy", fmt.locale)
            .apply { timeZone = android.icu.util.TimeZone.GMT_ZONE }.format(java.util.Date(d.toLong() * 86_400_000L))

        fun draw(r: ReportModel) {
            newPage()
            // 1 · summary
            text(s(R.string.rep_title), paint(20f, bold))
            text("${date(r.from)} – ${date(r.to)}", paint(10f, color = INK2))
            y += 6
            if (r.name.isNotBlank()) text("${s(R.string.rep_name)}: ${r.name}", paint(11f, bold))
            else text("${s(R.string.rep_name)}: ________________________________", paint(11f))
            if (r.cyclesUsed < r.cyclesRequested) text(s(R.string.rep_fewer, r.cyclesUsed, r.cyclesRequested), paint(9f, color = INK2))
            y += 6
            r.cycleLengths?.let { row(s(R.string.rep_cycles), "${it.count}"); row(s(R.string.rep_len), s(R.string.rep_spread, it.median, it.min, it.max)) }
            r.periodLengths?.let { row(s(R.string.rep_per), s(R.string.rep_spread, it.median, it.min, it.max)) }
            if (ReportSection.BLEED in r.include && r.heaviness.isNotEmpty()) row(
                s(R.string.rep_heaviness),
                listOf(Flow.LIGHT to R.string.rep_light, Flow.MEDIUM to R.string.rep_medium, Flow.HEAVY to R.string.rep_heavy, Flow.UNSPECIFIED to R.string.rep_unspec)
                    .mapNotNull { (f, id) -> r.heaviness[f]?.let { "${s(id)} $it" } }.joinToString(" · "),
            )
            if (r.topSymptoms.isNotEmpty()) row(s(R.string.rep_top), r.topSymptoms.joinToString(", ") { (k, n) -> "${fmt.item(k)} ($n)" })
            r.painMedian?.let { row(s(R.string.rep_pain), "$it / 10") }
            fun win(w: com.nordinary.cyclus.presenter.WindowMeans) =
                s(R.string.rep_win, w.period?.let { fmt.num(it, 1) } ?: "–", w.pre?.let { fmt.num(it, 1) } ?: "–", w.rest?.let { fmt.num(it, 1) } ?: "–")
            r.mood?.let { row(s(R.string.rep_mood), win(it)) }
            r.energy?.let { row(s(R.string.rep_energy), win(it)) }
            if (ReportSection.TEMP in r.include && r.tempCycles > 0) row(" ", s(R.string.rep_temp, r.tempCycles, r.tempShifts))
            if (ReportSection.LENGTHS in r.include) box(s(R.string.rep_ref))
            if (r.patterns.isNotEmpty()) {
                y += 6
                text(s(R.string.rep_pat), paint(11f, bold))
                for (p in r.patterns) text("• ${fmt.txt(p.title)}. ${fmt.txt(p.body)}", paint(10f), indent = 8f)
            }
            // 2 · bleeding calendar
            if (r.months.isNotEmpty()) {
                newPage()
                text(s(R.string.rep_cal), paint(14f, bold))
                legend()
                calendar(r.months)
            }
            // 3 · symptoms & pain
            val heat = r.heat
            if (heat != null || r.painPerCycle.isNotEmpty()) {
                newPage()
                text(s(R.string.rep_sym_title), paint(14f, bold))
                if (heat != null) { text(s(R.string.rep_heat_note), paint(9f, color = INK2)); heatmap(heat) }
                if (r.painPerCycle.isNotEmpty()) {
                    y += 10
                    text(s(R.string.rep_pain_cycle), paint(11f, bold))
                    for (p in r.painPerCycle) bar(s(R.string.rep_cycle_from, date(p.start)), p.maxPain, 10)
                }
            }
            // 4 · notes, contraception, sexual activity, questions
            val notesPage = r.notes.isNotEmpty() || r.contraception.isNotEmpty() || r.sexDays.isNotEmpty() || r.questions.isNotBlank() ||
                (ReportSection.NOTES in r.include) || (ReportSection.CONTRA in r.include) || (ReportSection.SEX in r.include)
            if (notesPage) {
                newPage()
                if (ReportSection.NOTES in r.include) {
                    text(s(R.string.rep_notes), paint(12f, bold))
                    if (r.notes.isEmpty()) text(s(R.string.rep_none), paint(10f, color = INK2))
                    for (n in r.notes) text("${date(n.day)}: ${n.text}", paint(10f), indent = 8f)
                    y += 8
                }
                if (ReportSection.CONTRA in r.include) {
                    text(s(R.string.rep_contra), paint(12f, bold))
                    if (r.contraception.isEmpty()) text(s(R.string.rep_none), paint(10f, color = INK2))
                    for (e in r.contraception) text(s(R.string.rep_contra_row, date(e.start), e.end?.let { date(it) } ?: s(R.string.rep_ongoing)), paint(10f), indent = 8f)
                    y += 8
                }
                if (ReportSection.SEX in r.include) {
                    text(s(R.string.rep_sex), paint(12f, bold))
                    for (x in r.sexDays) text("${s(R.string.rep_cycle_from, date(x.start))}: ${x.days}", paint(10f), indent = 8f)
                    y += 8
                }
                if (r.questions.isNotBlank()) {
                    text(s(R.string.rep_q), paint(12f, bold))
                    text(r.questions, paint(11f), indent = 8f)
                }
            }
        }

        // ---------- primitives ----------
        private fun newPage() {
            finishPage()
            pageCount++
            page = doc.startPage(PdfDocument.PageInfo.Builder(paper.w, paper.h, pageCount).create())
            c = page!!.canvas
            y = m
            header()
        }

        private fun ensure(h: Float) { if (y + h > bottom) newPage() }

        private fun finishPage() {
            val p = page ?: return
            val foot = paint(8f, color = INK2)
            val label = "${s(R.string.rep_foot)} · $pageCount/${if (total > 0) total else pageCount}"
            c.drawText(label, m, paper.h - 28f, foot)
            doc.finishPage(p)
            page = null
        }

        /**
         * Letterhead on every page, top right: the app's ring (same geometry as the launcher icon) with „Cyclus“ and a
         * short tagline. Small, no link, no claims.
         */
        private fun header() {
            val size = 16f
            val brand = paint(11f, bold, ACCENT)
            val tag = paint(7.5f, color = INK2)
            val name = "Cyclus"
            val tagline = s(R.string.rep_brand_tag)
            val textW = maxOf(brand.measureText(name), tag.measureText(tagline))
            val right = paper.w - m
            val x0 = right - textW - size - 6f
            val top = 22f
            val k = size / 48f // icon viewport 48
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f * k; color = ACCENT; strokeCap = Paint.Cap.ROUND }
            // open ring: arc over the top from (13,37) to (35,37), centre (24,24), r 15; dot at (24,9)
            c.drawArc(android.graphics.RectF(x0 + 9f * k, top + 9f * k, x0 + 39f * k, top + 39f * k), 130.2f, 279.6f, false, ring)
            c.drawCircle(x0 + 24f * k, top + 9f * k, 3f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
            val tx = x0 + size + 6f
            c.drawText(name, tx, top + 9.5f, brand)
            c.drawText(tagline, tx, top + 18.5f, tag)
        }

        private fun layout(t: String, p: TextPaint, w: Float) =
            StaticLayout.Builder.obtain(t, 0, t.length, p, w.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.15f).build()

        private fun text(t: String, p: TextPaint, indent: Float = 0f) {
            val l = layout(t, p, contentW - indent)
            ensure(l.height.toFloat())
            c.save(); c.translate(m + indent, y); l.draw(c); c.restore()
            y += l.height + 4
        }

        private fun row(label: String, value: String) {
            val lp = paint(10f, color = INK2); val vp = paint(10f, bold)
            val labelW = contentW * 0.42f
            val ll = layout(label, lp, labelW - 8); val vl = layout(value, vp, contentW - labelW)
            val h = maxOf(ll.height, vl.height).toFloat()
            ensure(h + 8)
            c.save(); c.translate(m, y); ll.draw(c); c.restore()
            c.save(); c.translate(m + labelW, y); vl.draw(c); c.restore()
            y += h + 4
            c.drawLine(m, y, m + contentW, y, Paint().apply { color = LINE; strokeWidth = 0.5f })
            y += 4
        }

        private fun box(t: String) {
            val p = paint(9f)
            val l = layout(t, p, contentW - 16)
            val h = l.height + 12f
            ensure(h + 8)
            y += 6
            c.drawRoundRect(RectF(m, y, m + contentW, y + h), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = INK2; strokeWidth = 0.8f })
            c.save(); c.translate(m + 8, y + 6); l.draw(c); c.restore()
            y += h + 6
        }

        private fun bar(label: String, value: Int, max: Int) {
            ensure(16f)
            val lp = paint(9f); val labelW = 150f
            c.drawText(label, m, y + 9, lp)
            val w = (contentW - labelW - 30) * value / max
            c.drawRect(m + labelW, y + 1, m + labelW + w, y + 11, Paint().apply { color = INK })
            c.drawText("$value", m + labelW + w + 4, y + 10, paint(9f, bold))
            y += 16
        }

        /** Grayscale cell by flow level: heavy solid, medium hatched, light dotted, unspecified thick outline, spotting dot. */
        private fun flowCell(f: Flow?, x: Float, top: Float, size: Float) {
            val r = RectF(x, top, x + size, top + size)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.5f; color = LINE }
            c.drawRect(r, stroke)
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
            when (f) {
                Flow.HEAVY -> c.drawRect(r, ink)
                Flow.MEDIUM -> { c.save(); c.clipRect(r); var d = -size; while (d < size) { c.drawLine(x + d, top + size, x + d + size, top, ink.apply { strokeWidth = 1.2f }); d += 3f }; c.restore() }
                Flow.LIGHT -> { var yy = top + 2; while (yy < top + size) { var xx = x + 2; while (xx < x + size) { c.drawCircle(xx, yy, 0.6f, ink); xx += 3f }; yy += 3f } }
                Flow.UNSPECIFIED -> c.drawRect(RectF(r.left + 1, r.top + 1, r.right - 1, r.bottom - 1), Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f; color = INK })
                Flow.SPOTTING -> c.drawCircle(r.centerX(), r.centerY(), 1.4f, ink)
                else -> {}
            }
        }

        private fun legend() {
            ensure(18f)
            var x = m
            val p = paint(8f, color = INK2)
            for ((f, id) in listOf(Flow.HEAVY to R.string.rep_heavy, Flow.MEDIUM to R.string.rep_medium, Flow.LIGHT to R.string.rep_light,
                Flow.UNSPECIFIED to R.string.rep_unspec, Flow.SPOTTING to R.string.rep_spotting)) {
                flowCell(f, x, y, 9f)
                val t = s(id); c.drawText(t, x + 12, y + 8, p); x += 18 + p.measureText(t) + 8
            }
            y += 18
        }

        private fun calendar(months: List<MonthGrid>) {
            val cols = 3
            val cell = 14f
            val monthW = contentW / cols
            val monthH = 26f + 6 * (cell + 2) + 10
            for (row in months.chunked(cols)) {
                ensure(monthH)
                row.forEachIndexed { i, mg ->
                    val x0 = m + i * monthW
                    val title = android.icu.text.DateFormat.getInstanceForSkeleton("MMMMy", fmt.locale)
                        .apply { timeZone = android.icu.util.TimeZone.GMT_ZONE }.format(java.util.Date(mg.firstDay.toLong() * 86_400_000L))
                    c.drawText(title, x0, y + 10, paint(9f, bold))
                    // weekday initials, Monday first, in the PDF language
                    val wdp = paint(6.5f, color = INK2)
                    for (k in 0 until 7) {
                        val name = java.time.DayOfWeek.of(k + 1).getDisplayName(java.time.format.TextStyle.NARROW, fmt.locale)
                        c.drawText(name, x0 + k * (cell + 2) + 4, y + 22, wdp)
                    }
                    val wd = (java.time.LocalDate.ofEpochDay(mg.firstDay.toLong()).dayOfWeek.value + 6) % 7
                    for (d in 0 until mg.days) {
                        val idx = d + wd
                        val cx = x0 + (idx % 7) * (cell + 2)
                        val cy = y + 26 + (idx / 7) * (cell + 2)
                        flowCell(mg.flow[d], cx, cy, cell)
                    }
                }
                y += monthH
            }
        }

        private fun heatmap(h: com.nordinary.cyclus.presenter.HeatMap) {
            val labelW = 110f
            val cw = (contentW - labelW) / h.rel.size
            ensure(14f)
            val hp = paint(8f, color = INK2)
            h.rel.forEachIndexed { i, r -> c.drawText(if (r == 0) "P1" else if (r > 0) "+$r" else "$r", m + labelW + i * cw + 2, y + 9, hp) }
            y += 14
            h.symptoms.forEachIndexed { si, sym ->
                ensure(16f)
                c.drawText(fmt.item(sym), m, y + 10, paint(9f))
                h.rates[si].forEachIndexed { i, v ->
                    val gray = (255 - (v * 215)).toInt().coerceIn(40, 255)
                    c.drawRect(m + labelW + i * cw + 1, y + 1, m + labelW + (i + 1) * cw - 1, y + 13, Paint().apply { color = Color.rgb(gray, gray, gray) })
                }
                y += 16
            }
        }

        fun close() { finishPage(); doc.close() }

        fun finish(): ByteArray {
            finishPage()
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            doc.close()
            return out.toByteArray()
        }

    }

    private const val INK = 0xFF1D1A1C.toInt()
    private const val INK2 = 0xFF5F575B.toInt()
    private const val ACCENT = 0xFFB3264E.toInt()
    private const val LINE = 0xFFBDB5B0.toInt()
}
