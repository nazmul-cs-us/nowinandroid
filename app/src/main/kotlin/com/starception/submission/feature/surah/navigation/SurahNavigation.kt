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

package com.starception.submission.feature.surah.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.starception.submission.feature.surah.SurahDetailScreen
import com.starception.submission.navigation.detailEnterTransition
import com.starception.submission.navigation.detailExitTransition
import com.starception.submission.navigation.detailPopEnterTransition
import com.starception.submission.navigation.detailPopExitTransition
import kotlinx.serialization.Serializable

@Serializable
data class SurahRoute(
    val surahNumber: Int,
    // Optional: only present when opened from news feed
    val newsResourceId: String? = null,
    // Optional: scroll to specific ayah number (0 = no scroll)
    val scrollToAyah: Int = 0,
)

fun NavController.navigateToSurah(
    surahNumber: Int,
    newsResourceId: String? = null,
    scrollToAyah: Int = 0,
    navOptions: NavOptions? = null,
) {
    navigate(route = SurahRoute(surahNumber, newsResourceId, scrollToAyah), navOptions)
}

fun NavGraphBuilder.surahScreen(
    onBackClick: () -> Unit,
    onTopicClick: (String) -> Unit = {},
    onNavigateToPreviousSurah: (Int) -> Unit = {},
    onNavigateToNextSurah: (Int) -> Unit = {},
) {
    composable<SurahRoute>(
        enterTransition = { detailEnterTransition() },
        exitTransition = { detailExitTransition() },
        popEnterTransition = { detailPopEnterTransition() },
        popExitTransition = { detailPopExitTransition() },
    ) { backStackEntry ->
        val surahRoute = backStackEntry.toRoute<SurahRoute>()
        // Use the Surah detail screen with MaterialTheme.colorScheme
        SurahDetailScreen(
            surahNumber = surahRoute.surahNumber,
            newsResourceId = surahRoute.newsResourceId,
            scrollToAyah = surahRoute.scrollToAyah,
            onBackClick = onBackClick,
            onTopicClick = onTopicClick,
            onNavigateToPreviousSurah = { onNavigateToPreviousSurah(surahRoute.surahNumber) },
            onNavigateToNextSurah = { onNavigateToNextSurah(surahRoute.surahNumber) },
        )
    }
}
