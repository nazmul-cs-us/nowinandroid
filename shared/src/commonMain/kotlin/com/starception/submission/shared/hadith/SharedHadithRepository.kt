/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.hadith

data class SharedHadith(
    val id: Int,
    val arabic: String,
    val english: String,
    val explanation: String = "",
)

interface SharedHadithRepository {
    suspend fun getHadith(id: Int): SharedHadith?
    suspend fun getHadiths(firstId: Int, lastId: Int): List<SharedHadith>

    /**
     * Shama'il At-Tirmidhi. Same [SharedHadith] shape, read from the
     * shamayele_tirmidhi_complete database; the English field prefers the
     * curated hadith_details translation when present.
     */
    suspend fun getShamayelHadith(id: Int): SharedHadith? = getShamayelHadiths(id, id).firstOrNull()
    suspend fun getShamayelHadiths(firstId: Int, lastId: Int): List<SharedHadith>
}

expect fun createSharedHadithRepository(): SharedHadithRepository
