package com.dustin.callguard

import com.dustin.callguard.complaints.ComplaintFileName
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComplaintFileNameTest {
    @Test
    fun readsTheDayFromTheFtcFileName() {
        assertEquals(LocalDate.of(2026, 11, 12), ComplaintFileName.dayFrom("DNC_Complaint_Numbers_2026-11-12.csv"))
    }

    @Test
    fun readsTheDayFromABrowserRenamedCopy() {
        assertEquals(LocalDate.of(2026, 11, 9), ComplaintFileName.dayFrom("DNC_Complaint_Numbers_2026-11-09 (1).csv"))
    }

    @Test
    fun returnsNullWhenThereIsNoUsableDate() {
        assertNull(ComplaintFileName.dayFrom("report.csv"))
        assertNull(ComplaintFileName.dayFrom("x_2026-13-45.csv"))
        assertNull(ComplaintFileName.dayFrom(null))
    }
}
