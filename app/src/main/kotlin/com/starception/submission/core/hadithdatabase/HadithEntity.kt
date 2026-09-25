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

package com.starception.submission.core.hadithdatabase

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a hadith from any collection
 */
@Entity(tableName = "hadiths")
data class HadithEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "text_arabic")
    val textArabic: String,

    @ColumnInfo(name = "text_plain")
    val textPlain: String?,

    @ColumnInfo(name = "elaboration")
    val elaboration: String?,
)

// ============= Domain Models =============

/**
 * Domain model for a hadith
 */
data class Hadith(
    val id: Int,
    val textArabic: String,
    val textPlain: String?,
    val elaboration: String?,
    val collectionName: String = "",
    val collectionNameArabic: String = "",
    val collectionNameEnglish: String = "",
    val author: String = "",
    val authorArabic: String = "",
    // Rich, source-authored fields available in the Shama'il database. Keeping
    // these separate prevents the UI from displaying the legacy combined
    // English/Bengali text_plain value as a single translation.
    val bengaliText: String? = null,
    val englishText: String? = null,
    val bengaliExplanation: String? = null,
)

/**
 * Metadata for a hadith collection
 */
data class HadithCollectionMetadata(
    val collectionId: Int,
    val name: String,
    val nameArabic: String,
    val nameEnglish: String,
    val author: String,
    val authorArabic: String,
    val hasElaboration: Boolean,
    val hadithCount: Int,
)

// ============= Extension Functions =============

fun HadithEntity.toHadith(
    collectionName: String = "",
    collectionNameArabic: String = "",
    collectionNameEnglish: String = "",
    author: String = "",
    authorArabic: String = "",
) = Hadith(
    id = id,
    textArabic = textArabic,
    textPlain = textPlain,
    elaboration = elaboration,
    collectionName = collectionName,
    collectionNameArabic = collectionNameArabic,
    collectionNameEnglish = collectionNameEnglish,
    author = author,
    authorArabic = authorArabic,
)
