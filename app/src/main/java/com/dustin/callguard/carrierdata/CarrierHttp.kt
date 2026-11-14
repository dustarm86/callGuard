package com.dustin.callguard.carrierdata

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** the small amount of http the carrier data downloads need. */
object CarrierHttp {
    private const val BROWSER_AGENT = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36"

    fun open(url: String, browserHeaders: Boolean, connectMs: Int = 15_000, readMs: Int = 60_000): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectMs
        connection.readTimeout = readMs
        if (browserHeaders) {
            connection.setRequestProperty("User-Agent", BROWSER_AGENT)
            connection.setRequestProperty("Accept", "application/zip,application/octet-stream,*/*")
            connection.setRequestProperty("Referer", "https://www.nationalnanpa.com/")
        }
        return connection
    }

    /**
     * runs [block] on the body of a successful download; any other answer is an IOException naming the http code.
     * [onBytes] hears how many bytes have arrived and the total size, when the server says it.
     */
    fun <T> download(
        url: String,
        browserHeaders: Boolean,
        onBytes: ((done: Long, total: Long?) -> Unit)? = null,
        block: (InputStream) -> T,
    ): T {
        val connection = open(url, browserHeaders)
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("http $code")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            onBytes?.invoke(0, total)
            val stream = if (onBytes == null) connection.inputStream else CountingInputStream(connection.inputStream, total, onBytes = onBytes)
            return stream.use(block)
        } finally {
            connection.disconnect()
        }
    }
}
