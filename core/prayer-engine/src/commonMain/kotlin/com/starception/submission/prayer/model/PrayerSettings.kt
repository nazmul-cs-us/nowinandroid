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

package com.starception.submission.prayer.model

import com.starception.submission.core.logging.SharedLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlin.time.Clock

/**
 * CALCULATION METHOD SETTINGS: Core astronomical parameters for prayer time calculations
 *
 * This stores calculation-specific settings that determine how prayer times are computed.
 *
 * INCLUDED SETTINGS:
 * - Method selection (Muslim World League, ISNA, etc.)
 * - Madhab for Asr calculation (Standard vs Hanafi)
 * - High latitude adjustments for polar regions
 * - Custom angle overrides for advanced users
 * - Per-prayer minute offsets for local customs
 */
@Serializable
@JsonIgnoreUnknownKeys
data class PrayerCalculationSettings(
    // Primary calculation standard
    val calculationMethod: CalculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
    // Asr shadow calculation method
    val asrMadhhab: AsrMadhhab = AsrMadhhab.STANDARD,
    // For polar regions
    val highLatitudeAdjustment: HighLatitudeAdjustment = HighLatitudeAdjustment.NONE,

    // CUSTOM ANGLE OVERRIDES - Advanced user customizations
    // Override Fajr sun angle (degrees below horizon)
    val customFajrAngle: Double? = null,
    // Override Isha sun angle (degrees below horizon)
    val customIshaAngle: Double? = null,
    // Override Isha delay (minutes after Maghrib)
    val customIshaDelay: Int? = null,
    // Override Maghrib offset (minutes after sunset)
    val customMaghribOffset: Int? = null,

    // TIME ADJUSTMENTS - Local custom offsets
    // Per-prayer minute adjustments
    val timeOffsets: PrayerTimeOffsets = PrayerTimeOffsets(),
) {
    /**
     * EFFECTIVE FAJR ANGLE: Gets the actual Fajr angle to use in calculations
     */
    fun getEffectiveFajrAngle(): Double {
        return customFajrAngle ?: calculationMethod.fajrAngle
    }

    /**
     * EFFECTIVE ISHA ANGLE: Gets the actual Isha angle to use in calculations
     * NOTE: 0.0 is treated as null (meaning use delay-based calculation instead)
     */
    fun getEffectiveIshaAngle(): Double? {
        SharedLog.i("PrayerSettings", "🔍 getEffectiveIshaAngle() called")
        SharedLog.i("PrayerSettings", "   customIshaAngle = $customIshaAngle")
        SharedLog.i("PrayerSettings", "   calculationMethod.ishaAngle = ${calculationMethod.ishaAngle}")

        val angle = customIshaAngle ?: calculationMethod.ishaAngle
        SharedLog.i("PrayerSettings", "   Selected angle (before 0.0 check) = $angle")

        val result = if (angle == 0.0) null else angle
        SharedLog.i("PrayerSettings", "   Final result (after 0.0 check) = $result")

        return result // 0.0 means "use delay instead"
    }

    /**
     * EFFECTIVE ISHA DELAY: Gets the actual Isha delay to use in calculations
     */
    fun getEffectiveIshaDelay(): Int? {
        return customIshaDelay ?: calculationMethod.ishaDelay
    }

    /**
     * EFFECTIVE MAGHRIB OFFSET: Gets the actual Maghrib offset to use in calculations
     */
    fun getEffectiveMaghribOffset(): Int {
        return customMaghribOffset ?: calculationMethod.maghribOffset
    }
}

/**
 * LOCATION PREFERENCES: Where to calculate prayer times for
 *
 * This stores location-specific settings for prayer time calculations.
 */
@Serializable
@JsonIgnoreUnknownKeys
data class PrayerLocationPreferences(
    // User's saved location (overrides GPS)
    val location: Location? = null,
    // Whether to use GPS when no saved location
    val useGpsLocation: Boolean = true,
)

/**
 * NOTIFICATION PREFERENCES: How to alert user for prayer times
 *
 * This stores notification-specific settings for prayer alerts.
 */
