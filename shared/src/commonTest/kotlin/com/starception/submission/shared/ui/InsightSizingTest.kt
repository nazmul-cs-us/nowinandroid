package com.starception.submission.shared.ui

import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.component.contentSizedInsightExtent
import com.starception.submission.core.designsystem.component.insightMaximumTileHeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InsightSizingTest {
    @Test
    fun visibleNavigationAndHeaderAreNeverIncludedInTheTileBudget() {
        val fullViewport = 760.dp
        val visibleNavigation = 88.dp
        val normal = insightMaximumTileHeight(fullViewport - visibleNavigation, 44.dp)
        assertEquals(612.dp, normal)
        assertTrue(normal + 44.dp + 16.dp <= fullViewport - visibleNavigation)
        val immersive = insightMaximumTileHeight(fullViewport, 0.dp)
        assertEquals(744.dp, immersive)
        assertTrue(immersive > normal)
    }

    @Test
    fun shortContentShrinksAfterLongContentWithoutRetainingOldHeight() {
        val short = contentSizedInsightExtent(1, 240.dp, 800.dp, 360.dp)
        val long = contentSizedInsightExtent(1, 240.dp, 800.dp, 600.dp)
        assertTrue(short < long)
        assertEquals(360f, 240f + short * 560f, 0.01f)
        assertEquals(short, contentSizedInsightExtent(1, 240.dp, 800.dp, 360.dp))
    }

    @Test
    fun loadingDoesNotReserveTheMaximumReadingHeight() {
        assertTrue(contentSizedInsightExtent(2, 240.dp, 800.dp, null) <
            contentSizedInsightExtent(2, 240.dp, 800.dp, 1200.dp))
    }

    @Test
    fun smallWindowsKeepAutomaticExpansionOutOfImmersiveMode() {
        for (page in 0..4) {
            assertEquals(0.98f, contentSizedInsightExtent(page, 240.dp, 300.dp, 900.dp))
            assertEquals(0f, contentSizedInsightExtent(page, 240.dp, 240.dp, 900.dp))
        }
    }

    @Test
    fun conciseContentRetainsAnExplicitExpandedState() {
        assertEquals(88f / 560f, contentSizedInsightExtent(1, 240.dp, 800.dp, 100.dp), 0.001f)
    }
    @Test
    fun loadingUsesMeasuredStatusHeightWithoutReadingExpansionFloor() {
        for (page in listOf(1, 4)) {
            val extent = contentSizedInsightExtent(page, 240.dp, 800.dp, 270.dp, isLoading = true)
            assertEquals(270f, 240f + extent * 560f, .01f)
            assertTrue(extent > 0f)
            assertTrue(extent < contentSizedInsightExtent(page, 240.dp, 800.dp, 270.dp))
        }
    }

    @Test
    fun unmeasuredLoadingStaysSmallAndFinalPassageCanGrowNormally() {
        for (page in listOf(1, 4)) {
            val loading = contentSizedInsightExtent(page, 240.dp, 800.dp, null, isLoading = true)
            val passage = contentSizedInsightExtent(page, 240.dp, 800.dp, 600.dp)
            assertTrue(240f + loading * 560f < 260f)
            assertEquals(600f, 240f + passage * 560f, .01f)
            assertTrue(loading < passage)
        }
    }

}
