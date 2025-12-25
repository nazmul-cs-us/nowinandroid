package com.starception.submission.core.quranicduas

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity for Quranic Duas table
 * Contains 40 duas directly from the Quran
 */
@Entity(
    tableName = "quranic_duas",
    indices = [Index(name = "idx_quranic_duas_number", value = ["dua_number"])]
)
data class QuranicDuaEntity(
    @PrimaryKey
    val id: Int,

    @ColumnInfo(name = "dua_number")
    val duaNumber: Int,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "surah_reference")
    val surahReference: String?,

    @ColumnInfo(name = "arabic")
    val arabic: String?,

    @ColumnInfo(name = "transliteration")
    val transliteration: String?,

    @ColumnInfo(name = "translation")
    val translation: String?,

    @ColumnInfo(name = "explanation")
    val explanation: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: String?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String?
)

/**
 * Domain model for Quranic Dua
 */
data class QuranicDua(
    val id: Int,
    val duaNumber: Int,
    val title: String,
    val surahReference: String,
    val arabic: String,
    val transliteration: String,
    val translation: String,
    val explanation: String
)

/**
 * Extension function to convert Entity to Domain model
 */
fun QuranicDuaEntity.toQuranicDua() = QuranicDua(
    id = id,
    duaNumber = duaNumber,
    title = title,
    surahReference = surahReference ?: "",
    arabic = arabic ?: "",
    transliteration = transliteration ?: "",
    translation = translation ?: "",
    explanation = explanation ?: ""
)
