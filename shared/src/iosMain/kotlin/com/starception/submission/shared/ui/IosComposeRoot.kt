/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.shared.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.ComposeUIViewController
import com.starception.submission.core.designsystem.theme.DarkCoastalColorScheme
import com.starception.submission.core.designsystem.theme.LightCoastalColorScheme
import com.starception.submission.core.designsystem.theme.sharedTypography
import com.starception.submission.shared.PrayerSchedule
import platform.UIKit.UIViewController

/**
 * Bridges the shared Compose UI into UIKit so `iosApp/` can present it.
 *
 * This is the whole iOS UI boundary: Swift owns the app lifecycle and hands the
 * screen to Compose. Everything below this line is shared with Android.
 *
 * Parameters are primitives rather than Kotlin types so the Swift call site stays
 * free of Kotlin date and settings classes.
 */
@Suppress("FunctionName", "LongParameterList")
fun PrayerTimesViewController(
    year: Int,
    month: Int,
    day: Int,
    latitude: Double,
    longitude: Double,
    timeZoneOffset: Double,
    placeName: String,
    fajrAngle: Double,
    ishaAngle: Double,
    asrShadowFactor: Int,
): UIViewController = ComposeUIViewController {
    val day = PrayerSchedule.forDate(
        year = year,
        month = month,
        day = day,
        latitude = latitude,
        longitude = longitude,
        timeZoneOffset = timeZoneOffset,
        fajrAngle = fajrAngle,
        ishaAngle = ishaAngle,
        asrShadowFactor = asrShadowFactor,
    )

    // The app's real Coastal palette from :core:theme, not stock Material. These
    // are the same scheme objects NiaTheme uses on Android, so the two platforms
    // cannot drift apart on colour.
    //
    // Dynamic colour is deliberately absent: it is Material You, an Android-only
    // feature with no iOS equivalent, and it stays behind in core:designsystem.
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkCoastalColorScheme else LightCoastalColorScheme,
        typography = sharedTypography(),
    ) {
        PrayerTimesScreen(placeName = placeName, day = day)
    }
}
