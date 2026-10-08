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
    @Query(
        """
        SELECT * FROM hadiths
        WHERE text_plain LIKE '%' || :query || '%'
        ORDER BY id ASC
        LIMIT :limit
    """,
    )
    suspend fun searchHadiths(query: String, limit: Int = 50): List<HadithEntity>

    /**
     * Search hadiths by Arabic text
     */
    @Query(
        """
        SELECT * FROM hadiths
        WHERE text_arabic LIKE '%' || :query || '%'
        ORDER BY id ASC
        LIMIT :limit
    """,
    )
    suspend fun searchHadithsArabic(query: String, limit: Int = 50): List<HadithEntity>

    /** Multi-token AND search used by the app-wide search surface. */
    @Query(
        """
        SELECT * FROM hadiths
        WHERE (:t0 = '' OR text_plain LIKE '%' || :t0 || '%' OR text_arabic LIKE '%' || :t0 || '%')
          AND (:t1 = '' OR text_plain LIKE '%' || :t1 || '%' OR text_arabic LIKE '%' || :t1 || '%')
          AND (:t2 = '' OR text_plain LIKE '%' || :t2 || '%' OR text_arabic LIKE '%' || :t2 || '%')
        ORDER BY id ASC
        LIMIT :limit
    """,
    )
    suspend fun searchHadithsMultiToken(
        t0: String,
        t1: String = "",
        t2: String = "",
        limit: Int = 20,
    ): List<HadithEntity>

    /**
     * Multi-token AND search where every token slot accepts up to two synonym
     * alternatives. Patterns arrive pre-wrapped with SQL wildcards
     * ('%prophet%'), so one query covers "prophet's favorite food" together
     * with the corpus phrasings (loved, liked) without re-scanning the
     * collection per variant.
     */
    @Query(
        """
        SELECT * FROM hadiths
        WHERE (:p0 = '' OR text_plain LIKE :p0 OR text_arabic LIKE :p0
            OR (:a0 <> '' AND (text_plain LIKE :a0 OR text_arabic LIKE :a0))
            OR (:b0 <> '' AND (text_plain LIKE :b0 OR text_arabic LIKE :b0)))
          AND (:p1 = '' OR text_plain LIKE :p1 OR text_arabic LIKE :p1
            OR (:a1 <> '' AND (text_plain LIKE :a1 OR text_arabic LIKE :a1))
            OR (:b1 <> '' AND (text_plain LIKE :b1 OR text_arabic LIKE :b1)))
          AND (:p2 = '' OR text_plain LIKE :p2 OR text_arabic LIKE :p2
            OR (:a2 <> '' AND (text_plain LIKE :a2 OR text_arabic LIKE :a2))
            OR (:b2 <> '' AND (text_plain LIKE :b2 OR text_arabic LIKE :b2)))
        ORDER BY id ASC
        LIMIT :limit
    """,
    )
    suspend fun searchHadithsMultiTokenAny(
        p0: String,
        a0: String = "",
        b0: String = "",
        p1: String = "",
        a1: String = "",
        b1: String = "",
        p2: String = "",
        a2: String = "",
        b2: String = "",
        limit: Int = 20,
    ): List<HadithEntity>

    /**
     * Get hadiths in range
     */
    @Query("SELECT * FROM hadiths WHERE id BETWEEN :startId AND :endId ORDER BY id ASC")
    suspend fun getHadithsInRange(startId: Int, endId: Int): List<HadithEntity>
}
