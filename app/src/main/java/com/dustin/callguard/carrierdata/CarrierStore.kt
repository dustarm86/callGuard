package com.dustin.callguard.carrierdata

import android.content.Context
import java.io.File

/** keeps downloaded carrier data on the phone, next to (not instead of) the copy built into the app. */
class CarrierStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "carrierdata")

    fun nanpaFile(): File? = File(directory, "nanpa.bin").takeIf { it.isFile }

    fun blocksFile(): File? = File(directory, "blocks.bin").takeIf { it.isFile }

    /** writes both files under temporary names first, so a crash or full disk can't leave a half-written file behind. */
    fun install(built: Built, sourceLabel: String, today: Int) {
        directory.mkdirs()
        val nanpaTemp = File(directory, "nanpa.bin.tmp").apply { writeBytes(built.nanpa) }
        val blocksTemp = File(directory, "blocks.bin.tmp").apply { writeBytes(built.blocks) }
        check(blocksTemp.renameTo(File(directory, "blocks.bin"))) { "couldn't save the block file" }
        check(nanpaTemp.renameTo(File(directory, "nanpa.bin"))) { "couldn't save the exchange file" }
        File(directory, "source.txt").writeText("$sourceLabel|$today")
    }

    /** for example "nanpa reports on 2026-11-14", or null when the data has never been updated. */
    fun lastUpdate(): String? {
        val text = runCatching { File(directory, "source.txt").readText() }.getOrNull() ?: return null
        val label = text.substringBefore('|')
        val day = text.substringAfter('|', "").toIntOrNull() ?: return label
        return "$label on ${CarrierDataInfo.date(day)}"
    }
}
