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

package com.starception.submission.core.ui

import android.content.Context
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.starception.submission.core.analytics.LocalAnalyticsHelper
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.core.model.data.UserNewsResource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * An extension on [LazyListScope] defining a feed with news resources.
 * Depending on the [feedState], this might emit no items.
 */
fun LazyStaggeredGridScope.newsFeed(
    feedState: NewsFeedUiState,
    onNewsResourcesCheckedChanged: (String, Boolean) -> Unit,
    onNewsResourceViewed: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    onExpandedCardClick: () -> Unit = {},
    onSurahClick: (Int, String?) -> Unit = { _, _ -> }, // surahNumber, newsResourceId
    onDuaClick: (UserNewsResource) -> Unit = { _ -> }, // Dua news resource
    onNewsClick: ((UserNewsResource) -> Unit)? = null,
) {
    when (feedState) {
        NewsFeedUiState.Loading -> Unit
        is NewsFeedUiState.Success -> {
            items(
                items = feedState.feed,
                key = { it.id },
                contentType = { "newsFeedItem" },
            ) { userNewsResource ->
                val context = LocalContext.current
                val analyticsHelper = LocalAnalyticsHelper.current
                val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

                // Check if this is a Surah news item
                val surahNumber = extractSurahNumber(
                    title = userNewsResource.title,
                    url = userNewsResource.url,
                    type = userNewsResource.type
                )

                // State for floating toolbar
                var showFloatingToolbar by remember { mutableStateOf(false) }

                // Show floating toolbar for Surah items
                if (showFloatingToolbar && surahNumber != null) {
                    SurahFloatingToolbar(
                        visible = showFloatingToolbar,
                        surahNumber = surahNumber,
                        surahName = userNewsResource.title,
                        onDismiss = { showFloatingToolbar = false },
                        onPlayAudio = {
                            // TODO: Implement play Surah audio
                            android.util.Log.d("SurahToolbar", "Play audio for Surah $surahNumber")
                        },
                        onBookmark = {
                            onNewsResourcesCheckedChanged(
                                userNewsResource.id,
                                !userNewsResource.isSaved
                            )
                        },
                        onShare = {
                            // Share Surah
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT,
                                    "Check out Surah ${userNewsResource.title}: ${userNewsResource.url}")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Surah"))
                        },
                        onDownload = {
                            // TODO: Implement download Surah
                            android.util.Log.d("SurahToolbar", "Download Surah $surahNumber")
                        },
                        onInfo = {
                            onSurahClick(surahNumber, userNewsResource.id)
                        }
                    )
                }

                // Check if this is a Dua item
                val isDuaItem = userNewsResource.type.contains("Dua", ignoreCase = true)

                NewsResourceCardExpanded(
                    userNewsResource = userNewsResource,
                    isBookmarked = userNewsResource.isSaved,
                    onClick = {
                        android.util.Log.d("NewsFeedClick", "📱 Card clicked: title='${userNewsResource.title}', surahNumber=$surahNumber, isDua=$isDuaItem")
                        onExpandedCardClick()
                        analyticsHelper.logNewsResourceOpened(
                            newsResourceId = userNewsResource.id,
                        )

                        // If it's a Surah, navigate to Surah detail screen
                        if (surahNumber != null) {
                            android.util.Log.d("NewsFeedClick", "🕌 Navigating to Surah $surahNumber")
                            onSurahClick(surahNumber, userNewsResource.id)
                        } else if (isDuaItem) {
                            android.util.Log.d("NewsFeedClick", "🤲 Navigating to Dua")
                            // Navigate to Dua detail screen
                            onDuaClick(userNewsResource)
                        } else if (onNewsClick != null) {
                            android.util.Log.d("NewsFeedClick", "📰 Custom news click handler")
                            // Use custom news click handler if provided
                            onNewsClick(userNewsResource)
                        } else if (userNewsResource.url.isNotBlank()) {
                            android.util.Log.d("NewsFeedClick", "🌐 Opening URL: ${userNewsResource.url}")
                            // Otherwise, open in Chrome Custom Tab (only if URL is not empty)
                            launchCustomChromeTab(context, Uri.parse(userNewsResource.url), backgroundColor)
                        } else {
                            android.util.Log.d("NewsFeedClick", "⚠️ No action taken - URL is blank and not Surah/Dua")
                        }

                        onNewsResourceViewed(userNewsResource.id)
                    },
                    hasBeenViewed = userNewsResource.hasBeenViewed,
                    onToggleBookmark = {
                        onNewsResourcesCheckedChanged(
                            userNewsResource.id,
                            !userNewsResource.isSaved,
                        )
                    },
                    onTopicClick = onTopicClick,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .animateItem()
                        .then(
                            // Add long-press detection for Surah items
                            if (surahNumber != null) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            showFloatingToolbar = true
                                        }
                                    )
                                }
                            } else Modifier
                        ),
                )
            }
        }
    }
}

