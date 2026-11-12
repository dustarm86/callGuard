package com.dustin.callguard

import com.dustin.callguard.screening.NanpValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NanpValidatorTest {

    @Test
    fun realLookingNumberHasNoProblems() {
        assertTrue(NanpValidator.problems("+13159488351", "3159488351").isEmpty())
        assertTrue(NanpValidator.problems("(212) 555-1234", "2125551234").isEmpty())
    }

    @Test
    fun flagsAreaCodeStartingWithZeroOrOne() {
        assertEquals(1, NanpValidator.problems("0152345678", "0152345678").size)
        assertTrue(NanpValidator.problems("1155550123", "1155550123").first().contains("area code"))
    }

    @Test
    fun flagsServiceCodes() {
        assertTrue(NanpValidator.problems("3115550123", "3115550123").any { it.contains("service code") })
        assertTrue(NanpValidator.problems("3159110123", "3159110123").any { it.contains("service code") })
    }

    @Test
    fun flagsExchangeStartingWithZeroOrOne() {
        assertTrue(NanpValidator.problems("3150551234", "3150551234").any { it.contains("exchange") })
        assertTrue(NanpValidator.problems("3151551234", "3151551234").any { it.contains("exchange") })
    }

    @Test
    fun flagsFictionalFiveFiveFive() {
        assertTrue(NanpValidator.problems("2125550150", "2125550150").any { it.contains("fiction") })
        assertTrue(NanpValidator.problems("2125551234", "2125551234").isEmpty())
    }

    @Test
    fun skipsInternationalAndShortNumbers() {
        assertTrue(NanpValidator.problems("+442071838750", "2071838750").isEmpty())
        assertTrue(NanpValidator.problems("011442071838750", "2071838750").isEmpty())
        assertTrue(NanpValidator.problems("21391", "21391").isEmpty())
        assertTrue(NanpValidator.problems(null, null).isEmpty())
    }
}
