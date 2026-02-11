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

import android.app.Application
import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private const val THEME_CACHE_PREFS = "theme_cache_prefs"
        private const val KEY_THEME_BRAND = "cached_theme_brand"
        private const val KEY_DARK_THEME_CONFIG = "cached_dark_theme_config"
        private const val KEY_USE_DYNAMIC_COLOR = "cached_use_dynamic_color"
    }

    // Load cached theme SYNCHRONOUSLY to prevent flash
    private val cachedTheme: UserData = loadCachedTheme()

    private val _uiState = MutableStateFlow<MainActivityUiState>(Success(cachedTheme))
    val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()

    /**
     * Load theme from SharedPreferences cache (SYNCHRONOUS - no flash!)
     */
    private fun loadCachedTheme(): UserData {
        val prefs = context.getSharedPreferences(THEME_CACHE_PREFS, Context.MODE_PRIVATE)

        val themeBrandOrdinal = prefs.getInt(KEY_THEME_BRAND, ThemeBrand.DEFAULT.ordinal)
        val darkThemeConfigOrdinal = prefs.getInt(KEY_DARK_THEME_CONFIG, DarkThemeConfig.FOLLOW_SYSTEM.ordinal)
        val useDynamicColor = prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, true)

        val themeBrand = ThemeBrand.entries.getOrElse(themeBrandOrdinal) { ThemeBrand.DEFAULT }
        val darkThemeConfig = DarkThemeConfig.entries.getOrElse(darkThemeConfigOrdinal) { DarkThemeConfig.FOLLOW_SYSTEM }

        Log.d("MainActivityViewModel", "📦 Loaded cached theme: brand=$themeBrand, darkConfig=$darkThemeConfig, dynamic=$useDynamicColor")

        return UserData(
            bookmarkedNewsResources = emptySet(),
            viewedNewsResources = emptySet(),
            followedTopics = emptySet(),
            themeBrand = themeBrand,
            darkThemeConfig = darkThemeConfig,
            useDynamicColor = useDynamicColor,
            shouldHideOnboarding = false,
        )
    }

    /**
     * Save theme to SharedPreferences cache for instant load on next startup
     */
    private fun cacheTheme(userData: UserData) {
        val prefs = context.getSharedPreferences(THEME_CACHE_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_THEME_BRAND, userData.themeBrand.ordinal)
            .putInt(KEY_DARK_THEME_CONFIG, userData.darkThemeConfig.ordinal)
            .putBoolean(KEY_USE_DYNAMIC_COLOR, userData.useDynamicColor)
            .apply()
        Log.d("MainActivityViewModel", "💾 Cached theme: brand=${userData.themeBrand}, darkConfig=${userData.darkThemeConfig}")
    }

    init {
        // Load user preferences in background and cache for next startup
        viewModelScope.launch {
            try {
                Log.d("MainActivityViewModel", "Loading user data for theme preferences")
                userDataRepository.userData.collect { userData ->
                    _uiState.value = Success(userData)
                    // Cache theme for instant load on next app startup
                    cacheTheme(userData)
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
