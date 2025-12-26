package com.starception.submission.core.contentdatabase

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity for news_resources table
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

    @ColumnInfo(name = "is_system", defaultValue = "1")
    val isSystem: Int = 1,

    @ColumnInfo(name = "is_user_created", defaultValue = "0")
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
