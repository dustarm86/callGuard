package com.dustin.callguard.screening

/**
 * allowlist prefixes cover a block of numbers, like (315) 555-01xx. they need at least 8 digits
 * so one entry can't let through a whole exchange, and a full 10 digits belongs in the number list.
 */
object PrefixNormalizer {
    const val MIN_DIGITS = 8
    const val MAX_DIGITS = 9

    /** digits only, with a leading country code 1 dropped; null when the result isn't 8 or 9 digits. */
    fun normalize(rawPrefix: String?): String? {
        if (rawPrefix.isNullOrBlank()) return null
        var digits = rawPrefix.filter { it.isDigit() }
        // north american area codes never start with 1, so a leading 1 is the country code
        if (digits.startsWith("1")) digits = digits.substring(1)
        return digits.takeIf { it.length in MIN_DIGITS..MAX_DIGITS }
    }

    /** "31555501" becomes "(315) 555-01xx". */
    fun format(prefix: String): String {
        val padded = prefix.padEnd(10, 'x')
        return "(${padded.substring(0, 3)}) ${padded.substring(3, 6)}-${padded.substring(6)}"
    }
}
