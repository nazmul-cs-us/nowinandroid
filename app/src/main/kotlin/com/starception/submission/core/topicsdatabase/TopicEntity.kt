package com.starception.submission.core.topicsdatabase

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity for Topics table
 * Maps to the 'topics' table in topics.db
 */
@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey
    val id: Int,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "short_description")
    val shortDescription: String?,

    @ColumnInfo(name = "long_description")
    val longDescription: String?,

    @ColumnInfo(name = "image_url")
    val imageUrl: String?,

    @ColumnInfo(name = "url")
    val url: String?,

    @ColumnInfo(name = "icon")
    val icon: String?,

    @ColumnInfo(name = "is_system")
    val isSystem: Int = 1,

    @ColumnInfo(name = "is_user_created")
    val isUserCreated: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String?
)

/**
 * Domain model for Topic
 */
data class Topic(
    val id: String,
    val name: String,
    val shortDescription: String,
    val longDescription: String,
    val imageUrl: String,
    val url: String,
    val icon: String?,
    val isSystem: Boolean,
    val isUserCreated: Boolean
)

/**
 * Extension function to convert Entity to Domain model
 */
fun TopicEntity.toTopic() = Topic(
    id = id.toString(),
    name = name,
    shortDescription = shortDescription ?: "",
    longDescription = longDescription ?: "",
    imageUrl = imageUrl ?: "",
    url = url ?: "",
    icon = icon,
    isSystem = isSystem == 1,
    isUserCreated = isUserCreated == 1
)

/**
 * Extension function to convert Domain model to Entity
 */
fun Topic.toEntity() = TopicEntity(
    id = id.toIntOrNull() ?: 0,
    name = name,
    shortDescription = shortDescription,
    longDescription = longDescription,
    imageUrl = imageUrl,
    url = url,
    icon = icon,
    isSystem = if (isSystem) 1 else 0,
    isUserCreated = if (isUserCreated) 1 else 0,
    createdAt = null,
    updatedAt = null
)
