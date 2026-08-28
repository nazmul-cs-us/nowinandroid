/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.feature.topic

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.core.designsystem.component.DynamicAsyncImage
import com.starception.submission.core.designsystem.component.NiaBackground
import com.starception.submission.core.designsystem.component.NiaFilterChip
import com.starception.submission.core.designsystem.component.NiaLoadingWheel
import com.starception.submission.core.designsystem.component.NiaTopicTag
import com.starception.submission.core.designsystem.component.scrollbar.DraggableScrollbar
import com.starception.submission.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.starception.submission.core.designsystem.component.scrollbar.scrollbarState
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.designsystem.icon.isMonochromeTopicIcon
import com.starception.submission.core.designsystem.icon.topicIconResFor
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.core.model.data.FollowableTopic
import com.starception.submission.core.model.data.BukhariBook
import com.starception.submission.core.model.data.BukhariBooks
import com.starception.submission.core.model.data.UserNewsResource
import com.starception.submission.core.designsystem.theme.QuranFonts
import com.starception.submission.core.ui.DevicePreviews
import com.starception.submission.core.ui.FlaticonIcon
import com.starception.submission.core.ui.FlaticonIcons
import com.starception.submission.core.ui.FlaticonPlayIcon
import com.starception.submission.core.ui.FlaticonSearchIcon
import com.starception.submission.core.ui.TrackScreenViewEvent
import com.starception.submission.core.ui.TrackScrollJank
import com.starception.submission.core.ui.UserNewsResourcePreviewParameterProvider
import com.starception.submission.core.ui.userNewsResourceCardItems
import com.starception.submission.feature.topic.R.string

@Composable
fun TopicScreen(
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    onTopicClick: (String) -> Unit,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource) -> Unit = { _ -> },
    onHadithClick: (String, Int) -> Unit = { _, _ -> },
    onBukhariBookClick: (Int) -> Unit = {},
    onBukhariBookPlayClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    // Optional app-provided content rendered directly under the topic header (gets the topic name).
    // Used to surface a "download missing content" card for content-backed topics (Quran/Hadith).
    belowHeaderContent: @Composable (topicName: String) -> Unit = {},
    viewModel: TopicViewModel = hiltViewModel(),
) {
    val topicUiState: TopicUiState by viewModel.topicUiState.collectAsStateWithLifecycle()
    val newsUiState: NewsUiState by viewModel.newsUiState.collectAsStateWithLifecycle()

    TrackScreenViewEvent(screenName = "Topic: ${viewModel.topicId}")
    TopicScreen(
        topicUiState = topicUiState,
        newsUiState = newsUiState,
        modifier = modifier.testTag("topic:${viewModel.topicId}"),
        showBackButton = showBackButton,
        onBackClick = onBackClick,
        onFollowClick = viewModel::followTopicToggle,
        onBookmarkChanged = viewModel::bookmarkNews,
        onNewsResourceViewed = { viewModel.setNewsResourceViewed(it, true) },
        onTopicClick = onTopicClick,
        onSurahClick = onSurahClick,
        onDuaClick = onDuaClick,
        onHadithClick = onHadithClick,
        onBukhariBookClick = onBukhariBookClick,
        onBukhariBookPlayClick = onBukhariBookPlayClick,
        belowHeaderContent = belowHeaderContent,
    )
}

