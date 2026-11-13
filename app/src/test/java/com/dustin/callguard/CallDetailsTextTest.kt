package com.dustin.callguard

import com.dustin.callguard.screening.CallDetailsText
import org.junit.Assert.assertEquals
import org.junit.Test

class CallDetailsTextTest {
    @Test
    fun namesDirectionPresentationAndVerification() {
        assertEquals("incoming", CallDetailsText.direction(0))
        assertEquals("restricted (caller hid the number)", CallDetailsText.presentation(2))
        assertEquals("failed", CallDetailsText.verification(2))
        assertEquals("other (9)", CallDetailsText.presentation(9))
    }

    @Test
    fun decodesKnownPropertyBitsAndKeepsTheRawMask() {
        assertEquals("0x0", CallDetailsText.properties(0))
        assertEquals("0x18 (wifi calling, high definition audio)", CallDetailsText.properties(0x18))
        assertEquals("0x2", CallDetailsText.properties(0x2))
    }
}
