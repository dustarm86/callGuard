package com.dustin.callguard

import com.dustin.callguard.carrierdata.Delimited
import org.junit.Assert.assertEquals
import org.junit.Test

class DelimitedTest {
    @Test
    fun splitsOnTheDelimiterAndKeepsEmptyFields() {
        assertEquals(listOf("a", "", "c", ""), Delimited.parseLine("a,,c,", ','))
        assertEquals(listOf("NY", "315-848", ""), Delimited.parseLine("NY\t315-848\t", '\t'))
    }

    @Test
    fun aQuotedFieldKeepsItsDelimiter() {
        assertEquals(listOf("a", "VERIZON NEW YORK, INC.", "c"), Delimited.parseLine("a,\"VERIZON NEW YORK, INC.\",c", ','))
    }

    @Test
    fun aDoubledQuoteInsideAQuotedFieldIsOneQuote() {
        assertEquals(listOf("say \"hi\"", "x"), Delimited.parseLine("\"say \"\"hi\"\"\",x", ','))
    }

    @Test
    fun aQuoteInTheMiddleOfAFieldIsJustText() {
        assertEquals(listOf("5\" pipe", "x"), Delimited.parseLine("5\" pipe,x", ','))
    }
}
