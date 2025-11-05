/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.starception.submission.feature.bookmarks.navigation.bookmarksScreen
import com.starception.submission.feature.foryou.navigation.ForYouBaseRoute
import com.starception.submission.feature.foryou.navigation.forYouSection
import com.starception.submission.feature.interests.navigation.navigateToInterests
import com.starception.submission.feature.search.navigation.searchScreen
import com.starception.submission.feature.topic.navigation.navigateToTopic
import com.starception.submission.feature.topic.navigation.topicScreen
import com.starception.submission.feature.prayertimes.navigation.prayerTimesScreen
import com.starception.submission.feature.prayertimes.navigation.PrayerTimesRoute
import com.starception.submission.feature.surah.navigation.navigateToSurah
import com.starception.submission.feature.surah.navigation.surahScreen
import com.starception.submission.navigation.TopLevelDestination.INTERESTS
import com.starception.submission.ui.NiaAppState
import com.starception.submission.ui.interests2pane.interestsListDetailScreen

/**
 * Top-level navigation graph. Navigation is organized as explained at
 * https://d.android.com/jetpack/compose/nav-adaptive
 *
 * The navigation graph defined in this file defines the different top level routes. Navigation
 * within each route is handled using state and Back Handlers.
 */
@Composable
fun NiaNavHost(
    appState: NiaAppState,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = appState.navController
    NavHost(
        navController = navController,
        startDestination = PrayerTimesRoute,
        modifier = modifier,
    ) {
        forYouSection(
            onTopicClick = navController::navigateToTopic,
            onSurahClick = navController::navigateToSurah,
        ) {
            topicScreen(
                showBackButton = true,
                onBackClick = navController::popBackStack,
                onTopicClick = navController::navigateToTopic,
            )
        }
        bookmarksScreen(
            onTopicClick = navController::navigateToTopic,
            onShowSnackbar = onShowSnackbar,
            onSurahClick = navController::navigateToSurah,
        )
        searchScreen(
            onBackClick = navController::popBackStack,
            onInterestsClick = { appState.navigateToTopLevelDestination(INTERESTS) },
            onTopicClick = navController::navigateToTopic,
            onSurahClick = navController::navigateToSurah,
        )
        interestsListDetailScreen()
        prayerTimesScreen()
        surahScreen(
            onBackClick = navController::popBackStack
        )
    }
}
