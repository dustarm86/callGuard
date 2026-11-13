package com.dustin.callguard.ui

/** pure helper so the dark mode rule can be unit tested. */
object ThemeChoice {
    fun useDark(alwaysDark: Boolean, systemIsDark: Boolean): Boolean = alwaysDark || systemIsDark
}
