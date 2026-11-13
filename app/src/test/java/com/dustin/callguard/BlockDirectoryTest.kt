package com.dustin.callguard

import com.dustin.callguard.screening.BlockDirectory
import com.dustin.callguard.screening.BlockHolderStatus
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockDirectoryTest {
    private val directory = BlockDirectory.parse(ByteArrayInputStream(BlockTestData.sample))

    @Test
    fun readsTheHeader() {
        assertEquals(20261113, directory.asOf)
        assertEquals(2, directory.codeCount)
    }

    @Test
    fun anAssignedBlockNamesItsCarrierAndMonth() {
        val holder = directory.lookup("3152005551")!!
        assertEquals(BlockHolderStatus.ASSIGNED, holder.status)
        assertEquals("BOOST SUBSCRIBERCO L.L.C.", holder.company)
        assertEquals("490J", holder.ocn)
        assertEquals("2014-09", holder.assigned)
    }

    @Test
    fun aSingleEntryExchangeAnswersForEveryBlockDigit() {
        for (digit in 0..9) {
            assertEquals(BlockHolderStatus.ASSIGNED, directory.lookup("315200${digit}000")!!.status)
        }
    }

    @Test
    fun aRetainedBlockNamesTheExchangeHolder() {
        val holder = directory.lookup("3152010000")!!
        assertEquals(BlockHolderStatus.RETAINED, holder.status)
        assertEquals("CELLCO PARTNERSHIP DBA VERIZON WIRELESS", holder.company)
        assertNull(holder.assigned)
    }

    @Test
    fun availableBlocksHaveNoCarrier() {
        val free = directory.lookup("3152013000")!!
        assertEquals(BlockHolderStatus.AVAILABLE, free.status)
        assertNull(free.company)
        assertEquals(BlockHolderStatus.AVAILABLE_IN_USE, directory.lookup("3152014000")!!.status)
    }

    @Test
    fun missingDataComesBackAsNull() {
        assertNull(directory.lookup("3152019000")) // block with no data
        assertNull(directory.lookup("3153009999")) // exchange not in the file
        assertNull(directory.lookup("315201"))
        assertNull(directory.lookup(null))
        assertNull(directory.lookup("31520A3000"))
    }

    @Test(expected = IOException::class)
    fun aFileWithTheWrongMagicIsRejected() {
        BlockDirectory.parse(ByteArrayInputStream(BlockTestData.sample.copyOf().also { it[0] = 'X'.code.toByte() }))
    }
}
