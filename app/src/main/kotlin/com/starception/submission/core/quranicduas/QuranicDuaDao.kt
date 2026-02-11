package com.starception.submission.core.quranicduas

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Quranic Duas database
 */
@Dao
interface QuranicDuaDao {

    /**
     * Get all Quranic Duas ordered by dua number
     */
    @Query("SELECT * FROM quranic_duas ORDER BY dua_number ASC")
    suspend fun getAllQuranicDuas(): List<QuranicDuaEntity>

    /**
     * Get all Quranic Duas as Flow
     */
    @Query("SELECT * FROM quranic_duas ORDER BY dua_number ASC")
    fun getAllQuranicDuasFlow(): Flow<List<QuranicDuaEntity>>

    /**
     * Get Quranic Dua by ID
     */
    @Query("SELECT * FROM quranic_duas WHERE id = :id")
    suspend fun getQuranicDuaById(id: Int): QuranicDuaEntity?

    /**
     * Get Quranic Dua by dua number
     */
    @Query("SELECT * FROM quranic_duas WHERE dua_number = :duaNumber")
    suspend fun getQuranicDuaByNumber(duaNumber: Int): QuranicDuaEntity?

    /**
     * Search Quranic Duas by title, arabic, or translation
     */
    @Query("""
        SELECT * FROM quranic_duas
        WHERE title LIKE '%' || :query || '%'
           OR arabic LIKE '%' || :query || '%'
           OR translation LIKE '%' || :query || '%'
           OR surah_reference LIKE '%' || :query || '%'
        ORDER BY dua_number ASC
    """)
    suspend fun searchQuranicDuas(query: String): List<QuranicDuaEntity>

    /**
     * Get total count of Quranic Duas
     */
    @Query("SELECT COUNT(*) FROM quranic_duas")
    suspend fun getQuranicDuaCount(): Int

    // ============= Write Operations (for refresh from assets) =============

    /**
     * Insert multiple Quranic Duas
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(duas: List<QuranicDuaEntity>)

    /**
     * Delete all Quranic Duas (for refresh from assets)
     */
    @Query("DELETE FROM quranic_duas")
    suspend fun deleteAll()
}
