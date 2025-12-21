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

import com.starception.submission.core.contentdatabase.TopicsDao
import com.starception.submission.core.data.Synchronizer
import com.starception.submission.core.data.model.asExternalModel
import com.starception.submission.core.model.data.Topic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Content database backed implementation of the [TopicsRepository].
 * Reads from pre-populated topics.db in assets for offline access.
 */
internal class OfflineFirstTopicsRepository @Inject constructor(
    private val topicsDao: TopicsDao,
) : TopicsRepository {

    override fun getTopics(): Flow<List<Topic>> =
        topicsDao.getAllTopicsFlow()
            .map { entities -> entities.map { it.asExternalModel() } }

    override fun getTopic(id: String): Flow<Topic> =
        topicsDao.getTopicByIdFlow(id.toIntOrNull() ?: 0)
            .filterNotNull()
            .map { it.asExternalModel() }

    /**
     * No network sync needed - data is pre-populated in content database.
     */
    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = true
}
