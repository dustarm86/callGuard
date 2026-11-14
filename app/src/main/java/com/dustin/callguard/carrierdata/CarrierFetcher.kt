package com.dustin.callguard.carrierdata

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/** gets both data files from one source, building them from the nanpa reports when the source is the reports themselves. */
object CarrierFetcher {
    fun fetch(source: CarrierSource, today: Int): Built = when (source.kind) {
        SourceKind.ZIP_REPORT -> Built(
            nanpa = CarrierHttp.download(source.exchangesUrl, source.browserHeaders) { stream ->
                NanpaBuilder.build(textInZip(stream, Charsets.UTF_8))
            },
            blocks = CarrierHttp.download(source.blocksUrl, source.browserHeaders) { stream ->
                BlockBuilder.build(textInZip(stream, Charsets.ISO_8859_1), today)
            },
        )
        SourceKind.PREBUILT -> Built(
            nanpa = CarrierHttp.download(source.exchangesUrl, source.browserHeaders) { it.readBytes() },
            blocks = CarrierHttp.download(source.blocksUrl, source.browserHeaders) { it.readBytes() },
        )
    }

    private fun textInZip(stream: InputStream, charset: Charset): BufferedReader {
        val zip = ZipInputStream(stream)
        while (true) {
            val entry = zip.nextEntry ?: throw IOException("no .txt report inside the zip")
            if (entry.name.endsWith(".txt", ignoreCase = true)) return BufferedReader(InputStreamReader(zip, charset), 64 * 1024)
        }
    }
}
