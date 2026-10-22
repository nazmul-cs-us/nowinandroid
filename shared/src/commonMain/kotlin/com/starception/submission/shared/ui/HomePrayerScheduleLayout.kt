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

internal data class HomePrayerScheduleLayout(val rows: Int, val rowHeight: Float)

/** Allocate the height left by the insight and location cards to prayer rows. */
internal fun homePrayerScheduleLayout(
    availableHeight: Float,
    showAllPrayers: Boolean,
    prayerCount: Int,
): HomePrayerScheduleLayout {
    val totalRows = ((prayerCount + 1) / 2).coerceAtLeast(1)
    // Header (44), control (32), and their fixed section gaps (16).
    val rowBudget = (availableHeight - 92f).coerceAtLeast(0f)
    val rows = if (showAllPrayers) {
        totalRows
    } else {
        (rowBudget / (78f + 8f)).toInt().coerceIn(1, totalRows)
    }
    return HomePrayerScheduleLayout(
        rows = rows,
        rowHeight = (rowBudget / rows - 8f).coerceAtLeast(52f),
    )
}
