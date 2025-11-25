package com.starception.submission.core.qurandatabase

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Enhanced Quran database
 * Provides queries for Tafseer, line-by-line layout, grammar analysis, and more
 *
 * Database: quran_enhanced.db
 * Table: quran
 */
@Dao
interface QuranEnhancedDao {

    // ============= Basic Ayah Queries =============

    /**
     * Get all Ayahs for a specific Surah
     * Returns lightweight basic model for list display
     */
    @Query("""
        SELECT id, sora as surahNumber, sora_name_en as surahNameEnglish,
               sora_name_ar as surahNameArabic, aya_no as ayahNumber,
               aya_text as ayahText, page as pageNumber, jozz as juz
        FROM quran
        WHERE sora = :surahNumber
        ORDER BY aya_no ASC
    """)
    suspend fun getAyahsBySurah(surahNumber: Int): List<QuranAyahBasic>

    /**
     * Get all Ayahs for a specific Surah (Flow for reactive updates)
     */
    @Query("SELECT * FROM quran WHERE sora = :surahNumber ORDER BY aya_no ASC")
    fun getAyahsBySurahFlow(surahNumber: Int): Flow<List<QuranEnhancedEntity>>

    /**
     * Get a specific Ayah by ID (full data)
     */
    @Query("SELECT * FROM quran WHERE id = :ayahId")
    suspend fun getAyahById(ayahId: Int): QuranEnhancedEntity?

    /**
     * Get a specific Ayah by Surah and Ayah number
     */
    @Query("SELECT * FROM quran WHERE sora = :surahNumber AND aya_no = :ayahNumber")
    suspend fun getAyah(surahNumber: Int, ayahNumber: Int): QuranEnhancedEntity?

    /**
     * Get total number of Ayahs in the Quran
     */
    @Query("SELECT COUNT(*) FROM quran")
    suspend fun getTotalAyahCount(): Int

    /**
     * Get number of Ayahs in a specific Surah
     */
    @Query("SELECT COUNT(*) FROM quran WHERE sora = :surahNumber")
    suspend fun getAyahCountBySurah(surahNumber: Int): Int

    // ============= Surah Information Queries =============

    /**
     * Get list of all Surahs with their first Ayah info
     * Useful for Surah selection screen
     */
    @Query("""
        SELECT DISTINCT sora as surahNumber, sora_name_en as surahNameEnglish,
               sora_name_ar as surahNameArabic,
               (SELECT COUNT(*) FROM quran q2 WHERE q2.sora = quran.sora) as ayahCount
        FROM quran
        ORDER BY sora ASC
    """)
    suspend fun getAllSurahs(): List<SurahInfo>

    /**
     * Get Surah information by number
     */
    @Query("""
        SELECT DISTINCT sora as surahNumber, sora_name_en as surahNameEnglish,
               sora_name_ar as surahNameArabic,
               (SELECT COUNT(*) FROM quran q2 WHERE q2.sora = quran.sora) as ayahCount
        FROM quran
        WHERE sora = :surahNumber
    """)
    suspend fun getSurahInfo(surahNumber: Int): SurahInfo?

    /**
     * Search Surahs by name (Arabic or English)
     */
    @Query("""
        SELECT DISTINCT sora as surahNumber, sora_name_en as surahNameEnglish,
               sora_name_ar as surahNameArabic,
               (SELECT COUNT(*) FROM quran q2 WHERE q2.sora = quran.sora) as ayahCount
        FROM quran
        WHERE sora_name_ar LIKE '%' || :query || '%'
           OR sora_name_en LIKE '%' || :query || '%'
        ORDER BY sora ASC
    """)
    suspend fun searchSurahs(query: String): List<SurahInfo>

    // ============= Page-Based Queries (Mushaf Layout) =============

    /**
     * Get all Ayahs on a specific page
     * For traditional Mushaf page display
     */
    @Query("""
        SELECT id, sora as surahNumber, aya_no as ayahNumber,
               aya_text as ayahText, page as pageNumber,
               line_start as lineStart, line_end as lineEnd
        FROM quran
        WHERE page = :pageNumber
        ORDER BY line_start ASC, aya_no ASC
    """)
    suspend fun getAyahsByPage(pageNumber: Int): List<QuranAyahPage>

    /**
     * Get all Ayahs on a specific page (Flow)
     */
    @Query("SELECT * FROM quran WHERE page = :pageNumber ORDER BY line_start ASC")
    fun getAyahsByPageFlow(pageNumber: Int): Flow<List<QuranEnhancedEntity>>

