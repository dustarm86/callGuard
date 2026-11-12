package com.dustin.callguard

import com.dustin.callguard.data.AllowedNumber
import com.dustin.callguard.data.AllowedPrefix
import com.dustin.callguard.screening.AllowlistMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowlistMatcherTest {
    private val now = 1_000_000L

    private fun number(n: String, expiresAt: Long? = null) = AllowedNumber(n, "test", 0L, expiresAt)

    @Test
    fun exactNumberMatches() {
        assertTrue(AllowlistMatcher.matches("3155550142", listOf(number("3155550142")), emptyList(), now))
    }

    @Test
    fun hiddenNumberNeverMatches() {
        assertFalse(AllowlistMatcher.matches(null, listOf(number("3155550142")), listOf(AllowedPrefix("315555", "x", 0L)), now))
    }

    @Test
    fun expiredEntryDoesNotMatch() {
        assertFalse(AllowlistMatcher.matches("3155550142", listOf(number("3155550142", now - 1)), emptyList(), now))
    }

    @Test
    fun futureExpiryStillMatches() {
        assertTrue(AllowlistMatcher.matches("3155550142", listOf(number("3155550142", now + 1)), emptyList(), now))
    }

    @Test
    fun prefixMatchesNumberBlock() {
        assertTrue(AllowlistMatcher.matches("3155550199", emptyList(), listOf(AllowedPrefix("3155550", "office", 0L)), now))
    }

    @Test
    fun differentNumberAndPrefixDoNotMatch() {
        assertFalse(AllowlistMatcher.matches("2125550103", listOf(number("3155550142")), listOf(AllowedPrefix("315555", "x", 0L)), now))
    }
}
