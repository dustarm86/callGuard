package com.dustin.callguard

import com.dustin.callguard.data.NumberInfo
import com.dustin.callguard.data.NumberTag
import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.trends.ReportBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReportBuilderTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 11, 11, 18, 0, 0, 0, zone)

    private fun call(number: String?, hour: Int) = ScreenedCall(
        rawNumber = number,
        normalizedNumber = number,
        timestamp = ZonedDateTime.of(2026, 11, 10, hour, 0, 0, 0, zone).toInstant().toEpochMilli(),
        outcome = "SILENCED",
    )

    @Test
    fun listsNewestCallFirstWithReadableNumbers() {
        val html = ReportBuilder.build(listOf(call("3155550101", 9), call("2125550103", 15)), emptyMap(), zone, now)
        val newer = html.indexOf("(212) 555-0103")
        val older = html.indexOf("(315) 555-0101")
        assertTrue(newer in 0 until older)
        assertTrue(html.contains("silenced: not a contact"))
    }

    @Test
    fun showsHiddenNumbersAndTags() {
        val info = mapOf("3155550101" to NumberInfo("3155550101", "dentist office", NumberTag.KNOWN, 0L))
        val html = ReportBuilder.build(listOf(call(null, 9), call("3155550101", 10)), info, zone, now)
        assertTrue(html.contains("hidden number"))
        assertTrue(html.contains("known · dentist office"))
    }

    @Test
    fun escapesHtmlInNotes() {
        val info = mapOf("3155550101" to NumberInfo("3155550101", "<b>&\"x\"", "", 0L))
        val html = ReportBuilder.build(listOf(call("3155550101", 10)), info, zone, now)
        assertFalse(html.contains("<b>&"))
        assertTrue(html.contains("&lt;b&gt;&amp;&quot;x&quot;"))
    }
}
