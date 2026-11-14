package com.dustin.callguard

import com.dustin.callguard.carrierdata.CarrierDataInfo
import com.dustin.callguard.carrierdata.CarrierSources
import com.dustin.callguard.carrierdata.SourceKind
import com.dustin.callguard.screening.NxxDirectory
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierSourcesTest {
    @Test
    fun nanpaComesFirstThenThePrebuiltCopies() {
        val kinds = CarrierSources.all.map { it.kind }
        assertEquals(listOf(SourceKind.ZIP_REPORT, SourceKind.ZIP_REPORT, SourceKind.PREBUILT, SourceKind.PREBUILT, SourceKind.PREBUILT), kinds)
        assertEquals(CarrierSources.all.size, CarrierSources.all.map { it.label }.toSet().size)
        assertEquals(listOf(false, true, false, false, false), CarrierSources.all.map { it.browserHeaders })
    }

    @Test
    fun everySourceHasBothFilesOverHttps() {
        CarrierSources.all.forEach {
            assertTrue(it.exchangesUrl.startsWith("https://"))
            assertTrue(it.blocksUrl.startsWith("https://"))
            assertTrue(it.exchangesUrl != it.blocksUrl)
        }
    }

    @Test
    fun datesReadInPlainForm() {
        assertEquals("2026-11-14", CarrierDataInfo.date(20261114))
        assertEquals("unknown", CarrierDataInfo.date(0))
    }

    @Test
    fun theDataSummaryHandlesMissingAndLoadedData() {
        assertEquals("not loaded", CarrierDataInfo.summary(null))
        assertEquals(listOf("carrier data isn't loaded"), CarrierDataInfo.lines(null, null))
        val directory = NxxDirectory.parse(ByteArrayInputStream(NxxTestData.sample))
        assertTrue(CarrierDataInfo.summary(directory).startsWith("exchanges as of 2026-"))
        val lines = CarrierDataInfo.lines(directory, null)
        assertEquals("1,000-blocks: not loaded", lines[1])
        assertEquals("built into the app, never updated from the internet", lines[2])
        assertEquals("last updated from nanpa reports on 2026-11-14", CarrierDataInfo.lines(directory, "nanpa reports on 2026-11-14")[2])
        assertNull(NxxDirectory.parse(ByteArrayInputStream(NxxTestData.sample)).blocks)
    }
}
