package com.starception.submission.feature.prayertimes

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

    @Test fun scrollingFromTheReflectionToLightFeedChangesIconContrast() {
        assertTrue(icons(coverLightText = false, darkTheme = false, scroll = 600f))
        assertFalse(icons(coverLightText = true, darkTheme = false, scroll = 1100f))
    }

    @Test fun scrolledDarkFeedAndImmersiveReaderKeepWhiteIcons() {
        assertTrue(icons(coverLightText = false, darkTheme = true, scroll = 1100f))
        assertTrue(icons(coverLightText = false, darkTheme = false, reader = true))
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
        isDarkTheme = darkTheme,
        feedBackground = if (darkTheme) Color(0xFF080B14) else Color(0xFFF7F8F0),
        readerExpanded = reader,
    )
}
