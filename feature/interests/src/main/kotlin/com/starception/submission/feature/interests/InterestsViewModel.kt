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

package com.starception.submission.feature.interests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.core.domain.GetFollowableTopicsUseCase
import com.starception.submission.core.domain.TopicSortField
import com.starception.submission.core.model.data.FollowableTopic
import com.starception.submission.feature.interests.navigation.InterestsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InterestsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    val userDataRepository: UserDataRepository,
    getFollowableTopics: GetFollowableTopicsUseCase,
) : ViewModel() {

    // Key used to save and retrieve the currently selected topic id from saved state.
    private val selectedTopicIdKey = "selectedTopicIdKey"

    private val interestsRoute: InterestsRoute = savedStateHandle.toRoute()
    private val selectedTopicId = savedStateHandle.getStateFlow(
        key = selectedTopicIdKey,
        initialValue = interestsRoute.initialTopicId,
    )

    // Custom order from persistent DataStore
    private val customTopicOrder = userDataRepository.userData.map { it.topicOrder }

    val uiState: StateFlow<InterestsUiState> = combine(
        selectedTopicId,
        getFollowableTopics(sortBy = TopicSortField.NAME),
        customTopicOrder,
    ) { selectedId, topics, customOrder ->
        // Apply custom order if available, otherwise use default order
        val orderedTopics = if (customOrder.isNotEmpty()) {
            val orderMap = customOrder.withIndex().associate { it.value to it.index }
            topics.sortedBy { orderMap[it.topic.id] ?: Int.MAX_VALUE }
        } else {
            topics
        }
        InterestsUiState.Interests(selectedId, orderedTopics)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InterestsUiState.Loading,
    )

    fun followTopic(followedTopicId: String, followed: Boolean) {
        viewModelScope.launch {
            userDataRepository.setTopicIdFollowed(followedTopicId, followed)
        }
    }

    fun onTopicClick(topicId: String?) {
        savedStateHandle[selectedTopicIdKey] = topicId
    }

    /**
     * Sets the topic order directly from a list of topic IDs.
     * Called when drag-and-drop reordering is complete.
     */
    fun setTopicOrder(newOrder: List<String>) {
        viewModelScope.launch {
            userDataRepository.setTopicOrder(newOrder)
        }
    }
}

sealed interface InterestsUiState {
    data object Loading : InterestsUiState

    data class Interests(
        val selectedTopicId: String?,
        val topics: List<FollowableTopic>,
    ) : InterestsUiState

    data object Empty : InterestsUiState
}
