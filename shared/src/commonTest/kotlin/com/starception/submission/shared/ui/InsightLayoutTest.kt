/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.shared.ui

import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.component.resolveHomePrayerRows
import com.starception.submission.core.designsystem.component.insightStageHeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InsightLayoutTest {
    @Test
    fun shortPrayerContentKeepsExpandedStage() {
        val extent = preferredInsightExpansionExtent(0, 240.dp, 600.dp, 200.dp)
        assertEquals(88f / 360f, extent, 0.001f)
    }

    @Test
    fun expandedHeightFollowsMeasuredContentWithoutEnteringImmersiveMode() {
        val short = preferredInsightExpansionExtent(1, 240.dp, 700.dp, 400.dp)
        val long = preferredInsightExpansionExtent(1, 240.dp, 700.dp, 600.dp)
        assertTrue(long > short)
        assertEquals(0.98f, preferredInsightExpansionExtent(2, 240.dp, 500.dp, 800.dp))
        assertEquals(0f, preferredInsightExpansionExtent(0, 240.dp, 240.dp, null))
    }

    @Test
    fun scheduleAddsRowsAndUsesRemainingHeight() {
        for ((height, expectedRows) in listOf(200f to 1, 280f to 2, 400f to 3)) {
            val layout = homePrayerScheduleLayout(height, false, 6)
            assertEquals(expectedRows, layout.rows)
            assertEquals(height, 92f + layout.rows * (layout.rowHeight + 8f), 0.001f)
        }
    }

    @Test
    fun shortViewportKeepsPrayerRowsReadableForScrolling() {
        val layout = homePrayerScheduleLayout(100f, true, 6)
        assertEquals(3, layout.rows)
        assertEquals(52f, layout.rowHeight)
    }

    @Test
    fun showAllFitsEveryPrayerIntoRemainingHeight() {
        val layout = homePrayerScheduleLayout(280f, true, 6)
        assertEquals(3, layout.rows)
        assertEquals(280f, 92f + layout.rows * (layout.rowHeight + 8f), 0.001f)
    }
    @Test
    fun measuredSpaceRevealsWholePrayerRowsDuringResize() {
        for ((height, rows) in listOf(96f to 1, 192f to 2, 288f to 3)) {
            val result = resolveHomePrayerRows(height, 6, false, 96f, 88f, 0f, 0f)
            assertEquals(rows, result.rows)
            assertEquals(height, result.rows * result.rowHeight, 0.001f)
            assertTrue(!result.needsScrolling)
        }
    }

    @Test
    fun accessibilityTextReducesRowsInsteadOfClippingContent() {
        val regular = resolveHomePrayerRows(288f, 6, false, 96f, 88f, 0f, 0f)
        val largeText = resolveHomePrayerRows(288f, 6, false, 144f, 132f, 0f, 0f)
        assertEquals(3, regular.rows)
        assertEquals(2, largeText.rows)
        assertTrue(largeText.rowHeight >= 132f)
        assertTrue(!largeText.needsScrolling)
    }

    @Test
    fun tinyViewportRetainsReadableRowsWithScrollFallback() {
        val result = resolveHomePrayerRows(40f, 6, false, 96f, 88f, 0f, 0f)
        assertEquals(1, result.rows)
        assertEquals(88f, result.rowHeight)
        assertTrue(result.needsScrolling)
    }

    @Test
    fun shrinkingInsightRetainsReadableStageAndReachableFooter() {
        val footerHeight = 104
        val gap = 14
        val minimumStage = 240
        for (viewport in listOf(600, 400, 260, 180, 80, 0)) {
            val stage = insightStageHeight(viewport, footerHeight, gap, minimumStage)
            assertTrue(stage >= minimumStage)
            val scrollableHeight = stage + gap + footerHeight
            assertTrue(scrollableHeight >= viewport)
            // When it fits, use all the available height without unnecessary scrolling.
            if (viewport >= minimumStage + gap + footerHeight) {
                assertEquals(viewport, scrollableHeight)
            }
        }
    }

    @Test
    fun wrappingMetadataCannotSqueezeReadingStageToZero() {
        val stage = insightStageHeight(280, 380, 8, 240)
        assertEquals(240, stage)
        assertEquals(628, stage + 380 + 8)
    }

    @Test
    fun largeTextAndCompassRetainTheirMinimumReadableHeight() {
        assertEquals(480, insightStageHeight(300, 160, 14, 480))
        assertEquals(320, insightStageHeight(200, 0, 0, 320))
    }
}
