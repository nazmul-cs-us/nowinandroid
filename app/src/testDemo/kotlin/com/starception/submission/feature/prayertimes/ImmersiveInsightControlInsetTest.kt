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

    @Test fun focusedActionAboveStripNeverProducesNegativePadding() {
        assertEquals(12.dp, calculateFocusedInsightControlTopInset((-40).dp))
        assertEquals(12.dp, calculateFocusedInsightControlTopInset(0.dp))
    }

    @Test fun focusedActionBelowStripPreservesMeasuredOffset() {
        assertEquals(28.dp, calculateFocusedInsightControlTopInset(28.dp))
    }
}
