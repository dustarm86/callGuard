package com.dustin.callguard

import com.dustin.callguard.data.ScreenerSettings
import com.dustin.callguard.screening.ScreeningEngine
import com.dustin.callguard.screening.ScreeningInput
import com.dustin.callguard.screening.ScreeningOutcome
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ScreeningEngineTest {

    // 2026-11-11 is a wednesday
    private val wednesdayMorning = LocalDateTime.of(2026, 11, 11, 10, 0)
    private val wednesdayEvening = LocalDateTime.of(2026, 11, 11, 20, 0)
    private val saturdayMorning = LocalDateTime.of(2026, 11, 14, 10, 0)

    private fun input(
        normalizedNumber: String? = "2165551234",
        isContact: Boolean = false,
        isAllowlisted: Boolean = false,
        hasRecentSilencedCall: Boolean = false,
        verificationFailed: Boolean = false,
        settings: ScreenerSettings = ScreenerSettings(),
        now: LocalDateTime = wednesdayMorning,
    ) = ScreeningInput(
        normalizedNumber = normalizedNumber,
        isContact = isContact,
        isAllowlisted = isAllowlisted,
        hasRecentSilencedCall = hasRecentSilencedCall,
        verificationFailed = verificationFailed,
        settings = settings,
        now = now,
    )

    @Test
    fun unknownNumberIsSilenced() {
        assertEquals(ScreeningOutcome.SILENCED, ScreeningEngine.decide(input()))
    }

    @Test
    fun hiddenNumberIsSilenced() {
        assertEquals(ScreeningOutcome.SILENCED, ScreeningEngine.decide(input(normalizedNumber = null)))
    }

    @Test
    fun contactRings() {
        assertEquals(ScreeningOutcome.ALLOWED_CONTACT, ScreeningEngine.decide(input(isContact = true)))
    }

    @Test
    fun allowlistedNumberRings() {
        assertEquals(ScreeningOutcome.ALLOWED_ALLOWLIST, ScreeningEngine.decide(input(isAllowlisted = true)))
    }

    @Test
    fun masterSwitchOffRingsEverything() {
        val settings = ScreenerSettings(screeningEnabled = false)
        assertEquals(ScreeningOutcome.ALLOWED_DISABLED, ScreeningEngine.decide(input(settings = settings)))
    }

    @Test
    fun workHoursOnlySilencesDuringWorkHours() {
        val settings = ScreenerSettings(workHoursOnly = true)
        assertEquals(ScreeningOutcome.SILENCED, ScreeningEngine.decide(input(settings = settings)))
    }

    @Test
    fun workHoursOnlyRingsAfterHours() {
        val settings = ScreenerSettings(workHoursOnly = true)
        assertEquals(
            ScreeningOutcome.ALLOWED_OFF_HOURS,
            ScreeningEngine.decide(input(settings = settings, now = wednesdayEvening)),
        )
    }

    @Test
    fun workHoursOnlyRingsOnWeekends() {
        val settings = ScreenerSettings(workHoursOnly = true)
        assertEquals(
            ScreeningOutcome.ALLOWED_OFF_HOURS,
            ScreeningEngine.decide(input(settings = settings, now = saturdayMorning)),
        )
    }

    @Test
    fun repeatCallerRingsOnlyWhenBypassIsEnabled() {
        assertEquals(
            ScreeningOutcome.SILENCED,
            ScreeningEngine.decide(input(hasRecentSilencedCall = true)),
        )
        val settings = ScreenerSettings(repeatBypassEnabled = true)
        assertEquals(
            ScreeningOutcome.ALLOWED_REPEAT,
            ScreeningEngine.decide(input(hasRecentSilencedCall = true, settings = settings)),
        )
    }

    @Test
    fun failedVerificationBeatsContactMatch() {
        assertEquals(
            ScreeningOutcome.SILENCED_SPOOF_SUSPECTED,
            ScreeningEngine.decide(input(isContact = true, verificationFailed = true)),
        )
    }

    @Test
    fun failedVerificationIgnoredWhenSettingIsOff() {
        val settings = ScreenerSettings(silenceFailedVerification = false)
        assertEquals(
            ScreeningOutcome.ALLOWED_CONTACT,
            ScreeningEngine.decide(input(isContact = true, verificationFailed = true, settings = settings)),
        )
    }
}
