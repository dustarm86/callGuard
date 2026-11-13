package com.dustin.callguard.dns

/**
 * enum (rfc 6116) maps a phone number to dns naptr records under e164.arpa. this is the pure part:
 * building the name and reading the raw dns answer. the network part is in [EnumLookup].
 */
object EnumDns {
    const val NAPTR_TYPE = 35

    data class NaptrRecord(
        val order: Int,
        val preference: Int,
        val flags: String,
        val service: String,
        val regexp: String,
        val replacement: String,
    )

    data class Answer(val rcode: Int, val records: List<NaptrRecord>)

    /** 8338083984 becomes 4.8.9.3.8.0.8.3.3.8.1.e164.arpa; null unless it is a full 10 or 11 digit north american number. */
    fun name(number: String): String? {
        val digits = number.filter(Char::isDigit)
        val full = when {
            digits.length == 10 -> "1$digits"
            digits.length == 11 && digits.startsWith("1") -> digits
            else -> return null
        }
        return full.reversed().toCharArray().joinToString(".") + ".e164.arpa"
    }

    /** a standard dns query for naptr records, as the body of an https (rfc 8484) request. */
    fun buildQuery(queryName: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun u16(value: Int) {
            out.write(value shr 8)
            out.write(value and 0xFF)
        }
        u16(0) // id; https queries use 0 so they can be cached
        u16(0x0100) // recursion desired
        u16(1)
        u16(0)
        u16(0)
        u16(0)
        queryName.split('.').filter { it.isNotEmpty() }.forEach { label ->
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0)
        u16(NAPTR_TYPE)
        u16(1)
        return out.toByteArray()
    }

    /** reads a raw dns response; throws IllegalArgumentException when it is cut off or malformed. */
    fun parse(packet: ByteArray): Answer {
        val reader = Reader(packet)
        reader.skip(2)
        val flags = reader.u16()
        val questions = reader.u16()
        val answers = reader.u16()
        reader.skip(4)
        repeat(questions) {
            reader.name()
            reader.skip(4)
        }
        val records = mutableListOf<NaptrRecord>()
        repeat(answers) {
            reader.name()
            val type = reader.u16()
            reader.skip(6) // class and ttl
            val length = reader.u16()
            val end = reader.position + length
            if (type == NAPTR_TYPE) {
                records += NaptrRecord(
                    order = reader.u16(),
                    preference = reader.u16(),
                    flags = reader.characterString(),
                    service = reader.characterString(),
                    regexp = reader.characterString(),
                    replacement = reader.name(),
                )
            }
            reader.position = end
        }
        return Answer(flags and 0xF, records)
    }

    /** plain-language lines for the probe screen. */
    fun describe(queryName: String, answer: Answer): List<String> {
        val lines = mutableListOf("asked dns for naptr records at $queryName")
        when {
            answer.records.isNotEmpty() -> {
                answer.records.sortedWith(compareBy({ it.order }, { it.preference })).forEach { record ->
                    lines += "service ${record.service} (order ${record.order}, preference ${record.preference})"
                    if (record.regexp.isNotEmpty()) lines += "  rewrite: ${record.regexp}"
                    if (record.replacement.isNotEmpty()) lines += "  replacement: ${record.replacement}"
                }
                lines += "someone registered this number in enum, which is unusual for a us number"
            }
            answer.rcode == 3 -> {
                lines += "no enum record: the name doesn't exist in public dns"
                lines += "expected for nearly every us number, because the us has no public enum registry. this says nothing about whether the caller is real or spoofed"
            }
            answer.rcode == 0 -> {
                lines += "the name exists but holds no naptr records"
                lines += "some dns proxies answer this way for record types they don't support, so treat it as no information"
            }
            else -> lines += "dns answered with ${rcodeName(answer.rcode)}, so there is no usable result"
        }
        return lines
    }

    private fun rcodeName(rcode: Int) = when (rcode) {
        1 -> "format error"
        2 -> "server failure"
        4 -> "not implemented"
        5 -> "refused"
        else -> "code $rcode"
    }

    private class Reader(private val bytes: ByteArray) {
        var position = 0

        fun skip(count: Int) {
            require(position + count <= bytes.size) { "dns answer is cut off" }
            position += count
        }

        fun u16(): Int {
            require(position + 2 <= bytes.size) { "dns answer is cut off" }
            val value = ((bytes[position].toInt() and 0xFF) shl 8) or (bytes[position + 1].toInt() and 0xFF)
            position += 2
            return value
        }

        fun characterString(): String {
            require(position < bytes.size) { "dns answer is cut off" }
            val length = bytes[position].toInt() and 0xFF
            require(position + 1 + length <= bytes.size) { "dns answer is cut off" }
            val text = String(bytes, position + 1, length, Charsets.ISO_8859_1)
            position += 1 + length
            return text
        }

        /** reads a domain name, following compression pointers. */
        fun name(): String {
            val labels = mutableListOf<String>()
            var cursor = position
            var jumped = false
            var hops = 0
            while (true) {
                require(cursor < bytes.size) { "dns answer is cut off" }
                val length = bytes[cursor].toInt() and 0xFF
                if (length == 0) {
                    if (!jumped) position = cursor + 1
                    break
                } else if (length and 0xC0 == 0xC0) {
                    require(cursor + 1 < bytes.size) { "dns answer is cut off" }
                    val target = ((length and 0x3F) shl 8) or (bytes[cursor + 1].toInt() and 0xFF)
                    if (!jumped) position = cursor + 2
                    jumped = true
                    require(++hops <= 20) { "dns answer loops" }
                    cursor = target
                } else {
                    require(cursor + 1 + length <= bytes.size) { "dns answer is cut off" }
                    labels += String(bytes, cursor + 1, length, Charsets.US_ASCII)
                    cursor += 1 + length
                }
            }
            return labels.joinToString(".")
        }
    }
}
