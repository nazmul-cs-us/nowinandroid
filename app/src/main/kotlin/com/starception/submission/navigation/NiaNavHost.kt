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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import com.starception.submission.core.qurandatabase.QuranRepository
import com.starception.submission.feature.search.SearchNote
import androidx.navigation.compose.composable
import com.starception.submission.MainActivityViewModel
import com.starception.submission.feature.bookmarks.navigation.bookmarksSection
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
import com.starception.submission.feature.dua.duaDetailScreen
import com.starception.submission.feature.dua.navigateToDuaDetail
import com.starception.submission.feature.hadith.hadithDetailScreen
import com.starception.submission.feature.hadith.navigateToHadithDetail
import com.starception.submission.navigation.TopLevelDestination.INTERESTS
import com.starception.submission.settings.navigation.settingsScreen
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
    mainViewModel: MainActivityViewModel? = null,
) {
    val navController = appState.navController
    val context = LocalContext.current
    val quranRepository = remember { QuranRepository(context) }
    NavHost(
        navController = navController,
        startDestination = PrayerTimesRoute,
        modifier = modifier,
    ) {
        forYouSection(
            onTopicClick = navController::navigateToTopic,
            onSurahClick = { surahNumber, newsResourceId -> navController.navigateToSurah(surahNumber, newsResourceId) },
            onDuaClick = { userNewsResource ->
                // Extract dua number from title (e.g., "Quranic Dua 1:" or "Quranic Dua #1")
                val duaNumber = Regex("Dua (\\d+)").find(userNewsResource.title)
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("#(\\d+)").find(userNewsResource.title)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1
                // Get the first topic ID to show correct "Dua X of Y" count
                val topicId = userNewsResource.followableTopics.firstOrNull()?.topic?.id ?: ""
                navController.navigateToDuaDetail(
                    title = userNewsResource.title,
                    content = userNewsResource.content,
                    quranReference = null,
                    duaNumber = duaNumber,
                    newsResourceId = userNewsResource.id,
                    topicId = topicId
                )
            },
        ) {
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
            )
            // Surah screen nested within For You section
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
            // Dua detail screen nested within For You section
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
        }
        bookmarksSection(
            onTopicClick = navController::navigateToTopic,
            onShowSnackbar = onShowSnackbar,
            onSurahClick = { surahNumber, newsResourceId -> navController.navigateToSurah(surahNumber, newsResourceId) },
            onDuaClick = { userNewsResource ->
                // Extract dua number from title (e.g., "Quranic Dua 1:" or "Quranic Dua #1")
                val duaNumber = Regex("Dua (\\d+)").find(userNewsResource.title)
                    ?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("#(\\d+)").find(userNewsResource.title)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1
                // Get the first topic ID to show correct "Dua X of Y" count
                val topicId = userNewsResource.followableTopics.firstOrNull()?.topic?.id ?: ""
                navController.navigateToDuaDetail(
                    title = userNewsResource.title,
                    content = userNewsResource.content,
                    quranReference = null,
                    duaNumber = duaNumber,
                    newsResourceId = userNewsResource.id,
                    topicId = topicId
                )
            },
        ) {
            // Surah screen nested within Bookmarks section
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
            // Dua detail screen nested within Bookmarks section
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
        }
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
        )
        prayerTimesScreen(
            onSurahClick = { surahNumber -> navController.navigateToSurah(surahNumber, null) },
            onSurahClickWithAyah = { surahNumber, ayahNumber -> navController.navigateToSurah(surahNumber, scrollToAyah = ayahNumber) }
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
