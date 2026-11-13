package com.dustin.callguard

import com.dustin.callguard.lookup.LineInfo
import com.dustin.callguard.lookup.LineLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineLookupTest {
    private val success = """
        {"status":"success","phone":"+16505550123","phone_valid":true,"phone_type":"voip",
         "phone_region":"California","country":"United States","country_code":"US","carrier":"Bandwidth"}
    """.trimIndent()

    @Test
    fun buildsTheAddressWithAnEncodedPlusAndKey() {
        assertEquals(
            "https://api.veriphone.io/v2/verify?phone=%2B13159488351&key=abc%26def",
            LineLookup.url("3159488351", " abc&def "),
        )
    }

    @Test
    fun readsASuccessfulAnswer() {
        val info = LineLookup.parse(success)
        assertEquals(true, info.valid)
        assertEquals("voip", info.type)
        assertEquals("Bandwidth", info.carrier)
        assertEquals("California", info.region)
        assertNull(info.error)
    }

    @Test
    fun anErrorAnswerCarriesItsMessage() {
        val info = LineLookup.parse("""{"status":"error","message":"Invalid API key"}""")
        assertEquals("Invalid API key", info.error)
        assertEquals(listOf("lookup failed: Invalid API key"), LineLookup.describe(info))
    }

    @Test
    fun missingFieldsAreSkippedAndGarbageIsAnErrorNotACrash() {
        val sparse = LineLookup.parse("""{"status":"success","phone_type":"mobile","carrier":""}""")
        assertEquals("mobile", sparse.type)
        assertNull(sparse.carrier)
        assertNull(sparse.valid)
        assertNotNull(LineLookup.parse("<html>nope</html>").error)
    }

    @Test
    fun describesTheLineTypeInPlainWords() {
        val lines = LineLookup.describe(LineLookup.parse(success))
        assertTrue(lines.any { it.startsWith("line type: internet phone (voip)") })
        assertTrue(lines.any { it == "carrier: Bandwidth" })
        assertTrue(lines.last().contains("out of date"))
        val landline = LineLookup.describe(LineInfo(true, "fixed_line", null, null, null))
        assertTrue(landline.any { it == "line type: landline" })
    }

    @Test
    fun anEmptyAnswerSaysSo() {
        val lines = LineLookup.describe(LineInfo(null, null, null, null, null))
        assertTrue(lines.first().startsWith("the service answered but gave no details"))
    }
}
