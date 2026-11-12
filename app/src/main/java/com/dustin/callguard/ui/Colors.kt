package com.dustin.callguard.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// callGuard's own colors, built from the app icon: forest and mint greens with a violet accent.

internal val CallGuardLightColors = lightColorScheme(
    primary = Color(0xFF1A7A4C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC3EDD6),
    onPrimaryContainer = Color(0xFF00391F),
    secondary = Color(0xFF7432C2),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9DDFB),
    onSecondaryContainer = Color(0xFF2B0A5E),
    tertiary = Color(0xFF0F4A38),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFE8DC),
    onTertiaryContainer = Color(0xFF002115),
    background = Color(0xFFF6FBF7),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FBF7),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFC0C9C2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F6F1),
    surfaceContainer = Color(0xFFEAF0EB),
    surfaceContainerHigh = Color(0xFFE4EAE5),
    surfaceContainerHighest = Color(0xFFDFE5E0),
)

internal val CallGuardDarkColors = darkColorScheme(
    primary = Color(0xFF6FDBA0),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0F4A38),
    onPrimaryContainer = Color(0xFFC3EDD6),
    secondary = Color(0xFFD1B8F7),
    onSecondary = Color(0xFF3F1782),
    secondaryContainer = Color(0xFF55289B),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFF93D6B8),
    onTertiary = Color(0xFF00382A),
    tertiaryContainer = Color(0xFF1B5C45),
    onTertiaryContainer = Color(0xFFCFE8DC),
    background = Color(0xFF0E1511),
    onBackground = Color(0xFFDDE5DE),
    surface = Color(0xFF0E1511),
    onSurface = Color(0xFFDDE5DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C2),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFF09100C),
    surfaceContainerLow = Color(0xFF161D19),
    surfaceContainer = Color(0xFF1A211D),
    surfaceContainerHigh = Color(0xFF252C27),
    surfaceContainerHighest = Color(0xFF303731),
)
