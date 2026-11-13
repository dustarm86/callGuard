package com.dustin.callguard.complaints

import android.content.Context
import java.io.File
import java.time.LocalDate

/** keeps one small tab-separated file per downloaded day in the app's private storage. */
class ComplaintStore(context: Context) {
    private val directory = File(context.filesDir, "complaints").apply { mkdirs() }

    fun hasDay(day: LocalDate): Boolean = File(directory, "$day.tsv").exists()

    fun saveDay(day: LocalDate, reports: List<ComplaintReport>) {
        val temporary = File(directory, "$day.tsv.tmp")
        temporary.writeText(ComplaintCsv.toTsv(reports))
        temporary.renameTo(File(directory, "$day.tsv"))
    }

    /** removes stored days older than [keepFrom]. */
    fun prune(keepFrom: LocalDate) {
        directory.listFiles()?.forEach { file ->
            val day = runCatching { LocalDate.parse(file.name.removeSuffix(".tsv")) }.getOrNull()
            if (day != null && day.isBefore(keepFrom)) file.delete()
        }
    }

    fun loadIndex(): ComplaintIndex {
        val builder = ComplaintIndex.Builder()
        val files = directory.listFiles { file -> file.name.endsWith(".tsv") }?.sortedBy { it.name }.orEmpty()
        for (file in files) {
            file.useLines { lines -> lines.forEach { line -> ComplaintCsv.fromTsvLine(line)?.let(builder::add) } }
        }
        return builder.build(files.size)
    }
}
