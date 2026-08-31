package com.starception.submission.prayer.model

import kotlinx.serialization.Serializable

/**
 * Prayer time calculation methods with their specific parameters
 */
@Serializable
enum class CalculationMethod(
    val displayName: String,
    val fajrAngle: Double,
    val ishaAngle: Double? = null,
    val ishaDelay: Int? = null, // Minutes after Maghrib
    val maghribOffset: Int = 0,
    val description: String
) {
    MUSLIM_WORLD_LEAGUE(
        displayName = "Muslim World League (MWL)",
        fajrAngle = 18.0,
        ishaAngle = 17.0,
        description = "Used in Europe, Far East, parts of US"
    ),
    
    UMM_AL_QURA(
        displayName = "Umm al-Qura (Makkah)",
        fajrAngle = 18.5,
        ishaAngle = 0.0,  // Uses time-based calculation (ishaDelay) instead of angle
        ishaDelay = 90, // 120 in Ramadan, 90 in other months
        description = "Used in Saudi Arabia"
    ),

    UAE_IACAD(
        displayName = "UAE IACAD (Dubai)",
        fajrAngle = 18.2,  // Corrected: IACAD uses 18.2° not 19.5°
        ishaAngle = 18.2,  // Corrected: IACAD uses 18.2° angle, not 90-min delay
        ishaDelay = 0,     // No delay - uses angle-based calculation
        description = "Official UAE calculation method (IACAD) - 18.2° for Fajr/Isha with +3min offsets"
    ),

    EGYPTIAN_AUTHORITY(
        displayName = "Egyptian General Authority",
        fajrAngle = 19.5,
        ishaAngle = 17.5,
        description = "Used in Egypt, Syria, Iraq, Lebanon, Malaysia, Parts of US"
    ),
    
    UNIVERSITY_OF_ISLAMIC_SCIENCES(
        displayName = "University of Islamic Sciences (Karachi)",
        fajrAngle = 18.0,
        ishaAngle = 18.0,
        description = "Used in Pakistan, Bangladesh, India, Afghanistan, Parts of Europe"
    ),
    
    ISNA(
        displayName = "Islamic Society of North America (ISNA)",
        fajrAngle = 15.0,
        ishaAngle = 15.0,
        description = "Used in North America (US, Canada, Mexico)"
    ),
    
    MUIS(
        displayName = "Majlis Ugama Islam Singapura (MUIS)",
        fajrAngle = 20.0,
        ishaAngle = 18.0,
        description = "Used in Singapore, Malaysia, Brunei"
    ),
    
    SHIA_ITHNA_ASHARI(
        displayName = "Shia Ithna Ashari (Leva Research Institute, Qum)",
        fajrAngle = 16.0,
        ishaAngle = 14.0,
        maghribOffset = 4, // 4 minutes after sunset
        description = "Used by Shia communities"
    ),
    
    INSTITUTE_OF_GEOPHYSICS_TEHRAN(
        displayName = "Institute of Geophysics, University of Tehran",
        fajrAngle = 17.7,
        ishaAngle = 14.0,
        maghribOffset = 4, // 4 minutes after sunset  
        description = "Used in Iran"
    );
    
    companion object {
        fun getMethodForCountry(countryCode: String): CalculationMethod {
            return when (countryCode.uppercase()) {
                "AE" -> UAE_IACAD
                "SA", "KW", "QA", "BH", "OM" -> UMM_AL_QURA
                "EG", "SY", "IQ", "LB", "JO" -> EGYPTIAN_AUTHORITY
                "PK", "BD", "IN", "AF" -> UNIVERSITY_OF_ISLAMIC_SCIENCES
                "US", "CA", "MX" -> ISNA
                "SG", "MY", "BN" -> MUIS
                "IR" -> INSTITUTE_OF_GEOPHYSICS_TEHRAN
                else -> MUSLIM_WORLD_LEAGUE
            }
        }
    }
}

/**
 * Madhhab rules for Asr prayer calculation
 */
@Serializable
enum class AsrMadhhab(
    val displayName: String,
    val shadowFactor: Int,
    val description: String
) {
    STANDARD(
        displayName = "Standard (Shafi'i, Maliki, Hanbali)",
        shadowFactor = 1,
        description = "Shadow length equals object length"
    ),
    
    HANAFI(
        displayName = "Hanafi",
        shadowFactor = 2,
        description = "Shadow length equals twice the object length"
    )
}

/**
 * High latitude adjustment methods
 */
@Serializable
enum class HighLatitudeAdjustment(
    val displayName: String,
    val description: String
) {
    NONE(
        displayName = "No Adjustment",
        description = "Use calculated times even if sun doesn't reach required angle"
    ),
    
    MIDDLE_OF_NIGHT(
        displayName = "Middle of the Night",
        description = "Fajr/Isha times are halfway between sunset and sunrise"
    ),
    
    ONE_SEVENTH_OF_NIGHT(
        displayName = "One-Seventh of the Night",
        description = "Night divided into 7 parts; Fajr/Isha offset by 1/7"
    ),
    
    ANGLE_BASED(
        displayName = "Angle Based",
        description = "Use method's depression angle regardless of astronomical data"
    ),
    
    NEAREST_LATITUDE(
        displayName = "Nearest Latitude",
        description = "Use twilight times calculated at the nearest standard latitude"
    )
}
