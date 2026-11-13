package com.dustin.callguard

import com.dustin.callguard.data.ScreenerSettings
import com.dustin.callguard.trends.Diagnostics
import com.dustin.callguard.trends.ReportBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DiagnosticsTest {
    @Test
    fun describesSetupInPlainLines() {
        val lines = Diagnostics.lines(
            androidRelease = "16", sdkInt = 36, device = "motorola razr fold", appVersion = "1.0",
            roleHeld = true, contactsGranted = false,
            settings = ScreenerSettings(rejectInsteadOfSilence = true, screeningEnabled = false),
        )
        assertEquals("android 16 (api 36)", lines[0])
        assertTrue("default call screener role: held" in lines)
        assertTrue("contacts permission: missing" in lines)
        assertTrue("screening: paused" in lines)
        assertTrue("unknown callers are: rejected" in lines)
        assertTrue("own number set: no" in lines)
    }

    @Test
    fun reportShowsDiagnosticsOnlyWhenGiven() {
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 11, 12, 20, 0, 0, 0, zone)
        val without = ReportBuilder.build(emptyList(), emptyMap(), zone, now)
        val with = ReportBuilder.build(emptyList(), emptyMap(), zone, now, listOf("android 16 (api 36)"))
        assertTrue("diagnostics" !in without)
        assertTrue("<li>android 16 (api 36)</li>" in with)
    }
}
