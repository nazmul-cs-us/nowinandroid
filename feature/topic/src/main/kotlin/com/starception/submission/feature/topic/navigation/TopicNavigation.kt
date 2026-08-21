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

package com.starception.submission.feature.topic.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.starception.submission.core.designsystem.animation.NiaTransitions
import com.starception.submission.core.model.data.UserNewsResource
import com.starception.submission.feature.topic.TopicScreen
import com.starception.submission.feature.topic.TopicViewModel
import kotlinx.serialization.Serializable

@Serializable data class TopicRoute(val id: String)

fun NavController.navigateToTopic(topicId: String, navOptions: NavOptionsBuilder.() -> Unit = {}) {
    navigate(route = TopicRoute(topicId)) {
        navOptions()
    }
}

fun NavGraphBuilder.topicScreen(
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    onTopicClick: (String) -> Unit,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource, String) -> Unit = { _, _ -> },
    onHadithClick: (String, Int) -> Unit = { _, _ -> },
    onBukhariBookClick: (Int) -> Unit = {},
    onBukhariBookPlayClick: (Int) -> Unit = {},
    belowHeaderContent: @Composable (topicName: String) -> Unit = {},
) {
    composable<TopicRoute>(
        enterTransition = { NiaTransitions.detailEnter() },
        exitTransition = { NiaTransitions.detailExit() },
        popEnterTransition = { NiaTransitions.detailPopEnter() },
        popExitTransition = { NiaTransitions.detailPopExit() },
    ) { entry ->
        val id = entry.toRoute<TopicRoute>().id
        TopicScreen(
            showBackButton = showBackButton,
            onBackClick = onBackClick,
            onTopicClick = onTopicClick,
            onSurahClick = onSurahClick,
            onDuaClick = { userNewsResource -> onDuaClick(userNewsResource, id) },
            onHadithClick = onHadithClick,
            onBukhariBookClick = onBukhariBookClick,
            onBukhariBookPlayClick = onBukhariBookPlayClick,
            belowHeaderContent = belowHeaderContent,
            viewModel = hiltViewModel<TopicViewModel, TopicViewModel.Factory>(
                key = id,
            ) { factory ->
                factory.create(id)
            },
        )
    }
}
