/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    val isSystem: Int? = 1,

    @ColumnInfo(name = "is_user_created", defaultValue = "0")
    val isUserCreated: Int? = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: String?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String?,
)
