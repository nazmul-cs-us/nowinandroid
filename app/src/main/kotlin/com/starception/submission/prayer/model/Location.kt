package com.starception.submission.prayer.model

import kotlinx.serialization.Serializable

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
@Serializable
data class Location(
    // GPS COORDINATES - Core data for astronomical calculations
    val latitude: Double,        // North/South position (-90 to +90 degrees)
    val longitude: Double,       // East/West position (-180 to +180 degrees)
    
    // TIMEZONE INFORMATION - Essential for local prayer times
    val timeZoneOffset: Double,  // Hours from UTC (e.g., +4.0 for UAE, -5.0 for EST)
    
    // HUMAN-READABLE NAMES - For display in UI
    val city: String = "",       // City name (e.g., "Dubai", "New York")
    val country: String = "",    // Country name (e.g., "United Arab Emirates", "United States")
    val countryCode: String = "",// ISO 3166-1 alpha-2 country code (e.g., "AE", "US")
    
    // DETAILED AREA INFORMATION - For enhanced location display
    val area: String = "",       // Neighborhood/area/district (e.g., "Downtown Dubai", "Manhattan")
    val subLocality: String = "",// Sub-locality within city (e.g., "Business Bay", "SoHo")
    val thoroughfare: String = "",// Street name or main road (e.g., "Sheikh Zayed Road", "Broadway")
    val administrativeArea: String = "", // State/province/emirate (e.g., "Dubai", "New York")
    
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
     * DISPLAY NAME FORMATTER: Creates user-friendly location names with detailed area information
     *
     * This provides readable location names for UI display, with smart fallbacks and area details.
     *
     * DISPLAY PRIORITY:
     * 1. "Area, City, Country (CC)" (e.g., "Downtown Dubai, Dubai, UAE (AE)") - Best case with area
     * 2. "City, Country (CC)" (e.g., "Dubai, UAE (AE)") - Standard case without area
     * 3. "City, Country" (e.g., "Dubai, UAE") - Without country code
     * 4. City with area fallbacks - Various combinations
     * 5. Coordinates (e.g., "25.2048, 55.2708") - Final fallback
     *
     * EDIT THIS TO:
     * - Change display format (add postal code, more area details)
     * - Modify coordinate precision
     * - Add localization support
     * - Include altitude in display
     */
    fun getDisplayName(): String {
        return when {
            // BEST CASE: Area, city, country, and country code available
            area.isNotEmpty() && city.isNotEmpty() && country.isNotEmpty() && countryCode.isNotEmpty() ->
                "$area, $city, $country ($countryCode)"

            // GOOD CASE: SubLocality instead of area
            area.isEmpty() && subLocality.isNotEmpty() && city.isNotEmpty() && country.isNotEmpty() && countryCode.isNotEmpty() ->
                "$subLocality, $city, $country ($countryCode)"

            // STANDARD CASE: City, country, and country code (no area)
            city.isNotEmpty() && country.isNotEmpty() && countryCode.isNotEmpty() ->
                "$city, $country ($countryCode)"

            // AREA WITH CITY: Area and city without country code
            area.isNotEmpty() && city.isNotEmpty() && country.isNotEmpty() ->
                "$area, $city, $country"

            // SUBLOCALITY WITH CITY: SubLocality and city without country code
            area.isEmpty() && subLocality.isNotEmpty() && city.isNotEmpty() && country.isNotEmpty() ->
                "$subLocality, $city, $country"

            // GOOD CASE: City and country without country code
            city.isNotEmpty() && country.isNotEmpty() -> "$city, $country"

            // AREA ONLY WITH CITY: Just area and city
            area.isNotEmpty() && city.isNotEmpty() -> "$area, $city"

            // SUBLOCALITY ONLY WITH CITY: Just subLocality and city
            area.isEmpty() && subLocality.isNotEmpty() && city.isNotEmpty() -> "$subLocality, $city"

            // FALLBACK 1: City only
            city.isNotEmpty() -> city

            // FALLBACK 2: Country with country code
            country.isNotEmpty() && countryCode.isNotEmpty() -> "$country ($countryCode)"

            // FALLBACK 3: Country only
            country.isNotEmpty() -> country

            // FALLBACK 4: Area only (if somehow we have area but no city)
            area.isNotEmpty() -> area

            // FALLBACK 5: SubLocality only
            subLocality.isNotEmpty() -> subLocality

            // FINAL FALLBACK: Show coordinates (4 decimal places = ~11 meter accuracy)
            else -> "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
        }
    }
}