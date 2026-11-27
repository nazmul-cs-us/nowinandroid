package com.starception.submission.shared.ui

import kotlin.test.*

class HomeAndroidParityTest {
    @Test fun adjacentCoverCopyStaysVisibleDuringTheSwipeCrossfade() {
        assertEquals(1f, homeCoverCopyOpacity(0f))
        assertEquals(.5f, homeCoverCopyOpacity(.5f))
        assertEquals(0f, homeCoverCopyOpacity(1f))
    }
    @Test fun reflectionsSwipeInBothDirectionsAndRespectRtl() {
        assertEquals(1, homeReflectionSwipeTarget(0, -40f, 32f, false))
        assertEquals(4, homeReflectionSwipeTarget(0, 40f, 32f, false))
        assertEquals(4, homeReflectionSwipeTarget(0, -40f, 32f, true))
        assertNull(homeReflectionSwipeTarget(0, 5f, 32f, false))
    }
    @Test fun measuredReflectionGetsSpaceBeforeDecorativeArtwork() {
        val height = homeRestingCoverHeight(700f, 640f, 180f, 24f)
        assertTrue(height + 180f + 24f <= 700f)
        assertTrue(homeRestingCoverHeight(700f, 640f, 300f, 24f) < height)
    }
    @Test fun furtherReadingUsesRealQuranHadithAndDuaDestinations() {
        assertEquals(HomeReadingKind.Quran, homeReadingKind(DeenlyKnowledgeTarget.Surah(1)))
        assertEquals(HomeReadingKind.Hadith, homeReadingKind(DeenlyKnowledgeTarget.BukhariHadith(1)))
        assertEquals(HomeReadingKind.Hadith, homeReadingKind(DeenlyKnowledgeTarget.ShamayelBook(1)))
        assertEquals(HomeReadingKind.Dua, homeReadingKind(DeenlyKnowledgeTarget.FortressDua(1)))
    }
}
