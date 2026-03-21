package com.starception.submission.feature.prayertimes.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.starception.submission.feature.prayertimes.PrayerTimesScreen
import com.starception.submission.prayer.service.EnhancedLocationService
import kotlinx.serialization.Serializable

@Serializable
object PrayerTimesRoute

fun NavController.navigateToPrayerTimes() = navigate(PrayerTimesRoute)

fun NavGraphBuilder.prayerTimesScreen(
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    locationService: EnhancedLocationService? = null,
    onSurahClick: (Int) -> Unit = {},
    onSurahClickWithAyah: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    downloadProgress: Float = 0f,
    downloadLabel: String = "Downloading content",
) {
    composable<PrayerTimesRoute> {
        PrayerTimesScreen(
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
            onSurahClick = onSurahClick,
            onSurahClickWithAyah = onSurahClickWithAyah,
            downloadProgress = downloadProgress,
            downloadLabel = downloadLabel,
        )
    }
}