package com.starception.submission.shared.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import com.starception.submission.core.designsystem.theme.ubuntuFontFamily
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A restrained editorial scale for the artwork-led home, independent of dashboard labels. */
internal object HomeTypography {
    private val base: TextStyle
        @Composable get() = TextStyle(
        fontFamily = ubuntuFontFamily(),
    )
    val greeting: TextStyle
        @Composable get() = base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-.25).sp)
    val hero: TextStyle
        @Composable get() = base.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, letterSpacing = (-.5).sp)
    val eyebrow: TextStyle
        @Composable get() = base.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = .9.sp)
    val section: TextStyle
        @Composable get() = base.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = (-.2).sp)
    val reflection: TextStyle
        @Composable get() = base.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal, letterSpacing = (-.2).sp)
    val cardTitle: TextStyle
        @Composable get() = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val body: TextStyle
        @Composable get() = base.copy(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val caption: TextStyle
        @Composable get() = base.copy(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
}

/** Keep schedule, location and banner copy in the same family as the cover. */
@Composable
internal fun homeTypography(base: Typography): Typography {
    val family = ubuntuFontFamily()
    fun TextStyle.home() = copy(fontFamily = family, letterSpacing = 0.sp)
    return base.copy(
        displayLarge = base.displayLarge.home(),
        displayMedium = base.displayMedium.home(),
        displaySmall = base.displaySmall.home(),
        headlineLarge = base.headlineLarge.home(),
        headlineMedium = base.headlineMedium.home(),
        headlineSmall = base.headlineSmall.home(),
        titleLarge = base.titleLarge.home(),
        titleMedium = base.titleMedium.home(),
        titleSmall = base.titleSmall.home(),
        bodyLarge = base.bodyLarge.home(),
        bodyMedium = base.bodyMedium.home(),
        bodySmall = base.bodySmall.home(),
        labelLarge = base.labelLarge.home(),
        labelMedium = base.labelMedium.home(),
        labelSmall = base.labelSmall.home(),
    )
}
