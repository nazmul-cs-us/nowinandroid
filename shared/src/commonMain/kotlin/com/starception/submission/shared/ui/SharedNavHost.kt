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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.starception.submission.shared.content.SharedContentStore
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * The shared app's destinations.
 *
 * Serializable objects rather than string routes, matching how the Android app
 * declares its own — so a screen moving between them does not have its
 * navigation rewritten on the way.
 *
 * The iOS host uses lean shared implementations where Android-only Room data is
 * unavailable. Every visible top-level destination is therefore functional.
 */
@Serializable
object PrayerTimesRoute

@Serializable
object PrayerSettingsRoute

@Serializable object ForYouRoute
@Serializable object SavedRoute
@Serializable object CourseRoute
@Serializable object InterestsRoute
@Serializable object SearchRoute
@Serializable object ProfileRoute
@Serializable object QuranLibraryRoute
@Serializable data class QuranDetailRoute(val number: Int)
@Serializable data class BukhariBookRoute(val id: Int)
@Serializable data class BukhariHadithRoute(val id: Int)
@Serializable data class TopicRoute(val id: Int)
@Serializable data class TopicArticleRoute(val topicId: Int, val articleId: Int)
@Serializable object QiblaRoute
@Serializable object RecommendationRoute

data class SharedHomeActions(
    val onOpenSettings: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onOpenSearch: () -> Unit,
    val onOpenQuran: (Int) -> Unit,
    val onOpenQibla: () -> Unit,
    val onOpenRecommendation: () -> Unit,
    val onSelectBottom: (Int) -> Unit,
)

/**
 * Hosts the shared screens.
 *
 * This exists so settings can be a destination rather than a sheet. A sheet was
 * the honest shape while there was nowhere to navigate to; now that there is,
 * settings gets a back stack, which is what makes room for the Qibla, Quran and
 * detail screens that follow.
 */
@Composable
fun SharedNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startInSettings: Boolean = false,
    latitude: Double,
    longitude: Double,
    today: LocalDate,
    home: @Composable (SharedHomeActions) -> Unit,
    settings: @Composable (onBack: () -> Unit) -> Unit,
) {
    val contentStore = remember { SharedContentStore() }
    val selectBottom: (Int) -> Unit = { index ->
        when (index) {
            0 -> navController.navigate(PrayerTimesRoute) {
                popUpTo(PrayerTimesRoute) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            1 -> navController.navigate(ForYouRoute) {
                popUpTo(PrayerTimesRoute) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            2 -> navController.navigate(SavedRoute) {
                popUpTo(PrayerTimesRoute) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            3 -> navController.navigate(CourseRoute) {
                popUpTo(PrayerTimesRoute) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            4 -> navController.navigate(InterestsRoute) {
                popUpTo(PrayerTimesRoute) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = if (startInSettings) PrayerSettingsRoute else PrayerTimesRoute,
        modifier = modifier,
    ) {
        composable<PrayerTimesRoute> {
            home(
                SharedHomeActions(
                    onOpenSettings = { navController.navigate(PrayerSettingsRoute) },
                    onOpenProfile = { navController.navigate(ProfileRoute) },
                    onOpenSearch = { navController.navigate(SearchRoute) },
                    onOpenQuran = { navController.navigate(QuranDetailRoute(it)) },
                    onOpenQibla = { navController.navigate(QiblaRoute) },
                    onOpenRecommendation = { navController.navigate(RecommendationRoute) },
                    onSelectBottom = selectBottom,
                ),
            )
        }
        composable<PrayerSettingsRoute> {
            // popBackStack rather than navigate(home): navigating would push a
            // second copy of the home screen and leave settings on the stack,
            // so the system back gesture would return to it.
            settings { navController.popBackStack() }
        }
        composable<ForYouRoute> {
            ForYouScreen(
                date = today,
                store = contentStore,
                onOpenRecommendation = { navController.navigate(RecommendationRoute) },
                onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
                onSelectBottom = selectBottom,
            )
        }
        composable<SavedRoute> {
            SavedScreen(
                store = contentStore,
                onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
                onOpenBukhariBook = { navController.navigate(BukhariBookRoute(it)) },
                onSelectBottom = selectBottom,
            )
        }
        composable<CourseRoute> {
            CourseScreen(contentStore, selectBottom)
        }
        composable<InterestsRoute> {
            InterestsScreen(
                store = contentStore,
                onSelectBottom = selectBottom,
                onOpenTopic = { navController.navigate(TopicRoute(it)) },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenQuranLibrary = { navController.navigate(QuranLibraryRoute) },
                onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
                onOpenBukhariBook = { navController.navigate(BukhariBookRoute(it)) },
            )
        }
        composable<ProfileRoute> {
            ProfileScreen(contentStore) { navController.popBackStack() }
        }
        composable<QuranLibraryRoute> {
            QuranLibraryScreen(
                store = contentStore,
                onBack = { navController.popBackStack() },
                onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
            )
        }
        composable<QuranDetailRoute> { entry ->
            QuranDetailScreen(
                number = entry.toRoute<QuranDetailRoute>().number,
                store = contentStore,
                onBack = { navController.popBackStack() },
            )
        }
        composable<BukhariBookRoute> { entry ->
            BukhariBookDetailScreen(
                id = entry.toRoute<BukhariBookRoute>().id,
                store = contentStore,
                onBack = { navController.popBackStack() },
                onOpenHadith = { navController.navigate(BukhariHadithRoute(it)) },
            )
        }
        composable<BukhariHadithRoute> { entry ->
            BukhariHadithDetailScreen(
                hadithId = entry.toRoute<BukhariHadithRoute>().id,
                onBack = { navController.popBackStack() },
            )
        }
        composable<TopicRoute> { entry ->
            TopicNewsScreen(
                topicId = entry.toRoute<TopicRoute>().id,
                store = contentStore,
                onBack = { navController.popBackStack() },
                onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
                onOpenBukhariBook = { navController.navigate(BukhariBookRoute(it)) },
                onOpenArticle = { topicId, articleId ->
                    navController.navigate(TopicArticleRoute(topicId, articleId))
                },
            )
        }
        composable<TopicArticleRoute> { entry ->
            val route = entry.toRoute<TopicArticleRoute>()
            TopicArticleDetailScreen(
                topicId = route.topicId,
                articleId = route.articleId,
                store = contentStore,
                onBack = { navController.popBackStack() },
            )
        }
        composable<QiblaRoute> {
            QiblaScreen(latitude, longitude) { navController.popBackStack() }
        }
        composable<RecommendationRoute> {
            RecommendationScreen(
                date = today,
                onBack = { navController.popBackStack() },
                onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
                onOpenBukhariBook = { navController.navigate(BukhariBookRoute(it)) },
            )
        }
    }
}