@VisibleForTesting
@Composable
internal fun TopicScreen(
    topicUiState: TopicUiState,
    newsUiState: NewsUiState,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    onFollowClick: (Boolean) -> Unit,
    onTopicClick: (String) -> Unit,
    onBookmarkChanged: (String, Boolean) -> Unit,
    onNewsResourceViewed: (String) -> Unit,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource) -> Unit = { _ -> },
    onHadithClick: (String, Int) -> Unit = { _, _ -> },
    onBukhariBookClick: (Int) -> Unit = {},
    onBukhariBookPlayClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    belowHeaderContent: @Composable (topicName: String) -> Unit = {},
) {
    val state = rememberLazyListState()
    var bukhariQuery by rememberSaveable { mutableStateOf("") }
    val filteredBukhariBooks = remember(bukhariQuery) {
        val query = bukhariQuery.trim()
        if (query.isEmpty()) {
            BukhariBooks.all
        } else {
            BukhariBooks.all.filter { book ->
                book.id.toString() == query ||
                    book.nameEnglish.contains(query, ignoreCase = true) ||
                    book.nameArabic.contains(query)
            }
        }
    }
    TrackScrollJank(scrollableState = state, stateName = "topic:screen")
    Box(
        modifier = modifier,
    ) {
        LazyColumn(
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
            }
            when (topicUiState) {
                TopicUiState.Loading -> item {
                    NiaLoadingWheel(
                        modifier = modifier,
                        contentDesc = stringResource(id = string.feature_topic_loading),
                    )
                }

                TopicUiState.Error -> {
                    item {
                        TopicToolbar(
                            showBackButton = showBackButton,
                            onBackClick = onBackClick,
                            onFollowClick = {},
                            uiState = FollowableTopic(
                                topic = com.starception.submission.core.model.data.Topic(
                                    id = "",
                                    name = "",
                                    shortDescription = "",
                                    longDescription = "",
                                    url = "",
                                    imageUrl = "",
                                ),
                                isFollowed = false,
                            ),
                        )
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Unable to load topic",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = "The content database may need to be downloaded. Please check your connection and try again.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                is TopicUiState.Success -> {
                    item {
                        TopicToolbar(
                            showBackButton = showBackButton,
                            onBackClick = onBackClick,
                            onFollowClick = onFollowClick,
                            uiState = topicUiState.followableTopic,
                            compact = topicUiState.followableTopic.topic.name.contains(
                                "Bukhari",
                                ignoreCase = true,
                            ),
                        )
                    }
                    topicBody(
                        name = topicUiState.followableTopic.topic.name,
                        description = topicUiState.followableTopic.topic.longDescription,
                        news = newsUiState,
                        imageUrl = topicUiState.followableTopic.topic.imageUrl,
                        onBookmarkChanged = onBookmarkChanged,
                        onNewsResourceViewed = onNewsResourceViewed,
                        onTopicClick = onTopicClick,
                        onSurahClick = onSurahClick,
                        onDuaClick = onDuaClick,
                        onHadithClick = onHadithClick,
                        onBukhariBookClick = onBukhariBookClick,
                        onBukhariBookPlayClick = onBukhariBookPlayClick,
                        bukhariQuery = bukhariQuery,
                        onBukhariQueryChange = { bukhariQuery = it },
                        filteredBukhariBooks = filteredBukhariBooks,
                        belowHeaderContent = belowHeaderContent,
                    )
                }
            }
            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
        // Use the real LazyColumn item count. Bukhari renders books in 49 paired rows, so the
        // old news-based estimate made the thumb stop early and fast scrolling ineffective.
        val itemsAvailable by remember(state) {
            derivedStateOf { state.layoutInfo.totalItemsCount }
        }
        val scrollbarState = state.scrollbarState(
            itemsAvailable = itemsAvailable,
        )
        state.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Orientation.Vertical,
            onThumbMoved = state.rememberDraggableScroller(
                itemsAvailable = itemsAvailable,
            ),
        )
    }
}

private fun LazyListScope.topicBody(
    name: String,
    description: String,
    news: NewsUiState,
    imageUrl: String,
    onBookmarkChanged: (String, Boolean) -> Unit,
    onNewsResourceViewed: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource) -> Unit = { _ -> },
    onHadithClick: (String, Int) -> Unit = { _, _ -> },
    onBukhariBookClick: (Int) -> Unit = {},
    onBukhariBookPlayClick: (Int) -> Unit = {},
    bukhariQuery: String = "",
    onBukhariQueryChange: (String) -> Unit = {},
    filteredBukhariBooks: List<BukhariBook> = BukhariBooks.all,
    belowHeaderContent: @Composable (topicName: String) -> Unit = {},
) {
    val isBukhari = name.contains("Bukhari", ignoreCase = true)

    // The collection browser owns its compact identity header. The generic topic hero is useful
    // for editorial topics, but duplicated the Bukhari title and consumed most of the first screen.
    if (!isBukhari) {
        item {
            TopicHeader(name, description, imageUrl)
        }
    }

    // App-provided slot under the header (e.g. a download card for missing Quran/Hadith content).
    item {
        belowHeaderContent(name)
    }

    if (isBukhari) {
        bukhariBookBrowser(
            query = bukhariQuery,
            onQueryChange = onBukhariQueryChange,
            books = filteredBukhariBooks,
            onBookClick = onBukhariBookClick,
            onBookPlayClick = onBukhariBookPlayClick,
        )
    } else {
        userNewsResourceCards(news, onBookmarkChanged, onNewsResourceViewed, onTopicClick, onSurahClick, onDuaClick, onHadithClick)
    }
}

