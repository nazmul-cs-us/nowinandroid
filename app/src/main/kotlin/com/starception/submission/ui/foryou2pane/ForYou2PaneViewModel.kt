/*
 * Copyright 2024 The Android Open Source Project
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

package com.starception.submission.ui.foryou2pane

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.core.data.repository.UserNewsResourceRepository
import com.starception.submission.core.model.data.UserNewsResource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForYou2PaneViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val userNewsResourceRepository: UserNewsResourceRepository,
    private val userDataRepository: UserDataRepository,
) : ViewModel() {

    private val _selectedNewsResourceId = MutableStateFlow<String?>(null)
    val selectedNewsResourceId: StateFlow<String?> = _selectedNewsResourceId

    // Get the selected news resource with bookmark state
    val selectedNewsResource: StateFlow<UserNewsResource?> = combine(
        _selectedNewsResourceId,
        userNewsResourceRepository.observeAll(),
    ) { selectedId, newsResources ->
        selectedId?.let { id ->
            newsResources.find { it.id == id }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun onNewsResourceClick(newsResourceId: String) {
        _selectedNewsResourceId.value = newsResourceId
    }

    fun clearSelection() {
        _selectedNewsResourceId.value = null
    }

    fun toggleBookmark(newsResourceId: String) {
        viewModelScope.launch {
            userDataRepository.setNewsResourceBookmarked(
                newsResourceId = newsResourceId,
                bookmarked = selectedNewsResource.value?.isSaved != true,
            )
        }
    }
}
