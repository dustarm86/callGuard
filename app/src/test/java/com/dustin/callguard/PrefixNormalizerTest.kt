package com.dustin.callguard

import com.dustin.callguard.screening.PrefixNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrefixNormalizerTest {

    @Test
    fun acceptsEightAndNineDigits() {
        assertEquals("31555501", PrefixNormalizer.normalize("315 555 01"))
        assertEquals("315555010", PrefixNormalizer.normalize("(315) 555-010"))
    }

    @Test
    fun dropsLeadingCountryCode() {
        assertEquals("31555501", PrefixNormalizer.normalize("+1 315 555 01"))
        assertEquals("31555501", PrefixNormalizer.normalize("1-315-555-01"))
    }

    @Test
    fun rejectsTooShortOrFullNumbers() {
        assertNull(PrefixNormalizer.normalize("315555"))
        assertNull(PrefixNormalizer.normalize("3155550123"))
        assertNull(PrefixNormalizer.normalize(""))
        assertNull(PrefixNormalizer.normalize(null))
    }

    @Test
    fun formatsWithPlaceholders() {
        assertEquals("(315) 555-01xx", PrefixNormalizer.format("31555501"))
        assertEquals("(315) 555-010x", PrefixNormalizer.format("315555010"))
    }
}
