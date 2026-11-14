package com.dustin.callguard.screening

import android.content.Context
import com.dustin.callguard.carrierdata.CarrierStore

/**
 * loads the carrier lookups. the app ships a copy in its assets, and the person can download a newer one;
 * whichever has the newer "as of" date wins, and a downloaded file that can't be read is ignored.
 * returns null when no exchange file could be read at all.
 */
object NxxDirectoryLoader {
    fun load(context: Context): NxxDirectory? {
        val store = CarrierStore(context)
        val downloaded = store.nanpaFile()?.let { file -> runCatching { file.inputStream().use { NxxDirectory.parse(it) } }.getOrNull() }
        val bundled = runCatching { context.assets.open("nanpa.bin").use { NxxDirectory.parse(it) } }.getOrNull()
        // listed downloaded-first so a tie goes to the downloaded copy
        val directory = listOfNotNull(downloaded, bundled).maxByOrNull { it.asOf } ?: return null

        // the block file is optional: without it the directory still answers at the exchange level
        val downloadedBlocks = store.blocksFile()?.let { file -> runCatching { file.inputStream().use { BlockDirectory.parse(it) } }.getOrNull() }
        val bundledBlocks = runCatching { context.assets.open("blocks.bin").use { BlockDirectory.parse(it) } }.getOrNull()
        directory.blocks = listOfNotNull(downloadedBlocks, bundledBlocks).maxByOrNull { it.asOf }
        return directory
    }
}