@Serializable
@JsonIgnoreUnknownKeys
data class PrayerNotificationPreferences(
    // Master notification toggle
    val notificationsEnabled: Boolean = true,
    // Notification sound selection
    val notificationSound: String = "default",
    // Vibration for notifications
    val vibrationEnabled: Boolean = true,

    // PER-PRAYER NOTIFICATION TOGGLES - Individual control for each prayer
    // Fajr notification toggle
    val fajrNotificationEnabled: Boolean = true,
    // Dhuhr notification toggle
    val dhuhrNotificationEnabled: Boolean = true,
    // Asr notification toggle
    val asrNotificationEnabled: Boolean = true,
    // Maghrib notification toggle
    val maghribNotificationEnabled: Boolean = true,
    // Isha notification toggle
    val ishaNotificationEnabled: Boolean = true,

    // PER-PRAYER ADHAN TOGGLES - Whether the Adhan sound plays when that
    // prayer's notification fires. Defaults keep the previous always-on behavior.
    // Fajr adhan toggle
    val fajrAdhanEnabled: Boolean = true,
    // Dhuhr adhan toggle
    val dhuhrAdhanEnabled: Boolean = true,
    // Asr adhan toggle
    val asrAdhanEnabled: Boolean = true,
    // Maghrib adhan toggle
    val maghribAdhanEnabled: Boolean = true,
    // Isha adhan toggle
    val ishaAdhanEnabled: Boolean = true,

    // ADHAN PLAYBACK VOLUME - 0 (silent) to 100 (full), applied to the adhan
    // player independently of the device's notification volume. This is the
    // master volume; per-prayer values below override it for that prayer.
    val adhanVolume: Int = 100,

    // PER-PRAYER ADHAN VOLUME - overrides [adhanVolume] for that prayer;
    // null means "follow the master volume".
    // Fajr adhan volume override
    val fajrAdhanVolume: Int? = null,
    // Dhuhr adhan volume override
    val dhuhrAdhanVolume: Int? = null,
    // Asr adhan volume override
    val asrAdhanVolume: Int? = null,
    // Maghrib adhan volume override
    val maghribAdhanVolume: Int? = null,
    // Isha adhan volume override
    val ishaAdhanVolume: Int? = null,

    // PER-PRAYER PRIOR NOTIFICATION TIME (minutes before prayer to send reminder)
    // Minutes before Fajr to send reminder
    val fajrPriorMinutes: Int = 10,
    // Minutes before Dhuhr to send reminder
    val dhuhrPriorMinutes: Int = 10,
    // Minutes before Asr to send reminder
    val asrPriorMinutes: Int = 10,
    // Minutes before Maghrib to send reminder
    val maghribPriorMinutes: Int = 10,
    // Minutes before Isha to send reminder
    val ishaPriorMinutes: Int = 10,

    // PER-PRAYER "GO TO MOSQUE" PHASE DURATION (minutes after prayer starts)
    // Fajr go-to-mosque phase duration
    val fajrGoToMosqueDuration: Int = 20,
    // Dhuhr go-to-mosque phase duration
    val dhuhrGoToMosqueDuration: Int = 20,
    // Asr go-to-mosque phase duration
    val asrGoToMosqueDuration: Int = 20,
    // Maghrib go-to-mosque phase duration (shorter due to short window)
    val maghribGoToMosqueDuration: Int = 10,
    // Isha go-to-mosque phase duration
    val ishaGoToMosqueDuration: Int = 20,

    // SILENT-DURING-PRAYER: auto-enable DND when prayer time arrives, restore after N minutes.
    // Defaults ON — fresh installs silence during prayer once the user grants DND access.
    val silentDuringPrayerEnabled: Boolean = true,
    val silentDuringPrayerMinutes: Int = 20,
) {
    /**
     * Check if notifications are enabled for a specific prayer
     * Considers both master toggle and individual prayer toggle
     */
    fun isNotificationEnabledForPrayer(prayerName: String): Boolean {
        if (!notificationsEnabled) return false // Master toggle off = no notifications

        return when (prayerName.lowercase()) {
            "fajr" -> fajrNotificationEnabled
            "dhuhr" -> dhuhrNotificationEnabled
            "asr" -> asrNotificationEnabled
            "maghrib" -> maghribNotificationEnabled
            "isha" -> ishaNotificationEnabled
            else -> false // Unknown prayer = no notification
        }
    }

    /**
     * Check if the Adhan sound is enabled for a specific prayer.
     * The Adhan plays alongside the prayer-time notification; this toggle
     * only controls whether it sounds for that prayer.
     */
    fun isAdhanEnabledForPrayer(prayerName: String): Boolean {
        return when (prayerName.lowercase()) {
            "fajr" -> fajrAdhanEnabled
            "dhuhr" -> dhuhrAdhanEnabled
            "asr" -> asrAdhanEnabled
            "maghrib" -> maghribAdhanEnabled
            "isha" -> ishaAdhanEnabled
            else -> false // Unknown prayer = no Adhan
        }
    }

    /**
     * Effective adhan volume for a specific prayer (0-100): the prayer's
     * individual override when set, otherwise the master [adhanVolume].
     */
    fun getAdhanVolumeForPrayer(prayerName: String): Int {
        val override = when (prayerName.lowercase()) {
            "fajr" -> fajrAdhanVolume
            "dhuhr" -> dhuhrAdhanVolume
            "asr" -> asrAdhanVolume
            "maghrib" -> maghribAdhanVolume
            "isha" -> ishaAdhanVolume
            else -> null
        }
        return (override ?: adhanVolume).coerceIn(0, 100)
    }

    /** Whether [prayerName] has an individual adhan volume override (not following master). */
    fun hasAdhanVolumeOverride(prayerName: String): Boolean {
        return when (prayerName.lowercase()) {
            "fajr" -> fajrAdhanVolume != null
            "dhuhr" -> dhuhrAdhanVolume != null
            "asr" -> asrAdhanVolume != null
            "maghrib" -> maghribAdhanVolume != null
            "isha" -> ishaAdhanVolume != null
            else -> false
        }
    }

    /** Minutes the user wants to allow for travelling to the mosque before the silent window kicks in. */
    fun goToMosqueDurationFor(prayerName: String): Int = when (prayerName.lowercase()) {
        "fajr" -> fajrGoToMosqueDuration
        "dhuhr" -> dhuhrGoToMosqueDuration
        "asr" -> asrGoToMosqueDuration
        "maghrib" -> maghribGoToMosqueDuration
        "isha" -> ishaGoToMosqueDuration
        else -> 0
    }

    /**
     * Get prior notification minutes for a specific prayer
     */
    fun getPriorMinutesForPrayer(prayerName: String): Int {
        return when (prayerName.lowercase()) {
            "fajr" -> fajrPriorMinutes
            "dhuhr" -> dhuhrPriorMinutes
            "asr" -> asrPriorMinutes
            "maghrib" -> maghribPriorMinutes
            "isha" -> ishaPriorMinutes
            else -> 10 // Default 10 minutes
        }
    }

    /**
     * Get "go to mosque" phase duration for a specific prayer
     */
    fun getGoToMosqueDurationForPrayer(prayerName: String): Int {
        return when (prayerName.lowercase()) {
            "fajr" -> fajrGoToMosqueDuration
            "dhuhr" -> dhuhrGoToMosqueDuration
            "asr" -> asrGoToMosqueDuration
            "maghrib" -> maghribGoToMosqueDuration
            "isha" -> ishaGoToMosqueDuration
            else -> 20 // Default 20 minutes for go-to-mosque phase
        }
    }
}

