/*
 * Copyright 2023 The Android Open Source Project
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

package com.starception.submission.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.starception.submission.core.designsystem.component.scrollbar.DraggableScrollbar
import com.starception.submission.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.starception.submission.core.designsystem.component.scrollbar.scrollbarState
import com.starception.submission.core.designsystem.icon.NiaIcons
import com.starception.submission.core.designsystem.theme.NiaTheme
import com.starception.submission.core.model.data.FollowableTopic
import com.starception.submission.core.model.data.UserNewsResource
import com.starception.submission.core.ui.DevicePreviews
import com.starception.submission.core.ui.InterestsItem
import com.starception.submission.core.ui.NewsFeedUiState.Success
import com.starception.submission.core.ui.R.string
import com.starception.submission.core.ui.TrackScreenViewEvent
import com.starception.submission.core.ui.newsFeed
import com.starception.submission.feature.search.R as searchR

@Composable
internal fun SearchRoute(
    onBackClick: () -> Unit,
    onInterestsClick: () -> Unit,
    onTopicClick: (String) -> Unit,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource) -> Unit = { _ -> },
    onNoteClick: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    searchNotes: suspend (String) -> List<SearchNote> = { emptyList() },
    modifier: Modifier = Modifier,
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val recentSearchQueriesUiState by searchViewModel.recentSearchQueriesUiState.collectAsStateWithLifecycle()
    val searchResultUiState by searchViewModel.searchResultUiState.collectAsStateWithLifecycle()
    val searchQuery by searchViewModel.searchQuery.collectAsStateWithLifecycle()
    val paginationState by searchViewModel.paginationState.collectAsStateWithLifecycle()

    // Search notes when query changes
    var notes by remember { mutableStateOf<List<SearchNote>>(emptyList()) }
    LaunchedEffect(searchQuery) {
        notes = if (searchQuery.trim().length >= 2) {
            searchNotes(searchQuery)
        } else {
            emptyList()
        }
    }

    // Combine notes with search result
    val enrichedSearchResultUiState = when (val state = searchResultUiState) {
        is SearchResultUiState.Success -> state.copy(notes = notes)
        else -> searchResultUiState
    }

    SearchScreen(
        modifier = modifier,
        searchQuery = searchQuery,
        recentSearchesUiState = recentSearchQueriesUiState,
        searchResultUiState = enrichedSearchResultUiState,
        paginationState = paginationState,
        onSearchQueryChanged = searchViewModel::onSearchQueryChanged,
        onSearchTriggered = searchViewModel::onSearchTriggered,
        onClearRecentSearches = searchViewModel::clearRecentSearches,
        onNewsResourcesCheckedChanged = searchViewModel::setNewsResourceBookmarked,
        onNewsResourceViewed = { searchViewModel.setNewsResourceViewed(it, true) },
        onFollowButtonClick = searchViewModel::followTopic,
        onBackClick = onBackClick,
        onInterestsClick = onInterestsClick,
        onTopicClick = onTopicClick,
        onSurahClick = onSurahClick,
        onDuaClick = onDuaClick,
        onNoteClick = onNoteClick,
        onLoadMore = searchViewModel::loadMoreResults,
    )
}

@Composable
internal fun SearchScreen(
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    recentSearchesUiState: RecentSearchQueriesUiState = RecentSearchQueriesUiState.Loading,
    searchResultUiState: SearchResultUiState = SearchResultUiState.Loading,
    paginationState: PaginationState = PaginationState(),
    onSearchQueryChanged: (String) -> Unit = {},
    onSearchTriggered: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    onNewsResourcesCheckedChanged: (String, Boolean) -> Unit = { _, _ -> },
    onNewsResourceViewed: (String) -> Unit = {},
    onFollowButtonClick: (String, Boolean) -> Unit = { _, _ -> },
    onBackClick: () -> Unit = {},
    onInterestsClick: () -> Unit = {},
    onTopicClick: (String) -> Unit = {},
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource) -> Unit = { _ -> },
    onNoteClick: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    onLoadMore: () -> Unit = {},
) {
    TrackScreenViewEvent(screenName = "Search")
    Column(modifier = modifier) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
        SearchToolbar(
            onBackClick = onBackClick,
            onSearchQueryChanged = onSearchQueryChanged,
            onSearchTriggered = onSearchTriggered,
            searchQuery = searchQuery,
        )
        when (searchResultUiState) {
            SearchResultUiState.Loading,
            SearchResultUiState.LoadFailed,
            -> Unit

            SearchResultUiState.SearchNotReady -> SearchNotReadyBody()
            SearchResultUiState.EmptyQuery,
            -> {
                if (recentSearchesUiState is RecentSearchQueriesUiState.Success) {
                    RecentSearchesBody(
                        onClearRecentSearches = onClearRecentSearches,
                        onRecentSearchClicked = {
                            onSearchQueryChanged(it)
                            onSearchTriggered(it)
                        },
                        recentSearchQueries = recentSearchesUiState.recentQueries.map { it.query },
                    )
                }
                // Show suggested verses when search is empty
                SuggestedVersesBody(
                    suggestedVerses = SuggestedVerses.verses,
                    onVerseClick = { verse ->
                        onNoteClick(verse.surahNumber, verse.ayahNumber)
                    },
                )
            }

            is SearchResultUiState.Success -> {
                if (searchResultUiState.isEmpty()) {
                    EmptySearchResultBody(
                        searchQuery = searchQuery,
                        onInterestsClick = onInterestsClick,
                    )
                    if (recentSearchesUiState is RecentSearchQueriesUiState.Success) {
                        RecentSearchesBody(
                            onClearRecentSearches = onClearRecentSearches,
                            onRecentSearchClicked = {
                                onSearchQueryChanged(it)
                                onSearchTriggered(it)
                            },
                            recentSearchQueries = recentSearchesUiState.recentQueries.map { it.query },
                        )
                    }
                } else {
                    SearchResultBody(
                        searchQuery = searchQuery,
                        topics = searchResultUiState.topics,
                        newsResources = searchResultUiState.newsResources,
                        notes = searchResultUiState.notes,
                        suggestedVerses = SuggestedVerses.search(searchQuery),
                        paginationState = paginationState,
                        onSearchTriggered = onSearchTriggered,
                        onTopicClick = onTopicClick,
                        onNewsResourcesCheckedChanged = onNewsResourcesCheckedChanged,
                        onNewsResourceViewed = onNewsResourceViewed,
                        onFollowButtonClick = onFollowButtonClick,
                        onSurahClick = onSurahClick,
                        onDuaClick = onDuaClick,
                        onNoteClick = onNoteClick,
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
    }
}

@Composable
fun EmptySearchResultBody(
    searchQuery: String,
    onInterestsClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 48.dp),
    ) {
        val message = stringResource(id = searchR.string.feature_search_result_not_found, searchQuery)
        val start = message.indexOf(searchQuery)
        Text(
            text = AnnotatedString(
                text = message,
                spanStyles = listOf(
                    AnnotatedString.Range(
                        SpanStyle(fontWeight = FontWeight.Bold),
                        start = start,
                        end = start + searchQuery.length,
                    ),
                ),
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        val tryAnotherSearchString = buildAnnotatedString {
            append(stringResource(id = searchR.string.feature_search_try_another_search))
            append(" ")
            withLink(
                LinkAnnotation.Clickable(
                    tag = "",
                    linkInteractionListener = {
                        onInterestsClick()
                    },
                ),
            ) {
                withStyle(
                    style = SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append(stringResource(id = searchR.string.feature_search_interests))
                }
            }

            append(" ")
            append(stringResource(id = searchR.string.feature_search_to_browse_topics))
        }
        Text(
            text = tryAnotherSearchString,
            style = MaterialTheme.typography.bodyLarge.merge(
                TextStyle(
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                ),
            ),
            modifier = Modifier
                .padding(start = 36.dp, end = 36.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun SearchNotReadyBody() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 48.dp),
    ) {
        Text(
            text = stringResource(id = searchR.string.feature_search_not_ready),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 24.dp),
        )
    }
}

@Composable
private fun SearchResultBody(
    searchQuery: String,
    topics: List<FollowableTopic>,
    newsResources: List<UserNewsResource>,
    notes: List<SearchNote> = emptyList(),
    suggestedVerses: List<SuggestedVerse> = emptyList(),
    paginationState: PaginationState = PaginationState(),
    onSearchTriggered: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    onNewsResourcesCheckedChanged: (String, Boolean) -> Unit,
    onNewsResourceViewed: (String) -> Unit,
    onFollowButtonClick: (String, Boolean) -> Unit,
    onSurahClick: (Int, String?) -> Unit = { _, _ -> },
    onDuaClick: (UserNewsResource) -> Unit = { _ -> },
    onNoteClick: (surahNumber: Int, ayahNumber: Int) -> Unit = { _, _ -> },
    onLoadMore: () -> Unit = {},
) {
    val state = rememberLazyStaggeredGridState()

    // Detect when user scrolls near bottom to trigger load more
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = state.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            val totalItems = state.layoutInfo.totalItemsCount
            // Load more when within 3 items of the end
            lastVisibleItem.index >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore, paginationState.isLoading, paginationState.hasMoreResults) {
        if (shouldLoadMore && !paginationState.isLoading && paginationState.hasMoreResults) {
            onLoadMore()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(300.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 24.dp,
            modifier = Modifier
                .fillMaxSize()
                .testTag("search:newsResources"),
            state = state,
        ) {
            if (topics.isNotEmpty()) {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(stringResource(id = searchR.string.feature_search_topics))
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                topics.forEach { followableTopic ->
                    val topicId = followableTopic.topic.id
                    item(
                        // Append a prefix to distinguish a key for news resources
                        key = "topic-$topicId",
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        InterestsItem(
                            name = followableTopic.topic.name,
                            following = followableTopic.isFollowed,
                            description = followableTopic.topic.shortDescription,
                            topicImageUrl = followableTopic.topic.imageUrl,
                            onClick = {
                                // Pass the current search query to ViewModel to save it as recent searches
                                onSearchTriggered(searchQuery)
                                onTopicClick(topicId)
                            },
                            onFollowButtonClick = { onFollowButtonClick(topicId, it) },
                        )
                    }
                }
            }

            if (newsResources.isNotEmpty()) {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(stringResource(id = searchR.string.feature_search_updates))
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                newsFeed(
                    feedState = Success(feed = newsResources),
                    onNewsResourcesCheckedChanged = onNewsResourcesCheckedChanged,
                    onNewsResourceViewed = onNewsResourceViewed,
                    onTopicClick = onTopicClick,
                    onSurahClick = onSurahClick,
                    onDuaClick = onDuaClick,
                    onExpandedCardClick = {
                        onSearchTriggered(searchQuery)
                    },
                    searchQuery = searchQuery,
                )
            }

            // Notes section
            if (notes.isNotEmpty()) {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("My Notes")
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                notes.forEach { note ->
                    item(
                        key = "note-${note.id}",
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        NoteSearchItem(
                            note = note,
                            onClick = {
                                onSearchTriggered(searchQuery)
                                onNoteClick(note.surahNumber, note.ayahNumber)
                            },
                        )
                    }
                }
            }

            // Suggested verses section
            if (suggestedVerses.isNotEmpty()) {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Suggested Verses")
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                suggestedVerses.forEach { verse ->
                    item(
                        key = "verse-${verse.surahNumber}-${verse.ayahNumber}",
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        SuggestedVerseItem(
                            verse = verse,
                            onClick = {
                                onSearchTriggered(searchQuery)
                                onNoteClick(verse.surahNumber, verse.ayahNumber)
                            },
                        )
                    }
                }
            }

            // Loading indicator for pagination
            if (paginationState.isLoading) {
                item(
                    key = "loading-indicator",
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }

            // Show "no more results" message when all results are loaded
            if (!paginationState.hasMoreResults && newsResources.isNotEmpty()) {
                item(
                    key = "end-of-results",
                    span = StaggeredGridItemSpan.FullLine,
                ) {
                    Text(
                        text = "All results loaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }
        }
        val itemsAvailable = topics.size + newsResources.size + notes.size + suggestedVerses.size +
            (if (paginationState.isLoading) 1 else 0) +
            (if (!paginationState.hasMoreResults && newsResources.isNotEmpty()) 1 else 0)
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

@Composable
private fun RecentSearchesBody(
    recentSearchQueries: List<String>,
    onClearRecentSearches: () -> Unit,
    onRecentSearchClicked: (String) -> Unit,
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(stringResource(id = searchR.string.feature_search_recent_searches))
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (recentSearchQueries.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onClearRecentSearches()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Icon(
                        imageVector = NiaIcons.Close,
                        contentDescription = stringResource(
                            id = searchR.string.feature_search_clear_recent_searches_content_desc,
                        ),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(recentSearchQueries) { recentSearch ->
                Text(
                    text = recentSearch,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .clickable { onRecentSearchClicked(recentSearch) }
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SearchToolbar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSearchTriggered: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = { onBackClick() }) {
            Icon(
                imageVector = NiaIcons.ArrowBack,
                contentDescription = stringResource(
                    id = string.core_ui_back,
                ),
            )
        }
        SearchTextField(
            onSearchQueryChanged = onSearchQueryChanged,
            onSearchTriggered = onSearchTriggered,
            searchQuery = searchQuery,
        )
    }
}

@Composable
private fun SearchTextField(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSearchTriggered: (String) -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Voice search state
    var isVoiceListening by remember { mutableStateOf(false) }
    var partialVoiceText by remember { mutableStateOf("") }
    var voiceStatusMessage by remember { mutableStateOf<String?>(null) }
    var activeVoiceService by remember { mutableStateOf("none") } // "sherpa", "whisper", "cloud", "none"
    var isUsingWhisper by remember { mutableStateOf(false) }

    // Voice services - Whisper (primary), Sherpa (backup), Cloud (fallback)
    val whisperService = remember { WhisperVoiceService(context) }
    val sherpaService = remember { SherpaVoiceService(context) }
    val cloudVoiceService = remember { VoiceSearchService(context) }

    // Initialize voice services in background
    LaunchedEffect(Unit) {
        whisperService.initialize()
        sherpaService.initialize()
    }

    // Observe Whisper status
    val whisperStatus by whisperService.statusMessage.collectAsStateWithLifecycle()
    val isWhisperInitialized by whisperService.isInitialized.collectAsStateWithLifecycle()
    val isWhisperTranscribing by whisperService.isTranscribing.collectAsStateWithLifecycle()

    // Observe Sherpa status
    val isSherpaInitialized by sherpaService.isInitialized.collectAsStateWithLifecycle()
    val sherpaRecognizedText by sherpaService.recognizedText.collectAsStateWithLifecycle()

    // Observe partial results from Sherpa (real-time)
    LaunchedEffect(sherpaRecognizedText) {
        if (isVoiceListening && isUsingWhisper && !sherpaRecognizedText.isNullOrBlank()) {
            partialVoiceText = sherpaRecognizedText ?: ""
            onSearchQueryChanged(partialVoiceText)
        }
    }

    // Observe partial results from cloud service
    val cloudRecognizedText by cloudVoiceService.recognizedText.collectAsStateWithLifecycle()
    LaunchedEffect(cloudRecognizedText) {
        if (isVoiceListening && !isUsingWhisper && !cloudRecognizedText.isNullOrBlank()) {
            partialVoiceText = cloudRecognizedText ?: ""
            onSearchQueryChanged(partialVoiceText)
        }
    }

    // Update status message
    LaunchedEffect(whisperStatus, isWhisperTranscribing) {
        voiceStatusMessage = when {
            isWhisperTranscribing -> "Transcribing..."
            whisperStatus != null -> whisperStatus
            else -> null
        }
    }

    val onSearchExplicitlyTriggered = {
        keyboardController?.hide()
        onSearchTriggered(searchQuery)
    }

    // Start voice search - uses Whisper (offline) if available, falls back to cloud
    val startVoiceSearch = {
        if (!isVoiceListening) {
            isVoiceListening = true
            partialVoiceText = ""
            keyboardController?.hide()

            val handleResult: (VoiceSearchService.VoiceSearchResult) -> Unit = { result ->
                isVoiceListening = false
                voiceStatusMessage = null
                when (result) {
                    is VoiceSearchService.VoiceSearchResult.Success -> {
                        onSearchQueryChanged(result.text)
                        onSearchTriggered(result.text)
                    }
                    is VoiceSearchService.VoiceSearchResult.Error -> {
                        // Error handled silently - user can try again
                    }
                    VoiceSearchService.VoiceSearchResult.Cancelled -> {
                        // Cancelled - no action needed
                    }
                }
            }

            // Priority: Whisper (accurate) > Sherpa (fast backup) > Cloud (fallback)
            if (isWhisperInitialized) {
                // Use Whisper for accurate offline recognition
                activeVoiceService = "whisper"
                isUsingWhisper = true
                voiceStatusMessage = "Recording..."
                whisperService.startListening(handleResult)
            } else if (isSherpaInitialized) {
                // Fall back to Sherpa for fast real-time recognition
                activeVoiceService = "sherpa"
                isUsingWhisper = true
                voiceStatusMessage = "Listening..."
                sherpaService.startListening(handleResult)
            } else {
                // Fall back to cloud if no offline service ready
                activeVoiceService = "cloud"
                isUsingWhisper = false
                voiceStatusMessage = "Listening..."
                cloudVoiceService.startListening(handleResult)
            }
        }
    }

    // Stop voice search
    val stopVoiceSearch = {
        if (isVoiceListening) {
            isVoiceListening = false
            voiceStatusMessage = "Processing..."
            when (activeVoiceService) {
                "sherpa" -> sherpaService.stopListening()
                "whisper" -> whisperService.stopListening()
                "cloud" -> cloudVoiceService.stopListening()
            }
            activeVoiceService = "none"
        }
    }

    TextField(
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        leadingIcon = {
            Icon(
                imageVector = NiaIcons.Search,
                contentDescription = stringResource(
                    id = searchR.string.feature_search_title,
                ),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        },
        trailingIcon = {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                // Voice search button - tap to start, tap again to stop
                IconButton(
                    onClick = {
                        if (isVoiceListening) {
                            stopVoiceSearch()
                        } else {
                            startVoiceSearch()
                        }
                    },
                ) {
                    if (isVoiceListening) {
                        // Show stop icon with recording indicator
                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                            // Pulsing circle background
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier.size(40.dp)
                            ) {
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.2f),
                                    radius = size.minDimension / 2
                                )
                            }
                            Icon(
                                imageVector = NiaIcons.Close,
                                contentDescription = "Stop recording",
                                tint = androidx.compose.ui.graphics.Color.Red,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    } else if (isWhisperTranscribing) {
                        // Show loading indicator while transcribing
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = NiaIcons.Mic,
                            contentDescription = "Voice search",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                // Clear button - hide when voice is listening (stop button already shows X)
                if (searchQuery.isNotEmpty() && !isVoiceListening) {
                    IconButton(
                        onClick = {
                            onSearchQueryChanged("")
                        },
                    ) {
                        Icon(
                            imageVector = NiaIcons.Close,
                            contentDescription = stringResource(
                                id = searchR.string.feature_search_clear_search_text_content_desc,
                            ),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        placeholder = {
            when {
                isVoiceListening -> {
                    Text(
                        text = "Recording.. Tap X to finish.",
                        color = androidx.compose.ui.graphics.Color.Red,
                        maxLines = 1,
                    )
                }
                isWhisperTranscribing -> {
                    Text(
                        text = "Transcribing...",
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                searchQuery.isEmpty() -> {
                    Text(
                        text = when {
                            isWhisperInitialized -> "Whisper ready"
                            isSherpaInitialized -> "Sherpa ready"
                            else -> "Loading..."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        onValueChange = {
            if ("\n" !in it) onSearchQueryChanged(it)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .focusRequester(focusRequester)
            .onKeyEvent {
                if (it.key == Key.Enter) {
                    if (searchQuery.isBlank()) return@onKeyEvent false
                    onSearchExplicitlyTriggered()
                    true
                } else {
                    false
                }
            }
            .testTag("searchTextField"),
        shape = RoundedCornerShape(32.dp),
        value = searchQuery,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                if (searchQuery.isBlank()) return@KeyboardActions
                onSearchExplicitlyTriggered()
            },
        ),
        maxLines = 1,
        singleLine = true,
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun NoteSearchItem(
    note: SearchNote,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Surah:Ayah badge
        Text(
            text = "${note.surahNumber}:${note.ayahNumber}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp),
        )

        // Note text
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = note.noteText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Tap to go to ayah",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestedVersesBody(
    suggestedVerses: List<SuggestedVerse>,
    onVerseClick: (SuggestedVerse) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("Popular Verses")
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            items(suggestedVerses) { verse ->
                SuggestedVerseItem(
                    verse = verse,
                    onClick = { onVerseClick(verse) },
                )
            }
        }
    }
}

@Composable
private fun SuggestedVerseItem(
    verse: SuggestedVerse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Surah:Ayah badge
        Text(
            text = "${verse.surahNumber}:${verse.ayahNumber}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 2.dp),
        )

        // Verse info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = verse.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = verse.arabicName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = verse.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        // Category badge
        Text(
            text = verse.category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview
@Composable
private fun SearchToolbarPreview() {
    NiaTheme {
        SearchToolbar(
            searchQuery = "",
            onBackClick = {},
            onSearchQueryChanged = {},
            onSearchTriggered = {},
        )
    }
}

@Preview
@Composable
private fun EmptySearchResultColumnPreview() {
    NiaTheme {
        EmptySearchResultBody(
            onInterestsClick = {},
            searchQuery = "C++",
        )
    }
}

@Preview
@Composable
private fun RecentSearchesBodyPreview() {
    NiaTheme {
        RecentSearchesBody(
            onClearRecentSearches = {},
            onRecentSearchClicked = {},
            recentSearchQueries = listOf("kotlin", "jetpack compose", "testing"),
        )
    }
}

@Preview
@Composable
private fun SearchNotReadyBodyPreview() {
    NiaTheme {
        SearchNotReadyBody()
    }
}

@DevicePreviews
@Composable
private fun SearchScreenPreview(
    @PreviewParameter(SearchUiStatePreviewParameterProvider::class)
    searchResultUiState: SearchResultUiState,
) {
    NiaTheme {
        SearchScreen(searchResultUiState = searchResultUiState)
    }
}
