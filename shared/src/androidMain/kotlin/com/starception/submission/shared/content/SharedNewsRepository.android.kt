package com.starception.submission.shared.content

/** Android's feed uses its Room-backed repositories. */
actual fun createSharedNewsRepository(): SharedNewsRepository = object : SharedNewsRepository {
    override suspend fun newsForTopics(
        topicIds: Set<Int>,
        limit: Int,
        offset: Int,
    ): List<SharedNewsResource> = emptyList()

    override suspend fun newsByIds(ids: Set<Int>): List<SharedNewsResource> = emptyList()

    override suspend fun newsForTopic(
        topicId: Int,
        limit: Int,
        offset: Int,
    ): List<SharedNewsResource> = emptyList()

    override suspend fun newsById(id: Int): SharedNewsResource? = null

    override suspend fun searchNews(query: String, limit: Int): List<SharedNewsResource> = emptyList()
}
