package com.starception.dua.feature.prayertimes.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.starception.dua.feature.prayertimes.PrayerTimesScreen
import kotlinx.serialization.Serializable

@Serializable
object PrayerTimesRoute

fun NavController.navigateToPrayerTimes() = navigate(PrayerTimesRoute)

fun NavGraphBuilder.prayerTimesScreen() {
    composable<PrayerTimesRoute> {
        PrayerTimesScreen()
    }
}