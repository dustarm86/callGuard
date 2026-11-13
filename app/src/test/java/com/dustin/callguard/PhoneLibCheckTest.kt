package com.dustin.callguard

import com.dustin.callguard.screening.PhoneLibCheck
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLibCheckTest {
    @Test
    fun anOrdinaryUsNumberIsValidAndFixedLineOrMobile() {
        val lines = PhoneLibCheck.lines("3159488351")
        assertTrue(lines.first().startsWith("valid north american number"))
        assertTrue(lines.any { it == "country: United States" })
        assertTrue(lines.any { it.startsWith("type: fixed line or mobile") })
    }

    @Test
    fun aTollFreeNumberIsRecognized() {
        assertTrue(PhoneLibCheck.lines("8338083984").any { it == "type: toll-free" })
    }

    @Test
    fun aJamaicanNumberNamesItsCountry() {
        assertTrue(PhoneLibCheck.lines("8765550123").any { it.startsWith("country: Jamaica") })
    }

    @Test
    fun anImpossibleNumberIsReportedAsInvalid() {
        val first = PhoneLibCheck.lines("1115550123").first()
        assertTrue(first.contains("not a valid") || first.contains("can't read"))
    }
}
