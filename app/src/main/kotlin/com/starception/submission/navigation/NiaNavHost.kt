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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import com.starception.submission.core.designsystem.animation.NiaTransitions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.core.qurandatabase.QuranRepository
import com.starception.submission.feature.search.SearchNote
import androidx.navigation.compose.composable
import com.starception.submission.MainActivityViewModel
import com.starception.submission.ui.bookmarks2pane.bookmarksListDetailScreen
import com.starception.submission.feature.foryou.navigation.ForYouRoute
import com.starception.submission.feature.interests.navigation.navigateToInterests
import com.starception.submission.feature.search.navigation.searchScreen
import com.starception.submission.feature.topic.navigation.navigateToTopic
import com.starception.submission.feature.topic.navigation.topicScreen
import com.starception.submission.feature.prayertimes.navigation.prayerTimesScreen
import com.starception.submission.feature.prayertimes.navigation.PrayerTimesRoute
import com.starception.submission.feature.course.navigation.courseScreen
import com.starception.submission.feature.course.navigation.navigateToCourseDetail
import com.starception.submission.feature.surah.navigation.navigateToSurah
import com.starception.submission.feature.surah.navigation.surahScreen
import com.starception.submission.feature.dua.duaDetailScreen
import com.starception.submission.feature.dua.navigateToDuaDetail
import com.starception.submission.feature.hadith.hadithDetailScreen
import com.starception.submission.feature.hadith.navigateToHadithDetail
import com.starception.submission.feature.salah.datacollection.navigateToSalahDataCollection
import com.starception.submission.feature.salah.datacollection.navigateToSalahLiveRecording
import com.starception.submission.feature.salah.datacollection.navigateToSalahPrayerReview
import com.starception.submission.feature.salah.datacollection.salahDataCollectionScreen
import com.starception.submission.feature.salah.datacollection.salahLiveRecordingScreen
import com.starception.submission.feature.salah.datacollection.salahPrayerReviewScreen
import com.starception.submission.navigation.TopLevelDestination.INTERESTS
import com.starception.submission.settings.navigation.settingsScreen
import com.starception.submission.ui.NiaAppState
import com.starception.submission.ui.foryou2pane.forYouListDetailScreen
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
    onTopAppBarActionClick: () -> Unit = {},
    mainViewModel: MainActivityViewModel? = null,
    deepLinkCourseId: String? = null,
) {
    val navController = appState.navController
    val context = LocalContext.current
    val quranRepository = remember { QuranRepository(context) }
    val homeDownloadProgress = if (mainViewModel != null) {
        val isDownloading by mainViewModel.isContentDownloading.collectAsStateWithLifecycle()
        val downloadProgress by mainViewModel.contentDownloadProgress.collectAsStateWithLifecycle()
        if (isDownloading) downloadProgress else 0f
    } else {
        0f
    }
    val homeDownloadLabel = if (mainViewModel != null) {
        val label by mainViewModel.contentDownloadLabel.collectAsStateWithLifecycle()
        label
    } else {
        ""
    }
    val homeMediaState = if (mainViewModel != null) {
        val state by mainViewModel.globalMedia.controllerState.collectAsStateWithLifecycle()
        state
    } else {
        com.starception.submission.media.MediaControllerUiState()
    }
    val homePrayerAlertOverride = if (mainViewModel != null) {
        val override by mainViewModel.prayerAlertState.collectAsStateWithLifecycle()
        override
    } else {
        com.starception.submission.feature.prayertimes.wobble.PrayerAlertState()
    }
    val homeIsSyncing = if (mainViewModel != null) {
        val s by mainViewModel.isSyncing.collectAsStateWithLifecycle()
        s
    } else false
    val homeTtsPreparing = if (mainViewModel != null) {
        val p by mainViewModel.isTtsPreparing.collectAsStateWithLifecycle()
        p
    } else false

    // Handle deep link for course
    var deepLinkHandled by remember { mutableStateOf(false) }
    LaunchedEffect(deepLinkCourseId) {
        if (deepLinkCourseId != null && !deepLinkHandled) {
            deepLinkHandled = true
            // Navigate to course detail after a short delay to ensure NavHost is ready
            kotlinx.coroutines.delay(300)
            navController.navigateToCourseDetail(deepLinkCourseId)
        }
    }

    // SearchView tap-target callbacks reach every top-level scaffold via this
    // CompositionLocal so we don't thread the same 4 lambdas through each
    // 2-pane wrapper. Defined once around the NavHost so all routes share them.
    val searchNavCallbacks = com.starception.submission.ui.SearchNavCallbacks(
        onTopicClick = navController::navigateToTopic,
        onNewsClick = { userNewsResource ->
            // Route by article type like the For You feed does: surah articles
            // open the Surah reader, hadith articles the Hadith detail; only
            // actual dua articles fall through to the Dua detail pager.
            val surahNumber = com.starception.submission.core.ui.extractSurahNumber(
                title = userNewsResource.title,
                url = userNewsResource.url,
                type = userNewsResource.type,
            )
            val hadithInfo = com.starception.submission.core.ui.extractHadithInfo(userNewsResource.url)
            when {
                surahNumber != null -> {
                    navController.navigateToSurah(surahNumber, userNewsResource.id)
                }
                hadithInfo != null -> {
                    val (databaseFile, hadithNumber) = hadithInfo
                    val collectionName = databaseFile.removeSuffix(".db")
                        .replace("_", " ")
                        .split(" ")
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    navController.navigateToHadithDetail(collectionName, hadithNumber, databaseFile)
                }
                else -> {
                    val duaNumber = Regex("Dua (\\d+)").find(userNewsResource.title)
                        ?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("#(\\d+)").find(userNewsResource.title)
                            ?.groupValues?.get(1)?.toIntOrNull()
                        ?: 1
                    navController.navigateToDuaDetail(
                        title = userNewsResource.title,
                        content = userNewsResource.content,
                        quranReference = null,
                        duaNumber = duaNumber,
                        newsResourceId = userNewsResource.id,
                        topicId = "",
                    )
                }
            }
        },
        onFortressDuaClick = { dua ->
            // Pass title in the "{Chapter}: Dua N" format the pager's title-match
            // fallback recognises — otherwise the screen would treat dua.position
            // as a Quranic-Dua page index and open the wrong invocation.
            val chapter = dua.chapterTitle.ifBlank { "Dua" }
            navController.navigateToDuaDetail(
                title = "$chapter: Dua ${dua.position}",
                content = dua.translation ?: dua.transliteration ?: "",
                quranReference = null,
                duaNumber = dua.position,
                newsResourceId = "",
                topicId = "",
            )
        },
        onQuranicDuaClick = { dua ->
            navController.navigateToDuaDetail(
                title = "Dua ${dua.duaNumber}: ${dua.title}",
                content = dua.translation ?: dua.explanation ?: "",
                quranReference = dua.surahReference,
                duaNumber = dua.duaNumber,
                newsResourceId = "",
                topicId = "",
            )
        },
        onVerseClick = { surah, ayah ->
            navController.navigateToSurah(surahNumber = surah, scrollToAyah = ayah)
        },
    )

    androidx.compose.runtime.CompositionLocalProvider(
        com.starception.submission.ui.LocalSearchNavCallbacks provides searchNavCallbacks,
    ) {
    NavHost(
        navController = navController,
        startDestination = PrayerTimesRoute,
        modifier = modifier,
        enterTransition = { NiaTransitions.fadeThroughEnter() },
        exitTransition = { NiaTransitions.fadeThroughExit() },
        popEnterTransition = { NiaTransitions.fadeThroughEnter() },
        popExitTransition = { NiaTransitions.fadeThroughExit() },
    ) {
        // ForYou two-pane layout (similar to Interests)
        forYouListDetailScreen(
            titleRes = TopLevelDestination.FOR_YOU.titleTextId,
            onSearchClick = { appState.navigateToSearch() },
            onSearchSubmit = { query -> appState.navigateToSearch(query) },
            onSettingsClick = onTopAppBarActionClick,
            onTopicClick = navController::navigateToTopic,
            onSurahClick = { surahNumber, newsResourceId -> navController.navigateToSurah(surahNumber, newsResourceId) },
            onDuaClick = { userNewsResource, topicId ->
                // Extract dua number from title (e.g., "Quranic Dua 1:" or "Quranic Dua #1")
                val duaNumber = Regex("Dua (\\d+)").find(userNewsResource.title)
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("#(\\d+)").find(userNewsResource.title)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1
                navController.navigateToDuaDetail(
                    title = userNewsResource.title,
                    content = userNewsResource.content,
                    quranReference = null,
                    duaNumber = duaNumber,
                    newsResourceId = userNewsResource.id,
                    topicId = topicId
                )
            },
            onHadithClick = { databaseFile, hadithNumber ->
                val collectionName = databaseFile.removeSuffix(".db")
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                navController.navigateToHadithDetail(collectionName, hadithNumber, databaseFile)
            },
            // Empty For You feed → switch to the Interests tab to follow topics.
            onBrowseTopicsClick = { appState.navigateToTopLevelDestination(INTERESTS) },
        )
        // Topic screen for ForYou
        topicScreen(
            showBackButton = true,
            onBackClick = navController::popBackStack,
            onTopicClick = navController::navigateToTopic,
            onSurahClick = { surahNumber, newsResourceId -> navController.navigateToSurah(surahNumber, newsResourceId) },
            onDuaClick = { userNewsResource, topicId ->
                val duaNumber = Regex("#(\\d+)").find(userNewsResource.title)
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("Dua (\\d+)").find(userNewsResource.title)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1
                navController.navigateToDuaDetail(
                    title = userNewsResource.title,
                    content = userNewsResource.content,
                    quranReference = null,
                    duaNumber = duaNumber,
                    newsResourceId = userNewsResource.id,
                    topicId = topicId
                )
            },
            onHadithClick = { databaseFile, hadithNumber ->
                val collectionName = databaseFile.removeSuffix(".db")
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                navController.navigateToHadithDetail(collectionName, hadithNumber, databaseFile)
            },
            // Show a download card under the header for content-backed topics (Quran/Bukhari)
            // whose database isn't downloaded yet.
            belowHeaderContent = { topicName ->
                com.starception.submission.download.TopicMissingContentCard(topicName)
            },
        )
        // Dua detail screen for ForYou
        duaDetailScreen(
            onBackClick = navController::popBackStack,
            onNavigateToSurah = { surahNumber, ayahNumber ->
                navController.navigateToSurah(surahNumber, scrollToAyah = ayahNumber)
            },
            isBookmarked = { newsResourceId ->
                mainViewModel?.isNewsResourceBookmarked(newsResourceId) ?: false
            },
            onToggleBookmark = { newsResourceId ->
                mainViewModel?.toggleNewsResourceBookmark(newsResourceId)
            },
            onTopicClick = navController::navigateToTopic,
            onHadithClick = { collectionName, hadithNumber, databaseFile ->
                navController.navigateToHadithDetail(collectionName, hadithNumber, databaseFile)
            }
        )
        // Bookmarks two-pane layout (similar to ForYou)
        bookmarksListDetailScreen(
            titleRes = TopLevelDestination.BOOKMARKS.titleTextId,
            onSearchClick = { appState.navigateToSearch() },
            onSearchSubmit = { query -> appState.navigateToSearch(query) },
            onSettingsClick = onTopAppBarActionClick,
            onTopicClick = navController::navigateToTopic,
            onShowSnackbar = onShowSnackbar,
            onSurahClick = { surahNumber, newsResourceId -> navController.navigateToSurah(surahNumber, newsResourceId) },
            onDuaClick = { userNewsResource, topicId ->
                // Extract dua number from title (e.g., "Quranic Dua 1:" or "Quranic Dua #1")
                val duaNumber = Regex("Dua (\\d+)").find(userNewsResource.title)
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("#(\\d+)").find(userNewsResource.title)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1
                navController.navigateToDuaDetail(
                    title = userNewsResource.title,
                    content = userNewsResource.content,
                    quranReference = null,
                    duaNumber = duaNumber,
                    newsResourceId = userNewsResource.id,
                    topicId = topicId
                )
            },
            onHadithClick = { databaseFile, hadithNumber ->
                val collectionName = databaseFile.removeSuffix(".db")
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                navController.navigateToHadithDetail(collectionName, hadithNumber, databaseFile)
            },
        )
        searchScreen(
            onBackClick = navController::popBackStack,
            onInterestsClick = { appState.navigateToTopLevelDestination(INTERESTS) },
            onTopicClick = navController::navigateToTopic,
            onSurahClick = { surahNumber, newsResourceId -> navController.navigateToSurah(surahNumber, newsResourceId) },
            onDuaClick = { userNewsResource ->
                val duaNumber = Regex("#(\\d+)").find(userNewsResource.title)
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("Dua (\\d+)").find(userNewsResource.title)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1
                navController.navigateToDuaDetail(
                    title = userNewsResource.title,
                    content = userNewsResource.content,
                    quranReference = null,
                    duaNumber = duaNumber,
                    newsResourceId = userNewsResource.id,
                )
            },
            onNoteClick = { surahNumber, ayahNumber ->
                navController.navigateToSurah(surahNumber, scrollToAyah = ayahNumber)
            },
            searchNotes = { query ->
                quranRepository.searchNotes(query).map { note ->
                    SearchNote(
                        id = note.id,
                        surahNumber = note.surahNumber,
                        ayahNumber = note.ayahNumber,
                        noteText = note.noteText,
                        updatedAt = note.updatedAt
                    )
                }
            },
        )
        interestsListDetailScreen(
            titleRes = TopLevelDestination.INTERESTS.titleTextId,
            onSearchClick = { appState.navigateToSearch() },
            onSearchSubmit = { query -> appState.navigateToSearch(query) },
            onSettingsClick = onTopAppBarActionClick,
            onSurahClick = { surahNumber, newsResourceId -> navController.navigateToSurah(surahNumber, newsResourceId) },
            onDuaClick = { userNewsResource, topicId ->
                val duaNumber = Regex("#(\\d+)").find(userNewsResource.title)
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("Dua (\\d+)").find(userNewsResource.title)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1
                navController.navigateToDuaDetail(
                    title = userNewsResource.title,
                    content = userNewsResource.content,
                    quranReference = null,
                    duaNumber = duaNumber,
                    newsResourceId = userNewsResource.id,
                    topicId = topicId
                )
            },
            onHadithClick = { databaseFile, hadithNumber ->
                val collectionName = databaseFile.removeSuffix(".db")
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                navController.navigateToHadithDetail(collectionName, hadithNumber, databaseFile)
            },
        )
        prayerTimesScreen(
            onSearchClick = { appState.navigateToSearch() },
            onSearchSubmit = { query -> appState.navigateToSearch(query) },
            onSettingsClick = onTopAppBarActionClick,
            onSurahClick = { surahNumber -> navController.navigateToSurah(surahNumber, null) },
            onSurahClickWithAyah = { surahNumber, ayahNumber -> navController.navigateToSurah(surahNumber, scrollToAyah = ayahNumber) },
            onMediaSourceClick = { source -> navController.navigateToMediaSourceDetail(source) },
            downloadProgress = homeDownloadProgress,
            downloadLabel = homeDownloadLabel,
            mediaState = homeMediaState,
            onMediaAction = { action -> mainViewModel?.globalMedia?.handleAction(action) },
            isTtsPreparing = homeTtsPreparing,
            onPrayerAlertChanged = { state -> mainViewModel?.updatePrayerAlert(state) },
            prayerAlertOverride = homePrayerAlertOverride,
            isSyncingExternal = homeIsSyncing,
            onSetSyncing = { syncing -> mainViewModel?.setSyncing(syncing) },
        )
        courseScreen(
            titleRes = TopLevelDestination.COURSE.titleTextId,
            onSearchClick = { appState.navigateToSearch() },
            onSearchSubmit = { query -> appState.navigateToSearch(query) },
            onSettingsClick = onTopAppBarActionClick,
            onSurahClick = { surahNumber -> navController.navigateToSurah(surahNumber, null) },
            onHadithClick = { databaseFile, hadithNumber ->
                val collectionName = databaseFile.removeSuffix(".db")
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                navController.navigateToHadithDetail(collectionName, hadithNumber, databaseFile)
            },
            onCourseClick = { courseId ->
                navController.navigateToCourseDetail(courseId)
            },
            onBackClick = navController::popBackStack,
        )
        // Surah screen accessible from Prayer Times (Noble Quran tile)
        surahScreen(
            onBackClick = navController::popBackStack,
            onTopicClick = navController::navigateToTopic,
            onNavigateToPreviousSurah = { currentSurahNumber ->
                if (currentSurahNumber > 1) {
                    navController.popBackStack()
                    navController.navigateToSurah(currentSurahNumber - 1, null)
                }
            },
            onNavigateToNextSurah = { currentSurahNumber ->
                if (currentSurahNumber < 114) {
                    navController.popBackStack()
                    navController.navigateToSurah(currentSurahNumber + 1, null)
                }
            }
        )
        // Unified Settings screen
        settingsScreen(
            onBackClick = navController::popBackStack,
            onNavigateToSalahDataCollection = navController::navigateToSalahDataCollection
        )
        // Salah data collection screen (developer tool)
        salahDataCollectionScreen(
            onBackClick = navController::popBackStack,
            onNavigateToLiveRecording = navController::navigateToSalahLiveRecording,
            onNavigateToReview = { filePath ->
                navController.navigateToSalahPrayerReview(filePath)
            }
        )
        // Live prayer recording screen
        salahLiveRecordingScreen(
            onNavigateToReview = { filePath ->
                navController.navigateToSalahPrayerReview(filePath)
            },
            onBackClick = navController::popBackStack
        )
        // Prayer review & labeling screen
        salahPrayerReviewScreen(
            onBackClick = navController::popBackStack
        )
        // Hadith detail screen
        hadithDetailScreen(
            onBackClick = navController::popBackStack,
            onNavigateToPreviousHadith = { collectionName, currentHadithNumber, databaseFile ->
                if (currentHadithNumber > 1) {
                    // Replace the current hadith entry in a single navigate call so only
                    // the forward enter/exit transition plays (no chained pop+push jank).
                    navController.navigateToHadithDetail(
                        collectionName, currentHadithNumber - 1, databaseFile,
                        navOptions = androidx.navigation.navOptions {
                            popUpTo<com.starception.submission.feature.hadith.HadithDetailRoute> {
                                inclusive = true
                            }
                        },
                    )
                }
            },
            onNavigateToNextHadith = { collectionName, currentHadithNumber, databaseFile ->
                navController.navigateToHadithDetail(
                    collectionName, currentHadithNumber + 1, databaseFile,
                    navOptions = androidx.navigation.navOptions {
                        popUpTo<com.starception.submission.feature.hadith.HadithDetailRoute> {
                            inclusive = true
                        }
                    },
                )
            }
        )
    }
    }
}

