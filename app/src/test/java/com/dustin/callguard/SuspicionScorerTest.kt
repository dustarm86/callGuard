package com.dustin.callguard

import com.dustin.callguard.data.NumberTag
import com.dustin.callguard.screening.SuspicionInput
import com.dustin.callguard.screening.SuspicionLevel
import com.dustin.callguard.screening.SuspicionScorer
import com.dustin.callguard.screening.BlockStatus
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
        nanpProblems: List<String> = emptyList(),
    ) = SuspicionInput(number, callCount, verificationFailed, ownNumber, allowedNumbers, tag, nanpProblems)

    private fun withExtras(
        blockReportedNumbers: Int = 0,
        callbackRiskPlace: String? = null,
        contactNeighbors: Int = 0,
    ) = input().copy(
        blockReportedNumbers = blockReportedNumbers,
        callbackRiskPlace = callbackRiskPlace,
        contactNeighbors = contactNeighbors,
    )

    @Test
    fun reportedNeighborsInTheBlockRaiseTheScoreInTwoSteps() {
        val base = SuspicionScorer.score(withExtras()).score
        assertEquals(base, SuspicionScorer.score(withExtras(blockReportedNumbers = 2)).score)
        assertEquals(base + 10, SuspicionScorer.score(withExtras(blockReportedNumbers = 3)).score)
        assertEquals(base + 20, SuspicionScorer.score(withExtras(blockReportedNumbers = 10)).score)
        assertTrue(SuspicionScorer.score(withExtras(blockReportedNumbers = 3)).reasons.any { it.contains("same block") })
    }

    @Test
    fun callbackRiskAreaCodesAddPointsAndNameTheCountry() {
        val base = SuspicionScorer.score(withExtras()).score
        val result = SuspicionScorer.score(withExtras(callbackRiskPlace = "Jamaica"))
        assertEquals(base + 15, result.score)
        assertTrue(result.reasons.any { it.contains("Jamaica") })
    }

    @Test
    fun contactNeighborsAddALittleAndAreCountedWithoutTheNumberItself() {
        val base = SuspicionScorer.score(withExtras()).score
        assertEquals(base + 10, SuspicionScorer.score(withExtras(contactNeighbors = 4)).score)
        assertEquals(1, SuspicionScorer.neighborCount("3159488351", listOf("3159488351", "3159480000", "2125550123")))
    }

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
    fun impossibleNumberIsHigh() {
        val result = SuspicionScorer.score(input(nanpProblems = listOf("exchange starts with 0")))
        assertEquals(SuspicionLevel.HIGH, result.level)
        assertTrue(result.reasons.any { it.contains("not a real number") })
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

    @Test
    fun unassignedBlockIsHigh() {
        val result = SuspicionScorer.score(input().copy(blockStatus = BlockStatus.UNASSIGNED))
        assertEquals(SuspicionLevel.HIGH, result.level)
        assertTrue(result.reasons.any { "not assigned to any carrier" in it })
    }

    @Test
    fun notInServiceBlockAddsPointsButAssignedAndUnknownDoNot() {
        val plain = SuspicionScorer.score(input()).score
        assertEquals(plain + 25, SuspicionScorer.score(input().copy(blockStatus = BlockStatus.NOT_IN_SERVICE)).score)
        assertEquals(plain, SuspicionScorer.score(input().copy(blockStatus = BlockStatus.ASSIGNED)).score)
        assertEquals(plain, SuspicionScorer.score(input().copy(blockStatus = BlockStatus.UNKNOWN)).score)
    }

    @Test
    fun blockStatusIsIgnoredWhenTheNumberIsAlreadyImpossible() {
        val impossible = input().copy(nanpProblems = listOf("exchange starts with 0"))
        val withBlock = impossible.copy(blockStatus = BlockStatus.UNASSIGNED)
        assertEquals(SuspicionScorer.score(impossible).score, SuspicionScorer.score(withBlock).score)
    }

    @Test
    fun complaintsRaiseTheScoreByHowManyThereAre() {
        val plain = SuspicionScorer.score(input()).score
        assertEquals(plain + 20, SuspicionScorer.score(input().copy(complaintCount = 1)).score)
        assertEquals(plain + 20, SuspicionScorer.score(input().copy(complaintCount = 2)).score)
        assertEquals(plain + 35, SuspicionScorer.score(input().copy(complaintCount = 3)).score)
    }
}
