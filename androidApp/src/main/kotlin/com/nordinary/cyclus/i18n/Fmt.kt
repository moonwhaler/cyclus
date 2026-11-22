package com.nordinary.cyclus.i18n

import android.content.res.Resources
import android.icu.text.DateFormat
import android.icu.util.TimeZone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
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
    val isDe: Boolean = locale.language == "de"
    private fun df(skeleton: String) = DateFormat.getInstanceForSkeleton(skeleton, locale).apply { timeZone = TimeZone.GMT_ZONE }
    private val fMd = df("MMMd")
    private val fWmd = df("EEEMMMd")
    private val fW = df("EEE")
    private val fLong = df("EEEEdMMMM")
    private fun date(day: Day) = Date(day.toLong() * 86_400_000L)

    /** "22. Nov." / "Nov 22" */
    fun md(day: Day): String = fMd.format(date(day))
    /** "So., 22. Nov." / "Sun, Nov 22" */
    fun wmd(day: Day): String = fWmd.format(date(day))
    /** "So" / "Sun" (no trailing dot, as in the outlook header) */
    fun weekday(day: Day): String = fW.format(date(day)).replace(".", "")
    fun long(day: Day): String = fLong.format(date(day))
    fun dayOfMonth(day: Day): Int = java.time.LocalDate.ofEpochDay(day.toLong()).dayOfMonth

    /** Prototype `range(a, b)`: "3.–8. Dez." / "Dec 3–8" / "28. Nov. – 3. Dez." */
    fun range(a: Day, b: Day): String {
        if (a == b) return md(a)
        val la = java.time.LocalDate.ofEpochDay(a.toLong())
        val lb = java.time.LocalDate.ofEpochDay(b.toLong())
        if (la.month == lb.month && la.year == lb.year) return if (isDe) "${la.dayOfMonth}.–${md(b)}" else "${md(a)}–${lb.dayOfMonth}"
        return "${md(a)} – ${md(b)}"
    }

    fun num(value: Double, decimals: Int): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = decimals; maximumFractionDigits = decimals; roundingMode = RoundingMode.HALF_UP
    }.format(value)

    fun temp(c: Double): String = num(c, 2)

    fun s(key: String, vararg args: Any): String {
        val id = TxRes.strings[key] ?: return key
        return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
    }

    fun plural(key: String, n: Int): String = res.getQuantityString(TxRes.plurals.getValue(key), n, n)

    /** Label of an engine item key (symptom, feeling, tag, flow, mucus, pain region). */
    fun item(key: String): String {
        val id = TxRes.sym[key] ?: TxRes.emo[key] ?: TxRes.tag[key] ?: TxRes.flow[key] ?: TxRes.mucus[key] ?: TxRes.region[key]
        return id?.let { res.getString(it) } ?: key
    }

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

@Composable
fun rememberFmt(): Fmt {
    val res = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(res, locale) { Fmt(res, locale) }
}
