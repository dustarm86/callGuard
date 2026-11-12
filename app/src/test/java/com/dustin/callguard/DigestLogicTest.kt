package com.dustin.callguard

import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.digest.DigestBuilder
import com.dustin.callguard.digest.DigestKind
import com.dustin.callguard.digest.DigestSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class DigestLogicTest {
    private val zone = ZoneId.of("America/New_York")

    private fun call(number: String?, hour: Int, outcome: String = "SILENCED"): ScreenedCall {
        val millis = ZonedDateTime.of(2026, 11, 10, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
        return ScreenedCall(rawNumber = number, normalizedNumber = number, timestamp = millis, outcome = outcome)
    }

    @Test
    fun emptyDigestSaysAllQuiet() {
        val content = DigestBuilder.build(DigestKind.DAILY, emptyList(), zone)
        assertEquals(0, content.silencedCount)
        assertTrue(content.title.contains("no calls silenced"))
    }

    @Test
    fun countsOnlySilencedCalls() {
        val calls = listOf(
            call("3155550101", 10),
            call("3155550102", 10, outcome = "ALLOWED_REPEAT"),
        )
        val content = DigestBuilder.build(DigestKind.DAILY, calls, zone)
        assertEquals(1, content.silencedCount)
        assertTrue(content.title.contains("1 call silenced"))
    }

    @Test
    fun summarizesAreaCodeHourRepeatsAndHidden() {
        val calls = listOf(
            call("3155550101", 10),
            call("3155550101", 10),
            call("3155550102", 11),
            call("2125550103", 10),
            call(null, 14),
        )
        val content = DigestBuilder.build(DigestKind.DAILY, calls, zone)
        assertEquals(5, content.silencedCount)
        assertTrue(content.text, content.text.contains("top area code: 315 (3)"))
        assertTrue(content.text, content.text.contains("busiest hour: 10 am"))
        assertTrue(content.text, content.text.contains("1 number called more than once"))
        assertTrue(content.text, content.text.contains("1 hidden"))
    }

    @Test
    fun weeklyTitleMentionsSevenDays() {
        val content = DigestBuilder.build(DigestKind.WEEKLY, listOf(call("3155550101", 9)), zone)
        assertTrue(content.title.contains("7 days"))
    }

    @Test
    fun dailyDelayIsLaterTodayWhenTimeHasNotPassed() {
        val now = ZonedDateTime.of(2026, 11, 11, 9, 0, 0, 0, zone)
        val delay = DigestSchedule.delayUntilNext(now, 17 * 60 + 30)
        assertEquals((8 * 60 + 30) * 60_000L, delay)
    }

    @Test
    fun dailyDelayRollsToTomorrowWhenTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 11, 11, 18, 0, 0, 0, zone)
        val delay = DigestSchedule.delayUntilNext(now, 17 * 60 + 30)
        assertEquals((23 * 60 + 30) * 60_000L, delay)
    }

    @Test
    fun weeklyDelayFindsTheNextMatchingWeekday() {
        // 2026-11-11 is a wednesday; next sunday 9:00 is 4 days and 0 hours away from wednesday 9:00
        val now = ZonedDateTime.of(2026, 11, 11, 9, 0, 0, 0, zone)
        val delay = DigestSchedule.delayUntilNext(now, 9 * 60, DayOfWeek.SUNDAY)
        assertEquals(4 * 24 * 60 * 60_000L, delay)
    }

    @Test
    fun weeklyDelayRollsToNextWeekWhenTodayHasPassed() {
        val now = ZonedDateTime.of(2026, 11, 11, 10, 0, 0, 0, zone)
        val delay = DigestSchedule.delayUntilNext(now, 9 * 60, DayOfWeek.WEDNESDAY)
        assertEquals((7 * 24 - 1) * 60 * 60_000L, delay)
    }
}
