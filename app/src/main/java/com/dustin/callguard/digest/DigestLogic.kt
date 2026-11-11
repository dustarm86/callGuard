package com.dustin.callguard.digest

import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.screening.AreaCodes
import com.dustin.callguard.screening.ScreeningOutcome
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DigestKind { DAILY, WEEKLY }

data class DigestContent(
    val title: String,
    val text: String,
    val silencedCount: Int,
)

/** pure digest text builder, no android dependencies. */
object DigestBuilder {

    fun build(kind: DigestKind, calls: List<ScreenedCall>, zone: ZoneId): DigestContent {
        val windowLabel = if (kind == DigestKind.WEEKLY) "the last 7 days" else "the last 24 hours"
        val silenced = calls.filter { call ->
            runCatching { ScreeningOutcome.valueOf(call.outcome) }.getOrNull()?.allowsRing == false
        }
        val count = silenced.size

        if (count == 0) {
            return DigestContent(
                title = "callGuard: no calls silenced in $windowLabel",
                text = "all quiet.",
                silencedCount = 0,
            )
        }

        val parts = mutableListOf<String>()

        val topAreaCode = silenced
            .mapNotNull { AreaCodes.areaCodeOf(it.normalizedNumber) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
        if (topAreaCode != null) {
            parts += "top area code: ${topAreaCode.key} (${topAreaCode.value})"
        }

        if (count >= 3) {
            val busiestHour = silenced
                .map { ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.timestamp), zone).hour }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
            if (busiestHour != null) {
                val formatted = LocalTime.of(busiestHour.key, 0)
                    .format(DateTimeFormatter.ofPattern("h a", Locale.US))
                    .lowercase(Locale.US)
                parts += "busiest hour: $formatted"
            }
        }

        val repeatNumbers = silenced
            .mapNotNull { it.normalizedNumber }
            .groupingBy { it }
            .eachCount()
            .count { it.value >= 2 }
        if (repeatNumbers > 0) {
            parts += "$repeatNumbers number${if (repeatNumbers == 1) "" else "s"} called more than once"
        }

        val hidden = silenced.count { it.normalizedNumber == null }
        if (hidden > 0) parts += "$hidden hidden"

        return DigestContent(
            title = "callGuard: $count call${if (count == 1) "" else "s"} silenced in $windowLabel",
            text = parts.joinToString(" · "),
            silencedCount = count,
        )
    }
}

/** pure schedule math, so the next delivery time is easy to test. */
object DigestSchedule {

    /**
     * milliseconds from [now] until the next time the digest should fire.
     * [minutesOfDay] is the local time of day; [dayOfWeek] is null for a daily digest.
     */
    fun delayUntilNext(now: ZonedDateTime, minutesOfDay: Int, dayOfWeek: DayOfWeek? = null): Long {
        val hour = minutesOfDay / 60
        val minute = minutesOfDay % 60
        var date = now.toLocalDate()
        if (dayOfWeek != null) {
            date = date.plusDays(((dayOfWeek.value - date.dayOfWeek.value + 7) % 7).toLong())
        }
        var target = date.atTime(hour, minute).atZone(now.zone)
        if (!target.isAfter(now)) {
            target = if (dayOfWeek != null) {
                date.plusWeeks(1).atTime(hour, minute).atZone(now.zone)
            } else {
                date.plusDays(1).atTime(hour, minute).atZone(now.zone)
            }
        }
        return Duration.between(now, target).toMillis()
    }
}
