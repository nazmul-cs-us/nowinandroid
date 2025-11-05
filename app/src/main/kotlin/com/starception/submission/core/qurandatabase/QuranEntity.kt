package com.starception.submission.core.qurandatabase

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Surah (Chapter) entity for Room database
 */
@Entity(
    tableName = "surahs",
    indices = [Index(value = ["number"], name = "idx_surah_number")]
)
data class SurahEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int?, // Nullable to match database schema (even though primary keys should be non-null)
    
    @ColumnInfo(name = "number")
    val number: Int,
    
    @ColumnInfo(name = "name_ar")
    val nameArabic: String?,
    
    @ColumnInfo(name = "name_en")
    val nameEnglish: String?,
    
    @ColumnInfo(name = "name_en_translation")
    val nameTranslation: String?,
    
    @ColumnInfo(name = "type")
    val revelationType: String?, // "Meccan" or "Medinan"
    
    @ColumnInfo(name = "total_verses")
    val totalVerses: Int? = null // This column exists in the database but we calculate it dynamically
)

/**
 * Ayah (Verse) entity for Room database
 */
@Entity(
    tableName = "ayahs",
    foreignKeys = [
        ForeignKey(
            entity = SurahEntity::class,
            parentColumns = ["id"],
            childColumns = ["surah_id"],
            onDelete = ForeignKey.NO_ACTION // Database has NO ACTION, not CASCADE
        )
    ],
    indices = [
        // Database schemas vary - transliteration DB has composite index, others have single column indices
        // We declare all indices that exist across different databases
        Index(value = ["surah_id"], name = "idx_ayah_surah_id"),
        Index(value = ["surah_number", "number_in_surah"], name = "idx_ayah_number"), // Composite index in transliteration/translation DBs
        Index(value = ["number_in_surah"], name = "index_ayahs_number_in_surah"), // In Arabic DB
        Index(value = ["number"], name = "index_ayahs_number"), // In Arabic DB
        Index(value = ["surah_id"], name = "index_ayahs_surah_id") // In Arabic DB
    ]
)
data class AyahEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int?, // Nullable to match database schema
    
    @ColumnInfo(name = "number")
    val number: Int?, // Overall ayah number (1-6236), nullable to match database
    
    @ColumnInfo(name = "text")
    val text: String, // Text content (NOT NULL in database)
    
    @ColumnInfo(name = "number_in_surah")
    val numberInSurah: Int, // Ayah number within the surah (NOT NULL in database)
    
    @ColumnInfo(name = "page")
    val page: Int?, // Mushaf page number, nullable to match database (default 1)
    
    @ColumnInfo(name = "surah_id")
    val surahId: Int, // Surah ID (NOT NULL in database)
    
    @ColumnInfo(name = "surah_number")
    val surahNumber: Int, // Additional column in database - surah number (NOT NULL)
    
    @ColumnInfo(name = "hizb_id")
    val hizbId: Int?, // Nullable to match database (default 1)
    
    @ColumnInfo(name = "juz_id")
    val juzId: Int?, // Nullable to match database (default 1)
    
    @ColumnInfo(name = "sajda")
    val sajda: Boolean? // Sajda (prostration) required, nullable to match database (default 0)
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
    val number: Int
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
    val number: Int
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
    val ayahCount: Int = 0
)

data class Ayah(
    val id: Int,
    val number: Int,
    val text: String,
    val numberInSurah: Int,
    val page: Int,
    val surahId: Int,
    val surahNumber: Int, // Added to match database schema
    val hizbId: Int,
    val juzId: Int,
    val sajda: Boolean
)

// Extension functions for conversion between entity and domain model
fun SurahEntity.toSurah(ayahCount: Int = 0) = Surah(
    id = id ?: 0, // Handle nullable id (should never be null in practice)
    number = number,
    nameArabic = nameArabic ?: "",
    nameEnglish = nameEnglish ?: "",
    nameTranslation = nameTranslation ?: "",
    revelationType = revelationType ?: "Meccan",
    ayahCount = ayahCount
)

fun AyahEntity.toAyah() = Ayah(
    id = id ?: 0, // Handle nullable id
    number = number ?: 0, // Handle nullable number
    text = text,
    numberInSurah = numberInSurah,
    page = page ?: 1, // Handle nullable page (default 1)
    surahId = surahId,
    surahNumber = surahNumber, // Include surah_number from database
    hizbId = hizbId ?: 1, // Handle nullable hizbId (default 1)
    juzId = juzId ?: 1, // Handle nullable juzId (default 1)
    sajda = sajda ?: false // Handle nullable sajda (default false)
)

