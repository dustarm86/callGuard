package com.dustin.callguard.complaints

import com.dustin.callguard.screening.NumberNormalizer

/** one consumer report of an unwanted call, as published by the ftc. unverified. */
data class ComplaintReport(
    val number: String,
    // yyyy-mm-dd the report was created
    val day: String,
    val robocall: Boolean,
    val subject: String,
)

/** reads the ftc's daily "do not call reported calls" csv, and a compact tab-separated form for storage. */
object ComplaintCsv {
    private val dayPattern = Regex("^\\d{4}-\\d{2}-\\d{2}")

    /** parses a whole csv file. rows without a usable 10-digit number are skipped. */
    fun parse(text: String): List<ComplaintReport> {
        val lines = text.lineSequence().iterator()
        if (!lines.hasNext()) return emptyList()
        val header = splitFields(lines.next().trimStart('﻿')).map { it.trim() }
        val numberColumn = header.indexOf("Company_Phone_Number")
        val createdColumn = header.indexOf("Created_Date")
        val subjectColumn = header.indexOf("Subject")
        val robocallColumn = header.indexOf("Recorded_Message_Or_Robocall")
        if (numberColumn < 0 || createdColumn < 0) return emptyList()

        val reports = mutableListOf<ComplaintReport>()
        for (line in lines) {
            if (line.isBlank()) continue
            val fields = splitFields(line)
            val number = NumberNormalizer.normalize(fields.getOrNull(numberColumn))?.takeIf { it.length == 10 } ?: continue
            val day = fields.getOrNull(createdColumn)?.trim()?.let { dayPattern.find(it)?.value } ?: continue
            val subject = fields.getOrNull(subjectColumn)?.let(::cleanSubject).orEmpty().ifBlank { "unspecified" }
            val robocall = fields.getOrNull(robocallColumn)?.trim() == "Y"
            reports += ComplaintReport(number, day, robocall, subject)
        }
        return reports
    }

    /** splits one csv line, honoring double quotes and "" as an escaped quote. */
    fun splitFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields += current.toString()
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }

    private fun cleanSubject(raw: String): String = raw.replace(Regex("\\s+"), " ").trim()

    fun toTsv(reports: List<ComplaintReport>): String =
        reports.joinToString("\n", postfix = "\n") { report ->
            listOf(report.number, report.day, if (report.robocall) "Y" else "N", report.subject.replace('\t', ' '))
                .joinToString("\t")
        }

    fun fromTsvLine(line: String): ComplaintReport? {
        val parts = line.split('\t')
        if (parts.size < 4 || parts[0].length != 10) return null
        return ComplaintReport(parts[0], parts[1], parts[2] == "Y", parts[3])
    }
}
