package com.starception.submission.usersettings

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per country = that country's tuned prayer calculation settings.
 *
 * Mirrors [com.starception.submission.prayer.model.PrayerCalculationSettings] plus the six
 * [com.starception.submission.prayer.model.PrayerTimeOffsets] fields, flattened into columns so
 * the whole store is a plain SQLite file that can be uploaded to / restored from Cloudflare as-is.
 * Enum fields are stored as their `.name`.
 */
@Entity(tableName = "prayer_country_settings")
data class PrayerCountrySettingsEntity(
    @PrimaryKey val countryCode: String,
    val calculationMethod: String,
    val asrMadhhab: String,
    val highLatitudeAdjustment: String,
    val customFajrAngle: Double?,
    val customIshaAngle: Double?,
    val customIshaDelay: Int?,
    val customMaghribOffset: Int?,
    val offsetFajr: Int,
    val offsetSunrise: Int,
    val offsetDhuhr: Int,
    val offsetAsr: Int,
    val offsetMaghrib: Int,
    val offsetIsha: Int,
    val autoDetectedCountryName: String?,
    val updatedAt: Long,
)
