package com.starception.submission.prayer.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * PRAYER SETTINGS MODEL: User preferences for personalized prayer time calculations
 * 
 * This stores all user customizations for prayer time calculations and notifications.
 * 
 * CALCULATION SETTINGS:
 * - Method selection (Muslim World League, ISNA, etc.)
 * - Madhab for Asr calculation (Standard vs Hanafi)
 * - High latitude adjustments for polar regions
 * - Custom angle overrides for advanced users
 * 
 * LOCATION SETTINGS:
 * - Saved user location (overrides GPS)
 * - GPS preference toggle
 * 
 * NOTIFICATION SETTINGS:
 * - Enable/disable notifications
 * - Sound and vibration preferences
 * 
 * TIME ADJUSTMENTS:
 * - Per-prayer minute offsets for local customs
 * 
 * EDIT THIS TO:
 * - Add new calculation methods
 * - Include additional notification options
 * - Add prayer name customizations
 * - Include reminder settings
 */
@Serializable
@JsonIgnoreUnknownKeys
data class PrayerSettings(
    // CALCULATION METHOD SETTINGS - Core astronomical parameters
    val calculationMethod: CalculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,  // Primary calculation standard
    val asrMadhhab: AsrMadhhab = AsrMadhhab.STANDARD,                                   // Asr shadow calculation method
    val highLatitudeAdjustment: HighLatitudeAdjustment = HighLatitudeAdjustment.NONE,   // For polar regions
    
    // AUTO-DETECTION INFO - Shows if settings were automatically configured
    val isMethodAutoDetected: Boolean = false,       // Whether calculation method was auto-detected from location
    val isMadhhabAutoDetected: Boolean = false,      // Whether madhhab was auto-detected from location  
    val autoDetectedCountryName: String? = null,     // Name of the auto-detected country
    val autoDetectedCountryCode: String? = null,     // Code of the auto-detected country
    val areCustomAnglesAutoDetected: Boolean = false, // Whether custom angles were auto-detected from JSON
    val originalAutoDetectedSettingsJson: String? = null, // JSON backup of original auto-detected settings for restore
    
    // CUSTOM ANGLE OVERRIDES - Advanced user customizations
    val customFajrAngle: Double? = null,        // Override Fajr sun angle (degrees below horizon)
    val customIshaAngle: Double? = null,        // Override Isha sun angle (degrees below horizon)
    val customIshaDelay: Int? = null,           // Override Isha delay (minutes after Maghrib)
    
    // TIME ADJUSTMENTS - Local custom offsets
    val timeOffsets: PrayerTimeOffsets = PrayerTimeOffsets(),  // Per-prayer minute adjustments
    
    // LOCATION PREFERENCES - Where to calculate prayer times for
    val location: Location? = null,             // User's saved location (overrides GPS)
    val useGpsLocation: Boolean = true,         // Whether to use GPS when no saved location
    
    // NOTIFICATION PREFERENCES - How to alert user
    val notificationsEnabled: Boolean = true,   // Master notification toggle
    val notificationSound: String = "default",  // Notification sound selection
    val vibrationEnabled: Boolean = true        // Vibration for notifications
) {
    /**
     * EFFECTIVE FAJR ANGLE: Gets the actual Fajr angle to use in calculations
     * 
     * This chooses between user's custom angle or the calculation method's default.
     * 
     * LOGIC:
     * - Uses custom angle if user set one
     * - Falls back to calculation method's standard angle
     * 
     * EDIT THIS TO:
     * - Add angle validation
     * - Include seasonal adjustments
     * - Add location-based defaults
     */
    fun getEffectiveFajrAngle(): Double {
        return customFajrAngle ?: calculationMethod.fajrAngle  // Custom overrides method default
    }
    
    /**
     * EFFECTIVE ISHA ANGLE: Gets the actual Isha angle to use in calculations
     * 
     * This chooses between user's custom angle or the calculation method's default.
     * 
     * NOTE: Returns null if both custom and method angles are null (uses delay instead)
     * 
     * EDIT THIS TO:
     * - Add angle validation
     * - Handle conflicting angle/delay settings
     * - Add seasonal adjustments
     */
    fun getEffectiveIshaAngle(): Double? {
        return customIshaAngle ?: calculationMethod.ishaAngle  // Custom overrides method default
    }
    
    /**
     * EFFECTIVE ISHA DELAY: Gets the actual Isha delay to use in calculations
     * 
     * This chooses between user's custom delay or the calculation method's default.
     * 
     * USAGE:
     * - When Isha angle calculation isn't suitable
     * - For regions where fixed delay after Maghrib is preferred
     * 
     * EDIT THIS TO:
     * - Add delay validation (reasonable ranges)
     * - Include seasonal delay adjustments
     * - Handle angle vs delay conflicts
     */
    fun getEffectiveIshaDelay(): Int? {
        return customIshaDelay ?: calculationMethod.ishaDelay  // Custom overrides method default
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
 * Stores the original auto-detected settings for restore functionality.
 * This is serialized to JSON and stored in PrayerSettings.originalAutoDetectedSettingsJson
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