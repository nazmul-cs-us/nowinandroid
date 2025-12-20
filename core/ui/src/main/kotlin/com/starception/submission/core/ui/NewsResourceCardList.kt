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

import android.net.Uri
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.starception.submission.core.analytics.LocalAnalyticsHelper
import com.starception.submission.core.model.data.UserNewsResource

/**
 * Extension function for displaying a [List] of [NewsResourceCardExpanded] backed by a list of
 * [UserNewsResource]s.
 *
 * [onToggleBookmark] defines the action invoked when a user wishes to bookmark an item
 * [onSurahClick] defines the action for Surah items (navigates to Surah detail instead of URL)
 * [onDuaClick] defines the action for Dua items (navigates to Dua detail instead of URL)
 * When a news resource card is tapped it will open the news resource URL in a Chrome Custom Tab,
 * unless it's a Surah/Dua item which navigates to the respective detail screen.
 */
fun LazyListScope.userNewsResourceCardItems(
    items: List<UserNewsResource>,
    onToggleBookmark: (item: UserNewsResource) -> Unit,
    onNewsResourceViewed: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource) -> Unit = { _ -> },
    itemModifier: Modifier = Modifier,
) = items(
    items = items,
    key = { it.id },
    itemContent = { userNewsResource ->
        val resourceUrl = Uri.parse(userNewsResource.url)
        val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
        val context = LocalContext.current
        val analyticsHelper = LocalAnalyticsHelper.current

        // Check if this is a Surah item
        val surahNumber = extractSurahNumber(
            title = userNewsResource.title,
            url = userNewsResource.url,
            type = userNewsResource.type
        )

        // Check if this is a Dua item
        val isDuaItem = userNewsResource.type.contains("Dua", ignoreCase = true)

        NewsResourceCardExpanded(
            userNewsResource = userNewsResource,
            isBookmarked = userNewsResource.isSaved,
            hasBeenViewed = userNewsResource.hasBeenViewed,
            onToggleBookmark = { onToggleBookmark(userNewsResource) },
            onClick = {
                analyticsHelper.logNewsResourceOpened(
                    newsResourceId = userNewsResource.id,
                )

                // If it's a Surah, navigate to Surah detail
                if (surahNumber != null) {
                    onSurahClick(surahNumber, userNewsResource.id)
                } else if (isDuaItem) {
                    // If it's a Dua, navigate to Dua detail
                    onDuaClick(userNewsResource)
                } else if (userNewsResource.url.isNotBlank()) {
                    launchCustomChromeTab(context, resourceUrl, backgroundColor)
                }
                onNewsResourceViewed(userNewsResource.id)
            },
            onTopicClick = onTopicClick,
            modifier = itemModifier,
        )
    },
)
