package com.dustin.callguard.screening

/** turns the numbers inside android's call details into readable words. pure, so it is unit tested. */
object CallDetailsText {
    fun direction(value: Int): String = when (value) {
        0 -> "incoming"
        1 -> "outgoing"
        -1 -> "unknown"
        else -> "other ($value)"
    }

    // values of TelecomManager.PRESENTATION_*
    fun presentation(value: Int): String = when (value) {
        1 -> "allowed (number shown)"
        2 -> "restricted (caller hid the number)"
        3 -> "unknown (network gave no number)"
        4 -> "payphone"
        else -> "other ($value)"
    }

    // values of Connection.VERIFICATION_STATUS_*
    fun verification(value: Int): String = when (value) {
        0 -> "not verified"
        1 -> "passed"
        2 -> "failed"
        else -> "other ($value)"
    }

    /** the raw bit mask in hex, plus names for the bits we know. */
    fun properties(mask: Int): String {
        val names = buildList {
            if (mask and 0x8 != 0) add("wifi calling")
            if (mask and 0x10 != 0) add("high definition audio")
            if (mask and 0x40 != 0) add("external call")
            if (mask and 0x100 != 0) add("self managed")
            if (mask and 0x400 != 0) add("rtt")
        }
        val hex = "0x" + mask.toString(16)
        return if (names.isEmpty()) hex else "$hex (${names.joinToString(", ")})"
    }
}
