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

package com.starception.submission.core.model.data

/**
 * USER DATA MODEL: Complete representation of user preferences and app state
 *
 * This data class contains all user-specific settings, preferences, and behavioral data
 * that persists across app sessions. It serves as the single source of truth for
 * personalizing the user experience.
 *
 * DATA CATEGORIES:
 * 1. Content Preferences: What topics to follow, articles bookmarked/viewed
 * 2. Theme Preferences: Visual appearance and color schemes
 * 3. App Behavior: Onboarding status and feature preferences
 *
 * IMMUTABILITY:
 * - Uses immutable data class pattern for predictable state management
 * - Changes create new instances rather than modifying existing ones
 * - Enables safe sharing across multiple UI components
 *
 * PERSISTENCE:
 * - Typically stored using Proto DataStore for type safety
 * - Automatically synchronized across app components
 * - Survives app restarts and device reboots
 *
 * REACTIVE UPDATES:
 * - Changes trigger Flow emissions in UserDataRepository
 * - UI automatically updates when preferences change
 * - Enables real-time personalization
 *
 * @param bookmarkedNewsResources Set of news article IDs that user has bookmarked
 * @param viewedNewsResources Set of news article IDs that user has viewed/read
 * @param followedTopics Set of topic IDs that user wants to see content for
 * @param themeBrand Selected theme branding (Default, Android, etc.)
 * @param darkThemeConfig Dark mode preference (Follow System, Light, Dark)
 * @param useDynamicColor Whether to use Material You dynamic colors (Android 12+)
 * @param shouldHideOnboarding Whether onboarding has been completed (hide on startup)
 * @param newsResourceLastOpenedTimes Map of news resource IDs to their last opened timestamp (epoch millis)
 */
data class UserData(
    val bookmarkedNewsResources: Set<String>,
    val viewedNewsResources: Set<String>,
    val followedTopics: Set<String>,
    val themeBrand: ThemeBrand,
    val darkThemeConfig: DarkThemeConfig,
    val useDynamicColor: Boolean,
    val shouldHideOnboarding: Boolean,
    val newsResourceLastOpenedTimes: Map<String, Long> = emptyMap(),
    val topicOrder: List<String> = emptyList(),
    /** ARGB int of the PRIMARY seed colour for ThemeBrand.CUSTOM; 0 = none chosen yet. */
    val customThemeColor: Int = 0,
    /** ARGB int of the SECONDARY seed colour for ThemeBrand.CUSTOM; 0 = none chosen yet. */
    val customSecondaryColor: Int = 0,
    /** ARGB int of the TERTIARY seed colour for ThemeBrand.CUSTOM; 0 = none chosen yet. */
    val customTertiaryColor: Int = 0,
)
