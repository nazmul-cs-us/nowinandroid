package com.starception.submission.core.newsdatabase

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity for news_resources table
 * Maps to the 'news_resources' table in news_resources.db
 */
@Entity(tableName = "news_resources")
data class NewsResourceEntity(
    @PrimaryKey
    val id: Int,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String?,

    @ColumnInfo(name = "url")
    val url: String?,

    @ColumnInfo(name = "header_image_url")
    val headerImageUrl: String?,

    @ColumnInfo(name = "publish_date")
    val publishDate: String?,

    @ColumnInfo(name = "type")
    val type: String?,

    @ColumnInfo(name = "is_system")
    val isSystem: Int = 1,

    @ColumnInfo(name = "is_user_created")
    val isUserCreated: Int = 0,

    @ColumnInfo(name = "source")
    val source: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: String?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String?
)

/**
 * Room Entity for news_topics junction table (many-to-many)
 */
@Entity(
    tableName = "news_topics",
    primaryKeys = ["news_id", "topic_id"]
)
data class NewsTopicCrossRef(
    @ColumnInfo(name = "news_id")
    val newsId: Int,

    @ColumnInfo(name = "topic_id")
    val topicId: Int
)

/**
 * Domain model for NewsResource
 */
data class NewsResource(
    val id: String,
    val title: String,
    val content: String,
    val url: String,
    val headerImageUrl: String,
    val publishDate: String,
    val type: String,
    val topicIds: List<String> = emptyList(),
    val isSystem: Boolean = true,
    val isUserCreated: Boolean = false,
    val source: String = ""
)

/**
 * Query result with topic IDs
 */
data class NewsResourceWithTopics(
    val id: Int,
    val title: String,
    val content: String?,
    val url: String?,
    val headerImageUrl: String?,
    val publishDate: String?,
    val type: String?,
    val isSystem: Int,
    val isUserCreated: Int,
    val source: String?,
    val topicIds: String? // Comma-separated topic IDs
)

/**
 * Extension function to convert Entity to Domain model
 */
fun NewsResourceEntity.toNewsResource(topicIds: List<String> = emptyList()) = NewsResource(
    id = id.toString(),
    title = title,
    content = content ?: "",
    url = url ?: "",
    headerImageUrl = headerImageUrl ?: "",
    publishDate = publishDate ?: "",
    type = type ?: "",
    topicIds = topicIds,
    isSystem = isSystem == 1,
    isUserCreated = isUserCreated == 1,
    source = source ?: ""
)

/**
 * Extension function to convert NewsResourceWithTopics to Domain model
 */
fun NewsResourceWithTopics.toNewsResource() = NewsResource(
    id = id.toString(),
    title = title,
    content = content ?: "",
    url = url ?: "",
    headerImageUrl = headerImageUrl ?: "",
    publishDate = publishDate ?: "",
    type = type ?: "",
    topicIds = topicIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    isSystem = isSystem == 1,
    isUserCreated = isUserCreated == 1,
    source = source ?: ""
)

/**
 * Extension function to convert Domain model to Entity
 */
fun NewsResource.toEntity() = NewsResourceEntity(
    id = id.toIntOrNull() ?: 0,
    title = title,
    content = content,
    url = url,
    headerImageUrl = headerImageUrl,
    publishDate = publishDate,
    type = type,
    isSystem = if (isSystem) 1 else 0,
    isUserCreated = if (isUserCreated) 1 else 0,
    source = source,
    createdAt = null,
    updatedAt = null
)
