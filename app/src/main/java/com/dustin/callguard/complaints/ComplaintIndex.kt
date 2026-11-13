package com.dustin.callguard.complaints

data class ComplaintStats(
    val count: Int,
    val robocalls: Int,
    // newest report day, yyyy-mm-dd
    val lastDay: String,
    // most common subjects with their counts
    val subjects: List<Pair<String, Int>>,
)

/** reports grouped by calling number, built from the downloaded daily files. */
class ComplaintIndex private constructor(
    private val byNumber: Map<String, Entry>,
    val reportCount: Int,
    val dayCount: Int,
    val newestDay: String?,
) {
    private class Entry {
        var count = 0
        var robocalls = 0
        var lastDay = ""
        val subjects = HashMap<String, Int>(2)
    }

    fun lookup(number: String?): ComplaintStats? {
        val entry = byNumber[number ?: return null] ?: return null
        return ComplaintStats(
            count = entry.count,
            robocalls = entry.robocalls,
            lastDay = entry.lastDay,
            subjects = entry.subjects.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value },
        )
    }

    class Builder {
        private val entries = HashMap<String, Entry>()
        private var reports = 0
        private var newest: String? = null

        fun add(report: ComplaintReport) {
            val entry = entries.getOrPut(report.number) { Entry() }
            entry.count++
            if (report.robocall) entry.robocalls++
            if (report.day > entry.lastDay) entry.lastDay = report.day
            entry.subjects.merge(report.subject, 1, Int::plus)
            reports++
            if (newest == null || report.day > newest!!) newest = report.day
        }

        /** [dayCount] is how many daily files were loaded. */
        fun build(dayCount: Int): ComplaintIndex = ComplaintIndex(entries, reports, dayCount, newest)
    }
}
