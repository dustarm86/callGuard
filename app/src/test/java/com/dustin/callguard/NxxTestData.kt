package com.dustin.callguard

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** builds a tiny nxx file in memory, in the same layout tools/buildNanpaData.py writes. */
object NxxTestData {
    data class Record(
        val key: Int,
        val company: Int = 0,
        val rateCenter: Int = 0,
        val state: Int = 0,
        val flags: Int = 2,
        val months: Int = 0,
    )

    // 2014-09 as (year - 1900) * 12 + month
    const val SEPT_2014 = (2014 - 1900) * 12 + 9

    fun build(records: List<Record>): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.write("NXX1".toByteArray(Charsets.US_ASCII))
        out.writeInt(20261112)
        out.writeShort(2)
        out.write("NY".toByteArray(Charsets.US_ASCII))
        out.write("CA".toByteArray(Charsets.US_ASCII))
        out.writeShort(2)
        out.writeUTF("6563"); out.writeUTF("CELLCO PARTNERSHIP DBA VERIZON WIRELESS")
        out.writeUTF("9104"); out.writeUTF("VERIZON NEW YORK, INC.")
        out.writeInt(2)
        out.writeUTF("SYRACUSE"); out.writeUTF("ALBANY")
        out.writeInt(records.size)
        for (record in records.sortedBy { it.key }) {
            out.writeInt(record.key)
            out.writeShort(record.company)
            out.writeShort(record.rateCenter)
            out.writeByte(record.state)
            out.writeByte(record.flags)
            out.writeShort(record.months)
        }
        return bytes.toByteArray()
    }

    // flags: 1 pooled, 2 in service, 4 unavailable
    val sample = build(
        listOf(
            Record(315200, company = 0, rateCenter = 0, flags = 3, months = SEPT_2014),
            Record(315201, company = 1, rateCenter = 1, flags = 2),
            Record(315202, company = 1, flags = 0),
            Record(315555, flags = 4),
        )
    )
}
