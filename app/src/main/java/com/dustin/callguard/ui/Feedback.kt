package com.dustin.callguard.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * shows a short message at the bottom of whatever tab is open, so a button that acts somewhere the
 * person can't see (another tab, a background job) still confirms that it worked.
 */
val LocalNotify = staticCompositionLocalOf<(String) -> Unit> { {} }
