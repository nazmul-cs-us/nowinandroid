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
    val elaboration: String?
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
    val authorArabic: String = ""
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
    val hadithCount: Int
)

// ============= Extension Functions =============

fun HadithEntity.toHadith(
    collectionName: String = "",
    collectionNameArabic: String = "",
    collectionNameEnglish: String = "",
    author: String = "",
    authorArabic: String = ""
) = Hadith(
    id = id,
    textArabic = textArabic,
    textPlain = textPlain,
    elaboration = elaboration,
    collectionName = collectionName,
    collectionNameArabic = collectionNameArabic,
    collectionNameEnglish = collectionNameEnglish,
    author = author,
    authorArabic = authorArabic
)
