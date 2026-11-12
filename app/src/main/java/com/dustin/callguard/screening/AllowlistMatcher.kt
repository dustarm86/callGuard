package com.dustin.callguard.screening

import com.dustin.callguard.data.AllowedNumber
import com.dustin.callguard.data.AllowedPrefix

/** pure allowlist check (exact numbers that haven't expired, plus number-block prefixes). */
object AllowlistMatcher {
    fun matches(
        normalizedNumber: String?,
        allowedNumbers: List<AllowedNumber>,
        prefixes: List<AllowedPrefix>,
        nowMillis: Long,
    ): Boolean {
        if (normalizedNumber == null) return false
        val exact = allowedNumbers.any { entry ->
            val expiresAt = entry.expiresAt
            entry.normalizedNumber == normalizedNumber && (expiresAt == null || expiresAt > nowMillis)
        }
        if (exact) return true
        return prefixes.any { normalizedNumber.startsWith(it.prefix) }
    }
}
