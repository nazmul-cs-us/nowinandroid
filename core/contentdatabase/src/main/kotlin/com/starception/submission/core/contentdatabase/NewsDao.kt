package com.starception.submission.core.contentdatabase

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
 */
@Dao
interface NewsDao {

    // ============= Read Operations =============

    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        GROUP BY n.id
        ORDER BY n.id ASC
    """)
    suspend fun getAllNewsResources(): List<NewsResourceWithTopics>

    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        GROUP BY n.id
        ORDER BY n.id ASC
    """)
    fun getAllNewsResourcesFlow(): Flow<List<NewsResourceWithTopics>>

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
        ORDER BY n.id ASC
    """)
    suspend fun getNewsResourcesByTopic(topicId: Int): List<NewsResourceWithTopics>

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
        ORDER BY n.id ASC
    """)
    fun getNewsResourcesByTopicFlow(topicId: Int): Flow<List<NewsResourceWithTopics>>

    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        WHERE n.type LIKE '%' || :type || '%'
        GROUP BY n.id
        ORDER BY n.id ASC
    """)
    suspend fun getNewsResourcesByType(type: String): List<NewsResourceWithTopics>

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
        ORDER BY n.id ASC
        LIMIT :limit
    """)
    suspend fun searchNewsResources(query: String, limit: Int = 100): List<NewsResourceWithTopics>

    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        WHERE n.id IN (:ids)
        GROUP BY n.id
        ORDER BY n.id ASC
    """)
    fun getNewsResourcesByIdsFlow(ids: List<Int>): Flow<List<NewsResourceWithTopics>>

    /**
     * Fetch news resources by IDs (suspend version for paginated queries).
     * This version is more efficient for single fetches without Flow overhead.
     */
    @Query("""
        SELECT n.id, n.title, n.content, n.url, n.header_image_url as headerImageUrl,
               n.publish_date as publishDate, n.type, n.is_system as isSystem,
               n.is_user_created as isUserCreated, n.source,
               GROUP_CONCAT(nt.topic_id) as topicIds
        FROM news_resources n
        LEFT JOIN news_topics nt ON n.id = nt.news_id
        WHERE n.id IN (:ids)
        GROUP BY n.id
        ORDER BY n.id ASC
    """)
    suspend fun getNewsResourcesByIds(ids: List<Int>): List<NewsResourceWithTopics>

    @Query("SELECT COUNT(*) FROM news_resources")
    suspend fun getNewsResourceCount(): Int

    @Query("SELECT COUNT(DISTINCT news_id) FROM news_topics WHERE topic_id = :topicId")
    suspend fun getNewsResourceCountByTopic(topicId: Int): Int

    @Query("SELECT topic_id FROM news_topics WHERE news_id = :newsId")
    suspend fun getTopicIdsForNews(newsId: Int): List<Int>

    // ============= Write Operations =============

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNewsResource(newsResource: NewsResourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewsTopicCrossRef(crossRef: NewsTopicCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNewsTopicCrossRefs(crossRefs: List<NewsTopicCrossRef>)

    @Update
    suspend fun updateNewsResource(newsResource: NewsResourceEntity)

    @Delete
    suspend fun deleteNewsResource(newsResource: NewsResourceEntity)

    @Query("DELETE FROM news_resources WHERE id = :newsId")
    suspend fun deleteNewsResourceById(newsId: Int)

    @Query("DELETE FROM news_topics WHERE news_id = :newsId")
    suspend fun deleteNewsTopics(newsId: Int)

    @Query("DELETE FROM news_resources WHERE is_user_created = 1")
    suspend fun deleteAllUserNewsResources()

    @Query("DELETE FROM news_resources")
    suspend fun deleteAllNewsResources()

    @Query("DELETE FROM news_topics")
    suspend fun deleteAllNewsTopics()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNewsResources(newsResources: List<NewsResourceEntity>)

    @Query("SELECT COALESCE(MAX(id), 9999) + 1 FROM news_resources WHERE id >= 10000")
    suspend fun getNextUserNewsResourceId(): Int

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
     * Gets the news resource ID for a surah by looking for content with surah reference
     * News resources for surahs typically have content like "surah:1" or "Surah 1"
     */
    @Query("""
        SELECT id FROM news_resources
        WHERE content LIKE '%surah:' || :surahNumber || '%'
           OR content LIKE '%Surah ' || :surahNumber || '%'
           OR title LIKE '%Surah ' || :surahNumber || '%'
        LIMIT 1
    """)
    suspend fun getNewsResourceIdForSurah(surahNumber: Int): Int?
}
