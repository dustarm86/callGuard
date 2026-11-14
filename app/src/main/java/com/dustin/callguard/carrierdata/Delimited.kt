package com.dustin.callguard.carrierdata

/** splits one line of a comma or tab separated report, honoring "quoted" fields the way the nanpa files use them. */
object Delimited {
    fun parseLine(line: String, delimiter: Char): List<String> {
        val fields = ArrayList<String>(16)
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted -> if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(c)
                }
                c == '"' && field.isEmpty() -> quoted = true
                c == delimiter -> {
                    fields.add(field.toString())
                    field.setLength(0)
                }
                else -> field.append(c)
            }
            i++
        }
        fields.add(field.toString())
        return fields
    }
}
