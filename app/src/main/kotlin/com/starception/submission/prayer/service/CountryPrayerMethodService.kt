package com.starception.submission.prayer.service

import android.content.Context
import android.location.Geocoder
import android.location.Location as AndroidLocation
import com.starception.submission.prayer.model.CalculationMethod
import com.starception.submission.prayer.model.AsrMadhhab
import com.starception.submission.prayer.model.Location
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Country-Based Prayer Method Service
 * 
 * Provides automatic prayer calculation method and madhhab selection based on user's location.
 * Uses comprehensive database of 80+ countries with region-specific Islamic calculation preferences.
 * 
 * ## Key Features:
 * - **Automatic Detection**: Determines calculation method from GPS coordinates
 * - **Country Database**: 80+ countries with appropriate calculation methods and madhhab
 * - **Regional Accuracy**: Uses location-specific Islamic traditions and scholarly preferences
 * - **Fallback System**: Defaults to widely-accepted methods when country is unidentified
 * - **Offline Support**: JSON data loaded from app assets, no network required
 * 
 * ## Usage Examples:
 * - UAE Location → Umm al-Qura University method + Maliki madhhab
 * - Pakistan Location → University of Karachi method + Hanafi madhhab
 * - Egypt Location → Egyptian General Authority + Shafi madhhab
 * - USA Location → ISNA method + Hanafi madhhab
 * 
 * ## Data Source:
 * - `country_prayer_methods.json` - Contains country-specific preferences
 * - Based on regional Islamic scholarly traditions and government recommendations
 * - Includes calculation method parameters and madhhab shadow ratios
 */
