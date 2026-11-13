package com.dustin.callguard.diagnostics

/** pure helpers for trimming big system dumps down to the lines that matter. */
object LogFilter {
    /** keeps lines containing any keyword (ignoring case), and only the newest [limit] of them. */
    fun keep(lines: List<String>, keywords: List<String>, limit: Int): List<String> =
        lines.filter { line -> keywords.any { line.contains(it, ignoreCase = true) } }.takeLast(limit)

    private val hardwareAddress = Regex("(?i)\\b([0-9a-f]{2}:){5}[0-9a-f]{2}\\b")

    /** hides bluetooth and other hardware addresses, which the telecom dump lists but nobody needs to share. */
    fun redactAddresses(text: String): String = hardwareAddress.replace(text, "xx:xx:xx:xx:xx:xx")

    /** names for UsageStatsManager standby bucket numbers. */
    fun standbyBucket(value: Int): String = when (value) {
        5 -> "exempted"
        10 -> "active"
        20 -> "working set"
        30 -> "frequent"
        40 -> "rare"
        45 -> "restricted"
        50 -> "never"
        else -> "other ($value)"
    }
}
