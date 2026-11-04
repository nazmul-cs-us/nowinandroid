package com.starception.submission.feature.prayertimes

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ImmersiveInsightControlInsetTest {
    @Test fun bannerAlreadyProvidesTopClearance() {
        assertEquals(12.dp, calculateImmersiveInsightControlInset(24.dp, 40.dp))
    }

    @Test fun edgeToEdgeArtworkKeepsControlsBelowSystemArea() {
        assertEquals(36.dp, calculateImmersiveInsightControlInset(24.dp, 0.dp))
        assertEquals(48.dp, calculateImmersiveInsightControlInset(36.dp, 0.dp))
    }

    @Test fun scrollingTheWholeTilePastTheTopPinsControlsBelowStatusBar() {
        val safeInset = calculateImmersiveInsightControlInset(24.dp, (-120).dp)
        assertEquals(156.dp, safeInset)
        assertEquals(156.dp, calculateFocusedInsightControlTopInset(48.dp, safeInset))
    }

    @Test fun focusedActionFollowsHeaderUntilItReachesSafeArea() {
        val safeInset = calculateImmersiveInsightControlInset(24.dp, (-40).dp)
        assertEquals(100.dp, calculateFocusedInsightControlTopInset(100.dp, safeInset))
        assertEquals(76.dp, calculateFocusedInsightControlTopInset(20.dp, safeInset))
    }
}
