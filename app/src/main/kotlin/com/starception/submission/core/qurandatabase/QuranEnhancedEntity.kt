package com.starception.submission.core.qurandatabase

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Enhanced Quran entity for the comprehensive Arabic Quran database
 * This database includes Tafseer, grammar analysis, line-by-line layout, and more
 *
 * Database: quran_enhanced.db (30MB)
 * Features:
 * - Multiple Arabic text versions (with/without tashkeel, emlaey)
 * - 3 Tafseer books (Saadi, Moysar, Baghawi)
 * - Arabic grammar analysis (I'rab)
 * - Revelation context (Asbab al-Nuzul)
 * - Word meanings in Arabic
 * - Line-by-line layout for Mushaf page display
 * - Page numbers for traditional Mushaf pagination
 * - Juz divisions
 */
@Entity(tableName = "quran")
data class QuranEnhancedEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,

    /**
     * Juz number (1-30)
     * The Quran is divided into 30 equal parts called Juz
     */
    @ColumnInfo(name = "jozz")
    val juz: Int,

    /**
     * Surah number (1-114)
     */
    @ColumnInfo(name = "sora")
    val surahNumber: Int,

    /**
     * Surah name in English (transliterated)
     * Example: "Al-Fātiḥah"
     */
    @ColumnInfo(name = "sora_name_en")
    val surahNameEnglish: String,

    /**
     * Surah name in Arabic
     * Example: "الفَاتِحة"
     */
    @ColumnInfo(name = "sora_name_ar")
    val surahNameArabic: String,

    /**
     * Page number in traditional Mushaf
     * Useful for Mushaf-style page layout display
     */
    @ColumnInfo(name = "page")
    val pageNumber: Int,

    /**
     * Starting line number on the page
     * For line-by-line Mushaf layout
     */
    @ColumnInfo(name = "line_start")
    val lineStart: Int,

    /**
     * Ending line number on the page
     * For line-by-line Mushaf layout
     */
    @ColumnInfo(name = "line_end")
    val lineEnd: Int,

    /**
     * Ayah number within the Surah
     */
    @ColumnInfo(name = "aya_no")
    val ayahNumber: Int,

    /**
     * Arabic text with full Tashkeel (diacritics)
     * This is the primary, fully vocalized Arabic text
     * Example: "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
     */
    @ColumnInfo(name = "aya_text")
    val ayahText: String,

    /**
     * Simplified Arabic text (Emlaey style)
     * Arabic text without diacritics for easier reading
     * Example: "بسم الله الرحمن الرحيم"
     */
    @ColumnInfo(name = "aya_text_emlaey")
    val ayahTextEmlaey: String,

    /**
     * Word meanings and explanations in Arabic
     * Provides Arabic explanations of difficult words
     * Example: "ربّ العالمين : مربّيهم ومالكهم ومدبر أمورهم"
     */
    @ColumnInfo(name = "maany_aya")
    val ayahMeanings: String,

    /**
     * Arabic grammar analysis (I'rab)
     * Grammatical parsing and analysis in Arabic
     * Useful for students of Arabic grammar
     */
    @ColumnInfo(name = "earab_quran")
    val grammaticalAnalysis: String,

    /**
     * Reasons of revelation (Asbab al-Nuzul) in Arabic
     * Historical context of when and why the verse was revealed
     */
    @ColumnInfo(name = "reasons_of_verses")
    val revelationReasons: String,

    /**
     * Tafseer As-Sa'di (التفسير السعدي)
     * Comprehensive Tafseer by Sheikh Abdur-Rahman As-Sa'di
     * One of the most popular contemporary Tafseer books
     */
    @ColumnInfo(name = "tafseer_saadi")
    val tafseerSaadi: String,

    /**
     * Tafseer Al-Moyassar (التفسير الميسر)
     * Simplified Tafseer approved by King Fahd Complex
     * Easy to understand interpretation
     */
    @ColumnInfo(name = "tafseer_moysar")
    val tafseerMoysar: String,

    /**
     * Tafseer Al-Baghawi (تفسير البغوي)
     * Classical Tafseer by Imam Al-Baghawi
     * Known as "Ma'alim at-Tanzil"
     */
    @ColumnInfo(name = "tafseer_bughiu")
    val tafseerBaghawi: String,

    /**
     * Arabic text with Tashkeel (alternative field)
     * May contain slightly different tashkeel notation
     */
    @ColumnInfo(name = "aya_text_tashkil")
    val ayahTextTashkil: String
)

/**
 * Domain model for Enhanced Quran Ayah
 * Used in the application layer (not database)
 */
data class QuranEnhancedAyah(
    val id: Int,
    val juz: Int,
    val surahNumber: Int,
    val surahNameEnglish: String,
    val surahNameArabic: String,
    val pageNumber: Int,
    val lineStart: Int,
    val lineEnd: Int,
    val ayahNumber: Int,
    val ayahText: String,
    val ayahTextEmlaey: String,
    val ayahMeanings: String,
    val grammaticalAnalysis: String,
    val revelationReasons: String,
    val tafseerSaadi: String,
    val tafseerMoysar: String,
    val tafseerBaghawi: String,
    val ayahTextTashkil: String
)

