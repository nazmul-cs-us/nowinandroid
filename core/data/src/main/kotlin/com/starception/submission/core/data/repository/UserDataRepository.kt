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

package com.starception.submission.core.data.repository

import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.core.model.data.UserData
import kotlinx.coroutines.flow.Flow

/**
 * USER DATA REPOSITORY: Interface for managing user preferences and app state
 * 
 * This repository interface defines operations for managing user-specific data including:
 * - Content preferences (followed topics, bookmarked articles)
 * - App behavior (theme, onboarding status)
 * - Reading tracking (viewed articles)
 * 
 * ARCHITECTURE PATTERN:
 * Uses Repository pattern with reactive data flows:
 * - Flow-based reactive data streams for UI updates
 * - Suspend functions for async operations
 * - Interface-based design for testability and flexibility
 * 
 * DATA CATEGORIES:
 * 1. Content Preferences: Topics to follow, bookmarked articles
 * 2. Theme Settings: Dark mode, color schemes, dynamic colors
 * 3. Behavioral Data: Onboarding status, article view tracking
 * 
 * IMPLEMENTATION:
 * Typically implemented using:
 * - Proto DataStore for structured preferences
 * - Room database for complex data relationships
 * - SharedPreferences for simple key-value data
 * 
 * TESTING:
 * Interface design enables easy mocking and test doubles
 * for comprehensive unit and integration testing.
 */
interface UserDataRepository {

    /**
     * USER DATA STREAM: Reactive stream of all user preferences and state
     * 
     * This Flow provides real-time updates whenever any user data changes,
     * enabling reactive UI updates throughout the app.
     * 
     * EMITTED DATA:
     * - UserData object containing all user preferences
     * - Updates automatically when any preference changes
     * - Cold Flow that starts when collected
     * 
     * USE CASES:
     * - UI state management in ViewModels
     * - Theme application throughout app
     * - Content filtering based on preferences
     * 
     * @return Flow<UserData> stream of user preferences
     */
    val userData: Flow<UserData>

    /**
     * BULK TOPIC FOLLOWING: Sets complete list of followed topic IDs
     * 
     * This replaces the entire set of followed topics with the provided IDs.
     * Used for initial setup or bulk operations.
     * 
     * BEHAVIOR:
     * - Replaces existing followed topics (not additive)
     * - Empty set unfollows all topics
     * - Triggers userData Flow update
     * 
     * @param followedTopicIds Complete set of topic IDs to follow
     */
    suspend fun setFollowedTopicIds(followedTopicIds: Set<String>)

    /**
     * INDIVIDUAL TOPIC FOLLOWING: Follow or unfollow a single topic
     * 
     * This updates the follow status for one specific topic without
     * affecting other followed topics.
     * 
     * BEHAVIOR:
     * - Adds topic to followed set if followed=true
     * - Removes topic from followed set if followed=false
     * - Triggers userData Flow update
     * - Idempotent operation (safe to call repeatedly)
     * 
     * @param followedTopicId ID of the topic to update
     * @param followed True to follow, false to unfollow
     */
    suspend fun setTopicIdFollowed(followedTopicId: String, followed: Boolean)

    /**
     * BOOKMARK MANAGEMENT: Mark news article as bookmarked or unbookmarked
     * 
     * This manages the user's saved articles list, allowing them to bookmark
     * articles for later reading.
     * 
     * BEHAVIOR:
     * - Adds article to bookmarks if bookmarked=true
     * - Removes article from bookmarks if bookmarked=false
     * - Triggers userData Flow update
     * - Idempotent operation
     * 
     * UI INTEGRATION:
     * - Updates bookmark icons throughout app
     * - Shows in dedicated bookmarks screen
     * 
     * @param newsResourceId Unique ID of the news article
     * @param bookmarked True to bookmark, false to remove bookmark
     */
    suspend fun setNewsResourceBookmarked(newsResourceId: String, bookmarked: Boolean)

    /**
     * READ STATUS TRACKING: Mark news article as viewed/unviewed
     * 
     * This tracks which articles the user has read, enabling features like:
     * - Marking articles as read/unread
     * - Showing read status in lists
     * - Analytics on reading behavior
     * 
     * BEHAVIOR:
     * - Marks article as viewed if viewed=true
     * - Marks article as unread if viewed=false
     * - Triggers userData Flow update
     * - Usually called automatically when article is opened
     * 
     * @param newsResourceId Unique ID of the news article
     * @param viewed True if article was viewed, false otherwise
     */
    suspend fun setNewsResourceViewed(newsResourceId: String, viewed: Boolean)

    /**
     * THEME BRAND SELECTION: Sets the app's visual theme branding
     * 
     * This controls the overall color scheme and branding of the app,
     * allowing users to personalize their experience.
     * 
     * AVAILABLE OPTIONS:
     * - Default: Standard app theme
     * - Android: Android-branded colors
     * - Custom themes as defined in ThemeBrand enum
     * 
     * BEHAVIOR:
     * - Updates app theme immediately
     * - Persists across app restarts
     * - Triggers userData Flow update
     * 
     * @param themeBrand The theme brand to apply
     */
    suspend fun setThemeBrand(themeBrand: ThemeBrand)

    /**
     * DARK THEME CONFIGURATION: Controls dark mode behavior
     * 
     * This manages how the app handles dark theme, including automatic
     * system following and manual overrides.
     * 
     * AVAILABLE OPTIONS:
     * - FOLLOW_SYSTEM: Follow device dark mode setting
     * - LIGHT: Always use light theme
     * - DARK: Always use dark theme
     * 
     * BEHAVIOR:
     * - Updates app theme immediately
     * - Persists user preference
     * - Triggers userData Flow update
     * 
     * @param darkThemeConfig The dark theme configuration to apply
     */
    suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig)

    /**
     * DYNAMIC COLOR PREFERENCE: Enable/disable Material You dynamic colors
     * 
     * This controls whether the app uses dynamic theming based on the user's
     * wallpaper colors (Material You feature on Android 12+).
     * 
     * BEHAVIOR:
     * - Enables/disables wallpaper-based colors
     * - Falls back to static colors when disabled
     * - Only effective on Android 12+ devices
     * - Triggers userData Flow update
     * 
     * COMPATIBILITY:
     * - Android 12+: Full dynamic color support
     * - Older devices: No effect (uses static colors)
     * 
     * @param useDynamicColor True to enable dynamic colors, false for static
     */
    suspend fun setDynamicColorPreference(useDynamicColor: Boolean)

    /**
     * ONBOARDING STATUS: Mark onboarding as completed or reset
     * 
     * This controls whether the user sees the onboarding flow when opening the app.
     * Used for first-time user experience and feature introductions.
     * 
     * BEHAVIOR:
     * - true: User has seen onboarding, don't show again
     * - false: Show onboarding on next app launch
     * - Persists across app restarts
     * - Triggers userData Flow update
     * 
     * USE CASES:
     * - Hide onboarding after completion
     * - Reset onboarding for testing
     * - Show onboarding for major feature updates
     * 
     * @param shouldHideOnboarding True to hide onboarding, false to show
     */
    suspend fun setShouldHideOnboarding(shouldHideOnboarding: Boolean)
}
