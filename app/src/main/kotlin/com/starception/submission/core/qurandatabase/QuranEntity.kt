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

package com.starception.submission.core.qurandatabase

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Surah (Chapter) entity for Room database
 */
/**
 * Surah (Chapter) entity for Room database
 * Note: Arabic DB has all columns NOT NULL, no total_verses column, no index
 * Translation DBs have nullable columns, total_verses column, and idx_surah_number index
 */
@Entity(
    tableName = "surahs",
    indices = [
        // Arabic DB has no index on surahs table
        // Translation DBs have idx_surah_number
        // Declare both to handle both schemas (Room will ignore missing indices)
        Index(value = ["number"], name = "idx_surah_number"),
    ],
)
data class SurahEntity(
    // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int?,

    // NOT NULL in all databases
    @ColumnInfo(name = "number")
    val number: Int,

    // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    @ColumnInfo(name = "name_ar")
    val nameArabic: String?,

    // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    @ColumnInfo(name = "name_en")
    val nameEnglish: String?,

    // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    @ColumnInfo(name = "name_en_translation")
    val nameTranslation: String?,

    // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    @ColumnInfo(name = "type")
    val revelationType: String?,

    // Note: total_verses exists only in translation DBs, not in Arabic
    // Room will fail validation for Arabic DB if this column is declared
    // We need to handle this via a custom approach
    // This column exists only in translation databases
    @ColumnInfo(name = "total_verses")
    val totalVerses: Int? = null,
)

/**
 * Ayah (Verse) entity for Room database
 * Note: Arabic database has different schema than translation databases:
 * - Arabic: All columns NOT NULL, no surah_number column
 * - Translations: Many columns nullable, has surah_number column
 */
@Entity(
    tableName = "ayahs",
    foreignKeys = [
        ForeignKey(
            entity = SurahEntity::class,
            parentColumns = ["id"],
            childColumns = ["surah_id"],
            // Translation DBs have NO ACTION
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        // Arabic DB has: index_ayahs_surah_id, index_ayahs_number, index_ayahs_number_in_surah
        // Translation DBs have: idx_ayah_surah_id, idx_ayah_number (composite), plus index_ayahs_*
        // Declare all to match both schemas
        // Note: Composite index on surah_number is only in translation DBs, but we declare it
        // Room will ignore indices that reference columns that don't exist
        // Arabic DB
        Index(value = ["surah_id"], name = "index_ayahs_surah_id"),
        // Arabic DB
        Index(value = ["number"], name = "index_ayahs_number"),
        // Arabic DB
        Index(value = ["number_in_surah"], name = "index_ayahs_number_in_surah"),
        // Translation DBs
        Index(value = ["surah_id"], name = "idx_ayah_surah_id"),
        // Note: idx_ayah_number is a composite index on surah_number and number_in_surah
        // This exists only in translation DBs, but Room will ignore it for Arabic DB
        // Translation DBs (composite)
        Index(value = ["surah_number", "number_in_surah"], name = "idx_ayah_number"),
    ],
)
data class AyahEntity(
    // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int?,

    // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    @ColumnInfo(name = "number")
    val number: Int?,

    // NOT NULL in all databases
    @ColumnInfo(name = "text")
    val text: String,

    // NOT NULL in all databases
    @ColumnInfo(name = "number_in_surah")
    val numberInSurah: Int,

    // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    @ColumnInfo(name = "page")
    val page: Int?,

    // NOT NULL in all databases
    @ColumnInfo(name = "surah_id")
    val surahId: Int,

    // Note: surah_number exists in all databases (added to Arabic DB to match translation DBs)
    // In translation DBs it's NOT NULL, in Arabic DB it's also NOT NULL after adding it
    // NOT NULL in all databases
    @ColumnInfo(name = "surah_number")
    val surahNumber: Int,

    // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    @ColumnInfo(name = "hizb_id")
    val hizbId: Int?,

    // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    @ColumnInfo(name = "juz_id")
    val juzId: Int?,

    // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    @ColumnInfo(name = "sajda")
    val sajda: Boolean?,
)

/**
 * Juz (Part) entity for Room database
 * The Quran is divided into 30 Juz (parts)
 */
@Entity(tableName = "juzs")
data class JuzEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "number")
    val number: Int,
)

/**
 * Hizb (Section) entity for Room database
 * Each Juz is divided into 2 Hizbs (60 Hizbs total)
 */
@Entity(tableName = "hizbs")
data class HizbEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "number")
    val number: Int,
)

/**
 * Data classes for use in the application (not database entities)
 */
data class Surah(
    val id: Int,
    val number: Int,
    val nameArabic: String,
    val nameEnglish: String,
    val nameTranslation: String,
    val revelationType: String,
    val ayahCount: Int = 0,
)

data class Ayah(
    val id: Int,
    val number: Int,
    val text: String,
    val numberInSurah: Int,
    val page: Int,
    val surahId: Int,
    // Added to match database schema
    val surahNumber: Int,
    val hizbId: Int,
    val juzId: Int,
    val sajda: Boolean,
)

// Extension functions for conversion between entity and domain model
fun SurahEntity.toSurah(ayahCount: Int = 0) = Surah(
    // Handle nullable id
    id = id ?: 0,
    number = number,
    nameArabic = nameArabic ?: "",
    nameEnglish = nameEnglish ?: "",
    nameTranslation = nameTranslation ?: "",
    revelationType = revelationType ?: "Meccan",
    ayahCount = ayahCount,
)

fun AyahEntity.toAyah(surahNumberParam: Int = 0) = Ayah(
    // Handle nullable id (translation DBs)
    id = id ?: 0,
    // Handle nullable number (translation DBs)
    number = number ?: 0,
    text = text,
    numberInSurah = numberInSurah,
    // Handle nullable page (translation DBs, default 1)
    page = page ?: 1,
    surahId = surahId,
    // NOT NULL in all databases now
    surahNumber = surahNumber,
    // Handle nullable hizbId (translation DBs, default 1)
    hizbId = hizbId ?: 1,
    // Handle nullable juzId (translation DBs, default 1)
    juzId = juzId ?: 1,
    // Handle nullable sajda (translation DBs, default false)
    sajda = sajda ?: false,
)

/**
 * Favourite Ayah entity for storing user's favourite ayahs
 */
@Entity(
    tableName = "favourite_ayahs",
    indices = [
        Index(value = ["surah_number", "ayah_number"], unique = true, name = "idx_favourite_unique"),
    ],
)
data class FavouriteAyahEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "surah_number")
    val surahNumber: Int,

    @ColumnInfo(name = "ayah_number")
    val ayahNumber: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Ayah Note entity for storing user's personal notes on ayahs
 * Notes are searchable from the main search bar
 */
@Entity(
    tableName = "ayah_notes",
    indices = [
        Index(value = ["surah_number", "ayah_number"], name = "idx_note_surah_ayah"),
    ],
)
data class AyahNoteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "surah_number")
    val surahNumber: Int,

    @ColumnInfo(name = "ayah_number")
    val ayahNumber: Int,

    @ColumnInfo(name = "note_text")
    val noteText: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
