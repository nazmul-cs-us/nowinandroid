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

import com.starception.submission.core.contentdatabase.NewsDao
import com.starception.submission.core.contentdatabase.TopicsDao
import com.starception.submission.core.data.Synchronizer
import com.starception.submission.core.data.model.asExternalModel
import com.starception.submission.core.model.data.NewsResource
import com.starception.submission.core.model.data.Topic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Content database backed implementation of the [NewsRepository].
 * Reads from pre-populated news_resources.db in assets for offline access.
 */
internal class OfflineFirstNewsRepository @Inject constructor(
    private val newsDao: NewsDao,
    private val topicsDao: TopicsDao,
) : NewsRepository {

    override fun getNewsResources(
        query: NewsResourceQuery,
    ): Flow<List<NewsResource>> = newsDao.getAllNewsResourcesFlow()
        .map { newsResources ->
            // Build topics map for efficient lookup
            val allTopics = topicsDao.getAllTopics()
            val topicsMap: Map<Int, Topic> = allTopics.associate { it.id to it.asExternalModel() }

            // Convert to external model
            var resources = newsResources.map { it.asExternalModel(topicsMap) }

            // Apply topic filter if specified
            query.filterTopicIds?.let { filterIds ->
                resources = resources.filter { resource ->
                    resource.topics.any { topic -> topic.id in filterIds }
                }
            }

            // Apply news ID filter if specified
            query.filterNewsIds?.let { filterIds ->
                resources = resources.filter { resource ->
                    resource.id in filterIds
                }
            }

            resources
        }

    /**
     * No network sync needed - data is pre-populated in content database.
     */
    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = true
}
