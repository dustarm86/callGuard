package com.dustin.callguard.screening

enum class CarrierKind(val label: String) {
    WIRELESS("wireless carrier"),
    INTERNET_PHONE("internet phone or wholesale carrier"),
    OTHER("landline or other carrier"),
}

/**
 * a rough guess from the company name only. the nanpa file has no line type, so this is a hint,
 * never a fact: wholesale carriers also serve ordinary businesses and wireless carriers also hold landlines.
 */
object CarrierKinds {
    private val internetPhoneWords = listOf(
        "BANDWIDTH", "ONVOY", "INTELIQUENT", "PEERLESS", "TWILIO", "SINCH", "TELNYX", "VONAGE", "VOIP",
        "IP ENABLED", "NEUTRAL TANDEM", "LEVEL 3", "NEXTIVA", "RINGCENTRAL", "COMMIO", "RINGSQUARED",
    )
    private val wirelessWords = listOf(
        "WIRELESS", "CELLCO", "CINGULAR", "T-MOBILE", "CELLULAR", "MOBILITY", "SPRINT SPECTRUM",
    )

    fun classify(company: String?): CarrierKind? {
        if (company.isNullOrBlank()) return null
        val name = company.uppercase()
        val tokens = name.split(Regex("[^A-Z0-9]+"))
        return when {
            internetPhoneWords.any { it in name } -> CarrierKind.INTERNET_PHONE
            wirelessWords.any { it in name } || "PCS" in tokens -> CarrierKind.WIRELESS
            else -> CarrierKind.OTHER
        }
    }
}
