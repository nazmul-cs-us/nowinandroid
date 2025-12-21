package com.starception.submission.core.contentdatabase

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

    @Query("SELECT * FROM topics ORDER BY id ASC")
    suspend fun getAllTopics(): List<TopicEntity>

    @Query("SELECT * FROM topics ORDER BY id ASC")
    fun getAllTopicsFlow(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE id = :topicId")
    suspend fun getTopicById(topicId: Int): TopicEntity?

    @Query("SELECT * FROM topics WHERE id = :topicId")
    fun getTopicByIdFlow(topicId: Int): Flow<TopicEntity?>

    @Query("SELECT * FROM topics WHERE id IN (:ids)")
    suspend fun getTopicsByIds(ids: List<Int>): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE is_system = 1 ORDER BY id ASC")
    suspend fun getSystemTopics(): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE is_user_created = 1 ORDER BY id ASC")
    suspend fun getUserTopics(): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE is_user_created = 1 ORDER BY id ASC")
    fun getUserTopicsFlow(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE name LIKE '%' || :query || '%' ORDER BY id ASC")
    suspend fun searchTopics(query: String): List<TopicEntity>

    @Query("SELECT COUNT(*) FROM topics")
    suspend fun getTopicCount(): Int

    @Query("SELECT COUNT(*) FROM topics WHERE is_user_created = 1")
    suspend fun getUserTopicCount(): Int

    // ============= Write Operations =============

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: TopicEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<TopicEntity>)

    @Update
    suspend fun updateTopic(topic: TopicEntity)

    @Delete
    suspend fun deleteTopic(topic: TopicEntity)

    @Query("DELETE FROM topics WHERE id = :topicId")
    suspend fun deleteTopicById(topicId: Int)

    @Query("DELETE FROM topics WHERE is_user_created = 1")
    suspend fun deleteAllUserTopics()

    @Query("SELECT COALESCE(MAX(id), 999) + 1 FROM topics WHERE id >= 1000")
    suspend fun getNextUserTopicId(): Int
}
