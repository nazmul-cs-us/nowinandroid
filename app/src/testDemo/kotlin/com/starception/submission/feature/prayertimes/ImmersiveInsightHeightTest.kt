package com.starception.submission.feature.prayertimes

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveInsightHeightTest {
    @Test fun heldBannerReducesAvailableTileHeight() {
        assertEquals(
            1530f,
            availableImmersiveInsightHeightPx(
                viewportBottomInWindowPx = 1670f,
                windowBottomInWindowPx = 1600f,
                tileTopInWindowPx = 70f,
                scrollOffsetPx = 0,
            ),
        )
    }

    @Test fun userScrollDoesNotGrowExpandedTile() {
        assertEquals(
            1530f,
            availableImmersiveInsightHeightPx(
                viewportBottomInWindowPx = 1670f,
                windowBottomInWindowPx = 1600f,
                tileTopInWindowPx = -30f,
                scrollOffsetPx = 100,
            ),
        )
    }

    @Test fun navigationStaysVisibleUntilReaderIsScrolled() {
        assertFalse(shouldHideHomeNavigationForInsight(true, false, 100, 24f))
        assertFalse(shouldHideHomeNavigationForInsight(true, true, 0, 24f))
        assertTrue(shouldHideHomeNavigationForInsight(true, true, 25, 24f))
        assertFalse(shouldHideHomeNavigationForInsight(false, true, 100, 24f))
    }

    @Test fun longPassagesStayInsideTheirReaderViewport() {
        assertEquals(180.dp, expandedReaderMinimumStageHeight(true, 900.dp, 180.dp))
        assertEquals(900.dp, expandedReaderMinimumStageHeight(false, 900.dp, 180.dp))
    }

    @Test fun parallaxCannotExposeTheTileSurface() {
        assertEquals(5f, boundedArtworkParallax(-8f, 1000f, 1.012f, 1f) * -1f, .01f)
        assertEquals(0f, boundedArtworkParallax(8f, 1000f, 1f, 1f), .01f)
    }
}
