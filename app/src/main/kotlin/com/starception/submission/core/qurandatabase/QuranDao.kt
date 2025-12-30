package com.starception.submission.core.qurandatabase

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
     * Get all Ayahs for a specific Surah by surah number (using JOIN)
     * This is useful when surah_id might differ between databases
     */
    @Query("""
        SELECT ayahs.* FROM ayahs 
        INNER JOIN surahs ON ayahs.surah_id = surahs.id 
        WHERE surahs.number = :surahNumber 
        ORDER BY ayahs.number_in_surah ASC
    """)
    suspend fun getAyahsBySurahNumber(surahNumber: Int): List<AyahEntity>
    
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
        val entityId = surahEntity.id ?: surahId // Use entity id if available, fallback to parameter
        val ayahCount = getAyahCount(entityId)
        return surahEntity.toSurah(ayahCount)
    }
    
    /**
     * Get all Surahs with their Ayah counts
     */
    @Transaction
    suspend fun getAllSurahsWithCounts(): List<Surah> {
        val surahs = getAllSurahsOnce()
        return surahs.map { surahEntity ->
            val surahId = surahEntity.id ?: 0 // Handle nullable id (should never be null in practice)
            val ayahCount = getAyahCount(surahId)
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

    // ============= Favourite Ayah Operations =============

    /**
     * Insert a favourite ayah
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavouriteAyah(favourite: FavouriteAyahEntity)

    /**
     * Delete a favourite ayah
     */
    @Query("DELETE FROM favourite_ayahs WHERE surah_number = :surahNumber AND ayah_number = :ayahNumber")
    suspend fun deleteFavouriteAyah(surahNumber: Int, ayahNumber: Int)

    /**
     * Check if an ayah is favourited
     */
    @Query("SELECT EXISTS(SELECT 1 FROM favourite_ayahs WHERE surah_number = :surahNumber AND ayah_number = :ayahNumber LIMIT 1)")
    suspend fun isAyahFavourite(surahNumber: Int, ayahNumber: Int): Boolean

    /**
     * Get all favourite ayahs
     */
    @Query("SELECT * FROM favourite_ayahs ORDER BY created_at DESC")
    suspend fun getAllFavouriteAyahs(): List<FavouriteAyahEntity>

    /**
     * Get favourite ayahs for a specific surah
     */
    @Query("SELECT * FROM favourite_ayahs WHERE surah_number = :surahNumber ORDER BY ayah_number ASC")
    suspend fun getFavouriteAyahsForSurah(surahNumber: Int): List<FavouriteAyahEntity>

    // ============= Ayah Note Operations =============

    /**
     * Insert a new ayah note
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAyahNote(note: AyahNoteEntity): Long

    /**
     * Update an existing ayah note
     */
    @Update
    suspend fun updateAyahNote(note: AyahNoteEntity)

    /**
     * Delete an ayah note
     */
    @Delete
    suspend fun deleteAyahNote(note: AyahNoteEntity)

    /**
     * Get all notes for a specific ayah
     */
    @Query("SELECT * FROM ayah_notes WHERE surah_number = :surahNumber AND ayah_number = :ayahNumber ORDER BY updated_at DESC")
    suspend fun getNotesForAyah(surahNumber: Int, ayahNumber: Int): List<AyahNoteEntity>

    /**
     * Get all notes for a specific surah (reactive)
     */
    @Query("SELECT * FROM ayah_notes WHERE surah_number = :surahNumber ORDER BY ayah_number ASC, updated_at DESC")
    fun getNotesForSurah(surahNumber: Int): Flow<List<AyahNoteEntity>>

    /**
     * Get all ayah notes ordered by most recently updated
     */
    @Query("SELECT * FROM ayah_notes ORDER BY updated_at DESC")
    fun getAllNotes(): Flow<List<AyahNoteEntity>>

    /**
     * Get all ayah notes (one-time read)
     */
    @Query("SELECT * FROM ayah_notes ORDER BY updated_at DESC")
    suspend fun getAllNotesOnce(): List<AyahNoteEntity>

    /**
     * Get distinct ayah numbers that have notes in a surah (reactive)
     */
    @Query("SELECT DISTINCT ayah_number FROM ayah_notes WHERE surah_number = :surahNumber")
    fun getAyahNumbersWithNotes(surahNumber: Int): Flow<List<Int>>

    /**
     * Check if an ayah has any notes
     */
    @Query("SELECT EXISTS(SELECT 1 FROM ayah_notes WHERE surah_number = :surahNumber AND ayah_number = :ayahNumber LIMIT 1)")
    suspend fun hasAyahNote(surahNumber: Int, ayahNumber: Int): Boolean

    /**
     * Delete all notes for a specific ayah
     */
    @Query("DELETE FROM ayah_notes WHERE surah_number = :surahNumber AND ayah_number = :ayahNumber")
    suspend fun deleteAllNotesForAyah(surahNumber: Int, ayahNumber: Int)

    /**
     * Get note count
     */
    @Query("SELECT COUNT(*) FROM ayah_notes")
    suspend fun getNoteCount(): Int
}

