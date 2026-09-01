package com.starception.submission.shared.content

/** The shared navigation host currently ships on iOS; Android uses its Room repositories. */
actual fun createSharedTopicRepository(): SharedTopicRepository = object : SharedTopicRepository {
    override suspend fun topics(): List<SharedTopic> = SharedTopics
    override suspend fun articles(topicId: Int): List<SharedTopicArticle> = emptyList()
}
