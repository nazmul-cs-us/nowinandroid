package com.starception.submission.core.topicsdatabase

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Topics database
 * Provides CRUD operations for topics
 */
@Dao
interface TopicsDao {

    // ============= Read Operations =============

    /**
     * Get all topics ordered by ID
     */
    @Query("SELECT * FROM topics ORDER BY id ASC")
    suspend fun getAllTopics(): List<TopicEntity>

    /**
     * Get all topics as Flow for reactive updates
     */
    @Query("SELECT * FROM topics ORDER BY id ASC")
    fun getAllTopicsFlow(): Flow<List<TopicEntity>>

    /**
     * Get topic by ID
     */
    @Query("SELECT * FROM topics WHERE id = :topicId")
    suspend fun getTopicById(topicId: Int): TopicEntity?

    /**
     * Get topic by ID as Flow
     */
    @Query("SELECT * FROM topics WHERE id = :topicId")
    fun getTopicByIdFlow(topicId: Int): Flow<TopicEntity?>

    /**
     * Get topics by list of IDs
     */
    @Query("SELECT * FROM topics WHERE id IN (:ids)")
    suspend fun getTopicsByIds(ids: List<Int>): List<TopicEntity>

    /**
     * Get system topics only (pre-installed)
     */
    @Query("SELECT * FROM topics WHERE is_system = 1 ORDER BY id ASC")
    suspend fun getSystemTopics(): List<TopicEntity>

    /**
     * Get user-created topics only
     */
    @Query("SELECT * FROM topics WHERE is_user_created = 1 ORDER BY id ASC")
    suspend fun getUserTopics(): List<TopicEntity>

    /**
     * Get user-created topics as Flow
     */
    @Query("SELECT * FROM topics WHERE is_user_created = 1 ORDER BY id ASC")
    fun getUserTopicsFlow(): Flow<List<TopicEntity>>

    /**
     * Search topics by name
     */
    @Query("SELECT * FROM topics WHERE name LIKE '%' || :query || '%' ORDER BY id ASC")
    suspend fun searchTopics(query: String): List<TopicEntity>

    /**
     * Get topic count
     */
    @Query("SELECT COUNT(*) FROM topics")
    suspend fun getTopicCount(): Int

    /**
     * Get user topic count
     */
    @Query("SELECT COUNT(*) FROM topics WHERE is_user_created = 1")
    suspend fun getUserTopicCount(): Int

    // ============= Write Operations =============

    /**
     * Insert a new topic
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: TopicEntity): Long

    /**
     * Insert multiple topics
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<TopicEntity>)

    /**
     * Update an existing topic
     */
    @Update
    suspend fun updateTopic(topic: TopicEntity)

    /**
     * Delete a topic
     */
    @Delete
    suspend fun deleteTopic(topic: TopicEntity)

    /**
     * Delete topic by ID
     */
    @Query("DELETE FROM topics WHERE id = :topicId")
    suspend fun deleteTopicById(topicId: Int)

    /**
     * Delete all user-created topics
     */
    @Query("DELETE FROM topics WHERE is_user_created = 1")
    suspend fun deleteAllUserTopics()

    /**
     * Get the next available ID for user topics (starting from 1000)
     */
    @Query("SELECT COALESCE(MAX(id), 999) + 1 FROM topics WHERE id >= 1000")
    suspend fun getNextUserTopicId(): Int
}
