package com.dustin.callguard.carrierdata

import com.dustin.callguard.screening.BlockDirectory
import com.dustin.callguard.screening.NxxDirectory
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** the two data files, ready to install. */
class Built(val nanpa: ByteArray, val blocks: ByteArray)

/** what a built pair of files holds, read back from the bytes themselves. */
data class Checked(val exchanges: Int, val nanpaAsOf: Int, val blockCodes: Int, val blocksAsOf: Int)

/**
 * works down a list of sources until one gives usable data. a source that fails, gives a damaged file, or has
 * older data than the app already holds is skipped and the next one is tried. nothing is installed until a
 * source passes every check, so the app never ends up with half an update.
 */
class CarrierUpdater(
    private val fetch: (CarrierSource, (CarrierProgress) -> Unit) -> Built,
    private val install: (Built, CarrierSource) -> Unit,
    private val minExchanges: Int = 50_000,
    private val minBlockCodes: Int = 50_000,
) {
    fun check(built: Built): Checked {
        val exchanges = NxxDirectory.parse(ByteArrayInputStream(built.nanpa))
        if (exchanges.size < minExchanges) throw IOException("the exchange file has only ${exchanges.size} codes, so it looks damaged")
        val blocks = BlockDirectory.parse(ByteArrayInputStream(built.blocks))
        if (blocks.codeCount < minBlockCodes) throw IOException("the block file has only ${blocks.codeCount} codes, so it looks damaged")
        return Checked(exchanges.size, exchanges.asOf, blocks.codeCount, blocks.asOf)
    }

    /** returns the lines for the screen; the first starts with "result:" and says what happened. */
    fun run(
        sources: List<CarrierSource>,
        currentNanpaAsOf: Int,
        currentBlocksAsOf: Int,
        progress: (List<String>) -> Unit = {},
        onDownload: (CarrierProgress) -> Unit = {},
    ): List<String> {
        val log = mutableListOf<String>()
        for (source in sources) {
            progress(listOf("result: updating…") + log + "trying ${source.label}…")
            try {
                val built = fetch(source, onDownload)
                val checked = check(built)
                if (checked.nanpaAsOf < currentNanpaAsOf || checked.blocksAsOf < currentBlocksAsOf) {
                    log += "✗ ${source.label}: has older data (exchanges ${CarrierDataInfo.date(checked.nanpaAsOf)}, " +
                        "blocks ${CarrierDataInfo.date(checked.blocksAsOf)}) than the app already holds"
                    continue
                }
                install(built, source)
                log += "✓ ${source.label}: downloaded and checked"
                return listOf(
                    "result: updated from ${source.label}. ${checked.exchanges} exchanges as of ${CarrierDataInfo.date(checked.nanpaAsOf)}, " +
                        "${checked.blockCodes} block codes as of ${CarrierDataInfo.date(checked.blocksAsOf)}",
                ) + log
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log += "✗ ${source.label}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return listOf("result: couldn't update. every source failed or had older data, so the app keeps what it has") + log
    }
}
