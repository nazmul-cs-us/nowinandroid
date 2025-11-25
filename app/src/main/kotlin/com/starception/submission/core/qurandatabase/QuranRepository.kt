package com.starception.submission.core.qurandatabase

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for accessing Quran data
 * Provides a clean API for the UI layer
 */
@Singleton
class QuranRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val database: QuranDatabase by lazy {
        QuranDatabase.getInstance(context)
    }

    private val quranDao: QuranDao by lazy {
        database.quranDao()
    }

    private val preferences by lazy {
        context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "QuranRepository"
        private const val PREF_BOOKMARKED_SURAHS = "bookmarked_surahs"
        private const val PREF_FAVOURITE_AYAHS = "favourite_ayahs"
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
            Log.d(TAG, "🔍 Getting Surah by number: $surahNumber")
            val allSurahs = quranDao.getAllSurahsOnce()
            Log.d(TAG, "📊 Total Surahs in DB: ${allSurahs.size}")
            if (allSurahs.isNotEmpty()) {
                Log.d(TAG, "📖 Sample Surah: ${allSurahs.first().nameEnglish} (number=${allSurahs.first().number})")
            }
            
            val entity = quranDao.getSurahByNumber(surahNumber)
            if (entity == null) {
                Log.e(TAG, "❌ Surah with number $surahNumber not found")
                return@withContext null
            }
            
            Log.d(TAG, "✅ Found Surah: ${entity.nameEnglish} (ID: ${entity.id}, Number: ${entity.number})")
            val surahId = entity.id ?: 0 // Handle nullable id (should never be null in practice)
            val ayahCount = quranDao.getAyahCount(surahId)
            Log.d(TAG, "📄 Ayah count: $ayahCount")
            entity.toSurah(ayahCount)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading Surah $surahNumber", e)
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
            val surah = quranDao.getSurahById(surahId)
            val surahNumber = surah?.number ?: 0
            quranDao.getAyahsBySurahOnce(surahId).map { it.toAyah(surahNumber) }
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

            Log.d(TAG, "📊 Database status:")
            Log.d(TAG, "   - Surahs: $surahCount (expected: 114)")
            Log.d(TAG, "   - Ayahs: $ayahCount (expected: 6236)")
            Log.d(TAG, "   - Status: ${if (isValid) "✅ Valid" else "❌ Invalid"}")

            isValid
        } catch (e: Exception) {
            Log.e(TAG, "❌ Database not initialized", e)
            false
        }
    }

    // ============= Bookmark Operations =============

    /**
     * Check if a Surah is bookmarked
     */
    fun isSurahBookmarked(surahNumber: Int): Boolean {
        val bookmarkedSurahs = getBookmarkedSurahs()
        val isBookmarked = surahNumber in bookmarkedSurahs
        Log.d("QuranRepository_BOOKMARK", "🔍 CHECK | surah=$surahNumber | bookmarked=$isBookmarked | all_bookmarks=$bookmarkedSurahs")
        return isBookmarked
    }

    /**
     * Toggle bookmark status for a Surah
     */
    fun setSurahBookmarked(surahNumber: Int, bookmarked: Boolean) {
        Log.d("QuranRepository_BOOKMARK", "💾 SET_START | surah=$surahNumber | bookmarked=$bookmarked")
        val bookmarkedSurahs = getBookmarkedSurahs().toMutableSet()
        Log.d("QuranRepository_BOOKMARK", "📊 BEFORE_SET | all_bookmarks=$bookmarkedSurahs")

        if (bookmarked) {
            bookmarkedSurahs.add(surahNumber)
            Log.d("QuranRepository_BOOKMARK", "➕ ADDED | surah=$surahNumber")
        } else {
            bookmarkedSurahs.remove(surahNumber)
            Log.d("QuranRepository_BOOKMARK", "➖ REMOVED | surah=$surahNumber")
        }

        Log.d("QuranRepository_BOOKMARK", "📊 AFTER_SET | all_bookmarks=$bookmarkedSurahs")
        saveBookmarkedSurahs(bookmarkedSurahs)

        // Verify the save worked
        val verifyBookmarks = getBookmarkedSurahs()
        val verified = verifyBookmarks.contains(surahNumber) == bookmarked
        Log.d("QuranRepository_BOOKMARK", "✅ VERIFY | surah=$surahNumber | expected=$bookmarked | actual=${verifyBookmarks.contains(surahNumber)} | success=$verified | all_bookmarks=$verifyBookmarks")

        Log.d(TAG, if (bookmarked) "📌 Bookmarked Surah $surahNumber" else "🔖 Removed bookmark from Surah $surahNumber")
    }

    /**
     * Get all bookmarked Surah numbers
     */
    fun getBookmarkedSurahs(): Set<Int> {
        val bookmarksString = preferences.getString(PREF_BOOKMARKED_SURAHS, "") ?: ""
        val bookmarks = if (bookmarksString.isBlank()) {
            emptySet()
        } else {
            bookmarksString.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        }
        Log.d("QuranRepository_BOOKMARK", "📖 GET_ALL | raw_string='$bookmarksString' | count=${bookmarks.size} | bookmarks=$bookmarks")
        return bookmarks
    }

    /**
     * Get all bookmarked Surahs as full Surah objects
     */
    suspend fun getBookmarkedSurahsList(): List<Surah> = withContext(Dispatchers.IO) {
        try {
            val bookmarkedNumbers = getBookmarkedSurahs()
            bookmarkedNumbers.mapNotNull { surahNumber ->
                getSurahByNumber(surahNumber)
            }.sortedBy { it.number }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bookmarked Surahs", e)
            emptyList()
        }
    }

    private fun saveBookmarkedSurahs(bookmarkedSurahs: Set<Int>) {
        val bookmarksString = bookmarkedSurahs.joinToString(",")
        Log.d("QuranRepository_BOOKMARK", "💾 SAVE | count=${bookmarkedSurahs.size} | bookmarks=$bookmarkedSurahs | string='$bookmarksString'")
        preferences.edit().putString(
            PREF_BOOKMARKED_SURAHS,
            bookmarksString
        ).apply()
        Log.d("QuranRepository_BOOKMARK", "💾 SAVE_COMPLETE | key='$PREF_BOOKMARKED_SURAHS'")
    }

    // ============= Favourite Ayah Operations (Database) =============

    /**
     * Check if an Ayah is marked as favourite
     * @param surahNumber The surah number (1-114)
     * @param ayahNumber The ayah number within the surah
     */
    suspend fun isAyahFavourite(surahNumber: Int, ayahNumber: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val isFavourite = quranDao.isAyahFavourite(surahNumber, ayahNumber)
            Log.d("QuranRepository_FAVOURITE", "🔍 CHECK | surah=$surahNumber | ayah=$ayahNumber | favourite=$isFavourite")
            isFavourite
        } catch (e: Exception) {
            Log.e(TAG, "Error checking favourite status for ayah $surahNumber:$ayahNumber", e)
            false
        }
    }

    /**
     * Set favourite status for an Ayah
     * @param surahNumber The surah number (1-114)
     * @param ayahNumber The ayah number within the surah
     * @param favourite True to mark as favourite, false to remove
     */
    suspend fun setAyahFavourite(surahNumber: Int, ayahNumber: Int, favourite: Boolean) = withContext(Dispatchers.IO) {
        try {
            Log.d("QuranRepository_FAVOURITE", "💾 SET_START | surah=$surahNumber | ayah=$ayahNumber | favourite=$favourite")

            if (favourite) {
                val entity = FavouriteAyahEntity(
                    surahNumber = surahNumber,
                    ayahNumber = ayahNumber
                )
                quranDao.insertFavouriteAyah(entity)
                Log.d("QuranRepository_FAVOURITE", "➕ ADDED | surah=$surahNumber | ayah=$ayahNumber")
            } else {
                quranDao.deleteFavouriteAyah(surahNumber, ayahNumber)
                Log.d("QuranRepository_FAVOURITE", "➖ REMOVED | surah=$surahNumber | ayah=$ayahNumber")
            }

            // Verify the operation
            val verifyFavourite = quranDao.isAyahFavourite(surahNumber, ayahNumber)
            val verified = verifyFavourite == favourite
            Log.d("QuranRepository_FAVOURITE", "✅ VERIFY | surah=$surahNumber | ayah=$ayahNumber | expected=$favourite | actual=$verifyFavourite | success=$verified")

            Log.d(TAG, if (favourite) "💖 Favourited Ayah $surahNumber:$ayahNumber" else "🤍 Removed favourite from Ayah $surahNumber:$ayahNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting favourite status for ayah $surahNumber:$ayahNumber", e)
        }
    }

    /**
     * Get favourite ayah numbers for a specific surah
     * @param surahNumber The surah number (1-114)
     * @return Set of ayah numbers that are favourited in this surah
     */
    suspend fun getFavouriteAyahsForSurah(surahNumber: Int): Set<Int> = withContext(Dispatchers.IO) {
        try {
            val favourites = quranDao.getFavouriteAyahsForSurah(surahNumber)
            val ayahNumbers = favourites.map { it.ayahNumber }.toSet()
            Log.d("QuranRepository_FAVOURITE", "📖 GET_FOR_SURAH | surah=$surahNumber | count=${ayahNumbers.size} | ayahs=$ayahNumbers")
            ayahNumbers
        } catch (e: Exception) {
            Log.e(TAG, "Error getting favourite ayahs for surah $surahNumber", e)
            emptySet()
        }
    }

    /**
     * Get all favourite ayahs
     */
    suspend fun getAllFavouriteAyahs(): List<FavouriteAyahEntity> = withContext(Dispatchers.IO) {
        try {
            val favourites = quranDao.getAllFavouriteAyahs()
            Log.d("QuranRepository_FAVOURITE", "📖 GET_ALL | count=${favourites.size}")
            favourites
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all favourite ayahs", e)
            emptyList()
        }
    }
}

