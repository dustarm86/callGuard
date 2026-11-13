package com.dustin.callguard.screening

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

enum class BlockHolderStatus {
    /** a carrier was assigned this 1,000-number block. */
    ASSIGNED,

    /** the carrier that holds the whole exchange kept this block for itself. */
    RETAINED,

    /** nobody holds the block right now, so numbers in it shouldn't be in service. */
    AVAILABLE,

    /** nobody holds the block, but some numbers in it still work (ported in or left over). */
    AVAILABLE_IN_USE,
}

data class BlockHolder(
    val status: BlockHolderStatus,
    val company: String? = null,
    val ocn: String? = null,
    // "2026-03", when known
    val assigned: String? = null,
)

/**
 * offline lookup of who holds each 1,000-number block (the first 7 digits), built from nanpa's thousands
 * block report by tools/buildBlockData.py. pooled exchanges are split into these blocks, so this is more
 * exact than the exchange-level [NxxDirectory]. read-only and pure, so it is unit tested.
 */
class BlockDirectory private constructor(
    /** date of the source file as yyyymmdd. */
    val asOf: Int,
    private val ocns: Array<String>,
    private val companies: Array<String>,
    private val keys: IntArray,
    // offsets[i] is the first entry of code i; offsets[keys.size] is the total entry count
    private val offsets: IntArray,
    private val words: IntArray,
    private val months: IntArray,
) {
    val codeCount: Int get() = keys.size

    fun lookup(normalizedNumber: String?): BlockHolder? {
        if (normalizedNumber == null || normalizedNumber.length != 10 || !normalizedNumber.all { it.isDigit() }) return null
        val index = keys.binarySearch(normalizedNumber.substring(0, 6).toInt())
        if (index < 0) return null
        val start = offsets[index]
        // one entry means all ten blocks of the exchange are identical
        val slot = if (offsets[index + 1] - start == 1) start else start + (normalizedNumber[6] - '0')
        val word = words[slot]
        if (word == NO_DATA) return null
        val status = when (word ushr 14) {
            0 -> BlockHolderStatus.ASSIGNED
            1 -> BlockHolderStatus.RETAINED
            2 -> BlockHolderStatus.AVAILABLE
            else -> BlockHolderStatus.AVAILABLE_IN_USE
        }
        if (status == BlockHolderStatus.AVAILABLE || status == BlockHolderStatus.AVAILABLE_IN_USE) return BlockHolder(status)
        val company = word and 0x3FFF
        return BlockHolder(
            status = status,
            company = companies[company].takeIf { it.isNotBlank() },
            ocn = ocns[company].takeIf { it.isNotBlank() },
            assigned = formatMonth(months[slot]),
        )
    }

    companion object {
        private const val NO_DATA = 0xFFFF

        private fun formatMonth(value: Int): String? {
            if (value <= 0) return null
            return "%04d-%02d".format(1900 + (value - 1) / 12, (value - 1) % 12 + 1)
        }

        fun parse(source: InputStream): BlockDirectory {
            val input = DataInputStream(BufferedInputStream(source, 64 * 1024))
            val magic = ByteArray(4).also { input.readFully(it) }
            if (String(magic, Charsets.US_ASCII) != "NXB1") throw IOException("not a callGuard block file")
            val asOf = input.readInt()
            val companyCount = input.readUnsignedShort()
            val ocns = Array(companyCount) { "" }
            val companies = Array(companyCount) { "" }
            for (i in 0 until companyCount) {
                ocns[i] = input.readUTF()
                companies[i] = input.readUTF()
            }
            val codeCount = input.readInt()
            val keys = IntArray(codeCount)
            val offsets = IntArray(codeCount + 1)
            for (i in 0 until codeCount) {
                keys[i] = input.readInt()
                offsets[i] = input.readInt()
            }
            val entryCount = input.readInt()
            offsets[codeCount] = entryCount
            val words = IntArray(entryCount)
            val months = IntArray(entryCount)
            for (i in 0 until entryCount) {
                words[i] = input.readUnsignedShort()
                months[i] = input.readUnsignedShort()
            }
            return BlockDirectory(asOf, ocns, companies, keys, offsets, words, months)
        }
    }
}
