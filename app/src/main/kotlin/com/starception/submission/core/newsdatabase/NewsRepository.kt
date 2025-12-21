package com.starception.submission.core.newsdatabase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for News Resources database
 * Provides a clean API for accessing and managing news resources
 */
@Singleton
class NewsRepository @Inject constructor(
    private val newsDao: NewsDao
) {

    // ============= Read Operations =============

    /**
     * Get all news resources
     */
    suspend fun getAllNewsResources(): List<NewsResource> {
        return newsDao.getAllNewsResources().map { it.toNewsResource() }
    }

    /**
     * Get all news resources as Flow
     */
    fun getAllNewsResourcesFlow(): Flow<List<NewsResource>> {
        return newsDao.getAllNewsResourcesFlow().map { resources ->
            resources.map { it.toNewsResource() }
        }
    }

    /**
     * Get news resource by ID
     */
    suspend fun getNewsResourceById(newsId: String): NewsResource? {
        val id = newsId.toIntOrNull() ?: return null
        return newsDao.getNewsResourceById(id)?.toNewsResource()
    }

    /**
     * Get news resources by topic ID
     */
    suspend fun getNewsResourcesByTopic(topicId: String): List<NewsResource> {
        val id = topicId.toIntOrNull() ?: return emptyList()
        return newsDao.getNewsResourcesByTopic(id).map { it.toNewsResource() }
    }

    /**
     * Get news resources by topic ID as Flow
     */
    fun getNewsResourcesByTopicFlow(topicId: String): Flow<List<NewsResource>> {
        val id = topicId.toIntOrNull() ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return newsDao.getNewsResourcesByTopicFlow(id).map { resources ->
            resources.map { it.toNewsResource() }
        }
    }

    /**
     * Get news resources by type
     */
    suspend fun getNewsResourcesByType(type: String): List<NewsResource> {
        return newsDao.getNewsResourcesByType(type).map { it.toNewsResource() }
    }

    /**
     * Search news resources
     */
    suspend fun searchNewsResources(query: String, limit: Int = 100): List<NewsResource> {
        return newsDao.searchNewsResources(query, limit).map { it.toNewsResource() }
    }

    /**
     * Get all Duas
     */
    suspend fun getAllDuas(): List<NewsResource> {
        return newsDao.getAllDuas().map { it.toNewsResource() }
    }

    /**
     * Get user-created news resources
     */
    suspend fun getUserNewsResources(): List<NewsResource> {
        return newsDao.getUserNewsResources().map { it.toNewsResource() }
    }

    /**
     * Get user-created news resources as Flow
     */
    fun getUserNewsResourcesFlow(): Flow<List<NewsResource>> {
        return newsDao.getUserNewsResourcesFlow().map { resources ->
            resources.map { it.toNewsResource() }
        }
    }

    /**
     * Get news resource count
     */
    suspend fun getNewsResourceCount(): Int {
        return newsDao.getNewsResourceCount()
    }

    /**
     * Get news resource count by topic
     */
    suspend fun getNewsResourceCountByTopic(topicId: String): Int {
        val id = topicId.toIntOrNull() ?: return 0
        return newsDao.getNewsResourceCountByTopic(id)
    }

    // ============= Write Operations =============

    /**
     * Create a new user news resource (e.g., user-created dua)
     */
    suspend fun createUserNewsResource(
        title: String,
        content: String,
        type: String = "Dua 🤲",
        topicIds: List<String> = emptyList()
    ): NewsResource {
        val nextId = newsDao.getNextUserNewsResourceId()
        val entity = NewsResourceEntity(
            id = nextId,
            title = title,
            content = content,
            url = "",
            headerImageUrl = "",
            publishDate = java.time.Instant.now().toString(),
            type = type,
            isSystem = 0,
            isUserCreated = 1,
            source = "user",
            createdAt = java.time.Instant.now().toString(),
            updatedAt = null
        )

        val topicIntIds = topicIds.mapNotNull { it.toIntOrNull() }
        newsDao.insertNewsResourceWithTopics(entity, topicIntIds)

        return entity.toNewsResource(topicIds)
    }

    /**
     * Update a user news resource
     */
    suspend fun updateUserNewsResource(
        newsResource: NewsResource,
        topicIds: List<String> = newsResource.topicIds
    ): Boolean {
        val newsId = newsResource.id.toIntOrNull() ?: return false
        val existing = newsDao.getNewsResourceById(newsId)

        // Only allow updating user-created resources
        if (existing == null || existing.isUserCreated != 1) {
            return false
        }

        val updatedEntity = newsResource.toEntity().copy(
            isUserCreated = 1,
            isSystem = 0,
            updatedAt = java.time.Instant.now().toString()
        )

        val topicIntIds = topicIds.mapNotNull { it.toIntOrNull() }
        newsDao.updateNewsResourceWithTopics(updatedEntity, topicIntIds)

        return true
    }

    /**
     * Delete a user news resource
     */
    suspend fun deleteUserNewsResource(newsId: String): Boolean {
        val id = newsId.toIntOrNull() ?: return false
        val existing = newsDao.getNewsResourceById(id)

        // Only allow deleting user-created resources
        if (existing == null || existing.isUserCreated != 1) {
            return false
        }

        newsDao.deleteNewsTopics(id)
        newsDao.deleteNewsResourceById(id)
        return true
    }

    /**
     * Delete all user news resources
     */
    suspend fun deleteAllUserNewsResources() {
        newsDao.deleteAllUserNewsResources()
    }
}
