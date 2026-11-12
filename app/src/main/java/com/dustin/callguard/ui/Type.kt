package com.dustin.callguard.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.dustin.callguard.R

// jetbrains mono (the android studio terminal font), licensed under the sil open font license
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val base = Typography()

// to limit the font to a few styles later, delete the lines you don't want here
internal val CallGuardTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = JetBrainsMono),
    displayMedium = base.displayMedium.copy(fontFamily = JetBrainsMono),
    displaySmall = base.displaySmall.copy(fontFamily = JetBrainsMono),
    headlineLarge = base.headlineLarge.copy(fontFamily = JetBrainsMono),
    headlineMedium = base.headlineMedium.copy(fontFamily = JetBrainsMono),
    headlineSmall = base.headlineSmall.copy(fontFamily = JetBrainsMono),
    titleLarge = base.titleLarge.copy(fontFamily = JetBrainsMono),
    titleMedium = base.titleMedium.copy(fontFamily = JetBrainsMono),
    titleSmall = base.titleSmall.copy(fontFamily = JetBrainsMono),
    bodyLarge = base.bodyLarge.copy(fontFamily = JetBrainsMono),
    bodyMedium = base.bodyMedium.copy(fontFamily = JetBrainsMono),
    bodySmall = base.bodySmall.copy(fontFamily = JetBrainsMono),
    labelLarge = base.labelLarge.copy(fontFamily = JetBrainsMono),
    labelMedium = base.labelMedium.copy(fontFamily = JetBrainsMono),
    labelSmall = base.labelSmall.copy(fontFamily = JetBrainsMono),
)
