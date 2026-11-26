package com.starception.submission.shared.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeWindowLayoutTest {
    @Test fun ipadUsesCurrentHomeInBothOrientations() {
        assertTrue(useArtworkHomeLayout(1032f, 1376f))
        assertTrue(useArtworkHomeLayout(1376f, 1032f))
    }
    @Test fun designedForIpadMacAndSplitWindowsUseCurrentHome() {
        assertTrue(useArtworkHomeLayout(1440f, 900f))
        assertTrue(useArtworkHomeLayout(600f, 700f))
        assertTrue(useArtworkHomeLayout(400f, 900f))
    }
    @Test fun portraitPhoneAndShortLandscapePhoneKeepTheirExistingDesigns() {
        assertTrue(useArtworkHomeLayout(390f, 844f))
        assertFalse(useArtworkHomeLayout(844f, 390f))
    }
}