@Singleton
class CountryPrayerMethodService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var countryData: CountryPrayerData? = null
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get prayer calculation method and madhhab based on location
     */
    suspend fun getPrayerMethodForLocation(
        location: AndroidLocation
    ): LocationBasedPrayerSettings = withContext(Dispatchers.IO) {
        android.util.Log.d("CountryPrayerMethodService", "Getting prayer method for location: ${location.latitude}, ${location.longitude}")
        try {
            // Load country data if not already loaded
            if (countryData == null) {
                android.util.Log.d("CountryPrayerMethodService", "Loading country data...")
                loadCountryData()
            }

            // Get country code from coordinates
            android.util.Log.d("CountryPrayerMethodService", "Getting country code from location...")
            val countryCode = getCountryCodeFromLocation(location)
            android.util.Log.d("CountryPrayerMethodService", "Detected country code: $countryCode")
            
            // Get country-specific settings
            val countrySettings = if (countryCode != null) {
                val settings = countryData?.countries?.get(countryCode)
                android.util.Log.d("CountryPrayerMethodService", "Country settings for $countryCode: ${settings?.name}")
                settings
            } else {
                android.util.Log.d("CountryPrayerMethodService", "No country code detected, using regional default")
                null
            }
            
            if (countrySettings != null) {
                // Get calculation method details from JSON
                val calculationMethodDetails = countryData?.calculationMethods?.get(countrySettings.calculationMethod)
                
                val result = LocationBasedPrayerSettings(
                    calculationMethod = mapCalculationMethod(countrySettings.calculationMethod),
                    madhhab = mapMadhhab(countrySettings.madhhab),
                    countryName = countrySettings.name,
                    countryCode = countryCode ?: "UNKNOWN",
                    isAutoDetected = true,
                    // Extract custom angle parameters from JSON
                    customFajrAngle = calculationMethodDetails?.fajrAngle,
                    customIshaAngle = calculationMethodDetails?.let { method ->
                        // Only use ishaAngle if it's not 0 (some methods use ishaOffset instead)
                        if (method.ishaAngle != 0.0) method.ishaAngle else null
                    },
                    customMaghribOffset = calculationMethodDetails?.let { method ->
                        if (method.maghribOffset != 0.0) method.maghribOffset.toInt() else null
                    },
                    customIshaOffset = calculationMethodDetails?.let { method ->
                        if (method.ishaOffset != 0.0) method.ishaOffset.toInt() else null
                    }
                )
                android.util.Log.d("CountryPrayerMethodService", 
                    "Returning auto-detected settings: ${result.countryName} - ${result.calculationMethod.displayName}" +
                    " (Fajr: ${result.customFajrAngle}°, Isha: ${result.customIshaAngle ?: "offset ${result.customIshaOffset}min"})")
                result
            } else {
                // Fallback to regional defaults based on coordinates
                getRegionalDefault(location)
            }
        } catch (e: Exception) {
            // Ultimate fallback
            LocationBasedPrayerSettings(
                calculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
                madhhab = AsrMadhhab.STANDARD,
                countryName = "Unknown",
                countryCode = "UNKNOWN",
                isAutoDetected = false
            )
        }
    }

    /**
     * Load country prayer methods from JSON assets
     */
    private suspend fun loadCountryData() = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open("country_prayer_methods.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            countryData = json.decodeFromString<CountryPrayerData>(jsonString)
        } catch (e: Exception) {
            // Log error but continue with fallback system
            android.util.Log.e("CountryPrayerMethodService", "Failed to load country data", e)
        }
    }

    /**
     * Get country code from GPS coordinates using reverse geocoding
     */
    private suspend fun getCountryCodeFromLocation(location: AndroidLocation): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) {
                return@withContext null
            }

            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            
            return@withContext addresses?.firstOrNull()?.countryCode
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Get regional default when specific country is not found
     */
    private fun getRegionalDefault(location: AndroidLocation): LocationBasedPrayerSettings {
        val lat = location.latitude
        val lng = location.longitude

        // Regional fallback based on geographical zones
        return when {
            // Middle East & Gulf
            lat >= 12.0 && lat <= 42.0 && lng >= 34.0 && lng <= 60.0 -> {
                LocationBasedPrayerSettings(
                    calculationMethod = CalculationMethod.UMM_AL_QURA,
                    madhhab = AsrMadhhab.STANDARD,
                    countryName = "Middle East Region",
                    countryCode = "ME_REGION",
                    isAutoDetected = false
                )
            }
            // South Asia
            lat >= 5.0 && lat <= 37.0 && lng >= 60.0 && lng <= 97.0 -> {
                LocationBasedPrayerSettings(
                    calculationMethod = CalculationMethod.UNIVERSITY_OF_ISLAMIC_SCIENCES,
                    madhhab = AsrMadhhab.HANAFI,
                    countryName = "South Asia Region",
                    countryCode = "SA_REGION",
                    isAutoDetected = false
                )
            }
            // Southeast Asia
            lat >= -11.0 && lat <= 28.0 && lng >= 92.0 && lng <= 141.0 -> {
                LocationBasedPrayerSettings(
                    calculationMethod = CalculationMethod.MUIS,
                    madhhab = AsrMadhhab.STANDARD,
                    countryName = "Southeast Asia Region",
                    countryCode = "SEA_REGION",
                    isAutoDetected = false
                )
            }
            // North America
            lat >= 15.0 && lat <= 72.0 && lng >= -168.0 && lng <= -52.0 -> {
                LocationBasedPrayerSettings(
                    calculationMethod = CalculationMethod.ISNA,
                    madhhab = AsrMadhhab.HANAFI,
                    countryName = "North America Region",
                    countryCode = "NA_REGION",
                    isAutoDetected = false
                )
            }
            // Africa & North Africa
            lat >= -35.0 && lat <= 37.0 && lng >= -18.0 && lng <= 52.0 -> {
                LocationBasedPrayerSettings(
                    calculationMethod = CalculationMethod.EGYPTIAN_AUTHORITY,
                    madhhab = AsrMadhhab.STANDARD,
                    countryName = "Africa Region",
                    countryCode = "AF_REGION",
                    isAutoDetected = false
                )
            }
            // Europe & rest of world
            else -> {
                LocationBasedPrayerSettings(
                    calculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
                    madhhab = AsrMadhhab.HANAFI,
                    countryName = "Global Region",
                    countryCode = "GLOBAL",
                    isAutoDetected = false
                )
            }
        }
    }

    /**
     * Map JSON calculation method string to enum
     */
    private fun mapCalculationMethod(methodName: String): CalculationMethod {
        return when (methodName) {
            "Umm_al_Qura_University_Makkah" -> CalculationMethod.UMM_AL_QURA
            "Egyptian_General_Authority_of_Survey" -> CalculationMethod.EGYPTIAN_AUTHORITY
            "University_of_Karachi" -> CalculationMethod.UNIVERSITY_OF_ISLAMIC_SCIENCES
            "Institute_of_Geophysics_University_of_Tehran" -> CalculationMethod.INSTITUTE_OF_GEOPHYSICS_TEHRAN
            "Islamic_Society_of_North_America" -> CalculationMethod.ISNA
            "Department_of_Islamic_Advancement_Malaysia", "Singapore", "Brunei" -> CalculationMethod.MUIS
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        }
    }

    /**
     * Map JSON madhhab string to enum
     */
    private fun mapMadhhab(madhhabName: String): AsrMadhhab {
        return when (madhhabName) {
            "Hanafi" -> AsrMadhhab.HANAFI
            "Shafi", "Maliki", "Hanbali", "Jafari", "Ibadi" -> AsrMadhhab.STANDARD
            else -> AsrMadhhab.STANDARD
        }
    }
}

/**
 * Location-based prayer settings result
 */
data class LocationBasedPrayerSettings(
    val calculationMethod: CalculationMethod,
    val madhhab: AsrMadhhab,
    val countryName: String,
    val countryCode: String,
    val isAutoDetected: Boolean,
    // Country-specific calculation parameters from JSON
    val customFajrAngle: Double? = null,
    val customIshaAngle: Double? = null,
    val customMaghribOffset: Int? = null,
    val customIshaOffset: Int? = null
)

/**
 * JSON data structures for country prayer methods
 */
@Serializable
data class CountryPrayerData(
    val countries: Map<String, CountryPrayerInfo>,
    val calculationMethods: Map<String, CalculationMethodInfo>,
    val madhhabOptions: Map<String, MadhhabInfo>
)

@Serializable
data class CountryPrayerInfo(
    val name: String,
    val calculationMethod: String,
    val madhhab: String,
    val coordinates: Coordinates
)

@Serializable
data class Coordinates(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class CalculationMethodInfo(
    val name: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val maghribOffset: Double,
    val ishaOffset: Double,
    val region: String
)

@Serializable
data class MadhhabInfo(
    val name: String,
    val asrShadowRatio: Int,
    val regions: List<String>
)