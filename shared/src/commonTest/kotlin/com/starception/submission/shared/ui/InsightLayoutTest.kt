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
}
