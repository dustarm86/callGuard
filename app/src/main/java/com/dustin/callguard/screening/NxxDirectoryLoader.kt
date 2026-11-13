package com.dustin.callguard.screening

import android.content.Context

/** loads the bundled nanpa lookup from the app assets. returns null when it was never built into this install. */
object NxxDirectoryLoader {
    fun load(context: Context): NxxDirectory? =
        runCatching { context.assets.open("nanpa.bin").use { NxxDirectory.parse(it) } }.getOrNull()
}
