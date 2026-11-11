package com.dustin.callguard.screening

/**
 * us-centric number normalizer. everything is reduced to digits, a leading
 * us country code is dropped, and longer international numbers keep their
 * last ten digits. short codes (like 21391) are left alone.
 */
object NumberNormalizer {
    fun normalize(rawNumber: String?): String? {
        if (rawNumber.isNullOrBlank()) return null
        val digits = rawNumber.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return when {
            digits.length == 11 && digits.startsWith("1") -> digits.substring(1)
            digits.length > 10 -> digits.takeLast(10)
            else -> digits
        }
    }

    fun format(normalizedNumber: String): String =
        if (normalizedNumber.length == 10) {
            "(${normalizedNumber.substring(0, 3)}) ${normalizedNumber.substring(3, 6)}-${normalizedNumber.substring(6)}"
        } else {
            normalizedNumber
        }
}
