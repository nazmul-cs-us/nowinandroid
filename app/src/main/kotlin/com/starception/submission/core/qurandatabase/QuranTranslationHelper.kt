package com.starception.submission.core.qurandatabase

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Helper class to manage multiple Quran translation databases
 * Supports loading Arabic (quran.db) and all translations
 */
object QuranTranslationHelper {
    
    private const val TAG = "QuranTranslationHelper"
    
    // Cache of database instances by translation code
    private val databaseCache = mutableMapOf<String, QuranDatabase>()
    
    /**
     * Get database instance for a specific translation
     * 
     * @param context Application context
     * @param translationCode Translation code: "ar" for Arabic, or any other supported code
     * @return QuranDatabase instance for the requested translation
     */
    @Synchronized
    fun getDatabase(context: Context, translationCode: String = "ar"): QuranDatabase {
        // Check if already in cache
        if (databaseCache.containsKey(translationCode)) {
            return databaseCache[translationCode]!!
        }
        
        // Determine database filename
        val dbFileName = when (translationCode) {
            "ar" -> "quran.db"
            else -> "quran_$translationCode.db"
        }
        
        val dbAssetPath = "databases/$dbFileName"
        
        Log.d(TAG, "📖 Loading Quran database: $translationCode from $dbAssetPath")
        
        // Create database instance
        val database = Room.databaseBuilder(
            context.applicationContext,
            QuranDatabase::class.java,
            "quran_${translationCode}_instance"
        )
            .createFromAsset(dbAssetPath)
            .fallbackToDestructiveMigration()
            .build()
        
        Log.d(TAG, "✅ Quran translation database created: $translationCode")
        
        // Cache the instance
        databaseCache[translationCode] = database
        
        Log.d(TAG, "✅ Database loaded successfully: $translationCode")
        return database
    }
    
    /**
     * Clear database cache (useful for testing or memory management)
     */
    @Synchronized
    fun clearCache() {
        databaseCache.values.forEach { db ->
            db.close()
        }
        databaseCache.clear()
        Log.d(TAG, "🗑️  Database cache cleared")
    }
    
    /**
     * Get available translation codes
     */
    fun getAvailableTranslations(): List<String> {
        return listOf(
            "ar",              // Arabic
            "transliteration", // English Transliteration
            "bn",              // Bengali
            "zh",              // Chinese
            "en",              // English
            "es",              // Spanish
            "fr",              // French
            "id",              // Indonesian
            "ru",              // Russian
            "sv",              // Swedish
            "tr",              // Turkish
            "ur"               // Urdu
        )
    }
    
    /**
     * Get display name for a translation code
     */
    fun getTranslationName(code: String): String {
        return when (code) {
            "ar" -> "Arabic"
            "transliteration" -> "English Transliteration"
            "bn" -> "Bengali"
            "zh" -> "Chinese"
            "en" -> "English"
            "es" -> "Spanish"
            "fr" -> "French"
            "id" -> "Indonesian"
            "ru" -> "Russian"
            "sv" -> "Swedish"
            "tr" -> "Turkish"
            "ur" -> "Urdu"
            else -> "Unknown"
        }
    }
}

/**
 * Repository for accessing translated Quran data
 * Provides a clean API for the UI layer with translation support
 */
