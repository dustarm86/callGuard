package com.dustin.callguard.screening

import android.content.Context

/** loads the bundled nanpa lookups from the app assets. returns null when the exchange file was never built into this install. */
object NxxDirectoryLoader {
    fun load(context: Context): NxxDirectory? {
        val directory = runCatching { context.assets.open("nanpa.bin").use { NxxDirectory.parse(it) } }.getOrNull() ?: return null
        // the block file is optional: without it the directory still answers at the exchange level
        directory.blocks = runCatching { context.assets.open("blocks.bin").use { BlockDirectory.parse(it) } }.getOrNull()
        return directory
    }
}
