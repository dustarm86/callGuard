package com.dustin.callguard.carrierdata

import com.dustin.callguard.screening.NxxDirectory

object CarrierDataInfo {
    /** 20261114 becomes 2026-11-14. */
    fun date(asOf: Int): String =
        if (asOf <= 0) "unknown" else "%04d-%02d-%02d".format(asOf / 10000, asOf / 100 % 100, asOf % 100)

    fun lines(directory: NxxDirectory?, lastUpdate: String?): List<String> {
        if (directory == null) return listOf("carrier data isn't loaded")
        val blocks = directory.blocks
        return listOf(
            "exchanges: ${directory.size} codes, data as of ${date(directory.asOf)}",
            if (blocks == null) "1,000-blocks: not loaded" else "1,000-blocks: ${blocks.codeCount} codes, data as of ${date(blocks.asOf)}",
            if (lastUpdate == null) "built into the app, never updated from the internet" else "last updated from $lastUpdate",
        )
    }

    fun summary(directory: NxxDirectory?): String =
        if (directory == null) "not loaded" else "exchanges as of ${date(directory.asOf)}"
}
