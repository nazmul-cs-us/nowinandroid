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

package com.starception.submission.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.starception.submission.core.database.model.TopicFtsEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [TopicFtsEntity] access.
 */
@Dao
interface TopicFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(topics: List<TopicFtsEntity>)

    @Query("""
        SELECT topicId FROM topicsFts
        WHERE topicsFts MATCH :query
        ORDER BY CASE
            WHEN lower(name) = lower(:rawQuery) THEN 0
            WHEN lower(name) LIKE lower(:rawQuery) || '%' THEN 1
            WHEN lower(name) LIKE '%' || lower(:rawQuery) || '%' THEN 2
            ELSE 3
        END, length(name), rowid
    """)
    fun searchAllTopics(query: String, rawQuery: String): Flow<List<String>>

    /**
     * Paginated search for topics.
     * @param query FTS search query
     * @param limit Maximum number of results to return
     * @param offset Number of results to skip
     */
    @Query("""
        SELECT topicId FROM topicsFts
        WHERE topicsFts MATCH :query
        ORDER BY CASE
            WHEN lower(name) = lower(:rawQuery) THEN 0
            WHEN lower(name) LIKE lower(:rawQuery) || '%' THEN 1
            WHEN lower(name) LIKE '%' || lower(:rawQuery) || '%' THEN 2
            ELSE 3
        END, length(name), rowid
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchTopicsPaginated(
        query: String,
        rawQuery: String,
        limit: Int,
        offset: Int,
    ): List<String>

    /**
     * Get total count of search results for a query.
     */
    @Query("SELECT count(*) FROM topicsFts WHERE topicsFts MATCH :query")
    suspend fun getSearchResultCount(query: String): Int

    @Query("SELECT count(*) FROM topicsFts")
    fun getCount(): Flow<Int>
}
