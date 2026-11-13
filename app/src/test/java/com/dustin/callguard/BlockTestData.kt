package com.dustin.callguard

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** builds a tiny block file in memory, in the same layout tools/buildBlockData.py writes. */
object BlockTestData {
    // (word, assignMonths) for one block
    data class Entry(val word: Int, val months: Int = 0)

    // one record per exchange: ten entries, or a single entry meaning all ten blocks are the same
    data class Code(val key: Int, val entries: List<Entry>)

    const val ASSIGNED = 0
    const val RETAINED = 1
    const val AVAILABLE = 2
    const val AVAILABLE_IN_USE = 3
    const val NO_DATA = 0xFFFF

    // 2014-09 as (year - 1900) * 12 + month
    const val SEPT_2014 = (2014 - 1900) * 12 + 9

    fun word(status: Int, company: Int = 0) = (status shl 14) or company

    fun build(codes: List<Code>): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.write("NXB1".toByteArray(Charsets.US_ASCII))
        out.writeInt(20261113)
        out.writeShort(2)
        out.writeUTF("6563"); out.writeUTF("CELLCO PARTNERSHIP DBA VERIZON WIRELESS")
        out.writeUTF("490J"); out.writeUTF("BOOST SUBSCRIBERCO L.L.C.")
        val sorted = codes.sortedBy { it.key }
        out.writeInt(sorted.size)
        var offset = 0
        for (code in sorted) {
            out.writeInt(code.key)
            out.writeInt(offset)
            offset += code.entries.size
        }
        out.writeInt(offset)
        for (code in sorted) {
            for (entry in code.entries) {
                out.writeShort(entry.word)
                out.writeShort(entry.months)
            }
        }
        return bytes.toByteArray()
    }

    val sample = build(
        listOf(
            // every block assigned to boost, stored as a single entry
            Code(315200, listOf(Entry(word(ASSIGNED, company = 1), SEPT_2014))),
            // verizon retains most blocks; block 3 is free, block 4 is free but has working numbers, block 9 has no data
            Code(
                315201,
                List(10) { block ->
                    when (block) {
                        3 -> Entry(word(AVAILABLE))
                        4 -> Entry(word(AVAILABLE_IN_USE))
                        9 -> Entry(NO_DATA)
                        else -> Entry(word(RETAINED, company = 0))
                    }
                },
            ),
        )
    )
}
