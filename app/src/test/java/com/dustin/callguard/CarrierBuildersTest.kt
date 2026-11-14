package com.dustin.callguard

import com.dustin.callguard.carrierdata.BlockBuilder
import com.dustin.callguard.carrierdata.NanpaBuilder
import com.dustin.callguard.screening.BlockDirectory
import com.dustin.callguard.screening.BlockHolderStatus
import com.dustin.callguard.screening.BlockStatus
import com.dustin.callguard.screening.NxxDirectory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierBuildersTest {
    object Reports {
        val exchanges = listOf(
            "State\tNPA-NXX\tOCN\tCompany\tRate Center\tEffective Date\tUse\tAssign Date\tInit/Growth\tPooled\tIn Service Report 11/10/2026",
            "NY\t315-848\t6563\tCELLCO PARTNERSHIP DBA VERIZON WIRELESS\tWATERTOWN\t01/01/2010\tAS\t09/15/2014\tG\tY\tYes",
            "NY\t315-200\t9104\t\"VERIZON NEW YORK, INC.\"\tSYRACUSE\t01/01/2001\tAS\t\t\tN\tNo",
            "NY\t315-555\t0000\tNONE\tX\t01/01/2001\tUA\t\t\tN\tNo",
            "garbage",
            "NY\tnot-a-code\t1\t2\t3\t4\t5\t6\t7\t8\t9",
        ).joinToString("\n")

        private val blockHeader = "State,NPA,NXX,X,Rate Center,STATUS,OCN,Assigned To,Date Assigned,Contaminated (Y or N)"

        val blocks: String = buildList {
            add(blockHeader)
            // every block of 315-200 is the same carrier, which the file stores as one entry
            for (x in 0..9) add("NY,315,200,$x,SYRACUSE,AS,490J,BOOST SUBSCRIBERCO L.L.C.,09/12/2014,N")
            // 315-201: retained blocks 0-2, free block 3, free-but-in-use block 4, nothing for 5-9
            for (x in 0..2) add("NY,315,201,$x,SYRACUSE,RT,6563,CELLCO PARTNERSHIP DBA VERIZON WIRELESS,,N")
            add("NY,315,201,3,SYRACUSE,AV,,,,N")
            add("NY,315,201,4,SYRACUSE,AV,,,,Y")
            add("NY,315,202,0,SYRACUSE,AS,1234,CAFÉ TELECOM,01/05/2020,N")
            add("NY,31,202,1,SYRACUSE,AS,1234,SHORT CODE,01/05/2020,N")
        }.joinToString("\n")
    }

    private fun reader(text: String) = BufferedReader(StringReader(text))

    @Test
    fun theExchangeReportBecomesAFileTheAppCanRead() {
        val directory = NxxDirectory.parse(ByteArrayInputStream(NanpaBuilder.build(reader(Reports.exchanges))))
        assertEquals(3, directory.size)
        assertEquals(20261110, directory.asOf)

        val watertown = directory.lookup("3158485551")
        assertEquals(BlockStatus.ASSIGNED, watertown.status)
        assertEquals("CELLCO PARTNERSHIP DBA VERIZON WIRELESS", watertown.company)
        assertEquals("6563", watertown.ocn)
        assertEquals("WATERTOWN", watertown.rateCenter)
        assertEquals("NY", watertown.state)
        assertTrue(watertown.pooled)
        assertEquals("2014-09", watertown.assigned)

        val syracuse = directory.lookup("3152005551")
        assertEquals(BlockStatus.NOT_IN_SERVICE, syracuse.status)
        assertEquals("VERIZON NEW YORK, INC.", syracuse.company)
        assertNull(syracuse.assigned)

        assertEquals(BlockStatus.UNAVAILABLE, directory.lookup("3155555551").status)
        assertEquals(BlockStatus.UNASSIGNED, directory.lookup("3159995551").status)
    }

    @Test(expected = IOException::class)
    fun anEmptyExchangeReportIsRejected() {
        NanpaBuilder.build(reader(""))
    }

    @Test
    fun theBlockReportBecomesAFileTheAppCanRead() {
        val directory = BlockDirectory.parse(ByteArrayInputStream(BlockBuilder.build(reader(Reports.blocks), 20261114)))
        assertEquals(20261114, directory.asOf)
        assertEquals(3, directory.codeCount)

        val boost = directory.lookup("3152005551")!!
        assertEquals(BlockHolderStatus.ASSIGNED, boost.status)
        assertEquals("BOOST SUBSCRIBERCO L.L.C.", boost.company)
        assertEquals("490J", boost.ocn)
        assertEquals("2014-09", boost.assigned)
        for (digit in 0..9) assertEquals(BlockHolderStatus.ASSIGNED, directory.lookup("315200${digit}000")!!.status)

        val retained = directory.lookup("3152010000")!!
        assertEquals(BlockHolderStatus.RETAINED, retained.status)
        assertEquals("CELLCO PARTNERSHIP DBA VERIZON WIRELESS", retained.company)
        assertEquals(BlockHolderStatus.AVAILABLE, directory.lookup("3152013000")!!.status)
        assertEquals(BlockHolderStatus.AVAILABLE_IN_USE, directory.lookup("3152014000")!!.status)
        assertNull(directory.lookup("3152019000"))
    }

    @Test
    fun nonAsciiLettersBecomeQuestionMarksLikeThePythonBuilder() {
        val directory = BlockDirectory.parse(ByteArrayInputStream(BlockBuilder.build(reader(Reports.blocks), 20261114)))
        assertEquals("CAF? TELECOM", directory.lookup("3152020000")!!.company)
    }

    @Test(expected = IOException::class)
    fun aBlockReportMissingAColumnIsRejected() {
        BlockBuilder.build(reader("NPA,NXX,X\n315,200,0"), 20261114)
    }
}