    /**
     * Get Ayahs by line range on a page
     * For line-by-line audio playback
     */
    @Query("""
        SELECT * FROM quran
        WHERE page = :pageNumber
          AND line_start >= :startLine
          AND line_end <= :endLine
        ORDER BY line_start ASC, aya_no ASC
    """)
    suspend fun getAyahsByLineRange(
        pageNumber: Int,
        startLine: Int,
        endLine: Int
    ): List<QuranEnhancedEntity>

    /**
     * Get total number of pages in the Quran
     */
    @Query("SELECT MAX(page) FROM quran")
    suspend fun getTotalPages(): Int

    // ============= Juz-Based Queries =============

    /**
     * Get all Ayahs in a specific Juz
     */
    @Query("SELECT * FROM quran WHERE jozz = :juzNumber ORDER BY id ASC")
    suspend fun getAyahsByJuz(juzNumber: Int): List<QuranEnhancedEntity>

    /**
     * Get all Ayahs in a specific Juz (Flow)
     */
    @Query("SELECT * FROM quran WHERE jozz = :juzNumber ORDER BY id ASC")
    fun getAyahsByJuzFlow(juzNumber: Int): Flow<List<QuranEnhancedEntity>>

    /**
     * Get Juz information with Ayah counts
     */
    @Query("""
        SELECT DISTINCT jozz as juzNumber,
               (SELECT COUNT(*) FROM quran q2 WHERE q2.jozz = quran.jozz) as ayahCount
        FROM quran
        ORDER BY jozz ASC
    """)
    suspend fun getAllJuzInfo(): List<JuzInfo>

    // ============= Tafseer Queries =============

    /**
     * Get Tafseer for a specific Ayah
     * Returns all three Tafseer books plus related information
     */
    @Query("""
        SELECT id, sora as surahNumber, sora_name_ar as surahNameArabic,
               aya_no as ayahNumber, aya_text as ayahText,
               tafseer_saadi as tafseerSaadi, tafseer_moysar as tafseerMoysar,
               tafseer_bughiu as tafseerBaghawi, maany_aya as ayahMeanings,
               earab_quran as grammaticalAnalysis, reasons_of_verses as revelationReasons
        FROM quran
        WHERE sora = :surahNumber AND aya_no = :ayahNumber
    """)
    suspend fun getTafseerForAyah(surahNumber: Int, ayahNumber: Int): QuranAyahTafseer?

    /**
     * Get Tafseer Saadi for a Surah
     * Returns only Tafseer Saadi (most popular contemporary Tafseer)
     */
    @Query("""
        SELECT aya_no as ayahNumber, aya_text as ayahText,
               tafseer_saadi as tafseer
        FROM quran
        WHERE sora = :surahNumber
        ORDER BY aya_no ASC
    """)
    suspend fun getTafseerSaadiBySurah(surahNumber: Int): List<AyahTafseerItem>

    /**
     * Get Tafseer Moysar for a Surah
     * Returns only Tafseer Moysar (simplified Tafseer)
     */
    @Query("""
        SELECT aya_no as ayahNumber, aya_text as ayahText,
               tafseer_moysar as tafseer
        FROM quran
        WHERE sora = :surahNumber
        ORDER BY aya_no ASC
    """)
    suspend fun getTafseerMoysarBySurah(surahNumber: Int): List<AyahTafseerItem>

    /**
     * Get Tafseer Baghawi for a Surah
     * Returns only Tafseer Baghawi (classical Tafseer)
     */
    @Query("""
        SELECT aya_no as ayahNumber, aya_text as ayahText,
               tafseer_bughiu as tafseer
        FROM quran
        WHERE sora = :surahNumber
        ORDER BY aya_no ASC
    """)
    suspend fun getTafseerBaghawiBySurah(surahNumber: Int): List<AyahTafseerItem>

    // ============= Grammar and Analysis Queries =============

    /**
     * Get grammatical analysis (I'rab) for an Ayah
     */
    @Query("""
        SELECT aya_no as ayahNumber, aya_text as ayahText,
               earab_quran as analysis
        FROM quran
        WHERE sora = :surahNumber AND aya_no = :ayahNumber
    """)
    suspend fun getGrammaticalAnalysis(surahNumber: Int, ayahNumber: Int): AyahAnalysisItem?

