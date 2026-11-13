package com.dustin.callguard.lookup

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * asks veriphone about one number. this sends the number and the person's own api key to
 * veriphone.io, so it only runs when the person taps the button. it never contacts the number.
 */
object LineLookupClient {
    suspend fun query(number: String, key: String): List<String> = withContext(Dispatchers.IO) {
        try {
            LineLookup.describe(LineLookup.parse(fetch(number, key)))
        } catch (e: Exception) {
            // the key is in the address, so make sure no message can carry it onto the screen
            val reason = (e.message ?: e.javaClass.simpleName).replace(key.trim(), "***")
            listOf("lookup failed: $reason")
        }
    }

    private fun fetch(number: String, key: String): String {
        val connection = URL(LineLookup.url(number, key)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            // error answers still carry a json message worth showing, so read either stream
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (body.isBlank()) throw IOException("http $code with no answer")
            return body
        } finally {
            connection.disconnect()
        }
    }
}
