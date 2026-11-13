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
        // what the first day that wasn't a 200 looked like, to explain a run that found nothing
        val firstMissing: String?,
    ) {
        fun summary(): String {
            val parts = mutableListOf("downloaded $downloaded new ${if (downloaded == 1) "day" else "days"} ($reports reports)")
            if (alreadyStored > 0) parts += "$alreadyStored already stored"
            if (notPublished > 0) parts += "$notPublished not published (weekends and holidays have no file)"
            if (failed > 0) parts += "$failed failed${firstError?.let { ": $it" }.orEmpty()}"
            if (downloaded == 0 && alreadyStored == 0 && failed == 0 && notPublished > 0) {
                parts += "no day had a file, which is unusual on weekdays, so the file address may have changed or ftc.gov may be refusing the app${firstMissing?.let { " (first answer: $it)" }.orEmpty()}"
            }
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
        var firstMissing: String? = null
        for (offset in 0 until RETENTION_DAYS) {
            val day = today.minusDays(offset.toLong())
            if (store.hasDay(day)) {
                alreadyStored++
                continue
            }
            try {
                val fetched = fetch(day)
                if (fetched == null) {
                    notPublished++
                    if (firstMissing == null) firstMissing = "http 404 for $BASE_URL$day.csv"
                } else {
                    val parsed = ComplaintCsv.parse(fetched.body)
                    if (parsed.isEmpty()) {
                        // a 200 that isn't a report file (a bot check or error page, say) is a failure, not a missing day
                        val start = fetched.body.trim().take(60).replace(Regex("\\s+"), " ")
                        throw IOException("got a page that isn't a report file (${fetched.contentType ?: "no type"}, ${fetched.body.length} bytes, starts \"$start\")")
                    }
                    store.saveDay(day, parsed)
                    downloaded++
                    reports += parsed.size
                }
            } catch (e: Exception) {
                failed++
                if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
            }
        }
        return Result(downloaded, alreadyStored, notPublished, failed, reports, firstError, firstMissing)
    }

    private class Fetched(val body: String, val contentType: String?)

    /** the file's text, or null when the server answered 404 for that day. */
    private fun fetch(day: LocalDate): Fetched? {
        val connection = URL("$BASE_URL$day.csv").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 40_000
            // ftc.gov answers 404 to unfamiliar clients, so ask the way a normal browser does
            connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            connection.setRequestProperty("Accept", "text/csv,text/plain,*/*;q=0.8")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    if (connection.contentLengthLong > MAX_BYTES) throw IOException("file too large")
                    Fetched(connection.inputStream.bufferedReader().use { it.readText() }, connection.contentType)
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
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        private const val BASE_URL = "https://www.ftc.gov/sites/default/files/DNC_Complaint_Numbers_"
    }
}
