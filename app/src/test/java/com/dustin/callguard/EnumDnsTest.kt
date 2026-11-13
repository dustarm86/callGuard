package com.dustin.callguard

import com.dustin.callguard.dns.EnumDns
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnumDnsTest {
    private fun u16(out: ByteArrayOutputStream, value: Int) {
        out.write(value shr 8)
        out.write(value and 0xFF)
    }

    private fun label(out: ByteArrayOutputStream, text: String) {
        out.write(text.length)
        out.write(text.toByteArray())
    }

    private fun header(out: ByteArrayOutputStream, rcode: Int, answers: Int) {
        u16(out, 0x1234)
        u16(out, 0x8180 or rcode)
        u16(out, 1)
        u16(out, answers)
        u16(out, 0)
        u16(out, 0)
        // question: 1.e164.arpa, type naptr, class in
        label(out, "1"); label(out, "e164"); label(out, "arpa"); out.write(0)
        u16(out, 35); u16(out, 1)
    }

    @Test
    fun buildsTheEnumName() {
        assertEquals("4.8.9.3.8.0.8.3.3.8.1.e164.arpa", EnumDns.name("8338083984"))
        assertEquals("4.8.9.3.8.0.8.3.3.8.1.e164.arpa", EnumDns.name("18338083984"))
        assertEquals("4.8.9.3.8.0.8.3.3.8.1.e164.arpa", EnumDns.name("(833) 808-3984"))
    }

    @Test
    fun refusesNumbersThatAreNotFullNorthAmericanNumbers() {
        assertNull(EnumDns.name("833"))
        assertNull(EnumDns.name("442071234567"))
    }

    @Test
    fun readsAnNxdomainAnswer() {
        val out = ByteArrayOutputStream()
        header(out, rcode = 3, answers = 0)
        val answer = EnumDns.parse(out.toByteArray())
        assertEquals(3, answer.rcode)
        assertTrue(answer.records.isEmpty())
        assertTrue(EnumDns.describe("x.e164.arpa", answer).any { it.contains("no enum record") })
    }

    @Test
    fun readsANaptrRecordWithACompressedName() {
        val out = ByteArrayOutputStream()
        header(out, rcode = 0, answers = 1)
        out.write(0xC0); out.write(12) // owner name points at the question name
        u16(out, 35); u16(out, 1)
        u16(out, 0); u16(out, 300) // ttl
        val data = ByteArrayOutputStream()
        u16(data, 10); u16(data, 100)
        listOf("u", "E2U+sip", "!^.*$!sip:info@example.com!").forEach {
            data.write(it.length); data.write(it.toByteArray())
        }
        data.write(0xC0); data.write(12) // replacement name, also compressed
        u16(out, data.size())
        out.write(data.toByteArray())

        val answer = EnumDns.parse(out.toByteArray())
        assertEquals(0, answer.rcode)
        assertEquals(1, answer.records.size)
        val record = answer.records.single()
        assertEquals(10, record.order)
        assertEquals(100, record.preference)
        assertEquals("E2U+sip", record.service)
        assertEquals("!^.*$!sip:info@example.com!", record.regexp)
        assertEquals("1.e164.arpa", record.replacement)
    }

    @Test
    fun aCutOffAnswerIsRejectedInsteadOfCrashing() {
        val out = ByteArrayOutputStream()
        header(out, rcode = 0, answers = 1)
        out.write(0xC0)
        val bytes = out.toByteArray()
        for (length in 0..bytes.size) {
            runCatching { EnumDns.parse(bytes.copyOf(length)) }
                .onFailure { assertTrue(it is IllegalArgumentException) }
        }
    }

    @Test
    fun aServerFailureIsDescribedAsNoUsableResult() {
        val lines = EnumDns.describe("x.e164.arpa", EnumDns.Answer(rcode = 2, records = emptyList()))
        assertTrue(lines.any { it.contains("server failure") })
    }

    @Test
    fun buildsAWellFormedNaptrQuery() {
        val query = EnumDns.buildQuery("4.3.2.1.e164.arpa")
        // header: id 0, recursion desired, one question
        assertEquals(listOf(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0), query.take(12).map { it.toInt() })
        // labels 4 3 2 1 e164 arpa, then the root, then type 35 and class 1
        val expectedTail = listOf(1, '4'.code, 1, '3'.code, 1, '2'.code, 1, '1'.code, 4) +
            "e164".map { it.code } + listOf(4) + "arpa".map { it.code } + listOf(0, 0, 35, 0, 1)
        assertEquals(expectedTail, query.drop(12).map { it.toInt() })
    }

    @Test
    fun aQueryWeBuildParsesAsAnAnswerShapedPacketWithoutRecords() {
        val answer = EnumDns.parse(EnumDns.buildQuery("4.3.2.1.e164.arpa"))
        assertTrue(answer.records.isEmpty())
    }
}
