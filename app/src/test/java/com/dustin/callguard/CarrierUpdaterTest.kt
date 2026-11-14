package com.dustin.callguard

import com.dustin.callguard.carrierdata.BlockBuilder
import com.dustin.callguard.carrierdata.Built
import com.dustin.callguard.carrierdata.CarrierSource
import com.dustin.callguard.carrierdata.CarrierUpdater
import com.dustin.callguard.carrierdata.NanpaBuilder
import com.dustin.callguard.carrierdata.SourceKind
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierUpdaterTest {
    private fun source(label: String) = CarrierSource(label, SourceKind.PREBUILT, "https://example.test/$label/n", "https://example.test/$label/b")

    private fun built(blocksAsOf: Int) = Built(
        nanpa = NanpaBuilder.build(BufferedReader(StringReader(CarrierBuildersTest.Reports.exchanges))),
        blocks = BlockBuilder.build(BufferedReader(StringReader(CarrierBuildersTest.Reports.blocks)), blocksAsOf),
    )

    private val installed = mutableListOf<String>()

    private fun updater(minCodes: Int = 1, fetch: (CarrierSource) -> Built) =
        CarrierUpdater(fetch = fetch, install = { _, source -> installed += source.label }, minExchanges = minCodes, minBlockCodes = minCodes)

    @Test
    fun skipsFailedAndOutOfDateSourcesAndStopsAtTheFirstGoodOne() {
        val updater = updater { source ->
            when (source.label) {
                "a" -> throw IOException("http 403")
                "b" -> built(blocksAsOf = 20200101)
                else -> built(blocksAsOf = 20261114)
            }
        }
        val lines = updater.run(listOf(source("a"), source("b"), source("c"), source("d")), currentNanpaAsOf = 20261001, currentBlocksAsOf = 20261113)

        assertEquals(listOf("c"), installed)
        assertTrue(lines.first().startsWith("result: updated from c."))
        assertTrue(lines.contains("✗ a: http 403"))
        assertTrue(lines.any { it.startsWith("✗ b: has older data") })
        assertTrue(lines.any { it.startsWith("✓ c:") })
        assertTrue(lines.none { it.startsWith("✓ d") || it.startsWith("✗ d") })
    }

    @Test
    fun whenEverySourceFailsNothingIsInstalledAndTheVerdictSaysSo() {
        val lines = updater { throw IOException("couldn't connect") }.run(listOf(source("a"), source("b")), 0, 0)
        assertTrue(installed.isEmpty())
        assertTrue(lines.first().startsWith("result: couldn't update"))
        assertEquals(3, lines.size)
    }

    @Test
    fun aDamagedFileIsRejectedBeforeItIsInstalled() {
        val lines = updater { Built(ByteArray(10) { 1 }, ByteArray(10) { 1 }) }.run(listOf(source("a")), 0, 0)
        assertTrue(installed.isEmpty())
        assertTrue(lines[1].startsWith("✗ a:"))
    }

    @Test
    fun aFileWithTooFewCodesLooksDamaged() {
        val lines = updater(minCodes = 1_000) { built(20261114) }.run(listOf(source("a")), 0, 0)
        assertTrue(installed.isEmpty())
        assertTrue(lines[1].contains("looks damaged"))
    }

    @Test
    fun reportsWhichSourceItIsTrying() {
        val seen = mutableListOf<List<String>>()
        updater { built(20261114) }.run(listOf(source("a")), 0, 0) { seen += it }
        assertEquals(listOf("result: updating…", "trying a…"), seen.first())
    }
}
