package com.starception.submission.prayer.service

import android.content.Context
import android.location.Geocoder
import android.location.Location as AndroidLocation
import android.util.Log
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
    
    companion object {
        private const val TAG = "CountryPrayerMethodService"
        private const val JSON_FILE = "country_prayer_methods.json"
        
        // Logging levels for different operations
        private fun logInfo(message: String) = Log.i(TAG, message)
        private fun logDebug(message: String) = Log.d(TAG, message)
        private fun logWarning(message: String) = Log.w(TAG, message)
        private fun logError(message: String, throwable: Throwable? = null) = Log.e(TAG, message, throwable)
    }

    private var countryData: CountryPrayerData? = null
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get prayer calculation method and madhhab based on location
     */
    suspend fun getPrayerMethodForLocation(
        location: AndroidLocation
    ): LocationBasedPrayerSettings = withContext(Dispatchers.IO) {
        logInfo("🌍 Starting auto-detection for coordinates: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}")
        
        try {
            // Load country data if not already loaded
            if (countryData == null) {
                logDebug("📦 Loading country prayer methods database...")
                val startTime = System.currentTimeMillis()
                loadCountryData()
                val loadTime = System.currentTimeMillis() - startTime
                logDebug("✅ Country data loaded successfully in ${loadTime}ms (${countryData?.countries?.size} countries, ${countryData?.calculationMethods?.size} methods)")
            }

            // Get country code from coordinates
            logDebug("🔍 Performing reverse geocoding...")
            val countryCode = getCountryCodeFromLocation(location)
            
            if (countryCode != null) {
                logInfo("🏳️ Country detected: $countryCode")
            } else {
                logWarning("⚠️ Unable to detect country from coordinates")
            }
            
            // Get country-specific settings
            val countrySettings = if (countryCode != null) {
                val settings = countryData?.countries?.get(countryCode)
                if (settings != null) {
                    logInfo("📋 Found prayer settings for ${settings.name} ($countryCode)")
                    logDebug("   - Calculation Method: ${settings.calculationMethod}")
                    logDebug("   - Madhhab: ${settings.madhhab}")
                } else {
                    logWarning("❌ No prayer settings found for country code: $countryCode")
                }
                settings
            } else {
                logDebug("🌐 Falling back to regional defaults")
                null
            }
            
            if (countrySettings != null) {
                // Get calculation method details from JSON
                val calculationMethodDetails = countryData?.calculationMethods?.get(countrySettings.calculationMethod)
                
                // Map the calculation method and madhhab
                val mappedCalculationMethod = mapCalculationMethod(countrySettings.calculationMethod)
                val mappedMadhhab = mapMadhhab(countrySettings.madhhab)
                
                logDebug("🔄 Mapping calculation method: ${countrySettings.calculationMethod} → ${mappedCalculationMethod.displayName}")
                logDebug("🔄 Mapping madhhab: ${countrySettings.madhhab} → $mappedMadhhab")
                
                val result = LocationBasedPrayerSettings(
                    calculationMethod = mappedCalculationMethod,
                    madhhab = mappedMadhhab,
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
                
                // Log comprehensive result
                logInfo("✅ Auto-detection successful for ${result.countryName}")
                logDebug("📊 Final Settings:")
                logDebug("   - Method: ${result.calculationMethod.displayName}")
                logDebug("   - Madhhab: ${result.madhhab}")
                logDebug("   - Fajr Angle: ${result.customFajrAngle ?: "default"}°")
                logDebug("   - Isha: ${result.customIshaAngle?.let { "${it}°" } ?: result.customIshaOffset?.let { "${it}min offset" } ?: "default"}")
                logDebug("   - Maghrib Offset: ${result.customMaghribOffset ?: "default"}min")
                
                result
            } else {
                // Fallback to regional defaults based on coordinates
                logDebug("🌐 Using regional defaults for unknown country")
                getRegionalDefault(location)
            }
        } catch (e: Exception) {
            // Ultimate fallback with comprehensive error logging
            logError("❌ Auto-detection failed - using ultimate fallback", e)
            logError("   - Error type: ${e.javaClass.simpleName}")
            logError("   - Message: ${e.message}")
            logWarning("🔧 Using Muslim World League method as safe default")
            
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
            logDebug("📂 Reading $JSON_FILE from assets...")
            val inputStream = context.assets.open(JSON_FILE)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            
            logDebug("🔍 Parsing JSON data...")
            countryData = json.decodeFromString<CountryPrayerData>(jsonString)
            
            logInfo("✅ Successfully loaded prayer methods database")
            logDebug("   - Countries: ${countryData?.countries?.size}")
            logDebug("   - Calculation Methods: ${countryData?.calculationMethods?.size}")
            logDebug("   - Madhhab Options: ${countryData?.madhhabOptions?.size}")
            
        } catch (e: IOException) {
            logError("❌ Failed to read $JSON_FILE from assets", e)
            logError("   - Check if file exists in app/src/main/assets/")
        } catch (e: Exception) {
            logError("❌ Failed to parse country data JSON", e)
            logError("   - File: $JSON_FILE")
            logError("   - Error: ${e.message}")
        }
    }

    /**
     * Get country code from GPS coordinates using reverse geocoding
     */
    private suspend fun getCountryCodeFromLocation(location: AndroidLocation): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) {
                logWarning("⚠️ Geocoder service not available on this device")
                return@withContext null
            }

            logDebug("🌐 Performing reverse geocoding for coordinates...")
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            
            val countryCode = addresses?.firstOrNull()?.countryCode
            if (countryCode != null) {
                val countryName = addresses.firstOrNull()?.countryName
                logDebug("🏳️ Geocoding successful: $countryName ($countryCode)")
            } else {
                logWarning("⚠️ Geocoding returned no results for location")
            }
            
            return@withContext countryCode
        } catch (e: IOException) {
            logError("❌ Geocoding failed due to network/service issue", e)
            null
        } catch (e: Exception) {
            logError("❌ Unexpected error during geocoding", e)
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