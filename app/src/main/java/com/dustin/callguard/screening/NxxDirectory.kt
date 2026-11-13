package com.dustin.callguard.screening

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

enum class BlockStatus {
    /** no data: not a 10-digit number, or an area code the nanpa file doesn't cover (canada, caribbean, toll-free). */
    UNKNOWN,

    /** the area code is covered but this exchange is not assigned to any carrier. */
    UNASSIGNED,

    /** reserved for special use, like directory assistance or test codes. */
    UNAVAILABLE,

    /** assigned to a carrier but not in service yet. */
    NOT_IN_SERVICE,

    ASSIGNED,
}

data class BlockInfo(
    val status: BlockStatus,
    val company: String? = null,
    val ocn: String? = null,
    val rateCenter: String? = null,
    val state: String? = null,
    // pooled codes are split into 1,000-number blocks, so the listed carrier may not hold this exact block
    val pooled: Boolean = false,
    // "2014-09", when known
    val assigned: String? = null,
)

/**
 * offline lookup of who holds each area code + exchange (the first 6 digits), built from nanpa's
 * central office code report by tools/buildNanpaData.py. read-only and pure, so it is unit tested.
 */
class NxxDirectory private constructor(
    /** date of the nanpa file as yyyymmdd. */
    val asOf: Int,
    private val states: Array<String>,
    private val ocns: Array<String>,
    private val companies: Array<String>,
    private val rateCenters: Array<String>,
    private val keys: IntArray,
    private val companyIds: IntArray,
    private val rateCenterIds: IntArray,
    private val stateIds: IntArray,
    private val flags: IntArray,
    private val assignMonths: IntArray,
) {
    private val knownAreaCodes: Set<Int> = keys.mapTo(HashSet()) { it / 1000 }

    val size: Int get() = keys.size

    /** the 1,000-number block data, attached by [NxxDirectoryLoader] when blocks.bin is bundled; null otherwise. */
    @Volatile
    var blocks: BlockDirectory? = null

    fun blockHolder(normalizedNumber: String?): BlockHolder? = blocks?.lookup(normalizedNumber)

    fun lookup(normalizedNumber: String?): BlockInfo {
        if (normalizedNumber == null || normalizedNumber.length != 10 || !normalizedNumber.all { it.isDigit() }) {
            return BlockInfo(BlockStatus.UNKNOWN)
        }
        val key = normalizedNumber.substring(0, 6).toInt()
        val index = keys.binarySearch(key)
        if (index < 0) {
            // a missing code only means "unassigned" inside area codes the file actually covers
            return if (key / 1000 in knownAreaCodes) BlockInfo(BlockStatus.UNASSIGNED) else BlockInfo(BlockStatus.UNKNOWN)
        }
        val flag = flags[index]
        val status = when {
            flag and UNAVAILABLE != 0 -> BlockStatus.UNAVAILABLE
            flag and IN_SERVICE == 0 -> BlockStatus.NOT_IN_SERVICE
            else -> BlockStatus.ASSIGNED
        }
        return BlockInfo(
            status = status,
            company = companies[companyIds[index]].takeIf { it.isNotBlank() },
            ocn = ocns[companyIds[index]].takeIf { it.isNotBlank() },
            rateCenter = rateCenters[rateCenterIds[index]].takeIf { it.isNotBlank() },
            state = states[stateIds[index]].trim().takeIf { it.isNotEmpty() },
            pooled = flag and POOLED != 0,
            assigned = formatMonth(assignMonths[index]),
        )
    }

    companion object {
        private const val POOLED = 1
        private const val IN_SERVICE = 2
        private const val UNAVAILABLE = 4

        private fun formatMonth(value: Int): String? {
            if (value <= 0) return null
            val year = 1900 + (value - 1) / 12
            val month = (value - 1) % 12 + 1
            return "%04d-%02d".format(year, month)
        }

        fun parse(source: InputStream): NxxDirectory {
            val input = DataInputStream(BufferedInputStream(source, 64 * 1024))
            val magic = ByteArray(4).also { input.readFully(it) }
            if (String(magic, Charsets.US_ASCII) != "NXX1") throw IOException("not a callGuard nxx file")
            val asOf = input.readInt()
            val states = Array(input.readUnsignedShort()) {
                String(ByteArray(2).also { bytes -> input.readFully(bytes) }, Charsets.US_ASCII)
            }
            val companyCount = input.readUnsignedShort()
            val ocns = Array(companyCount) { "" }
            val companies = Array(companyCount) { "" }
            for (i in 0 until companyCount) {
                ocns[i] = input.readUTF()
                companies[i] = input.readUTF()
            }
            val rateCenters = Array(input.readInt()) { input.readUTF() }
            val count = input.readInt()
            val keys = IntArray(count)
            val companyIds = IntArray(count)
            val rateCenterIds = IntArray(count)
            val stateIds = IntArray(count)
            val flags = IntArray(count)
            val assignMonths = IntArray(count)
            for (i in 0 until count) {
                keys[i] = input.readInt()
                companyIds[i] = input.readUnsignedShort()
                rateCenterIds[i] = input.readUnsignedShort()
                stateIds[i] = input.readUnsignedByte()
                flags[i] = input.readUnsignedByte()
                assignMonths[i] = input.readUnsignedShort()
            }
            return NxxDirectory(
                asOf, states, ocns, companies, rateCenters,
                keys, companyIds, rateCenterIds, stateIds, flags, assignMonths,
            )
        }
    }
}
