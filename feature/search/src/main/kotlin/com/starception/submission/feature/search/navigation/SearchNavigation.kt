/*
 * Copyright 2023 The Android Open Source Project
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

package com.starception.submission.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.starception.submission.core.designsystem.animation.NiaTransitions
import com.starception.submission.core.model.data.UserNewsResource
import com.starception.submission.feature.search.SearchNote
import com.starception.submission.feature.search.SearchRoute
import kotlinx.serialization.Serializable

@Serializable data class SearchRoute(val initialQuery: String? = null)

fun NavController.navigateToSearch(
    initialQuery: String? = null,
    navOptions: NavOptions? = null,
) = navigate(SearchRoute(initialQuery), navOptions)

fun NavGraphBuilder.searchScreen(
    onBackClick: () -> Unit,
    onInterestsClick: () -> Unit,
    onTopicClick: (String) -> Unit,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource) -> Unit = { _ -> },
    onNoteClick: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    searchNotes: suspend (String) -> List<SearchNote> = { emptyList() },
) {
    // TODO: Handle back stack for each top-level destination. At the moment each top-level
    // destination may have own search screen's back stack.
    composable<SearchRoute>(
        enterTransition = { NiaTransitions.slideUpEnter() },
        exitTransition = { NiaTransitions.fadeThroughExit() },
        popEnterTransition = { NiaTransitions.fadeThroughEnter() },
        popExitTransition = { NiaTransitions.slideDownExit() },
    ) { backStackEntry ->
        val route = backStackEntry.toRoute<SearchRoute>()
        SearchRoute(
            onBackClick = onBackClick,
            onInterestsClick = onInterestsClick,
            onTopicClick = onTopicClick,
            onSurahClick = onSurahClick,
            onDuaClick = onDuaClick,
            onNoteClick = onNoteClick,
            searchNotes = searchNotes,
            initialQuery = route.initialQuery,
        )
    }
}
