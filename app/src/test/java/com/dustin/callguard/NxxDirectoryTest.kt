package com.dustin.callguard

import com.dustin.callguard.screening.BlockStatus
import com.dustin.callguard.screening.NxxDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class NxxDirectoryTest {
    private val directory = NxxDirectory.parse(ByteArrayInputStream(NxxTestData.sample))

    @Test
    fun readsAssignedBlockDetails() {
        val info = directory.lookup("3152001234")
        assertEquals(BlockStatus.ASSIGNED, info.status)
        assertEquals("CELLCO PARTNERSHIP DBA VERIZON WIRELESS", info.company)
        assertEquals("6563", info.ocn)
        assertEquals("SYRACUSE", info.rateCenter)
        assertEquals("NY", info.state)
        assertEquals(true, info.pooled)
        assertEquals("2014-09", info.assigned)
        assertEquals(20261112, directory.asOf)
    }

    @Test
    fun unassignedOnlyInsideCoveredAreaCodes() {
        assertEquals(BlockStatus.UNASSIGNED, directory.lookup("3159991234").status)
        // 416 is not in the file at all, so it must stay unknown rather than look fake
        assertEquals(BlockStatus.UNKNOWN, directory.lookup("4169991234").status)
    }

    @Test
    fun flagsNotInServiceAndSpecialUseBlocks() {
        assertEquals(BlockStatus.NOT_IN_SERVICE, directory.lookup("3152021234").status)
        assertEquals(BlockStatus.UNAVAILABLE, directory.lookup("3155551234").status)
    }

    @Test
    fun unusableInputIsUnknown() {
        assertEquals(BlockStatus.UNKNOWN, directory.lookup(null).status)
        assertEquals(BlockStatus.UNKNOWN, directory.lookup("21391").status)
        assertNull(directory.lookup("315200123").company)
    }

    @Test
    fun rejectsFilesWithTheWrongHeader() {
        assertThrows(IOException::class.java) {
            NxxDirectory.parse(ByteArrayInputStream("NOPE0000".toByteArray()))
        }
    }
}
