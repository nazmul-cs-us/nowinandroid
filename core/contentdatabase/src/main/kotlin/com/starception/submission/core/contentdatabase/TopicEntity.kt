package com.starception.submission.core.contentdatabase

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

    @ColumnInfo(name = "is_system", defaultValue = "1")
    val isSystem: Int = 1,

    @ColumnInfo(name = "is_user_created", defaultValue = "0")
    val isUserCreated: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String?
)
