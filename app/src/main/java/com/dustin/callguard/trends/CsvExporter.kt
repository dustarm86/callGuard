package com.dustin.callguard.trends

import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.data.VerificationStatus
import com.dustin.callguard.screening.AreaCodes
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** builds a csv of the screened call log, oldest first. */
object CsvExporter {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun build(calls: List<ScreenedCall>, zone: ZoneId): String {
        val lines = mutableListOf("date,time,number,area_code,place,state,outcome,caller_verification")
        for (call in calls.sortedBy { it.timestamp }) {
            val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(call.timestamp), zone)
            val number = call.normalizedNumber
            val verification = when (call.verificationStatus) {
                VerificationStatus.PASSED -> "passed"
                VerificationStatus.FAILED -> "failed"
                else -> "not_checked"
            }
            val (place, state) = splitRegion(AreaCodes.lookup(number).orEmpty())
            lines += listOf(
                time.toLocalDate().toString(),
                time.format(timeFormat),
                number ?: "hidden",
                AreaCodes.areaCodeOf(number).orEmpty(),
                place,
                state,
                call.outcome,
                verification,
            ).joinToString(",") { csvField(it) }
        }
        return lines.joinToString("\n", postfix = "\n")
    }

    private val regionWithState = Regex("^(.*), ([A-Z]{2})$")

    /** "Manhattan, NY" becomes ("Manhattan", "NY"); anything else stays whole in the place column. */
    fun splitRegion(region: String): Pair<String, String> {
        val match = regionWithState.find(region) ?: return region to ""
        return match.groupValues[1] to match.groupValues[2]
    }

    /** quotes a field when it contains a comma, quote, or line break. */
    fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
