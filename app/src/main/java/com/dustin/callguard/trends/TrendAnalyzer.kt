package com.dustin.callguard.trends

import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.data.VerificationStatus
import com.dustin.callguard.screening.AreaCodes
import com.dustin.callguard.screening.NumberNormalizer
import com.dustin.callguard.screening.ScreeningOutcome
import com.dustin.callguard.screening.SuspicionScorer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

data class AreaCodeCount(val areaCode: String, val region: String?, val count: Int)

data class NumberCount(val number: String, val count: Int)

data class Trends(
    val silencedCount: Int,
    val distinctNumbers: Int,
    val hiddenCount: Int,
    val failedVerificationCount: Int,
    // silenced calls whose first 6 digits match the user's own number
    val neighborSpoofCount: Int,
    val topAreaCodes: List<AreaCodeCount>,
    val topRepeatNumbers: List<NumberCount>,
    // 24 entries, midnight first
    val callsByHour: List<Int>,
    // 7 entries, monday first
    val callsByWeekday: List<Int>,
)

/** pure trend math over the logged calls, no android dependencies. */
object TrendAnalyzer {

    fun analyze(calls: List<ScreenedCall>, zone: ZoneId, ownNumber: String?): Trends {
        val silenced = calls.filter { call ->
            runCatching { ScreeningOutcome.valueOf(call.outcome) }.getOrNull()?.allowsRing == false
        }
        val own = NumberNormalizer.normalize(ownNumber)

        val numberCounts = silenced
            .mapNotNull { it.normalizedNumber }
            .groupingBy { it }
            .eachCount()

        val topAreaCodes = silenced
            .mapNotNull { AreaCodes.areaCodeOf(it.normalizedNumber) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(TOP_LIMIT)
            .map { AreaCodeCount(it.key, areaRegion(it.key), it.value) }

        val topRepeats = numberCounts.entries
            .filter { it.value >= 2 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(TOP_LIMIT)
            .map { NumberCount(it.key, it.value) }

        val byHour = IntArray(24)
        val byWeekday = IntArray(7)
        for (call in silenced) {
            val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(call.timestamp), zone)
            byHour[time.hour]++
            byWeekday[time.dayOfWeek.value - 1]++
        }

        return Trends(
            silencedCount = silenced.size,
            distinctNumbers = numberCounts.size,
            hiddenCount = silenced.count { it.normalizedNumber == null },
            failedVerificationCount = silenced.count { it.verificationStatus == VerificationStatus.FAILED },
            neighborSpoofCount = if (own == null) 0 else silenced.count {
                SuspicionScorer.sharesPrefix(it.normalizedNumber, own)
            },
            topAreaCodes = topAreaCodes,
            topRepeatNumbers = topRepeats,
            callsByHour = byHour.toList(),
            callsByWeekday = byWeekday.toList(),
        )
    }

    // area code lookup works on a full number, so pad the code to ten digits
    private fun areaRegion(areaCode: String): String? = AreaCodes.lookup(areaCode + "5550100")

    private const val TOP_LIMIT = 5
}
