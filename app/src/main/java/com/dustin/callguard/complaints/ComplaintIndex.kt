package com.dustin.callguard.complaints

data class ComplaintStats(
    val count: Int,
    val robocalls: Int,
    // newest report day, yyyy-mm-dd
    val lastDay: String,
    // most common subjects with their counts
    val subjects: List<Pair<String, Int>>,
)

/** what the reports say about the other numbers in a number's area code and exchange (its first 6 digits). */
data class BlockComplaintStats(
    // distinct reported numbers in the block, not counting the one asked about
    val otherNumbers: Int,
    // reports of those other numbers
    val reports: Int,
    val robocalls: Int,
)

/** reports grouped by calling number, built from the downloaded daily files. */
class ComplaintIndex private constructor(
    private val byNumber: Map<String, Entry>,
    private val byBlock: Map<String, BlockEntry>,
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

    private class BlockEntry {
        val numbers = HashSet<String>()
        var reports = 0
        var robocalls = 0
    }

    /** spam campaigns tend to rotate through numbers in one block, so reports of neighbors say something. */
    fun blockLookup(number: String?): BlockComplaintStats? {
        if (number == null || number.length != 10) return null
        val block = byBlock[number.substring(0, 6)] ?: return null
        val own = byNumber[number]
        val others = block.numbers.size - if (own != null) 1 else 0
        if (others <= 0) return null
        return BlockComplaintStats(
            otherNumbers = others,
            reports = block.reports - (own?.count ?: 0),
            robocalls = block.robocalls - (own?.robocalls ?: 0),
        )
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
        private val blocks = HashMap<String, BlockEntry>()
        private var reports = 0
        private var newest: String? = null

        fun add(report: ComplaintReport) {
            val entry = entries.getOrPut(report.number) { Entry() }
            entry.count++
            if (report.robocall) entry.robocalls++
            if (report.day > entry.lastDay) entry.lastDay = report.day
            entry.subjects.merge(report.subject, 1, Int::plus)
            if (report.number.length == 10) {
                val block = blocks.getOrPut(report.number.substring(0, 6)) { BlockEntry() }
                block.numbers += report.number
                block.reports++
                if (report.robocall) block.robocalls++
            }
            reports++
            if (newest == null || report.day > newest!!) newest = report.day
        }

        /** [dayCount] is how many daily files were loaded. */
        fun build(dayCount: Int): ComplaintIndex = ComplaintIndex(entries, blocks, reports, dayCount, newest)
    }
}
