package com.starception.submission.prayer.model

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
        android.util.Log.i("PrayerSettings", "🔍 getEffectiveIshaAngle() called")
        android.util.Log.i("PrayerSettings", "   customIshaAngle = $customIshaAngle")
        android.util.Log.i("PrayerSettings", "   calculationMethod.ishaAngle = ${calculationMethod.ishaAngle}")

        val angle = customIshaAngle ?: calculationMethod.ishaAngle
        android.util.Log.i("PrayerSettings", "   Selected angle (before 0.0 check) = $angle")

        val result = if (angle == 0.0) null else angle
        android.util.Log.i("PrayerSettings", "   Final result (after 0.0 check) = $result")

        return result  // 0.0 means "use delay instead"
    }
    
    /**
     * EFFECTIVE ISHA DELAY: Gets the actual Isha delay to use in calculations
     */
    fun getEffectiveIshaDelay(): Int? {
        return customIshaDelay ?: calculationMethod.ishaDelay
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
    val vibrationEnabled: Boolean = true        // Vibration for notifications
)

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
        android.util.Log.i("PrayerSettings_Legacy", "🔍 getEffectiveIshaAngle() called (LEGACY CLASS)")
        android.util.Log.i("PrayerSettings_Legacy", "   customIshaAngle = $customIshaAngle")
        android.util.Log.i("PrayerSettings_Legacy", "   calculationMethod.ishaAngle = ${calculationMethod.ishaAngle}")

        val angle = customIshaAngle ?: calculationMethod.ishaAngle
        android.util.Log.i("PrayerSettings_Legacy", "   Selected angle (before 0.0 check) = $angle")

        val result = if (angle == 0.0) null else angle
        android.util.Log.i("PrayerSettings_Legacy", "   Final result (after 0.0 check) = $result")

        return result  // 0.0 means "use delay instead"
    }

    fun getEffectiveIshaDelay(): Int? {
        return customIshaDelay ?: calculationMethod.ishaDelay
    }

    /**
     * Convert to separate preference classes
     */
    fun toSeparatePreferences(): Triple<PrayerCalculationSettings, PrayerLocationPreferences, PrayerNotificationPreferences> {
        return Triple(
            PrayerCalculationSettings(
                calculationMethod = calculationMethod,
                asrMadhhab = asrMadhhab,
                highLatitudeAdjustment = highLatitudeAdjustment,
                customFajrAngle = customFajrAngle,
                customIshaAngle = customIshaAngle,
                customIshaDelay = customIshaDelay,
                timeOffsets = timeOffsets
            ),
            PrayerLocationPreferences(
                location = location,
                useGpsLocation = useGpsLocation
            ),
            PrayerNotificationPreferences(
                notificationsEnabled = notificationsEnabled,
                notificationSound = notificationSound,
                vibrationEnabled = vibrationEnabled
            )
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
    val backupTimestamp: Long = System.currentTimeMillis()
)