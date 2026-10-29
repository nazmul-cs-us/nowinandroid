package com.starception.submission.feature.prayertimes

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
}
