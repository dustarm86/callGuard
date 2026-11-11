package com.dustin.callguard

import com.dustin.callguard.data.NumberTag
import com.dustin.callguard.screening.SuspicionInput
import com.dustin.callguard.screening.SuspicionLevel
import com.dustin.callguard.screening.SuspicionScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspicionScorerTest {

    private fun input(
        number: String = "3159488351",
        callCount: Int = 1,
        verificationFailed: Boolean = false,
        ownNumber: String? = "3155551234",
        allowedNumbers: List<String> = emptyList(),
        tag: String? = null,
    ) = SuspicionInput(number, callCount, verificationFailed, ownNumber, allowedNumbers, tag)

    @Test
    fun plainUnknownCallerIsLow() {
        val result = SuspicionScorer.score(input())
        assertEquals(SuspicionLevel.LOW, result.level)
    }

    @Test
    fun neighborOfOwnNumberIsMediumOnItsOwn() {
        val result = SuspicionScorer.score(input(number = "3155559999"))
        assertEquals(SuspicionLevel.MEDIUM, result.level)
        assertTrue(result.reasons.any { it.contains("neighbor spoofing") })
    }

    @Test
    fun neighborThatCallsTwiceIsHigh() {
        val result = SuspicionScorer.score(input(number = "3155559999", callCount = 2))
        assertEquals(SuspicionLevel.HIGH, result.level)
    }

    @Test
    fun failedVerificationIsHigh() {
        val result = SuspicionScorer.score(input(verificationFailed = true))
        assertEquals(SuspicionLevel.HIGH, result.level)
    }

    @Test
    fun ownNumberCallingIsFlagged() {
        val result = SuspicionScorer.score(input(number = "3155551234"))
        assertTrue(result.reasons.any { it.contains("your own number") })
    }

    @Test
    fun neighborOfAllowedNumberAddsPoints() {
        val plain = SuspicionScorer.score(input(number = "2125550100", ownNumber = null))
        val neighbor = SuspicionScorer.score(
            input(number = "2125550100", ownNumber = null, allowedNumbers = listOf("2125550199"))
        )
        assertTrue(neighbor.score > plain.score)
    }

    @Test
    fun missingOwnNumberSkipsNeighborCheck() {
        val result = SuspicionScorer.score(input(number = "3155559999", ownNumber = null))
        assertFalse(result.reasons.any { it.contains("neighbor") })
    }

    @Test
    fun tagsOverrideEverything() {
        assertEquals(0, SuspicionScorer.score(input(verificationFailed = true, tag = NumberTag.KNOWN)).score)
        assertEquals(100, SuspicionScorer.score(input(tag = NumberTag.SPAM)).score)
    }

    @Test
    fun sharesPrefixNeedsTenDigits() {
        assertTrue(SuspicionScorer.sharesPrefix("3155551234", "3155559999"))
        assertFalse(SuspicionScorer.sharesPrefix("3155551234", "3156551234"))
        assertFalse(SuspicionScorer.sharesPrefix("21391", "21392"))
        assertFalse(SuspicionScorer.sharesPrefix(null, "3155551234"))
    }
}
