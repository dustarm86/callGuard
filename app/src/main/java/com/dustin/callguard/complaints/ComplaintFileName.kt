package com.dustin.callguard.complaints

import java.time.LocalDate

/** works out which day an imported ftc file is for, from its name (DNC_Complaint_Numbers_2026-11-12.csv). */
object ComplaintFileName {
    private val dayPattern = Regex("(\\d{4}-\\d{2}-\\d{2})")

    fun dayFrom(fileName: String?): LocalDate? {
        val text = dayPattern.find(fileName.orEmpty())?.value ?: return null
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }
}
