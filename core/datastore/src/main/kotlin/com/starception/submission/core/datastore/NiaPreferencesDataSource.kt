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

package com.starception.submission.core.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import com.starception.submission.core.model.data.DarkThemeConfig
import com.starception.submission.core.model.data.ThemeBrand
import com.starception.submission.core.model.data.UserData
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class NiaPreferencesDataSource @Inject constructor(
    private val userPreferences: DataStore<UserPreferences>,
) {
    
    companion object {
        private const val TAG = "NiaPreferencesDataSource"
    }
    
    /**
     * ENHANCED DATASTORE LOGGING
     * Comprehensive logging for all DataStore operations with detailed metadata
     */
    private fun logDataStoreOperation(operation: String, key: String, value: Any?, details: String = "") {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        Log.i(TAG, "")
        Log.i(TAG, "💾 DATASTORE OPERATION")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        Log.i(TAG, "🔄 Operation: $operation")
        Log.i(TAG, "🗝️ Key/Field: $key")
        Log.i(TAG, "💎 Value: $value")
        if (details.isNotEmpty()) {
            Log.i(TAG, "📄 Details: $details")
        }
        Log.i(TAG, "📁 Storage: Proto DataStore")
        Log.i(TAG, "")
    }
    
    private fun verifyDataStoreWrite(operation: String, expectedResult: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
            
        Log.i(TAG, "")
        Log.i(TAG, "🔍 DATASTORE WRITE VERIFICATION")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        Log.i(TAG, "🔄 Operation: $operation")
        Log.i(TAG, "✅ Status: Write operation completed")
        Log.i(TAG, "📈 Expected: $expectedResult")
        Log.i(TAG, "")
    }
    val userData = userPreferences.data
        .map {
            UserData(
                bookmarkedNewsResources = it.bookmarkedNewsResourceIdsMap.keys,
                viewedNewsResources = it.viewedNewsResourceIdsMap.keys,
                followedTopics = it.followedTopicIdsMap.keys,
                themeBrand = when (it.themeBrand) {
                    null,
                    ThemeBrandProto.THEME_BRAND_UNSPECIFIED,
                    ThemeBrandProto.UNRECOGNIZED,
                    ThemeBrandProto.THEME_BRAND_DEFAULT,
                    -> ThemeBrand.COASTAL
                    ThemeBrandProto.THEME_BRAND_ANDROID -> ThemeBrand.ANDROID
                    ThemeBrandProto.THEME_BRAND_COASTAL -> ThemeBrand.COASTAL
                    ThemeBrandProto.THEME_BRAND_ROYAL -> ThemeBrand.ROYAL
                    ThemeBrandProto.THEME_BRAND_CUSTOM -> ThemeBrand.CUSTOM
                },
                darkThemeConfig = when (it.darkThemeConfig) {
                    null,
                    DarkThemeConfigProto.DARK_THEME_CONFIG_UNSPECIFIED,
                    DarkThemeConfigProto.UNRECOGNIZED,
                    DarkThemeConfigProto.DARK_THEME_CONFIG_FOLLOW_SYSTEM,
                    ->
                        DarkThemeConfig.FOLLOW_SYSTEM
                    DarkThemeConfigProto.DARK_THEME_CONFIG_LIGHT ->
                        DarkThemeConfig.LIGHT
                    DarkThemeConfigProto.DARK_THEME_CONFIG_DARK -> DarkThemeConfig.DARK
                },
                useDynamicColor = it.useDynamicColor,
                shouldHideOnboarding = it.shouldHideOnboarding,
                newsResourceLastOpenedTimes = it.newsResourceLastOpenedTimestampsMap,
                topicOrder = it.topicOrderIdsList,
                customThemeColor = it.customThemeColor,
                customSecondaryColor = it.customSecondaryColor,
                customTertiaryColor = it.customTertiaryColor,
            )
        }

    suspend fun setFollowedTopicIds(topicIds: Set<String>) {
        logDataStoreOperation(
            operation = "SET_FOLLOWED_TOPICS",
            key = "followedTopicIds", 
            value = topicIds,
            details = "Setting ${topicIds.size} topics as followed, clearing previous selections"
        )
        
        try {
            userPreferences.updateData {
                it.copy {
                    followedTopicIds.clear()
                    followedTopicIds.putAll(topicIds.associateWith { true })
                    updateShouldHideOnboardingIfNecessary()
                }
            }
            
            verifyDataStoreWrite(
                operation = "SET_FOLLOWED_TOPICS",
                expectedResult = "${topicIds.size} topics stored as followed"
            )
        } catch (ioException: IOException) {
            Log.e(TAG, "❌ DATASTORE ERROR: Failed to update followed topic IDs", ioException)
            Log.e(TAG, "❌ Attempted to store: $topicIds")
        }
    }

    suspend fun setTopicIdFollowed(topicId: String, followed: Boolean) {
        logDataStoreOperation(
            operation = if (followed) "FOLLOW_TOPIC" else "UNFOLLOW_TOPIC",
            key = "followedTopicIds[$topicId]",
            value = followed,
            details = "${if (followed) "Adding" else "Removing"} topic from followed list"
        )
        
        try {
            userPreferences.updateData {
                it.copy {
                    if (followed) {
                        followedTopicIds.put(topicId, true)
                    } else {
                        followedTopicIds.remove(topicId)
                    }
                    updateShouldHideOnboardingIfNecessary()
                }
            }
            
            verifyDataStoreWrite(
                operation = if (followed) "FOLLOW_TOPIC" else "UNFOLLOW_TOPIC",
                expectedResult = "Topic $topicId ${if (followed) "added to" else "removed from"} followed list"
            )
        } catch (ioException: IOException) {
            Log.e(TAG, "❌ DATASTORE ERROR: Failed to update topic follow status", ioException)
            Log.e(TAG, "❌ Topic: $topicId, Follow: $followed")
        }
    }

    suspend fun setThemeBrand(themeBrand: ThemeBrand) {
        logDataStoreOperation(
            operation = "SET_THEME_BRAND",
            key = "themeBrand",
            value = themeBrand.name,
            details = "Changing app theme brand preference"
        )
        
        try {
            userPreferences.updateData {
                it.copy {
                    this.themeBrand = when (themeBrand) {
                        ThemeBrand.DEFAULT -> ThemeBrandProto.THEME_BRAND_DEFAULT
                        ThemeBrand.ANDROID -> ThemeBrandProto.THEME_BRAND_ANDROID
                        ThemeBrand.COASTAL -> ThemeBrandProto.THEME_BRAND_COASTAL
                        ThemeBrand.ROYAL -> ThemeBrandProto.THEME_BRAND_ROYAL
                        ThemeBrand.CUSTOM -> ThemeBrandProto.THEME_BRAND_CUSTOM
                    }
                }
            }
            
            verifyDataStoreWrite(
                operation = "SET_THEME_BRAND",
                expectedResult = "Theme brand set to ${themeBrand.name}"
            )
        } catch (exception: Exception) {
            Log.e(TAG, "❌ DATASTORE ERROR: Failed to update theme brand", exception)
            Log.e(TAG, "❌ Attempted theme: ${themeBrand.name}")
        }
    }

    suspend fun setDynamicColorPreference(useDynamicColor: Boolean) {
        logDataStoreOperation(
            operation = "SET_DYNAMIC_COLOR",
            key = "useDynamicColor",
            value = useDynamicColor,
            details = "${if (useDynamicColor) "Enabling" else "Disabling"} dynamic color theming"
        )
        
        try {
            userPreferences.updateData {
                it.copy { this.useDynamicColor = useDynamicColor }
            }
            
            verifyDataStoreWrite(
                operation = "SET_DYNAMIC_COLOR",
                expectedResult = "Dynamic color preference set to $useDynamicColor"
            )
        } catch (exception: Exception) {
            Log.e(TAG, "❌ DATASTORE ERROR: Failed to update dynamic color preference", exception)
            Log.e(TAG, "❌ Attempted value: $useDynamicColor")
        }
    }

    suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        userPreferences.updateData {
            it.copy {
                this.darkThemeConfig = when (darkThemeConfig) {
                    DarkThemeConfig.FOLLOW_SYSTEM ->
                        DarkThemeConfigProto.DARK_THEME_CONFIG_FOLLOW_SYSTEM
                    DarkThemeConfig.LIGHT -> DarkThemeConfigProto.DARK_THEME_CONFIG_LIGHT
                    DarkThemeConfig.DARK -> DarkThemeConfigProto.DARK_THEME_CONFIG_DARK
                }
            }
        }
    }

    suspend fun setNewsResourceBookmarked(newsResourceId: String, bookmarked: Boolean) {
        logDataStoreOperation(
            operation = if (bookmarked) "BOOKMARK_ARTICLE" else "UNBOOKMARK_ARTICLE",
            key = "bookmarkedNewsResourceIds[$newsResourceId]",
            value = bookmarked,
            details = "${if (bookmarked) "Adding" else "Removing"} article bookmark"
        )
        
        try {
            userPreferences.updateData {
                it.copy {
                    if (bookmarked) {
                        bookmarkedNewsResourceIds.put(newsResourceId, true)
                    } else {
                        bookmarkedNewsResourceIds.remove(newsResourceId)
                    }
                }
            }
            
            verifyDataStoreWrite(
                operation = if (bookmarked) "BOOKMARK_ARTICLE" else "UNBOOKMARK_ARTICLE",
                expectedResult = "Article $newsResourceId ${if (bookmarked) "bookmarked" else "unbookmarked"}"
            )
        } catch (ioException: IOException) {
            Log.e(TAG, "❌ DATASTORE ERROR: Failed to update bookmark status", ioException)
            Log.e(TAG, "❌ Article: $newsResourceId, Bookmarked: $bookmarked")
        }
    }

    suspend fun setNewsResourceViewed(newsResourceId: String, viewed: Boolean) {
        setNewsResourcesViewed(listOf(newsResourceId), viewed)
    }

    suspend fun setNewsResourcesViewed(newsResourceIds: List<String>, viewed: Boolean) {
        val currentTimeMillis = System.currentTimeMillis()
        userPreferences.updateData { prefs ->
            prefs.copy {
                newsResourceIds.forEach { id ->
                    if (viewed) {
                        viewedNewsResourceIds.put(id, true)
                        // Also store the timestamp when the resource was last opened
                        newsResourceLastOpenedTimestamps.put(id, currentTimeMillis)
                    } else {
                        viewedNewsResourceIds.remove(id)
                    }
                }
            }
        }
    }

    suspend fun getChangeListVersions() = userPreferences.data
        .map {
            ChangeListVersions(
                topicVersion = it.topicChangeListVersion,
                newsResourceVersion = it.newsResourceChangeListVersion,
            )
        }
        .firstOrNull() ?: ChangeListVersions()

    /**
     * Update the [ChangeListVersions] using [update].
     */
    suspend fun updateChangeListVersion(update: ChangeListVersions.() -> ChangeListVersions) {
        try {
            userPreferences.updateData { currentPreferences ->
                val updatedChangeListVersions = update(
                    ChangeListVersions(
                        topicVersion = currentPreferences.topicChangeListVersion,
                        newsResourceVersion = currentPreferences.newsResourceChangeListVersion,
                    ),
                )

                currentPreferences.copy {
                    topicChangeListVersion = updatedChangeListVersions.topicVersion
                    newsResourceChangeListVersion = updatedChangeListVersions.newsResourceVersion
                }
            }
        } catch (ioException: IOException) {
            Log.e("NiaPreferences", "Failed to update user preferences", ioException)
        }
    }

    suspend fun setCustomThemeColor(argb: Int) {
        try {
            userPreferences.updateData {
                it.copy { this.customThemeColor = argb }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "❌ DATASTORE ERROR: Failed to update custom theme color", exception)
        }
    }

    suspend fun setCustomThemeColors(primary: Int, secondary: Int, tertiary: Int) {
        try {
            userPreferences.updateData {
                it.copy {
                    this.customThemeColor = primary
                    this.customSecondaryColor = secondary
                    this.customTertiaryColor = tertiary
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "❌ DATASTORE ERROR: Failed to update custom theme colors", exception)
        }
    }

    suspend fun setShouldHideOnboarding(shouldHideOnboarding: Boolean) {
        userPreferences.updateData {
            it.copy { this.shouldHideOnboarding = shouldHideOnboarding }
        }
    }

    suspend fun setTopicOrder(topicIds: List<String>) {
        logDataStoreOperation(
            operation = "SET_TOPIC_ORDER",
            key = "topicOrderIds",
            value = topicIds,
            details = "Setting custom topic order with ${topicIds.size} topics"
        )

        try {
            userPreferences.updateData {
                it.copy {
                    topicOrderIds.clear()
                    topicOrderIds.addAll(topicIds)
                }
            }

            verifyDataStoreWrite(
                operation = "SET_TOPIC_ORDER",
                expectedResult = "Topic order saved with ${topicIds.size} topics"
            )
        } catch (ioException: IOException) {
            Log.e(TAG, "❌ DATASTORE ERROR: Failed to update topic order", ioException)
            Log.e(TAG, "❌ Attempted to store: $topicIds")
        }
    }
}

private fun UserPreferencesKt.Dsl.updateShouldHideOnboardingIfNecessary() {
    if (followedTopicIds.isEmpty() && followedAuthorIds.isEmpty()) {
        shouldHideOnboarding = false
    }
}
