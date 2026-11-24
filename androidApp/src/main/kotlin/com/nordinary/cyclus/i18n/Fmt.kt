package com.nordinary.cyclus.i18n

import android.content.res.Resources
import android.icu.text.DateFormat
import android.icu.text.PluralRules
import android.icu.util.TimeZone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import com.nordinary.cyclus.R
import com.nordinary.cyclus.core.Day
import com.nordinary.cyclus.insights.Num
import com.nordinary.cyclus.insights.Txt
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * Locale-aware rendering of engine output: dates (same CLDR skeletons as the prototype's Intl formats),
 * numbers (rounding half away from zero like Intl) and [Txt] keys → generated string resources.
 */
class Fmt(private val res: Resources, val locale: Locale) {
    private fun df(skeleton: String) = DateFormat.getInstanceForSkeleton(skeleton, locale).apply { timeZone = TimeZone.GMT_ZONE }
    private val fMd = df("MMMd")
    /** The locale's own day+month pattern ("d. MMM", "MMM d", "d MMM", "d 'de' MMM" …): decides how a range reads. */
    private val mdPattern = (fMd as? android.icu.text.SimpleDateFormat)?.toPattern() ?: "MMM d"
    private val dayFirst = mdPattern.indexOf('d').let { d -> d >= 0 && d < mdPattern.indexOf('M') }
    /** What follows the day number before the month: "." in German, nothing in French, Spanish, Italian, Portuguese. */
    private val daySuffix = mdPattern.substring(mdPattern.indexOf('d') + 1).takeWhile { it != ' ' && it != 'M' && it != '\'' && it != '/' }
    private val fWmd = df("EEEMMMd")
    private val fW = df("EEE")
    private val fLong = df("EEEEdMMMM")
    private fun date(day: Day) = Date(day.toLong() * 86_400_000L)

    /** "22. Nov." / "Nov 22" */
    fun md(day: Day): String = fMd.format(date(day))
    /** "So., 22. Nov." / "Sun, Nov 22" */
    fun wmd(day: Day): String = fWmd.format(date(day))
    /** "So" / "Sun" (no trailing dot, as in the outlook header) */
    fun weekday(day: Day): String = fW.format(date(day)).replace(".", "").let { w ->
        // pt-PT's short form is the full word ("terça", "sábado"): narrow column headers need three letters. Not for
        // Polish: "niedz" cut to "nie" would read as "no"
        if (w.length > 4 && locale.language == "pt") w.take(3) else w
    }
    fun long(day: Day): String = fLong.format(date(day))
    /** A moment in the device's time zone, with year and time: "23. Nov. 2026, 14:05" / "Nov 23, 2026, 2:05 PM". */
    fun dateTime(epochMillis: Long): String =
        DateFormat.getInstanceForSkeleton("yMMMdjmm", locale).apply { timeZone = TimeZone.getDefault() }.format(Date(epochMillis))
    fun dayOfMonth(day: Day): Int = java.time.LocalDate.ofEpochDay(day.toLong()).dayOfMonth

    /** Prototype `range(a, b)`: "3.–8. Dez." / "Dec 3–8" / "3–8 déc." / "28. Nov. – 3. Dez." */
    fun range(a: Day, b: Day): String {
        if (a == b) return md(a)
        val la = java.time.LocalDate.ofEpochDay(a.toLong())
        val lb = java.time.LocalDate.ofEpochDay(b.toLong())
        if (la.month == lb.month && la.year == lb.year) return if (dayFirst) "${la.dayOfMonth}$daySuffix–${md(b)}" else "${md(a)}–${lb.dayOfMonth}"
        return "${md(a)} – ${md(b)}"
    }

    fun num(value: Double, decimals: Int): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = decimals; maximumFractionDigits = decimals; roundingMode = RoundingMode.HALF_UP
    }.format(value)

    fun temp(c: Double): String = num(c, 2)

    fun s(key: String, vararg args: Any): String {
        val id = TxRes.strings[key] ?: return key
        val out = if (args.isEmpty()) res.getString(id) else {
            val raw = res.getString(id)
            if (PLURAL_MARK.containsMatchIn(raw)) String.format(locale, inlinePlurals(raw, args), *args) else res.getString(id, *args)
        }
        // "… 24. Nov.." → "… 24. Nov." when an abbreviated date ends a sentence (keeps real ellipses)
        return if (args.isEmpty()) out else DOUBLE_DOT.replace(out) { ".${it.groupValues[1]}" }
    }

    private val pluralRules by lazy { PluralRules.forLocale(locale) }

    /**
     * Inline plural forms for languages whose nouns follow the number (Polish, Czech, Croatian: "1 dzień", "2 dni",
     * "5 dni"): `{2|one:den|few:dny|other:dní}` picks the form for argument 2 by the locale's CLDR rules. Missing
     * forms fall back to other, then many. Only translations use it; the source texts have no markers.
     */
    private fun inlinePlurals(raw: String, args: Array<out Any>): String = PLURAL_MARK.replace(raw) { m ->
        val forms = m.groupValues[2].split('|').associate { it.substringBefore(':') to it.substringAfter(':') }
        val a = args.getOrNull(m.groupValues[1].toInt() - 1)
        val n: Double? = when (a) {
            is Number -> a.toDouble()
            is String -> runCatching { NumberFormat.getNumberInstance(locale).parse(a.trim())?.toDouble() }.getOrNull()
            else -> null
        }
        // "13,0" is a decimal for CLDR ("13,0 dnia", not "13,0 dni"): count the visible fraction digits
        val sep = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
        val fraction = (a as? String)?.substringAfter(sep, "")?.takeWhile { it.isDigit() }?.length ?: 0
        val cat = when {
            n == null -> "other"
            fraction > 0 && android.os.Build.VERSION.SDK_INT >= 31 -> pluralRules.select(
                android.icu.number.NumberFormatter.withLocale(locale)
                    .precision(android.icu.number.Precision.fixedFraction(fraction)).format(n),
            )
            else -> pluralRules.select(n)
        }
        // forms arrive escaped like the rest of the template ("%%")
        forms[cat] ?: forms["other"] ?: forms["many"] ?: forms.values.last()
    }

    fun plural(key: String, n: Int): String = res.getQuantityString(TxRes.plurals.getValue(key), n, n)

    /** Label of an engine item key (symptom, feeling, tag, flow, mucus, pain region). */
    fun item(key: String): String {
        if (key == com.nordinary.cyclus.core.Flow.UNSPECIFIED.key) return res.getString(R.string.flow_unspecified)
        val id = TxRes.sym[key] ?: TxRes.emo[key] ?: TxRes.tag[key] ?: TxRes.flow[key] ?: TxRes.mucus[key] ?: TxRes.region[key]
        return id?.let { res.getString(it) } ?: key
    }

    /** String with {b}…{/b} markers (from the prototype's inline <b>) as bold spans. */
    fun rich(key: String, vararg args: Any): AnnotatedString {
        val raw = s(key, *args)
        return androidx.compose.ui.text.buildAnnotatedString {
            var rest = raw
            while (true) {
                val a = rest.indexOf("{b}")
                val b = if (a >= 0) rest.indexOf("{/b}", a) else -1
                if (a < 0 || b < 0) { append(rest); break }
                append(rest.substring(0, a))
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                append(rest.substring(a + 3, b)); pop()
                rest = rest.substring(b + 4)
            }
        }
    }

    fun array(key: String): List<String> = when (key) {
        "wk" -> res.getStringArray(R.array.tx_wk).toList()
        "method" -> res.getStringArray(R.array.tx_method).toList()
        else -> emptyList()
    }

    fun monthYear(year: Int, month: Int): String =
        DateFormat.getInstanceForSkeleton("MMMMy", locale).apply { timeZone = TimeZone.GMT_ZONE }
            .format(date(java.time.LocalDate.of(year, month, 1).toEpochDay().toInt()))

    fun monthShort(day: Day): String = df("MMM").format(date(day)).replace(".", "")

    fun txt(t: Txt): String {
        if (t.key in TxRes.plurals) return plural(t.key, (t.args.first() as Number).toInt())
        val args = t.args.map { a ->
            when (a) {
                is Txt -> txt(a)
                is Num -> num(a.value, a.decimals)
                is String -> item(a)
                is Int -> a.toString()
                else -> a.toString()
            }
        }.toTypedArray()
        val out = s(t.key, *args)
        return if (t.lowercase) out.lowercase(locale) else out
    }
}

/** "24. Nov.." and numeric "5. 12.." (Czech, Croatian), also around a bold end marker ("5. 12.{/b}."). */
private val DOUBLE_DOT = Regex("(?<=[\\p{L}\\d])\\.(\\{/b\\})?\\.(?!\\.)")
/** `{2|one:den|few:dny|other:dní}`: see [Fmt.inlinePlurals]. */
private val PLURAL_MARK = Regex("\\{(\\d)\\|([a-z]+:[^{}|]*(?:\\|[a-z]+:[^{}|]*)*)\\}")

@Composable
fun rememberFmt(): Fmt {
    val res = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(res, locale) { Fmt(res, locale) }
}