/**
 * Lightweight model for list display
 * Contains only essential fields for performance
 */
data class QuranAyahBasic(
    val id: Int,
    val surahNumber: Int,
    val surahNameEnglish: String,
    val surahNameArabic: String,
    val ayahNumber: Int,
    val ayahText: String,
    val pageNumber: Int,
    val juz: Int
)

/**
 * Model for Tafseer display
 * Groups all Tafseer interpretations for an Ayah
 */
data class QuranAyahTafseer(
    val id: Int,
    val surahNumber: Int,
    val surahNameArabic: String,
    val ayahNumber: Int,
    val ayahText: String,
    val tafseerSaadi: String,
    val tafseerMoysar: String,
    val tafseerBaghawi: String,
    val ayahMeanings: String,
    val grammaticalAnalysis: String,
    val revelationReasons: String
)

/**
 * Model for page-based Mushaf display
 * Contains line layout information
 */
data class QuranAyahPage(
    val id: Int,
    val surahNumber: Int,
    val ayahNumber: Int,
    val ayahText: String,
    val pageNumber: Int,
    val lineStart: Int,
    val lineEnd: Int
)

// Extension functions for conversion between entity and domain models

/**
 * Helper function to remove Bismillah from ayah text where it shouldn't be
 *
 * Bismillah rules:
 * - Surah 1 (Al-Fatiha): Bismillah IS counted as ayah 1 - keep it
 * - Surah 9 (At-Tawbah): No Bismillah at all - nothing to remove
 * - All other surahs: Bismillah is recited but NOT part of ayah 1 - remove it from ayah 1
 */
private fun removeBismillahIfNeeded(ayahText: String, surahNumber: Int, ayahNumber: Int): String {
    // Only process first ayah of surahs 2-8 and 10-114
    if (ayahNumber != 1 || surahNumber == 1 || surahNumber == 9) {
        return ayahText
    }

    // Bismillah text with various possible forms
    val bismillahPatterns = listOf(
        "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",  // With Quranic diacritics
        "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",  // Standard diacritics
        "بسم الله الرحمن الرحيم"                  // Without diacritics
    )

    var cleanedText = ayahText
    for (pattern in bismillahPatterns) {
        // Remove Bismillah and any trailing spaces
        cleanedText = cleanedText.replace(pattern, "").trim()
    }

    return cleanedText
}

/**
 * Convert entity to full domain model
 */
fun QuranEnhancedEntity.toQuranEnhancedAyah() = QuranEnhancedAyah(
    id = id,
    juz = juz,
    surahNumber = surahNumber,
    surahNameEnglish = surahNameEnglish,
    surahNameArabic = surahNameArabic,
    pageNumber = pageNumber,
    lineStart = lineStart,
    lineEnd = lineEnd,
    ayahNumber = ayahNumber,
    ayahText = ayahText,
    ayahTextEmlaey = ayahTextEmlaey,
    ayahMeanings = ayahMeanings,
    grammaticalAnalysis = grammaticalAnalysis,
    revelationReasons = revelationReasons,
    tafseerSaadi = tafseerSaadi,
    tafseerMoysar = tafseerMoysar,
    tafseerBaghawi = tafseerBaghawi,
    ayahTextTashkil = ayahTextTashkil
)

/**
 * Convert entity to lightweight basic model
 */
fun QuranEnhancedEntity.toQuranAyahBasic() = QuranAyahBasic(
    id = id,
    surahNumber = surahNumber,
    surahNameEnglish = surahNameEnglish,
    surahNameArabic = surahNameArabic,
    ayahNumber = ayahNumber,
    ayahText = ayahText,
    pageNumber = pageNumber,
    juz = juz
)

/**
 * Convert entity to Tafseer model
 * Automatically removes Bismillah from ayah 1 of surahs 2-8 and 10-114
 */
fun QuranEnhancedEntity.toQuranAyahTafseer() = QuranAyahTafseer(
    id = id,
    surahNumber = surahNumber,
    surahNameArabic = surahNameArabic,
    ayahNumber = ayahNumber,
    ayahText = removeBismillahIfNeeded(ayahText, surahNumber, ayahNumber),
    tafseerSaadi = tafseerSaadi,
    tafseerMoysar = tafseerMoysar,
    tafseerBaghawi = tafseerBaghawi,
    ayahMeanings = ayahMeanings,
    grammaticalAnalysis = grammaticalAnalysis,
    revelationReasons = revelationReasons
)

/**
 * Convert entity to page layout model
 */
fun QuranEnhancedEntity.toQuranAyahPage() = QuranAyahPage(
    id = id,
    surahNumber = surahNumber,
    ayahNumber = ayahNumber,
    ayahText = ayahText,
    pageNumber = pageNumber,
    lineStart = lineStart,
    lineEnd = lineEnd
)