/**
 * Extract Surah number from title or URL
 * Returns null if not a Surah
 * Example title: "Surah 1: Al-Faatiha" -> 1
 * Example URL: "https://quran.com/1" -> 1
 */
fun extractSurahNumber(title: String, url: String, type: String): Int? {
    android.util.Log.d("SurahExtract", "🔍 Checking: title='$title', url='$url', type='$type'")

    // First check if it's a Quran or Surah type
    if (type.contains("Quran", ignoreCase = true) || type.contains("Surah", ignoreCase = true)) {
        // Try to extract from title pattern "Surah N:"
        val titleRegex = Regex("Surah\\s+(\\d+):")
        titleRegex.find(title)?.groupValues?.get(1)?.toIntOrNull()?.let {
            android.util.Log.d("SurahExtract", "✅ Found Surah $it from title (Quran/Surah type)")
            return it
        }
    }

    // Fallback: try URL pattern
    return try {
        val urlRegex = Regex("https?://quran\\.com/(\\d+)$")
        val result = urlRegex.find(url)?.groupValues?.get(1)?.toIntOrNull()
        if (result != null) {
            android.util.Log.d("SurahExtract", "✅ Found Surah $result from URL")
        } else {
            android.util.Log.d("SurahExtract", "❌ No Surah number found")
        }
        result
    } catch (e: Exception) {
        android.util.Log.e("SurahExtract", "❌ Error extracting Surah: ${e.message}")
        null
    }
}

fun launchCustomChromeTab(context: Context, uri: Uri, @ColorInt toolbarColor: Int) {
    val customTabBarColor = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(toolbarColor).build()
    val customTabsIntent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(customTabBarColor)
        .build()

    try {
        customTabsIntent.launchUrl(context, uri)
    } catch (e: android.content.ActivityNotFoundException) {
        // Fallback to regular browser intent if Custom Tabs not available
        try {
            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            context.startActivity(browserIntent)
        } catch (e2: android.content.ActivityNotFoundException) {
            // No browser available - show toast or log
            android.widget.Toast.makeText(
                context,
                "No browser available to open link",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

/**
 * A sealed hierarchy describing the state of the feed of news resources.
 */
sealed interface NewsFeedUiState {
    /**
     * The feed is still loading.
     */
    data object Loading : NewsFeedUiState

    /**
     * The feed is loaded with the given list of news resources.
     */
    data class Success(
        /**
         * The list of news resources contained in this feed.
         */
        val feed: List<UserNewsResource>,
    ) : NewsFeedUiState
}

@Preview
@Composable
private fun NewsFeedLoadingPreview() {
    NiaTheme {
        LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(300.dp)) {
            newsFeed(
                feedState = NewsFeedUiState.Loading,
                onNewsResourcesCheckedChanged = { _, _ -> },
                onNewsResourceViewed = {},
                onTopicClick = {},
            )
        }
    }
}

@Preview
@Preview(device = Devices.TABLET)
@Composable
private fun NewsFeedContentPreview(
    @PreviewParameter(UserNewsResourcePreviewParameterProvider::class)
    userNewsResources: List<UserNewsResource>,
) {
    NiaTheme {
        LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(300.dp)) {
            newsFeed(
                feedState = NewsFeedUiState.Success(userNewsResources),
                onNewsResourcesCheckedChanged = { _, _ -> },
                onNewsResourceViewed = {},
                onTopicClick = {},
            )
        }
    }
}
