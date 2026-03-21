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

    NavHost(
        navController = navController,
        startDestination = PrayerTimesRoute,
        modifier = modifier,
    ) {
        // ForYou two-pane layout (similar to Interests)
        forYouListDetailScreen(
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
            onSettingsClick = onTopAppBarActionClick,
            onSurahClick = { surahNumber -> navController.navigateToSurah(surahNumber, null) },
            onSurahClickWithAyah = { surahNumber, ayahNumber -> navController.navigateToSurah(surahNumber, scrollToAyah = ayahNumber) },
            downloadProgress = homeDownloadProgress,
            downloadLabel = "Downloading content",
        )
        courseScreen(
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
            onNavigateToLiveRecording = navController::navigateToSalahLiveRecording
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
                    navController.popBackStack()
                    navController.navigateToHadithDetail(collectionName, currentHadithNumber - 1, databaseFile)
                }
            },
            onNavigateToNextHadith = { collectionName, currentHadithNumber, databaseFile ->
                navController.popBackStack()
                navController.navigateToHadithDetail(collectionName, currentHadithNumber + 1, databaseFile)
            }
        )
    }
}
