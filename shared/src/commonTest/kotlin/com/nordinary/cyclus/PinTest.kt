package com.nordinary.cyclus

import com.nordinary.cyclus.security.Pin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinTest {
    @Test fun hashAndVerify() {
        val (salt, hash) = Pin.hash("482915")
        assertTrue(Pin.verify("482915", salt, hash))
        assertFalse(Pin.verify("482916", salt, hash))
        assertFalse(Pin.verify("48291", salt, hash))
        assertFalse(Pin.verify("abcdef", salt, hash))
        // a different salt gives a different hash
        assertTrue(Pin.hash("482915").second != hash)
    }

    @Test fun backoffGrows() {
        assertEquals(0, Pin.backoffMillis(4))
        assertEquals(30_000, Pin.backoffMillis(5))
        assertTrue(Pin.backoffMillis(9) > Pin.backoffMillis(8))
    }
}
