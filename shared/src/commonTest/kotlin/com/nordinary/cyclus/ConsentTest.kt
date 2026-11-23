package com.nordinary.cyclus

import com.nordinary.cyclus.backup.SettingKeys
import com.nordinary.cyclus.presenter.Consent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsentTest {
    private val t0 = 1_795_000_000_000
    private val day = 86_400_000L

    @Test fun confirmAndReadBack() {
        val v = Consent.confirm(null, t0)
        val s = mapOf(Consent.KEY to v)
        assertEquals(listOf(Consent.Record(Consent.VERSION, t0)), Consent.history(s))
        assertEquals(t0, Consent.current(s)?.atMillis)
        assertFalse(Consent.needed(s, t0 + day))
    }

    @Test fun missingOrMalformedIsNeeded() {
        assertTrue(Consent.needed(emptyMap(), t0))
        for (bad in listOf("", "1", "1|", "|5", "x|5", "1|x", "0|5", "1|0", "1|-3", "1|2|3", ";;")) {
            assertTrue(Consent.history(mapOf(Consent.KEY to bad)).isEmpty(), bad)
            assertTrue(Consent.needed(mapOf(Consent.KEY to bad), t0), bad)
        }
    }

    @Test fun askedAgainAfterAYear() {
        val s = mapOf(Consent.KEY to Consent.confirm(null, t0))
        assertFalse(Consent.needed(s, t0 + 365 * day))
        assertTrue(Consent.needed(s, t0 + 366 * day))
        // a clock set back is not a reason to ask
        assertFalse(Consent.needed(s, t0 - 30 * day))
    }

    @Test fun otherWordingIsAskedAgain() {
        val old = "${Consent.VERSION + 1}|$t0" // any version but the current one
        assertTrue(Consent.needed(mapOf(Consent.KEY to old), t0))
        assertNull(Consent.current(mapOf(Consent.KEY to old)))
        // confirming keeps the history
        val next = Consent.confirm(old, t0 + day)
        assertEquals(2, Consent.history(mapOf(Consent.KEY to next)).size)
        assertFalse(Consent.needed(mapOf(Consent.KEY to next), t0 + day))
    }

    @Test fun historyIsCapped() {
        var v: String? = null
        repeat(Consent.MAX_HISTORY + 5) { v = Consent.confirm(v, t0 + it * day) }
        val h = Consent.history(mapOf(Consent.KEY to v!!))
        assertEquals(Consent.MAX_HISTORY, h.size)
        assertEquals(t0 + (Consent.MAX_HISTORY + 4) * day, h.last().atMillis)
    }

    @Test fun neverTravelsInExportsOrSnapshots() {
        assertFalse(Consent.KEY in SettingKeys.USER_PREFS)
        assertFalse(Consent.KEY in SettingKeys.SECURITY)
    }
}
