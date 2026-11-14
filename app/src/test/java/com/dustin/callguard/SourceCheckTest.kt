package com.dustin.callguard

import com.dustin.callguard.carrierdata.SourceCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCheckTest {
    @Test
    fun readsTheTotalSizeFromAContentRangeHeader() {
        assertEquals(12_345_678L, SourceCheck.totalFromContentRange("bytes 0-0/12345678"))
        assertNull(SourceCheck.totalFromContentRange("bytes 0-0/*"))
        assertNull(SourceCheck.totalFromContentRange(null))
    }

    @Test
    fun sizesReadInPlainUnits() {
        assertEquals("12.3 MB", SourceCheck.size(12_345_678))
        assertEquals("820 KB", SourceCheck.size(820_000))
        assertEquals("size unknown", SourceCheck.size(null))
    }

    @Test
    fun aGoodAnswerWorksAndABlockedOrMissingOneSaysWhy() {
        assertTrue(SourceCheck.judge("a", 206, 5_000_000, "application/zip", true).worked)
        assertTrue(SourceCheck.judge("a", 403, null, null, true).detail.contains("blocks"))
        assertTrue(SourceCheck.judge("a", 404, null, null, true).detail.contains("nothing is published"))
        val page = SourceCheck.judge("a", 200, null, "text/html", false)
        assertFalse(page.worked)
        assertTrue(page.detail.contains("web page"))
    }

    @Test
    fun theSummaryStartsWithHowManyWorked() {
        val worked = SourceCheck.Outcome("one", true, "works")
        val failed = SourceCheck.Outcome("two", false, "refused")
        val some = SourceCheck.summary(listOf(worked, failed))
        assertEquals("result: 1 of 2 sources can be reached from this phone", some.first())
        assertEquals(listOf("✓ one: works", "✗ two: refused"), some.drop(1))
        assertTrue(SourceCheck.summary(listOf(failed)).first().startsWith("result: none of the 1 sources"))
    }
}
