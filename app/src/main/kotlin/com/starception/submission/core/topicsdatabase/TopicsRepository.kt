package com.starception.submission.core.topicsdatabase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Topics database
 * Provides a clean API for accessing and managing topics
 */
@Singleton
class TopicsRepository @Inject constructor(
    private val topicsDao: TopicsDao
) {

    // ============= Read Operations =============

    /**
     * Get all topics
     */
    suspend fun getAllTopics(): List<Topic> {
        return topicsDao.getAllTopics().map { it.toTopic() }
    }

    /**
     * Get all topics as Flow
     */
    fun getAllTopicsFlow(): Flow<List<Topic>> {
        return topicsDao.getAllTopicsFlow().map { topics ->
            topics.map { it.toTopic() }
        }
    }

    /**
     * Get topic by ID
     */
    suspend fun getTopicById(topicId: String): Topic? {
        val id = topicId.toIntOrNull() ?: return null
        return topicsDao.getTopicById(id)?.toTopic()
    }

    /**
     * Get topic by ID as Flow
     */
    fun getTopicByIdFlow(topicId: String): Flow<Topic?> {
        val id = topicId.toIntOrNull() ?: return kotlinx.coroutines.flow.flowOf(null)
        return topicsDao.getTopicByIdFlow(id).map { it?.toTopic() }
    }

    /**
     * Get system topics only
     */
    suspend fun getSystemTopics(): List<Topic> {
        return topicsDao.getSystemTopics().map { it.toTopic() }
    }

    /**
     * Get user-created topics
     */
    suspend fun getUserTopics(): List<Topic> {
        return topicsDao.getUserTopics().map { it.toTopic() }
    }

    /**
     * Get user-created topics as Flow
     */
    fun getUserTopicsFlow(): Flow<List<Topic>> {
        return topicsDao.getUserTopicsFlow().map { topics ->
            topics.map { it.toTopic() }
        }
    }

    /**
     * Search topics by name
     */
    suspend fun searchTopics(query: String): List<Topic> {
        return topicsDao.searchTopics(query).map { it.toTopic() }
    }

    /**
     * Get topic count
     */
    suspend fun getTopicCount(): Int {
        return topicsDao.getTopicCount()
    }

    /**
     * Get user topic count
     */
    suspend fun getUserTopicCount(): Int {
        return topicsDao.getUserTopicCount()
    }

    // ============= Write Operations =============

    /**
     * Create a new user topic
     */
    suspend fun createUserTopic(
        name: String,
        shortDescription: String = "",
        longDescription: String = "",
        imageUrl: String = "",
        icon: String? = null
    ): Topic {
        val nextId = topicsDao.getNextUserTopicId()
        val entity = TopicEntity(
            id = nextId,
            name = name,
            shortDescription = shortDescription,
            longDescription = longDescription,
            imageUrl = imageUrl,
            url = "",
            icon = icon,
            isSystem = 0,
            isUserCreated = 1,
            createdAt = java.time.Instant.now().toString(),
            updatedAt = null
        )
        topicsDao.insertTopic(entity)
        return entity.toTopic()
    }

    /**
     * Update a user topic
     */
    suspend fun updateUserTopic(topic: Topic): Boolean {
        // Only allow updating user-created topics
        val existing = topicsDao.getTopicById(topic.id.toIntOrNull() ?: return false)
        if (existing == null || existing.isUserCreated != 1) {
            return false
        }

        val updatedEntity = topic.toEntity().copy(
            isUserCreated = 1,
            isSystem = 0,
            updatedAt = java.time.Instant.now().toString()
        )
        topicsDao.updateTopic(updatedEntity)
        return true
    }

    /**
     * Delete a user topic
     */
    suspend fun deleteUserTopic(topicId: String): Boolean {
        val id = topicId.toIntOrNull() ?: return false
        val existing = topicsDao.getTopicById(id)

        // Only allow deleting user-created topics
        if (existing == null || existing.isUserCreated != 1) {
            return false
        }

        topicsDao.deleteTopicById(id)
        return true
    }

    /**
     * Delete all user topics
     */
    suspend fun deleteAllUserTopics() {
        topicsDao.deleteAllUserTopics()
    }
}
