package com.starception.dua.prayer.model

/**
 * Represents a geographical location with timezone information
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val timeZoneOffset: Double, // Hours from UTC
    val city: String = "",
    val country: String = "",
    val altitude: Double = 0.0 // Meters above sea level
) {
    /**
     * Validates if the location coordinates are valid
     */
    fun isValid(): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
    
    /**
     * Returns a formatted display string for the location
     */
    fun getDisplayName(): String {
        return when {
            city.isNotEmpty() && country.isNotEmpty() -> "$city, $country"
            city.isNotEmpty() -> city
            country.isNotEmpty() -> country
            else -> "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
        }
    }
}