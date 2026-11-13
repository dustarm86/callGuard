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
    fun unknownOrMissingNumbersHaveNoStats() {
        assertNull(index.lookup("9999999999"))
        assertNull(index.lookup(null))
    }
}
