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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.starception.submission.shared.audio.QuranAudioPlayer
import com.starception.submission.shared.content.SharedContentStore
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.Flow
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

@Serializable data class CourseDetailRoute(val courseId: String)

@Serializable object InterestsRoute

@Serializable object SearchRoute

@Serializable object ProfileRoute

@Serializable object QuranLibraryRoute

@Serializable object DrivingModeRoute

@Serializable data class QuranDetailRoute(val number: Int)

@Serializable data class NewsDetailRoute(val id: Int)

@Serializable data class BukhariBookRoute(val id: Int)

@Serializable data class ShamayelBookRoute(val id: Int)

@Serializable data class ShamayelHadithRoute(val id: Int)

@Serializable data class BukhariHadithRoute(val id: Int)

@Serializable data class HadithCollectionBookRoute(val databaseFile: String, val bookId: Int)

@Serializable data class HadithCollectionHadithRoute(val databaseFile: String, val hadithId: Int)

@Serializable data class TopicRoute(val id: Int)

@Serializable data class TopicArticleRoute(val topicId: Int, val articleId: Int)

@Serializable object FortressLibraryRoute

@Serializable data class FortressChapterRoute(val id: Int)

@Serializable object DuaLibraryRoute

@Serializable data class DuaDetailRoute(val number: Int)

@Serializable object SalahTrainingRoute

@Serializable object PrayerSimulationRoute

@Serializable data class SalahReviewRoute(val fileName: String)

@Serializable object QiblaRoute

@Serializable object RecommendationRoute

data class SharedHomeActions(
    val searchController: SharedSearchController? = null,
    val onOpenSettings: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onOpenSearch: () -> Unit,
    val onOpenQuran: (Int) -> Unit,
    val onOpenBukhariBook: (Int) -> Unit = {},
    val onOpenBukhariHadith: (Int) -> Unit = {},
    val onOpenHadithSource: (String, Int) -> Unit = { _, _ -> },
    val onOpenQuranicDua: (Int) -> Unit = {},
    val onOpenFortressChapter: (Int) -> Unit = {},
    val onOpenShamayelBook: (Int) -> Unit = {},
    val onOpenQibla: () -> Unit,
    val onOpenRecommendation: () -> Unit,
    val onOpenDrivingMode: () -> Unit = {},
    val onSelectBottom: (Int) -> Unit,
)

