package com.dustin.callguard.screening

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/** a second opinion from google's phone number library, which works fully offline. */
object PhoneLibCheck {
    fun lines(normalizedNumber: String): List<String> {
        val util = PhoneNumberUtil.getInstance()
        val number = try {
            util.parse("+1$normalizedNumber", "US")
        } catch (e: NumberParseException) {
            return listOf("the library can't read this as a north american number")
        }
        val lines = mutableListOf<String>()
        lines += if (util.isValidNumber(number)) {
            "valid north american number according to the library"
        } else {
            "the library says this is not a valid north american number"
        }
        util.getRegionCodeForNumber(number)?.let { region ->
            val country = Locale("", region).getDisplayCountry(Locale.US)
            if (country.isNotBlank()) lines += "country: $country"
        }
        lines += when (util.getNumberType(number)) {
            PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE -> "type: fixed line or mobile (the library can't tell them apart for these numbers)"
            PhoneNumberUtil.PhoneNumberType.FIXED_LINE -> "type: fixed line"
            PhoneNumberUtil.PhoneNumberType.MOBILE -> "type: mobile"
            PhoneNumberUtil.PhoneNumberType.TOLL_FREE -> "type: toll-free"
            PhoneNumberUtil.PhoneNumberType.PREMIUM_RATE -> "type: premium rate (calling back can cost money)"
            PhoneNumberUtil.PhoneNumberType.SHARED_COST -> "type: shared cost"
            PhoneNumberUtil.PhoneNumberType.VOIP -> "type: internet phone"
            PhoneNumberUtil.PhoneNumberType.PERSONAL_NUMBER -> "type: personal number"
            else -> "type: unknown"
        }
        return lines
    }
}
