package com.dustin.callguard

import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.data.VerificationStatus
import com.dustin.callguard.trends.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CsvExporterTest {
    private val zone = ZoneId.of("America/New_York")

    private fun millis(hour: Int) =
        ZonedDateTime.of(2026, 11, 10, hour, 5, 9, 0, zone).toInstant().toEpochMilli()

    @Test
    fun writesHeaderAndRowsOldestFirst() {
        val calls = listOf(
            ScreenedCall(1, "2125550103", "2125550103", millis(15), "SILENCED", VerificationStatus.FAILED),
            ScreenedCall(2, null, null, millis(9), "SILENCED"),
        )
        val lines = CsvExporter.build(calls, zone).trim().lines()
        assertEquals("date,time,number,area_code,place,state,outcome,caller_verification", lines[0])
        assertEquals("2026-11-10,09:05:09,hidden,,,,SILENCED,not_checked", lines[1])
        assertEquals("2026-11-10,15:05:09,2125550103,212,Manhattan,NY,SILENCED,failed", lines[2])
    }

    @Test
    fun splitsRegionIntoPlaceAndState() {
        assertEquals("Manhattan" to "NY", CsvExporter.splitRegion("Manhattan, NY"))
        assertEquals("Rhode Island" to "", CsvExporter.splitRegion("Rhode Island"))
        assertEquals("toll-free" to "", CsvExporter.splitRegion("toll-free"))
        assertEquals("north-central NY (Syracuse, Utica, Watertown)" to "",
            CsvExporter.splitRegion("north-central NY (Syracuse, Utica, Watertown)"))
    }

    @Test
    fun escapesCommasQuotesAndNewlines() {
        assertEquals("plain", CsvExporter.csvField("plain"))
        assertEquals("\"a,b\"", CsvExporter.csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.csvField("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvExporter.csvField("line1\nline2"))
    }
}
