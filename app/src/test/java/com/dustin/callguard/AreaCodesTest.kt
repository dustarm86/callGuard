package com.dustin.callguard

import com.dustin.callguard.screening.AreaCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaCodesTest {

    @Test
    fun looksUpKnownAreaCodes() {
        assertEquals("Manhattan, NY", AreaCodes.lookup("2125550123"))
        assertEquals("north-central NY (Syracuse, Utica, Watertown)", AreaCodes.lookup("3159488351"))
    }

    @Test
    fun overlayCodesShareTheirRegion() {
        assertEquals(AreaCodes.lookup("3155550123"), AreaCodes.lookup("6805550123"))
    }

    @Test
    fun recognizesTollFree() {
        assertEquals("toll-free", AreaCodes.lookup("8005550123"))
        assertTrue(AreaCodes.isTollFree("8335550123"))
        assertFalse(AreaCodes.isTollFree("3155550123"))
    }

    @Test
    fun returnsNullForUnknownOrShortNumbers() {
        assertNull(AreaCodes.lookup("0005550123"))
        assertNull(AreaCodes.lookup("21391"))
        assertNull(AreaCodes.lookup(null))
        assertNull(AreaCodes.areaCodeOf("21391"))
    }
}
