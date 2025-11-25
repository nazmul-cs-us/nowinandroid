package com.starception.submission.core.qurandatabase

import android.content.Context
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Enhanced Quran database access
 * Provides a clean API for accessing Tafseer, grammar analysis, and line-by-line features
 *
 * This repository focuses on the enhanced Arabic features:
 * - 3 Tafseer books (Saadi, Moysar, Baghawi)
 * - Arabic grammar analysis (I'rab)
 * - Word meanings and explanations
 * - Revelation context (Asbab al-Nuzul)
 * - Line-by-line page layout
 * - Multiple Arabic text variants
 */
@Singleton
class QuranEnhancedRepository @Inject constructor(
    private val quranEnhancedDao: QuranEnhancedDao
) {

    // ============= Basic Ayah Access =============

    /**
     * Get all Ayahs for a specific Surah
     * Returns lightweight models for list display
     */
    suspend fun getAyahsBySurah(surahNumber: Int): List<QuranAyahBasic> {
        return quranEnhancedDao.getAyahsBySurah(surahNumber)
    }

    /**
     * Get a specific Ayah with all data
     */
    suspend fun getAyah(surahNumber: Int, ayahNumber: Int): QuranEnhancedAyah? {
        return quranEnhancedDao.getAyah(surahNumber, ayahNumber)?.toQuranEnhancedAyah()
    }

    /**
     * Get a specific Ayah by ID
     */
    suspend fun getAyahById(ayahId: Int): QuranEnhancedAyah? {
        return quranEnhancedDao.getAyahById(ayahId)?.toQuranEnhancedAyah()
    }

    /**
     * Get total Ayah count
     */
    suspend fun getTotalAyahCount(): Int {
        return quranEnhancedDao.getTotalAyahCount()
    }

    /**
     * Get Ayah count for a Surah
     */
    suspend fun getAyahCountBySurah(surahNumber: Int): Int {
        return quranEnhancedDao.getAyahCountBySurah(surahNumber)
    }

    // ============= Surah Information =============

    /**
     * Get list of all Surahs with their info
     */
    suspend fun getAllSurahs(): List<SurahInfo> {
        return quranEnhancedDao.getAllSurahs()
    }

    /**
     * Get information for a specific Surah
     */
    suspend fun getSurahInfo(surahNumber: Int): SurahInfo? {
        return quranEnhancedDao.getSurahInfo(surahNumber)
    }

    /**
     * Search Surahs by name
     */
    suspend fun searchSurahs(query: String): List<SurahInfo> {
        return quranEnhancedDao.searchSurahs(query)
    }

    // ============= Page-Based Access (Mushaf Layout) =============

    /**
     * Get all Ayahs on a specific page
     * For traditional Mushaf page display
     */
    suspend fun getAyahsByPage(pageNumber: Int): List<QuranAyahPage> {
        return quranEnhancedDao.getAyahsByPage(pageNumber)
    }

    /**
     * Get Ayahs by line range on a page
     * For line-by-line audio playback
     */
    suspend fun getAyahsByLineRange(
        pageNumber: Int,
        startLine: Int,
        endLine: Int
    ): List<QuranEnhancedAyah> {
        return quranEnhancedDao.getAyahsByLineRange(pageNumber, startLine, endLine)
            .map { it.toQuranEnhancedAyah() }
    }

    /**
     * Get total number of pages
     */
    suspend fun getTotalPages(): Int {
        return quranEnhancedDao.getTotalPages()
    }

    /**
     * Get Ayahs by page as Flow for reactive updates
     */
    fun getAyahsByPageFlow(pageNumber: Int): Flow<List<QuranEnhancedEntity>> {
        return quranEnhancedDao.getAyahsByPageFlow(pageNumber)
    }

    // ============= Juz-Based Access =============

    /**
     * Get all Ayahs in a specific Juz
     */
    suspend fun getAyahsByJuz(juzNumber: Int): List<QuranEnhancedAyah> {
        return quranEnhancedDao.getAyahsByJuz(juzNumber)
            .map { it.toQuranEnhancedAyah() }
    }

    /**
     * Get all Juz information
     */
    suspend fun getAllJuzInfo(): List<JuzInfo> {
        return quranEnhancedDao.getAllJuzInfo()
    }

    /**
     * Get Ayahs by Juz as Flow
     */
    fun getAyahsByJuzFlow(juzNumber: Int): Flow<List<QuranEnhancedEntity>> {
        return quranEnhancedDao.getAyahsByJuzFlow(juzNumber)
    }

    // ============= Tafseer Access =============

    /**
     * Get complete Tafseer for an Ayah
     * Includes all three Tafseer books plus related information
     */
    suspend fun getTafseerForAyah(surahNumber: Int, ayahNumber: Int): QuranAyahTafseer? {
        return quranEnhancedDao.getTafseerForAyah(surahNumber, ayahNumber)
    }

    /**
     * Get Tafseer Saadi for entire Surah
     * Most popular contemporary Tafseer
     */
    suspend fun getTafseerSaadiBySurah(surahNumber: Int): List<AyahTafseerItem> {
        return quranEnhancedDao.getTafseerSaadiBySurah(surahNumber)
    }

    /**
     * Get Tafseer Moysar for entire Surah
     * Simplified Tafseer approved by King Fahd Complex
     */
    suspend fun getTafseerMoysarBySurah(surahNumber: Int): List<AyahTafseerItem> {
        return quranEnhancedDao.getTafseerMoysarBySurah(surahNumber)
    }

    /**
     * Get Tafseer Baghawi for entire Surah
     * Classical Tafseer
     */
    suspend fun getTafseerBaghawiBySurah(surahNumber: Int): List<AyahTafseerItem> {
        return quranEnhancedDao.getTafseerBaghawiBySurah(surahNumber)
    }

    // ============= Grammar and Analysis =============

    /**
     * Get grammatical analysis (I'rab) for an Ayah
     */
    suspend fun getGrammaticalAnalysis(
        surahNumber: Int,
        ayahNumber: Int
    ): AyahAnalysisItem? {
        return quranEnhancedDao.getGrammaticalAnalysis(surahNumber, ayahNumber)
    }

    /**
     * Get word meanings for an Ayah
     */
    suspend fun getAyahMeanings(surahNumber: Int, ayahNumber: Int): AyahMeaningsItem? {
        return quranEnhancedDao.getAyahMeanings(surahNumber, ayahNumber)
    }

    /**
     * Get revelation reasons (Asbab al-Nuzul) for an Ayah
     */
    suspend fun getRevelationReasons(
        surahNumber: Int,
        ayahNumber: Int
    ): AyahReasonsItem? {
        return quranEnhancedDao.getRevelationReasons(surahNumber, ayahNumber)
    }

    // ============= Search Functions =============

    /**
     * Search Ayahs by Arabic text (with tashkeel)
     */
    suspend fun searchAyahsArabic(query: String, limit: Int = 100): List<QuranEnhancedAyah> {
        return quranEnhancedDao.searchAyahsArabic(query, limit)
            .map { it.toQuranEnhancedAyah() }
    }

    /**
     * Search Ayahs by simplified Arabic text
     * Better for search as users often type without diacritics
     */
    suspend fun searchAyahsEmlaey(query: String, limit: Int = 100): List<QuranEnhancedAyah> {
        return quranEnhancedDao.searchAyahsEmlaey(query, limit)
            .map { it.toQuranEnhancedAyah() }
    }

    /**
     * Search in Tafseer Saadi
     */
    suspend fun searchTafseerSaadi(query: String, limit: Int = 50): List<QuranEnhancedAyah> {
        return quranEnhancedDao.searchTafseerSaadi(query, limit)
            .map { it.toQuranEnhancedAyah() }
    }

    /**
     * Search in all Tafseer books
     */
    suspend fun searchAllTafseer(query: String, limit: Int = 50): List<QuranEnhancedAyah> {
        return quranEnhancedDao.searchAllTafseer(query, limit)
            .map { it.toQuranEnhancedAyah() }
    }

    // ============= Text Variants =============

    /**
     * Get different text variants for an Ayah
     */
    suspend fun getTextVariants(surahNumber: Int, ayahNumber: Int): AyahTextVariants? {
        return quranEnhancedDao.getTextVariants(surahNumber, ayahNumber)
    }
}

/**
 * Unified Quran Repository
 * Provides access to both standard (with translations) and enhanced (with Tafseer) databases
 */
@Singleton
class UnifiedQuranRepository @Inject constructor(
    private val quranDao: QuranDao,
    private val quranEnhancedDao: QuranEnhancedDao
) {

    // ============= Standard Database Access =============

    /**
     * Get Ayahs from standard database (supports multiple translations)
     */
    suspend fun getAyahsWithTranslation(surahId: Int): List<AyahEntity> {
        return quranDao.getAyahsBySurahOnce(surahId)
    }

    /**
     * Get all Surahs from standard database
     */
    suspend fun getAllSurahsStandard(): List<SurahEntity> {
        return quranDao.getAllSurahsOnce()
    }

    // ============= Enhanced Database Access =============

    /**
     * Get Ayahs with enhanced features (Tafseer, grammar, etc.)
     */
    suspend fun getAyahsEnhanced(surahNumber: Int): List<QuranAyahBasic> {
        return quranEnhancedDao.getAyahsBySurah(surahNumber)
    }

    /**
     * Get all Surahs from enhanced database
     */
    suspend fun getAllSurahsEnhanced(): List<SurahInfo> {
        return quranEnhancedDao.getAllSurahs()
    }

    /**
     * Get Tafseer for an Ayah
     */
    suspend fun getTafseer(surahNumber: Int, ayahNumber: Int): QuranAyahTafseer? {
        return quranEnhancedDao.getTafseerForAyah(surahNumber, ayahNumber)
    }

    /**
     * Get page-based Ayahs for Mushaf display
     */
    suspend fun getAyahsForPage(pageNumber: Int): List<QuranAyahPage> {
        return quranEnhancedDao.getAyahsByPage(pageNumber)
    }

    // ============= Combined Search =============

    /**
     * Search across both databases
     * Returns results from standard database (for translation support)
     */
    suspend fun searchAyahsWithTranslation(query: String, limit: Int = 100): List<AyahEntity> {
        return quranDao.searchAyahsWithLimit(query, limit)
    }

    /**
     * Search in Arabic text and Tafseer
     * Returns results from enhanced database
     */
    suspend fun searchArabicAndTafseer(query: String, limit: Int = 50): List<QuranEnhancedEntity> {
        return quranEnhancedDao.searchAllTafseer(query, limit)
    }
}
