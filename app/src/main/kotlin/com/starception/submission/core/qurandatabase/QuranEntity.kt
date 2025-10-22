package com.starception.submission.core.qurandatabase

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Surah (Chapter) entity for Room database
 */
@Entity(tableName = "surahs")
data class SurahEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,
    
    @ColumnInfo(name = "number")
    val number: Int,
    
    @ColumnInfo(name = "name_ar")
    val nameArabic: String,
    
    @ColumnInfo(name = "name_en")
    val nameEnglish: String,
    
    @ColumnInfo(name = "name_en_translation")
    val nameTranslation: String,
    
    @ColumnInfo(name = "type")
    val revelationType: String // "Meccan" or "Medinan"
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
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["surah_id"]),
        Index(value = ["number"]),
        Index(value = ["number_in_surah"])
    ]
)
data class AyahEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,
    
    @ColumnInfo(name = "number")
    val number: Int, // Overall ayah number (1-6236)
    
    @ColumnInfo(name = "text")
    val text: String, // Arabic text
    
    @ColumnInfo(name = "number_in_surah")
    val numberInSurah: Int, // Ayah number within the surah
    
    @ColumnInfo(name = "page")
    val page: Int, // Mushaf page number
    
    @ColumnInfo(name = "surah_id")
    val surahId: Int,
    
    @ColumnInfo(name = "hizb_id")
    val hizbId: Int,
    
    @ColumnInfo(name = "juz_id")
    val juzId: Int,
    
    @ColumnInfo(name = "sajda")
    val sajda: Boolean // Sajda (prostration) required
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
    val hizbId: Int,
    val juzId: Int,
    val sajda: Boolean
)

// Extension functions for conversion between entity and domain model
fun SurahEntity.toSurah(ayahCount: Int = 0) = Surah(
    id = id,
    number = number,
    nameArabic = nameArabic,
    nameEnglish = nameEnglish,
    nameTranslation = nameTranslation,
    revelationType = revelationType,
    ayahCount = ayahCount
)

fun AyahEntity.toAyah() = Ayah(
    id = id,
    number = number,
    text = text,
    numberInSurah = numberInSurah,
    page = page,
    surahId = surahId,
    hizbId = hizbId,
    juzId = juzId,
    sajda = sajda
)

