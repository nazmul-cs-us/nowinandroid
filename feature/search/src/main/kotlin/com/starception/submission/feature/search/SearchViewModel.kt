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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starception.submission.core.analytics.AnalyticsEvent
import com.starception.submission.core.analytics.AnalyticsEvent.Param
import com.starception.submission.core.analytics.AnalyticsHelper
import com.starception.submission.core.data.repository.RecentSearchRepository
import com.starception.submission.core.data.repository.SearchContentsRepository
import com.starception.submission.core.data.repository.UserDataRepository
import com.starception.submission.core.domain.GetRecentSearchQueriesUseCase
import com.starception.submission.core.domain.GetSearchContentsUseCase
import com.starception.submission.core.model.data.NewsResource
import com.starception.submission.core.model.data.Topic
import com.starception.submission.core.model.data.UserSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    getSearchContentsUseCase: GetSearchContentsUseCase,
    recentSearchQueriesUseCase: GetRecentSearchQueriesUseCase,
    private val searchContentsRepository: SearchContentsRepository,
    private val recentSearchRepository: RecentSearchRepository,
    private val userDataRepository: UserDataRepository,
    private val savedStateHandle: SavedStateHandle,
    private val analyticsHelper: AnalyticsHelper,
) : ViewModel() {

    val searchQuery = savedStateHandle.getStateFlow(key = SEARCH_QUERY, initialValue = "")

    // Pagination state
    private val _paginationState = MutableStateFlow(PaginationState())
    val paginationState: StateFlow<PaginationState> = _paginationState.asStateFlow()

    val searchResultUiState: StateFlow<SearchResultUiState> =
        searchContentsRepository.getSearchContentsCount()
            .flatMapLatest { totalCount ->
                android.util.Log.d("SearchViewModel", "🔍 FTS total count: $totalCount")
                if (totalCount < SEARCH_MIN_FTS_ENTITY_COUNT) {
                    android.util.Log.w("SearchViewModel", "⚠️ Search not ready - FTS count ($totalCount) < $SEARCH_MIN_FTS_ENTITY_COUNT")
                    flowOf(SearchResultUiState.SearchNotReady)
                } else {
                    searchQuery.flatMapLatest { query ->
                        if (query.trim().length < SEARCH_QUERY_MIN_LENGTH) {
                            flowOf(SearchResultUiState.EmptyQuery)
                        } else {
                            android.util.Log.d("SearchViewModel", "🔎 Searching for: '$query'")
                            getSearchContentsUseCase(query)
                                // Not using .asResult() here, because it emits Loading state every
                                // time the user types a letter in the search box, which flickers the screen.
                                .map<UserSearchResult, SearchResultUiState> { data ->
                                    android.util.Log.d("SearchViewModel", "📊 Search results: topics=${data.topics.size}, newsResources=${data.newsResources.size}")
                                    SearchResultUiState.Success(
                                        topics = data.topics,
                                        newsResources = data.newsResources,
                                    )
                                }
                                .catch {
                                    android.util.Log.e("SearchViewModel", "❌ Search failed", it)
                                    emit(SearchResultUiState.LoadFailed)
                                }
                        }
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SearchResultUiState.Loading,
            )

    val recentSearchQueriesUiState: StateFlow<RecentSearchQueriesUiState> =
        recentSearchQueriesUseCase()
            .map(RecentSearchQueriesUiState::Success)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RecentSearchQueriesUiState.Loading,
            )

    fun onSearchQueryChanged(query: String) {
        savedStateHandle[SEARCH_QUERY] = query
        resetPaginationState() // Reset pagination when query changes
    }

    /**
     * Called when the search action is explicitly triggered by the user. For example, when the
     * search icon is tapped in the IME or when the enter key is pressed in the search text field.
     *
     * The search results are displayed on the fly as the user types, but to explicitly save the
     * search query in the search text field, defining this method.
     */
    fun onSearchTriggered(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            recentSearchRepository.insertOrReplaceRecentSearch(searchQuery = query)
        }
        analyticsHelper.logEventSearchTriggered(query = query)
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            recentSearchRepository.clearRecentSearches()
        }
    }

    fun setNewsResourceBookmarked(newsResourceId: String, isChecked: Boolean) {
        viewModelScope.launch {
            userDataRepository.setNewsResourceBookmarked(newsResourceId, isChecked)
        }
    }

    fun followTopic(followedTopicId: String, followed: Boolean) {
        viewModelScope.launch {
            userDataRepository.setTopicIdFollowed(followedTopicId, followed)
        }
    }

    fun setNewsResourceViewed(newsResourceId: String, viewed: Boolean) {
        viewModelScope.launch {
            userDataRepository.setNewsResourceViewed(newsResourceId, viewed)
        }
    }

    /**
     * Load more search results (pagination).
     * Call this when user scrolls to the bottom of the list.
     */
    fun loadMoreResults() {
        val currentQuery = searchQuery.value
        if (currentQuery.isBlank() || _paginationState.value.isLoading || !_paginationState.value.hasMoreResults) {
            return
        }

        viewModelScope.launch {
            _paginationState.update { it.copy(isLoading = true) }
            try {
                val nextPage = _paginationState.value.currentPage + 1
                val newResults = searchContentsRepository.searchContentsPaginated(
                    searchQuery = currentQuery,
                    page = nextPage,
                    pageSize = PAGE_SIZE,
                )

                val hasMore = newResults.newsResources.isNotEmpty() || newResults.topics.isNotEmpty()

                _paginationState.update { state ->
                    state.copy(
                        currentPage = nextPage,
                        isLoading = false,
                        hasMoreResults = hasMore,
                        additionalNewsResources = state.additionalNewsResources + newResults.newsResources,
                        additionalTopics = state.additionalTopics + newResults.topics,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SearchViewModel", "Failed to load more results", e)
                _paginationState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Reset pagination state when search query changes.
     */
    private fun resetPaginationState() {
        _paginationState.value = PaginationState()
    }
}

private fun AnalyticsHelper.logEventSearchTriggered(query: String) =
    logEvent(
        event = AnalyticsEvent(
            type = SEARCH_QUERY,
            extras = listOf(element = Param(key = SEARCH_QUERY, value = query)),
        ),
    )

/** Minimum length where search query is considered as [SearchResultUiState.EmptyQuery] */
private const val SEARCH_QUERY_MIN_LENGTH = 2

/** Minimum number of the fts table's entity count where it's considered as search is not ready */
private const val SEARCH_MIN_FTS_ENTITY_COUNT = 1
private const val SEARCH_QUERY = "searchQuery"

/** Page size for paginated search results */
private const val PAGE_SIZE = 20

/**
 * State for pagination in search results.
 * Stores raw NewsResource and Topic data - these should be combined with UserData at the UI layer
 * to create UserNewsResource and FollowableTopic for display.
 */
data class PaginationState(
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    val hasMoreResults: Boolean = true,
    val additionalNewsResources: List<NewsResource> = emptyList(),
    val additionalTopics: List<Topic> = emptyList(),
    val error: String? = null,
)
