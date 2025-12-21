package com.starception.submission.core.newsdatabase

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for News Resources database
 * Provides CRUD operations for news resources
 */
@Dao
interface NewsDao {

    // ============= Read Operations =============

    /**
     * Get all news resources with their topic IDs
     */
    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        GROUP BY n.id
        ORDER BY n.publish_date DESC
    """)
    suspend fun getAllNewsResources(): List<NewsResourceWithTopics>

    /**
     * Get all news resources as Flow
     */
    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        GROUP BY n.id
        ORDER BY n.publish_date DESC
    """)
    fun getAllNewsResourcesFlow(): Flow<List<NewsResourceWithTopics>>

    /**
     * Get news resource by ID
     */
    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        WHERE n.id = :newsId
        GROUP BY n.id
    """)
    suspend fun getNewsResourceById(newsId: Int): NewsResourceWithTopics?

    /**
     * Get news resources by topic ID
     */
    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt2.topic_id) as topicIds
        FROM news_resources n
        INNER JOIN news_topics nt ON n.id = nt.news_id
        LEFT JOIN news_topics nt2 ON n.id = nt2.news_id
        WHERE nt.topic_id = :topicId
        GROUP BY n.id
        ORDER BY n.publish_date DESC
    """)
    suspend fun getNewsResourcesByTopic(topicId: Int): List<NewsResourceWithTopics>

    /**
     * Get news resources by topic ID as Flow
     */
    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt2.topic_id) as topicIds
        FROM news_resources n
        INNER JOIN news_topics nt ON n.id = nt.news_id
        LEFT JOIN news_topics nt2 ON n.id = nt2.news_id
        WHERE nt.topic_id = :topicId
        GROUP BY n.id
        ORDER BY n.publish_date DESC
    """)
    fun getNewsResourcesByTopicFlow(topicId: Int): Flow<List<NewsResourceWithTopics>>

    /**
     * Get news resources by type (e.g., "Dua 🤲", "Quran 📖")
     */
    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        WHERE n.type LIKE '%' || :type || '%'
        GROUP BY n.id
        ORDER BY n.publish_date DESC
    """)
    suspend fun getNewsResourcesByType(type: String): List<NewsResourceWithTopics>

    /**
     * Search news resources by title or content
     */
    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        WHERE n.title LIKE '%' || :query || '%'
           OR n.content LIKE '%' || :query || '%'
        GROUP BY n.id
        ORDER BY n.publish_date DESC
        LIMIT :limit
    """)
    suspend fun searchNewsResources(query: String, limit: Int = 100): List<NewsResourceWithTopics>

    /**
     * Get user-created news resources
     */
    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        WHERE n.is_user_created = 1
        GROUP BY n.id
        ORDER BY n.publish_date DESC
    """)
    suspend fun getUserNewsResources(): List<NewsResourceWithTopics>

    /**
     * Get user-created news resources as Flow
     */
    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        WHERE n.is_user_created = 1
        GROUP BY n.id
        ORDER BY n.publish_date DESC
    """)
    fun getUserNewsResourcesFlow(): Flow<List<NewsResourceWithTopics>>

    /**
     * Get news resource count
     */
    @Query("SELECT COUNT(*) FROM news_resources")
    suspend fun getNewsResourceCount(): Int

    /**
     * Get news resource count by topic
     */
    @Query("SELECT COUNT(DISTINCT news_id) FROM news_topics WHERE topic_id = :topicId")
    suspend fun getNewsResourceCountByTopic(topicId: Int): Int

    /**
     * Get all Dua type news resources
     */
    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        WHERE n.type LIKE '%Dua%'
        GROUP BY n.id
        ORDER BY n.id ASC
    """)
    suspend fun getAllDuas(): List<NewsResourceWithTopics>

    // ============= Write Operations =============

    /**
     * Insert a new news resource
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNewsResource(newsResource: NewsResourceEntity): Long

    /**
     * Insert news topic association
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewsTopicCrossRef(crossRef: NewsTopicCrossRef)

    /**
     * Insert multiple news topic associations
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewsTopicCrossRefs(crossRefs: List<NewsTopicCrossRef>)

    /**
     * Update an existing news resource
     */
    @Update
    suspend fun updateNewsResource(newsResource: NewsResourceEntity)

    /**
     * Delete a news resource
     */
    @Delete
    suspend fun deleteNewsResource(newsResource: NewsResourceEntity)

    /**
     * Delete news resource by ID
     */
    @Query("DELETE FROM news_resources WHERE id = :newsId")
    suspend fun deleteNewsResourceById(newsId: Int)

    /**
     * Delete topic associations for a news resource
     */
    @Query("DELETE FROM news_topics WHERE news_id = :newsId")
    suspend fun deleteNewsTopics(newsId: Int)

    /**
     * Delete all user-created news resources
     */
    @Query("DELETE FROM news_resources WHERE is_user_created = 1")
    suspend fun deleteAllUserNewsResources()

    /**
     * Get the next available ID for user news resources (starting from 10000)
     */
    @Query("SELECT COALESCE(MAX(id), 9999) + 1 FROM news_resources WHERE id >= 10000")
    suspend fun getNextUserNewsResourceId(): Int

    /**
     * Insert news resource with topics (transaction)
     */
    @Transaction
    suspend fun insertNewsResourceWithTopics(
        newsResource: NewsResourceEntity,
        topicIds: List<Int>
    ) {
        insertNewsResource(newsResource)
        val crossRefs = topicIds.map { NewsTopicCrossRef(newsResource.id, it) }
        insertNewsTopicCrossRefs(crossRefs)
    }

    /**
     * Update news resource with topics (transaction)
     */
    @Transaction
    suspend fun updateNewsResourceWithTopics(
        newsResource: NewsResourceEntity,
        topicIds: List<Int>
    ) {
        updateNewsResource(newsResource)
        deleteNewsTopics(newsResource.id)
        val crossRefs = topicIds.map { NewsTopicCrossRef(newsResource.id, it) }
        insertNewsTopicCrossRefs(crossRefs)
    }
}
