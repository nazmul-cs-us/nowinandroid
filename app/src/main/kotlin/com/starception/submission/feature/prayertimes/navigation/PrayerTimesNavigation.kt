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
    onSettingsClick: () -> Unit = {},
    locationService: EnhancedLocationService? = null
) {
    composable<PrayerTimesRoute> {
        PrayerTimesScreen()
    }
}