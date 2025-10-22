package com.starception.submission.core.qurandatabase

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Quran database
 * Provides methods to query Surahs and Ayahs
 */
@Dao
interface QuranDao {
    
    // ============= Surah Queries =============
    
    /**
     * Get all Surahs
     */
    @Query("SELECT * FROM surahs ORDER BY number ASC")
    fun getAllSurahs(): Flow<List<SurahEntity>>
    
    /**
     * Get all Surahs (one-time read)
     */
    @Query("SELECT * FROM surahs ORDER BY number ASC")
    suspend fun getAllSurahsOnce(): List<SurahEntity>
    
    /**
     * Get a specific Surah by ID
     */
    @Query("SELECT * FROM surahs WHERE id = :surahId")
    suspend fun getSurahById(surahId: Int): SurahEntity?
    
    /**
     * Get a specific Surah by number
     */
    @Query("SELECT * FROM surahs WHERE number = :surahNumber")
    suspend fun getSurahByNumber(surahNumber: Int): SurahEntity?
    
    /**
     * Get Surahs by revelation type
     */
    @Query("SELECT * FROM surahs WHERE type = :revelationType ORDER BY number ASC")
    fun getSurahsByType(revelationType: String): Flow<List<SurahEntity>>
    
    /**
     * Search Surahs by name (Arabic or English)
     */
    @Query("""
        SELECT * FROM surahs 
        WHERE name_ar LIKE '%' || :query || '%' 
           OR name_en LIKE '%' || :query || '%'
           OR name_en_translation LIKE '%' || :query || '%'
        ORDER BY number ASC
    """)
    fun searchSurahs(query: String): Flow<List<SurahEntity>>
    
    // ============= Ayah Queries =============
    
    /**
     * Get all Ayahs for a specific Surah
     */
    @Query("SELECT * FROM ayahs WHERE surah_id = :surahId ORDER BY number_in_surah ASC")
    fun getAyahsBySurah(surahId: Int): Flow<List<AyahEntity>>
    
    /**
     * Get all Ayahs for a specific Surah (one-time read)
     */
    @Query("SELECT * FROM ayahs WHERE surah_id = :surahId ORDER BY number_in_surah ASC")
    suspend fun getAyahsBySurahOnce(surahId: Int): List<AyahEntity>
    
    /**
     * Get a specific Ayah by its global number
     */
    @Query("SELECT * FROM ayahs WHERE number = :ayahNumber")
    suspend fun getAyahByNumber(ayahNumber: Int): AyahEntity?
    
    /**
     * Get a specific Ayah by ID
     */
    @Query("SELECT * FROM ayahs WHERE id = :ayahId")
    suspend fun getAyahById(ayahId: Int): AyahEntity?
    
    /**
     * Get Ayahs by page number
     */
    @Query("SELECT * FROM ayahs WHERE page = :pageNumber ORDER BY number ASC")
    fun getAyahsByPage(pageNumber: Int): Flow<List<AyahEntity>>
    
    /**
     * Get Ayahs by Juz number
     */
    @Query("SELECT * FROM ayahs WHERE juz_id = :juzNumber ORDER BY number ASC")
    fun getAyahsByJuz(juzNumber: Int): Flow<List<AyahEntity>>
    
    /**
     * Get Ayahs by Hizb number
     */
    @Query("SELECT * FROM ayahs WHERE hizb_id = :hizbNumber ORDER BY number ASC")
    fun getAyahsByHizb(hizbNumber: Int): Flow<List<AyahEntity>>
    
    /**
     * Get all Ayahs with Sajda (prostration)
     */
    @Query("SELECT * FROM ayahs WHERE sajda = 1 ORDER BY number ASC")
    fun getSajdaAyahs(): Flow<List<AyahEntity>>
    
    /**
     * Search Ayahs by text content
     */
    @Query("SELECT * FROM ayahs WHERE text LIKE '%' || :query || '%' ORDER BY number ASC")
    fun searchAyahs(query: String): Flow<List<AyahEntity>>
    
    /**
     * Search Ayahs by text content with limit
     */
    @Query("SELECT * FROM ayahs WHERE text LIKE '%' || :query || '%' ORDER BY number ASC LIMIT :limit")
    suspend fun searchAyahsWithLimit(query: String, limit: Int): List<AyahEntity>
    
    // ============= Combined Queries =============
    
    /**
     * Get count of Ayahs in a Surah
     */
    @Query("SELECT COUNT(*) FROM ayahs WHERE surah_id = :surahId")
    suspend fun getAyahCount(surahId: Int): Int
    
    /**
     * Get Surah with its Ayah count
     */
    @Transaction
    suspend fun getSurahWithAyahCount(surahId: Int): Surah? {
        val surahEntity = getSurahById(surahId) ?: return null
        val ayahCount = getAyahCount(surahId)
        return surahEntity.toSurah(ayahCount)
    }
    
    /**
     * Get all Surahs with their Ayah counts
     */
    @Transaction
    suspend fun getAllSurahsWithCounts(): List<Surah> {
        val surahs = getAllSurahsOnce()
        return surahs.map { surahEntity ->
            val ayahCount = getAyahCount(surahEntity.id)
            surahEntity.toSurah(ayahCount)
        }
    }
    
    /**
     * Get total number of Ayahs in the Quran
     */
    @Query("SELECT COUNT(*) FROM ayahs")
    suspend fun getTotalAyahCount(): Int
    
    /**
     * Get range of Ayahs (for pagination)
     */
    @Query("SELECT * FROM ayahs ORDER BY number ASC LIMIT :limit OFFSET :offset")
    suspend fun getAyahsPage(limit: Int, offset: Int): List<AyahEntity>
}