private fun LazyListScope.bukhariBookBrowser(
    query: String,
    onQueryChange: (String) -> Unit,
    books: List<BukhariBook>,
    onBookClick: (Int) -> Unit,
    onBookPlayClick: (Int) -> Unit,
) {
    item(key = "bukhari-book-search") {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            shape = RoundedCornerShape(
                topStart = 32.dp,
                topEnd = 32.dp,
                bottomEnd = 32.dp,
                bottomStart = 10.dp,
            ),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "SAHIH BUKHARI",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = "Browse 97 books",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 28.sp,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomEnd = 8.dp,
                            bottomStart = 20.dp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "97",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "BOOKS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Text(
                    text = "7,277 hadiths, arranged by subject",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = {
                        FlaticonSearchIcon(contentDescription = null)
                    },
                    placeholder = { Text("Search English or Arabic") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
                if (query.isNotBlank()) {
                    Text(
                        text = "${books.size} ${if (books.size == 1) "book" else "books"} found",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }

    if (books.isEmpty()) {
        item(key = "bukhari-no-results") {
            Text(
                text = "No Bukhari books match your search.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(
            items = books.chunked(2),
            key = { row -> "bukhari-row-${row.first().id}" },
        ) { rowBooks ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowBooks.forEachIndexed { index, book ->
                    BukhariBookCard(
                        book = book,
                        onClick = { onBookClick(book.id) },
                        onPlayClick = { onBookPlayClick(book.id) },
                        position = when {
                            rowBooks.size == 1 -> BukhariBookCardPosition.Solo
                            index == 0 -> BukhariBookCardPosition.Left
                            else -> BukhariBookCardPosition.Right
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                if (rowBooks.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BukhariBookCard(
    book: BukhariBook,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    position: BukhariBookCardPosition,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val outerCorner by animateDpAsState(
        targetValue = if (isPressed) 24.dp else 30.dp,
        label = "bukhariBookOuterCorner",
    )
    val innerCorner by animateDpAsState(
        targetValue = if (isPressed) 24.dp else 8.dp,
        label = "bukhariBookInnerCorner",
    )
    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 1.025f else 1f,
        label = "bukhariBookPressedScale",
    )
    val shape = when (position) {
        BukhariBookCardPosition.Left -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = innerCorner,
            bottomEnd = innerCorner,
            bottomStart = outerCorner,
        )
        BukhariBookCardPosition.Right -> RoundedCornerShape(
            topStart = innerCorner,
            topEnd = outerCorner,
            bottomEnd = outerCorner,
            bottomStart = innerCorner,
        )
        BukhariBookCardPosition.Solo -> RoundedCornerShape(outerCorner)
    }
    val (containerColor, contentColor) = bukhariBookCardColors()

    Card(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 160.dp)
            .zIndex(if (isPressed) 1f else 0f)
            .graphicsLayer {
                scaleX = pressedScale
                scaleY = pressedScale
            },
        shape = shape,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = book.id.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = contentColor.copy(alpha = 0.82f),
                )
                val playInteractionSource = remember { MutableInteractionSource() }
                val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                val playCorner by animateDpAsState(
                    targetValue = if (isPlayPressed) 26.dp else 14.dp,
                    label = "bukhariPlayCorner",
                )
                FilledIconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(
                        topStart = 22.dp,
                        topEnd = 22.dp,
                        bottomEnd = playCorner,
                        bottomStart = 22.dp,
                    ),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = contentColor,
                        contentColor = containerColor,
                    ),
                    interactionSource = playInteractionSource,
                ) {
                    FlaticonPlayIcon(
                        contentDescription = "Play all ${book.hadithCount} hadiths in ${book.nameEnglish}",
                        tint = containerColor,
                        iconSize = 20.dp,
                    )
                }
            }
            Text(
                text = book.nameEnglish,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.nameArabic,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = QuranFonts.PDMSSaleem,
                    lineHeight = 26.sp,
                ),
                color = contentColor.copy(alpha = 0.76f),
                textAlign = TextAlign.End,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            NiaTopicTag(
                followed = false,
                onClick = {},
            ) {
                Text(
                    text = "${book.hadithCount} HADITHS · ${book.firstHadithId}–${book.lastHadithId}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private enum class BukhariBookCardPosition {
    Left,
    Right,
    Solo,
}

@Composable
private fun bukhariBookCardColors(): Pair<Color, Color> =
    MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

@Composable
private fun TopicHeader(name: String, description: String, imageUrl: String) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
    ) {
        val topicIconRes = topicIconResFor(name)
        val headerModifier = Modifier
            .align(Alignment.CenterHorizontally)
            .size(132.dp)
            .padding(bottom = 12.dp)
        if (topicIconRes != null) {
            Image(
                painter = painterResource(topicIconRes),
                contentDescription = null,
                modifier = headerModifier,
                colorFilter = if (isMonochromeTopicIcon(name)) {
                    androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                } else {
                    null
                },
            )
        } else {
            DynamicAsyncImage(
                imageUrl = imageUrl,
                contentDescription = null,
                modifier = headerModifier,
            )
        }
        Text(name, style = MaterialTheme.typography.displayMedium)
        if (description.isNotEmpty()) {
            Text(
                description,
                modifier = Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

// TODO: Could/should this be replaced with [LazyGridScope.newsFeed]?
private fun LazyListScope.userNewsResourceCards(
    news: NewsUiState,
    onBookmarkChanged: (String, Boolean) -> Unit,
    onNewsResourceViewed: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource) -> Unit = { _ -> },
    onHadithClick: (String, Int) -> Unit = { _, _ -> },
) {
    when (news) {
        is NewsUiState.Success -> {
            userNewsResourceCardItems(
                items = news.news,
                onToggleBookmark = { onBookmarkChanged(it.id, !it.isSaved) },
                onNewsResourceViewed = onNewsResourceViewed,
                onTopicClick = onTopicClick,
                onSurahClick = onSurahClick,
                onDuaClick = onDuaClick,
                onHadithClick = onHadithClick,
                itemModifier = Modifier.padding(24.dp),
            )
        }

        is NewsUiState.Loading -> item {
            NiaLoadingWheel(contentDesc = "Loading news") // TODO
        }

        else -> item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Unable to load content",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "The news database may need to be downloaded. Please visit Settings to manage content downloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Preview
@Composable
private fun TopicBodyPreview() {
    NiaTheme {
        LazyColumn {
            topicBody(
                name = "Jetpack Compose",
                description = "Lorem ipsum maximum",
                news = NewsUiState.Success(emptyList()),
                imageUrl = "",
                onBookmarkChanged = { _, _ -> },
                onNewsResourceViewed = {},
                onTopicClick = {},
            )
        }
    }
}

@Composable
private fun TopicToolbar(
    uiState: FollowableTopic,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    compact: Boolean = false,
    onBackClick: () -> Unit = {},
    onFollowClick: (Boolean) -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = if (compact) 8.dp else 32.dp),
    ) {
        if (showBackButton) {
            IconButton(onClick = { onBackClick() }) {
                FlaticonIcon(
                    glyph = FlaticonIcons.ARROW_BACK,
                    contentDescription = stringResource(
                        id = com.starception.submission.core.ui.R.string.core_ui_back,
                    ),
                    fontSize = 24.sp,
                )
            }
        } else {
            // Keeps the NiaFilterChip aligned to the end of the Row.
            Spacer(modifier = Modifier.width(1.dp))
        }
        val selected = uiState.isFollowed
        NiaFilterChip(
            selected = selected,
            onSelectedChange = onFollowClick,
            modifier = Modifier.padding(end = 24.dp),
        ) {
            if (selected) {
                Text("FOLLOWING")
            } else {
                Text("NOT FOLLOWING")
            }
        }
    }
}

@DevicePreviews
@Composable
fun TopicScreenPopulated(
    @PreviewParameter(UserNewsResourcePreviewParameterProvider::class)
    userNewsResources: List<UserNewsResource>,
) {
    NiaTheme {
        NiaBackground {
            TopicScreen(
                topicUiState = TopicUiState.Success(userNewsResources[0].followableTopics[0]),
                newsUiState = NewsUiState.Success(userNewsResources),
                showBackButton = true,
                onBackClick = {},
                onFollowClick = {},
                onBookmarkChanged = { _, _ -> },
                onNewsResourceViewed = {},
                onTopicClick = {},
            )
        }
    }
}

@DevicePreviews
@Composable
fun TopicScreenLoading() {
    NiaTheme {
        NiaBackground {
            TopicScreen(
                topicUiState = TopicUiState.Loading,
                newsUiState = NewsUiState.Loading,
                showBackButton = true,
                onBackClick = {},
                onFollowClick = {},
                onBookmarkChanged = { _, _ -> },
                onNewsResourceViewed = {},
                onTopicClick = {},
            )
        }
    }
}
