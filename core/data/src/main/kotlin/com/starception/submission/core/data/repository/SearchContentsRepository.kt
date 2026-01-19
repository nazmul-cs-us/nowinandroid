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

package com.starception.submission.core.data.repository

import com.starception.submission.core.model.data.SearchResult
import kotlinx.coroutines.flow.Flow

/**
 * Data layer interface for the search feature.
 */
interface SearchContentsRepository {

    /**
     * Populate the fts tables for the search contents.
     */
    suspend fun populateFtsData()

    /**
     * Query the contents matched with the [searchQuery] and returns it as a [Flow] of [SearchResult]
     */
    fun searchContents(searchQuery: String): Flow<SearchResult>

    /**
     * Paginated search for contents.
     * @param searchQuery The search query
     * @param page Page number (0-indexed)
     * @param pageSize Number of items per page
     * @return SearchResult with paginated results
     */
    suspend fun searchContentsPaginated(
        searchQuery: String,
        page: Int = 0,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): SearchResult

    /**
     * Get total count of search results for a query.
     */
    suspend fun getSearchResultsCount(searchQuery: String): SearchResultsCount

    fun getSearchContentsCount(): Flow<Int>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}

/**
 * Holds the count of search results for news resources and topics.
 */
data class SearchResultsCount(
    val newsResourcesCount: Int,
    val topicsCount: Int,
) {
    val totalCount: Int get() = newsResourcesCount + topicsCount
}
