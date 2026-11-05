package com.starception.submission.feature.prayertimes

import com.starception.submission.core.designsystem.theme.DarkChiliSpiceColorScheme
import com.starception.submission.core.designsystem.theme.DarkChocolateTruffleColorScheme
import com.starception.submission.core.designsystem.theme.DarkDamsonCuracaoColorScheme
import com.starception.submission.core.designsystem.theme.DarkPurpleGoldColorScheme
import com.starception.submission.core.designsystem.theme.LightChiliSpiceColorScheme
import com.starception.submission.core.designsystem.theme.LightChocolateTruffleColorScheme
import com.starception.submission.core.designsystem.theme.LightDamsonCuracaoColorScheme
import com.starception.submission.core.designsystem.theme.LightPurpleGoldColorScheme
import com.starception.submission.core.designsystem.theme.homeFeedPalette
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSurfaceContrastTest {
    @Test fun paleWeatherBannerUsesDarkIconsAndDismissalReturnsControlToArtwork() {
        val paleYellow = Color(0xFFFFF6B0)
        assertEquals(false, homeBannerUsesLightIcons(.5f, paleYellow))
        assertNull(homeBannerUsesLightIcons(0f, paleYellow))
        assertEquals(true, homeBannerUsesLightIcons(.5f, Color(0xFF303023)))
    }

    @Test fun nightArtworkUsesWhiteIconsEvenInLightAppTheme() {
        assertTrue(icons(coverLightText = true, darkTheme = false))
    }

    @Test fun brightReadingArtworkUsesDarkIconsEvenInDarkAppTheme() {
        assertFalse(icons(coverLightText = false, darkTheme = true))
    }

    @Test fun reflectionAndLightFeedUseTheSameIconContrast() {
        assertFalse(icons(coverLightText = false, darkTheme = false, scroll = 600f))
        assertFalse(icons(coverLightText = true, darkTheme = false, scroll = 1100f))
    }

    @Test fun scrolledDarkFeedAndImmersiveReaderKeepWhiteIcons() {
        assertTrue(icons(coverLightText = false, darkTheme = true, scroll = 1100f))
        assertTrue(icons(coverLightText = false, darkTheme = false, reader = true))
    }

    @Test fun darkGradientFeedKeepsWhiteIconsInLightAppTheme() {
        assertTrue(homeStatusBarUsesLightIcons(
            coverUsesLightText = true,
            scrollOffset = 1100f,
            coverHeight = 550f,
            feedBackground = Color(0xFF080B14),
            readerExpanded = false,
        ))
    }

    @Test fun everyCuratedHomeFeedKeepsWhiteTextAndStatusIconsLegible() {
        val schemes = listOf(
            LightPurpleGoldColorScheme, DarkPurpleGoldColorScheme,
            LightChocolateTruffleColorScheme, DarkChocolateTruffleColorScheme,
            LightDamsonCuracaoColorScheme, DarkDamsonCuracaoColorScheme,
            LightChiliSpiceColorScheme, DarkChiliSpiceColorScheme,
            // Also protect legacy/custom selections with very pale accents.
            lightColorScheme(primary = Color.White, secondary = Color.White),
        )
        val palettes = schemes.map(::homeFeedPalette)
        assertEquals("Each curated selection must change the Home feed", 8,
            palettes.take(8).map { it.background }.distinct().size)
        palettes.forEach { palette ->
            val strongestGlow = palette.secondaryGlow.copy(alpha = .34f).compositeOver(
                palette.primaryGlow.copy(alpha = .48f).compositeOver(palette.background))
            listOf(palette.background, palette.primarySurface, palette.secondarySurface, strongestGlow)
                .forEach { background ->
                    val whiteTextContrast = 1.05f / (background.luminance() + .05f)
                    assertTrue("Home white text contrast must be at least 4.5:1", whiteTextContrast >= 4.5f)
                }
            assertTrue(homeStatusBarUsesLightIcons(false, 1100f, 550f, palette.background, false))
        }
    }

    private fun icons(
        coverLightText: Boolean,
        darkTheme: Boolean,
        scroll: Float = 0f,
        reader: Boolean = false,
    ) = homeStatusBarUsesLightIcons(
        coverUsesLightText = coverLightText,
        scrollOffset = scroll,
        coverHeight = 550f,
        feedBackground = if (darkTheme) Color(0xFF080B14) else Color(0xFFF7F8F0),
        readerExpanded = reader,
    )
}
