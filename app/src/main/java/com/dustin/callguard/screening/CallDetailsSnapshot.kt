package com.dustin.callguard.screening

import android.os.Build
import android.telecom.Call

/**
 * records what android handed the screener for one call, as readable lines, so a call that behaves
 * strangely can be examined later. values that are missing on this android version are skipped.
 */
object CallDetailsSnapshot {
    fun describe(details: Call.Details): String {
        val lines = mutableListOf<String>()
        fun add(label: String, read: () -> String?) {
            val value = runCatching(read).getOrNull()
            if (value != null) lines += "$label: $value"
        }
        add("direction") { CallDetailsText.direction(details.callDirection) }
        add("number presentation") { CallDetailsText.presentation(details.handlePresentation) }
        add("caller name from carrier") { details.callerDisplayName?.takeIf { it.isNotBlank() } ?: "none" }
        add("caller name presentation") { CallDetailsText.presentation(details.callerDisplayNamePresentation) }
        add("verification") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                CallDetailsText.verification(details.callerNumberVerificationStatus)
            } else {
                null
            }
        }
        add("call properties") { CallDetailsText.properties(details.callProperties) }
        add("capabilities") { "0x" + details.callCapabilities.toString(16) }
        add("phone account") { details.accountHandle?.componentName?.shortClassName }
        add("extras") { details.extras?.keySet()?.sorted()?.joinToString(", ")?.ifEmpty { "none" } }
        add("intent extras") { details.intentExtras?.keySet()?.sorted()?.joinToString(", ")?.ifEmpty { "none" } }
        return lines.joinToString("\n")
    }
}
