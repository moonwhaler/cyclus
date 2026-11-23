package com.nordinary.cyclus.ui.about

import kotlin.test.Test
import kotlin.test.assertEquals

class ReflowTest {
    @Test fun joinsWrappedLinesKeepsParagraphsAndListItems() {
        val text = """
            Copyright (c) 2008, ZETETIC LLC
            All rights reserved.

            Redistribution and use are permitted
            provided that:
                * Redistributions of source code must
                  retain the notice.
                * Redistributions in binary form must
                  reproduce the notice.

               1. Definitions.
               (a) You must give
               a copy.
        """.trimIndent()
        assertEquals(
            "Copyright (c) 2008, ZETETIC LLC All rights reserved.\n\n" +
                "Redistribution and use are permitted provided that:\n" +
                "* Redistributions of source code must retain the notice.\n" +
                "* Redistributions in binary form must reproduce the notice.\n\n" +
                "1. Definitions.\n(a) You must give a copy.",
            reflow(text),
        )
    }

    @Test fun windowsLineEndings() = assertEquals("a b\n\nc", reflow("a\r\nb\r\n\r\nc\r\n"))
}
