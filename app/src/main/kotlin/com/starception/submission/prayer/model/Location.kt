package com.starception.submission.prayer.model

/**
 * LOCATION MODEL: Represents a geographical location for prayer time calculations
 * 
 * This data class stores all location information needed for accurate prayer time calculations.
 * 
 * KEY COMPONENTS:
 * - GPS coordinates (latitude/longitude)
 * - Timezone offset for local time calculations
 * - Human-readable location names
 * - Altitude for more accurate astronomical calculations
 * 
 * USAGE:
 * - Prayer time calculations (core requirement)
 * - Location caching and storage
 * - User location preferences
 * - Fallback location definitions
 * 
 * EDIT THIS TO:
 * - Add new location properties (region, postal code, etc.)
 * - Modify validation rules
 * - Change display name formatting
 */
data class Location(
    // GPS COORDINATES - Core data for astronomical calculations
    val latitude: Double,        // North/South position (-90 to +90 degrees)
    val longitude: Double,       // East/West position (-180 to +180 degrees)
    
    // TIMEZONE INFORMATION - Essential for local prayer times
    val timeZoneOffset: Double,  // Hours from UTC (e.g., +4.0 for UAE, -5.0 for EST)
    
    // HUMAN-READABLE NAMES - For display in UI
    val city: String = "",       // City name (e.g., "Dubai", "New York")
    val country: String = "",    // Country name (e.g., "UAE", "USA")
    
    // OPTIONAL PRECISION DATA - For enhanced accuracy
    val altitude: Double = 0.0   // Meters above sea level (affects sunrise/sunset times)
) {
    /**
     * COORDINATE VALIDATOR: Ensures location coordinates are within valid Earth bounds
     * 
     * This prevents calculation errors from invalid GPS coordinates.
     * 
     * VALIDATION RULES:
     * - Latitude: -90 to +90 degrees (South Pole to North Pole)
     * - Longitude: -180 to +180 degrees (around the Earth)
     * 
     * EDIT THIS TO:
     * - Add altitude validation
     * - Add timezone offset validation
     * - Add coordinate precision checks
     */
    fun isValid(): Boolean {
        return latitude in -90.0..90.0 &&     // Valid latitude range
               longitude in -180.0..180.0      // Valid longitude range
        // TODO: Consider adding timezone validation: timeZoneOffset in -12.0..14.0
    }
    
    /**
     * DISPLAY NAME FORMATTER: Creates user-friendly location names
     * 
     * This provides readable location names for UI display, with smart fallbacks.
     * 
     * DISPLAY PRIORITY:
     * 1. "City, Country" (e.g., "Dubai, UAE") - Best case
     * 2. City only (e.g., "Dubai") - If no country
     * 3. Country only (e.g., "UAE") - If no city
     * 4. Coordinates (e.g., "25.2048, 55.2708") - Final fallback
     * 
     * EDIT THIS TO:
     * - Change display format (add region, postal code)
     * - Modify coordinate precision
     * - Add localization support
     * - Include altitude in display
     */
    fun getDisplayName(): String {
        return when {
            // BEST CASE: Both city and country available
            city.isNotEmpty() && country.isNotEmpty() -> "$city, $country"
            
            // FALLBACK 1: City only
            city.isNotEmpty() -> city
            
            // FALLBACK 2: Country only
            country.isNotEmpty() -> country
            
            // FINAL FALLBACK: Show coordinates (4 decimal places = ~11 meter accuracy)
            else -> "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
        }
    }
}