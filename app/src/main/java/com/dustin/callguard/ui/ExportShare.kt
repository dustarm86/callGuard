package com.dustin.callguard.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.trends.CsvExporter
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** writes exports to the app cache and opens android's share sheet for them. */
object ExportShare {
    fun shareCsv(context: Context, calls: List<ScreenedCall>) {
        share(context, "callguard-log-${LocalDate.now()}.csv", "text/csv", CsvExporter.build(calls, ZoneId.systemDefault()))
    }

    fun shareReport(context: Context, html: String) {
        share(context, "callguard-report-${LocalDate.now()}.html", "text/html", html)
    }

    private fun share(context: Context, fileName: String, mimeType: String, content: String) {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, fileName)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "export call log"))
    }
}
