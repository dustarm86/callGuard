package com.dustin.callguard.complaints

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * downloads the ftc's public daily files of reported unwanted-call numbers. it only requests a file
 * named by date; no phone number or personal data is ever sent.
 */
class ComplaintDownloader(private val store: ComplaintStore) {

    data class Result(
        val downloaded: Int,
        val alreadyStored: Int,
        val notPublished: Int,
        val failed: Int,
        val reports: Int,
        val firstError: String?,
    ) {
        fun summary(): String {
            val parts = mutableListOf("downloaded $downloaded new ${if (downloaded == 1) "day" else "days"} ($reports reports)")
            if (alreadyStored > 0) parts += "$alreadyStored already stored"
            if (notPublished > 0) parts += "$notPublished not published (weekends and holidays have no file)"
            if (failed > 0) parts += "$failed failed${firstError?.let { ": $it" }.orEmpty()}"
            return parts.joinToString(", ")
        }
    }

    fun refresh(today: LocalDate): Result {
        store.prune(today.minusDays(RETENTION_DAYS.toLong()))
        var downloaded = 0
        var alreadyStored = 0
        var notPublished = 0
        var failed = 0
        var reports = 0
        var firstError: String? = null
        for (offset in 0 until RETENTION_DAYS) {
            val day = today.minusDays(offset.toLong())
            if (store.hasDay(day)) {
                alreadyStored++
                continue
            }
            try {
                val body = fetch(day)
                // a missing day can also come back as a normal page that isn't a report file, so an unreadable body counts as not published
                val parsed = body?.let { ComplaintCsv.parse(it) }.orEmpty()
                if (parsed.isEmpty()) {
                    notPublished++
                } else {
                    store.saveDay(day, parsed)
                    downloaded++
                    reports += parsed.size
                }
            } catch (e: Exception) {
                failed++
                if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
            }
        }
        return Result(downloaded, alreadyStored, notPublished, failed, reports, firstError)
    }

    /** the file's text, or null when no file was published for that day. */
    private fun fetch(day: LocalDate): String? {
        val connection = URL("$BASE_URL$day.csv").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 40_000
            connection.setRequestProperty("User-Agent", "callGuard personal call screener")
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    if (connection.contentLengthLong > MAX_BYTES) throw IOException("file too large")
                    connection.inputStream.bufferedReader().use { it.readText() }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("http $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val RETENTION_DAYS = 14
        private const val MAX_BYTES = 25L * 1024 * 1024
        private const val BASE_URL = "https://www.ftc.gov/sites/default/files/DNC_Complaint_Numbers_"
    }
}
