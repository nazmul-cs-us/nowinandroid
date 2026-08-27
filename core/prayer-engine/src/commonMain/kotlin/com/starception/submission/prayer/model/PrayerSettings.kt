package com.starception.submission.prayer.model

import com.starception.submission.core.logging.SharedLog
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

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
    val calculationMethod: CalculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,  // Primary calculation standard
    val asrMadhhab: AsrMadhhab = AsrMadhhab.STANDARD,                                   // Asr shadow calculation method
    val highLatitudeAdjustment: HighLatitudeAdjustment = HighLatitudeAdjustment.NONE,   // For polar regions
    
    // CUSTOM ANGLE OVERRIDES - Advanced user customizations
    val customFajrAngle: Double? = null,        // Override Fajr sun angle (degrees below horizon)
    val customIshaAngle: Double? = null,        // Override Isha sun angle (degrees below horizon)
    val customIshaDelay: Int? = null,           // Override Isha delay (minutes after Maghrib)
    val customMaghribOffset: Int? = null,       // Override Maghrib offset (minutes after sunset)

    // TIME ADJUSTMENTS - Local custom offsets
    val timeOffsets: PrayerTimeOffsets = PrayerTimeOffsets()  // Per-prayer minute adjustments
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

        return result  // 0.0 means "use delay instead"
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
    val location: Location? = null,             // User's saved location (overrides GPS)
    val useGpsLocation: Boolean = true          // Whether to use GPS when no saved location
)

/**
 * NOTIFICATION PREFERENCES: How to alert user for prayer times
 *
 * This stores notification-specific settings for prayer alerts.
 */
@Serializable
@JsonIgnoreUnknownKeys
data class PrayerNotificationPreferences(
    val notificationsEnabled: Boolean = true,   // Master notification toggle
    val notificationSound: String = "default",  // Notification sound selection
    val vibrationEnabled: Boolean = true,       // Vibration for notifications

    // PER-PRAYER NOTIFICATION TOGGLES - Individual control for each prayer
    val fajrNotificationEnabled: Boolean = true,    // Fajr notification toggle
    val dhuhrNotificationEnabled: Boolean = true,   // Dhuhr notification toggle
    val asrNotificationEnabled: Boolean = true,     // Asr notification toggle
    val maghribNotificationEnabled: Boolean = true, // Maghrib notification toggle
    val ishaNotificationEnabled: Boolean = true,    // Isha notification toggle

    // PER-PRAYER PRIOR NOTIFICATION TIME (minutes before prayer to send reminder)
    val fajrPriorMinutes: Int = 10,     // Minutes before Fajr to send reminder
    val dhuhrPriorMinutes: Int = 10,    // Minutes before Dhuhr to send reminder
    val asrPriorMinutes: Int = 10,      // Minutes before Asr to send reminder
    val maghribPriorMinutes: Int = 10,  // Minutes before Maghrib to send reminder
    val ishaPriorMinutes: Int = 10,     // Minutes before Isha to send reminder

    // PER-PRAYER "GO TO MOSQUE" PHASE DURATION (minutes after prayer starts)
    val fajrGoToMosqueDuration: Int = 20,     // Fajr go-to-mosque phase duration
    val dhuhrGoToMosqueDuration: Int = 20,    // Dhuhr go-to-mosque phase duration
    val asrGoToMosqueDuration: Int = 20,      // Asr go-to-mosque phase duration
    val maghribGoToMosqueDuration: Int = 10,  // Maghrib go-to-mosque phase duration (shorter due to short window)
    val ishaGoToMosqueDuration: Int = 20,     // Isha go-to-mosque phase duration

    // SILENT-DURING-PRAYER: auto-enable DND when prayer time arrives, restore after N minutes.
    // Defaults ON — fresh installs silence during prayer once the user grants DND access.
    val silentDuringPrayerEnabled: Boolean = true,
    val silentDuringPrayerMinutes: Int = 20
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
    val originalAutoDetectedSettingsJson: String? = null
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

        return result  // 0.0 means "use delay instead"
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
            timeOffsets = timeOffsets
        )
    }

    /**
     * Convert to location preferences only
     */
    fun toLocationPreferences(): PrayerLocationPreferences {
        return PrayerLocationPreferences(
            location = location,
            useGpsLocation = useGpsLocation
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
    val fajr: Int = 0,      // Fajr (Dawn) offset in minutes
    val sunrise: Int = 0,   // Sunrise offset in minutes
    val dhuhr: Int = 0,     // Dhuhr (Noon) offset in minutes
    val asr: Int = 0,       // Asr (Afternoon) offset in minutes
    val maghrib: Int = 0,   // Maghrib (Sunset) offset in minutes
    val isha: Int = 0       // Isha (Night) offset in minutes
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
            else -> 0  // No offset for unrecognized prayer names
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
    val backupTimestamp: Long = Clock.System.now().toEpochMilliseconds()
)