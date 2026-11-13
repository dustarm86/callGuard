package com.dustin.callguard

import com.dustin.callguard.diagnostics.LogFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class LogFilterTest {
    @Test
    fun keepsOnlyMatchingLinesIgnoringCase() {
        val lines = listOf("a Telecom thing", "unrelated", "CALLGUARD: hi", "other")
        assertEquals(listOf("a Telecom thing", "CALLGUARD: hi"), LogFilter.keep(lines, listOf("telecom", "callguard"), 10))
    }

    @Test
    fun keepsTheNewestLinesWhenOverTheLimit() {
        val lines = listOf("telecom 1", "telecom 2", "telecom 3")
        assertEquals(listOf("telecom 2", "telecom 3"), LogFilter.keep(lines, listOf("telecom"), 2))
    }

    @Test
    fun namesStandbyBuckets() {
        assertEquals("active", LogFilter.standbyBucket(10))
        assertEquals("restricted", LogFilter.standbyBucket(45))
        assertEquals("other (7)", LogFilter.standbyBucket(7))
    }
}
