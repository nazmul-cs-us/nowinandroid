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

package com.starception.submission.feature.bookmarks.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.starception.submission.feature.bookmarks.BookmarksRoute
import kotlinx.serialization.Serializable

@Serializable object BookmarksRoute

@Serializable object BookmarksBaseRoute // route to base navigation graph

fun NavController.navigateToBookmarks(navOptions: NavOptions) =
    navigate(route = BookmarksRoute, navOptions)

/**
 * The Bookmarks section of the app. It can also display Surah content.
 * This uses a nested navigation graph to maintain tab selection when viewing Surahs.
 *
 * @param onTopicClick - Called when a topic is clicked, contains the ID of the topic
 * @param onShowSnackbar - Shows snackbar for undo bookmark removal
 * @param onSurahClick - Called when a Surah news item is clicked, contains the Surah number and news resource ID
 * @param onNewsClick - Called when a news item is clicked
 * @param surahDestination - Destination for surah content
 */
fun NavGraphBuilder.bookmarksSection(
    onTopicClick: (String) -> Unit,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onNewsClick: ((com.starception.submission.core.model.data.UserNewsResource) -> Unit)? = null,
    surahDestination: NavGraphBuilder.() -> Unit,
) {
    navigation<BookmarksBaseRoute>(startDestination = BookmarksRoute) {
        composable<BookmarksRoute> {
            BookmarksRoute(onTopicClick, onShowSnackbar, onSurahClick, onNewsClick)
        }
        surahDestination()
    }
}

// Keep old method for backward compatibility
fun NavGraphBuilder.bookmarksScreen(
    onTopicClick: (String) -> Unit,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onNewsClick: ((com.starception.submission.core.model.data.UserNewsResource) -> Unit)? = null,
) {
    composable<BookmarksRoute> {
        BookmarksRoute(onTopicClick, onShowSnackbar, onSurahClick, onNewsClick)
    }
}
