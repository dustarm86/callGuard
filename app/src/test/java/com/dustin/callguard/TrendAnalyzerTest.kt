package com.dustin.callguard

import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.data.VerificationStatus
import com.dustin.callguard.trends.TrendAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TrendAnalyzerTest {
    private val zone = ZoneId.of("America/New_York")

    // 2026-11-09 is a monday, 2026-11-10 a tuesday
    private fun call(
        number: String?,
        day: Int = 9,
        hour: Int = 10,
        outcome: String = "SILENCED",
        verification: Int = VerificationStatus.NOT_VERIFIED,
    ): ScreenedCall {
        val millis = ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
        return ScreenedCall(
            rawNumber = number,
            normalizedNumber = number,
            timestamp = millis,
            outcome = outcome,
            verificationStatus = verification,
        )
    }

    @Test
    fun emptyLogGivesZeroes() {
        val trends = TrendAnalyzer.analyze(emptyList(), zone, null)
        assertEquals(0, trends.silencedCount)
        assertEquals(24, trends.callsByHour.size)
        assertEquals(7, trends.callsByWeekday.size)
        assertTrue(trends.topAreaCodes.isEmpty())
    }

    @Test
    fun ignoresCallsThatWereNotSilenced() {
        val trends = TrendAnalyzer.analyze(
            listOf(call("3155550101"), call("3155550102", outcome = "ALLOWED_REPEAT")),
            zone,
            null,
        )
        assertEquals(1, trends.silencedCount)
    }

    @Test
    fun ranksAreaCodesAndFindsRepeats() {
        val calls = listOf(
            call("3155550101"), call("3155550101"), call("3155550102"),
            call("2125550103"), call(null),
        )
        val trends = TrendAnalyzer.analyze(calls, zone, null)
        assertEquals("315", trends.topAreaCodes.first().areaCode)
        assertEquals(3, trends.topAreaCodes.first().count)
        assertEquals("3155550101", trends.topRepeatNumbers.single().number)
        assertEquals(1, trends.hiddenCount)
        assertEquals(3, trends.distinctNumbers)
    }

    @Test
    fun bucketsByHourAndWeekday() {
        val calls = listOf(call("3155550101", day = 9, hour = 10), call("3155550102", day = 10, hour = 15))
        val trends = TrendAnalyzer.analyze(calls, zone, null)
        assertEquals(1, trends.callsByHour[10])
        assertEquals(1, trends.callsByHour[15])
        assertEquals(1, trends.callsByWeekday[0])
        assertEquals(1, trends.callsByWeekday[1])
    }

    @Test
    fun countsNeighborSpoofsAndFailedVerification() {
        val calls = listOf(
            call("3155559999"),
            call("2125550103", verification = VerificationStatus.FAILED),
            call("3166660000"),
        )
        val trends = TrendAnalyzer.analyze(calls, zone, "(315) 555-1234")
        assertEquals(1, trends.neighborSpoofCount)
        assertEquals(1, trends.failedVerificationCount)
    }

    @Test
    fun neighborCountIsZeroWithoutOwnNumber() {
        val trends = TrendAnalyzer.analyze(listOf(call("3155559999")), zone, "")
        assertEquals(0, trends.neighborSpoofCount)
    }
}
