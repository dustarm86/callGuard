package com.dustin.callguard.trends

import com.dustin.callguard.data.ScreenerSettings

/** the context lines at the top of an exported report, so a shared report explains its own setup. */
object Diagnostics {
    fun lines(
        androidRelease: String,
        sdkInt: Int,
        device: String,
        appVersion: String,
        roleHeld: Boolean,
        contactsGranted: Boolean,
        settings: ScreenerSettings,
    ): List<String> = listOf(
        "android $androidRelease (api $sdkInt)",
        "device: $device",
        "callGuard $appVersion",
        "default call screener role: ${if (roleHeld) "held" else "not held"}",
        "contacts permission: ${if (contactsGranted) "granted" else "missing"}",
        "screening: ${if (settings.screeningEnabled) "on" else "paused"}",
        "unknown callers are: ${if (settings.rejectInsteadOfSilence) "rejected" else "silenced"}",
        "own number set: ${if (settings.ownNumber.isNotBlank()) "yes" else "no"}",
    )
}
