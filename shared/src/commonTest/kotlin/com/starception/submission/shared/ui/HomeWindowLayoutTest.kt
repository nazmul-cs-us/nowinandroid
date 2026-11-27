package com.starception.submission.shared.ui

import kotlin.test.*

class HomeWindowLayoutTest {
    @Test fun everyIphoneIpadAndMacWindowUsesCurrentHome() {
        for ((width, height) in listOf(390f to 844f, 844f to 390f, 1032f to 1376f,
            1376f to 1032f, 1440f to 550f, 400f to 900f)) {
            assertTrue(useArtworkHomeLayout(width, height))
        }
    }
    @Test fun ipadWidthsUseReadableColumnsInsteadOfStretchedPhoneCards() {
        assertEquals(3, homeWindowLayout(1376f).prayerColumns)
        assertEquals(2, homeWindowLayout(744f).prayerColumns)
        assertEquals(2, homeWindowLayout(390f).prayerColumns)
        assertEquals(1, homeWindowLayout(280f).prayerColumns)
    }
    @Test fun splitViewAndLargeFontsNeverOverflowTheWindow() {
        for (width in listOf(280f, 320f, 390f, 540f, 744f, 1032f, 1440f, 2400f)) {
            val layout = homeWindowLayout(width, 2f)
            assertTrue(layout.contentWidth + layout.gutter * 2 <= width)
            assertTrue(layout.headerWidth + layout.gutter * 2 <= width)
            assertTrue(layout.readerWidth <= width)
            assertTrue(layout.contentWidth <= 1100f)
            assertTrue(layout.readerWidth <= 960f)
        }
        assertEquals(1, homeWindowLayout(540f, 2f).prayerColumns)
    }
    @Test fun greetingMatchesAndroidSignedInAndSignedOutPresentation() {
        assertEquals("Sign In", homeAccountGreetingLabel(null, "Reader"))
        assertEquals("Amina", homeAccountGreetingLabel("amina@example.com", "Amina"))
        assertEquals("amina khan", homeAccountGreetingLabel("amina.khan@example.com", "Reader"))
    }
}
