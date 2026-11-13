package com.dustin.callguard.data

import android.content.Context

/** the numbers the person has probed, newest first, so they can be probed again with one tap. */
object ProbeHistory {
    const val MAX = 15

    /** puts [number] at the front; a number already in the list moves up instead of appearing twice. */
    fun add(history: List<String>, number: String): List<String> =
        (listOf(number) + history.filter { it != number }).take(MAX)

    fun encode(history: List<String>): String = history.joinToString(",")

    /** reads what [encode] wrote, skipping anything that isn't all digits. */
    fun decode(text: String): List<String> =
        text.split(',').filter { it.isNotEmpty() && it.all(Char::isDigit) }.take(MAX)
}

/** keeps the probe history on the device. */
class ProbeHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("callguard_probe_history", Context.MODE_PRIVATE)

    fun load(): List<String> = ProbeHistory.decode(prefs.getString(KEY, "").orEmpty())

    fun save(history: List<String>) {
        prefs.edit().putString(KEY, ProbeHistory.encode(history)).apply()
    }

    private companion object {
        const val KEY = "numbers"
    }
}
