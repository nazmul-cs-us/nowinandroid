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

package com.starception.submission.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.feature.settings.SettingsUiState.Loading
import com.starception.submission.feature.settings.SettingsUiState.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * SETTINGS VIEW MODEL: Manages app theme and visual preference settings
 * 
 * This ViewModel handles all user-configurable app settings including theme selection,
 * dark mode preferences, and dynamic color options. It provides a reactive interface
 * between the settings UI and the underlying user data repository.
 * 
 * KEY RESPONSIBILITIES:
 * - Theme brand selection (Default, Android, etc.)
 * - Dark theme configuration (Follow System, Light, Dark)
 * - Dynamic color preferences (Material You support)
 * - Settings state management with loading states
 * 
 * ARCHITECTURE PATTERN:
 * Uses MVVM with reactive programming:
 * - StateFlow for UI state management
 * - Maps UserData to UI-specific settings model
 * - Suspend functions for async preference updates
 * - Hilt dependency injection
 * 
 * UI STATE MANAGEMENT:
 * - Loading: Initial state while fetching preferences
 * - Success: Contains current settings ready for display
 * - Automatic UI updates when preferences change
 * 
 * PERFORMANCE:
 * - WhileSubscribed with 5-second timeout for efficient resource management
 * - Only active while UI is subscribed
 * - Prevents memory leaks from inactive subscriptions
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
) : ViewModel() {
    val settingsUiState: StateFlow<SettingsUiState> =
        userDataRepository.userData
            .map { userData ->
                Success(
                    settings = UserEditableSettings(
                        brand = userData.themeBrand,
                        useDynamicColor = userData.useDynamicColor,
                        darkThemeConfig = userData.darkThemeConfig,
                    ),
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = WhileSubscribed(5.seconds.inWholeMilliseconds),
                initialValue = Loading,
            )

    /**
     * THEME BRAND UPDATE: Changes the app's visual branding theme
     * 
     * Updates the overall color scheme and visual branding of the app.
     * Change takes effect immediately throughout the app.
     * 
     * @param themeBrand The new theme brand to apply
     */
    fun updateThemeBrand(themeBrand: ThemeBrand) {
        Log.d("SettingsViewModel", "updateThemeBrand called with: $themeBrand")
        viewModelScope.launch {
            userDataRepository.setThemeBrand(themeBrand)
            Log.d("SettingsViewModel", "setThemeBrand completed for: $themeBrand")
        }
    }

    /**
     * DARK THEME CONFIG UPDATE: Changes dark mode behavior
     * 
     * Controls how the app handles dark theme:
     * - FOLLOW_SYSTEM: Follow device dark mode setting
     * - LIGHT: Always use light theme
     * - DARK: Always use dark theme
     * 
     * @param darkThemeConfig The dark theme configuration to apply
     */
    fun updateDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        Log.d("SettingsViewModel", "updateDarkThemeConfig called with: $darkThemeConfig")
        viewModelScope.launch {
            userDataRepository.setDarkThemeConfig(darkThemeConfig)
            Log.d("SettingsViewModel", "setDarkThemeConfig completed for: $darkThemeConfig")
        }
    }

    /**
     * DYNAMIC COLOR PREFERENCE UPDATE: Enable/disable Material You colors
     * 
     * Controls whether the app uses dynamic theming based on wallpaper colors.
     * Only effective on Android 12+ devices with Material You support.
     * 
     * @param useDynamicColor True to enable dynamic colors, false for static colors
     */
    fun updateDynamicColorPreference(useDynamicColor: Boolean) {
        viewModelScope.launch {
            userDataRepository.setDynamicColorPreference(useDynamicColor)
        }
    }
}

/**
 * USER EDITABLE SETTINGS: UI-focused model for user-configurable preferences
 * 
 * This data class represents the subset of user preferences that are directly
 * editable through the settings screen. It's a UI-specific projection of UserData
 * that focuses only on visual and theme-related settings.
 * 
 * PURPOSE:
 * - Separates UI concerns from complete user data model
 * - Provides clean interface for settings screen
 * - Excludes read-only or non-theme settings
 * 
 * SETTINGS INCLUDED:
 * - Theme branding selection
 * - Dynamic color preference (Material You)
 * - Dark theme configuration
 * 
 * @param brand Selected theme branding (Default, Android, etc.)
 * @param useDynamicColor Whether to use wallpaper-based colors (Android 12+)
 * @param darkThemeConfig Dark mode setting (Follow System, Light, Dark)
 */
data class UserEditableSettings(
    val brand: ThemeBrand,
    val useDynamicColor: Boolean,
    val darkThemeConfig: DarkThemeConfig,
)

/**
 * SETTINGS UI STATE: Represents different states of the settings screen
 * 
 * This sealed interface defines the possible states of the settings UI,
 * enabling proper loading state management and error handling.
 * 
 * STATES:
 * - Loading: Initial state while fetching user preferences
 * - Success: Settings loaded and ready for display/editing
 * 
 * USAGE:
 * - Loading state: Show loading indicators or placeholders
 * - Success state: Display actual settings with edit capabilities
 */
sealed interface SettingsUiState {
    /**
     * LOADING STATE: Settings are being fetched from repository
     * 
     * Used during initial load while user preferences are retrieved
     * from persistent storage (DataStore/SharedPreferences).
     */
    data object Loading : SettingsUiState
    
    /**
     * SUCCESS STATE: Settings loaded and ready for display
     * 
     * Contains the current user preferences formatted for UI display.
     * All settings are editable through the provided update functions.
     * 
     * @param settings Current user-editable settings
     */
    data class Success(val settings: UserEditableSettings) : SettingsUiState
}
