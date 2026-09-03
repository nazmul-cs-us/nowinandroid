/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.content

data class SharedNewsResource(
    val id: Int,
    val title: String,
    val content: String,
    val url: String,
    val headerImageUrl: String,
    val publishDate: String,
    val type: String,
    val source: String,
    val topicIds: Set<Int>,
)

interface SharedNewsRepository {
    suspend fun newsForTopics(
        topicIds: Set<Int>,
        limit: Int = 100,
        offset: Int = 0,
    ): List<SharedNewsResource>

    suspend fun newsByIds(ids: Set<Int>): List<SharedNewsResource>

    suspend fun newsForTopic(
        topicId: Int,
        limit: Int = 100,
        offset: Int = 0,
    ): List<SharedNewsResource>

    suspend fun newsById(id: Int): SharedNewsResource?

    suspend fun searchNews(query: String, limit: Int = 30): List<SharedNewsResource>
}

expect fun createSharedNewsRepository(): SharedNewsRepository
