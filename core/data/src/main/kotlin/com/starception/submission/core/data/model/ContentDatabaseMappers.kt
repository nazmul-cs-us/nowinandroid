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

package com.starception.submission.core.data.model

import com.starception.submission.core.contentdatabase.NewsResourceWithTopics
import com.starception.submission.core.contentdatabase.TopicEntity as ContentTopicEntity
import com.starception.submission.core.model.data.NewsResource
import com.starception.submission.core.model.data.Topic
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Extension function to convert ContentDatabase TopicEntity to external Topic model
 */
fun ContentTopicEntity.asExternalModel() = Topic(
    id = id.toString(),
    name = name,
    shortDescription = shortDescription ?: "",
    longDescription = longDescription ?: "",
    url = url ?: "",
    imageUrl = imageUrl ?: "",
)

/**
 * Extension function to convert ContentDatabase NewsResourceWithTopics to external NewsResource model
 */
fun NewsResourceWithTopics.asExternalModel(topicsMap: Map<Int, Topic>): NewsResource {
    val topicIdsList = topicIds?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
    val associatedTopics = topicIdsList.mapNotNull { topicsMap[it] }

    return NewsResource(
        id = id.toString(),
        title = title,
        content = content ?: "",
        url = url ?: "",
        headerImageUrl = headerImageUrl,
        publishDate = parsePublishDate(publishDate),
        type = type ?: "Article 📓",
        topics = associatedTopics,
    )
}

/**
 * Parse publish date string to Instant.
 *
 * The news.db generators (NewsDbGenerator, DatabaseSyncHelper, scripts/generate_news_db.py)
 * write local datetimes WITHOUT an offset ("2026-07-17T20:18:01"). Instant.parse requires
 * one, so parse those as device-local time; DISTANT_PAST would otherwise leak to the UI
 * as "Jan 1, 100001".
 */
private fun parsePublishDate(dateString: String?): Instant {
    if (dateString.isNullOrBlank()) {
        return Instant.DISTANT_PAST
    }
    return try {
        Instant.parse(dateString)
    } catch (e: Exception) {
        try {
            LocalDateTime.parse(dateString).toInstant(TimeZone.currentSystemDefault())
        } catch (e: Exception) {
            Instant.DISTANT_PAST
        }
    }
}
