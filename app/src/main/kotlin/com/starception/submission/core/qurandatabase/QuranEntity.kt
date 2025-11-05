package com.starception.submission.core.qurandatabase

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
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
        Index(value = ["number"], name = "idx_surah_number")
    ]
)
data class SurahEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int?, // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    
    @ColumnInfo(name = "number")
    val number: Int, // NOT NULL in all databases
    
    @ColumnInfo(name = "name_ar")
    val nameArabic: String?, // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    
    @ColumnInfo(name = "name_en")
    val nameEnglish: String?, // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    
    @ColumnInfo(name = "name_en_translation")
    val nameTranslation: String?, // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    
    @ColumnInfo(name = "type")
    val revelationType: String?, // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    
    // Note: total_verses exists only in translation DBs, not in Arabic
    // Room will fail validation for Arabic DB if this column is declared
    // We need to handle this via a custom approach
    @ColumnInfo(name = "total_verses")
    val totalVerses: Int? = null // This column exists only in translation databases
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
            onDelete = ForeignKey.NO_ACTION // Translation DBs have NO ACTION
        )
    ],
    indices = [
        // Arabic DB has: index_ayahs_surah_id, index_ayahs_number, index_ayahs_number_in_surah
        // Translation DBs have: idx_ayah_surah_id, idx_ayah_number (composite), plus index_ayahs_*
        // Declare all to match both schemas
        // Note: Composite index on surah_number is only in translation DBs, but we declare it
        // Room will ignore indices that reference columns that don't exist
        Index(value = ["surah_id"], name = "index_ayahs_surah_id"), // Arabic DB
        Index(value = ["number"], name = "index_ayahs_number"), // Arabic DB
        Index(value = ["number_in_surah"], name = "index_ayahs_number_in_surah"), // Arabic DB
        Index(value = ["surah_id"], name = "idx_ayah_surah_id"), // Translation DBs
        // Note: idx_ayah_number is a composite index on surah_number and number_in_surah
        // This exists only in translation DBs, but Room will ignore it for Arabic DB
        Index(value = ["surah_number", "number_in_surah"], name = "idx_ayah_number") // Translation DBs (composite)
    ]
)
data class AyahEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int?, // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    
    @ColumnInfo(name = "number")
    val number: Int?, // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    
    @ColumnInfo(name = "text")
    val text: String, // NOT NULL in all databases
    
    @ColumnInfo(name = "number_in_surah")
    val numberInSurah: Int, // NOT NULL in all databases
    
    @ColumnInfo(name = "page")
    val page: Int?, // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    
    @ColumnInfo(name = "surah_id")
    val surahId: Int, // NOT NULL in all databases
    
    // Note: surah_number exists in all databases (added to Arabic DB to match translation DBs)
    // In translation DBs it's NOT NULL, in Arabic DB it's also NOT NULL after adding it
    @ColumnInfo(name = "surah_number")
    val surahNumber: Int, // NOT NULL in all databases
    
    @ColumnInfo(name = "hizb_id")
    val hizbId: Int?, // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    
    @ColumnInfo(name = "juz_id")
    val juzId: Int?, // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
    
    @ColumnInfo(name = "sajda")
    val sajda: Boolean? // Nullable in translation DBs, NOT NULL in Arabic (Room can read NOT NULL into nullable)
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
    id = id ?: 0, // Handle nullable id
    number = number,
    nameArabic = nameArabic ?: "",
    nameEnglish = nameEnglish ?: "",
    nameTranslation = nameTranslation ?: "",
    revelationType = revelationType ?: "Meccan",
    ayahCount = ayahCount
)

fun AyahEntity.toAyah(surahNumberParam: Int = 0) = Ayah(
    id = id ?: 0, // Handle nullable id (translation DBs)
    number = number ?: 0, // Handle nullable number (translation DBs)
    text = text,
    numberInSurah = numberInSurah,
    page = page ?: 1, // Handle nullable page (translation DBs, default 1)
    surahId = surahId,
    surahNumber = surahNumber, // NOT NULL in all databases now
    hizbId = hizbId ?: 1, // Handle nullable hizbId (translation DBs, default 1)
    juzId = juzId ?: 1, // Handle nullable juzId (translation DBs, default 1)
    sajda = sajda ?: false // Handle nullable sajda (translation DBs, default false)
)

