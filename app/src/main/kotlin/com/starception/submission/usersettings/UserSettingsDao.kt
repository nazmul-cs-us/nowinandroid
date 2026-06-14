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
