package com.nordinary.cyclus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nordinary.cyclus.i18n.Fmt
import com.nordinary.cyclus.insights.Num
import com.nordinary.cyclus.insights.Txt
import com.nordinary.cyclus.report.ReportPdf
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Inline plural markers (`{N|one:…|few:…}`) in the Polish, Czech and Croatian texts pick the form the number needs. */
@RunWith(AndroidJUnit4::class)
class FmtPluralDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fmt(tag: String) = Locale.forLanguageTag(tag).let { Fmt(ReportPdf.localized(ctx, it).resources, it) }
    private val nbsp = ' '

    @Test fun polishDays() {
        val f = fmt("pl")
        assertEquals("za około 1${nbsp}dzień", f.s("aboutDays", 1))
        assertEquals("za około 3${nbsp}dni", f.s("aboutDays", 3))
        assertEquals("za około 5${nbsp}dni", f.s("aboutDays", "5"))
        assertEquals("Ostatnie 3", f.s("lastN", 3))
        assertEquals("Ostatnich 6", f.s("lastN", 6))
        assertEquals("Ostatnich 12", f.s("lastN", 12))
        // a decimal takes the fraction form, also when it ends in ",0"
        assertEquals(
            "Średnio około 13,0${nbsp}dnia od owulacji do okresu (średnia, dopóki Cyclus nie pozna twojej)",
            f.txt(Txt("fromCyclesSubPop", listOf(Num(13.0, 1)))),
        )
        assertEquals(
            "Średni cykl 31,5${nbsp}dnia wobec 28,0 · cykle z${nbsp}dużym stresem w${nbsp}pierwszej połowie: 3",
            f.txt(Txt("stLenB", listOf(Num(31.5, 1), Num(28.0, 1), 3))),
        )
    }

    @Test fun czechDays() {
        val f = fmt("cs")
        assertEquals("asi za 1${nbsp}den", f.s("aboutDays", 1))
        assertEquals("asi za 4${nbsp}dny", f.s("aboutDays", 4))
        assertEquals("asi za 5${nbsp}dní", f.s("aboutDays", 5))
        // a range follows its second number
        assertEquals("3–4${nbsp}dny", f.s("daysRange", 3, 4))
        assertEquals("3–5${nbsp}dní", f.s("daysRange", 3, 5))
        assertEquals("Poslední 3", f.s("lastN", 3))
        assertEquals("Posledních 6", f.s("lastN", 6))
        // a numeric date ending a sentence keeps one period, also inside a bold marker
        assertEquals(true, f.s("testBody", "so 5. 12.").startsWith("{b}so 5. 12.{/b} Dřívější"))
    }

    @Test fun croatianDays() {
        val f = fmt("hr")
        assertEquals("za otprilike 21${nbsp}dan", f.s("aboutDays", 21))
        assertEquals("za otprilike 22${nbsp}dana", f.s("aboutDays", 22))
        assertEquals("za otprilike 11${nbsp}dana", f.s("aboutDays", 11))
        assertEquals("Zadnja 3", f.s("lastN", 3))
        assertEquals("Zadnjih 12", f.s("lastN", 12))
    }

    @Test fun literalPercentNextToMarkers() {
        // "%%" in a template with a marker stays one "%" (forms are escaped like the rest of the template)
        val f = fmt("pl")
        val out = f.s("itB", "40", "10", 3)
        assertEquals("Zanotowano w${nbsp}40% tych dni wobec 10% w${nbsp}pozostałe · cykle: 3", out)
    }

    @Test fun noMarkerLeaksInAnyLanguage() {
        for (tag in listOf("de", "en", "fr", "es-ES", "pt-PT", "it", "pl", "cs", "hr")) {
            val f = fmt(tag)
            for (key in com.nordinary.cyclus.i18n.TxRes.strings.keys) {
                val out = runCatching { f.s(key, 2, 5, 7, 9, 11) }.getOrElse { f.s(key) }
                assertFalse(Regex("\\{\\d\\|").containsMatchIn(out), "$tag/$key: $out")
            }
        }
    }
}
