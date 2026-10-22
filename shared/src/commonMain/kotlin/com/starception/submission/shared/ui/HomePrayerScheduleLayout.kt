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
    fontScale: Float = 1f,
): HomePrayerScheduleLayout {
    val layout = com.starception.submission.core.designsystem.component.resolveHomePrayerRows(
        availableHeight = availableHeight,
        prayerCount = prayerCount,
        showAll = showAllPrayers,
        minimumRowHeight = 78f * fontScale.coerceAtLeast(1f),
        readableRowHeight = 52f * fontScale.coerceAtLeast(1f),
        sectionChromeHeight = 92f * fontScale.coerceAtLeast(1f),
    )
    return HomePrayerScheduleLayout(layout.rows, layout.rowHeight)
}
