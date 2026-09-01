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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.starception.submission.core.designsystem.theme.DarkAndroidColorScheme
import com.starception.submission.core.designsystem.theme.DarkCoastalColorScheme
import com.starception.submission.core.designsystem.theme.DarkDefaultColorScheme
import com.starception.submission.core.designsystem.theme.DarkRoyalColorScheme
import com.starception.submission.core.designsystem.theme.LightAndroidColorScheme
import com.starception.submission.core.designsystem.theme.LightCoastalColorScheme
import com.starception.submission.core.designsystem.theme.LightDefaultColorScheme
import com.starception.submission.core.designsystem.theme.LightRoyalColorScheme
import com.starception.submission.core.designsystem.theme.sharedTypography
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.prayer.model.prayerDefaultsFor
import com.starception.submission.shared.PrayerSchedule
import com.starception.submission.shared.location.DeviceLocation
import com.starception.submission.shared.location.LocationProvider
import com.starception.submission.shared.notifications.IosPrayerSchedulePublisher
import com.starception.submission.shared.salah.SalahProgress
import com.starception.submission.shared.salah.SalahTracker
import com.starception.submission.shared.settings.LastLocationStore
import com.starception.submission.shared.settings.UserAppearanceSettings
import com.starception.submission.shared.settings.UserPrayerSettings
import com.starception.submission.shared.weather.CurrentConditionsClient
import kotlin.time.Clock
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

/**
 * Bridges the shared Compose UI into UIKit so `iosApp/` can present it.
 *
 * This is the whole iOS UI boundary: Swift owns the app lifecycle and hands the
 * screen to Compose. Everything below this line is shared with Android.
 */
@Suppress("FunctionName")
fun PrayerTimesViewController(): UIViewController = ComposeUIViewController {
    val tracker = remember { SalahTracker() }
    val settingsStore = remember { UserPrayerSettings() }
    val appearanceStore = remember { UserAppearanceSettings() }
    val locationStore = remember { LastLocationStore() }

    var location by remember { mutableStateOf(locationStore.location()) }
    var resolved by remember { mutableStateOf(false) }
    var weatherCode by remember { mutableStateOf<Int?>(null) }
    var temperature by remember { mutableStateOf<Double?>(null) }
    var refreshRequest by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var syncResultText by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Clock.System.now()) }
    var themeSettings by remember { mutableStateOf(appearanceStore.settings()) }

    LaunchedEffect(refreshRequest) {
        isRefreshing = refreshRequest > 0
        val savedLocation = location
        val refreshedLocation = LocationProvider().current()
        if (refreshedLocation != null) {
            location = refreshedLocation
            locationStore.save(refreshedLocation)
        }
        resolved = true

        val target = refreshedLocation ?: savedLocation ?: FALLBACK_LOCATION
        val conditions = CurrentConditionsClient.fetch(target.latitude, target.longitude)
        weatherCode = conditions?.weatherCode
        temperature = conditions?.temperatureCelsius
        if (refreshRequest > 0) {
            isRefreshing = false
            syncResultText = if (refreshedLocation != null) {
                "Location and prayer times updated"
            } else if (savedLocation != null) {
                "Location unavailable; using last saved location"
            } else {
                "Location unavailable; using Dubai fallback"
            }
            delay(3_000)
            syncResultText = null
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = Clock.System.now()
        }
    }

    val place = location ?: FALLBACK_LOCATION
    val country = prayerDefaultsFor(place.countryCode)
    var prayerSettings by remember(place.countryCode, country) {
        mutableStateOf(settingsStore.settings(place.countryCode, country))
    }
    var notificationPrefs by remember { mutableStateOf(settingsStore.notifications()) }
    // Prayer times belong to the resolved location, not necessarily the device's
    // current timezone (for example, when viewing a cached location while travelling).
    val localNow = now.toLocalDateTime(timeZoneForOffset(place.timeZoneOffset))
    val today = localNow.date

    var completed by remember(today) { mutableStateOf(tracker.completed(today)) }

    // Keyed, not recomputed on every recomposition. The calculator is not cheap
    // and logs several thousand lines per run, all synchronously on the main
    // thread — recomputing it for a "+1 minute" tap stalls the UI visibly.
    val day = remember(
        today,
        place.latitude,
        place.longitude,
        place.timeZoneOffset,
        place.countryCode,
        country,
        prayerSettings,
        weatherCode,
        temperature,
        localNow.hour,
        localNow.minute,
    ) {
        PrayerSchedule.forDate(
            year = today.year,
            month = today.monthNumber,
            day = today.dayOfMonth,
            latitude = place.latitude,
            longitude = place.longitude,
            timeZoneOffset = place.timeZoneOffset,
            // The country's own method keeps its authority's published offsets.
            defaults = country,
            settings = prayerSettings,
            // Null until the forecast arrives, which prayerSkyWeather treats as Clear.
            weatherCode = weatherCode,
            temperatureCelsius = temperature,
            countryCode = place.countryCode,
            isFriday = today.dayOfWeek == DayOfWeek.FRIDAY,
            nowHour = localNow.hour,
            nowMinute = localNow.minute,
        )
    }

    LaunchedEffect(
        today,
        place,
        country,
        prayerSettings,
        notificationPrefs,
        localNow.hour,
        localNow.minute,
    ) {
        IosPrayerSchedulePublisher.publish(
            locationName = place.placeName,
            startDate = today,
            latitude = place.latitude,
            longitude = place.longitude,
            timeZoneOffset = place.timeZoneOffset,
            countryCode = place.countryCode,
            defaults = country,
            settings = prayerSettings,
            preferences = notificationPrefs,
        )
    }

    val useDarkTheme = when (themeSettings.darkThemeConfig) {
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
    }
    val appVersion = remember {
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            ?: "Unknown"
    }

    MaterialTheme(
        colorScheme = iosColorScheme(themeSettings.brand, useDarkTheme),
        typography = sharedTypography(),
    ) {
        SharedNavHost(
            latitude = place.latitude,
            longitude = place.longitude,
            today = today,
            home = { actions ->
                PrayerTimesScreen(
                    placeName = place.placeName.ifEmpty { "Locating…" },
                    day = day,
                    salah = SalahProgress.from(completed),
                    onTogglePrayer = { completed = tracker.toggle(today, it) },
                    offsets = prayerSettings.timeOffsets,
                    onAdjustPrayer = { prayer, delta ->
                        prayerSettings = settingsStore.adjust(
                            place.countryCode,
                            country,
                            prayer,
                            delta,
                        )
                    },
                    onOpenSettings = actions.onOpenSettings,
                    today = today,
                    isLocating = !resolved,
                    isRefreshing = isRefreshing,
                    syncResultText = syncResultText,
                    onRefresh = { refreshRequest += 1 },
                    latitude = place.latitude,
                    longitude = place.longitude,
                    notifications = notificationPrefs,
                    onTogglePrayerNotification = { prayer ->
                        notificationPrefs = notificationPrefs.togglePrayer(prayer)
                        settingsStore.saveNotifications(notificationPrefs)
                    },
                    onOpenProfile = actions.onOpenProfile,
                    onOpenSearch = actions.onOpenSearch,
                    onVoiceTap = actions.onOpenSearch,
                    onOpenQuran = actions.onOpenQuran,
                    onOpenQibla = actions.onOpenQibla,
                    onOpenRecommendation = actions.onOpenRecommendation,
                    onSelectBottom = actions.onSelectBottom,
                )
            },
            settings = { onBack ->
                PrayerSettingsScreen(
                    settings = prayerSettings,
                    countryName = country?.countryName,
                    showRestoreOption = settingsStore.isChanged(place.countryCode, country),
                    onSettingsChange = { updated ->
                        prayerSettings = updated
                        settingsStore.save(place.countryCode, updated)
                    },
                    onRestore = {
                        settingsStore.restoreDefaults(place.countryCode)
                        prayerSettings = settingsStore.settings(place.countryCode, country)
                    },
                    onBack = onBack,
                    notifications = notificationPrefs,
                    onNotificationsChange = { updated ->
                        notificationPrefs = updated
                        settingsStore.saveNotifications(updated)
                    },
                    themeSettings = themeSettings,
                    onThemeBrandChange = { brand ->
                        appearanceStore.saveBrand(brand)
                        themeSettings = themeSettings.copy(brand = brand)
                    },
                    onDarkThemeConfigChange = { config ->
                        appearanceStore.saveDarkTheme(config)
                        themeSettings = themeSettings.copy(darkThemeConfig = config)
                    },
                    appVersion = appVersion,
                )
            },
        )
    }
}

