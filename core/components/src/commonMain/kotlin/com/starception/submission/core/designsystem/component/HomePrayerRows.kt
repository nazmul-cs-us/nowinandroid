/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.core.designsystem.component

import kotlin.math.ceil

data class HomePrayerRows(val rows: Int, val rowHeight: Float, val needsScrolling: Boolean)

/** Fit whole rows into the measured viewport, retaining readable cards on short screens. */
fun resolveHomePrayerRows(
    availableHeight: Float,
    prayerCount: Int,
    showAll: Boolean,
    minimumRowHeight: Float = 78f,
    readableRowHeight: Float = 52f,
    sectionChromeHeight: Float = 92f,
    rowSpacing: Float = 8f,
    maximumRowHeight: Float = 140f,
): HomePrayerRows {
    val totalRows = ((prayerCount + 1) / 2).coerceAtLeast(1)
    val budget = (availableHeight - sectionChromeHeight).coerceAtLeast(0f)
    val minimumHeight = maxOf(minimumRowHeight, readableRowHeight)
    val maximumHeight = maxOf(minimumHeight, maximumRowHeight)
    // Add a row before stretching existing cards beyond the comfortable height.
    // Preserve natural content height and allow scrolling if that row cannot fully fit.
    val rowCount = if (showAll) totalRows else
        ceil(budget / (maximumHeight + rowSpacing)).toInt().coerceIn(1, totalRows)
    val height = (budget / rowCount - rowSpacing)
        .coerceIn(minimumHeight, maximumHeight)
    return HomePrayerRows(
        rows = rowCount,
        rowHeight = height,
        needsScrolling = rowCount * (height + rowSpacing) > budget + 0.5f,
    )
}
