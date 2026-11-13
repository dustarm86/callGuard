package com.dustin.callguard

import com.dustin.callguard.data.ProbeHistory
import org.junit.Assert.assertEquals
import org.junit.Test

class ProbeHistoryTest {
    @Test
    fun newestComesFirst() {
        assertEquals(listOf("3", "2", "1"), ProbeHistory.add(ProbeHistory.add(ProbeHistory.add(emptyList(), "1"), "2"), "3"))
    }

    @Test
    fun probingANumberAgainMovesItToTheFrontWithoutDuplicating() {
        assertEquals(listOf("1", "3", "2"), ProbeHistory.add(listOf("3", "2", "1"), "1"))
    }

    @Test
    fun theListStopsAtTheLimitAndDropsTheOldest() {
        var history = emptyList<String>()
        for (n in 1..(ProbeHistory.MAX + 5)) history = ProbeHistory.add(history, n.toString())
        assertEquals(ProbeHistory.MAX, history.size)
        assertEquals((ProbeHistory.MAX + 5).toString(), history.first())
        assertEquals("6", history.last())
    }

    @Test
    fun savingAndReadingBackGivesTheSameList() {
        val history = listOf("3158488515", "3157751156")
        assertEquals(history, ProbeHistory.decode(ProbeHistory.encode(history)))
    }

    @Test
    fun readingSkipsBlanksAndGarbage() {
        assertEquals(emptyList<String>(), ProbeHistory.decode(""))
        assertEquals(listOf("3158488515", "3157751156"), ProbeHistory.decode("3158488515,,abc,31x,3157751156"))
    }
}
