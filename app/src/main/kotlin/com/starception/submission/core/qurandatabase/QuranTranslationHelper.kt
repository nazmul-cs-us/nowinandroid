package com.starception.submission.core.qurandatabase

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import android.util.Log
import com.starception.submission.download.AssetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

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
    fun getDatabase(context: Context, translationCode: String = "ar", assetRepository: AssetRepository? = null): QuranDatabase {
        // Check if already in cache
        if (databaseCache.containsKey(translationCode)) {
            Log.d(TAG, "♻️ Using cached database for translation: $translationCode")
            return databaseCache[translationCode]!!
        }

        // Determine database filename and CDN key
        val dbFileName = when (translationCode) {
            "ar" -> "quran.db"
            else -> "quran_$translationCode.db"
        }

        val dbAssetPath = "databases/$dbFileName"
        val cdnKey = "databases/quran/$dbFileName"

        Log.d(TAG, "📖 Loading Quran database: $translationCode from $dbAssetPath")

            try {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    QuranDatabase::class.java,
                    "quran_${translationCode}_instance"
                )

                // Try CDN/extracted file first, fall back to bundled asset
                val dbFile = assetRepository?.getDatabaseFile(cdnKey)
                if (dbFile != null) {
                    builder.createFromFile(dbFile)
                } else {
                    builder.createFromAsset(dbAssetPath)
                }

                val database = builder
                    .fallbackToDestructiveMigration()
                    .build()

                Log.d(TAG, "✅ Quran translation database created: $translationCode")

                // Cache the instance
                databaseCache[translationCode] = database

                Log.d(TAG, "✅ Database loaded and cached successfully: $translationCode")
                return database
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to load database for translation: $translationCode", e)
                Log.e(TAG, "❌ Database path: $dbAssetPath", e)
                e.printStackTrace()
                throw e
            }
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
     * Close + evict the in-memory Room instance for [translationCode] AND delete the
     * Room-managed DB file on disk (including -shm / -wal siblings).
     *
     * Why this exists: Room only copies the source database into its managed location
     * on first open. If a previous open used an empty/broken bundled stub and produced
     * a near-empty managed file, simply clearing the in-memory cache and reopening will
     * reuse that broken file — the freshly downloaded CDN source is never re-copied.
     * Deleting the managed file forces Room to re-copy from the current source on the
     * next [getDatabase] call.
     */
    @Synchronized
    fun resetTranslationDatabase(context: Context, translationCode: String) {
        databaseCache.remove(translationCode)?.close()
        val dbName = "quran_${translationCode}_instance"
        val dbDir = context.applicationContext.getDatabasePath(dbName).parentFile ?: return
        listOf(dbName, "$dbName-shm", "$dbName-wal", "$dbName-journal").forEach { name ->
            val f = File(dbDir, name)
            if (f.exists()) {
                val deleted = f.delete()
                Log.d(TAG, "🗑️  ${if (deleted) "deleted" else "FAILED to delete"} $name")
            }
        }
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
    private val translationCode: String = "ar",
    private val assetRepository: AssetRepository? = null
) {

    private val database: QuranDatabase by lazy {
        try {
            QuranTranslationHelper.getDatabase(context, translationCode, assetRepository)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize database for translation: $translationCode", e)
            throw e
        }
    }
    
    private val quranDao: QuranDao by lazy {
        try {
            database.quranDao()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get DAO for translation: $translationCode", e)
            throw e
        }
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
            // Ensure database is initialized before querying
            // This will trigger lazy initialization if not already done
            val dao = quranDao
            
            Log.d(TAG, "🔍 Querying Surah $surahNumber in translation: $translationCode")
            
            // Query for the surah directly
            val entity = dao.getSurahByNumber(surahNumber)
            
            if (entity == null) {
                Log.w(TAG, "⚠️ Query returned null for Surah $surahNumber in translation: $translationCode")
                
                // Debug: Check if database has any surahs
                val allSurahs = try {
                    dao.getAllSurahsOnce()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to get all surahs from database", e)
                    return@withContext null
                }
                
                Log.d(TAG, "📊 Database has ${allSurahs.size} surahs total")
                if (allSurahs.isNotEmpty()) {
                    Log.d(TAG, "📖 First surah: ${allSurahs.first().nameEnglish} (number=${allSurahs.first().number}, id=${allSurahs.first().id})")
                    Log.d(TAG, "📖 Last surah: ${allSurahs.last().nameEnglish} (number=${allSurahs.last().number}, id=${allSurahs.last().id})")
                    
                    // Check if surah number exists in the list
                    val foundSurah = allSurahs.find { it.number == surahNumber }
                    if (foundSurah == null) {
                        Log.w(TAG, "⚠️ Surah number $surahNumber not found in surah list. Available numbers: ${allSurahs.map { it.number }.take(10)}...")
                    }
                } else {
                    Log.e(TAG, "❌ Database appears to be empty - no surahs found!")
                }
                
                return@withContext null
            }
            
            Log.d(TAG, "✅ Found Surah: ${entity.nameEnglish} (ID: ${entity.id}, Number: ${entity.number})")
            val surahId = entity.id ?: 0 // Handle nullable id (should never be null in practice)
            val ayahCount = dao.getAyahCount(surahId)
            Log.d(TAG, "📄 Ayah count: $ayahCount")
            entity.toSurah(ayahCount)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading Surah $surahNumber in translation $translationCode", e)
            e.printStackTrace()
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
            entities.map { it.toAyah(0) } // surahNumber will be 0 for now
        }
    }
    
    /**
     * Get all Ayahs for a specific Surah (one-time read)
     */
    suspend fun getAyahsBySurahOnce(surahId: Int): List<Ayah> = withContext(Dispatchers.IO) {
        try {
            // Get surah first to get its number
            val surah = quranDao.getSurahById(surahId)
            val surahNumber = surah?.number ?: 0
            quranDao.getAyahsBySurahOnce(surahId).map { it.toAyah(surahNumber) }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Ayahs for Surah $surahId", e)
            emptyList()
        }
    }
    
    /**
     * Get all Ayahs for a specific Surah by surah number (using JOIN)
     * This is useful when surah_id might differ between databases or when surahs table is missing
     */
    suspend fun getAyahsBySurahNumber(surahNumber: Int): List<Ayah> = withContext(Dispatchers.IO) {
        try {
            // We already have surahNumber, so pass it directly
            quranDao.getAyahsBySurahNumber(surahNumber).map { it.toAyah(surahNumber) }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Ayahs for Surah number $surahNumber", e)
            emptyList()
        }
    }
    
    /**
     * Get a specific Ayah by its global number
     */
    suspend fun getAyahByNumber(ayahNumber: Int): Ayah? = withContext(Dispatchers.IO) {
        try {
            val ayah = quranDao.getAyahByNumber(ayahNumber)
            if (ayah != null) {
                // Get surah to get its number
                val surah = quranDao.getSurahById(ayah.surahId)
                ayah.toAyah(surah?.number ?: 0)
            } else null
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
            entities.map { it.toAyah(0) } // surahNumber will be 0 for now
        }
    }
    
    /**
     * Get Ayahs by Juz (part) number
     */
    fun getAyahsByJuz(juzNumber: Int): Flow<List<Ayah>> {
        return quranDao.getAyahsByJuz(juzNumber).map { entities ->
            entities.map { it.toAyah(0) } // surahNumber will be 0 for now
        }
    }
    
    /**
     * Get all Ayahs that require Sajda (prostration)
     */
    fun getSajdaAyahs(): Flow<List<Ayah>> {
        return quranDao.getSajdaAyahs().map { entities ->
            entities.map { it.toAyah(0) } // surahNumber will be 0 for now
        }
    }
    
    /**
     * Search Ayahs by text content
     */
    fun searchAyahs(query: String): Flow<List<Ayah>> {
        return quranDao.searchAyahs(query).map { entities ->
            entities.map { it.toAyah(0) } // surahNumber will be 0 for now
        }
    }
    
    /**
     * Search Ayahs with result limit
     */
    suspend fun searchAyahsWithLimit(query: String, limit: Int = 50): List<Ayah> = withContext(Dispatchers.IO) {
        try {
            quranDao.searchAyahsWithLimit(query, limit).map { it.toAyah(0) } // surahNumber will be 0 for now
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
            quranDao.getAyahsPage(pageSize, offset).map { it.toAyah(0) } // surahNumber will be 0 for now
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