internal val LocalQuranAudioPlayer = staticCompositionLocalOf<QuranAudioPlayer> {
    error("No QuranAudioPlayer provided")
}

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
    drivingCoordinator: com.starception.submission.shared.travel.DrivingModeCoordinator =
        com.starception.submission.shared.travel.DrivingModeCoordinator(),
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startInSettings: Boolean = false,
    startInQibla: Boolean = false,
    startInSurah: Int? = null,
    startFortressChapter: Int? = null,
    startDuaNumber: Int? = null,
    startBukhariHadithId: Int? = null,
    startBottomIndex: Int = 0,
    resetToPrayerTimes: Boolean = false,
    onSelectTopLevel: ((Int) -> Unit)? = null,
    externalVoiceSearchRequests: Flow<Int>? = null,
    onExternalVoiceSearchHandled: (() -> Unit)? = null,
    externalPrayerTimesRequests: Flow<Unit>? = null,
    latitude: Double,
    longitude: Double,
    today: LocalDate,
    home: @Composable (SharedHomeActions) -> Unit,
    settings: @Composable (onBack: () -> Unit, onOpenSalahTraining: () -> Unit) -> Unit,
    createQualityAnalyzer: (() -> com.starception.submission.shared.ml.SalahQualityAnalyzer?)? = null,
    salah3DService: Salah3DSceneService? = null,
    prayerDay: com.starception.submission.shared.SharedPrayerDay? = null,
    notifications: com.starception.submission.prayer.model.PrayerNotificationPreferences? = null,
    globalRefreshing: Boolean = false,
    globalSyncResultText: String? = null,
) {
    // The prayer-time alert (go-to-mosue countdown when prayer approaches)
    // shows in the sync strip of EVERY top page, like Android's app-level
    // PullToSyncContainer.
    val tabPrayerAlert = prayerDay?.prayerAlertState(notifications ?: com.starception.submission.prayer.model.PrayerNotificationPreferences())
    var showQiblaDirection by remember { mutableStateOf(false) }
    if (showQiblaDirection) {
        QiblaGlobeSheet(latitude, longitude, onDismiss = { showQiblaDirection = false })
    }
    val contentStore = remember { SharedContentStore() }
    val quranPlayer = remember { QuranAudioPlayer() }
    val openNews: (Int) -> Unit = { id ->
        if (id in SURAH_NEWS_ID_RANGE) {
            navController.navigate(QuranDetailRoute(id - SURAH_NEWS_ID_OFFSET))
        } else {
            navController.navigate(NewsDetailRoute(id))
        }
    }
    // The app-level SearchPrefillBus equivalent: one search surface, shared
    // by every bottom-tab page (Android's AppTopSearchBar pattern).
    val searchController = rememberSharedSearchController(
        onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
        onOpenBukhariBook = { navController.navigate(BukhariBookRoute(it)) },
        onOpenBukhariHadith = { navController.navigate(BukhariHadithRoute(it)) },
        onOpenQuranicDua = { navController.navigate(DuaDetailRoute(it)) },
        onOpenFortressChapter = { navController.navigate(FortressChapterRoute(it)) },
        onOpenNews = openNews,
        onRecordRecent = { contentStore.addRecentSearch(it) },
    )
    LaunchedEffect(resetToPrayerTimes) {
        if (resetToPrayerTimes) {
            navController.navigate(PrayerTimesRoute) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(externalVoiceSearchRequests, startBottomIndex) {
        externalVoiceSearchRequests?.collect { requestedTab ->
            if (requestedTab == startBottomIndex) {
                searchController.openVoice()
                onExternalVoiceSearchHandled?.invoke()
            }
        }
    }
    DisposableEffect(quranPlayer) {
        onDispose {
            quranPlayer.stop()
            com.starception.submission.shared.media.QuranPlaybackBus.clear()
        }
    }
    val openTopic: (Int) -> Unit = { id ->
        navController.navigate(TopicRoute(id)) { launchSingleTop = true }
    }
    val selectBottom: (Int) -> Unit = { index ->
        if (onSelectTopLevel != null) {
            onSelectTopLevel(index)
        } else when (index) {
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
    LaunchedEffect(externalPrayerTimesRequests) {
        externalPrayerTimesRequests?.collect {
            selectBottom(0)
        }
    }
    // UI-testing hooks: push a reader detail screen over the home tab so the
    // back stack matches the real flow — tapping Back must pop to home.
    LaunchedEffect(startFortressChapter, startDuaNumber, startBukhariHadithId) {
        startFortressChapter
            ?.takeIf { it in 1..298 }
            ?.let { navController.navigate(FortressChapterRoute(it)) }
        startDuaNumber
            ?.takeIf { it > 0 }
            ?.let { navController.navigate(DuaDetailRoute(it)) }
        startBukhariHadithId
            ?.takeIf { it > 0 }
            ?.let { navController.navigate(BukhariHadithRoute(it)) }
    }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val ownsSyncContainer = currentRoute in tabRouteNames ||
        currentRoute == PrayerSettingsRoute::class.qualifiedName
    val detailMediaBar = if (ownsSyncContainer) {
        null
    } else {
        QuranPlaybackSyncBar { surahNumber ->
            navController.navigate(QuranDetailRoute(surahNumber)) { launchSingleTop = true }
        }
    }
    Box(modifier) {
        com.starception.submission.feature.prayertimes.wobble.PullToSyncContainer(
            isRefreshing = false,
            onRefresh = {},
            mediaBar = detailMediaBar,
            enabled = false,
            modifier = Modifier.fillMaxSize(),
        ) { _ ->
        NavHost(
            navController = navController,
            startDestination = startInSurah
                ?.takeIf { it in 1..114 }
                ?.let(::QuranDetailRoute)
                ?: when {
                    startInQibla -> QiblaRoute
                    startInSettings -> PrayerSettingsRoute
                    startBottomIndex == 1 -> ForYouRoute
                    startBottomIndex == 2 -> SavedRoute
                    startBottomIndex == 3 -> CourseRoute
                    startBottomIndex == 4 -> InterestsRoute
                    else -> PrayerTimesRoute
                },
            modifier = Modifier,
            // iOS navigation choreography: bottom-tab switches use a
            // Material fade-through (subtle scale + delayed fade), while every detail screen performs a
            // UINavigationController-style horizontal push — the incoming
            // page slides in from the trailing edge on a no-bounce spring
            // (interruptible mid-flight), the outgoing page recedes a third
            // of the width with a gentle fade. Pop reverses the move.
            enterTransition = {
                if (isTabSwitch(targetState)) {
                    fadeIn(tween(260, delayMillis = 55)) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(260),
                    )
                } else {
                    slideInHorizontally(
                        animationSpec = iosPushSpring(),
                        initialOffsetX = { it },
                    ) + fadeIn(tween(200))
                }
            },
            exitTransition = {
                if (isTabSwitch(initialState)) {
                    fadeOut(tween(120)) + scaleOut(
                        targetScale = 0.98f,
                        animationSpec = tween(180),
                    )
                } else {
                    slideOutHorizontally(
                        animationSpec = iosPushSpring(),
                        targetOffsetX = { -it / 3 },
                    ) + fadeOut(tween(320))
                }
            },
            popEnterTransition = {
                if (isTabSwitch(targetState)) {
                    fadeIn(tween(260, delayMillis = 55)) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(260),
                    )
                } else {
                    slideInHorizontally(
                        animationSpec = iosPushSpring(),
                        initialOffsetX = { -it / 3 },
                    ) + fadeIn(tween(200))
                }
            },
            popExitTransition = {
                if (isTabSwitch(targetState)) {
                    fadeOut(tween(120)) + scaleOut(
                        targetScale = 0.98f,
                        animationSpec = tween(180),
                    )
                } else {
                    slideOutHorizontally(
                        animationSpec = iosPushSpring(),
                        targetOffsetX = { it },
                    ) + fadeOut(tween(320))
                }
            },
        ) {
            composable<PrayerTimesRoute> {
                CompositionLocalProvider(LocalQuranAudioPlayer provides quranPlayer) {
                    home(
                        SharedHomeActions(
                            searchController = searchController,
                            onOpenSettings = { navController.navigate(PrayerSettingsRoute) },
                            onOpenProfile = { navController.navigate(ProfileRoute) },
                            onOpenSearch = { navController.navigate(SearchRoute) },
                            onOpenQuran = { navController.navigate(QuranDetailRoute(it)) },
                            onOpenBukhariBook = { navController.navigate(BukhariBookRoute(it)) },
                            onOpenBukhariHadith = { navController.navigate(BukhariHadithRoute(it)) },
                            onOpenHadithSource = { databaseFile, hadithId ->
                                when (databaseFile) {
                                    "sahih_bukhari.db" -> navController.navigate(BukhariHadithRoute(hadithId))
                                    "shamayele_tirmidhi_complete.db" -> navController.navigate(ShamayelHadithRoute(hadithId))
                                    else -> navController.navigate(HadithCollectionHadithRoute(databaseFile, hadithId))
                                }
                            },
                            onOpenQuranicDua = { navController.navigate(DuaDetailRoute(it)) },
                            onOpenFortressChapter = { navController.navigate(FortressChapterRoute(it)) },
                            onOpenShamayelBook = { navController.navigate(ShamayelBookRoute(it)) },
                            onOpenQibla = { showQiblaDirection = true },
                            onOpenRecommendation = { navController.navigate(RecommendationRoute) },
                            onOpenDrivingMode = { navController.navigate(DrivingModeRoute) },
                            onSelectBottom = selectBottom,
                        ),
                    )
                }
            }
            composable<PrayerSettingsRoute> {
                // Settings hosts the Content & Storage downloads, so the
                // app-level sync strip carries the download banner here too —
                // the same ContentDownloadBus feed the home strip shows.
                // Download-only mode: the pull gesture stays disabled so the
                // settings scrollables are untouched.
                val downloadStatus by com.starception.submission.shared.assets.ContentDownloadBus
                    .state.collectAsState()
                com.starception.submission.feature.prayertimes.wobble.PullToSyncContainer(
                    isRefreshing = globalRefreshing,
                    onRefresh = { },
                    syncResultText = globalSyncResultText,
                    prayerAlertState = tabPrayerAlert
                        ?: com.starception.submission.feature.prayertimes.wobble.PrayerAlertState(),
                    downloadProgress = downloadStatus?.progress ?: 0f,
                    downloadLabel = downloadStatus?.label.orEmpty(),
                    mediaBar = QuranPlaybackSyncBar { surahNumber ->
                        navController.navigate(QuranDetailRoute(surahNumber))
                    },
                    enabled = false,
                ) {
                    // popBackStack rather than navigate(home): navigating would push a
                    // second copy of the home screen and leave settings on the stack,
                    // so the system back gesture would return to it.
                    settings(
                        { navController.popBackStack() },
                        { navController.navigate(SalahTrainingRoute) },
                    )
                }
            }
            composable<ForYouRoute> {
                ForYouScreen(
                    date = today,
                    store = contentStore,
                    onOpenRecommendation = { navController.navigate(RecommendationRoute) },
                    onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
                    onSelectBottom = selectBottom,
                    onOpenNews = openNews,
                    onOpenTopic = openTopic,
                    searchController = searchController,
                    onOpenSettings = { navController.navigate(PrayerSettingsRoute) },
                    onOpenProfile = { navController.navigate(ProfileRoute) },
                )
            }
            composable<SavedRoute> {
                SavedScreen(
                    store = contentStore,
                    onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
                    onOpenBukhariBook = { navController.navigate(BukhariBookRoute(it)) },
                    onSelectBottom = selectBottom,
                    onOpenNews = openNews,
                    onOpenTopic = openTopic,
                    searchController = searchController,
                    onOpenSettings = { navController.navigate(PrayerSettingsRoute) },
                    onOpenProfile = { navController.navigate(ProfileRoute) },
                )
            }
            composable<CourseDetailRoute> { entry ->
                val route = entry.toRoute<CourseDetailRoute>()
                CourseDetailScreen(
                    courseId = route.courseId,
                    store = contentStore,
                    onBack = { navController.popBackStack() },
                    onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
                    onOpenBukhariBook = { navController.navigate(BukhariBookRoute(it)) },
                )
            }
            composable<CourseRoute> {
                CourseScreen(
                    store = contentStore,
                    onSelectBottom = selectBottom,
                    searchController = searchController,
                    onOpenSettings = { navController.navigate(PrayerSettingsRoute) },
                    onOpenProfile = { navController.navigate(ProfileRoute) },
                    prayerAlert = tabPrayerAlert,
                    onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
                    onOpenBukhariBook = { navController.navigate(BukhariBookRoute(it)) },
                    onOpenCourseDetail = { navController.navigate(CourseDetailRoute(it)) },
                )
            }
            composable<InterestsRoute> {
                InterestsScreen(
                    store = contentStore,
                    onSelectBottom = selectBottom,
                    onOpenTopic = openTopic,
                    onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
                    searchController = searchController,
                    onOpenSettings = { navController.navigate(PrayerSettingsRoute) },
                    onOpenProfile = { navController.navigate(ProfileRoute) },
                    prayerAlert = tabPrayerAlert,
                    globalRefreshing = globalRefreshing,
                    globalSyncResultText = globalSyncResultText,
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
            composable<DrivingModeRoute> {
                DrivingModeScreen(
                    coordinator = drivingCoordinator,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<FortressLibraryRoute> {
                FortressLibraryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChapter = { chapterId ->
                        navController.navigate(FortressChapterRoute(chapterId))
                    },
                )
            }
            composable<FortressChapterRoute> { entry ->
                val route = entry.toRoute<FortressChapterRoute>()
                FortressChapterScreen(
                    chapterId = route.id,
                    player = quranPlayer,
                    store = contentStore,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<DuaLibraryRoute> {
                SharedDuaLibraryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDua = { duaNumber ->
                        navController.navigate(DuaDetailRoute(duaNumber))
                    },
                )
            }
            composable<DuaDetailRoute> { entry ->
                val route = entry.toRoute<DuaDetailRoute>()
                SharedDuaDetailScreen(
                    number = route.number,
                    store = contentStore,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<QuranLibraryRoute> {
                QuranLibraryScreen(
                    store = contentStore,
                    onBack = { navController.popBackStack() },
                    onOpenSurah = { navController.navigate(QuranDetailRoute(it)) },
                    onOpenDuas = { navController.navigate(DuaLibraryRoute) },
                    onOpenFortress = { navController.navigate(FortressLibraryRoute) },
                )
            }
            composable<QuranDetailRoute> { entry ->
                QuranDetailScreen(
                    number = entry.toRoute<QuranDetailRoute>().number,
                    store = contentStore,
                    player = quranPlayer,
                    onOpenSurah = { surahNumber ->
                        // Prev/next surah replaces the current entry so swiping
                        // through surahs doesn't pile up back-stack entries.
                        navController.navigate(QuranDetailRoute(surahNumber)) {
                            navController.currentDestination?.id?.let { id ->
                                popUpTo(id) { inclusive = true }
                            }
                            launchSingleTop = true
                        }
                    },
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(PrayerTimesRoute) {
                                popUpTo<QuranDetailRoute> { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
            composable<NewsDetailRoute> { entry ->
                val id = entry.toRoute<NewsDetailRoute>().id
                if (id in SURAH_NEWS_ID_RANGE) {
                    QuranDetailScreen(
                        number = id - SURAH_NEWS_ID_OFFSET,
                        store = contentStore,
                        player = quranPlayer,
                        onOpenSurah = { surahNumber ->
                            navController.navigate(QuranDetailRoute(surahNumber)) {
                                navController.currentDestination?.id?.let { destinationId ->
                                    popUpTo(destinationId) { inclusive = true }
                                }
                                launchSingleTop = true
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    NewsDetailScreen(
                        id = id,
                        store = contentStore,
                        onBack = { navController.popBackStack() },
                    )
                }
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
                    store = contentStore,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<ShamayelBookRoute> { entry ->
                ShamayelBookDetailScreen(
                    id = entry.toRoute<ShamayelBookRoute>().id,
                    store = contentStore,
                    onBack = { navController.popBackStack() },
                    onOpenHadith = { navController.navigate(ShamayelHadithRoute(it)) },
                )
            }
            composable<ShamayelHadithRoute> { entry ->
                ShamayelHadithDetailScreen(
                    hadithId = entry.toRoute<ShamayelHadithRoute>().id,
                    store = contentStore,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<HadithCollectionBookRoute> { entry ->
                val route = entry.toRoute<HadithCollectionBookRoute>()
                HadithCollectionBookScreen(
                    databaseFile = route.databaseFile,
                    bookId = route.bookId,
                    onBack = { navController.popBackStack() },
                    onOpenHadith = {
                        navController.navigate(HadithCollectionHadithRoute(route.databaseFile, it))
                    },
                )
            }
            composable<HadithCollectionHadithRoute> { entry ->
                val route = entry.toRoute<HadithCollectionHadithRoute>()
                HadithCollectionHadithScreen(
                    databaseFile = route.databaseFile,
                    hadithId = route.hadithId,
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
                    onOpenShamayelBook = { navController.navigate(ShamayelBookRoute(it)) },
                    onOpenHadithCollectionBook = { databaseFile, bookId ->
                        navController.navigate(HadithCollectionBookRoute(databaseFile, bookId))
                    },
                    onOpenArticle = { topicId, articleId ->
                        navController.navigate(TopicArticleRoute(topicId, articleId))
                    },
                    onOpenNews = openNews,
                    onOpenTopic = openTopic,
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
            composable<SalahTrainingRoute> {
                SalahTrainingLabScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSession = { fileName ->
                        navController.navigate(SalahReviewRoute(fileName))
                    },
                    onOpenPrayerSimulation = { navController.navigate(PrayerSimulationRoute) },
                )
            }
            composable<PrayerSimulationRoute> {
                PrayerSimulationScreen(
                    onBack = { navController.popBackStack() },
                    scene3DService = salah3DService,
                )
            }
            composable<SalahReviewRoute> { entry ->
                SalahSessionReviewScreen(
                    fileName = entry.toRoute<SalahReviewRoute>().fileName,
                    // Forward the factory itself: invoking it here would read
                    // + parse the bundled norm params during navigation on the
                    // main thread.
                    qualityAnalyzerFactory = createQualityAnalyzer,
                    scene3DService = salah3DService,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<QiblaRoute> {
                QiblaScreen(latitude, longitude) {
                    if (!navController.popBackStack()) {
                        navController.navigate(PrayerTimesRoute) {
                            launchSingleTop = true
                        }
                    }
                }
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

        // The shared search surface floats above every destination.
        SharedSearchOverlay(searchController)
    }
}

private val SURAH_NEWS_ID_RANGE = 2001..2114
private const val SURAH_NEWS_ID_OFFSET = 2000

/**
 * The bottom-bar destinations: switching between them is a quick tab
 * crossfade, not a navigation push, matching UIKit tab semantics. Object
 * routes resolve to their qualified class name as the route pattern.
 */
private val tabRouteNames = setOf(
    PrayerTimesRoute::class.qualifiedName,
    ForYouRoute::class.qualifiedName,
    SavedRoute::class.qualifiedName,
    CourseRoute::class.qualifiedName,
    InterestsRoute::class.qualifiedName,
)

private fun isTabSwitch(state: NavBackStackEntry): Boolean =
    state.destination.route in tabRouteNames

/**
 * The iOS push spring: critically damped with a ~450ms settle, so pushes
 * feel decisive without bouncing, and an interrupted transition hands its
 * velocity over mid-flight instead of restarting.
 */
private fun iosPushSpring(): androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset> =
    spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 550f,
    )
