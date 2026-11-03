package com.starception.submission.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSystemBarAppearanceTest {
    @Test fun paleStatusBannerOverridesTheDarkArtworkAndSearchBelowIt() {
        assertTrue(useDarkStatusBarIcons(false, true, true, false, false, homeBannerLightIcons = false))
        assertTrue(useDarkStatusBarIcons(true, true, true, true, false, homeBannerLightIcons = false))
    }

    @Test fun dismissedBannerReturnsToArtworkAndDoesNotLeakIntoOtherTabs() {
        assertFalse(useDarkStatusBarIcons(false, true, true, false, false, homeBannerLightIcons = null))
        assertFalse(useDarkStatusBarIcons(true, false, true, false, false, homeBannerLightIcons = false))
    }

    @Test fun initialHomeFrameDoesNotFlashBlackIconsOnNightArtwork() {
        assertFalse(useDarkStatusBarIcons(false, true, null, false, false))
    }

    @Test fun homeUsesArtworkContrastInBothThemes() {
        assertFalse(useDarkStatusBarIcons(false, true, true, false, false))
        assertTrue(useDarkStatusBarIcons(true, true, false, false, false))
    }

    @Test fun searchOverlayUsesItsOwnThemeInsteadOfTheUnderlyingPhotograph() {
        assertTrue(useDarkStatusBarIcons(false, true, true, true, false))
        assertFalse(useDarkStatusBarIcons(true, true, false, true, false))
    }

    @Test fun leavingHomeOrRotatingToLandscapeReturnsToThemeContrast() {
        assertTrue(useDarkStatusBarIcons(false, false, true, false, false))
        assertFalse(useDarkStatusBarIcons(true, false, false, false, false))
    }
}
