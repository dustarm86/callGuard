package com.dustin.callguard.carrierdata

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * finds out which places the phone itself can download carrier data from, before any of it is built into the app.
 * each check asks for just the first byte of the file, so it costs almost no data.
 */
object SourceCheck {
    data class Source(val label: String, val url: String, val browserHeaders: Boolean)

    data class Outcome(val label: String, val worked: Boolean, val detail: String)

    const val NANPA = "https://reports.nanpa.com/public"
    const val RELEASE = "https://github.com/dustarm86/callGuard/releases/latest/download"

    val sources = listOf(
        Source("nanpa exchanges, plain request", "$NANPA/CoCodeAssignment_Utilized_AllStates_Public.zip", false),
        Source("nanpa exchanges, browser-style request", "$NANPA/CoCodeAssignment_Utilized_AllStates_Public.zip", true),
        Source("nanpa 1,000-blocks, browser-style request", "$NANPA/ThousandsBlockAssignment_All.zip", true),
        Source("callGuard github release, exchanges", "$RELEASE/nanpa.bin", false),
        Source("callGuard github release, blocks", "$RELEASE/blocks.bin", false),
    )

    /** the total size from a "content-range: bytes 0-0/12345" header, or null when it isn't there. */
    fun totalFromContentRange(header: String?): Long? =
        header?.substringAfterLast('/', "")?.trim()?.toLongOrNull()

    fun size(bytes: Long?): String = when {
        bytes == null -> "size unknown"
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        else -> "${bytes / 1_000} KB"
    }

    /** turns one http answer into a plain-language outcome. [looksLikeFile] is false when the body was a web page. */
    fun judge(label: String, code: Int, total: Long?, type: String?, looksLikeFile: Boolean): Outcome = when {
        code in 200..299 && looksLikeFile -> Outcome(label, true, "works: http $code, ${size(total)}")
        code in 200..299 -> Outcome(label, false, "answered http $code but with a web page (${type ?: "unknown type"}), not the file")
        code == 403 -> Outcome(label, false, "refused: http 403, the site blocks this kind of request")
        code == 404 -> Outcome(label, false, "not found: http 404, nothing is published there")
        else -> Outcome(label, false, "failed: http $code")
    }

    /** the first line of the screen: how many sources worked, and which ones. */
    fun summary(outcomes: List<Outcome>): List<String> {
        val worked = outcomes.filter { it.worked }
        val head = when {
            worked.isEmpty() -> "result: none of the ${outcomes.size} sources can be reached from this phone right now"
            else -> "result: ${worked.size} of ${outcomes.size} sources can be reached from this phone"
        }
        return listOf(head) + outcomes.map { (if (it.worked) "✓ " else "✗ ") + it.label + ": " + it.detail }
    }

    suspend fun run(): List<String> = withContext(Dispatchers.IO) { summary(sources.map(::check)) }

    private fun check(source: Source): Outcome {
        val connection = URL(source.url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Range", "bytes=0-0")
            if (source.browserHeaders) {
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36")
                connection.setRequestProperty("Accept", "application/zip,application/octet-stream,*/*")
                connection.setRequestProperty("Referer", "https://www.nationalnanpa.com/")
            }
            val code = connection.responseCode
            val type = connection.contentType
            val total = totalFromContentRange(connection.getHeaderField("Content-Range")) ?: connection.contentLengthLong.takeIf { it > 1 }
            // a web page in place of the file means the site served a block or sign-in page
            val looksLikeFile = type?.contains("html", ignoreCase = true) != true
            judge(source.label, code, total, type, looksLikeFile)
        } catch (e: Exception) {
            Outcome(source.label, false, "couldn't connect: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }
}