class QuranTranslationRepository(
    private val context: Context,
    private val translationCode: String = "ar"
) {
    
    private val database: QuranDatabase by lazy {
        QuranTranslationHelper.getDatabase(context, translationCode)
    }
    
    private val quranDao: QuranDao by lazy {
        database.quranDao()
    }
    
    companion object {
        private const val TAG = "QuranTranslationRepository"
    }
    
    // ============= Surah Operations =============
    
    /**
     * Get all Surahs
     */
    fun getAllSurahs(): Flow<List<Surah>> {
        return quranDao.getAllSurahs().map { entities ->
            entities.map { entity ->
                entity.toSurah()
            }
        }
    }
    
    /**
     * Get all Surahs with their Ayah counts
     */
    suspend fun getAllSurahsWithCounts(): List<Surah> = withContext(Dispatchers.IO) {
        try {
            quranDao.getAllSurahsWithCounts()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Surahs with counts", e)
            emptyList()
        }
    }
    
    /**
     * Get a specific Surah by number (1-114)
     */
    suspend fun getSurahByNumber(surahNumber: Int): Surah? = withContext(Dispatchers.IO) {
        try {
            val entity = quranDao.getSurahByNumber(surahNumber)
            entity?.let {
                val ayahCount = quranDao.getAyahCount(it.id)
                it.toSurah(ayahCount)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Surah $surahNumber", e)
            null
        }
    }
    
    /**
     * Get a specific Surah by ID
     */
    suspend fun getSurahById(surahId: Int): Surah? = withContext(Dispatchers.IO) {
        try {
            quranDao.getSurahWithAyahCount(surahId)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Surah with ID $surahId", e)
            null
        }
    }
    
    /**
     * Get Surahs by revelation type (Meccan/Medinan)
     */
    fun getSurahsByType(revelationType: String): Flow<List<Surah>> {
        return quranDao.getSurahsByType(revelationType).map { entities ->
            entities.map { it.toSurah() }
        }
    }
    
    /**
     * Search Surahs by name
     */
    fun searchSurahs(query: String): Flow<List<Surah>> {
        return quranDao.searchSurahs(query).map { entities ->
            entities.map { it.toSurah() }
        }
    }
    
    // ============= Ayah Operations =============
    
    /**
     * Get all Ayahs for a specific Surah
     */
    fun getAyahsBySurah(surahId: Int): Flow<List<Ayah>> {
        return quranDao.getAyahsBySurah(surahId).map { entities ->
            entities.map { it.toAyah() }
        }
    }
    
    /**
     * Get all Ayahs for a specific Surah (one-time read)
     */
    suspend fun getAyahsBySurahOnce(surahId: Int): List<Ayah> = withContext(Dispatchers.IO) {
        try {
            quranDao.getAyahsBySurahOnce(surahId).map { it.toAyah() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Ayahs for Surah $surahId", e)
            emptyList()
        }
    }
    
    /**
     * Get a specific Ayah by its global number
     */
    suspend fun getAyahByNumber(ayahNumber: Int): Ayah? = withContext(Dispatchers.IO) {
        try {
            quranDao.getAyahByNumber(ayahNumber)?.toAyah()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Ayah $ayahNumber", e)
            null
        }
    }
    
    /**
     * Get Ayahs by page number
     */
    fun getAyahsByPage(pageNumber: Int): Flow<List<Ayah>> {
        return quranDao.getAyahsByPage(pageNumber).map { entities ->
            entities.map { it.toAyah() }
        }
    }
    
    /**
     * Get Ayahs by Juz (part) number
     */
    fun getAyahsByJuz(juzNumber: Int): Flow<List<Ayah>> {
        return quranDao.getAyahsByJuz(juzNumber).map { entities ->
            entities.map { it.toAyah() }
        }
    }
    
    /**
     * Get all Ayahs that require Sajda (prostration)
     */
    fun getSajdaAyahs(): Flow<List<Ayah>> {
        return quranDao.getSajdaAyahs().map { entities ->
            entities.map { it.toAyah() }
        }
    }
    
    /**
     * Search Ayahs by text content
     */
    fun searchAyahs(query: String): Flow<List<Ayah>> {
        return quranDao.searchAyahs(query).map { entities ->
            entities.map { it.toAyah() }
        }
    }
    
    /**
     * Search Ayahs with result limit
     */
    suspend fun searchAyahsWithLimit(query: String, limit: Int = 50): List<Ayah> = withContext(Dispatchers.IO) {
        try {
            quranDao.searchAyahsWithLimit(query, limit).map { it.toAyah() }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Ayahs for '$query'", e)
            emptyList()
        }
    }
    
    // ============= Statistics =============
    
    /**
     * Get total number of Ayahs in the Quran (should be 6236)
     */
    suspend fun getTotalAyahCount(): Int = withContext(Dispatchers.IO) {
        try {
            quranDao.getTotalAyahCount()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total Ayah count", e)
            0
        }
    }
    
    /**
     * Get number of Ayahs in a specific Surah
     */
    suspend fun getAyahCount(surahId: Int): Int = withContext(Dispatchers.IO) {
        try {
            quranDao.getAyahCount(surahId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Ayah count for Surah $surahId", e)
            0
        }
    }
    
    // ============= Pagination =============
    
    /**
     * Get a page of Ayahs for pagination
     */
    suspend fun getAyahsPage(page: Int, pageSize: Int = 20): List<Ayah> = withContext(Dispatchers.IO) {
        try {
            val offset = page * pageSize
            quranDao.getAyahsPage(pageSize, offset).map { it.toAyah() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Ayahs page $page", e)
            emptyList()
        }
    }
    
    // ============= Health Check =============
    
    /**
     * Check if database is properly initialized
     */
    suspend fun isDatabaseInitialized(): Boolean = withContext(Dispatchers.IO) {
        try {
            val surahCount = quranDao.getAllSurahsOnce().size
            val ayahCount = quranDao.getTotalAyahCount()
            
            val isValid = surahCount == 114 && ayahCount > 6000
            
            Log.d(TAG, "📊 Database status ($translationCode):")
            Log.d(TAG, "   - Surahs: $surahCount (expected: 114)")
            Log.d(TAG, "   - Ayahs: $ayahCount (expected: 6236)")
            Log.d(TAG, "   - Status: ${if (isValid) "✅ Valid" else "❌ Invalid"}")
            
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "❌ Database not initialized", e)
            false
        }
    }
}