/**
 * LEGACY PRAYER SETTINGS: Composite model for backward compatibility
 *
 * @deprecated This composite model is being phased out in favor of separate preference classes.
 * Use PrayerCalculationSettings, PrayerLocationPreferences, and PrayerNotificationPreferences instead.
 */
@Deprecated("Use separate preference classes instead", ReplaceWith("Use PrayerCalculationSettings, PrayerLocationPreferences, and PrayerNotificationPreferences"))
@Serializable
@JsonIgnoreUnknownKeys
data class PrayerSettings(
    // Calculation settings
    val calculationMethod: CalculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
    val asrMadhhab: AsrMadhhab = AsrMadhhab.STANDARD,
    val highLatitudeAdjustment: HighLatitudeAdjustment = HighLatitudeAdjustment.NONE,
    val customFajrAngle: Double? = null,
    val customIshaAngle: Double? = null,
    val customIshaDelay: Int? = null,
    val customMaghribOffset: Int? = null,
    val timeOffsets: PrayerTimeOffsets = PrayerTimeOffsets(),

    // Location preferences
    val location: Location? = null,
    val useGpsLocation: Boolean = true,

    // Notification preferences
    val notificationsEnabled: Boolean = true,
    val notificationSound: String = "default",
    val vibrationEnabled: Boolean = true,

    // AUTO-DETECTION INFO - Kept for backward compatibility (deprecated)
    @Deprecated("Auto-detection moved to separate system")
    val isMethodAutoDetected: Boolean = false,
    @Deprecated("Auto-detection moved to separate system")
    val isMadhhabAutoDetected: Boolean = false,
    @Deprecated("Auto-detection moved to separate system")
    val autoDetectedCountryName: String? = null,
    @Deprecated("Auto-detection moved to separate system")
    val autoDetectedCountryCode: String? = null,
    @Deprecated("Auto-detection moved to separate system")
    val areCustomAnglesAutoDetected: Boolean = false,
    @Deprecated("Auto-detection moved to separate system")
    val originalAutoDetectedSettingsJson: String? = null,
) {
    fun getEffectiveFajrAngle(): Double {
        return customFajrAngle ?: calculationMethod.fajrAngle
    }

    fun getEffectiveIshaAngle(): Double? {
        SharedLog.i("PrayerSettings_Legacy", "🔍 getEffectiveIshaAngle() called (LEGACY CLASS)")
        SharedLog.i("PrayerSettings_Legacy", "   customIshaAngle = $customIshaAngle")
        SharedLog.i("PrayerSettings_Legacy", "   calculationMethod.ishaAngle = ${calculationMethod.ishaAngle}")

        val angle = customIshaAngle ?: calculationMethod.ishaAngle
        SharedLog.i("PrayerSettings_Legacy", "   Selected angle (before 0.0 check) = $angle")

        val result = if (angle == 0.0) null else angle
        SharedLog.i("PrayerSettings_Legacy", "   Final result (after 0.0 check) = $result")

        return result // 0.0 means "use delay instead"
    }

    fun getEffectiveIshaDelay(): Int? {
        return customIshaDelay ?: calculationMethod.ishaDelay
    }

    fun getEffectiveMaghribOffset(): Int {
        return customMaghribOffset ?: calculationMethod.maghribOffset
    }

    /**
     * Convert to calculation settings only (does NOT touch notification preferences)
     * Use this instead of toSeparatePreferences() to avoid resetting notification settings
     */
    fun toCalculationSettings(): PrayerCalculationSettings {
        return PrayerCalculationSettings(
            calculationMethod = calculationMethod,
            asrMadhhab = asrMadhhab,
            highLatitudeAdjustment = highLatitudeAdjustment,
            customFajrAngle = customFajrAngle,
            customIshaAngle = customIshaAngle,
            customIshaDelay = customIshaDelay,
            customMaghribOffset = customMaghribOffset,
            timeOffsets = timeOffsets,
        )
    }

    /**
     * Convert to location preferences only
     */
    fun toLocationPreferences(): PrayerLocationPreferences {
        return PrayerLocationPreferences(
            location = location,
            useGpsLocation = useGpsLocation,
        )
    }
}

