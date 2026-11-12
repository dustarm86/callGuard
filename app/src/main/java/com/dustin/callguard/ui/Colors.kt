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
    tertiary = Color(0xFF0B6C8E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC4EBFF),
    onTertiaryContainer = Color(0xFF001F2B),
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
    // palette taken from android studio's terminal: near-black base, bright green, soft purple, sky blue
    primary = Color(0xFF92FC71),
    onPrimary = Color(0xFF0C2A05),
    primaryContainer = Color(0xFF21401B),
    onPrimaryContainer = Color(0xFF92FC71),
    secondary = Color(0xFFA989F8),
    onSecondary = Color(0xFF1F0A52),
    secondaryContainer = Color(0xFF38295F),
    onSecondaryContainer = Color(0xFFDDD0FF),
    tertiary = Color(0xFF80D4FB),
    onTertiary = Color(0xFF00293A),
    tertiaryContainer = Color(0xFF1D3F52),
    onTertiaryContainer = Color(0xFFC4EBFF),
    background = Color(0xFF191A1C),
    onBackground = Color(0xFFD1D3D9),
    surface = Color(0xFF191A1C),
    onSurface = Color(0xFFD1D3D9),
    surfaceVariant = Color(0xFF2C2E33),
    onSurfaceVariant = Color(0xFFBCBEC3),
    outline = Color(0xFF787A80),
    outlineVariant = Color(0xFF3A3B3F),
    surfaceContainerLowest = Color(0xFF141517),
    surfaceContainerLow = Color(0xFF202124),
    surfaceContainer = Color(0xFF26282C),
    surfaceContainerHigh = Color(0xFF2C2E33),
    surfaceContainerHighest = Color(0xFF33353A),
)
