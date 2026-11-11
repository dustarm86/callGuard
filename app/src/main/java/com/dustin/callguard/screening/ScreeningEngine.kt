package com.dustin.callguard.screening

import com.dustin.callguard.data.ScreenerSettings
import java.time.LocalDateTime

enum class ScreeningOutcome(
    val allowsRing: Boolean,
    val shouldLog: Boolean,
    val label: String,
) {
    ALLOWED_DISABLED(true, false, "screening off"),
    ALLOWED_OFF_HOURS(true, false, "outside work hours"),
    ALLOWED_CONTACT(true, false, "contact"),
    ALLOWED_ALLOWLIST(true, false, "allowlist"),
    ALLOWED_REPEAT(true, true, "rang: called again"),
    SILENCED_SPOOF_SUSPECTED(false, true, "silenced: failed caller verification"),
    SILENCED(false, true, "silenced: not a contact"),
}

data class ScreeningInput(
    val normalizedNumber: String?,
    val isContact: Boolean,
    val isAllowlisted: Boolean,
    val hasRecentSilencedCall: Boolean,
    val verificationFailed: Boolean,
    val settings: ScreenerSettings,
    val now: LocalDateTime,
)

/** pure decision logic, no android dependencies, so it is easy to unit test. */
object ScreeningEngine {
    fun decide(input: ScreeningInput): ScreeningOutcome {
        val settings = input.settings
        if (!settings.screeningEnabled) return ScreeningOutcome.ALLOWED_DISABLED
        if (settings.workHoursOnly && !settings.isWithinWorkHours(input.now)) {
            return ScreeningOutcome.ALLOWED_OFF_HOURS
        }
        if (settings.silenceFailedVerification && input.verificationFailed) {
            return ScreeningOutcome.SILENCED_SPOOF_SUSPECTED
        }
        if (input.isContact) return ScreeningOutcome.ALLOWED_CONTACT
        if (input.isAllowlisted) return ScreeningOutcome.ALLOWED_ALLOWLIST
        if (settings.repeatBypassEnabled && input.hasRecentSilencedCall) {
            return ScreeningOutcome.ALLOWED_REPEAT
        }
        return ScreeningOutcome.SILENCED
    }
}
