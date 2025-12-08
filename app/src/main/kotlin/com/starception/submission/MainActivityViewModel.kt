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

package com.starception.submission

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.MainActivityUiState.Loading
import com.starception.submission.MainActivityUiState.Success
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.core.model.data.UserData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
) : ViewModel() {
    // Start with default state but load real data immediately (no splash blocking)
    private val _uiState = MutableStateFlow<MainActivityUiState>(Success(
        UserData(
            bookmarkedNewsResources = emptySet(),
            viewedNewsResources = emptySet(),
            followedTopics = emptySet(),
            themeBrand = ThemeBrand.DEFAULT,
            darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
            useDynamicColor = true,
            shouldHideOnboarding = false,
        )
    ))
    val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()
    
    init {
        // Load user preferences in background WITHOUT blocking app startup
        viewModelScope.launch {
            try {
                Log.d("MainActivityViewModel", "Loading user data for theme preferences")
                userDataRepository.userData.collect { userData ->
                    _uiState.value = Success(userData)
                    Log.d("MainActivityViewModel", "Theme updated: ${userData.darkThemeConfig}")
                }
            } catch (e: Exception) {
                Log.e("MainActivityViewModel", "Error loading user data, keeping defaults", e)
                // Keep default data on error
            }
        }
    }

    /**
     * Check if a news resource is bookmarked
     */
    fun isNewsResourceBookmarked(newsResourceId: String): Boolean {
        val currentState = _uiState.value
        return if (currentState is Success) {
            newsResourceId in currentState.userData.bookmarkedNewsResources
        } else {
            false
        }
    }

    /**
     * Toggle bookmark state for a news resource
     */
    fun toggleNewsResourceBookmark(newsResourceId: String) {
        viewModelScope.launch {
            val isCurrentlyBookmarked = isNewsResourceBookmarked(newsResourceId)
            userDataRepository.setNewsResourceBookmarked(newsResourceId, !isCurrentlyBookmarked)
        }
    }
}

sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState

    data class Success(val userData: UserData) : MainActivityUiState {
        override val shouldDisableDynamicTheming = !userData.useDynamicColor

        override val themeBrand: ThemeBrand = userData.themeBrand

        override fun shouldUseDarkTheme(isSystemDarkTheme: Boolean) =
            when (userData.darkThemeConfig) {
                DarkThemeConfig.FOLLOW_SYSTEM -> isSystemDarkTheme
                DarkThemeConfig.LIGHT -> false
                DarkThemeConfig.DARK -> true
            }
    }

    /**
     * Returns `true` if the state wasn't loaded yet and it should keep showing the splash screen.
     */
    fun shouldKeepSplashScreen() = this is Loading

    /**
     * Returns `true` if the dynamic color is disabled.
     */
    val shouldDisableDynamicTheming: Boolean get() = true

    /**
     * Returns the theme brand to be used.
     */
    val themeBrand: ThemeBrand get() = ThemeBrand.DEFAULT

    /**
     * Returns `true` if dark theme should be used.
     */
    fun shouldUseDarkTheme(isSystemDarkTheme: Boolean) = isSystemDarkTheme
}
