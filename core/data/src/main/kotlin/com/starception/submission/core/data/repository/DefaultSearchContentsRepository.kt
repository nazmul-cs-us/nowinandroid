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

import com.starception.submission.core.contentdatabase.NewsDao
import com.starception.submission.core.contentdatabase.NewsResourceWithTopics
import com.starception.submission.core.contentdatabase.TopicEntity
import com.starception.submission.core.contentdatabase.TopicsDao
import com.starception.submission.core.database.dao.NewsResourceFtsDao
import com.starception.submission.core.database.dao.TopicFtsDao
import com.starception.submission.core.database.model.NewsResourceFtsEntity
import com.starception.submission.core.database.model.TopicFtsEntity
import com.starception.submission.core.model.data.NewsResource
import com.starception.submission.core.model.data.SearchResult
import com.starception.submission.core.model.data.Topic
import com.starception.submission.core.network.Dispatcher
import com.starception.submission.core.network.NiaDispatchers.IO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import javax.inject.Inject

internal class DefaultSearchContentsRepository @Inject constructor(
    private val newsResourceFtsDao: NewsResourceFtsDao,
    private val topicFtsDao: TopicFtsDao,
    private val contentNewsDao: NewsDao,
    private val contentTopicsDao: TopicsDao,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : SearchContentsRepository {

    override suspend fun populateFtsData() {
        withContext(ioDispatcher) {
            // Populate FTS from content database (pre-populated asset database)
            val newsResources = contentNewsDao.getAllNewsResources()
            val ftsEntities = newsResources.map { entity ->
                NewsResourceFtsEntity(
                    newsResourceId = entity.id.toString(),
                    title = entity.title,
                    content = entity.content ?: "",
                )
            }
            newsResourceFtsDao.insertAll(ftsEntities)

            // Populate topics FTS from content database
            val topics = contentTopicsDao.getAllTopics()
            val topicFtsEntities = topics.map { entity ->
                TopicFtsEntity(
                    topicId = entity.id.toString(),
                    name = entity.name,
                    shortDescription = entity.shortDescription ?: "",
                    longDescription = entity.longDescription ?: "",
                )
            }
            topicFtsDao.insertAll(topicFtsEntities)
        }
    }

    /**
     * Reactive search using Flows - uses first page of paginated results for efficiency.
     * This maintains backward compatibility while being efficient.
     */
    override fun searchContents(searchQuery: String): Flow<SearchResult> {
        // SQLite FTS3/4 only supports trailing wildcards — `*query*` silently
        // matches nothing (parsed as a literal token), which is why obvious hits
        // like the "Distress & Anxiety" topic used to disappear. Use prefix
        // matching instead so any token starting with the query is found.
        val ftsQuery = "$searchQuery*"

        // Use paginated approach with first page only for Flow-based search
        val newsResourceIds = newsResourceFtsDao.searchAllNewsResources(ftsQuery, searchQuery)
        val topicIds = topicFtsDao.searchAllTopics(ftsQuery, searchQuery)

        val newsResourcesFlow = newsResourceIds
            .mapLatest { ids ->
                ids.mapNotNull { it.toIntOrNull() }
                    .take(SearchContentsRepository.DEFAULT_PAGE_SIZE)
            }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    contentNewsDao.getNewsResourcesByIdsFlow(ids)
                }
            }

        val topicsFlow = topicIds
            .mapLatest { ids ->
                ids.mapNotNull { it.toIntOrNull() }
                    .take(SearchContentsRepository.DEFAULT_PAGE_SIZE)
            }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    contentTopicsDao.getTopicsByIdsFlow(ids)
                }
            }

        return combine(newsResourcesFlow, topicsFlow) { newsResources, topics ->
            SearchResult(
                topics = topics.map { it.asExternalModel() },
                newsResources = newsResources.map { it.asExternalModel(topics) },
            )
        }
    }

    /**
     * Efficient paginated search - fetches only the requested page of results.
     * Use this for infinite scroll / load more functionality.
     */
    override suspend fun searchContentsPaginated(
        searchQuery: String,
        page: Int,
        pageSize: Int,
    ): SearchResult = withContext(ioDispatcher) {
        val ftsQuery = "$searchQuery*"
        val offset = page * pageSize

        // Fetch paginated IDs from FTS tables
        val newsResourceIds = newsResourceFtsDao
            .searchNewsResourcesPaginated(ftsQuery, searchQuery, pageSize, offset)
            .mapNotNull { it.toIntOrNull() }

        val topicIds = topicFtsDao
            .searchTopicsPaginated(ftsQuery, searchQuery, pageSize, offset)
            .mapNotNull { it.toIntOrNull() }

        // Fetch actual data from content database
        val newsResources = if (newsResourceIds.isNotEmpty()) {
            contentNewsDao.getNewsResourcesByIds(newsResourceIds)
        } else {
            emptyList()
        }

        val topics = if (topicIds.isNotEmpty()) {
            contentTopicsDao.getTopicsByIds(topicIds)
        } else {
            emptyList()
        }

        SearchResult(
            topics = topics.map { it.asExternalModel() },
            newsResources = newsResources.map { it.asExternalModel(topics) },
        )
    }

    /**
     * Get the total count of search results for pagination UI.
     */
    override suspend fun getSearchResultsCount(searchQuery: String): SearchResultsCount =
        withContext(ioDispatcher) {
            val ftsQuery = "$searchQuery*"
            SearchResultsCount(
                newsResourcesCount = newsResourceFtsDao.getSearchResultCount(ftsQuery),
                topicsCount = topicFtsDao.getSearchResultCount(ftsQuery),
            )
        }

    override fun getSearchContentsCount(): Flow<Int> =
        combine(
            newsResourceFtsDao.getCount(),
            topicFtsDao.getCount(),
        ) { newsResourceCount, topicsCount ->
            newsResourceCount + topicsCount
        }
}

/**
 * Convert TopicEntity from content database to external Topic model
 */
private fun TopicEntity.asExternalModel(): Topic = Topic(
    id = id.toString(),
    name = name,
    shortDescription = shortDescription ?: "",
    longDescription = longDescription ?: "",
    url = url ?: "",
    imageUrl = imageUrl ?: "",
)

/**
 * Convert NewsResourceWithTopics from content database to external NewsResource model
 */
private fun NewsResourceWithTopics.asExternalModel(allTopics: List<TopicEntity>): NewsResource {
    // Parse comma-separated topic IDs and map to Topic models
    val topicIdList = topicIds?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
    val matchedTopics = allTopics.filter { it.id in topicIdList }.map { it.asExternalModel() }

    return NewsResource(
        id = id.toString(),
        title = title,
        content = content ?: "",
        url = url ?: "",
        headerImageUrl = headerImageUrl,
        publishDate = parsePublishDate(publishDate),
        type = type ?: "",
        topics = matchedTopics,
    )
}

/**
 * Parse publish date string to Instant
 */
private fun parsePublishDate(dateString: String?): Instant {
    if (dateString.isNullOrBlank()) {
        return Instant.DISTANT_PAST
    }
    return try {
        Instant.parse(dateString)
    } catch (e: Exception) {
        // Try common date format patterns
        Instant.DISTANT_PAST
    }
}
