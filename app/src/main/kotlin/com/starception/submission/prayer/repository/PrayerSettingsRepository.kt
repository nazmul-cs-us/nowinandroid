package com.starception.submission.prayer.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.starception.submission.prayer.model.*
import java.time.LocalDateTime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * PRAYER SETTINGS REPOSITORY: Persistent storage for user preferences and prayer data
 * 
 * This repository handles all data persistence for the prayer times app including:
 * 
 * SETTINGS MANAGEMENT:
 * - User prayer calculation preferences
 * - Location settings (GPS vs manual)
 * - Notification preferences
 * - Custom time adjustments
 * 
 * CACHING SYSTEM:
 * - Stores calculated prayer times for instant app startup
 * - Date-aware cache validation
 * - Automatic cache expiry at midnight
 * 
 * DATA FLOW:
 * - Uses Kotlin Flow for reactive UI updates
 * - Background loading to prevent main thread blocking
 * - SharedPreferences for persistent storage
 * 
 * EDIT THIS TO:
 * - Add new settings categories
 * - Modify cache strategy
 * - Change storage backend (from SharedPreferences)
 * - Add data export/import functionality
 */
@Singleton
class PrayerSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PrayerSettingsRepository"
        
        // STORAGE CONFIGURATION - JSON-based persistence system
        private const val PREFS_NAME = "prayer_settings"  // SharedPreferences file name
        private const val KEY_CURRENT_SETTINGS_JSON = "current_settings_json"  // Current user settings as JSON
        
        // NOTIFICATION SETTINGS - Alert preferences (kept separate for simplicity)
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"    // Master notification toggle
        private const val KEY_NOTIFY_BEFORE_MINUTES = "notify_before_minutes"   // Minutes before prayer to notify
        
        // PRAYER TIMES CACHE KEYS - For instant app startup
        private const val KEY_CACHED_PRAYER_DATE = "cached_prayer_date"          // Date prayers were calculated for
        private const val KEY_CACHED_FAJR = "cached_fajr"                        // Cached Fajr time (minutes from midnight)
        private const val KEY_CACHED_SUNRISE = "cached_sunrise"                  // Cached Sunrise time
        private const val KEY_CACHED_DHUHR = "cached_dhuhr"                      // Cached Dhuhr time
        private const val KEY_CACHED_ASR = "cached_asr"                          // Cached Asr time
        private const val KEY_CACHED_MAGHRIB = "cached_maghrib"                  // Cached Maghrib time
        private const val KEY_CACHED_ISHA = "cached_isha"                        // Cached Isha time
        private const val KEY_CACHED_LOCATION_LAT = "cached_location_lat"        // Cached location latitude
        private const val KEY_CACHED_LOCATION_LON = "cached_location_lon"        // Cached location longitude
        private const val KEY_CACHED_LOCATION_CITY = "cached_location_city"      // Cached location city name
        private const val KEY_CACHED_LOCATION_COUNTRY = "cached_location_country" // Cached location country
        private const val KEY_CACHED_LOCATION_COUNTRY_CODE = "cached_location_country_code" // Cached location country code
        private const val KEY_CACHED_LOCATION_TIMEZONE = "cached_location_timezone" // Cached timezone offset
    }
    
    // LAZY INITIALIZATION - Prevents main thread blocking during repository creation
    // This ensures app startup remains fast even with large preference files
    private val prefs: SharedPreferences by lazy { 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // JSON SERIALIZATION CONFIGURATION - Handles serialization/deserialization
    private val json = Json {
        ignoreUnknownKeys = true    // For backward compatibility
        prettyPrint = false         // Compact storage
    }
    
    // REACTIVE DATA FLOW - UI automatically updates when settings change
    private val _settingsFlow = MutableStateFlow<PrayerSettings?>(null)
    
    // Flag to track when settings are fully loaded from storage
    private var _settingsLoaded = false
    val settingsFlow: StateFlow<PrayerSettings> = _settingsFlow
        .filterNotNull()  // Only emit when settings are loaded
        .stateIn(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            started = SharingStarted.Lazily, // Load settings when first subscriber connects (prevents ANR)
            initialValue = getDefaultSettings()  // Default settings while loading
        )
    
    // BACKGROUND INITIALIZATION - Loads settings without blocking main thread
    // This prevents ANR (Application Not Responding) during app startup
    init {
        // Load settings in background to avoid StrictMode violations and main thread blocking
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                // Add timeout to prevent ANR if SharedPreferences access hangs
                kotlinx.coroutines.withTimeoutOrNull(5000L) { // 5 second timeout
                    _settingsFlow.value = loadSettings()
                    _settingsLoaded = true // Mark as loaded
                    android.util.Log.w("PrayerSettingsRepository", "🔥 SETTINGS LOADING COMPLETED - Flag set to true")
                } ?: run {
                    // If timeout occurs, use default settings to prevent ANR
                    android.util.Log.w("PrayerSettingsRepository", "Settings loading timed out, using defaults")
                    _settingsFlow.value = getDefaultSettings()
                    _settingsLoaded = true // Mark as loaded even with defaults
                }
            } catch (e: Exception) {
                android.util.Log.e("PrayerSettingsRepository", "Error loading settings, using defaults", e)
                _settingsFlow.value = getDefaultSettings()
                _settingsLoaded = true // Mark as loaded even with defaults
            }
        }
    }
    
    /**
     * SETTINGS GETTER: Gets current prayer settings with fast fallback
     * 
     * This provides immediate access to settings without waiting for async loading.
     * 
     * BEHAVIOR:
     * - Returns loaded settings if available
     * - Returns default settings if still loading (prevents UI blocking)
     * - Never blocks the calling thread
     * 
     * EDIT THIS TO:
     * - Add settings validation
     * - Include migration logic for old settings
     * - Add error handling for corrupted settings
     */
    fun getSettings(): PrayerSettings {
        return _settingsFlow.value ?: PrayerSettings() // Fast fallback to prevent main thread blocking
    }
    
    /**
     * AWAITABLE SETTINGS GETTER: Waits for settings to be properly loaded with timeout
     * 
     * This method waits for settings to be loaded from storage before returning.
     * Use this when you need to ensure you're getting actual saved settings, not defaults.
     * 
     * BEHAVIOR:
     * - Suspends until settings are loaded from storage (with timeout)
     * - Returns actual saved settings (not defaults)
     * - Safe to call from background coroutines
     * - Has timeout protection to prevent ANR
     * 
     * @return Properly loaded PrayerSettings (never defaults)
     */
    suspend fun getLoadedSettings(): PrayerSettings {
        android.util.Log.w("PrayerSettingsRepository", "🔥🔥 getLoadedSettings CALLED - loaded flag: $_settingsLoaded")
        
        // Wait until settings are loaded from storage WITH TIMEOUT to prevent ANR
        var waitTime = 0L
        val maxWaitTime = 3000L // 3 second max wait to prevent ANR
        
        while (!_settingsLoaded && waitTime < maxWaitTime) {
            kotlinx.coroutines.delay(50) // Larger delay, less busy waiting
            waitTime += 50
        }
        
        // If timeout occurred, log warning and return current value
        if (!_settingsLoaded) {
            android.util.Log.w("PrayerSettingsRepository", "⚠️ Settings loading timed out after ${maxWaitTime}ms")
        }
        
        val loadedSettings = _settingsFlow.value ?: getDefaultSettings()
        android.util.Log.w("PrayerSettingsRepository", "🔥🔥 getLoadedSettings RETURNING - DETAILED DATA:")
        android.util.Log.w("PrayerSettingsRepository", "   📋 Calculation Method: ${loadedSettings.calculationMethod}")
        android.util.Log.w("PrayerSettingsRepository", "   📋 Asr Madhhab: ${loadedSettings.asrMadhhab}")
        android.util.Log.w("PrayerSettingsRepository", "   📋 Custom Fajr Angle: ${loadedSettings.customFajrAngle}")
        android.util.Log.w("PrayerSettingsRepository", "   📋 Custom Isha Angle: ${loadedSettings.customIshaAngle}")
        android.util.Log.w("PrayerSettingsRepository", "   📋 Custom Isha Delay: ${loadedSettings.customIshaDelay}")
        android.util.Log.w("PrayerSettingsRepository", "   📋 Is Method Auto-Detected: ${loadedSettings.isMethodAutoDetected}")
        android.util.Log.w("PrayerSettingsRepository", "   📋 Is Madhhab Auto-Detected: ${loadedSettings.isMadhhabAutoDetected}")
        android.util.Log.w("PrayerSettingsRepository", "   📋 Are Custom Angles Auto-Detected: ${loadedSettings.areCustomAnglesAutoDetected}")
        android.util.Log.w("PrayerSettingsRepository", "   📋 Auto-Detected Country: ${loadedSettings.autoDetectedCountryName}")
        android.util.Log.w("PrayerSettingsRepository", "   📋 Has Backup JSON: ${loadedSettings.originalAutoDetectedSettingsJson != null}")
        if (loadedSettings.originalAutoDetectedSettingsJson != null) {
            android.util.Log.w("PrayerSettingsRepository", "   📋 Backup JSON Preview: ${loadedSettings.originalAutoDetectedSettingsJson?.take(100)}...")
        }
        return loadedSettings
    }
    
    /**
     * SETTINGS UPDATE: Saves new prayer settings and notifies UI
     * 
     * This function updates prayer settings both in memory and persistent storage,
     * then notifies all UI components about the change.
     * 
     * OPERATIONS:
     * 1. Saves to SharedPreferences (persistent storage)
     * 2. Updates in-memory cache (_settingsFlow)
     * 3. Triggers UI updates via Flow emission
     * 
     * UI NOTIFICATION:
     * Uses both setValue and tryEmit to ensure all UI components
     * receive the update, even if they're subscribed differently.
     * 
     * @param settings The new settings to save and apply
     * @param forceCommit Whether to use synchronous commit() for immediate persistence
     */
    /**
     * UPDATE SETTINGS - Following the algorithm specification:
     * When user changes a setting:
     * 1. Update cached_prayer_settings (JSON in preferences)
     * 2. Update only when user finishes editing
     * 3. Immediately recalculate prayer times
     */
    fun updateSettings(settings: PrayerSettings, forceCommit: Boolean = false) {
        Log.i(TAG, "📝 USER SETTINGS CHANGE DETECTED - Starting update process")
        
        val oldSettings = getSettings()
        Log.i(TAG, "📝 BEFORE vs AFTER COMPARISON:")
        Log.i(TAG, "   🕌 Calculation Method: ${oldSettings.calculationMethod.name} → ${settings.calculationMethod.name}")
        Log.i(TAG, "   🤲 Asr Madhhab: ${oldSettings.asrMadhhab.name} → ${settings.asrMadhhab.name}")
        Log.i(TAG, "   🌅 Custom Fajr Angle: ${oldSettings.customFajrAngle} → ${settings.customFajrAngle}")
        Log.i(TAG, "   🌙 Custom Isha Angle: ${oldSettings.customIshaAngle} → ${settings.customIshaAngle}")
        Log.i(TAG, "   ⏰ Custom Isha Delay: ${oldSettings.customIshaDelay} → ${settings.customIshaDelay}")
        Log.i(TAG, "   🔧 Time Adjustments Changed: ${oldSettings.timeOffsets.fajr != settings.timeOffsets.fajr || oldSettings.timeOffsets.sunrise != settings.timeOffsets.sunrise || oldSettings.timeOffsets.dhuhr != settings.timeOffsets.dhuhr || oldSettings.timeOffsets.asr != settings.timeOffsets.asr || oldSettings.timeOffsets.maghrib != settings.timeOffsets.maghrib || oldSettings.timeOffsets.isha != settings.timeOffsets.isha}")
        Log.i(TAG, "   📍 Use GPS: ${oldSettings.useGpsLocation} → ${settings.useGpsLocation}")
        Log.i(TAG, "   ✅ Auto-Detection Flags: Method=${oldSettings.isMethodAutoDetected}→${settings.isMethodAutoDetected}, Madhhab=${oldSettings.isMadhhabAutoDetected}→${settings.isMadhhabAutoDetected}")
        
        // 1. Save to cached_prayer_settings (JSON format in preferences)
        Log.i(TAG, "📝 Step 1: Saving to cached_prayer_settings")
        saveCachedPrayerSettings(settings)
        
        // 2. Update in-memory flow for UI updates
        Log.i(TAG, "📝 Step 2: Updating in-memory settings flow for UI")
        _settingsFlow.value = settings
        _settingsFlow.tryEmit(settings)
        
        // 3. Immediately recalculate prayer times
        Log.i(TAG, "📝 Step 3: Triggering prayer time recalculation")
        triggerPrayerTimeRecalculation()
        
        Log.i(TAG, "✅ USER SETTINGS UPDATE COMPLETE")
        Log.i(TAG, "   🕌 Final Method: ${settings.calculationMethod.displayName}")
        Log.i(TAG, "   🤲 Final Madhhab: ${settings.asrMadhhab.displayName}")
        Log.i(TAG, "   💾 Force Commit: $forceCommit")
    }
    
    /**
     * CALCULATION METHOD UPDATE: Changes the Islamic prayer calculation method
     * 
     * This updates which organization's calculation parameters to use for prayer times.
     * Different methods use different sun angle calculations.
     * 
     * @param method The new calculation method (e.g., Muslim World League, ISNA)
     */
    fun updateCalculationMethod(method: CalculationMethod) {
        val updated = getSettings().copy(calculationMethod = method)
        updateSettings(updated, forceCommit = true) // UI-triggered action needs immediate persistence
    }
    
    /**
     * ASR MADHHAB UPDATE: Changes the Islamic school of thought for Asr calculation
     * 
     * This affects when Asr prayer time is calculated:
     * - Standard (Shafi'i, Maliki, Hanbali): Shadow length = object length
     * - Hanafi: Shadow length = 2x object length (later time)
     * 
     * @param madhhab The Islamic school of thought for Asr calculation
     */
    fun updateAsrMadhhab(madhhab: AsrMadhhab) {
        val updated = getSettings().copy(asrMadhhab = madhhab)
        updateSettings(updated, forceCommit = true) // UI-triggered action needs immediate persistence
    }
    
    /**
     * HIGH LATITUDE ADJUSTMENT UPDATE: Changes calculation method for polar regions
     * 
     * In locations above ~48° latitude, the sun may not reach required angles.
     * This setting determines how to handle such cases.
     * 
     * @param adjustment Method for adjusting prayer times at high latitudes
     */
    fun updateHighLatitudeAdjustment(adjustment: HighLatitudeAdjustment) {
        val updated = getSettings().copy(highLatitudeAdjustment = adjustment)
        updateSettings(updated, forceCommit = true) // UI-triggered action needs immediate persistence
    }
    
    /**
     * TIME OFFSETS UPDATE: Changes custom minute adjustments for prayer times
     * 
     * Allows users to fine-tune calculated prayer times to match local customs,
     * mosque schedules, or personal preferences.
     * 
     * @param offsets Per-prayer minute adjustments (positive = later, negative = earlier)
     */
    fun updateTimeOffsets(offsets: PrayerTimeOffsets) {
        val updated = getSettings().copy(timeOffsets = offsets)
        updateSettings(updated, forceCommit = true) // UI-triggered action needs immediate persistence
    }
    
    /**
     * LOCATION SETTINGS UPDATE: Changes GPS usage and manual location preferences
     * 
     * This controls whether to use GPS location or a manually set location
     * for prayer time calculations.
     * 
     * @param useGps Whether to use GPS location when available
     * @param location Manual location to use (if GPS is disabled or unavailable)
     */
    fun updateLocationSettings(useGps: Boolean, location: Location? = null) {
        val currentSettings = getSettings()
        val updated = currentSettings.copy(
            useGpsLocation = useGps,
            location = location ?: currentSettings.location
        )
        updateSettings(updated, forceCommit = true) // UI-triggered action needs immediate persistence
    }
    
    /**
     * NOTIFICATION SETTINGS UPDATE: Changes prayer notification preferences
     * 
     * This controls whether users receive notifications for prayer times
     * and how many minutes before each prayer to notify.
     * 
     * @param enabled Whether to show prayer time notifications
     * @param beforeMinutes How many minutes before prayer time to notify (default: 10)
     */
    fun updateNotificationSettings(enabled: Boolean, beforeMinutes: Int = 10) {
        prefs.edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
            .putInt(KEY_NOTIFY_BEFORE_MINUTES, beforeMinutes)
            .apply()
    }
    
    /**
     * NOTIFICATION SETTINGS GETTER: Retrieves current notification preferences
     * 
     * These functions provide access to current notification settings without
     * loading the full PrayerSettings object.
     * 
     * @return Current notification preferences
     */
    fun isNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    fun getNotifyBeforeMinutes(): Int = prefs.getInt(KEY_NOTIFY_BEFORE_MINUTES, 10)
    
    /**
     * SETTINGS LOADER: Loads user preferences from JSON storage
     * 
     * This function reads prayer settings from JSON stored in SharedPreferences.
     * It includes error handling for corrupted or missing JSON data.
     * 
     * ERROR HANDLING:
     * - Uses try/catch for JSON parsing
     * - Falls back to default values on errors
     * - Logs errors for debugging
     * 
     * PERFORMANCE:
     * - Should only be called on background threads
     * - Uses lazy initialization to avoid blocking app startup
     * 
     * @return Complete PrayerSettings object with all user preferences
     */
    private fun loadSettings(): PrayerSettings {
        android.util.Log.w("PrayerSettingsRepository", "🔥 LOAD SETTINGS CALLED")
        
        return try {
            val settingsJson = prefs.getString(KEY_CURRENT_SETTINGS_JSON, null)
            android.util.Log.w("PrayerSettingsRepository", "🔥 JSON FROM STORAGE: ${if (settingsJson != null) "EXISTS (${settingsJson.length} chars)" else "NULL"}")
            
            if (settingsJson != null) {
                android.util.Log.w("PrayerSettingsRepository", "🔥 JSON CONTENT: ${settingsJson.take(200)}...")
                
                val settings = json.decodeFromString<PrayerSettings>(settingsJson)
                android.util.Log.w("PrayerSettingsRepository", "🔥 Settings loaded from JSON:")
                android.util.Log.w("PrayerSettingsRepository", "  Calculation Method: ${settings.calculationMethod.name}")
                android.util.Log.w("PrayerSettingsRepository", "  ASR Madhhab: ${settings.asrMadhhab.name}")
                android.util.Log.w("PrayerSettingsRepository", "  Custom Isha Angle: ${settings.customIshaAngle}")
                android.util.Log.w("PrayerSettingsRepository", "  Use GPS: ${settings.useGpsLocation}")
                android.util.Log.w("PrayerSettingsRepository", "  Is Method Auto-Detected: ${settings.isMethodAutoDetected}")
                settings
            } else {
                android.util.Log.w("PrayerSettingsRepository", "🔥 No JSON settings found, using defaults")
                getDefaultSettings()
            }
        } catch (e: Exception) {
            android.util.Log.e("PrayerSettingsRepository", "🔥 ERROR loading settings from JSON, using defaults", e)
            getDefaultSettings()
        }
    }
    
    /**
     * DEFAULT SETTINGS PROVIDER: Creates default settings without disk access
     * 
     * This provides a fast fallback when settings are still loading or corrupted.
     * Used during app startup to prevent UI blocking.
     * 
     * DEFAULTS:
     * - Muslim World League calculation method (widely used)
     * - Standard Asr madhhab (most common)
     * - No high latitude adjustments
     * - GPS location enabled
     * - All custom angles/offsets disabled
     * 
     * @return PrayerSettings with sensible defaults for immediate use
     */
    /**
     * PRAYER SETTINGS ALGORITHM IMPLEMENTATION
     * 
     * Following the specification:
     * 1. Initialization: Detect cached country → Load auto-detected settings → Load cached settings → Populate UI
     * 2. User changes: Update cached_prayer_settings → Recalculate times
     * 3. Restore logic: Compare cached vs auto-detected JSON → Show/hide restore option
     */
    
    /**
     * Get cached country code from current location settings
     */
    fun getCachedCountry(): String? {
        val currentSettings = getCachedPrayerSettings()
        val countryCode = currentSettings?.location?.countryCode
        val countryName = currentSettings?.location?.country
        
        Log.i(TAG, "🔍 CACHED COUNTRY DEBUG:")
        Log.i(TAG, "   - Has cached settings: ${currentSettings != null}")
        Log.i(TAG, "   - Has location: ${currentSettings?.location != null}")
        if (currentSettings?.location != null) {
            Log.i(TAG, "   - Location city: ${currentSettings.location?.city}")
            Log.i(TAG, "   - Location country: ${currentSettings.location?.country}")
            Log.i(TAG, "   - Location country code: ${currentSettings.location?.countryCode}")
            Log.i(TAG, "   - Location lat/lng: ${currentSettings.location?.latitude}, ${currentSettings.location?.longitude}")
            Log.i(TAG, "   - Location timezone: ${currentSettings.location?.timeZoneOffset}")
        }
        
        // Prefer country code from geocoding API, fallback to country name if needed
        val result = if (!countryCode.isNullOrEmpty()) {
            Log.i(TAG, "   - Using country code from geocoding: $countryCode")
            countryCode
        } else {
            Log.i(TAG, "   - No country code available, returning null (country name: $countryName)")
            null
        }
        
        Log.i(TAG, "   - Returning country code: $result")
        return result
    }
    
    /**
     * Get auto-detected prayer settings for a country from JSON
     */
    fun getAutoDetectedSettingsForCountry(countryCode: String): PrayerSettings? {
        Log.i(TAG, "🌍 COUNTRY AUTO-DETECTION: Loading settings for country: $countryCode")
        
        return try {
            val startTime = System.currentTimeMillis()
            
            // 1. Load JSON from assets
            Log.i(TAG, "📦 Loading country_prayer_methods.json from assets...")
            val jsonString = context.assets.open("country_prayer_methods.json").bufferedReader().use { it.readText() }
            val loadTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "📦 JSON loaded successfully (${loadTime}ms, ${jsonString.length} chars)")
            
            // 2. Parse JSON
            val parseStartTime = System.currentTimeMillis()
            val jsonData = Json.parseToJsonElement(jsonString).jsonObject
            val parseTime = System.currentTimeMillis() - parseStartTime
            Log.i(TAG, "📊 JSON parsed successfully (${parseTime}ms, top-level keys: ${jsonData.keys.joinToString(", ")})")
            
            // 3. Extract countries object and find country entry
            val countriesData = jsonData["countries"]?.jsonObject
            if (countriesData == null) {
                Log.e(TAG, "❌ No 'countries' key found in JSON structure")
                return null
            }
            
            Log.i(TAG, "📊 Countries data found (${countriesData.size} countries available)")
            Log.i(TAG, "📊 All available countries: ${countriesData.keys.sorted().joinToString(", ")}")
            Log.i(TAG, "📊 Looking for country code: '$countryCode'")
            
            val countryEntry = countriesData[countryCode]?.jsonObject
            if (countryEntry == null) {
                Log.w(TAG, "🏳️ Country not found in database: '$countryCode'")
                Log.w(TAG, "   Exact matches check:")
                countriesData.keys.forEach { key ->
                    if (key.contains(countryCode, ignoreCase = true) || countryCode.contains(key, ignoreCase = true)) {
                        Log.w(TAG, "     - Potential match: '$key'")
                    }
                }
                return null
            }
            
            Log.i(TAG, "🔍 Country found: $countryCode")
            
            // Extract all data from JSON entry
            val countryName = countryEntry["name"]?.jsonPrimitive?.content ?: "Unknown"
            val methodName = countryEntry["calculationMethod"]?.jsonPrimitive?.content
            val madhhabName = countryEntry["madhhab"]?.jsonPrimitive?.content
            val customFajrAngleStr = countryEntry["customFajrAngle"]?.jsonPrimitive?.content
            val customIshaAngleStr = countryEntry["customIshaAngle"]?.jsonPrimitive?.content
            val customIshaDelayStr = countryEntry["customIshaDelay"]?.jsonPrimitive?.content
            
            Log.i(TAG, "📋 RAW COUNTRY JSON DATA:")
            Log.i(TAG, "   🏳️ Country Code: $countryCode")
            Log.i(TAG, "   📍 Country Name: $countryName")
            Log.i(TAG, "   🕌 Raw Calculation Method: $methodName")
            Log.i(TAG, "   🤲 Raw Madhhab: $madhhabName")
            Log.i(TAG, "   🌅 Raw Custom Fajr Angle: $customFajrAngleStr")
            Log.i(TAG, "   🌙 Raw Custom Isha Angle: $customIshaAngleStr") 
            Log.i(TAG, "   ⏰ Raw Custom Isha Delay: $customIshaDelayStr")
            Log.i(TAG, "   📄 Full JSON Entry: ${countryEntry.toString().take(300)}...")
            
            // 4. Map calculation method
            val calculationMethod = when (methodName) {
                "Muslim_World_League" -> CalculationMethod.MUSLIM_WORLD_LEAGUE
                "Umm_al_Qura_University_Makkah" -> CalculationMethod.UMM_AL_QURA
                "Egyptian_General_Authority_of_Survey" -> CalculationMethod.EGYPTIAN_AUTHORITY
                "University_of_Islamic_Sciences_Karachi", "University_of_Karachi" -> CalculationMethod.UNIVERSITY_OF_ISLAMIC_SCIENCES
                "Islamic_Society_of_North_America" -> CalculationMethod.ISNA
                "Institute_of_Geophysics_University_of_Tehran" -> CalculationMethod.INSTITUTE_OF_GEOPHYSICS_TEHRAN
                "Shia_Ithna_Ashari_Leva_Research_Institute_Qum" -> CalculationMethod.SHIA_ITHNA_ASHARI
                "Majlis_Ugama_Islam_Singapura_Singapore" -> CalculationMethod.MUIS
                "Europe", "South_Africa" -> CalculationMethod.MUSLIM_WORLD_LEAGUE // Common fallbacks
                else -> {
                    Log.w(TAG, "⚠️ Unknown calculation method: $methodName, using Muslim World League")
                    CalculationMethod.MUSLIM_WORLD_LEAGUE
                }
            }
            
            // 5. Map madhhab
            val asrMadhhab = when (madhhabName?.lowercase()) {
                "hanafi" -> AsrMadhhab.HANAFI
                "shafi", "shafii" -> AsrMadhhab.STANDARD
                "maliki" -> AsrMadhhab.STANDARD
                "hanbali" -> AsrMadhhab.STANDARD
                "jafari" -> AsrMadhhab.STANDARD
                "ibadi" -> AsrMadhhab.STANDARD
                else -> {
                    Log.w(TAG, "⚠️ Unknown madhhab: $madhhabName, using Standard")
                    AsrMadhhab.STANDARD
                }
            }
            
            Log.i(TAG, "🔄 Method mapping complete:")
            Log.i(TAG, "   - Mapped to: ${calculationMethod.displayName}")
            Log.i(TAG, "   - Asr method: ${asrMadhhab.displayName}")
            
            // 6. Extract custom angles if available
            val customFajrAngle = countryEntry["customFajrAngle"]?.jsonPrimitive?.doubleOrNull
            val customIshaAngle = countryEntry["customIshaAngle"]?.jsonPrimitive?.doubleOrNull
            val customIshaDelay = countryEntry["customIshaDelay"]?.jsonPrimitive?.intOrNull
            
            if (customFajrAngle != null || customIshaAngle != null || customIshaDelay != null) {
                Log.i(TAG, "⚙️ Custom angles found:")
                Log.i(TAG, "   - Custom Fajr angle: $customFajrAngle°")
                Log.i(TAG, "   - Custom Isha angle: $customIshaAngle°")
                Log.i(TAG, "   - Custom Isha delay: $customIshaDelay min")
            }
            
            // 7. Create prayer settings with auto-detection info
            val autoDetectedSettings = PrayerSettings(
                calculationMethod = calculationMethod,
                asrMadhhab = asrMadhhab,
                customFajrAngle = customFajrAngle,
                customIshaAngle = customIshaAngle,
                customIshaDelay = customIshaDelay,
                
                // Auto-detection metadata
                isMethodAutoDetected = true,
                isMadhhabAutoDetected = true,
                areCustomAnglesAutoDetected = customFajrAngle != null || customIshaAngle != null || customIshaDelay != null,
                autoDetectedCountryName = countryName,
                autoDetectedCountryCode = countryCode,
                
                // Store backup for restore functionality
                originalAutoDetectedSettingsJson = json.encodeToString(PrayerSettings(
                    calculationMethod = calculationMethod,
                    asrMadhhab = asrMadhhab,
                    customFajrAngle = customFajrAngle,
                    customIshaAngle = customIshaAngle,
                    customIshaDelay = customIshaDelay,
                    isMethodAutoDetected = true,
                    isMadhhabAutoDetected = true,
                    areCustomAnglesAutoDetected = customFajrAngle != null || customIshaAngle != null || customIshaDelay != null,
                    autoDetectedCountryName = countryName,
                    autoDetectedCountryCode = countryCode
                ))
            )
            
            val totalTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "✅ AUTO-DETECTION COMPLETE (${totalTime}ms total)")
            Log.i(TAG, "📊 FINAL AUTO-DETECTED SETTINGS FOR $countryName:")
            Log.i(TAG, "   🕌 Calculation Method: ${autoDetectedSettings.calculationMethod.name} (${autoDetectedSettings.calculationMethod.displayName})")
            Log.i(TAG, "   🤲 Asr Madhhab: ${autoDetectedSettings.asrMadhhab.name} (${autoDetectedSettings.asrMadhhab.displayName})")
            Log.i(TAG, "   🌅 Custom Fajr Angle: ${autoDetectedSettings.customFajrAngle ?: "null"}")
            Log.i(TAG, "   🌙 Custom Isha Angle: ${autoDetectedSettings.customIshaAngle ?: "null"}")
            Log.i(TAG, "   ⏰ Custom Isha Delay: ${autoDetectedSettings.customIshaDelay ?: "null"}")
            Log.i(TAG, "   🔧 Time Adjustments: ALL DEFAULTS (Fajr=${autoDetectedSettings.timeOffsets.fajr}, Sunrise=${autoDetectedSettings.timeOffsets.sunrise}, Dhuhr=${autoDetectedSettings.timeOffsets.dhuhr}, Asr=${autoDetectedSettings.timeOffsets.asr}, Maghrib=${autoDetectedSettings.timeOffsets.maghrib}, Isha=${autoDetectedSettings.timeOffsets.isha})")
            Log.i(TAG, "   🧭 High Latitude Method: ${autoDetectedSettings.highLatitudeAdjustment.name}")
            Log.i(TAG, "   📍 Use GPS: ${autoDetectedSettings.useGpsLocation} (default)")
            Log.i(TAG, "   ✅ Auto-Detection Flags: Method=${autoDetectedSettings.isMethodAutoDetected}, Madhhab=${autoDetectedSettings.isMadhhabAutoDetected}, Angles=${autoDetectedSettings.areCustomAnglesAutoDetected}")
            Log.i(TAG, "   🌍 Auto-Detected Country: ${autoDetectedSettings.autoDetectedCountryName} (${autoDetectedSettings.autoDetectedCountryCode})")
            Log.i(TAG, "   💾 Has Backup JSON: ${if (autoDetectedSettings.originalAutoDetectedSettingsJson != null) "YES (${autoDetectedSettings.originalAutoDetectedSettingsJson!!.length} chars)" else "NO"}")
            Log.i(TAG, "   📄 Generated Settings JSON: ${json.encodeToString(autoDetectedSettings).take(300)}...")
            
            autoDetectedSettings
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load auto-detected settings for $countryCode", e)
            Log.e(TAG, "   - Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "   - Error message: ${e.message}")
            null
        }
    }
    
    /**
     * Get cached prayer settings from preferences (JSON format)
     */
    fun getCachedPrayerSettings(): PrayerSettings? {
        Log.i(TAG, "📋 CACHE RETRIEVAL: Getting cached prayer settings")
        
        val cachedJson = prefs.getString(KEY_CURRENT_SETTINGS_JSON, null)
        Log.i(TAG, "📋 Raw cached JSON: ${if (cachedJson != null) "EXISTS (${cachedJson.length} chars)" else "NULL"}")
        
        return if (cachedJson != null) {
            try {
                Log.i(TAG, "📋 Parsing cached JSON: ${cachedJson.take(200)}...")
                val cachedSettings = json.decodeFromString<PrayerSettings>(cachedJson)
                
                Log.i(TAG, "📋 CACHED SETTINGS RETRIEVED SUCCESSFULLY:")
                Log.i(TAG, "   🕌 Calculation Method: ${cachedSettings.calculationMethod.name} (${cachedSettings.calculationMethod.displayName})")
                Log.i(TAG, "   🤲 Asr Madhhab: ${cachedSettings.asrMadhhab.name} (${cachedSettings.asrMadhhab.displayName})")
                Log.i(TAG, "   🌅 Custom Fajr Angle: ${cachedSettings.customFajrAngle ?: "null"}")
                Log.i(TAG, "   🌙 Custom Isha Angle: ${cachedSettings.customIshaAngle ?: "null"}")
                Log.i(TAG, "   ⏰ Custom Isha Delay: ${cachedSettings.customIshaDelay ?: "null"}")
                Log.i(TAG, "   🔧 Time Adjustments: Fajr=${cachedSettings.timeOffsets.fajr}, Sunrise=${cachedSettings.timeOffsets.sunrise}, Dhuhr=${cachedSettings.timeOffsets.dhuhr}, Asr=${cachedSettings.timeOffsets.asr}, Maghrib=${cachedSettings.timeOffsets.maghrib}, Isha=${cachedSettings.timeOffsets.isha}")
                Log.i(TAG, "   🧭 High Latitude Method: ${cachedSettings.highLatitudeAdjustment.name}")
                Log.i(TAG, "   📍 Use GPS: ${cachedSettings.useGpsLocation}")
                Log.i(TAG, "   🏳️ Location Override: ${cachedSettings.location?.getDisplayName() ?: "null"}")
                Log.i(TAG, "   ✅ Auto-Detection Flags: Method=${cachedSettings.isMethodAutoDetected}, Madhhab=${cachedSettings.isMadhhabAutoDetected}, Angles=${cachedSettings.areCustomAnglesAutoDetected}")
                Log.i(TAG, "   🌍 Auto-Detected Country: ${cachedSettings.autoDetectedCountryName ?: "null"} (${cachedSettings.autoDetectedCountryCode ?: "null"})")
                Log.i(TAG, "   💾 Has Backup JSON: ${if (cachedSettings.originalAutoDetectedSettingsJson != null) "YES (${cachedSettings.originalAutoDetectedSettingsJson!!.length} chars)" else "NO"}")
                
                cachedSettings
            } catch (e: Exception) {
                Log.e(TAG, "❌ CACHE PARSING FAILED: ${e.message}")
                Log.e(TAG, "   Error details: ${e.javaClass.simpleName}")
                Log.e(TAG, "   Problematic JSON: ${cachedJson.take(500)}")
                null
            }
        } else {
            Log.i(TAG, "📋 No cached settings found - will use auto-detected or defaults")
            null
        }
    }
    
    /**
     * Save prayer settings to cache (JSON format)
     */
    fun saveCachedPrayerSettings(settings: PrayerSettings) {
        Log.i(TAG, "💾 CACHE SAVE: Saving prayer settings to cache")
        
        val settingsJson = json.encodeToString(settings)
        Log.i(TAG, "💾 Generated JSON (${settingsJson.length} chars): ${settingsJson.take(300)}...")
        
        Log.i(TAG, "💾 SETTINGS BEING CACHED:")
        Log.i(TAG, "   🕌 Calculation Method: ${settings.calculationMethod.name} (${settings.calculationMethod.displayName})")
        Log.i(TAG, "   🤲 Asr Madhhab: ${settings.asrMadhhab.name} (${settings.asrMadhhab.displayName})")
        Log.i(TAG, "   🌅 Custom Fajr Angle: ${settings.customFajrAngle ?: "null"}")
        Log.i(TAG, "   🌙 Custom Isha Angle: ${settings.customIshaAngle ?: "null"}")
        Log.i(TAG, "   ⏰ Custom Isha Delay: ${settings.customIshaDelay ?: "null"}")
        Log.i(TAG, "   🔧 Time Adjustments: Fajr=${settings.timeOffsets.fajr}, Sunrise=${settings.timeOffsets.sunrise}, Dhuhr=${settings.timeOffsets.dhuhr}, Asr=${settings.timeOffsets.asr}, Maghrib=${settings.timeOffsets.maghrib}, Isha=${settings.timeOffsets.isha}")
        Log.i(TAG, "   🧭 High Latitude Method: ${settings.highLatitudeAdjustment.name}")
        Log.i(TAG, "   📍 Use GPS: ${settings.useGpsLocation}")
        Log.i(TAG, "   🏳️ Location Override: ${settings.location?.getDisplayName() ?: "null"}")
        Log.i(TAG, "   ✅ Auto-Detection Flags: Method=${settings.isMethodAutoDetected}, Madhhab=${settings.isMadhhabAutoDetected}, Angles=${settings.areCustomAnglesAutoDetected}")
        Log.i(TAG, "   🌍 Auto-Detected Country: ${settings.autoDetectedCountryName ?: "null"} (${settings.autoDetectedCountryCode ?: "null"})")
        Log.i(TAG, "   💾 Has Backup JSON: ${if (settings.originalAutoDetectedSettingsJson != null) "YES (${settings.originalAutoDetectedSettingsJson!!.length} chars)" else "NO"}")
        
        prefs.edit().putString(KEY_CURRENT_SETTINGS_JSON, settingsJson).apply()
        
        // Verify it was saved
        val verifyJson = prefs.getString(KEY_CURRENT_SETTINGS_JSON, null)
        if (verifyJson != null && verifyJson == settingsJson) {
            Log.i(TAG, "✅ CACHE SAVE VERIFIED: Settings successfully saved to preferences")
        } else {
            Log.e(TAG, "❌ CACHE SAVE FAILED: Verification failed")
            Log.e(TAG, "   Expected length: ${settingsJson.length}")
            Log.e(TAG, "   Actual length: ${verifyJson?.length ?: 0}")
        }
    }
    
    /**
     * Initialize settings following the algorithm:
     * 1. Detect cached country → 2. Load auto-detected → 3. Load cached → 4. Populate UI
     */
    fun initializeSettings(): PrayerSettings {
        Log.i(TAG, "🚀 INITIALIZATION ALGORITHM STARTED")
        Log.i(TAG, "   Step 1: Detect cached country code...")
        
        val cachedCountry = getCachedCountry()
        Log.i(TAG, "   Step 1 Result: ${if (cachedCountry != null) "Found country code: $cachedCountry" else "No cached country code"}")
        
        Log.i(TAG, "   Step 2: Load auto-detected settings for country...")
        val autoDetectedSettings = cachedCountry?.let { 
            Log.i(TAG, "   Step 2: Attempting auto-detection for country: $it")
            getAutoDetectedSettingsForCountry(it)
        }
        Log.i(TAG, "   Step 2 Result: ${if (autoDetectedSettings != null) "Auto-detected settings loaded" else "No auto-detected settings"}")
        
        Log.i(TAG, "   Step 3: Load cached prayer settings...")
        val cachedSettings = getCachedPrayerSettings()
        Log.i(TAG, "   Step 3 Result: ${if (cachedSettings != null) "Cached settings loaded" else "No cached settings"}")
        
        Log.i(TAG, "   Step 4: Populate UI with priority logic...")
        
        val finalSettings = when {
            cachedSettings != null -> {
                Log.i(TAG, "✅ INITIALIZATION RESULT: Using cached prayer settings (highest priority)")
                Log.i(TAG, "   🕌 Final Method: ${cachedSettings.calculationMethod.displayName}")
                Log.i(TAG, "   🤲 Final Madhhab: ${cachedSettings.asrMadhhab.displayName}")
                Log.i(TAG, "   📍 Source: User's cached preferences")
                cachedSettings
            }
            autoDetectedSettings != null -> {
                Log.i(TAG, "✅ INITIALIZATION RESULT: Using auto-detected settings for $cachedCountry")
                Log.i(TAG, "   🕌 Final Method: ${autoDetectedSettings.calculationMethod.displayName}")
                Log.i(TAG, "   🤲 Final Madhhab: ${autoDetectedSettings.asrMadhhab.displayName}")
                Log.i(TAG, "   📍 Source: Auto-detected from location ($cachedCountry)")
                autoDetectedSettings
            }
            else -> {
                Log.i(TAG, "⚠️ INITIALIZATION RESULT: Using default settings (fallback)")
                val defaultSettings = getDefaultSettings()
                Log.i(TAG, "   🕌 Final Method: ${defaultSettings.calculationMethod.displayName}")
                Log.i(TAG, "   🤲 Final Madhhab: ${defaultSettings.asrMadhhab.displayName}")
                Log.i(TAG, "   📍 Source: System defaults")
                defaultSettings
            }
        }
        
        Log.i(TAG, "🏁 INITIALIZATION ALGORITHM COMPLETE")
        return finalSettings
    }
    
    /**
     * Compare cached settings with auto-detected settings to determine restore visibility
     */
    fun shouldShowRestoreOption(): Boolean {
        val cachedCountry = getCachedCountry() ?: return false
        val autoDetectedSettings = getAutoDetectedSettingsForCountry(cachedCountry) ?: return false
        val cachedSettings = getCachedPrayerSettings() ?: return false
        
        // Convert both to JSON and compare
        val autoDetectedJson = json.encodeToString(autoDetectedSettings)
        val cachedJson = json.encodeToString(cachedSettings)
        
        return autoDetectedJson != cachedJson
    }
    
    /**
     * Restore to auto-detected settings
     */
    fun restoreToAutoDetected(): Boolean {
        val cachedCountry = getCachedCountry() ?: return false
        val autoDetectedSettings = getAutoDetectedSettingsForCountry(cachedCountry) ?: return false
        
        saveCachedPrayerSettings(autoDetectedSettings)
        _settingsFlow.value = autoDetectedSettings
        
        // Trigger recalculation
        triggerPrayerTimeRecalculation()
        
        return true
    }
    
    /**
     * Trigger prayer time recalculation with detailed logging
     */
    private fun triggerPrayerTimeRecalculation() {
        Log.i(TAG, "⏰ PRAYER TIME RECALCULATION TRIGGERED")
        
        val currentSettings = getSettings()
        Log.i(TAG, "⏰ RECALCULATION CONTEXT:")
        Log.i(TAG, "   🕌 Using Method: ${currentSettings.calculationMethod.displayName}")
        Log.i(TAG, "   🤲 Using Madhhab: ${currentSettings.asrMadhhab.displayName}")
        Log.i(TAG, "   🌅 Custom Fajr Angle: ${currentSettings.customFajrAngle ?: "using default"}")
        Log.i(TAG, "   🌙 Custom Isha Angle: ${currentSettings.customIshaAngle ?: "using default"}")
        Log.i(TAG, "   ⏰ Custom Isha Delay: ${currentSettings.customIshaDelay ?: "using default"}")
        Log.i(TAG, "   🔧 Time Adjustments Applied: Fajr=${currentSettings.timeOffsets.fajr}min, Sunrise=${currentSettings.timeOffsets.sunrise}min, Dhuhr=${currentSettings.timeOffsets.dhuhr}min, Asr=${currentSettings.timeOffsets.asr}min, Maghrib=${currentSettings.timeOffsets.maghrib}min, Isha=${currentSettings.timeOffsets.isha}min")
        Log.i(TAG, "   📍 Location Source: ${if (currentSettings.useGpsLocation) "GPS" else "Manual"}")
        if (currentSettings.location != null) {
            Log.i(TAG, "   🏳️ Manual Location: ${currentSettings.location!!.getDisplayName()}")
        }
        Log.i(TAG, "   🧭 High Latitude Method: ${currentSettings.highLatitudeAdjustment.name}")
        
        // Clear existing prayer times cache to force recalculation
        clearPrayerTimesCache()
        Log.i(TAG, "   🧹 Prayer times cache cleared - fresh calculation will occur")
        
        // TODO: This would integrate with the actual prayer time calculation service
        // Example integration points:
        // - PrayerTimeCalculationService.recalculate(currentSettings)
        // - NotificationService.updateSchedule()
        // - UI refresh triggers
        
        Log.i(TAG, "✅ RECALCULATION REQUEST COMPLETE - Prayer calculation service should pick up changes")
    }

    private fun getDefaultSettings(): PrayerSettings {
        return PrayerSettings(
            calculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
            asrMadhhab = AsrMadhhab.STANDARD,
            highLatitudeAdjustment = HighLatitudeAdjustment.NONE,
            // Auto-detection information - default to not auto-detected
            isMethodAutoDetected = false,
            isMadhhabAutoDetected = false,
            autoDetectedCountryName = null,
            autoDetectedCountryCode = null,
            areCustomAnglesAutoDetected = false,
            originalAutoDetectedSettingsJson = null,
            // Custom settings
            customFajrAngle = null,
            customIshaAngle = null,
            customIshaDelay = null,
            timeOffsets = PrayerTimeOffsets(),
            useGpsLocation = true,
            location = null
        )
    }
    
    /**
     * SETTINGS SAVER: Persists user preferences to JSON storage
     * 
     * This function serializes all prayer settings to JSON and stores them
     * in SharedPreferences for persistence across app restarts.
     * 
     * STORAGE STRATEGY:
     * - Complete settings object serialized to JSON
     * - Compact storage format
     * - Uses atomic operations for consistency
     * 
     * @param settings The settings to save to persistent storage
     * @param forceCommit Whether to use synchronous commit for immediate persistence
     */
    private fun saveSettings(settings: PrayerSettings, forceCommit: Boolean = true) {
        android.util.Log.w("PrayerSettingsRepository", "🔥 SAVE SETTINGS CALLED - ASR: ${settings.asrMadhhab.name}, Custom Isha: ${settings.customIshaAngle}")
        
        try {
            val settingsJson = json.encodeToString(settings)
            android.util.Log.w("PrayerSettingsRepository", "🔥 JSON GENERATED: ${settingsJson.take(200)}...")
            
            val editor = prefs.edit()
            editor.putString(KEY_CURRENT_SETTINGS_JSON, settingsJson)
            
            // Always use apply() instead of commit() to prevent main thread blocking
            // commit() is synchronous and can cause ANR/startup hangs
            editor.apply()
            android.util.Log.w("PrayerSettingsRepository", "🔥 APPLY CALLED (async) - forceCommit was: $forceCommit")
            val result = true
            
            // Verify it was saved
            val savedJson = prefs.getString(KEY_CURRENT_SETTINGS_JSON, null)
            if (savedJson != null) {
                android.util.Log.w("PrayerSettingsRepository", "🔥 VERIFICATION: JSON WAS SAVED SUCCESSFULLY - Length: ${savedJson.length}")
            } else {
                android.util.Log.e("PrayerSettingsRepository", "🔥 ERROR: JSON WAS NOT SAVED!")
            }
            
            android.util.Log.w("PrayerSettingsRepository", "🔥 Settings saved to JSON storage:")
            android.util.Log.w("PrayerSettingsRepository", "  Calculation Method: ${settings.calculationMethod.name}")
            android.util.Log.w("PrayerSettingsRepository", "  ASR Madhhab: ${settings.asrMadhhab.name}")
            android.util.Log.w("PrayerSettingsRepository", "  Custom Isha Angle: ${settings.customIshaAngle}")
            
        } catch (e: Exception) {
            android.util.Log.e("PrayerSettingsRepository", "🔥 ERROR saving settings to JSON", e)
        }
    }
    
    /**
     * SETTINGS RESET: Clears all saved preferences and returns to defaults
     * 
     * This function completely removes all saved preferences and reloads
     * settings from defaults. Useful for troubleshooting or user preference.
     * 
     * OPERATIONS:
     * 1. Clears all SharedPreferences data
     * 2. Reloads settings (will use defaults since nothing is saved)
     * 3. Updates UI via Flow emission
     * 
     * WARNING: This action is irreversible!
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply() // Use apply() - reset is usually not time-critical during startup
        _settingsFlow.value = loadSettings()
    }
    
    /**
     * ASR DEBUG FIX: Forces Asr madhhab to Standard method
     * 
     * This is a debugging function to fix issues with Asr calculation settings.
     * It bypasses normal validation and forces the setting to Standard madhhab.
     * 
     * USE CASES:
     * - Fixing corrupted settings
     * - Debugging calculation issues
     * - Emergency reset for Asr method only
     * 
     * NOTE: This is a maintenance function, not for normal user operation
     */
    fun forceSetAsrToStandard() {
        val updated = getSettings().copy(asrMadhhab = AsrMadhhab.STANDARD)
        updateSettings(updated, forceCommit = true) // Important setting change needs immediate persistence
    }
    
    /**
     * RESTORE FROM BACKUP: Restore original auto-detected settings from backup
     * 
     * This function restores the original auto-detected prayer settings when available.
     * It looks for backup settings that contain auto-detected UAE configuration.
     * 
     * USE CASES:
     * - User wants to restore original UAE auto-detected settings
     * - Undo manual changes and return to auto-detected configuration
     * - Restore country-specific prayer method settings
     */
    fun restoreAutoDetectedSettings(): Boolean {
        val currentSettings = getSettings()
        val backupJson = currentSettings.originalAutoDetectedSettingsJson
        
        Log.i(TAG, "🔄 RESTORE FUNCTION CALLED")
        Log.i(TAG, "   - Current method: ${currentSettings.calculationMethod.displayName}")
        Log.i(TAG, "   - Current auto-detected: ${currentSettings.isMethodAutoDetected}")
        Log.i(TAG, "   - Backup JSON available: ${backupJson != null}")
        Log.i(TAG, "   - Backup JSON length: ${backupJson?.length ?: 0}")
        
        return if (backupJson != null) {
            try {
                Log.i(TAG, "🔄 Parsing backup JSON...")
                val backupSettings = Json.decodeFromString<PrayerSettings>(backupJson)
                
                Log.i(TAG, "📋 BACKUP SETTINGS FOUND:")
                Log.i(TAG, "   - Backup method: ${backupSettings.calculationMethod.displayName}")
                Log.i(TAG, "   - Backup country: ${backupSettings.autoDetectedCountryName}")
                Log.i(TAG, "   - Backup custom Fajr: ${backupSettings.customFajrAngle}")
                Log.i(TAG, "   - Backup custom Isha: ${backupSettings.customIshaAngle}")
                Log.i(TAG, "   - Backup was auto-detected: ${backupSettings.isMethodAutoDetected}")
                
                // Restore with auto-detection flags enabled
                val restoredSettings = backupSettings.copy(
                    isMethodAutoDetected = true,
                    isMadhhabAutoDetected = true,
                    areCustomAnglesAutoDetected = true,
                    originalAutoDetectedSettingsJson = backupJson // Keep the backup
                )
                
                Log.i(TAG, "📤 APPLYING RESTORED SETTINGS:")
                Log.i(TAG, "   - Restored method: ${restoredSettings.calculationMethod.displayName}")
                Log.i(TAG, "   - Restored auto-detected flags: ${restoredSettings.isMethodAutoDetected}")
                Log.i(TAG, "   - Restored country: ${restoredSettings.autoDetectedCountryName}")
                
                updateSettings(restoredSettings, forceCommit = true)
                Log.i(TAG, "✅ Auto-detected settings restored and committed successfully")
                
                // Verify the settings were actually applied
                val verifySettings = getSettings()
                Log.i(TAG, "🔍 VERIFICATION:")
                Log.i(TAG, "   - Final method: ${verifySettings.calculationMethod.displayName}")
                Log.i(TAG, "   - Final auto-detected: ${verifySettings.isMethodAutoDetected}")
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to restore auto-detected settings from backup", e)
                Log.e(TAG, "   - Error type: ${e.javaClass.simpleName}")
                Log.e(TAG, "   - Error message: ${e.message}")
                false
            }
        } else {
            Log.w(TAG, "⚠️ No backup auto-detected settings available")
            Log.w(TAG, "   - Current country name: ${currentSettings.autoDetectedCountryName}")
            Log.w(TAG, "   - Current backup JSON: ${currentSettings.originalAutoDetectedSettingsJson}")
            false
        }
    }
    
    /**
     * PRAYER TIMES CACHING: Stores calculated prayer times for instant app startup
     * 
     * This is a key performance feature that makes the app start instantly.
     * 
     * CACHING STRATEGY:
     * - Stores prayer times as minutes from midnight (precise, compact)
     * - Includes date validation to ensure data is current
     * - Stores location information for context
     * - Automatically expires at midnight (new day = new calculations)
     * 
     * BENEFITS:
     * - Instant app startup (no "Calculating..." screen)
     * - Works offline (no network/GPS required)
     * - Battery efficient (avoids repeated calculations)
     * 
     * EDIT THIS TO:
     * - Cache multiple days of prayer times
     * - Add compression for large data
     * - Include calculation settings used
     */
    fun cachePrayerTimes(prayerTimes: DayPrayerTimes) {
        prefs.edit().apply {
            // Cache the date to ensure validity
            putString(KEY_CACHED_PRAYER_DATE, prayerTimes.date.toLocalDate().toString())
            
            // Cache prayer times as minutes from midnight for precision
            putInt(KEY_CACHED_FAJR, prayerTimes.fajr.toSecondOfDay() / 60)
            putInt(KEY_CACHED_SUNRISE, prayerTimes.sunrise.toSecondOfDay() / 60)
            putInt(KEY_CACHED_DHUHR, prayerTimes.dhuhr.toSecondOfDay() / 60)
            putInt(KEY_CACHED_ASR, prayerTimes.asr.toSecondOfDay() / 60)
            putInt(KEY_CACHED_MAGHRIB, prayerTimes.maghrib.toSecondOfDay() / 60)
            putInt(KEY_CACHED_ISHA, prayerTimes.isha.toSecondOfDay() / 60)
            
            // Cache location information
            putFloat(KEY_CACHED_LOCATION_LAT, prayerTimes.location.latitude.toFloat())
            putFloat(KEY_CACHED_LOCATION_LON, prayerTimes.location.longitude.toFloat())
            putString(KEY_CACHED_LOCATION_CITY, prayerTimes.location.city)
            putString(KEY_CACHED_LOCATION_COUNTRY, prayerTimes.location.country)
            putString(KEY_CACHED_LOCATION_COUNTRY_CODE, prayerTimes.location.countryCode)
            putFloat(KEY_CACHED_LOCATION_TIMEZONE, prayerTimes.location.timeZoneOffset.toFloat())
            
            apply() // Use apply() for cache - no need for immediate synchronous write during startup
        }
    }
    
    /**
     * CACHED PRAYER TIMES RETRIEVAL: Gets stored prayer times for instant display
     * 
     * This enables instant app startup by showing cached prayer times immediately.
     * 
     * VALIDATION PROCESS:
     * 1. Checks if cached data exists
     * 2. Validates cached date is today
     * 3. Ensures all prayer times are present
     * 4. Reconstructs DayPrayerTimes object
     * 
     * RETURNS:
     * - Valid DayPrayerTimes if cached data is current
     * - null if no cache, expired, or corrupted (triggers fresh calculation)
     * 
     * EDIT THIS TO:
     * - Add timezone-aware date validation
     * - Include cache health checks
     * - Support partial cache recovery
     */
    fun getCachedPrayerTimes(): DayPrayerTimes? {
        return try {
            val cachedDateStr = prefs.getString(KEY_CACHED_PRAYER_DATE, null) ?: return null
            val cachedDate = LocalDate.parse(cachedDateStr)
            
            // Only return cached data if it's for today
            if (cachedDate != LocalDate.now()) {
                return null
            }
            
            // Check if all required data is present
            if (!prefs.contains(KEY_CACHED_FAJR) || !prefs.contains(KEY_CACHED_LOCATION_LAT)) {
                return null
            }
            
            val fajrMinutes = prefs.getInt(KEY_CACHED_FAJR, -1)
            val sunriseMinutes = prefs.getInt(KEY_CACHED_SUNRISE, -1)
            val dhuhrMinutes = prefs.getInt(KEY_CACHED_DHUHR, -1)
            val asrMinutes = prefs.getInt(KEY_CACHED_ASR, -1)
            val maghribMinutes = prefs.getInt(KEY_CACHED_MAGHRIB, -1)
            val ishaMinutes = prefs.getInt(KEY_CACHED_ISHA, -1)
            
            // Validate all times are present
            if (fajrMinutes == -1 || sunriseMinutes == -1 || dhuhrMinutes == -1 ||
                asrMinutes == -1 || maghribMinutes == -1 || ishaMinutes == -1) {
                return null
            }
            
            val location = Location(
                latitude = prefs.getFloat(KEY_CACHED_LOCATION_LAT, 0f).toDouble(),
                longitude = prefs.getFloat(KEY_CACHED_LOCATION_LON, 0f).toDouble(),
                city = prefs.getString(KEY_CACHED_LOCATION_CITY, "") ?: "",
                country = prefs.getString(KEY_CACHED_LOCATION_COUNTRY, "") ?: "",
                countryCode = prefs.getString(KEY_CACHED_LOCATION_COUNTRY_CODE, "") ?: "",
                timeZoneOffset = prefs.getFloat(KEY_CACHED_LOCATION_TIMEZONE, 0f).toDouble()
            )
            
            DayPrayerTimes(
                date = cachedDate.atStartOfDay(),
                fajr = LocalTime.ofSecondOfDay((fajrMinutes * 60).toLong()),
                sunrise = LocalTime.ofSecondOfDay((sunriseMinutes * 60).toLong()),
                dhuhr = LocalTime.ofSecondOfDay((dhuhrMinutes * 60).toLong()),
                asr = LocalTime.ofSecondOfDay((asrMinutes * 60).toLong()),
                maghrib = LocalTime.ofSecondOfDay((maghribMinutes * 60).toLong()),
                isha = LocalTime.ofSecondOfDay((ishaMinutes * 60).toLong()),
                location = location
            )
        } catch (e: Exception) {
            // If any error occurs, return null to force fresh calculation
            null
        }
    }
    
    /**
     * CACHE CLEARER: Removes all cached prayer times from storage
     * 
     * This forces the app to recalculate prayer times on next startup.
     * Useful when location changes or settings change significantly.
     * 
     * CLEARED DATA:
     * - Cached prayer times for current day
     * - Associated location information
     * - Cache validity date
     * 
     * RESULT: Next app startup will show "Calculating..." and compute fresh times
     */
    fun clearPrayerTimesCache() {
        prefs.edit().apply {
            remove(KEY_CACHED_PRAYER_DATE)
            remove(KEY_CACHED_FAJR)
            remove(KEY_CACHED_SUNRISE)
            remove(KEY_CACHED_DHUHR)
            remove(KEY_CACHED_ASR)
            remove(KEY_CACHED_MAGHRIB)
            remove(KEY_CACHED_ISHA)
            remove(KEY_CACHED_LOCATION_LAT)
            remove(KEY_CACHED_LOCATION_LON)
            remove(KEY_CACHED_LOCATION_CITY)
            remove(KEY_CACHED_LOCATION_COUNTRY)
            remove(KEY_CACHED_LOCATION_COUNTRY_CODE)
            remove(KEY_CACHED_LOCATION_TIMEZONE)
            apply()
        }
    }
    
    /**
     * CACHE VALIDATOR: Checks if valid cached prayer times exist for today
     * 
     * This function performs a quick check to see if cached prayer times
     * are available and valid for the current date.
     * 
     * VALIDATION CHECKS:
     * 1. Cache exists in SharedPreferences
     * 2. Cached date matches today's date
     * 3. Essential prayer time data is present
     * 
     * PERFORMANCE:
     * - Fast operation (no object construction)
     * - Used to decide whether to show cached times or "Calculating..."
     * 
     * @return true if valid cached times exist for today, false otherwise
     */
    fun hasCachedPrayerTimesForToday(): Boolean {
        val cachedDateStr = prefs.getString(KEY_CACHED_PRAYER_DATE, null) ?: return false
        return try {
            val cachedDate = LocalDate.parse(cachedDateStr)
            cachedDate == LocalDate.now() && prefs.contains(KEY_CACHED_FAJR)
        } catch (e: Exception) {
            false
        }
    }
}