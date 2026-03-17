package com.starception.submission.feature.salah.datacollection

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
object SalahDataCollectionRoute

@Serializable
object SalahLiveRecordingRoute

@Serializable
data class SalahPrayerReviewRoute(val filePath: String)

fun NavController.navigateToSalahDataCollection() = navigate(SalahDataCollectionRoute)

fun NavController.navigateToSalahLiveRecording() = navigate(SalahLiveRecordingRoute)

fun NavController.navigateToSalahPrayerReview(filePath: String) = navigate(SalahPrayerReviewRoute(filePath))

fun NavGraphBuilder.salahDataCollectionScreen(
    onBackClick: () -> Unit,
    onNavigateToLiveRecording: () -> Unit = {}
) {
    composable<SalahDataCollectionRoute> {
        SalahDataCollectionScreen(
            onBackClick = onBackClick,
            onNavigateToLiveRecording = onNavigateToLiveRecording
        )
    }
}

fun NavGraphBuilder.salahLiveRecordingScreen(
    onNavigateToReview: (String) -> Unit,
    onBackClick: () -> Unit
) {
    composable<SalahLiveRecordingRoute> {
        LivePrayerRecordingScreen(
            onNavigateToReview = onNavigateToReview,
            onBack = onBackClick
        )
    }
}

fun NavGraphBuilder.salahPrayerReviewScreen(
    onBackClick: () -> Unit
) {
    composable<SalahPrayerReviewRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<SalahPrayerReviewRoute>()
        PrayerReviewScreen(
            filePath = route.filePath,
            onBack = onBackClick
        )
    }
}
