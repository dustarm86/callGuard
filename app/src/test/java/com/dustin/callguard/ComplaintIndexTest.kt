package com.dustin.callguard

import com.dustin.callguard.complaints.ComplaintIndex
import com.dustin.callguard.complaints.ComplaintReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComplaintIndexTest {
    private fun report(number: String, day: String, robocall: Boolean = true, subject: String = "Other") =
        ComplaintReport(number, day, robocall, subject)

    private val index = ComplaintIndex.Builder().apply {
        add(report("3155550142", "2026-11-09", subject = "Warranties"))
        add(report("3155550142", "2026-11-11", subject = "Warranties"))
        add(report("3155550142", "2026-11-10", robocall = false, subject = "Other"))
        add(report("2125550103", "2026-11-08"))
    }.build(dayCount = 3)

    @Test
    fun groupsReportsByNumber() {
        val stats = index.lookup("3155550142")!!
        assertEquals(3, stats.count)
        assertEquals(2, stats.robocalls)
        assertEquals("2026-11-11", stats.lastDay)
        assertEquals(listOf("Warranties" to 2, "Other" to 1), stats.subjects)
    }

    @Test
    fun summarizesTheWholeIndex() {
        assertEquals(4, index.reportCount)
        assertEquals(3, index.dayCount)
        assertEquals("2026-11-11", index.newestDay)
    }

    @Test
    fun countsReportedNeighborsInTheSameBlock() {
        val blocks = ComplaintIndex.Builder().apply {
            add(report("3155550101", "2026-11-09"))
            add(report("3155550102", "2026-11-09", robocall = false))
            add(report("3155550102", "2026-11-10"))
            add(report("3155550103", "2026-11-10"))
            add(report("2125550103", "2026-11-08"))
        }.build(dayCount = 2)
        // asking about a reported number leaves it out of its own neighbors
        val stats = blocks.blockLookup("3155550101")!!
        assertEquals(2, stats.otherNumbers)
        assertEquals(3, stats.reports)
        assertEquals(2, stats.robocalls)
        // an unreported number in the block sees all three reported numbers
        assertEquals(3, blocks.blockLookup("3155559999")!!.otherNumbers)
    }

    @Test
    fun aBlockWithOnlyTheNumberItselfHasNoNeighbors() {
        assertNull(index.blockLookup("2125550103"))
        assertNull(index.blockLookup("999"))
        assertNull(index.blockLookup(null))
    }

    @Test
    fun unknownOrMissingNumbersHaveNoStats() {
        assertNull(index.lookup("9999999999"))
        assertNull(index.lookup(null))
    }
}
