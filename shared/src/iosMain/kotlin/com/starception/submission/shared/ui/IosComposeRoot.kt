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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.starception.submission.core.designsystem.theme.DarkCoastalColorScheme
import com.starception.submission.core.designsystem.theme.LightCoastalColorScheme
import com.starception.submission.core.designsystem.theme.sharedTypography
import com.starception.submission.shared.PrayerSchedule
import com.starception.submission.shared.location.DeviceLocation
import com.starception.submission.shared.location.LocationProvider
import com.starception.submission.shared.weather.CurrentConditionsClient
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.UIKit.UIViewController

/**
 * Bridges the shared Compose UI into UIKit so `iosApp/` can present it.
 *
 * This is the whole iOS UI boundary: Swift owns the app lifecycle and hands the
 * screen to Compose. Everything below this line is shared with Android.
 */
@Suppress("FunctionName")
fun PrayerTimesViewController(): UIViewController = ComposeUIViewController {
    var location by remember { mutableStateOf<DeviceLocation?>(null) }
    var resolved by remember { mutableStateOf(false) }
    var weatherCode by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        location = LocationProvider().current()
        // Tracked separately from `location` being null, which is also what a
        // denied permission looks like — without this the UI cannot tell
        // "still asking" from "asked and refused".
        resolved = true
    }

    // Keyed on the resolved position so the forecast follows the user rather
    // than being fetched once for wherever they happened to start.
    LaunchedEffect(location?.latitude, location?.longitude) {
        val place = location ?: return@LaunchedEffect
        weatherCode = CurrentConditionsClient
            .fetch(place.latitude, place.longitude)
            ?.weatherCode
    }

    val place = location ?: FALLBACK_LOCATION
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    val day = PrayerSchedule.forDate(
        year = today.year,
        month = today.monthNumber,
        day = today.dayOfMonth,
        latitude = place.latitude,
        longitude = place.longitude,
        timeZoneOffset = place.timeZoneOffset,
        fajrAngle = UAE_FAJR_ANGLE,
        ishaAngle = UAE_ISHA_ANGLE,
        asrShadowFactor = STANDARD_ASR_SHADOW,
        // Null until the forecast arrives, which prayerSkyWeather treats as Clear.
        weatherCode = weatherCode,
    )

    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkCoastalColorScheme else LightCoastalColorScheme,
        typography = sharedTypography(),
    ) {
        PrayerTimesScreen(
            placeName = place.placeName.ifEmpty { "Locating…" },
            day = day,
            isLocating = !resolved,
        )
    }
}

/**
 * Used until Core Location answers, and if it never does.
 *
 * Prayer times are wrong for the wrong place, so this is a stopgap, not a
 * default worth keeping: the settings slice brings the user's chosen location
 * across and this goes away.
 */
private val FALLBACK_LOCATION = DeviceLocation(
    latitude = 25.1030198,
    longitude = 55.1677409,
    timeZoneOffset = 4.0,
    placeName = "Nad Al Hamar, Dubai",
)

/** UAE_IACAD: 18.2° for both Fajr and Isha. */
private const val UAE_FAJR_ANGLE = 18.2
private const val UAE_ISHA_ANGLE = 18.2

/** 1 for Shafi'i/Maliki/Hanbali; Hanafi would be 2. */
private const val STANDARD_ASR_SHADOW = 1