private fun iosColorScheme(brand: ThemeBrand, dark: Boolean): ColorScheme = when (brand) {
    ThemeBrand.DEFAULT -> if (dark) DarkDefaultColorScheme else LightDefaultColorScheme
    ThemeBrand.ANDROID -> if (dark) DarkAndroidColorScheme else LightAndroidColorScheme
    ThemeBrand.COASTAL -> if (dark) DarkCoastalColorScheme else LightCoastalColorScheme
    ThemeBrand.ROYAL -> if (dark) DarkRoyalColorScheme else LightRoyalColorScheme
    ThemeBrand.CUSTOM -> if (dark) DarkCoastalColorScheme else LightCoastalColorScheme
}

/**
 * Used only when neither Core Location nor a previously saved fix is available.
 *
 * Prayer times are wrong for the wrong place, so this is a stopgap, not a
 * default worth keeping. A successful fix is persisted and takes precedence on
 * future launches.
 */
private val FALLBACK_LOCATION = DeviceLocation(
    latitude = 25.1030198,
    longitude = 55.1677409,
    timeZoneOffset = 4.0,
    placeName = "Nad Al Hamar, Dubai",
    countryCode = "AE",
)

private fun timeZoneForOffset(offsetHours: Double): TimeZone {
    val offsetMinutes = (offsetHours * 60).roundToInt().coerceIn(-18 * 60, 18 * 60)
    val absoluteMinutes = abs(offsetMinutes)
    val hours = (absoluteMinutes / 60).toString().padStart(2, '0')
    val minutes = (absoluteMinutes % 60).toString().padStart(2, '0')
    val sign = if (offsetMinutes < 0) "-" else "+"
    return TimeZone.of("UTC$sign$hours:$minutes")
}

private fun com.starception.submission.prayer.model.PrayerNotificationPreferences.togglePrayer(
    prayer: String,
) = when (prayer.lowercase()) {
    "fajr" -> copy(fajrNotificationEnabled = !fajrNotificationEnabled)
    "dhuhr" -> copy(dhuhrNotificationEnabled = !dhuhrNotificationEnabled)
    "asr" -> copy(asrNotificationEnabled = !asrNotificationEnabled)
    "maghrib" -> copy(maghribNotificationEnabled = !maghribNotificationEnabled)
    "isha" -> copy(ishaNotificationEnabled = !ishaNotificationEnabled)
    else -> this
}
