package com.dustin.callguard.screening

/**
 * checks a number against the north american numbering plan (us, canada and friends).
 * a number that breaks these rules can't be a real line, so it is a strong spoof signal.
 * international numbers (a plus sign that isn't +1, or too many digits) are skipped, since
 * the normalizer keeps only their last ten digits and those would be checked wrongly.
 */
object NanpValidator {

    /** reasons this can't be a real north american number; empty when it looks valid or can't be checked. */
    fun problems(rawNumber: String?, normalizedNumber: String?): List<String> {
        val digits = rawNumber?.filter { it.isDigit() }.orEmpty()
        val startsWithPlus = rawNumber?.trim()?.startsWith("+") == true
        val isInternational = (startsWithPlus && !digits.startsWith("1")) ||
            digits.length > 11 ||
            (digits.length == 11 && !digits.startsWith("1"))
        if (isInternational) return emptyList()

        val number = normalizedNumber?.takeIf { it.length == 10 } ?: return emptyList()
        val found = mutableListOf<String>()

        if (number[0] == '0' || number[0] == '1') {
            found += "area code starts with ${number[0]}, which no real area code does"
        }
        if (number[1] == '1' && number[2] == '1') {
            found += "${number.substring(0, 3)} is a service code, not an area code"
        }
        if (number[3] == '0' || number[3] == '1') {
            found += "exchange (digits 4-6) starts with ${number[3]}, which isn't assigned"
        } else if (number[4] == '1' && number[5] == '1') {
            found += "exchange ${number.substring(3, 6)} is a service code"
        }
        if (number.substring(3, 6) == "555" && number.substring(6, 8) == "01") {
            found += "555-01xx numbers are reserved for fiction"
        }
        return found
    }
}