/**
 * Opens the detail page for whatever the media mini-bar is playing — Quran
 * surah, hadith TTS, or a Fortress dua recitation. Shared by the app-level
 * PullToSyncContainer (NiaApp) and the Home screen's inner container.
 */
fun androidx.navigation.NavController.navigateToMediaSourceDetail(
    source: com.starception.submission.media.MediaSource,
) {
    when (source) {
        is com.starception.submission.media.MediaSource.Quran -> {
            navigateToSurah(source.surahIndex + 1)
        }
        is com.starception.submission.media.MediaSource.Hadith -> {
            // Only Bukhari flows through hadith TTS playback today.
            navigateToHadithDetail(
                collectionName = source.collectionName,
                hadithNumber = source.hadithNumber,
                databaseFile = "sahih_bukhari.db",
            )
        }
        is com.starception.submission.media.MediaSource.Fortress -> {
            // Title is the "{Chapter}: Dua N" format the DuaDetail pager's
            // title-match fallback recognizes.
            val duaNumber = Regex("Dua (\\d+)").find(source.title)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 1
            navigateToDuaDetail(
                title = source.title,
                content = "",
                quranReference = null,
                duaNumber = duaNumber,
                newsResourceId = "",
                topicId = "",
            )
        }
        else -> Unit
    }
}
