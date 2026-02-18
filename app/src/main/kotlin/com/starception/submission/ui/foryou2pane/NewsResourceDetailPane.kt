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

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.model.data.UserNewsResource
import com.starception.submission.core.ui.BookmarkButton
import com.starception.submission.core.ui.NewsResourceHeaderImage
import com.starception.submission.core.ui.NewsResourceMetaData
import com.starception.submission.core.ui.NewsResourceShortDescription
import com.starception.submission.core.ui.NewsResourceTitle
import com.starception.submission.core.ui.NewsResourceTopics
import com.starception.submission.core.ui.launchCustomChromeTab
import com.starception.submission.feature.foryou.R

/**
 * Placeholder shown when no news resource is selected
 */
@Composable
fun NewsResourceDetailPlaceholder(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = NiaIcons.Upcoming,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Select a news item to view details",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Detail pane showing the full news resource content
 */
@Composable
fun NewsResourceDetailPane(
    userNewsResource: UserNewsResource,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onTopicClick: (String) -> Unit,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header with back button and bookmark
        if (showBackButton) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = NiaIcons.ArrowBack,
                        contentDescription = stringResource(
                            id = com.starception.submission.core.ui.R.string.core_ui_back,
                        ),
                    )
                }
                BookmarkButton(
                    isBookmarked = isBookmarked,
                    onClick = onToggleBookmark,
                )
            }
        }

        // Header image
        NewsResourceHeaderImage(
            headerImageUrl = userNewsResource.headerImageUrl,
        )

        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Title with bookmark (if no back button shown)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                NewsResourceTitle(
                    newsResourceTitle = userNewsResource.title,
                    modifier = Modifier.weight(1f),
                )
                if (!showBackButton) {
                    BookmarkButton(
                        isBookmarked = isBookmarked,
                        onClick = onToggleBookmark,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metadata (date, type)
            NewsResourceMetaData(
                publishDate = userNewsResource.publishDate,
                resourceType = userNewsResource.type,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Full content
            NewsResourceShortDescription(
                newsResourceShortDescription = userNewsResource.content,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Topics
            NewsResourceTopics(
                topics = userNewsResource.followableTopics,
                onTopicClick = onTopicClick,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Open in browser button (if URL exists)
            if (userNewsResource.url.isNotBlank()) {
                Button(
                    onClick = {
                        launchCustomChromeTab(
                            context = context,
                            uri = Uri.parse(userNewsResource.url),
                            toolbarColor = backgroundColor,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.feature_foryou_open_in_browser))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
