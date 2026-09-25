/*
 * Copyright 2026 The Android Open Source Project
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

package com.starception.submission.feature.salah.datacollection

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.starception.submission.core.designsystem.animation.NiaTransitions
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
    onNavigateToLiveRecording: () -> Unit = {},
    onNavigateToReview: (String) -> Unit = {},
) {
    composable<SalahDataCollectionRoute>(
        enterTransition = { NiaTransitions.detailEnter() },
        exitTransition = { NiaTransitions.detailExit() },
        popEnterTransition = { NiaTransitions.detailPopEnter() },
        popExitTransition = { NiaTransitions.detailPopExit() },
    ) {
        SalahDataCollectionScreen(
            onBackClick = onBackClick,
            onNavigateToLiveRecording = onNavigateToLiveRecording,
            onNavigateToReview = onNavigateToReview,
        )
    }
}

fun NavGraphBuilder.salahLiveRecordingScreen(
    onNavigateToReview: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    composable<SalahLiveRecordingRoute>(
        enterTransition = { NiaTransitions.detailEnter() },
        exitTransition = { NiaTransitions.detailExit() },
        popEnterTransition = { NiaTransitions.detailPopEnter() },
        popExitTransition = { NiaTransitions.detailPopExit() },
    ) {
        LivePrayerRecordingScreen(
            onNavigateToReview = onNavigateToReview,
            onBack = onBackClick,
        )
    }
}

fun NavGraphBuilder.salahPrayerReviewScreen(
    onBackClick: () -> Unit,
) {
    composable<SalahPrayerReviewRoute>(
        enterTransition = { NiaTransitions.detailEnter() },
        exitTransition = { NiaTransitions.detailExit() },
        popEnterTransition = { NiaTransitions.detailPopEnter() },
        popExitTransition = { NiaTransitions.detailPopExit() },
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<SalahPrayerReviewRoute>()
        PrayerReviewScreen(
            filePath = route.filePath,
            onBack = onBackClick,
        )
    }
}
