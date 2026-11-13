package com.dustin.callguard

import com.dustin.callguard.data.ScreenerSettings
import com.dustin.callguard.ui.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeChoiceTest {
    @Test
    fun alwaysDarkStaysDarkInLightSystem() {
        assertEquals(true, ThemeChoice.useDark(alwaysDark = true, systemIsDark = false))
    }

    @Test
    fun followsSystemWhenAlwaysDarkIsOff() {
        assertEquals(false, ThemeChoice.useDark(alwaysDark = false, systemIsDark = false))
        assertEquals(true, ThemeChoice.useDark(alwaysDark = false, systemIsDark = true))
    }

    @Test
    fun darkModeIsOnByDefault() {
        assertEquals(true, ScreenerSettings().alwaysDark)
    }
}
