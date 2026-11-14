package com.dustin.callguard

import com.dustin.callguard.carrierdata.CarrierProgress
import com.dustin.callguard.carrierdata.CountingInputStream
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierProgressTest {
    private fun progress(done: Long, total: Long?, elapsedMs: Long) =
        CarrierProgress("nanpa reports", "block report", 2, 2, done, total, elapsedMs)

    @Test
    fun theFractionFillsTheBarAndStaysInRange() {
        assertEquals(0.25f, progress(3_000_000, 12_000_000, 5_000).fraction!!, 0.0001f)
        assertEquals(1f, progress(13_000_000, 12_000_000, 5_000).fraction!!, 0.0001f)
        assertNull(progress(1_000, null, 5_000).fraction)
    }

    @Test
    fun theTimeLeftComesFromTheSpeedSoFar() {
        // 3 MB in 6 seconds is 500 KB/s, so the last 9 MB takes 18 seconds
        val p = progress(3_000_000, 12_000_000, 6_000)
        assertEquals(500_000L, p.bytesPerSecond)
        assertEquals(18L, p.etaSeconds)
    }

    @Test
    fun noEstimateIsShownTooEarlyOrWithoutASize() {
        assertNull(progress(100_000, 12_000_000, 800).bytesPerSecond)
        assertNull(progress(500_000, 12_000_000, 1_500).etaSeconds)
        assertNull(progress(500_000, null, 9_000).etaSeconds)
    }

    @Test
    fun durationsReadInPlainUnits() {
        assertEquals("45 s", CarrierProgress.duration(45))
        assertEquals("2 min 5 s", CarrierProgress.duration(125))
    }

    @Test
    fun theLinesShowSizePercentSpeedAndTimeLeft() {
        val lines = progress(3_000_000, 12_000_000, 6_000).lines()
        assertEquals("downloading the block report, file 2 of 2, from nanpa reports", lines[0])
        assertEquals("3.0 MB of 12.0 MB · 25% · 500 KB/s · about 18 s left", lines[1])
    }

    @Test
    fun whenEverythingHasArrivedItSaysTheFileIsBeingBuilt() {
        val lines = progress(12_000_000, 12_000_000, 20_000).lines()
        assertTrue(lines[1].startsWith("all 12.0 MB downloaded, now building"))
    }

    @Test
    fun anUnknownSizeStillShowsHowMuchHasArrived() {
        assertEquals("1.5 MB so far (the size isn't known)", progress(1_500_000, null, 3_000).lines()[1])
    }

    @Test
    fun theCountingStreamReportsEachChunkAndTheEnd() {
        val seen = mutableListOf<Pair<Long, Long?>>()
        val stream = CountingInputStream(ByteArrayInputStream(ByteArray(100)), 100, minIntervalMs = 0) { done, total -> seen += done to total }
        val buffer = ByteArray(30)
        while (stream.read(buffer) >= 0) Unit
        assertEquals(listOf(30L to 100L, 60L to 100L, 90L to 100L, 100L to 100L), seen)
    }

    @Test
    fun theCountingStreamStillReportsTheLastByteWhenReportsAreThrottled() {
        val seen = mutableListOf<Long>()
        val stream = CountingInputStream(ByteArrayInputStream(ByteArray(100)), 100, minIntervalMs = 60_000) { done, _ -> seen += done }
        val buffer = ByteArray(10)
        while (stream.read(buffer) >= 0) Unit
        assertEquals(100L, seen.last())
        assertTrue(seen.size < 10)
    }
}
