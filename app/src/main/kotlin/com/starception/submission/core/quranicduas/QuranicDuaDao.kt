/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    @Query(
        """
        SELECT * FROM quranic_duas
        WHERE title LIKE '%' || :query || '%'
           OR arabic LIKE '%' || :query || '%'
           OR translation LIKE '%' || :query || '%'
           OR surah_reference LIKE '%' || :query || '%'
        ORDER BY dua_number ASC
    """,
    )
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
