package com.dustin.callguard

import com.dustin.callguard.screening.NumberNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberNormalizerTest {

    @Test
    fun stripsFormattingAndCountryCode() {
        assertEquals("2029805513", NumberNormalizer.normalize("+1 (202) 980-5513"))
        assertEquals("2029805513", NumberNormalizer.normalize("12029805513"))
        assertEquals("2029805513", NumberNormalizer.normalize("202-980-5513"))
    }

    @Test
    fun keepsShortCodesAsIs() {
        assertEquals("21391", NumberNormalizer.normalize("21391"))
        assertEquals("302489", NumberNormalizer.normalize("302489"))
    }

    @Test
    fun returnsNullForMissingOrNonNumericInput() {
        assertNull(NumberNormalizer.normalize(null))
        assertNull(NumberNormalizer.normalize(""))
        assertNull(NumberNormalizer.normalize("   "))
        assertNull(NumberNormalizer.normalize("unknown"))
    }

    @Test
    fun internationalNumbersKeepLastTenDigits() {
        assertEquals("7911123456", NumberNormalizer.normalize("+44 7911 123456"))
    }

    @Test
    fun formatsTenDigitNumbers() {
        assertEquals("(202) 980-5513", NumberNormalizer.format("2029805513"))
        assertEquals("21391", NumberNormalizer.format("21391"))
    }
}
