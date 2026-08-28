package com.starception.submission.core.hadithdatabase

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Hadith databases
 * Used to query hadith collections (Bukhari, Muslim, Tirmidhi, etc.)
 */
@Dao
interface HadithDao {

    /**
     * Get hadith by ID
     */
    @Query("SELECT * FROM hadiths WHERE id = :hadithId")
    suspend fun getHadithById(hadithId: Int): HadithEntity?

    /**
     * Get all hadiths
     */
    @Query("SELECT * FROM hadiths ORDER BY id ASC")
    suspend fun getAllHadiths(): List<HadithEntity>

    /**
     * Get all hadiths as Flow
     */
    @Query("SELECT * FROM hadiths ORDER BY id ASC")
    fun getAllHadithsFlow(): Flow<List<HadithEntity>>

    /**
     * Get total hadith count
     */
    @Query("SELECT COUNT(*) FROM hadiths")
    suspend fun getHadithCount(): Int

    /**
     * Search hadiths by text
     */
    @Query("""
        SELECT * FROM hadiths
        WHERE text_plain LIKE '%' || :query || '%'
        ORDER BY id ASC
        LIMIT :limit
    """)
    suspend fun searchHadiths(query: String, limit: Int = 50): List<HadithEntity>

    /**
     * Search hadiths by Arabic text
     */
    @Query("""
        SELECT * FROM hadiths
        WHERE text_arabic LIKE '%' || :query || '%'
        ORDER BY id ASC
        LIMIT :limit
    """)
    suspend fun searchHadithsArabic(query: String, limit: Int = 50): List<HadithEntity>

    /** Multi-token AND search used by the app-wide search surface. */
    @Query("""
        SELECT * FROM hadiths
        WHERE (:t0 = '' OR text_plain LIKE '%' || :t0 || '%' OR text_arabic LIKE '%' || :t0 || '%')
          AND (:t1 = '' OR text_plain LIKE '%' || :t1 || '%' OR text_arabic LIKE '%' || :t1 || '%')
          AND (:t2 = '' OR text_plain LIKE '%' || :t2 || '%' OR text_arabic LIKE '%' || :t2 || '%')
        ORDER BY id ASC
        LIMIT :limit
    """)
    suspend fun searchHadithsMultiToken(
        t0: String,
        t1: String = "",
        t2: String = "",
        limit: Int = 20,
    ): List<HadithEntity>

    /**
     * Get hadiths in range
     */
    @Query("SELECT * FROM hadiths WHERE id BETWEEN :startId AND :endId ORDER BY id ASC")
    suspend fun getHadithsInRange(startId: Int, endId: Int): List<HadithEntity>
}
