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

package com.starception.submission.usersettings

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {

    @Upsert
    suspend fun upsertCountry(entity: PrayerCountrySettingsEntity)

    @Query("SELECT * FROM prayer_country_settings WHERE countryCode = :code LIMIT 1")
    suspend fun getCountry(code: String): PrayerCountrySettingsEntity?

    @Query("SELECT * FROM prayer_country_settings ORDER BY countryCode")
    suspend fun getAllCountries(): List<PrayerCountrySettingsEntity>

    @Query("SELECT * FROM prayer_country_settings")
    fun observeAll(): Flow<List<PrayerCountrySettingsEntity>>

    @Query("SELECT value FROM settings_meta WHERE key = :key LIMIT 1")
    suspend fun getMeta(key: String): String?

    @Upsert
    suspend fun putMeta(entity: SettingsMetaEntity)
}
