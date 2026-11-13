package com.dustin.callguard

import com.dustin.callguard.complaints.ComplaintCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplaintCsvTest {
    private val header = "Company_Phone_Number,Created_Date,Violation_Date,Consumer_City,Consumer_State,Consumer_Area_Code,Subject,Recorded_Message_Or_Robocall"

    private val csv = header + "\n" +
        "8338083984,2026-11-11 00:05:34,2026-11-03 15:07:00,BELLAIRE,Texas,713,\"Reducing your debt (credit cards, mortgage, student loans)\",Y\n" +
        "8335179626,2026-11-11 00:05:48,2026-11-10 17:34:00,Lakewood,Colorado,720,Other,Y\n" +
        ",2026-11-11 00:08:05,2026-11-10 13:19:00,Schaumburg,Illinois,847,Other,Y\n" +
        "7753910435,2026-11-11 00:08:43,2026-09-24 13:08:00,,Nevada,775,Warranties  & protection plans,N\n"

    @Test
    fun parsesRowsAndSkipsMissingNumbers() {
        val reports = ComplaintCsv.parse(csv)
        assertEquals(3, reports.size)
        assertEquals("8338083984", reports[0].number)
        assertEquals("2026-11-11", reports[0].day)
        assertEquals("Reducing your debt (credit cards, mortgage, student loans)", reports[0].subject)
        assertEquals(true, reports[0].robocall)
        assertEquals("Warranties & protection plans", reports[2].subject)
        assertEquals(false, reports[2].robocall)
    }

    @Test
    fun splitsQuotedFieldsWithCommasAndEscapedQuotes() {
        assertEquals(listOf("a", "b \"c\" d", "e,f"), ComplaintCsv.splitFields("a,\"b \"\"c\"\" d\",\"e,f\""))
        assertEquals(listOf("", "x", ""), ComplaintCsv.splitFields(",x,"))
    }

    @Test
    fun filesWithoutTheExpectedHeaderGiveNothing() {
        assertTrue(ComplaintCsv.parse("a,b,c\n1,2,3\n").isEmpty())
        assertTrue(ComplaintCsv.parse("").isEmpty())
    }

    @Test
    fun tsvRoundTripKeepsEveryField() {
        val reports = ComplaintCsv.parse(csv)
        val lines = ComplaintCsv.toTsv(reports).trim().lines()
        assertEquals(reports, lines.map { ComplaintCsv.fromTsvLine(it) })
    }

    @Test
    fun badTsvLinesAreIgnored() {
        assertEquals(null, ComplaintCsv.fromTsvLine("garbage"))
        assertEquals(null, ComplaintCsv.fromTsvLine("123\t2026-11-11\tY\tOther"))
    }
}