/**
 * PRAYER TIME OFFSETS: Fine-tune prayer times with custom minute adjustments
 *
 * This allows users to adjust calculated prayer times to match local customs,
 * mosque schedules, or personal preferences.
 *
 * COMMON USE CASES:
 * - Match local mosque timetables (+/- few minutes)
 * - Account for local geographic factors
 * - Personal preference adjustments
 * - Community-specific timings
 *
 * OFFSET VALUES:
 * - Positive values = later time (e.g., +5 = 5 minutes after calculated time)
 * - Negative values = earlier time (e.g., -3 = 3 minutes before calculated time)
 * - Zero = no adjustment (use calculated time exactly)
 *
 * EDIT THIS TO:
 * - Add offset validation (reasonable ranges like -30 to +30 minutes)
 * - Include seasonal offset support
 * - Add location-based default offsets
 */
@Serializable
data class PrayerTimeOffsets(
    // Fajr (Dawn) offset in minutes
    val fajr: Int = 0,
    // Sunrise offset in minutes
    val sunrise: Int = 0,
    // Dhuhr (Noon) offset in minutes
    val dhuhr: Int = 0,
    // Asr (Afternoon) offset in minutes
    val asr: Int = 0,
    // Maghrib (Sunset) offset in minutes
    val maghrib: Int = 0,
    // Isha (Night) offset in minutes
    val isha: Int = 0,
) {
    /**
     * OFFSET LOOKUP: Get offset value for any prayer by name
     *
     * This provides a convenient way to get offset values programmatically.
     *
     * EDIT THIS TO:
     * - Add validation for prayer names
     * - Support alternative prayer name spellings
     * - Add error handling for invalid names
     */
    fun getOffset(prayer: String): Int {
        return when (prayer.lowercase()) {
            "fajr" -> fajr
            "sunrise" -> sunrise
            "dhuhr" -> dhuhr
            "asr" -> asr
            "maghrib" -> maghrib
            "isha" -> isha
            else -> 0 // No offset for unrecognized prayer names
        }
    }
}

/**
 * BACKUP DATA FOR RESTORE FUNCTIONALITY
 *
 * Stores the original auto-detected calculation settings for restore functionality.
 * Only stores calculation-related settings as location and notification preferences
 * are not auto-detected.
 */
@Serializable
data class AutoDetectedSettingsBackup(
    val calculationMethod: CalculationMethod,
    val asrMadhhab: AsrMadhhab,
    val customFajrAngle: Double? = null,
    val customIshaAngle: Double? = null,
    val customIshaDelay: Int? = null,
    val timeOffsets: PrayerTimeOffsets,
    val countryName: String,
    val countryCode: String,
    // kotlin.time.Clock rather than System.currentTimeMillis(), which is
    // JVM-only. Same value, available on both platforms.
    val backupTimestamp: Long = Clock.System.now().toEpochMilliseconds(),
)