    /**
     * Get word meanings for an Ayah
     */
    @Query("""
        SELECT aya_no as ayahNumber, aya_text as ayahText,
               maany_aya as meanings
        FROM quran
        WHERE sora = :surahNumber AND aya_no = :ayahNumber
    """)
    suspend fun getAyahMeanings(surahNumber: Int, ayahNumber: Int): AyahMeaningsItem?

    /**
     * Get revelation reasons (Asbab al-Nuzul) for an Ayah
     */
    @Query("""
        SELECT aya_no as ayahNumber, aya_text as ayahText,
               reasons_of_verses as reasons
        FROM quran
        WHERE sora = :surahNumber AND aya_no = :ayahNumber
    """)
    suspend fun getRevelationReasons(surahNumber: Int, ayahNumber: Int): AyahReasonsItem?

    // ============= Search Queries =============

    /**
     * Search Ayahs by Arabic text (with tashkeel)
     */
    @Query("""
        SELECT * FROM quran
        WHERE aya_text LIKE '%' || :query || '%'
        ORDER BY sora ASC, aya_no ASC
        LIMIT :limit
    """)
    suspend fun searchAyahsArabic(query: String, limit: Int = 100): List<QuranEnhancedEntity>

    /**
     * Search Ayahs by simplified Arabic text (without tashkeel)
     * Better for search as users often type without diacritics
     */
    @Query("""
        SELECT * FROM quran
        WHERE aya_text_emlaey LIKE '%' || :query || '%'
        ORDER BY sora ASC, aya_no ASC
        LIMIT :limit
    """)
    suspend fun searchAyahsEmlaey(query: String, limit: Int = 100): List<QuranEnhancedEntity>

    /**
     * Search in Tafseer Saadi
     */
    @Query("""
        SELECT * FROM quran
        WHERE tafseer_saadi LIKE '%' || :query || '%'
        ORDER BY sora ASC, aya_no ASC
        LIMIT :limit
    """)
    suspend fun searchTafseerSaadi(query: String, limit: Int = 50): List<QuranEnhancedEntity>

    /**
     * Search in all Tafseer books
     */
    @Query("""
        SELECT * FROM quran
        WHERE tafseer_saadi LIKE '%' || :query || '%'
           OR tafseer_moysar LIKE '%' || :query || '%'
           OR tafseer_bughiu LIKE '%' || :query || '%'
        ORDER BY sora ASC, aya_no ASC
        LIMIT :limit
    """)
    suspend fun searchAllTafseer(query: String, limit: Int = 50): List<QuranEnhancedEntity>

    // ============= Text Variant Queries =============

    /**
     * Get different text variants for an Ayah
     * Returns all three Arabic text versions
     */
    @Query("""
        SELECT sora as surahNumber, aya_no as ayahNumber,
               aya_text as textWithTashkeel,
               aya_text_emlaey as textEmlaey,
               aya_text_tashkil as textTashkil
        FROM quran
        WHERE sora = :surahNumber AND aya_no = :ayahNumber
    """)
    suspend fun getTextVariants(surahNumber: Int, ayahNumber: Int): AyahTextVariants?
}

// ============= Data Classes for Query Results =============

/**
 * Surah information with Ayah count
 */
data class SurahInfo(
    val surahNumber: Int,
    val surahNameEnglish: String,
    val surahNameArabic: String,
    val ayahCount: Int
)

/**
 * Juz information with Ayah count
 */
data class JuzInfo(
    val juzNumber: Int,
    val ayahCount: Int
)

/**
 * Ayah with single Tafseer
 * Used for Tafseer list display
 */
data class AyahTafseerItem(
    val ayahNumber: Int,
    val ayahText: String,
    val tafseer: String
)

/**
 * Ayah with grammatical analysis
 */
data class AyahAnalysisItem(
    val ayahNumber: Int,
    val ayahText: String,
    val analysis: String
)

/**
 * Ayah with word meanings
 */
data class AyahMeaningsItem(
    val ayahNumber: Int,
    val ayahText: String,
    val meanings: String
)

/**
 * Ayah with revelation reasons
 */
data class AyahReasonsItem(
    val ayahNumber: Int,
    val ayahText: String,
    val reasons: String
)

/**
 * Different text variants for an Ayah
 */
data class AyahTextVariants(
    val surahNumber: Int,
    val ayahNumber: Int,
    val textWithTashkeel: String,
    val textEmlaey: String,
    val textTashkil: String
)
