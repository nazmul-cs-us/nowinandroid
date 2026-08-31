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

package com.starception.submission.shared.settings

import com.starception.submission.prayer.model.AsrMadhhab
import com.starception.submission.prayer.model.CountryPrayerDefaults
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.model.PrayerSettings
import com.starception.submission.prayer.model.PrayerTimeOffsets
import com.starception.submission.shared.storage.KeyValueStore
import com.starception.submission.shared.storage.platformKeyValueStore
import kotlinx.serialization.json.Json

/**
 * The user's prayer settings, persisted.
 *
 * Stores [PrayerSettings] itself rather than a reduced set of fields. That is the
 * model the Android settings screen already edits and the app already persists as
 * JSON, so the two platforms describe a configuration the same way and the shared
 * settings UI can be handed the real type instead of an adapter.
 */
class UserPrayerSettings(private val store: KeyValueStore = platformKeyValueStore()) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * The stored settings, or the country's defaults where nothing is stored.
     *
     * Falling back to the country rather than a fixed default is what lets
     * someone who has never opened settings still get their local method, and
     * keeps working when they travel.
     */
    fun settings(countryCode: String, country: CountryPrayerDefaults?): PrayerSettings {
        val stored = storedSettings(countryCode)
        if (stored.isNullOrBlank()) return defaultsFrom(country)

        // A value that will not parse means a format change or hand-editing.
        // Falling back to the country's defaults keeps prayer times on screen;
        // failing here would leave the user with no times at all.
        return runCatching { json.decodeFromString<PrayerSettings>(stored) }
            .getOrElse { defaultsFrom(country) }
    }

    fun save(countryCode: String, settings: PrayerSettings) {
        store.putString(settingsKey(countryCode), json.encodeToString(settings))
    }

    /**
     * Notification preferences, stored separately from calculation settings.
     *
     * Separate because they change for different reasons: one is about how times
     * are computed, the other about being told when they arrive. Keeping them in
     * one blob would mean a notification toggle rewriting the calculation record.
     */
    fun notifications(): PrayerNotificationPreferences {
        val stored = store.getString(KEY_NOTIFICATIONS)
        if (stored.isNullOrBlank()) return PrayerNotificationPreferences()
        return runCatching { json.decodeFromString<PrayerNotificationPreferences>(stored) }
            .getOrElse { PrayerNotificationPreferences() }
    }

    fun saveNotifications(preferences: PrayerNotificationPreferences) {
        store.putString(KEY_NOTIFICATIONS, json.encodeToString(preferences))
    }

    /** Clears only this country's choices so its defaults apply again. */
    fun restoreDefaults(countryCode: String) {
        store.putString(settingsKey(countryCode), "")
    }

    fun isChanged(countryCode: String, country: CountryPrayerDefaults?): Boolean =
        settings(countryCode, country).toCalculationSettings() !=
            defaultsFrom(country).toCalculationSettings()

    /** Adjusts one prayer by [delta] minutes, clamped to the dial's range. */
    fun adjust(
        countryCode: String,
        country: CountryPrayerDefaults?,
        prayer: String,
        delta: Int,
    ): PrayerSettings {
        val current = settings(countryCode, country)
        val updated = current.copy(
            timeOffsets = current.timeOffsets.withOffset(
                prayer = prayer,
                minutes = (current.timeOffsets.getOffset(prayer) + delta)
                    .coerceIn(-MAX_OFFSET, MAX_OFFSET),
            ),
        )
        save(countryCode, updated)
        return updated
    }

    /** Moves the former global value into the first country opened after upgrade. */
    private fun storedSettings(countryCode: String): String? {
        val key = settingsKey(countryCode)
        store.getString(key)?.takeIf { it.isNotBlank() }?.let { return it }

        val legacy = store.getString(KEY_LEGACY_SETTINGS)?.takeIf { it.isNotBlank() }
            ?: return null
        store.putString(key, legacy)
        store.putString(KEY_LEGACY_SETTINGS, "")
        return legacy
    }

    private fun settingsKey(countryCode: String): String =
        "$KEY_SETTINGS_PREFIX${countryCode.trim().uppercase().ifEmpty { UNKNOWN_COUNTRY }}"

    private fun defaultsFrom(country: CountryPrayerDefaults?) = PrayerSettings(
        calculationMethod = country?.method ?: PrayerSettings().calculationMethod,
        asrMadhhab = if (country?.asrShadowFactor == AsrMadhhab.HANAFI.shadowFactor) {
            AsrMadhhab.HANAFI
        } else {
            AsrMadhhab.STANDARD
        },
    )

    private companion object {
        const val KEY_LEGACY_SETTINGS = "cached_prayer_settings"
        const val KEY_SETTINGS_PREFIX = "cached_prayer_settings_country_"
        const val UNKNOWN_COUNTRY = "UNKNOWN"
        const val KEY_NOTIFICATIONS = "prayer_notification_preferences"

        /**
         * The same range the Android dial allows: three hours either way. Wide
         * enough for any mosque's practice, bounded so a runaway input cannot put
         * a prayer on the wrong day.
         */
        const val MAX_OFFSET = 180
    }
}

/** Returns a copy with [prayer]'s offset replaced. */
fun PrayerTimeOffsets.withOffset(prayer: String, minutes: Int): PrayerTimeOffsets =
    when (prayer.lowercase()) {
        "fajr" -> copy(fajr = minutes)
        "sunrise" -> copy(sunrise = minutes)
        "dhuhr" -> copy(dhuhr = minutes)
        "asr" -> copy(asr = minutes)
        "maghrib" -> copy(maghrib = minutes)
        "isha" -> copy(isha = minutes)
        else -> this
    }

/** Reads as `+5m`, `-3m`, or empty when unadjusted — matching the Android tiles. */
fun formatOffset(minutes: Int): String = when {
    minutes > 0 -> "+${minutes}m"
    minutes < 0 -> "${minutes}m"
    else -> ""
}
