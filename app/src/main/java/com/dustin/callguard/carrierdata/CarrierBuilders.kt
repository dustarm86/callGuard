package com.dustin.callguard.carrierdata

import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

/** the same files tools/buildNanpaData.py and tools/buildBlockData.py write, built on the phone from the nanpa reports. */
private fun asciiText(value: String): String =
    value.trim().map { if (it.code < 128) it else '?' }.joinToString("")

private val slashDate = Regex("(\\d+)/(\\d+)/(\\d{4})")

/** months since 1900, the way the data files store a "date assigned"; 0 when the text has no date at the start. */
private fun assignMonths(text: String): Int {
    val match = slashDate.find(text)?.takeIf { it.range.first == 0 } ?: return 0
    return (match.groupValues[3].toInt() - 1900) * 12 + match.groupValues[1].toInt()
}

/** nanpa's "central office code assignment, utilized" report (tab separated) becomes nanpa.bin. */
object NanpaBuilder {
    fun build(reader: BufferedReader): ByteArray {
        val header = reader.readLine() ?: throw IOException("the exchange report is empty")
        val headerFields = Delimited.parseLine(header.removePrefix("﻿"), '\t')
        val asOf = slashDate.find(headerFields.last())?.let {
            it.groupValues[3].toInt() * 10000 + it.groupValues[1].toInt() * 100 + it.groupValues[2].toInt()
        } ?: 0

        val states = LinkedHashMap<String, Int>()
        val companies = LinkedHashMap<Pair<String, String>, Int>()
        val rateCenters = LinkedHashMap<String, Int>()
        val records = ArrayList<IntArray>(160_000)

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            val row = Delimited.parseLine(line, '\t')
            if (row.size < 11) continue
            val cells = row.take(11).map { it.trim() }
            val state = cells[0]
            val digits = cells[1].replace("-", "")
            if (digits.length != 6 || !digits.all { it in '0'..'9' }) continue
            val stateId = states.getOrPut(state.take(2)) { states.size }
            val companyId = companies.getOrPut(asciiText(cells[2]) to asciiText(cells[3])) { companies.size }
            val rateCenterId = rateCenters.getOrPut(asciiText(cells[4])) { rateCenters.size }
            val flags = (if (cells[9] == "Y") 1 else 0) or (if (cells[10] == "Yes") 2 else 0) or (if (cells[6] == "UA") 4 else 0)
            records.add(intArrayOf(digits.toInt(), companyId, rateCenterId, stateId, flags, assignMonths(cells[7])))
        }
        if (states.size >= 256 || companies.size >= 65_536 || rateCenters.size >= 65_536) {
            throw IOException("the exchange report has more states, carriers or rate centers than the file format holds")
        }
        records.sortWith(compareBy<IntArray>({ it[0] }, { it[1] }, { it[2] }, { it[3] }, { it[4] }, { it[5] }))

        val bytes = ByteArrayOutputStream(4 * 1024 * 1024)
        DataOutputStream(bytes).use { out ->
            out.write("NXX1".toByteArray(Charsets.US_ASCII))
            out.writeInt(asOf)
            out.writeShort(states.size)
            states.keys.forEach { out.write(it.padEnd(2).take(2).toByteArray(Charsets.US_ASCII)) }
            out.writeShort(companies.size)
            companies.keys.forEach { (ocn, name) ->
                out.writeUTF(ocn)
                out.writeUTF(name)
            }
            out.writeInt(rateCenters.size)
            rateCenters.keys.forEach { out.writeUTF(it) }
            out.writeInt(records.size)
            for (record in records) {
                out.writeInt(record[0])
                out.writeShort(record[1])
                out.writeShort(record[2])
                out.writeByte(record[3])
                out.writeByte(record[4])
                out.writeShort(record[5])
            }
        }
        return bytes.toByteArray()
    }
}

/** nanpa's "thousands block assignment" report (comma separated) becomes blocks.bin. [asOf] is yyyymmdd, the day it was downloaded. */
object BlockBuilder {
    private const val MISSING = 0xFFFFL shl 16

    fun build(reader: BufferedReader, asOf: Int): ByteArray {
        val header = reader.readLine() ?: throw IOException("the block report is empty")
        val names = Delimited.parseLine(header.removePrefix("﻿"), ',').map { it.trim() }
        fun column(name: String) = names.indexOf(name).also { if (it < 0) throw IOException("the block report has no \"$name\" column") }
        val npaAt = column("NPA")
        val nxxAt = column("NXX")
        val xAt = column("X")
        val statusAt = column("STATUS")
        val ocnAt = column("OCN")
        val nameAt = column("Assigned To")
        val dateAt = column("Date Assigned")
        val contaminatedAt = column("Contaminated (Y or N)")

        val companies = LinkedHashMap<Pair<String, String>, Int>()
        val codes = HashMap<Int, LongArray>(200_000)

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            val row = Delimited.parseLine(line, ',')
            fun cell(at: Int) = row.getOrNull(at)?.trim().orEmpty()
            val npa = cell(npaAt)
            val nxx = cell(nxxAt)
            val x = cell(xAt)
            if (npa.length != 3 || nxx.length != 3 || x.length != 1) continue
            if (!npa.all { it in '0'..'9' } || !nxx.all { it in '0'..'9' } || x[0] !in '0'..'9') continue
            val status = cell(statusAt)
            var months = 0
            val statusCode: Int
            var index = 0
            if (status == "AS" || status == "RT") {
                statusCode = if (status == "AS") 0 else 1
                index = companies.getOrPut(asciiText(cell(ocnAt)) to asciiText(cell(nameAt))) { companies.size }
                months = assignMonths(cell(dateAt))
            } else {
                statusCode = if (cell(contaminatedAt) == "Y") 3 else 2
            }
            val blocks = codes.getOrPut(npa.toInt() * 1000 + nxx.toInt()) { LongArray(10) { MISSING } }
            blocks[x[0] - '0'] = (((statusCode shl 14) or index).toLong() shl 16) or months.toLong()
        }
        if (companies.size >= 16_384) throw IOException("the block report has more carriers than the file format holds")

        val keys = codes.keys.sorted()
        val entries = ArrayList<Long>(keys.size * 10)
        val offsets = IntArray(keys.size)
        keys.forEachIndexed { i, key ->
            val blocks = codes.getValue(key)
            offsets[i] = entries.size
            if (blocks.none { it == MISSING } && blocks.toSet().size == 1) entries.add(blocks[0]) else blocks.forEach { entries.add(it) }
        }

        val bytes = ByteArrayOutputStream(8 * 1024 * 1024)
        DataOutputStream(bytes).use { out ->
            out.write("NXB1".toByteArray(Charsets.US_ASCII))
            out.writeInt(asOf)
            out.writeShort(companies.size)
            companies.keys.forEach { (ocn, name) ->
                out.writeUTF(ocn)
                out.writeUTF(name)
            }
            out.writeInt(keys.size)
            keys.forEachIndexed { i, key ->
                out.writeInt(key)
                out.writeInt(offsets[i])
            }
            out.writeInt(entries.size)
            for (entry in entries) {
                out.writeShort((entry shr 16).toInt())
                out.writeShort((entry and 0xFFFF).toInt())
            }
        }
        return bytes.toByteArray()
    }
}
