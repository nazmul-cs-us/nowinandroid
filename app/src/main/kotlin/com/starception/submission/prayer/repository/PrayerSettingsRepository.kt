package com.starception.submission.prayer.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.starception.submission.prayer.model.*
import com.starception.submission.usersettings.UserSettingsStore
import com.starception.submission.widget.PrayerWidgetUpdater
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    @ApplicationContext private val context: Context,
    private val userSettingsStore: com.starception.submission.usersettings.UserSettingsStore
) {
    // Background scope for per-country store writes (Room DAO is suspend).
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // A pending country-change CONSENT request. When the detected country differs from the active
    // one we surface a proposal here instead of changing anything; the UI shows a consent bottom
    // sheet and ONLY [applyPendingCountrySwitch] mutates settings. Null when nothing to confirm.
    private val _pendingCountrySwitch = MutableStateFlow<CountrySwitchProposal?>(null)
    val pendingCountrySwitch: StateFlow<CountrySwitchProposal?> = _pendingCountrySwitch.asStateFlow()

    // Always-alive country-change detection (repository is an app singleton, unlike the prayer
    // ViewModel which only exists while the prayer screen is open). Started from the load init
    // block below, AFTER settingsFlow has been constructed — starting it during property init would
    // race the async coroutine against settingsFlow's later initializer (NPE on a null flow).
    private fun startCountryChangeObserver() {
        storeScope.launch {
            settingsFlow
                .map { getCachedCountry()?.trim()?.uppercase().orEmpty() }
                .distinctUntilChanged()
                .filter { it.isNotEmpty() }
                .collect { code ->
                    runCatching { onCountryDetected(code) }
                        .onFailure { Log.e(TAG, "country-change observer failed for '$code'", it) }
                }
        }
    }
    companion object {
        private const val TAG = "PrayerSettingsRepository"
        
        // STORAGE CONFIGURATION - JSON-based persistence system
        private const val PREFS_NAME = "prayer_settings"  // SharedPreferences file name
        
        // SEPARATE PREFERENCE KEYS - Following new architecture
        private const val KEY_CALCULATION_SETTINGS_JSON = "calculation_settings_json"  // Prayer calculation settings
        private const val KEY_LOCATION_PREFERENCES_JSON = "location_preferences_json"  // Location preferences
        private const val KEY_NOTIFICATION_PREFERENCES_JSON = "notification_preferences_json"  // Notification preferences
        
        // LEGACY KEY - For backward compatibility
        private const val KEY_CURRENT_SETTINGS_JSON = "current_settings_json"  // Legacy combined settings
        
        // NOTIFICATION SETTINGS - Alert preferences (kept separate for simplicity)
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"    // Master notification toggle
        private const val KEY_NOTIFY_BEFORE_MINUTES = "notify_before_minutes"   // Minutes before prayer to notify
        private const val KEY_DND_PROMPT_SHOWN = "dnd_prompt_shown"             // One-time first-run DND access prompt
        
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
        private const val KEY_CACHED_LOCATION_AREA = "cached_location_area"      // Cached location area/neighborhood
        private const val KEY_CACHED_LOCATION_SUB_LOCALITY = "cached_location_sub_locality" // Cached location sub-locality
        private const val KEY_CACHED_LOCATION_THOROUGHFARE = "cached_location_thoroughfare" // Cached location thoroughfare/street
        private const val KEY_CACHED_LOCATION_ADMIN_AREA = "cached_location_admin_area" // Cached location administrative area
    }
    
    // LAZY INITIALIZATION - Prevents main thread blocking during repository creation
    // This ensures app startup remains fast even with large preference files
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // INSTANCE TRACKING - For debugging multiple instance issues
    private val instanceId = System.identityHashCode(this).toString(16)

    init {
        // Load preferences from storage on repository creation
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                Log.i(TAG, "🚀 PrayerSettingsRepository initialization started [Instance: $instanceId]")
                loadAllSettings()
                _settingsLoaded = true
                Log.i(TAG, "✅ PrayerSettingsRepository initialization completed [Instance: $instanceId]")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during repository initialization [Instance: $instanceId]", e)
            }
        }
    }
    
    /**
     * RE-INITIALIZE WITH LOCATION: Called when location becomes available for the first time
     * This allows country-based auto-detection to happen after initial app launch
     */
    fun reinitializeWithLocation() {
        val location = getLocationPreferences().location ?: return
        val detectedCountry = location.countryCode.trim().uppercase().ifEmpty {
            com.starception.submission.prayer.service.CountryCodeMapper.resolveCountryCode(
                null,
                location.country,
            ).trim().uppercase()
        }
        if (detectedCountry.isEmpty()) return

        // Country changes are consent-gated. onCountryDetected applies defaults only when there is
        // no active country yet; every later change merely publishes a proposal for the UI.
        storeScope.launch {
            runCatching { onCountryDetected(detectedCountry) }
                .onFailure {
                    Log.e(TAG, "Failed to process detected country '$detectedCountry'", it)
                }
        }
    }

    /**
     * FORCE RE-INITIALIZE FOR TIMEZONE CHANGE: Called when device timezone changes
     * Unlike reinitializeWithLocation(), this ALWAYS updates settings based on new timezone
     * regardless of current calculation method settings.
     *
     * @param newTimezoneId The new timezone ID (e.g., "Asia/Kolkata", "America/New_York")
     */
    fun forceReinitializeForTimezoneChange(newTimezoneId: String) {
        Log.i(TAG, "")
        Log.i(TAG, "🌍 TIMEZONE CHANGE DETECTED: $newTimezoneId")
        Log.i(TAG, "=".repeat(60))

        // Map timezone to country code
        val countryCode = getCountryCodeFromTimezone(newTimezoneId)
        if (countryCode == null) {
            Log.w(TAG, "⚠️ Could not determine country from timezone: $newTimezoneId")
            Log.i(TAG, "=".repeat(60))
            return
        }

        Log.i(TAG, "📍 Detected country code: $countryCode")

        // Get auto-detected settings for the new country
        val autoDetectedSettings = getAutoDetectedSettingsForCountry(countryCode)
        if (autoDetectedSettings == null) {
            Log.w(TAG, "⚠️ No auto-detected settings available for country: $countryCode")
            Log.i(TAG, "=".repeat(60))
            return
        }

        Log.i(TAG, "✅ Found settings for $countryCode:")
        Log.i(TAG, "   📐 Calculation Method: ${autoDetectedSettings.calculationMethod.name}")
        Log.i(TAG, "   🕌 Madhab: ${autoDetectedSettings.asrMadhhab.name}")

        // A timezone change commonly means travel. It must use the same consent path as a GPS or
        // manual-location country change; otherwise it can overwrite settings behind the sheet.
        storeScope.launch {
            runCatching { onCountryDetected(countryCode) }
                .onFailure { Log.e(TAG, "Failed to process timezone country '$countryCode'", it) }
        }
        Log.i(TAG, "🎯 Prayer settings change proposed for timezone: $newTimezoneId")
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "")
    }

    /**
     * Map timezone ID to ISO country code
     * Uses known timezone-to-country mappings
     */
    private fun getCountryCodeFromTimezone(timezoneId: String): String? {
        // Common timezone to country mappings
        val timezoneCountryMap = mapOf(
            // India
            "Asia/Kolkata" to "IN",
            "Asia/Calcutta" to "IN",

            // UAE
            "Asia/Dubai" to "AE",

            // Saudi Arabia
            "Asia/Riyadh" to "SA",

            // Pakistan
            "Asia/Karachi" to "PK",

            // Bangladesh
            "Asia/Dhaka" to "BD",

            // Indonesia
            "Asia/Jakarta" to "ID",

            // Malaysia
            "Asia/Kuala_Lumpur" to "MY",

            // Turkey
            "Europe/Istanbul" to "TR",

            // Egypt
            "Africa/Cairo" to "EG",

            // UK
            "Europe/London" to "GB",

            // USA
            "America/New_York" to "US",
            "America/Chicago" to "US",
            "America/Denver" to "US",
            "America/Los_Angeles" to "US",

            // Canada
            "America/Toronto" to "CA",
            "America/Vancouver" to "CA",

            // Australia
            "Australia/Sydney" to "AU",
            "Australia/Melbourne" to "AU",

            // Germany
            "Europe/Berlin" to "DE",

            // France
            "Europe/Paris" to "FR",

            // Qatar
            "Asia/Qatar" to "QA",

            // Kuwait
            "Asia/Kuwait" to "KW",

            // Bahrain
            "Asia/Bahrain" to "BH",

            // Oman
            "Asia/Muscat" to "OM",

            // Jordan
            "Asia/Amman" to "JO",

            // Morocco
            "Africa/Casablanca" to "MA",

            // Singapore
            "Asia/Singapore" to "SG",

            // Nigeria
            "Africa/Lagos" to "NG",

            // South Africa
            "Africa/Johannesburg" to "ZA",
        )

        return timezoneCountryMap[timezoneId]
    }

    // COMPREHENSIVE PREFERENCE LOGGING SYSTEM - Enhanced logging with detailed data tracking
    // This provides complete visibility into all preference operations for debugging and monitoring
    
    /**
     * ENHANCED PREFERENCE READ LOGGING
     * Logs all preference read operations with key, value, type, and metadata
     * 
     * Specific Tag: "PrayerSettings_PREF_READ" for filtered logging
     */
    private fun logPrefRead(key: String, value: Any?, defaultValue: Any? = null) {
        val valueType = when (value) {
            is String -> "String(${value.length} chars)"
            is Int -> "Integer"
            is Float -> "Float" 
            is Boolean -> "Boolean"
            is Double -> "Double"
            null -> "NULL"
            else -> value::class.simpleName ?: "Unknown"
        }
        
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        // Primary logging with main tag
        Log.i(TAG, "")
        Log.i(TAG, "🔍 PREFERENCE READ OPERATION")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        Log.i(TAG, "🗝️ Key: '$key'")
        Log.i(TAG, "📊 Data Type: $valueType")
        Log.i(TAG, "💎 Current Value: $value")
        if (defaultValue != null) {
            Log.i(TAG, "🔄 Default Fallback: $defaultValue")
        }
        Log.i(TAG, "📁 Storage File: $PREFS_NAME")
        Log.i(TAG, "")
        
        // Dedicated preference operation logging with specific tag
        Log.i("PrayerSettings_PREF_READ", "🔍 READ | $timestamp | key='$key' | type=$valueType | value=$value | file=$PREFS_NAME")
        if (defaultValue != null) {
            Log.i("PrayerSettings_PREF_READ", "🔄 DEFAULT | key='$key' | fallback=$defaultValue")
        }
    }
    
    /**
     * ENHANCED PREFERENCE WRITE LOGGING
     * Logs all preference write operations with comprehensive metadata
     * 
     * Specific Tag: "PrayerSettings_PREF_WRITE" for filtered logging
     */
    private fun logPrefWrite(key: String, value: Any?) {
        val valueType = when (value) {
            is String -> "String(${value.length} chars)"
            is Int -> "Integer"
            is Float -> "Float" 
            is Boolean -> "Boolean"
            is Double -> "Double"
            null -> "NULL"
            else -> value::class.simpleName ?: "Unknown"
        }
        
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        // Primary logging with main tag
        Log.i(TAG, "")
        Log.i(TAG, "💾 PREFERENCE WRITE OPERATION")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        Log.i(TAG, "🗝️ Key: '$key'")
        Log.i(TAG, "📊 Data Type: $valueType")
        Log.i(TAG, "💎 New Value: $value")
        Log.i(TAG, "📁 Storage File: $PREFS_NAME")
        Log.i(TAG, "")
        
        // Dedicated preference operation logging with specific tag
        Log.i("PrayerSettings_PREF_WRITE", "💾 WRITE | $timestamp | key='$key' | type=$valueType | value=$value | file=$PREFS_NAME")
    }
    
    /**
     * ENHANCED JSON READ LOGGING
     * Provides detailed analysis of JSON preference reads with size, parsing info, and content preview
     * 
     * Specific Tag: "PrayerSettings_JSON_READ" for filtered logging
     */
    private fun logPrefReadJson(key: String, jsonContent: String?, description: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
            
        // Primary logging with main tag
        Log.i(TAG, "")
        Log.i(TAG, "📄 JSON PREFERENCE READ")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        Log.i(TAG, "🗝️ Key: '$key'")
        Log.i(TAG, "📝 Description: $description")
        Log.i(TAG, "📁 Storage File: $PREFS_NAME")
        
        // Status and content analysis
        val status: String
        val size = jsonContent?.length ?: 0
        
        if (jsonContent.isNullOrEmpty()) {
            status = "EMPTY/NULL"
            Log.i(TAG, "📊 Status: EMPTY/NULL - Using defaults")
            Log.i(TAG, "🔄 Action: Will fallback to default values")
        } else if (jsonContent == "{}") {
            status = "EMPTY_OBJECT"
            Log.i(TAG, "📊 Status: EMPTY JSON OBJECT")
            Log.i(TAG, "🔄 Action: Will fallback to default values")
        } else {
            status = "DATA_FOUND"
            Log.i(TAG, "📊 Status: DATA FOUND")
            Log.i(TAG, "📏 Size: ${jsonContent.length} characters")
            Log.i(TAG, "🔄 Action: Will parse JSON content")
            
            // Show JSON content with proper formatting for debugging
            Log.i(TAG, "📄 JSON CONTENT:")
            // Split long JSON into readable chunks
            if (jsonContent.length > 200) {
                Log.i(TAG, "${jsonContent.substring(0, 200)}...")
                Log.i(TAG, "📏 (Showing first 200 chars of ${jsonContent.length} total)")
            } else {
                Log.i(TAG, jsonContent)
            }
            
            // Try to provide quick JSON analysis
            try {
                val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(jsonContent)
                when {
                    jsonElement.jsonObject.keys.isEmpty() -> Log.i(TAG, "🔍 Analysis: Empty JSON object")
                    else -> Log.i(TAG, "🔍 Analysis: ${jsonElement.jsonObject.keys.size} properties found")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ JSON Analysis: Failed to parse - ${e.message}")
            }
        }
        Log.i(TAG, "")
        
        // Dedicated JSON operation logging with specific tag
        Log.i("PrayerSettings_JSON_READ", "📄 JSON_READ | $timestamp | key='$key' | desc='$description' | status=$status | size=${size}ch | file=$PREFS_NAME")
        if (status == "DATA_FOUND" && jsonContent != null) {
            // Log first 100 chars of JSON for compact view
            val preview = if (jsonContent.length > 100) {
                "${jsonContent.substring(0, 100)}..."
            } else {
                jsonContent
            }
            Log.i("PrayerSettings_JSON_READ", "📄 CONTENT | key='$key' | preview=$preview")
        }
    }
    
    /**
     * ENHANCED JSON WRITE LOGGING
     * Comprehensive logging for JSON preference writes with validation and content analysis
     * 
     * Specific Tag: "PrayerSettings_JSON_WRITE" for filtered logging
     */
    private fun logPrefWriteJson(key: String, jsonContent: String, description: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
            
        // Primary logging with main tag
        Log.i(TAG, "")
        Log.i(TAG, "💾 JSON PREFERENCE WRITE")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        Log.i(TAG, "🗝️ Key: '$key'")
        Log.i(TAG, "📝 Description: $description")
        Log.i(TAG, "📁 Storage File: $PREFS_NAME")
        Log.i(TAG, "📏 Size: ${jsonContent.length} characters")
        
        // JSON content analysis
        var isValid = false
        var fieldCount = 0
        var fieldNames = ""
        
        try {
            val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(jsonContent)
            isValid = true
            fieldCount = jsonElement.jsonObject.keys.size
            fieldNames = jsonElement.jsonObject.keys.joinToString(", ")
            
            Log.i(TAG, "✅ JSON Validation: Valid JSON format")
            Log.i(TAG, "🔍 Properties: $fieldCount fields")
            Log.i(TAG, "🏷️ Field Names: $fieldNames")
        } catch (e: Exception) {
            Log.e(TAG, "❌ JSON Validation: Invalid JSON - ${e.message}")
        }
        
        // Show JSON content with formatting
        Log.i(TAG, "📄 JSON CONTENT TO SAVE:")
        if (jsonContent.length > 300) {
            Log.i(TAG, "${jsonContent.substring(0, 300)}...")
            Log.i(TAG, "📏 (Showing first 300 chars of ${jsonContent.length} total)")
        } else {
            // Pretty print for better readability in logs
            try {
                val prettyJson = kotlinx.serialization.json.Json { prettyPrint = true }
                    .encodeToString(kotlinx.serialization.json.JsonElement.serializer(), 
                        kotlinx.serialization.json.Json.parseToJsonElement(jsonContent))
                Log.i(TAG, prettyJson)
            } catch (e: Exception) {
                Log.i(TAG, jsonContent)
            }
        }
        Log.i(TAG, "")
        
        // Dedicated JSON operation logging with specific tag
        val status = if (isValid) "VALID" else "INVALID"
        Log.i("PrayerSettings_JSON_WRITE", "💾 JSON_WRITE | $timestamp | key='$key' | desc='$description' | status=$status | size=${jsonContent.length}ch | fields=$fieldCount | file=$PREFS_NAME")
        if (isValid && fieldNames.isNotEmpty()) {
            Log.i("PrayerSettings_JSON_WRITE", "🏷️ FIELDS | key='$key' | names=[$fieldNames]")
        }
        
        // Log content preview for compact view
        val preview = if (jsonContent.length > 100) {
            "${jsonContent.substring(0, 100)}..."
        } else {
            jsonContent
        }
        Log.i("PrayerSettings_JSON_WRITE", "📄 CONTENT | key='$key' | preview=$preview")
    }
    
    /**
     * PREFERENCE OPERATION VERIFICATION
     * Verifies that write operations were successful by reading back the data
     * 
     * Specific Tag: "PrayerSettings_PREF_VERIFY" for filtered logging
     */
    private fun verifyPrefWrite(key: String, expectedValue: Any?, operationType: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
            
        // Primary logging with main tag
        Log.i(TAG, "")
        Log.i(TAG, "🔍 PREFERENCE WRITE VERIFICATION")
        Log.i(TAG, "⏰ Timestamp: $timestamp")
        Log.i(TAG, "🗝️ Key: '$key'")
        Log.i(TAG, "📝 Operation: $operationType")
        
        val actualValue = when (expectedValue) {
            is String -> prefs.getString(key, null)
            is Int -> prefs.getInt(key, -999999) // Use unlikely default to detect missing values
            is Float -> prefs.getFloat(key, -999999f)
            is Boolean -> prefs.getBoolean(key, !expectedValue) // Use opposite as default
            else -> null
        }
        
        val success = when (expectedValue) {
            is String -> actualValue == expectedValue
            is Int -> actualValue != -999999 && actualValue == expectedValue
            is Float -> actualValue != -999999f && actualValue == expectedValue
            is Boolean -> actualValue == expectedValue
            else -> true
        }
        
        if (success) {
            Log.i(TAG, "✅ Verification: SUCCESS")
            Log.i(TAG, "💾 Confirmed Value: $actualValue")
        } else {
            Log.e(TAG, "❌ Verification: FAILED")
            Log.e(TAG, "❌ Expected: $expectedValue")
            Log.e(TAG, "❌ Actual: $actualValue")
        }
        Log.i(TAG, "")
        
        // Dedicated verification logging with specific tag
        val status = if (success) "SUCCESS" else "FAILED"
        val resultLevel = if (success) "i" else "e"
        
        Log.i("PrayerSettings_PREF_VERIFY", "🔍 VERIFY | $timestamp | key='$key' | op='$operationType' | status=$status | expected=$expectedValue | actual=$actualValue")
        
        if (!success) {
            Log.e("PrayerSettings_PREF_VERIFY", "❌ FAILED | key='$key' | expected_type=${expectedValue?.javaClass?.simpleName} | actual_type=${actualValue?.javaClass?.simpleName}")
        }
    }
    
    // JSON SERIALIZATION CONFIGURATION - Handles serialization/deserialization
    private val json = Json {
        ignoreUnknownKeys = true    // For backward compatibility
        prettyPrint = false         // Compact storage
        encodeDefaults = true       // Include fields with default values in JSON
    }
    
    // REACTIVE DATA FLOW - Separate flows for each preference type
    // Initialize with defaults immediately to avoid null handling issues
    private val _calculationSettingsFlow = MutableStateFlow(getDefaultCalculationSettings())
    private val _locationPreferencesFlow = MutableStateFlow(getDefaultLocationPreferences())
    private val _notificationPreferencesFlow = MutableStateFlow(getDefaultNotificationPreferences())

    // LEGACY COMBINED FLOW - For backward compatibility
    private val _settingsFlow = MutableStateFlow<PrayerSettings?>(null)
    
    // Flag to track when settings are fully loaded from storage
    private var _settingsLoaded = false
    
    // PUBLIC FLOWS - Expose separate preference types
    // Direct exposure using asStateFlow() ensures immediate reactivity without caching issues
    val calculationSettingsFlow: StateFlow<PrayerCalculationSettings> = _calculationSettingsFlow.asStateFlow()
    val locationPreferencesFlow: StateFlow<PrayerLocationPreferences> = _locationPreferencesFlow.asStateFlow()
    val notificationPreferencesFlow: StateFlow<PrayerNotificationPreferences> = _notificationPreferencesFlow.asStateFlow()
    
    // LEGACY COMBINED FLOW - For backward compatibility
    val settingsFlow: StateFlow<PrayerSettings> = _settingsFlow
        .filterNotNull()  // Only emit when settings are loaded
        .stateIn(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            started = SharingStarted.Lazily, // Load settings when first subscriber connects (prevents ANR)
            initialValue = getDefaultSettings()  // Default settings while loading
        )
    
    // PRAYER SETTINGS ALGORITHM - REPOSITORY INITIALIZATION
    // This triggers the initialization algorithm when the repository is created
    init {
        Log.i(TAG, "")
        Log.i(TAG, "🚀 PRAYER SETTINGS REPOSITORY INITIALIZATION")
        Log.i(TAG, "=".repeat(70))
        Log.i(TAG, "🔧 STARTUP PHASE: Background loading to prevent ANR")
        
        // Load settings in background to avoid StrictMode violations and main thread blocking
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val initStartTime = System.currentTimeMillis()
            
            try {
                // Add timeout to prevent ANR if SharedPreferences access hangs
                kotlinx.coroutines.withTimeoutOrNull(5000L) { // 5 second timeout
                    Log.i(TAG, "🔄 Starting initialization algorithm...")
                    loadAllSettings()
                    val loadTime = System.currentTimeMillis() - initStartTime
                    
                    _settingsLoaded = true // Mark as loaded
                    Log.i(TAG, "")
                    Log.i(TAG, "✅ REPOSITORY INITIALIZATION COMPLETE")
                    Log.i(TAG, "⏱️ Total initialization time: ${loadTime}ms")
                    Log.i(TAG, "📊 INITIALIZATION RESULT:")
                    Log.i(TAG, "   ✅ Settings loaded successfully")
                    Log.i(TAG, "   ✅ Reactive flows populated")
                    Log.i(TAG, "   ✅ Repository ready for UI consumption")
                    Log.i(TAG, "=".repeat(70))
                    Log.i(TAG, "")
                } ?: run {
                    // If timeout occurs, use default settings to prevent ANR
                    val timeoutTime = System.currentTimeMillis() - initStartTime
                    Log.e(TAG, "⚠️ INITIALIZATION TIMEOUT after ${timeoutTime}ms - using defaults")
                    loadDefaultSettings()
                    _settingsLoaded = true // Mark as loaded even with defaults
                    
                    Log.w(TAG, "")
                    Log.w(TAG, "⚠️ REPOSITORY INITIALIZATION COMPLETED WITH TIMEOUT")
                    Log.w(TAG, "📊 FALLBACK RESULT:")
                    Log.w(TAG, "   ⚠️ Used default settings due to timeout")
                    Log.w(TAG, "   ✅ Reactive flows populated with defaults")
                    Log.w(TAG, "   ✅ Repository ready for UI consumption")
                    Log.w(TAG, "=".repeat(70))
                    Log.w(TAG, "")
                }
            } catch (e: Exception) {
                val errorTime = System.currentTimeMillis() - initStartTime
                Log.e(TAG, "❌ INITIALIZATION ERROR after ${errorTime}ms - using defaults", e)
                loadDefaultSettings()
                _settingsLoaded = true // Mark as loaded even with defaults
                
                Log.e(TAG, "")
                Log.e(TAG, "❌ REPOSITORY INITIALIZATION COMPLETED WITH ERROR")
                Log.e(TAG, "📊 ERROR RECOVERY RESULT:")
                Log.e(TAG, "   ❌ Error occurred: ${e.message}")
                Log.e(TAG, "   ✅ Used default settings for error recovery")
                Log.e(TAG, "   ✅ Reactive flows populated with defaults")
                Log.e(TAG, "   ✅ Repository ready for UI consumption")
                Log.e(TAG, "=".repeat(70))
                Log.e(TAG, "")
            }

            // settingsFlow is now constructed and settings are loaded: safe to start the
            // always-on per-country switch observer (no null-flow race).
            startCountryChangeObserver()
        }
    }

    /**
     * NEW GETTERS: Get current preferences by type with fast fallback
     */
    fun getCalculationSettings(): PrayerCalculationSettings {
        return _calculationSettingsFlow.value  // No longer nullable
    }

    /**
     * Calculation settings that are guaranteed to reflect storage.
     *
     * [getCalculationSettings] serves the in-memory flow, which starts at defaults and is
     * filled by the background load started in the constructor. Callers that run before
     * that finishes therefore see a schedule with no offsets at all — which is what the
     * home-screen widget saw, since it is built from a broadcast and reads within
     * milliseconds of the repository being created. It showed prayer times minutes apart
     * from the app for anyone who had tuned their schedule.
     *
     * Reads SharedPreferences directly when the load has not completed, so it must not be
     * called on the main thread. Additive: the flow, its loading and its consumers are
     * untouched.
     */
    fun getCalculationSettingsFromStorage(): PrayerCalculationSettings {
        if (_settingsLoaded) return _calculationSettingsFlow.value
        return loadCalculationSettings() ?: _calculationSettingsFlow.value
    }
    
    fun getLocationPreferences(): PrayerLocationPreferences {
        return _locationPreferencesFlow.value  // No longer nullable
    }

    fun getNotificationPreferences(): PrayerNotificationPreferences {
        return _notificationPreferencesFlow.value  // No longer nullable
    }

    /**
     * Returns the persisted notification preferences for background entry points.
     *
     * A Worker can start in a fresh app process before this repository's
     * asynchronous initialization has populated the in-memory flow. Reading the
     * flow in that window returns notification defaults and can play Adhan for a
     * prayer the user already disabled. This storage-backed accessor closes that
     * race and should be called off the main thread.
     */
    fun getNotificationPreferencesFromStorage(): PrayerNotificationPreferences {
        if (_settingsLoaded) return _notificationPreferencesFlow.value
        return loadNotificationPreferences() ?: _notificationPreferencesFlow.value
    }
    
    /**
     * LEGACY SETTINGS GETTER: Gets combined prayer settings for backward compatibility
     * 
     * @deprecated Use getCalculationSettings(), getLocationPreferences(), and getNotificationPreferences() instead
     */
    @Deprecated("Use separate preference getters instead", ReplaceWith("Use getCalculationSettings(), getLocationPreferences(), and getNotificationPreferences()"))
    fun getSettings(): PrayerSettings {
        return _settingsFlow.value ?: combineToLegacySettings() // Fast fallback to prevent main thread blocking
    }
    
    /**
     * NEW AWAITABLE GETTERS: Wait for preferences to be properly loaded with timeout
     */
    suspend fun getLoadedCalculationSettings(): PrayerCalculationSettings {
        android.util.Log.w("PrayerSettingsRepository", "🔥🔥 getLoadedCalculationSettings CALLED - loaded flag: $_settingsLoaded")
        
        // Wait until settings are loaded from storage WITH TIMEOUT to prevent ANR
        var waitTime = 0L
        val maxWaitTime = 3000L // 3 second max wait to prevent ANR
        
        while (!_settingsLoaded && waitTime < maxWaitTime) {
            kotlinx.coroutines.delay(50)
            waitTime += 50
        }
        
        if (!_settingsLoaded) {
            android.util.Log.w("PrayerSettingsRepository", "⚠️ Settings loading timed out after ${maxWaitTime}ms")
        }
        
        return _calculationSettingsFlow.value ?: getDefaultCalculationSettings()
    }
    
    suspend fun getLoadedLocationPreferences(): PrayerLocationPreferences {
        var waitTime = 0L
        val maxWaitTime = 3000L
        
        while (!_settingsLoaded && waitTime < maxWaitTime) {
            kotlinx.coroutines.delay(50)
            waitTime += 50
        }
        
        return _locationPreferencesFlow.value ?: getDefaultLocationPreferences()
    }
    
    suspend fun getLoadedNotificationPreferences(): PrayerNotificationPreferences {
        var waitTime = 0L
        val maxWaitTime = 3000L
        
        while (!_settingsLoaded && waitTime < maxWaitTime) {
            kotlinx.coroutines.delay(50)
            waitTime += 50
        }
        
        return _notificationPreferencesFlow.value ?: getDefaultNotificationPreferences()
    }
    
    /**
     * LEGACY AWAITABLE SETTINGS GETTER: For backward compatibility
     * 
     * @deprecated Use separate loaded preference getters instead
     */
    @Deprecated("Use separate loaded preference getters instead")
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
        
        return _settingsFlow.value ?: combineToLegacySettings()
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
     * PRAYER SETTINGS ALGORITHM - USER CHANGE HANDLING
     * 
     * Following the algorithm for when user changes a setting:
     * 1. Update the corresponding field in cached_prayer_settings (JSON format)
     * 2. Update only when user finishes editing (not on every keystroke)
     * 3. After saving changes, immediately recalculate prayer times
     * 4. Update restore option logic
     */
    fun updateCalculationSettings(settings: PrayerCalculationSettings, forceCommit: Boolean = false) {
        Log.i(TAG, "")
        Log.i(TAG, "🎯 PRAYER SETTINGS ALGORITHM - USER CHANGE HANDLING")
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "📝 ALGORITHM PHASE: When User Changes a Setting")
        
        val startTime = System.currentTimeMillis()
        val oldSettings = getCalculationSettings()
        
        // Log detailed comparison
        Log.i(TAG, "📊 BEFORE vs AFTER COMPARISON:")
        Log.i(TAG, "   🕌 Calculation Method: '${oldSettings.calculationMethod.displayName}' → '${settings.calculationMethod.displayName}'")
        if (oldSettings.calculationMethod != settings.calculationMethod) {
            Log.i(TAG, "     🔄 CHANGED: ${oldSettings.calculationMethod.name} → ${settings.calculationMethod.name}")
        }
        
        Log.i(TAG, "   🤲 Asr Madhhab: '${oldSettings.asrMadhhab.displayName}' → '${settings.asrMadhhab.displayName}'")
        if (oldSettings.asrMadhhab != settings.asrMadhhab) {
            Log.i(TAG, "     🔄 CHANGED: ${oldSettings.asrMadhhab.name} → ${settings.asrMadhhab.name}")
        }
        
        Log.i(TAG, "   🌅 Custom Fajr Angle: ${oldSettings.customFajrAngle ?: "default"} → ${settings.customFajrAngle ?: "default"}")
        if (oldSettings.customFajrAngle != settings.customFajrAngle) {
            Log.i(TAG, "     🔄 CHANGED: Custom Fajr angle modified")
        }
        
        Log.i(TAG, "   🌙 Custom Isha Angle: ${oldSettings.customIshaAngle ?: "default"} → ${settings.customIshaAngle ?: "default"}")
        if (oldSettings.customIshaAngle != settings.customIshaAngle) {
            Log.i(TAG, "     🔄 CHANGED: Custom Isha angle modified")
        }
        
        Log.i(TAG, "   ⏰ Custom Isha Delay: ${oldSettings.customIshaDelay ?: "default"} → ${settings.customIshaDelay ?: "default"}")
        if (oldSettings.customIshaDelay != settings.customIshaDelay) {
            Log.i(TAG, "     🔄 CHANGED: Custom Isha delay modified")
        }
        
        Log.i(TAG, "   🧭 High Latitude: ${oldSettings.highLatitudeAdjustment.name} → ${settings.highLatitudeAdjustment.name}")
        if (oldSettings.highLatitudeAdjustment != settings.highLatitudeAdjustment) {
            Log.i(TAG, "     🔄 CHANGED: High latitude adjustment modified")
        }
        
        // ALGORITHM STEP 1: Update calculation_settings_json
        Log.i(TAG, "")
        Log.i(TAG, "💾 ALGORITHM STEP 1: Update calculation_settings_json")
        val saveStart = System.currentTimeMillis()
        saveCalculationSettings(settings)
        val saveTime = System.currentTimeMillis() - saveStart
        Log.i(TAG, "✅ STEP 1 COMPLETE: Settings saved to preferences (${saveTime}ms)")
        Log.i(TAG, "   📄 Storage: JSON format in SharedPreferences")
        Log.i(TAG, "   🔑 Key: calculation_settings_json")

        // ALGORITHM STEP 1b: Also update cached_prayer_settings for Prayer Settings dialog compatibility
        Log.i(TAG, "")
        Log.i(TAG, "💾 ALGORITHM STEP 1b: Update cached_prayer_settings (for Prayer Settings dialog)")
        val cacheStart = System.currentTimeMillis()

        // Get current cached settings and update only the calculation part
        val currentCached = getCachedPrayerSettings()
        if (currentCached != null) {
            val updatedCached = currentCached.copy(
                calculationMethod = settings.calculationMethod,
                asrMadhhab = settings.asrMadhhab,
                highLatitudeAdjustment = settings.highLatitudeAdjustment,
                customFajrAngle = settings.customFajrAngle,
                customIshaAngle = settings.customIshaAngle,
                customIshaDelay = settings.customIshaDelay,
                customMaghribOffset = settings.customMaghribOffset,
                timeOffsets = settings.timeOffsets
            )
            saveCachedPrayerSettings(updatedCached)
            Log.i(TAG, "✅ STEP 1b COMPLETE: Cached settings updated (${System.currentTimeMillis() - cacheStart}ms)")
        } else {
            Log.w(TAG, "⚠️ STEP 1b SKIPPED: No cached settings found to update")
        }

        // Update reactive flows
        Log.i(TAG, "")
        Log.i(TAG, "🔄 REACTIVE FLOW UPDATE: Updating in-memory flows for UI")
        _calculationSettingsFlow.value = settings
        _calculationSettingsFlow.tryEmit(settings)
        updateLegacyCombinedFlow()
        Log.i(TAG, "✅ Reactive flows updated - UI will receive new values")

        // PER-COUNTRY STORE: persist this edit into the current country's SQLite bucket and
        // signal the (dormant) cloud-sync layer. Non-blocking; canonical record for sync.
        persistActiveCountryToStore(settings)
        
        // ALGORITHM STEP 3: After saving changes, immediately recalculate prayer times
        Log.i(TAG, "")
        Log.i(TAG, "⚡ ALGORITHM STEP 3: Immediately recalculate prayer times")
        val recalcStart = System.currentTimeMillis()
        triggerPrayerTimeRecalculation()
        val recalcTime = System.currentTimeMillis() - recalcStart
        Log.i(TAG, "✅ STEP 3 COMPLETE: Prayer time recalculation triggered (${recalcTime}ms)")
        
        // Check restore option logic
        Log.i(TAG, "")
        Log.i(TAG, "🔄 RESTORE OPTION LOGIC: Checking if restore button should be shown")
        val restoreStart = System.currentTimeMillis()
        val shouldShowRestore = shouldShowRestoreOption()
        val restoreTime = System.currentTimeMillis() - restoreStart
        Log.i(TAG, "🎯 RESTORE DECISION: ${if (shouldShowRestore) "SHOW restore button" else "HIDE restore button"} (${restoreTime}ms)")
        
        val totalTime = System.currentTimeMillis() - startTime
        Log.i(TAG, "")
        Log.i(TAG, "🏁 USER CHANGE HANDLING COMPLETE")
        Log.i(TAG, "⏱️ Total processing time: ${totalTime}ms")
        Log.i(TAG, "📊 SUMMARY:")
        Log.i(TAG, "   ✅ Settings updated and persisted")
        Log.i(TAG, "   ✅ Prayer time recalculation triggered")
        Log.i(TAG, "   ✅ Restore option logic evaluated")
        Log.i(TAG, "   ✅ UI will automatically update via reactive flows")
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "")
    }
    
    fun updateLocationPreferences(preferences: PrayerLocationPreferences, forceCommit: Boolean = false) {
        Log.i(TAG, "📝 LOCATION PREFERENCES UPDATE - Starting process")
        
        saveLocationPreferences(preferences)
        _locationPreferencesFlow.value = preferences
        _locationPreferencesFlow.tryEmit(preferences)
        updateLegacyCombinedFlow()
        // Resolve the country only after the reactive location has been updated. Calling this from
        // saveLocationPreferences read the previous location and could bypass/contradict consent.
        if (preferences.location != null) reinitializeWithLocation()
        triggerPrayerTimeRecalculation()
        
        Log.i(TAG, "✅ LOCATION PREFERENCES UPDATE COMPLETE")
    }
    
    fun updateNotificationPreferences(preferences: PrayerNotificationPreferences, forceCommit: Boolean = false) {
        Log.i(TAG, "📝 NOTIFICATION PREFERENCES UPDATE - Starting process")

        saveNotificationPreferences(preferences)
        _notificationPreferencesFlow.value = preferences
        _notificationPreferencesFlow.tryEmit(preferences)
        updateLegacyCombinedFlow()

        // Mirror notification prefs into the synced store (global, not per-country) so they back up.
        persistNotificationPrefsToStore(preferences)

        Log.i(TAG, "✅ NOTIFICATION PREFERENCES UPDATE COMPLETE")
    }

    /** Persist the global notification preferences JSON into the per-user store and signal sync. */
    private fun persistNotificationPrefsToStore(prefs: PrayerNotificationPreferences) {
        storeScope.launch {
            try {
                userSettingsStore.putMeta(
                    UserSettingsStore.KEY_NOTIFICATION_PREFS_JSON,
                    json.encodeToString(prefs),
                )
                userSettingsStore.markLocalChange()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to persist notification prefs to store", e)
            }
        }
    }

    /**
     * Apply notification preferences from the per-user store onto the live SharedPreferences + flow.
     * Called after a cloud pull/merge so restored prefs take effect. Does NOT re-signal sync.
     */
    suspend fun applyNotificationPrefsFromStore() {
        val jsonStr = userSettingsStore.getMeta(UserSettingsStore.KEY_NOTIFICATION_PREFS_JSON) ?: return
        val prefs = runCatching {
            json.decodeFromString<PrayerNotificationPreferences>(jsonStr)
        }.getOrNull() ?: return
        saveNotificationPreferences(prefs)
        _notificationPreferencesFlow.value = prefs
        _notificationPreferencesFlow.tryEmit(prefs)
        updateLegacyCombinedFlow()
        Log.i(TAG, "🔔 Applied notification prefs from store (cloud restore)")
    }

    /**
     * TOGGLE PER-PRAYER NOTIFICATION - Enable/disable notifications for individual prayers
     *
     * This allows users to control notifications for each prayer independently.
     *
     * @param prayerName Name of the prayer (Fajr, Dhuhr, Asr, Maghrib, Isha)
     * @param enabled Whether to enable or disable notification for this prayer
     */
    fun togglePrayerNotification(prayerName: String, enabled: Boolean) {
        Log.i(TAG, "🔔 TOGGLE PRAYER NOTIFICATION: $prayerName -> $enabled")

        val currentPrefs = _notificationPreferencesFlow.value
        val updatedPrefs = when (prayerName.lowercase()) {
            "fajr" -> currentPrefs.copy(fajrNotificationEnabled = enabled)
            "dhuhr" -> currentPrefs.copy(dhuhrNotificationEnabled = enabled)
            "asr" -> currentPrefs.copy(asrNotificationEnabled = enabled)
            "maghrib" -> currentPrefs.copy(maghribNotificationEnabled = enabled)
            "isha" -> currentPrefs.copy(ishaNotificationEnabled = enabled)
            else -> {
                Log.w(TAG, "⚠️ Unknown prayer name: $prayerName")
                return
            }
        }

        updateNotificationPreferences(updatedPrefs)
        Log.i(TAG, "✅ $prayerName notification ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * LEGACY UPDATE SETTINGS - For backward compatibility
     * NOTE: This method only updates calculation and location settings.
     * It does NOT touch notification preferences to prevent resetting them.
     *
     * @deprecated Use updateCalculationSettings(), updateLocationPreferences(), and updateNotificationPreferences() instead
     */
    @Deprecated("Use separate preference update methods instead")
    fun updateSettings(settings: PrayerSettings, forceCommit: Boolean = false) {
        Log.i(TAG, "")
        Log.i(TAG, "📝 REPOSITORY UPDATE SETTINGS OPERATION (NOTIFICATION-SAFE)")
        Log.i(TAG, "=".repeat(80))
        Log.i(TAG, "🔄 Processing user settings update from UI...")
        val startTime = System.currentTimeMillis()

        Log.i(TAG, "📊 Settings to save:")
        Log.i(TAG, "  - Calculation Method: ${settings.calculationMethod.name}")
        Log.i(TAG, "  - Asr Madhhab: ${settings.asrMadhhab.name}")
        Log.i(TAG, "  - Custom Fajr Angle: ${settings.customFajrAngle}°")
        Log.i(TAG, "  - Custom Isha Angle: ${settings.customIshaAngle}°")
        Log.i(TAG, "  - Force Commit: $forceCommit")

        // Use safe conversion methods that don't touch notifications
        val calculation = settings.toCalculationSettings()
        val location = settings.toLocationPreferences()

        Log.i(TAG, "💾 Saving calculation settings...")
        updateCalculationSettings(calculation, forceCommit)

        Log.i(TAG, "💾 Saving location preferences...")
        updateLocationPreferences(location, forceCommit)

        // NOTE: NOT updating notification preferences to prevent resetting them!
        Log.i(TAG, "⏭️ Skipping notification preferences (preserving existing settings)")

        // ALGORITHM: Also save to cached_prayer_settings for restore comparison
        Log.i(TAG, "")
        Log.i(TAG, "💾 ALGORITHM STEP: Save to cached_prayer_settings (for restore logic)")
        val cacheStartTime = System.currentTimeMillis()
        saveCachedPrayerSettings(settings)
        val cacheTime = System.currentTimeMillis() - cacheStartTime
        Log.i(TAG, "✅ Cached prayer settings saved successfully (${cacheTime}ms)")

        val totalTime = System.currentTimeMillis() - startTime
        Log.i(TAG, "")
        Log.i(TAG, "✅ SETTINGS UPDATE OPERATION COMPLETED")
        Log.i(TAG, "⏱️ Total operation time: ${totalTime}ms")
        Log.i(TAG, "📊 OPERATION SUMMARY:")
        Log.i(TAG, "   ✅ Calculation settings saved")
        Log.i(TAG, "   ✅ Location preferences saved")
        Log.i(TAG, "   ⏭️ Notification preferences preserved (not touched)")
        Log.i(TAG, "   ✅ Cached prayer settings updated")
        Log.i(TAG, "=".repeat(80))
        Log.i(TAG, "")
    }
    
    /**
     * INDIVIDUAL SETTING UPDATES - Using new separate preference system
     */
    fun updateCalculationMethod(method: CalculationMethod) {
        val updated = getCalculationSettings().copy(calculationMethod = method)
        updateCalculationSettings(updated, forceCommit = true)
    }
    
    fun updateAsrMadhhab(madhhab: AsrMadhhab) {
        val updated = getCalculationSettings().copy(asrMadhhab = madhhab)
        updateCalculationSettings(updated, forceCommit = true)
    }
    
    fun updateHighLatitudeAdjustment(adjustment: HighLatitudeAdjustment) {
        val updated = getCalculationSettings().copy(highLatitudeAdjustment = adjustment)
        updateCalculationSettings(updated, forceCommit = true)
    }
    
    suspend fun updateTimeOffsets(offsets: PrayerTimeOffsets) {
        Log.d(TAG, "")
        Log.d(TAG, "⏰ PRAYER TIME OFFSETS UPDATE")
        Log.d(TAG, "=".repeat(50))
        Log.d(TAG, "📝 REQUEST: Updating time offsets")
        Log.d(TAG, "   🌅 Fajr: ${offsets.fajr} minutes")
        Log.d(TAG, "   🌄 Sunrise: ${offsets.sunrise} minutes")
        Log.d(TAG, "   🌞 Dhuhr: ${offsets.dhuhr} minutes")
        Log.d(TAG, "   🌇 Asr: ${offsets.asr} minutes")
        Log.d(TAG, "   🌆 Maghrib: ${offsets.maghrib} minutes")
        Log.d(TAG, "   🌙 Isha: ${offsets.isha} minutes")
        
        val current = getLoadedCalculationSettings()
        Log.d(TAG, "📖 CURRENT OFFSETS (before update):")
        Log.d(TAG, "   🌅 Fajr: ${current.timeOffsets.fajr} → ${offsets.fajr}")
        Log.d(TAG, "   🌄 Sunrise: ${current.timeOffsets.sunrise} → ${offsets.sunrise}")
        Log.d(TAG, "   🌞 Dhuhr: ${current.timeOffsets.dhuhr} → ${offsets.dhuhr}")
        Log.d(TAG, "   🌇 Asr: ${current.timeOffsets.asr} → ${offsets.asr}")
        Log.d(TAG, "   🌆 Maghrib: ${current.timeOffsets.maghrib} → ${offsets.maghrib}")
        Log.d(TAG, "   🌙 Isha: ${current.timeOffsets.isha} → ${offsets.isha}")
        
        val updated = current.copy(timeOffsets = offsets)
        Log.d(TAG, "💾 SAVING: Calling updateCalculationSettings with forceCommit=true")
        updateCalculationSettings(updated, forceCommit = true)
        Log.d(TAG, "✅ SAVE COMPLETE: Time offsets updated successfully")
        
        // Verify the update
        val verifyOffsets = getCalculationSettings().timeOffsets
        Log.d(TAG, "🔍 VERIFICATION: Reading back saved offsets")
        Log.d(TAG, "   🌅 Fajr: ${verifyOffsets.fajr} (expected: ${offsets.fajr})")
        Log.d(TAG, "   🌄 Sunrise: ${verifyOffsets.sunrise} (expected: ${offsets.sunrise})")
        Log.d(TAG, "   🌞 Dhuhr: ${verifyOffsets.dhuhr} (expected: ${offsets.dhuhr})")
        Log.d(TAG, "   🌇 Asr: ${verifyOffsets.asr} (expected: ${offsets.asr})")
        Log.d(TAG, "   🌆 Maghrib: ${verifyOffsets.maghrib} (expected: ${offsets.maghrib})")
        Log.d(TAG, "   🌙 Isha: ${verifyOffsets.isha} (expected: ${offsets.isha})")
        
        val allMatch = verifyOffsets.fajr == offsets.fajr &&
                      verifyOffsets.sunrise == offsets.sunrise &&
                      verifyOffsets.dhuhr == offsets.dhuhr &&
                      verifyOffsets.asr == offsets.asr &&
                      verifyOffsets.maghrib == offsets.maghrib &&
                      verifyOffsets.isha == offsets.isha
        
        if (allMatch) {
            Log.i(TAG, "✅ VERIFICATION SUCCESS: All offsets saved correctly")
        } else {
            Log.e(TAG, "❌ VERIFICATION FAILED: Some offsets not saved correctly")
        }
        Log.d(TAG, "=".repeat(50))
    }
    
    /**
     * Get the default (auto-detected) offset for a specific prayer
     * Used by double-tap gesture to reset to country-based default
     *
     * @param prayerName Name of the prayer (Fajr, Dhuhr, Asr, Maghrib, Isha, Sunrise)
     * @return The auto-detected default offset for this prayer, or 0 if not available
     */
    fun getDefaultPrayerOffset(prayerName: String): Int {
        Log.d(TAG, "🔍 GET DEFAULT OFFSET: Retrieving auto-detected offset for $prayerName")

        // Get the cached country code
        val cachedCountry = getCachedCountry()
        if (cachedCountry == null) {
            Log.w(TAG, "⚠️ No country code available - returning 0 as default offset")
            return 0
        }

        // Get auto-detected settings for the country
        val autoDetectedSettings = getAutoDetectedSettingsForCountry(cachedCountry)
        if (autoDetectedSettings == null) {
            Log.w(TAG, "⚠️ No auto-detected settings for country '$cachedCountry' - returning 0 as default offset")
            return 0
        }

        // Get the offset for the specific prayer
        val defaultOffset = when (prayerName.lowercase()) {
            "fajr" -> autoDetectedSettings.timeOffsets.fajr
            "sunrise" -> autoDetectedSettings.timeOffsets.sunrise
            "dhuhr" -> autoDetectedSettings.timeOffsets.dhuhr
            "asr" -> autoDetectedSettings.timeOffsets.asr
            "maghrib" -> autoDetectedSettings.timeOffsets.maghrib
            "isha" -> autoDetectedSettings.timeOffsets.isha
            else -> {
                Log.w(TAG, "⚠️ Unknown prayer name: $prayerName - returning 0 as default offset")
                0
            }
        }

        Log.d(TAG, "✅ DEFAULT OFFSET for $prayerName: $defaultOffset minutes (from country: $cachedCountry)")
        return defaultOffset
    }

    /**
     * Update offset for a single prayer (used by Interactive Prayer Dial)
     */
    suspend fun updateSinglePrayerOffset(prayerName: String, offsetMinutes: Int) {
        Log.d(TAG, "")
        Log.d(TAG, "🎯 SINGLE PRAYER OFFSET UPDATE")
        Log.d(TAG, "=".repeat(50))
        Log.d(TAG, "📝 INTERACTIVE DIAL REQUEST:")
        Log.d(TAG, "   🔤 Prayer Name: $prayerName")
        Log.d(TAG, "   ⏱️ Offset: $offsetMinutes minutes")
        
        val current = getLoadedCalculationSettings()
        val currentOffsets = current.timeOffsets
        
        Log.d(TAG, "📖 CURRENT OFFSETS (before update):")
        Log.d(TAG, "   🌅 Fajr: ${currentOffsets.fajr}")
        Log.d(TAG, "   🌄 Sunrise: ${currentOffsets.sunrise}")
        Log.d(TAG, "   🌞 Dhuhr: ${currentOffsets.dhuhr}")
        Log.d(TAG, "   🌇 Asr: ${currentOffsets.asr}")
        Log.d(TAG, "   🌆 Maghrib: ${currentOffsets.maghrib}")
        Log.d(TAG, "   🌙 Isha: ${currentOffsets.isha}")
        
        val newOffsets = when (prayerName.lowercase()) {
            "fajr" -> {
                Log.d(TAG, "🌅 UPDATING Fajr: ${currentOffsets.fajr} → $offsetMinutes")
                currentOffsets.copy(fajr = offsetMinutes)
            }
            "sunrise" -> {
                Log.d(TAG, "🌄 UPDATING Sunrise: ${currentOffsets.sunrise} → $offsetMinutes")
                currentOffsets.copy(sunrise = offsetMinutes)
            }
            "dhuhr" -> {
                Log.d(TAG, "🌞 UPDATING Dhuhr: ${currentOffsets.dhuhr} → $offsetMinutes")
                currentOffsets.copy(dhuhr = offsetMinutes)
            }
            "asr" -> {
                Log.d(TAG, "🌇 UPDATING Asr: ${currentOffsets.asr} → $offsetMinutes")
                currentOffsets.copy(asr = offsetMinutes)
            }
            "maghrib" -> {
                Log.d(TAG, "🌆 UPDATING Maghrib: ${currentOffsets.maghrib} → $offsetMinutes")
                currentOffsets.copy(maghrib = offsetMinutes)
            }
            "isha" -> {
                Log.d(TAG, "🌙 UPDATING Isha: ${currentOffsets.isha} → $offsetMinutes")
                currentOffsets.copy(isha = offsetMinutes)
            }
            else -> {
                Log.w(TAG, "⚠️ UNKNOWN PRAYER NAME: $prayerName - no update performed")
                currentOffsets
            }
        }
        
        if (newOffsets != currentOffsets) {
            Log.d(TAG, "💾 SAVING: Updated offsets via updateTimeOffsets()")
            updateTimeOffsets(newOffsets)
            Log.i(TAG, "✅ SINGLE PRAYER OFFSET UPDATE COMPLETE: $prayerName = $offsetMinutes minutes")
        } else {
            Log.w(TAG, "⚠️ NO CHANGES: Prayer name '$prayerName' not recognized")
        }
        Log.d(TAG, "=".repeat(50))
    }
    
    fun updateLocationSettings(useGps: Boolean, location: Location? = null) {
        val current = getLocationPreferences()
        val updated = current.copy(
            useGpsLocation = useGps,
            location = location ?: current.location
        )
        updateLocationPreferences(updated, forceCommit = true)
        Log.i(TAG, "🔔 latthi mar!")
    }
    
    fun updateNotificationSound(sound: String) {
        val updated = getNotificationPreferences().copy(notificationSound = sound)
        updateNotificationPreferences(updated, forceCommit = true)
    }
    
    fun updateVibrationEnabled(enabled: Boolean) {
        val updated = getNotificationPreferences().copy(vibrationEnabled = enabled)
        updateNotificationPreferences(updated, forceCommit = true)
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
        Log.i(TAG, "")
        Log.i(TAG, "🔔 NOTIFICATION SETTINGS UPDATE")
        Log.i(TAG, "=".repeat(60))
        
        logPrefWrite(KEY_NOTIFICATIONS_ENABLED, enabled)
        logPrefWrite(KEY_NOTIFY_BEFORE_MINUTES, beforeMinutes)
        
        prefs.edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
            .putInt(KEY_NOTIFY_BEFORE_MINUTES, beforeMinutes)
            .apply()
            
        // Verify the writes were successful
        verifyPrefWrite(KEY_NOTIFICATIONS_ENABLED, enabled, "notification enabled flag")
        verifyPrefWrite(KEY_NOTIFY_BEFORE_MINUTES, beforeMinutes, "notification timing")
        
        Log.i(TAG, "✅ NOTIFICATION SETTINGS: Update completed successfully")
        Log.i(TAG, "")
    }
    
    /**
     * NOTIFICATION SETTINGS GETTER: Retrieves current notification preferences
     * 
     * These functions provide access to current notification settings without
     * loading the full PrayerSettings object.
     * 
     * @return Current notification preferences
     */
    fun isNotificationsEnabled(): Boolean {
        val value = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        logPrefRead(KEY_NOTIFICATIONS_ENABLED, value, true)
        return value
    }
    
    fun getNotifyBeforeMinutes(): Int {
        val value = prefs.getInt(KEY_NOTIFY_BEFORE_MINUTES, 10)
        logPrefRead(KEY_NOTIFY_BEFORE_MINUTES, value, 10)
        return value
    }
    
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
            logPrefReadJson(KEY_CURRENT_SETTINGS_JSON, settingsJson, "legacy combined settings")
            
            if (settingsJson != null) {
                android.util.Log.w("PrayerSettingsRepository", "Parsing cached JSON:")
                android.util.Log.w("PrayerSettingsRepository", "🔥 JSON CONTENT:")
                // Pretty-print the JSON for better readability
                try {
                    val prettyJson = Json { prettyPrint = true }.encodeToString(
                        Json.parseToJsonElement(settingsJson)
                    )
                    prettyJson.lines().forEach { line ->
                        android.util.Log.w("PrayerSettingsRepository", line)
                    }
                } catch (e: Exception) {
                    // Fallback to original format if pretty-printing fails
                    android.util.Log.w("PrayerSettingsRepository", settingsJson)
                }
                
                val settings = json.decodeFromString<PrayerSettings>(settingsJson)
                android.util.Log.w("PrayerSettingsRepository", "🔥 Settings loaded from JSON:")
                android.util.Log.w("PrayerSettingsRepository", "  Calculation Method: ${settings.calculationMethod.name}")
                android.util.Log.w("PrayerSettingsRepository", "  ASR Madhhab: ${settings.asrMadhhab.name}")
                android.util.Log.w("PrayerSettingsRepository", "  Custom Isha Angle: ${settings.customIshaAngle}")
                android.util.Log.w("PrayerSettingsRepository", "  Use GPS: ${settings.useGpsLocation}")
                android.util.Log.w("PrayerSettingsRepository", "  Use GPS: ${settings.useGpsLocation}")
                settings
            } else {
                android.util.Log.w("PrayerSettingsRepository", "🔥 No JSON settings found, running initialization algorithm")
                android.util.Log.w("PrayerSettingsRepository", "🚀 TRIGGERING AUTO-DETECTION FOR FIRST-TIME SETUP")
                initializeSettings()
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
        Log.i(TAG, "🔍 CACHED COUNTRY DEBUG:")

        // The current location is the detected country. Legacy auto-detection metadata describes
        // the settings currently in use, so reading it first masks a newly selected country.
        val currentLocation = getLocationPreferences().location
        val countryCode = currentLocation?.countryCode
        val countryName = currentLocation?.country

        Log.i(TAG, "   - Has location: ${currentLocation != null}")
        if (currentLocation != null) {
            Log.i(TAG, "   - Location city: ${currentLocation.city}")
            Log.i(TAG, "   - Location country: ${currentLocation.country}")
            Log.i(TAG, "   - Location country code: ${currentLocation.countryCode}")
            Log.i(TAG, "   - Location display: ${currentLocation.getDisplayName()}")
            Log.i(TAG, "   - Location lat/lng: ${currentLocation.latitude}, ${currentLocation.longitude}")
            Log.i(TAG, "   - Location timezone: ${currentLocation.timeZoneOffset}")
        }

        // Prefer country code from geocoding API, fallback to country name mapping
        val result = if (!countryCode.isNullOrEmpty()) {
            Log.i(TAG, "   ✅ PRIORITY 2: Using country code from location geocoding: $countryCode")
            countryCode
        } else if (!countryName.isNullOrEmpty()) {
            Log.i(TAG, "   - No country code available, trying to map country name: $countryName")
            Log.i(TAG, "   🔧 USING COUNTRY CODE MAPPER FOR AUTO-DETECTION:")

            // Use CountryCodeMapper to resolve country name to ISO code
            val mappedCode = com.starception.submission.prayer.service.CountryCodeMapper.resolveCountryCode(
                null, // no geocoder code
                countryName // use country name for mapping
            )

            if (mappedCode.isNotEmpty()) {
                Log.i(TAG, "   ✅ MAPPED COUNTRY NAME: '$countryName' → '$mappedCode'")
                Log.i(TAG, "   🎯 SUCCESS: Auto-detection can proceed with country code '$mappedCode'")
                mappedCode
            } else {
                Log.w(TAG, "   ❌ MAPPING FAILED: Country name '$countryName' not found in mapper")
                Log.w(TAG, "   💡 Consider adding mapping for this country name")
                null
            }
        } else {
            Log.w(TAG, "   ❌ No country code or country name available from location")
            null
        }

        Log.i(TAG, "   - Returning country code: $result")
        if (!result.isNullOrEmpty()) return result

        // Backward-compatible fallback for installs whose saved location predates country codes.
        val cachedSettings = getCachedPrayerSettings()
        val autoDetectedCode = cachedSettings?.autoDetectedCountryCode
        if (!autoDetectedCode.isNullOrEmpty()) {
            Log.i(TAG, "   ✅ FALLBACK: Using legacy auto-detected code: $autoDetectedCode")
            return autoDetectedCode
        }
        return null
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
            Log.i(TAG, "   📄 Full JSON Entry:")
            try {
                val prettyJson = Json { prettyPrint = true }.encodeToString(countryEntry)
                prettyJson.lines().forEach { line ->
                    Log.i(TAG, "   $line")
                }
            } catch (e: Exception) {
                Log.i(TAG, "   ${countryEntry.toString()}")
            }
            
            // 4. Map calculation method
            val calculationMethod = when (methodName) {
                "Muslim_World_League" -> CalculationMethod.MUSLIM_WORLD_LEAGUE
                "Umm_al_Qura_University_Makkah" -> CalculationMethod.UMM_AL_QURA
                "UAE_IACAD" -> CalculationMethod.UAE_IACAD
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

            // 6. Extract calculation parameters with method defaults, then override if country specifies
            // IMPORTANT: Angles go to root, Offsets go to timeOffsets

            // Step 1: Get calculation method defaults from JSON
            // Load the calculation method details from JSON to get precise values (e.g., maghribOffset: 4.5)
            val calculationMethodEntry = jsonData["calculationMethods"]?.jsonObject?.get(methodName)?.jsonObject
            val methodFajrAngle = calculationMethodEntry?.get("fajrAngle")?.jsonPrimitive?.doubleOrNull ?: calculationMethod.fajrAngle
            val methodIshaAngle = calculationMethodEntry?.get("ishaAngle")?.jsonPrimitive?.doubleOrNull
            val methodMaghribOffset = calculationMethodEntry?.get("maghribOffset")?.jsonPrimitive?.doubleOrNull?.toInt() ?: calculationMethod.maghribOffset
            val methodIshaOffset = calculationMethodEntry?.get("ishaOffset")?.jsonPrimitive?.intOrNull ?: calculationMethod.ishaDelay  // This is actually a delay, not an angle

            Log.i(TAG, "📐 Calculation Method Defaults:")
            Log.i(TAG, "   - Fajr angle: $methodFajrAngle°")
            Log.i(TAG, "   - Isha angle: $methodIshaAngle°")
            Log.i(TAG, "   - Maghrib offset: $methodMaghribOffset min")
            Log.i(TAG, "   - Isha offset: $methodIshaOffset min")

            // Step 2: Override ANGLES with country-specific values if present
            // NOTE: Treat 0.0 as "use delay-based calculation" (null) for Isha
            val customFajrAngle = countryEntry["customFajrAngle"]?.jsonPrimitive?.doubleOrNull ?: methodFajrAngle
            val customIshaAngle = countryEntry["customIshaAngle"]?.jsonPrimitive?.doubleOrNull?.takeIf { it != 0.0 }
                ?: methodIshaAngle?.takeIf { it != 0.0 }

            // Check if country overrode angle values
            val fajrOverridden = countryEntry["customFajrAngle"] != null
            val ishaAngleOverridden = countryEntry["customIshaAngle"] != null

            // Step 3: Extract custom maghrib offset from JSON (if country overrides it)
            val customMaghribOffset = countryEntry["maghribOffset"]?.jsonPrimitive?.doubleOrNull?.toInt()
                ?: methodMaghribOffset

            // Step 4: Extract time offsets (user adjustments ONLY - NOT calculation parameters)
            val timeOffsetsJson = countryEntry["timeOffsets"]?.jsonObject
            val countryTimeOffsets = PrayerTimeOffsets(
                fajr = timeOffsetsJson?.get("fajr")?.jsonPrimitive?.intOrNull ?: 0,
                sunrise = timeOffsetsJson?.get("sunrise")?.jsonPrimitive?.intOrNull ?: 0,
                dhuhr = timeOffsetsJson?.get("dhuhr")?.jsonPrimitive?.intOrNull ?: 0,
                asr = timeOffsetsJson?.get("asr")?.jsonPrimitive?.intOrNull ?: 0,
                maghrib = timeOffsetsJson?.get("maghrib")?.jsonPrimitive?.intOrNull ?: 0,  // User adjustments ONLY
                isha = timeOffsetsJson?.get("isha")?.jsonPrimitive?.intOrNull ?: 0  // User adjustments ONLY
            )

            // Log calculation angles (show which were overridden by country)
            if (fajrOverridden || ishaAngleOverridden) {
                Log.i(TAG, "⚙️ COUNTRY-SPECIFIC ANGLE OVERRIDES found:")
                if (fajrOverridden) Log.i(TAG, "   - Fajr angle: $customFajrAngle° (OVERRIDDEN from $methodFajrAngle°)")
                else Log.i(TAG, "   - Fajr angle: $customFajrAngle° (from method)")

                if (ishaAngleOverridden) Log.i(TAG, "   - Isha angle: $customIshaAngle° (OVERRIDDEN from $methodIshaAngle°)")
                else Log.i(TAG, "   - Isha angle: $customIshaAngle° (from method)")
            } else {
                Log.i(TAG, "⚙️ CALCULATION ANGLES: Using calculation method defaults")
                Log.i(TAG, "   - Fajr angle: $customFajrAngle° (from method)")
                Log.i(TAG, "   - Isha angle: $customIshaAngle° (from method)")
            }

            // Log time offsets (show which values came from where)
            val hasNonZeroOffsets = countryTimeOffsets.fajr != 0 || countryTimeOffsets.sunrise != 0 ||
                                    countryTimeOffsets.dhuhr != 0 || countryTimeOffsets.asr != 0 ||
                                    countryTimeOffsets.maghrib != 0 || countryTimeOffsets.isha != 0

            if (hasNonZeroOffsets) {
                Log.i(TAG, "⚙️ TIME OFFSETS (from method offsets + country adjustments):")
                if (countryTimeOffsets.fajr != 0) Log.i(TAG, "   - Fajr: ${countryTimeOffsets.fajr} min")
                if (countryTimeOffsets.sunrise != 0) Log.i(TAG, "   - Sunrise: ${countryTimeOffsets.sunrise} min")
                if (countryTimeOffsets.dhuhr != 0) Log.i(TAG, "   - Dhuhr: ${countryTimeOffsets.dhuhr} min")
                if (countryTimeOffsets.asr != 0) Log.i(TAG, "   - Asr: ${countryTimeOffsets.asr} min")
                if (countryTimeOffsets.maghrib != 0) Log.i(TAG, "   - Maghrib: ${countryTimeOffsets.maghrib} min (from method)")
                if (countryTimeOffsets.isha != 0) Log.i(TAG, "   - Isha: ${countryTimeOffsets.isha} min (from method)")
            } else {
                Log.i(TAG, "⚙️ TIME OFFSETS: All zero")
            }
            
            // 8. Create prayer settings with country-specific overrides
            // IMPORTANT: Calculation parameters (angles, delays, offsets) stored as custom* fields
            //            User adjustments stored in timeOffsets
            val autoDetectedSettings = PrayerSettings(
                calculationMethod = calculationMethod,
                asrMadhhab = asrMadhhab,
                customFajrAngle = customFajrAngle,
                customIshaAngle = customIshaAngle,
                customIshaDelay = methodIshaOffset,  // Use method's ishaDelay (e.g., 90 for Umm al-Qura)
                customMaghribOffset = customMaghribOffset,  // Use method's/country's maghribOffset (e.g., 4 for Iran)
                // Time offsets include only user adjustments (NOT method parameters)
                timeOffsets = countryTimeOffsets,
                // Set auto-detection metadata for restore button functionality
                isMethodAutoDetected = true,
                isMadhhabAutoDetected = true,
                areCustomAnglesAutoDetected = (fajrOverridden || ishaAngleOverridden),
                autoDetectedCountryName = countryName,
                autoDetectedCountryCode = countryCode
            )
            
            val totalTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "✅ AUTO-DETECTION COMPLETE (${totalTime}ms total)")
            Log.i(TAG, "📊 FINAL AUTO-DETECTED SETTINGS FOR $countryName:")
            Log.i(TAG, "   🕌 Calculation Method: ${autoDetectedSettings.calculationMethod.name} (${autoDetectedSettings.calculationMethod.displayName})")
            Log.i(TAG, "   🤲 Asr Madhhab: ${autoDetectedSettings.asrMadhhab.name} (${autoDetectedSettings.asrMadhhab.displayName})")
            Log.i(TAG, "   🌅 Fajr Angle: ${autoDetectedSettings.customFajrAngle}° ${if (fajrOverridden) "(country override)" else "(from method)"}")
            Log.i(TAG, "   🌙 Isha Angle: ${autoDetectedSettings.customIshaAngle}° ${if (ishaAngleOverridden) "(country override)" else "(from method)"}")
            Log.i(TAG, "   🌆 Maghrib Offset: ${autoDetectedSettings.customMaghribOffset}min (calculation parameter)")
            Log.i(TAG, "   ⏰ Isha Delay: ${autoDetectedSettings.customIshaDelay}min (calculation parameter)")
            Log.i(TAG, "   ⏱️ User Time Offsets: Fajr=${autoDetectedSettings.timeOffsets.fajr}, Sunrise=${autoDetectedSettings.timeOffsets.sunrise}, Dhuhr=${autoDetectedSettings.timeOffsets.dhuhr}, Asr=${autoDetectedSettings.timeOffsets.asr}, Maghrib=${autoDetectedSettings.timeOffsets.maghrib}, Isha=${autoDetectedSettings.timeOffsets.isha}")
            Log.i(TAG, "   🧭 High Latitude Method: ${autoDetectedSettings.highLatitudeAdjustment.name}")
            Log.i(TAG, "   📍 Use GPS: ${autoDetectedSettings.useGpsLocation} (default)")
            Log.i(TAG, "   🌍 Detected for Country: $countryName ($countryCode)")
            Log.i(TAG, "   📄 Generated Settings JSON:")
            Log.i(TAG, "   🔥 JSON CONTENT:")
            // Pretty-print the JSON for better readability
            try {
                val prettyJson = Json { prettyPrint = true }.encodeToString(autoDetectedSettings)
                prettyJson.lines().forEach { line ->
                    Log.i(TAG, "   $line")
                }
            } catch (e: Exception) {
                // Fallback to original format if pretty-printing fails
                val fullJson = json.encodeToString(autoDetectedSettings)
                Log.i(TAG, "   $fullJson")
            }
            
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
        Log.i(TAG, "📋 PREFERENCE READ: key='$KEY_CURRENT_SETTINGS_JSON' (legacy combined settings)")
        Log.i(TAG, "📋 Raw cached JSON: ${if (cachedJson != null) "EXISTS (${cachedJson.length} chars)" else "NULL"}")
        
        return if (cachedJson != null) {
            try {
                Log.i(TAG, "📋 Parsing cached JSON:")
                try {
                    val prettyJson = Json { prettyPrint = true }.encodeToString(
                        Json.parseToJsonElement(cachedJson)
                    )
                    prettyJson.lines().forEach { line ->
                        Log.i(TAG, line)
                    }
                } catch (e: Exception) {
                    Log.i(TAG, cachedJson)
                }
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
                Log.i(TAG, "   🕌 Method: ${cachedSettings.calculationMethod.displayName}")
                Log.i(TAG, "   🤲 Madhhab: ${cachedSettings.asrMadhhab.displayName}")
                Log.i(TAG, "   📍 Location: ${cachedSettings.location?.getDisplayName() ?: "GPS"}")
                
                cachedSettings
            } catch (e: Exception) {
                Log.e(TAG, "❌ CACHE PARSING FAILED: ${e.message}")
                Log.e(TAG, "   Error details: ${e.javaClass.simpleName}")
                Log.e(TAG, "   Problematic JSON:")
                try {
                    val prettyJson = Json { prettyPrint = true }.encodeToString(
                        Json.parseToJsonElement(cachedJson)
                    )
                    prettyJson.lines().forEach { line ->
                        Log.e(TAG, "   $line")
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "   $cachedJson")
                }
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
        Log.i(TAG, "💾 Generated JSON (${settingsJson.length} chars):")
        try {
            val prettyJson = Json { prettyPrint = true }.encodeToString(
                Json.parseToJsonElement(settingsJson)
            )
            prettyJson.lines().forEach { line ->
                Log.i(TAG, line)
            }
        } catch (e: Exception) {
            Log.i(TAG, settingsJson)
        }
        
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
        Log.i(TAG, "   🕌 Method: ${settings.calculationMethod.displayName}")
        Log.i(TAG, "   🤲 Madhhab: ${settings.asrMadhhab.displayName}")
        Log.i(TAG, "   📋 Settings saved successfully")
        
        logPrefWriteJson(KEY_CURRENT_SETTINGS_JSON, settingsJson, "legacy combined settings")
        prefs.edit().putString(KEY_CURRENT_SETTINGS_JSON, settingsJson).apply()
        
        // Verify it was saved
        val verifyJson = prefs.getString(KEY_CURRENT_SETTINGS_JSON, null)
        logPrefReadJson(KEY_CURRENT_SETTINGS_JSON, verifyJson, "verification after save")
        if (verifyJson != null && verifyJson == settingsJson) {
            Log.i(TAG, "✅ CACHE SAVE VERIFIED: Settings successfully saved to preferences")
        } else {
            Log.e(TAG, "❌ CACHE SAVE FAILED: Verification failed")
            Log.e(TAG, "   Expected length: ${settingsJson.length}")
            Log.e(TAG, "   Actual length: ${verifyJson?.length ?: 0}")
        }
    }
    
    /**
     * PRAYER SETTINGS ALGORITHM IMPLEMENTATION - INITIALIZATION PHASE
     * 
     * Following the specified algorithm:
     * 1. Detect cached country code (if available)
     * 2. Load auto-detected prayer settings for that country from country_prayer_methods.json
     * 3. Load cached_prayer_settings from preferences (if it exists)
     * 4. Populate the UI fields:
     *    - If cached_prayer_settings exists → use it to fill all values
     *    - Else → use auto-detected values + default values for missing fields
     */
    fun initializeSettings(): PrayerSettings {
        Log.i(TAG, "")
        Log.i(TAG, "🎯 PRAYER SETTINGS ALGORITHM - INITIALIZATION PHASE STARTED")
        Log.i(TAG, "=".repeat(80))
        
        // STEP 1: Detect cached country code (if available)
        Log.i(TAG, "📍 ALGORITHM STEP 1: Detect cached country code (if available)")
        val startTime = System.currentTimeMillis()
        val cachedCountry = getCachedCountry()
        val step1Time = System.currentTimeMillis() - startTime
        
        if (cachedCountry != null) {
            Log.i(TAG, "✅ STEP 1 SUCCESS: Found cached country code: '$cachedCountry' (${step1Time}ms)")
        } else {
            Log.w(TAG, "⚠️ STEP 1 RESULT: No cached country code available (${step1Time}ms)")
            Log.w(TAG, "   🔍 This means either:")
            Log.w(TAG, "     - User hasn't set location yet")
            Log.w(TAG, "     - GPS location not acquired")
            Log.w(TAG, "     - Location data doesn't contain country info")
        }
        
        // STEP 2: Load auto-detected prayer settings from country_prayer_methods.json
        Log.i(TAG, "")
        Log.i(TAG, "🗺️ ALGORITHM STEP 2: Load auto-detected settings from country_prayer_methods.json")
        val step2Start = System.currentTimeMillis()
        val autoDetectedSettings = if (cachedCountry != null) {
            Log.i(TAG, "   🔄 Loading auto-detected settings for country: '$cachedCountry'")
            getAutoDetectedSettingsForCountry(cachedCountry)
        } else {
            Log.w(TAG, "   ⏭️ Skipping auto-detection (no country code available)")
            null
        }
        val step2Time = System.currentTimeMillis() - step2Start
        
        if (autoDetectedSettings != null) {
            Log.i(TAG, "✅ STEP 2 SUCCESS: Auto-detected settings loaded for '$cachedCountry' (${step2Time}ms)")
            Log.i(TAG, "   📊 Auto-detected values:")
            Log.i(TAG, "     🕌 Calculation Method: ${autoDetectedSettings.calculationMethod.displayName}")
            Log.i(TAG, "     🤲 Asr Madhhab: ${autoDetectedSettings.asrMadhhab.displayName}")
            Log.i(TAG, "     🌅 Custom Fajr Angle: ${autoDetectedSettings.customFajrAngle ?: "default"}")
            Log.i(TAG, "     🌙 Custom Isha Angle: ${autoDetectedSettings.customIshaAngle ?: "default"}")
            Log.i(TAG, "     ⏰ Custom Isha Delay: ${autoDetectedSettings.customIshaDelay ?: "default"}")
        } else {
            Log.w(TAG, "⚠️ STEP 2 RESULT: No auto-detected settings available (${step2Time}ms)")
            Log.w(TAG, "   💡 Will use system defaults for missing fields")
        }
        
        // STEP 3: Load cached_prayer_settings from preferences (if it exists)
        Log.i(TAG, "")
        Log.i(TAG, "💾 ALGORITHM STEP 3: Load cached_prayer_settings from preferences")
        val step3Start = System.currentTimeMillis()
        val cachedSettings = getCachedPrayerSettings()
        val step3Time = System.currentTimeMillis() - step3Start
        
        if (cachedSettings != null) {
            Log.i(TAG, "✅ STEP 3 SUCCESS: Cached prayer settings found (${step3Time}ms)")
            Log.i(TAG, "   📊 Cached values:")
            Log.i(TAG, "     🕌 Calculation Method: ${cachedSettings.calculationMethod.displayName}")
            Log.i(TAG, "     🤲 Asr Madhhab: ${cachedSettings.asrMadhhab.displayName}")
            Log.i(TAG, "     🌅 Custom Fajr Angle: ${cachedSettings.customFajrAngle ?: "default"}")
            Log.i(TAG, "     🌙 Custom Isha Angle: ${cachedSettings.customIshaAngle ?: "default"}")
            Log.i(TAG, "     ⏰ Custom Isha Delay: ${cachedSettings.customIshaDelay ?: "default"}")
            Log.i(TAG, "     🔧 Time Adjustments: Fajr=${cachedSettings.timeOffsets.fajr}, Dhuhr=${cachedSettings.timeOffsets.dhuhr}, Asr=${cachedSettings.timeOffsets.asr}, Maghrib=${cachedSettings.timeOffsets.maghrib}, Isha=${cachedSettings.timeOffsets.isha}")
            Log.i(TAG, "     📍 Location: ${cachedSettings.location?.getDisplayName() ?: "GPS"}")
            Log.i(TAG, "     🔔 Notifications: ${cachedSettings.notificationsEnabled}")
        } else {
            Log.w(TAG, "⚠️ STEP 3 RESULT: No cached prayer settings found (${step3Time}ms)")
            Log.w(TAG, "   💡 This is expected for first-time users or after app reset")
        }
        
        // STEP 4: Populate the UI fields with priority logic
        Log.i(TAG, "")
        Log.i(TAG, "🎨 ALGORITHM STEP 4: Populate UI fields with priority logic")
        Log.i(TAG, "   📋 Priority Logic:")
        Log.i(TAG, "     1. If cached_prayer_settings exists → use it (highest priority)")
        Log.i(TAG, "     2. Else → use auto-detected + defaults for missing fields")
        Log.i(TAG, "     3. Fallback → use system defaults")
        
        val finalSettings = when {
            // Priority 1: Use cached settings if available
            cachedSettings != null -> {
                Log.i(TAG, "🎯 ALGORITHM CHOICE: Using cached prayer settings (Priority 1)")
                Log.i(TAG, "   ✅ Reason: User has previously configured settings")
                Log.i(TAG, "   📍 Source: User's cached preferences (JSON from SharedPreferences)")

                // IMPORTANT: Merge auto-detected country info into cached settings for restore button
                if (autoDetectedSettings != null && cachedCountry != null) {
                    Log.i(TAG, "   🔄 Merging auto-detected country info into cached settings")
                    Log.i(TAG, "   🌍 Country: ${autoDetectedSettings.autoDetectedCountryName} ($cachedCountry)")
                    cachedSettings.copy(
                        autoDetectedCountryName = autoDetectedSettings.autoDetectedCountryName,
                        autoDetectedCountryCode = autoDetectedSettings.autoDetectedCountryCode
                    )
                } else {
                    Log.w(TAG, "   ⚠️ No auto-detected country info available to merge")
                    cachedSettings
                }
            }

            // Priority 2: Use auto-detected settings + defaults
            autoDetectedSettings != null -> {
                Log.i(TAG, "🎯 ALGORITHM CHOICE: Using auto-detected settings + defaults (Priority 2)")
                Log.i(TAG, "   ✅ Reason: No cached settings, but country-specific settings available")
                Log.i(TAG, "   📍 Source: Auto-detected from country_prayer_methods.json for '$cachedCountry'")
                Log.i(TAG, "   💡 Note: Missing fields (Time Adjustments, etc.) filled with defaults")
                autoDetectedSettings
            }

            // Priority 3: Fallback to system defaults
            else -> {
                Log.w(TAG, "🎯 ALGORITHM CHOICE: Using system defaults (Priority 3 - Fallback)")
                Log.w(TAG, "   ⚠️ Reason: No cached settings AND no auto-detected settings")
                Log.w(TAG, "   📍 Source: Hardcoded system defaults")
                Log.w(TAG, "   💡 This should rarely happen in production")
                getDefaultSettings()
            }
        }
        
        // ALGORITHM COMPLETION SUMMARY
        val totalTime = System.currentTimeMillis() - startTime
        Log.i(TAG, "")
        Log.i(TAG, "🏁 PRAYER SETTINGS ALGORITHM - INITIALIZATION PHASE COMPLETE")
        Log.i(TAG, "⏱️ Total execution time: ${totalTime}ms")
        Log.i(TAG, "📊 FINAL UI VALUES TO BE POPULATED:")
        Log.i(TAG, "   🕌 Calculation Method: ${finalSettings.calculationMethod.displayName}")
        Log.i(TAG, "   🤲 Asr Madhhab: ${finalSettings.asrMadhhab.displayName}")
        Log.i(TAG, "   🧭 High Latitude: ${finalSettings.highLatitudeAdjustment.name}")
        Log.i(TAG, "   🌅 Custom Fajr Angle: ${finalSettings.customFajrAngle ?: "using method default"}")
        Log.i(TAG, "   🌙 Custom Isha Angle: ${finalSettings.customIshaAngle ?: "using method default"}")
        Log.i(TAG, "   ⏰ Custom Isha Delay: ${finalSettings.customIshaDelay ?: "using method default"}")
        Log.i(TAG, "   🔧 Time Adjustments: Fajr=${finalSettings.timeOffsets.fajr}min, Sunrise=${finalSettings.timeOffsets.sunrise}min, Dhuhr=${finalSettings.timeOffsets.dhuhr}min, Asr=${finalSettings.timeOffsets.asr}min, Maghrib=${finalSettings.timeOffsets.maghrib}min, Isha=${finalSettings.timeOffsets.isha}min")
        Log.i(TAG, "   📍 Location: ${if (finalSettings.useGpsLocation) "GPS" else finalSettings.location?.getDisplayName() ?: "GPS"}")
        Log.i(TAG, "   🔔 Notifications: ${if (finalSettings.notificationsEnabled) "Enabled" else "Disabled"} (${finalSettings.notificationSound})")
        Log.i(TAG, "   📳 Vibration: ${if (finalSettings.vibrationEnabled) "Enabled" else "Disabled"}")
        Log.i(TAG, "=".repeat(80))
        Log.i(TAG, "")
        
        return finalSettings
    }
    
    /**
     * Compare cached settings with auto-detected settings to determine restore visibility
     */
    /**
     * PRAYER SETTINGS ALGORITHM - RESTORE OPTION LOGIC
     * 
     * Following the algorithm for restore option visibility:
     * 1. After each user change, compare cached_prayer_settings with auto-detected settings
     * 2. If different → show Restore to Auto-Detected option
     * 3. If identical → hide the restore option
     */
    fun shouldShowRestoreOption(): Boolean {
        Log.i(TAG, "")
        Log.i(TAG, "🎯 PRAYER SETTINGS ALGORITHM - RESTORE OPTION LOGIC")
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "🔍 ALGORITHM PHASE: Compare cached vs auto-detected settings")
        
        val startTime = System.currentTimeMillis()
        
        // STEP 1: Get cached country code
        Log.i(TAG, "📍 STEP 1: Get cached country code")
        val cachedCountry = getCachedCountry()
        if (cachedCountry == null) {
            Log.w(TAG, "⚠️ No country code available - cannot determine auto-detected settings")
            Log.w(TAG, "🎯 RESTORE DECISION: HIDE restore button (no reference to compare against)")
            return false
        }
        Log.i(TAG, "✅ Found country code: '$cachedCountry'")
        
        // STEP 2: Get auto-detected settings for country
        Log.i(TAG, "")
        Log.i(TAG, "🗺️ STEP 2: Get auto-detected settings for country '$cachedCountry'")
        val autoDetectedSettings = getAutoDetectedSettingsForCountry(cachedCountry)
        if (autoDetectedSettings == null) {
            Log.w(TAG, "⚠️ No auto-detected settings available for country '$cachedCountry'")
            Log.w(TAG, "🎯 RESTORE DECISION: HIDE restore button (no auto-detected reference)")
            return false
        }
        Log.i(TAG, "✅ Auto-detected settings loaded:")
        Log.i(TAG, "   🕌 Method: ${autoDetectedSettings.calculationMethod.displayName}")
        Log.i(TAG, "   🤲 Madhhab: ${autoDetectedSettings.asrMadhhab.displayName}")
        
        // STEP 3: Get current cached settings
        Log.i(TAG, "")
        Log.i(TAG, "💾 STEP 3: Get current cached prayer settings")
        val cachedSettings = getCachedPrayerSettings()
        if (cachedSettings == null) {
            Log.w(TAG, "⚠️ No cached settings available")
            Log.w(TAG, "🎯 RESTORE DECISION: HIDE restore button (nothing to compare)")
            return false
        }
        Log.i(TAG, "✅ Cached settings loaded:")
        Log.i(TAG, "   🕌 Method: ${cachedSettings.calculationMethod.displayName}")
        Log.i(TAG, "   🤲 Madhhab: ${cachedSettings.asrMadhhab.displayName}")
        
        // STEP 4: Compare ONLY calculation-related fields (not location, notifications, etc.)
        Log.i(TAG, "")
        Log.i(TAG, "⚖️ STEP 4: Compare calculation-related fields (cached vs auto-detected)")
        Log.i(TAG, "   💡 Comparing: calculationMethod, asrMadhhab, customFajrAngle, customIshaAngle, customIshaDelay, timeOffsets")

        // Compare each calculation-related field
        val methodMatch = cachedSettings.calculationMethod == autoDetectedSettings.calculationMethod
        val madhhabMatch = cachedSettings.asrMadhhab == autoDetectedSettings.asrMadhhab
        val fajrAngleMatch = cachedSettings.customFajrAngle == autoDetectedSettings.customFajrAngle
        val ishaAngleMatch = cachedSettings.customIshaAngle == autoDetectedSettings.customIshaAngle
        val ishaDelayMatch = cachedSettings.customIshaDelay == autoDetectedSettings.customIshaDelay
        val timeOffsetsMatch = cachedSettings.timeOffsets == autoDetectedSettings.timeOffsets

        Log.i(TAG, "📊 Field-by-field comparison:")
        Log.i(TAG, "   - calculationMethod: ${if (methodMatch) "✅ MATCH" else "❌ DIFFERENT"} (cached=${cachedSettings.calculationMethod.name}, auto=${autoDetectedSettings.calculationMethod.name})")
        Log.i(TAG, "   - asrMadhhab: ${if (madhhabMatch) "✅ MATCH" else "❌ DIFFERENT"} (cached=${cachedSettings.asrMadhhab.name}, auto=${autoDetectedSettings.asrMadhhab.name})")
        Log.i(TAG, "   - customFajrAngle: ${if (fajrAngleMatch) "✅ MATCH" else "❌ DIFFERENT"} (cached=${cachedSettings.customFajrAngle}, auto=${autoDetectedSettings.customFajrAngle})")
        Log.i(TAG, "   - customIshaAngle: ${if (ishaAngleMatch) "✅ MATCH" else "❌ DIFFERENT"} (cached=${cachedSettings.customIshaAngle}, auto=${autoDetectedSettings.customIshaAngle})")
        Log.i(TAG, "   - customIshaDelay: ${if (ishaDelayMatch) "✅ MATCH" else "❌ DIFFERENT"} (cached=${cachedSettings.customIshaDelay}, auto=${autoDetectedSettings.customIshaDelay})")
        Log.i(TAG, "   - timeOffsets: ${if (timeOffsetsMatch) "✅ MATCH" else "❌ DIFFERENT"}")
        if (!timeOffsetsMatch) {
            Log.i(TAG, "     • fajr: cached=${cachedSettings.timeOffsets.fajr}, auto=${autoDetectedSettings.timeOffsets.fajr}")
            Log.i(TAG, "     • sunrise: cached=${cachedSettings.timeOffsets.sunrise}, auto=${autoDetectedSettings.timeOffsets.sunrise}")
            Log.i(TAG, "     • dhuhr: cached=${cachedSettings.timeOffsets.dhuhr}, auto=${autoDetectedSettings.timeOffsets.dhuhr}")
            Log.i(TAG, "     • asr: cached=${cachedSettings.timeOffsets.asr}, auto=${autoDetectedSettings.timeOffsets.asr}")
            Log.i(TAG, "     • maghrib: cached=${cachedSettings.timeOffsets.maghrib}, auto=${autoDetectedSettings.timeOffsets.maghrib}")
            Log.i(TAG, "     • isha: cached=${cachedSettings.timeOffsets.isha}, auto=${autoDetectedSettings.timeOffsets.isha}")
        }

        val areIdentical = methodMatch && madhhabMatch && fajrAngleMatch && ishaAngleMatch && ishaDelayMatch && timeOffsetsMatch
        val processingTime = System.currentTimeMillis() - startTime
        
        Log.i(TAG, "")
        Log.i(TAG, "🎯 FINAL RESTORE DECISION:")
        if (!areIdentical) {
            Log.i(TAG, "✅ SHOW RESTORE BUTTON - Settings have been modified from auto-detected values")
            Log.i(TAG, "   💡 User can restore to auto-detected settings for '$cachedCountry'")
        } else {
            Log.i(TAG, "❌ HIDE RESTORE BUTTON - Settings match auto-detected values exactly")
            Log.i(TAG, "   💡 No changes to restore")
        }
        
        Log.i(TAG, "")
        Log.i(TAG, "🏁 RESTORE OPTION LOGIC COMPLETE")
        Log.i(TAG, "⏱️ Processing time: ${processingTime}ms")
        Log.i(TAG, "📊 ALGORITHM SUMMARY:")
        Log.i(TAG, "   ✅ Retrieved country code: '$cachedCountry'")
        Log.i(TAG, "   ✅ Loaded auto-detected settings from JSON")
        Log.i(TAG, "   ✅ Loaded cached prayer settings")
        Log.i(TAG, "   ✅ Compared JSON strings: ${if (areIdentical) "IDENTICAL" else "DIFFERENT"}")
        Log.i(TAG, "   ✅ Decision: ${if (areIdentical) "HIDE" else "SHOW"} restore option")
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "")
        
        return !areIdentical
    }
    
    /**
     * PRAYER SETTINGS ALGORITHM - RESTORE TO AUTO-DETECTED
     * 
     * Following the algorithm for restore action:
     * 1. When user clicks Restore, overwrite cached_prayer_settings with auto-detected settings
     * 2. Include defaults for missing fields
     * 3. Trigger immediate prayer time recalculation
     */
    fun restoreToAutoDetected(): Boolean {
        Log.i(TAG, "")
        Log.i(TAG, "🎯 PRAYER SETTINGS ALGORITHM - RESTORE TO AUTO-DETECTED [Instance: $instanceId]")
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "🔄 ALGORITHM PHASE: When user clicks Restore")

        val startTime = System.currentTimeMillis()
        
        // STEP 1: Get country code
        Log.i(TAG, "📍 STEP 1: Get cached country code")
        val cachedCountry = getCachedCountry()
        if (cachedCountry == null) {
            Log.e(TAG, "❌ RESTORE FAILED: No country code available")
            return false
        }
        Log.i(TAG, "✅ Country code: '$cachedCountry'")
        
        // STEP 2: Get auto-detected settings
        Log.i(TAG, "")
        Log.i(TAG, "🗺️ STEP 2: Load auto-detected settings for '$cachedCountry'")
        val autoDetectedSettings = getAutoDetectedSettingsForCountry(cachedCountry)
        if (autoDetectedSettings == null) {
            Log.e(TAG, "❌ RESTORE FAILED: No auto-detected settings for country '$cachedCountry'")
            return false
        }
        Log.i(TAG, "✅ Auto-detected settings loaded:")
        Log.i(TAG, "   🕌 Method: ${autoDetectedSettings.calculationMethod.displayName}")
        Log.i(TAG, "   🤲 Madhhab: ${autoDetectedSettings.asrMadhhab.displayName}")
        Log.i(TAG, "   🌅 Custom Fajr: ${autoDetectedSettings.customFajrAngle ?: "default"}")
        Log.i(TAG, "   🌙 Custom Isha: ${autoDetectedSettings.customIshaAngle ?: "default"}")
        Log.i(TAG, "   ⏰ Isha Delay: ${autoDetectedSettings.customIshaDelay ?: "default"}")
        
        // STEP 3: Overwrite cached_prayer_settings
        Log.i(TAG, "")
        Log.i(TAG, "💾 STEP 3: Overwrite cached_prayer_settings with auto-detected values")
        val saveStart = System.currentTimeMillis()
        saveCachedPrayerSettings(autoDetectedSettings)
        val saveTime = System.currentTimeMillis() - saveStart
        Log.i(TAG, "✅ Settings overwritten in preferences (${saveTime}ms)")
        Log.i(TAG, "   📄 Storage: JSON format in SharedPreferences")
        Log.i(TAG, "   🔑 Key: current_settings_json")

        // CRITICAL: Save to calculation_settings_json so it persists across app restarts
        // NOTE: Only update calculation and location, NOT notification preferences!
        Log.i(TAG, "")
        Log.i(TAG, "💾 STEP 3b: Save to calculation_settings_json for persistence")
        val calculation = autoDetectedSettings.toCalculationSettings()
        val location = autoDetectedSettings.toLocationPreferences()
        saveCalculationSettings(calculation)
        Log.i(TAG, "✅ Calculation settings saved to calculation_settings_json")
        Log.i(TAG, "⏭️ Notification preferences preserved (not touched by restore)")

        // Update reactive flows
        Log.i(TAG, "")
        Log.i(TAG, "🔄 REACTIVE FLOW UPDATE: Update in-memory flows")
        _settingsFlow.value = autoDetectedSettings
        _settingsFlow.tryEmit(autoDetectedSettings)

        // Update separate preference flows - EXCEPT notification preferences!
        _calculationSettingsFlow.value = calculation
        _calculationSettingsFlow.tryEmit(calculation)
        _locationPreferencesFlow.value = location
        // NOTE: NOT updating _notificationPreferencesFlow to preserve user's notification settings
        Log.i(TAG, "✅ Calculation and location flows updated - notification settings preserved")

        // VERIFY FLOW EMISSION: Log the exact values emitted
        Log.i(TAG, "")
        Log.i(TAG, "🔍 VERIFY FLOW EMISSION: [Instance: $instanceId]")
        Log.i(TAG, "   📦 _calculationSettingsFlow.value = $calculation")
        Log.i(TAG, "   🕰️ Offsets in emitted value:")
        Log.i(TAG, "      - Fajr: ${calculation.timeOffsets.fajr}")
        Log.i(TAG, "      - Sunrise: ${calculation.timeOffsets.sunrise}")
        Log.i(TAG, "      - Dhuhr: ${calculation.timeOffsets.dhuhr}")
        Log.i(TAG, "      - Asr: ${calculation.timeOffsets.asr}")
        Log.i(TAG, "      - Maghrib: ${calculation.timeOffsets.maghrib}")
        Log.i(TAG, "      - Isha: ${calculation.timeOffsets.isha}")
        Log.i(TAG, "   ✅ Flow emission verified")
        
        // STEP 4: Trigger immediate prayer time recalculation
        Log.i(TAG, "")
        Log.i(TAG, "⚡ STEP 4: Trigger immediate prayer time recalculation")
        val recalcStart = System.currentTimeMillis()
        triggerPrayerTimeRecalculation()
        val recalcTime = System.currentTimeMillis() - recalcStart
        Log.i(TAG, "✅ Prayer time recalculation triggered (${recalcTime}ms)")
        
        val totalTime = System.currentTimeMillis() - startTime
        
        Log.i(TAG, "")
        Log.i(TAG, "🏁 RESTORE TO AUTO-DETECTED COMPLETE")
        Log.i(TAG, "⏱️ Total processing time: ${totalTime}ms")
        Log.i(TAG, "📊 RESTORE SUMMARY:")
        Log.i(TAG, "   ✅ Loaded auto-detected settings for '$cachedCountry'")
        Log.i(TAG, "   ✅ Overwritten cached_prayer_settings")
        Log.i(TAG, "   ✅ Updated all reactive flows")
        Log.i(TAG, "   ✅ Triggered prayer time recalculation")
        Log.i(TAG, "   ✅ UI will automatically update to show auto-detected values")
        Log.i(TAG, "   ✅ Restore button will now be hidden (settings match auto-detected)")
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "")
        
        return true
    }
    
    // =============================================
    // PER-COUNTRY SETTINGS STORE (SQLite, sync source)
    // =============================================

    /**
     * A proposed prayer-settings change after detecting a new country. Surfaced to the UI as a
     * consent bottom sheet; NOTHING changes until the user accepts via [applyPendingCountrySwitch].
     */
    data class CountrySwitchProposal(
        val countryCode: String,
        val countryName: String?,
        val proposedMethod: String,
        val proposedAsr: String,
        val currentMethod: String,
        /** true if we already hold the user's saved settings for this country (restore vs first visit). */
        val isRestore: Boolean,
        /** Repair an active-country bucket polluted by the former pre-consent auto-apply bug. */
        val preferAutoDetected: Boolean = false,
    )

    /** Persist the ACTIVE country's settings into its SQLite bucket and signal cloud sync. */
    private fun persistActiveCountryToStore(settings: PrayerCalculationSettings) {
        storeScope.launch {
            try {
                val code = userSettingsStore.lastKnownCountry()
                    ?: getCachedCountry()?.trim()?.uppercase()
                if (!code.isNullOrEmpty()) {
                    userSettingsStore.save(code, settings)
                }
                userSettingsStore.markLocalChange()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to persist active country to store", e)
            }
        }
    }

    /**
     * Resolve a freshly detected country. We NEVER change settings here — on a real country change
     * we publish a [CountrySwitchProposal] for the UI to confirm. The active country's settings are
     * mutated only by [applyPendingCountrySwitch] after the user consents.
     *
     * - First-ever detection: silently adopt the current (already auto-detected) settings as this
     *   country's baseline bucket — nothing to override, so no consent needed.
     * - Same as the active country: clear any stale proposal (and the "declined" marker).
     * - New country: publish a consent proposal EVERY time (so it re-appears on each app open) unless
     *   the user has explicitly declined this specific country via "Keep current".
     */
    suspend fun onCountryDetected(countryCode: String) {
        val code = countryCode.trim().uppercase()
        if (code.isEmpty()) return

        val active = userSettingsStore.lastKnownCountry()

        // First-ever run has no prior country/settings to preserve, so initialize directly from
        // that country's defaults and establish the baseline without showing a consent prompt.
        if (active.isNullOrEmpty()) {
            val auto = getAutoDetectedSettingsForCountry(code)
            val initialSettings = auto?.toCalculationSettings() ?: getCalculationSettings()
            applyActiveSettings(initialSettings, code)
            userSettingsStore.save(code, initialSettings, auto?.autoDetectedCountryName)
            userSettingsStore.setLastKnownCountry(code)
            userSettingsStore.markLocalChange() // back up the initial bucket
            Log.i(TAG, "🌍 STORE: seeded baseline bucket for '$code' (first run)")
            return
        }

        val declinedCountry = userSettingsStore.getMeta(UserSettingsStore.KEY_DECLINED_COUNTRY)
        if (code == active) {
            val auto = getAutoDetectedSettingsForCountry(code)
            val current = getCalculationSettings()
            val activeSettingsMismatch = auto != null &&
                (current.calculationMethod != auto.calculationMethod ||
                    current.asrMadhhab != auto.asrMadhhab)

            if (activeSettingsMismatch && declinedCountry != code) {
                // Older builds could change the calculation settings before consent while leaving
                // lastKnownCountry untouched. Returning to that country must offer its real
                // defaults instead of trusting the now-polluted saved bucket.
                _pendingCountrySwitch.value = CountrySwitchProposal(
                    countryCode = code,
                    countryName = auto.autoDetectedCountryName,
                    proposedMethod = auto.calculationMethod.displayName,
                    proposedAsr = auto.asrMadhhab.displayName,
                    currentMethod = current.calculationMethod.displayName,
                    isRestore = false,
                    preferAutoDetected = true,
                )
                Log.i(TAG, "🌍 STORE: active '$code' has mismatched settings — requesting repair consent")
            } else {
                _pendingCountrySwitch.value = null
            }

            // Returning from a different declined country allows that departure to prompt again.
            // A decline for this same country is retained to avoid repeatedly showing the repair.
            if (declinedCountry != null && declinedCountry != code) {
                userSettingsStore.putMeta(UserSettingsStore.KEY_DECLINED_COUNTRY, null)
            }
            return
        }

        // Only an explicit "Keep current" for THIS country stops the prompt. Otherwise re-publish
        // every time so the consent sheet reappears on each app open until the user decides.
        if (declinedCountry == code) return

        Log.i(TAG, "🌍 STORE: country '$active' → '$code' — requesting consent")
        val existing = userSettingsStore.getForCountry(code)
        val auto = getAutoDetectedSettingsForCountry(code)
        val proposed = existing ?: auto?.toCalculationSettings() ?: getCalculationSettings()
        _pendingCountrySwitch.value = CountrySwitchProposal(
            countryCode = code,
            countryName = auto?.autoDetectedCountryName,
            proposedMethod = proposed.calculationMethod.displayName,
            proposedAsr = proposed.asrMadhhab.displayName,
            currentMethod = getCalculationSettings().calculationMethod.displayName,
            isRestore = existing != null,
        )
    }

    /** Re-evaluate the current detected country (e.g. on app resume) so the prompt reappears. */
    suspend fun revalidatePendingCountrySwitch() {
        val detected = getCachedCountry()?.trim()?.uppercase() ?: return
        onCountryDetected(detected)
    }

    /** User accepted the pending proposal: apply that country's settings and make it active. */
    suspend fun applyPendingCountrySwitch() {
        val proposal = _pendingCountrySwitch.value ?: return
        val code = proposal.countryCode
        val existing = userSettingsStore.getForCountry(code)
        val auto = getAutoDetectedSettingsForCountry(code)
        val settings = if (proposal.preferAutoDetected) {
            auto?.toCalculationSettings() ?: existing ?: getCalculationSettings()
        } else {
            existing ?: auto?.toCalculationSettings() ?: getCalculationSettings()
        }
        if (existing == null || proposal.preferAutoDetected) {
            userSettingsStore.save(code, settings, auto?.autoDetectedCountryName)
            userSettingsStore.markLocalChange()
        }
        applyActiveSettings(settings, code) // sets active country = code, applies + recalculates
        userSettingsStore.putMeta(UserSettingsStore.KEY_DECLINED_COUNTRY, null) // a clean decision
        _pendingCountrySwitch.value = null
        Log.i(TAG, "🌍 STORE: user APPLIED settings for '$code'")
    }

    /** User chose "Keep current": a terminal decision — don't re-prompt for this country. */
    suspend fun keepCurrentForDetectedCountry() {
        val code = _pendingCountrySwitch.value?.countryCode
        if (code != null) userSettingsStore.putMeta(UserSettingsStore.KEY_DECLINED_COUNTRY, code)
        _pendingCountrySwitch.value = null
        Log.i(TAG, "🌍 STORE: user kept current settings (declined '$code')")
    }

    /** Sheet swiped away without a choice: hide for now, re-prompt on the next app open. */
    fun dismissProposalForNow() {
        _pendingCountrySwitch.value = null
    }

    /** Push a country's settings into the active flows + SharedPreferences cache and recalculate. */
    private fun applyActiveSettings(settings: PrayerCalculationSettings, countryCode: String) {
        saveCalculationSettings(settings)

        // Keep cached_prayer_settings (used by the Prayer Settings dialog) in sync, like updateCalculationSettings does.
        getCachedPrayerSettings()?.let { cached ->
            saveCachedPrayerSettings(
                cached.copy(
                    calculationMethod = settings.calculationMethod,
                    asrMadhhab = settings.asrMadhhab,
                    highLatitudeAdjustment = settings.highLatitudeAdjustment,
                    customFajrAngle = settings.customFajrAngle,
                    customIshaAngle = settings.customIshaAngle,
                    customIshaDelay = settings.customIshaDelay,
                    customMaghribOffset = settings.customMaghribOffset,
                    timeOffsets = settings.timeOffsets
                )
            )
        }

        _calculationSettingsFlow.value = settings
        _calculationSettingsFlow.tryEmit(settings)
        updateLegacyCombinedFlow()
        storeScope.launch {
            runCatching { userSettingsStore.setLastKnownCountry(countryCode) }
        }
        triggerPrayerTimeRecalculation()
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
            android.util.Log.w("PrayerSettingsRepository", "🔥 JSON GENERATED:")
            try {
                val prettyJson = Json { prettyPrint = true }.encodeToString(
                    Json.parseToJsonElement(settingsJson)
                )
                prettyJson.lines().forEach { line ->
                    android.util.Log.w("PrayerSettingsRepository", line)
                }
            } catch (e: Exception) {
                android.util.Log.w("PrayerSettingsRepository", settingsJson)
            }
            
            val editor = prefs.edit()
            Log.i(TAG, "💾 PREFERENCE WRITE: key='$KEY_CURRENT_SETTINGS_JSON' (legacy combined settings)")
            editor.putString(KEY_CURRENT_SETTINGS_JSON, settingsJson)
            
            // Always use apply() instead of commit() to prevent main thread blocking
            // commit() is synchronous and can cause ANR/startup hangs
            editor.apply()
            android.util.Log.w("PrayerSettingsRepository", "🔥 APPLY CALLED (async) - forceCommit was: $forceCommit")
            val result = true
            
            // Verify it was saved
            Log.i(TAG, "🔍 PREFERENCE VERIFY READ: key='$KEY_CURRENT_SETTINGS_JSON' (legacy combined settings)")
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
        Log.i(TAG, "   - Current method: ${currentSettings.calculationMethod.displayName}")
        Log.i(TAG, "   - Backup JSON available: ${backupJson != null}")
        Log.i(TAG, "   - Backup JSON length: ${backupJson?.length ?: 0}")
        
        return if (backupJson != null) {
            try {
                Log.i(TAG, "🔄 Parsing backup JSON...")
                val backupSettings = Json.decodeFromString<PrayerSettings>(backupJson)
                
                Log.i(TAG, "📋 BACKUP SETTINGS FOUND:")
                Log.i(TAG, "   - Backup method: ${backupSettings.calculationMethod.displayName}")
                Log.i(TAG, "   - Backup method: ${backupSettings.calculationMethod.displayName}")
                Log.i(TAG, "   - Backup custom Fajr: ${backupSettings.customFajrAngle}")
                Log.i(TAG, "   - Backup custom Isha: ${backupSettings.customIshaAngle}")
                Log.i(TAG, "   - Backup madhhab: ${backupSettings.asrMadhhab.displayName}")
                
                // Restore with auto-detection flags enabled
                val restoredSettings = backupSettings.copy(
                    isMethodAutoDetected = true,
                    isMadhhabAutoDetected = true,
                    areCustomAnglesAutoDetected = true,
                    originalAutoDetectedSettingsJson = backupJson // Keep the backup
                )
                
                Log.i(TAG, "📤 APPLYING RESTORED SETTINGS:")
                Log.i(TAG, "   - Restored method: ${restoredSettings.calculationMethod.displayName}")
                Log.i(TAG, "   - Restored method: ${restoredSettings.calculationMethod.displayName}")
                Log.i(TAG, "   - Restored madhhab: ${restoredSettings.asrMadhhab.displayName}")
                
                updateSettings(restoredSettings, forceCommit = true)
                Log.i(TAG, "✅ Auto-detected settings restored and committed successfully")
                
                // Verify the settings were actually applied
                val verifySettings = getSettings()
                Log.i(TAG, "🔍 VERIFICATION:")
                Log.i(TAG, "   - Final method: ${verifySettings.calculationMethod.displayName}")
                Log.i(TAG, "   - Final method: ${verifySettings.calculationMethod.displayName}")
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to restore auto-detected settings from backup", e)
                Log.e(TAG, "   - Error type: ${e.javaClass.simpleName}")
                Log.e(TAG, "   - Error message: ${e.message}")
                false
            }
        } else {
            Log.w(TAG, "⚠️ No backup auto-detected settings available")
            Log.w(TAG, "   - Current method: ${currentSettings.calculationMethod.displayName}")
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
        Log.i(TAG, "")
        Log.i(TAG, "💾 PRAYER TIMES CACHE OPERATION")
        Log.i(TAG, "=".repeat(70))
        Log.i(TAG, "📅 Caching prayer times for: ${prayerTimes.date.toLocalDate()}")
        Log.i(TAG, "📍 Location: ${prayerTimes.location.city}, ${prayerTimes.location.country}")
        Log.i(TAG, "📊 Total cache keys: 12 (1 date + 6 prayer times + 5 location data)")
        Log.i(TAG, "")
        
        val dateStr = prayerTimes.date.toLocalDate().toString()
        val fajrMinutes = prayerTimes.fajr.toSecondOfDay() / 60
        val sunriseMinutes = prayerTimes.sunrise.toSecondOfDay() / 60
        val dhuhrMinutes = prayerTimes.dhuhr.toSecondOfDay() / 60
        val asrMinutes = prayerTimes.asr.toSecondOfDay() / 60
        val maghribMinutes = prayerTimes.maghrib.toSecondOfDay() / 60
        val ishaMinutes = prayerTimes.isha.toSecondOfDay() / 60
        
        prefs.edit().apply {
            // Cache the date to ensure validity
            logPrefWrite(KEY_CACHED_PRAYER_DATE, dateStr)
            putString(KEY_CACHED_PRAYER_DATE, dateStr)
            
            Log.i(TAG, "⏰ PRAYER TIME CACHE DATA:")
            logPrefWrite(KEY_CACHED_FAJR, "$fajrMinutes minutes (${prayerTimes.fajr})")
            logPrefWrite(KEY_CACHED_SUNRISE, "$sunriseMinutes minutes (${prayerTimes.sunrise})")
            logPrefWrite(KEY_CACHED_DHUHR, "$dhuhrMinutes minutes (${prayerTimes.dhuhr})")
            logPrefWrite(KEY_CACHED_ASR, "$asrMinutes minutes (${prayerTimes.asr})")
            logPrefWrite(KEY_CACHED_MAGHRIB, "$maghribMinutes minutes (${prayerTimes.maghrib})")
            logPrefWrite(KEY_CACHED_ISHA, "$ishaMinutes minutes (${prayerTimes.isha})")
            
            putInt(KEY_CACHED_FAJR, fajrMinutes)
            putInt(KEY_CACHED_SUNRISE, sunriseMinutes)
            putInt(KEY_CACHED_DHUHR, dhuhrMinutes)
            putInt(KEY_CACHED_ASR, asrMinutes)
            putInt(KEY_CACHED_MAGHRIB, maghribMinutes)
            putInt(KEY_CACHED_ISHA, ishaMinutes)
            
            // Cache location information
            Log.i(TAG, "📍 LOCATION CACHE DATA:")
            logPrefWrite(KEY_CACHED_LOCATION_LAT, prayerTimes.location.latitude)
            logPrefWrite(KEY_CACHED_LOCATION_LON, prayerTimes.location.longitude)
            logPrefWrite(KEY_CACHED_LOCATION_CITY, prayerTimes.location.city)
            logPrefWrite(KEY_CACHED_LOCATION_COUNTRY, prayerTimes.location.country)
            logPrefWrite(KEY_CACHED_LOCATION_COUNTRY_CODE, prayerTimes.location.countryCode)
            logPrefWrite(KEY_CACHED_LOCATION_TIMEZONE, prayerTimes.location.timeZoneOffset)
            logPrefWrite(KEY_CACHED_LOCATION_AREA, prayerTimes.location.area)
            logPrefWrite(KEY_CACHED_LOCATION_SUB_LOCALITY, prayerTimes.location.subLocality)
            logPrefWrite(KEY_CACHED_LOCATION_THOROUGHFARE, prayerTimes.location.thoroughfare)
            logPrefWrite(KEY_CACHED_LOCATION_ADMIN_AREA, prayerTimes.location.administrativeArea)
            
            putFloat(KEY_CACHED_LOCATION_LAT, prayerTimes.location.latitude.toFloat())
            putFloat(KEY_CACHED_LOCATION_LON, prayerTimes.location.longitude.toFloat())
            putString(KEY_CACHED_LOCATION_CITY, prayerTimes.location.city)
            putString(KEY_CACHED_LOCATION_COUNTRY, prayerTimes.location.country)
            putString(KEY_CACHED_LOCATION_COUNTRY_CODE, prayerTimes.location.countryCode)
            putFloat(KEY_CACHED_LOCATION_TIMEZONE, prayerTimes.location.timeZoneOffset.toFloat())
            putString(KEY_CACHED_LOCATION_AREA, prayerTimes.location.area)
            putString(KEY_CACHED_LOCATION_SUB_LOCALITY, prayerTimes.location.subLocality)
            putString(KEY_CACHED_LOCATION_THOROUGHFARE, prayerTimes.location.thoroughfare)
            putString(KEY_CACHED_LOCATION_ADMIN_AREA, prayerTimes.location.administrativeArea)
            
            apply() // Use apply() for cache - no need for immediate synchronous write during startup
        }
        
        Log.i(TAG, "")
        Log.i(TAG, "🔍 CACHE WRITE VERIFICATION:")
        // Verify critical cache data was written correctly
        verifyPrefWrite(KEY_CACHED_PRAYER_DATE, dateStr, "prayer date")
        verifyPrefWrite(KEY_CACHED_FAJR, fajrMinutes, "fajr time")
        verifyPrefWrite(KEY_CACHED_DHUHR, dhuhrMinutes, "dhuhr time")
        verifyPrefWrite(KEY_CACHED_LOCATION_CITY, prayerTimes.location.city, "location city")
        verifyPrefWrite(KEY_CACHED_LOCATION_AREA, prayerTimes.location.area, "location area")
        verifyPrefWrite(KEY_CACHED_LOCATION_SUB_LOCALITY, prayerTimes.location.subLocality, "location sub-locality")
        
        Log.i(TAG, "✅ PRAYER TIMES CACHE: Successfully cached all data to preferences")
        Log.i(TAG, "📈 Cache Performance: Fast app startup enabled for ${prayerTimes.date.toLocalDate()}")
        Log.i(TAG, "")

        // This is the single point where a fresh calculation becomes the app's persisted
        // truth, so it is also where the home-screen widget has to be told to redraw —
        // a settings, location or date change would otherwise leave it showing yesterday's
        // schedule until updatePeriodMillis next fired. Fire-and-forget; no widget placed
        // makes it a no-op.
        PrayerWidgetUpdater.refresh(context)
    }
    
    /**
     * CACHED LOCATION RETRIEVAL: Gets the last location prayer times were calculated for.
     *
     * Unlike [getCachedPrayerTimes] this has no date validity check, so it keeps working
     * the morning after the cached times go stale. That is what makes it the right entry
     * point for headless callers (the home-screen widget) that must recalculate today's
     * times without waking GPS: cached location + stored settings is enough input for
     * [com.starception.submission.prayer.service.PrayerTimeCalculatorService].
     *
     * Returns null only when prayer times have never been cached on this install.
     */
    fun getCachedLocation(): Location? {
        if (!prefs.contains(KEY_CACHED_LOCATION_LAT)) return null

        return try {
            Location(
                latitude = prefs.getFloat(KEY_CACHED_LOCATION_LAT, 0f).toDouble(),
                longitude = prefs.getFloat(KEY_CACHED_LOCATION_LON, 0f).toDouble(),
                timeZoneOffset = prefs.getFloat(KEY_CACHED_LOCATION_TIMEZONE, 0f).toDouble(),
                city = prefs.getString(KEY_CACHED_LOCATION_CITY, "") ?: "",
                country = prefs.getString(KEY_CACHED_LOCATION_COUNTRY, "") ?: "",
                countryCode = prefs.getString(KEY_CACHED_LOCATION_COUNTRY_CODE, "") ?: "",
                area = prefs.getString(KEY_CACHED_LOCATION_AREA, "") ?: "",
                subLocality = prefs.getString(KEY_CACHED_LOCATION_SUB_LOCALITY, "") ?: "",
                thoroughfare = prefs.getString(KEY_CACHED_LOCATION_THOROUGHFARE, "") ?: "",
                administrativeArea = prefs.getString(KEY_CACHED_LOCATION_ADMIN_AREA, "") ?: "",
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to read cached location", e)
            null
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
        Log.i(TAG, "")
        Log.i(TAG, "💾 CACHE RETRIEVAL OPERATION")
        Log.i(TAG, "=".repeat(80))
        Log.i(TAG, "🔍 Checking SharedPreferences for cached prayer times")
        val startTime = System.currentTimeMillis()
        
        return try {
            val cachedDateStr = prefs.getString(KEY_CACHED_PRAYER_DATE, null)
            logPrefRead(KEY_CACHED_PRAYER_DATE, cachedDateStr, "null")
            
            if (cachedDateStr == null) {
                Log.w(TAG, "❌ No cached prayer date found - cache is empty")
                Log.i(TAG, "⏱️ Cache retrieval completed in ${System.currentTimeMillis() - startTime}ms")
                Log.i(TAG, "")
                return null
            }
            
            Log.i(TAG, "📅 Found cached date: $cachedDateStr")
            val cachedDate = LocalDate.parse(cachedDateStr)
            val today = LocalDate.now()
            Log.i(TAG, "📅 Today's date: $today")
            Log.i(TAG, "🔄 Date comparison: cached=$cachedDate, today=$today, isToday=${cachedDate == today}")
            
            // Only return cached data if it's for today
            if (cachedDate != today) {
                Log.w(TAG, "❌ Cached data is stale - cached date is $cachedDate but today is $today")
                Log.i(TAG, "🗑️ Cache validation failed - will need fresh calculation")
                Log.i(TAG, "⏱️ Cache retrieval completed in ${System.currentTimeMillis() - startTime}ms")
                Log.i(TAG, "")
                return null
            }
            
            Log.i(TAG, "✅ Cache date validation passed - data is for today")
            Log.i(TAG, "🔍 Checking data completeness...")
            
            // Check if all required data is present
            val hasAllRequiredData = prefs.contains(KEY_CACHED_FAJR) && 
                                   prefs.contains(KEY_CACHED_LOCATION_LAT) &&
                                   prefs.contains(KEY_CACHED_SUNRISE) &&
                                   prefs.contains(KEY_CACHED_DHUHR) &&
                                   prefs.contains(KEY_CACHED_ASR) &&
                                   prefs.contains(KEY_CACHED_MAGHRIB) &&
                                   prefs.contains(KEY_CACHED_ISHA)
            
            Log.i(TAG, "📊 Cache completeness check:")
            Log.i(TAG, "  - Prayer times: ${if (prefs.contains(KEY_CACHED_FAJR)) "✅" else "❌"}")
            Log.i(TAG, "  - Location data: ${if (prefs.contains(KEY_CACHED_LOCATION_LAT)) "✅" else "❌"}")
            Log.i(TAG, "  - All required data: ${if (hasAllRequiredData) "✅" else "❌"}")
            
            if (!hasAllRequiredData) {
                Log.w(TAG, "❌ Cache data is incomplete - missing required fields")
                Log.i(TAG, "⏱️ Cache retrieval completed in ${System.currentTimeMillis() - startTime}ms")
                Log.i(TAG, "")
                return null
            }
            
            val fajrMinutes = prefs.getInt(KEY_CACHED_FAJR, -1)
            val sunriseMinutes = prefs.getInt(KEY_CACHED_SUNRISE, -1)
            val dhuhrMinutes = prefs.getInt(KEY_CACHED_DHUHR, -1)
            val asrMinutes = prefs.getInt(KEY_CACHED_ASR, -1)
            val maghribMinutes = prefs.getInt(KEY_CACHED_MAGHRIB, -1)
            val ishaMinutes = prefs.getInt(KEY_CACHED_ISHA, -1)
            
            Log.i(TAG, "🕐 Reading cached prayer times (as minutes since midnight):")
            logPrefRead(KEY_CACHED_FAJR, "$fajrMinutes (${LocalTime.ofSecondOfDay((fajrMinutes * 60).toLong())})", -1)
            logPrefRead(KEY_CACHED_SUNRISE, "$sunriseMinutes (${LocalTime.ofSecondOfDay((sunriseMinutes * 60).toLong())})", -1)
            logPrefRead(KEY_CACHED_DHUHR, "$dhuhrMinutes (${LocalTime.ofSecondOfDay((dhuhrMinutes * 60).toLong())})", -1)
            logPrefRead(KEY_CACHED_ASR, "$asrMinutes (${LocalTime.ofSecondOfDay((asrMinutes * 60).toLong())})", -1)
            logPrefRead(KEY_CACHED_MAGHRIB, "$maghribMinutes (${LocalTime.ofSecondOfDay((maghribMinutes * 60).toLong())})", -1)
            logPrefRead(KEY_CACHED_ISHA, "$ishaMinutes (${LocalTime.ofSecondOfDay((ishaMinutes * 60).toLong())})", -1)
            
            // Validate all times are present
            val invalidTimes = listOf(
                "Fajr" to fajrMinutes,
                "Sunrise" to sunriseMinutes, 
                "Dhuhr" to dhuhrMinutes,
                "Asr" to asrMinutes,
                "Maghrib" to maghribMinutes,
                "Isha" to ishaMinutes
            ).filter { it.second == -1 }
            
            if (invalidTimes.isNotEmpty()) {
                Log.w(TAG, "❌ Invalid prayer times found: ${invalidTimes.map { it.first }}")
                Log.i(TAG, "⏱️ Cache retrieval completed in ${System.currentTimeMillis() - startTime}ms")
                Log.i(TAG, "")
                return null
            }
            
            Log.i(TAG, "✅ All prayer times are valid")
            
            val latitude = prefs.getFloat(KEY_CACHED_LOCATION_LAT, 0f).toDouble()
            val longitude = prefs.getFloat(KEY_CACHED_LOCATION_LON, 0f).toDouble()
            val city = prefs.getString(KEY_CACHED_LOCATION_CITY, "") ?: ""
            val country = prefs.getString(KEY_CACHED_LOCATION_COUNTRY, "") ?: ""
            val area = prefs.getString(KEY_CACHED_LOCATION_AREA, "") ?: ""
            val subLocality = prefs.getString(KEY_CACHED_LOCATION_SUB_LOCALITY, "") ?: ""
            val thoroughfare = prefs.getString(KEY_CACHED_LOCATION_THOROUGHFARE, "") ?: ""
            val administrativeArea = prefs.getString(KEY_CACHED_LOCATION_ADMIN_AREA, "") ?: ""
            
            logPrefRead(KEY_CACHED_LOCATION_LAT, latitude, 0f)
            logPrefRead(KEY_CACHED_LOCATION_LON, longitude, 0f)
            logPrefRead(KEY_CACHED_LOCATION_CITY, city, "")
            logPrefRead(KEY_CACHED_LOCATION_COUNTRY, country, "")
            logPrefRead(KEY_CACHED_LOCATION_AREA, area, "")
            logPrefRead(KEY_CACHED_LOCATION_SUB_LOCALITY, subLocality, "")
            logPrefRead(KEY_CACHED_LOCATION_THOROUGHFARE, thoroughfare, "")
            logPrefRead(KEY_CACHED_LOCATION_ADMIN_AREA, administrativeArea, "")
            
            val location = Location(
                latitude = latitude,
                longitude = longitude,
                city = city,
                country = country,
                countryCode = run {
                    val countryCode = prefs.getString(KEY_CACHED_LOCATION_COUNTRY_CODE, "") ?: ""
                    logPrefRead(KEY_CACHED_LOCATION_COUNTRY_CODE, countryCode, "")
                    countryCode
                },
                timeZoneOffset = run {
                    val timezone = prefs.getFloat(KEY_CACHED_LOCATION_TIMEZONE, 0f).toDouble()
                    logPrefRead(KEY_CACHED_LOCATION_TIMEZONE, timezone, 0f)
                    timezone
                },
                area = area,
                subLocality = subLocality,
                thoroughfare = thoroughfare,
                administrativeArea = administrativeArea
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
            remove(KEY_CACHED_LOCATION_AREA)
            remove(KEY_CACHED_LOCATION_SUB_LOCALITY)
            remove(KEY_CACHED_LOCATION_THOROUGHFARE)
            remove(KEY_CACHED_LOCATION_ADMIN_AREA)
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
        Log.i(TAG, "📋 PREFERENCE READ: key='$KEY_CACHED_PRAYER_DATE' (cached prayer date for validation)")
        val cachedDateStr = prefs.getString(KEY_CACHED_PRAYER_DATE, null) ?: return false
        return try {
            val cachedDate = LocalDate.parse(cachedDateStr)
            cachedDate == LocalDate.now() && prefs.contains(KEY_CACHED_FAJR)
        } catch (e: Exception) {
            false
        }
    }
    
    // ===== NEW SEPARATE PREFERENCE SYSTEM =====
    
    /**
     * LOAD ALL SETTINGS: Load separate preference types or migrate from legacy
     *
     * IMPORTANT: This function preserves existing preferences when doing partial migration.
     * If only some preferences are missing, we keep existing ones and only fill in gaps.
     * This prevents notification settings from being reset when location/calculation changes.
     */
    private fun loadAllSettings() {
        android.util.Log.w("PrayerSettingsRepository", "🔥 LOAD ALL SETTINGS CALLED")

        // Try loading separate preferences first
        val calculationSettings = loadCalculationSettings()
        val locationPreferences = loadLocationPreferences()
        val notificationPreferences = loadNotificationPreferences()

        android.util.Log.w("PrayerSettingsRepository", "📊 Load results: calc=${calculationSettings != null}, loc=${locationPreferences != null}, notif=${notificationPreferences != null}")

        if (calculationSettings != null && locationPreferences != null && notificationPreferences != null) {
            // All preferences exist - use them directly
            android.util.Log.w("PrayerSettingsRepository", "✅ All preferences loaded successfully")
            _calculationSettingsFlow.value = calculationSettings
            _locationPreferencesFlow.value = locationPreferences
            _notificationPreferencesFlow.value = notificationPreferences
            _settingsFlow.value = combineToLegacySettings(calculationSettings, locationPreferences, notificationPreferences)
        } else {
            // FRESH INSTALL: Some preferences missing, use defaults for missing ones
            android.util.Log.w("PrayerSettingsRepository", "🆕 Some preferences missing - using defaults for missing ones")

            // Use existing preferences if available, otherwise use defaults
            val finalCalcSettings = calculationSettings ?: run {
                android.util.Log.w("PrayerSettingsRepository", "📝 Calculation settings missing - using defaults")
                getDefaultCalculationSettings()
            }

            val finalLocPrefs = locationPreferences ?: run {
                android.util.Log.w("PrayerSettingsRepository", "📝 Location preferences missing - using defaults")
                getDefaultLocationPreferences()
            }

            val finalNotificationPrefs = notificationPreferences ?: run {
                android.util.Log.w("PrayerSettingsRepository", "📝 Notification preferences missing - using defaults")
                getDefaultNotificationPreferences()
            }

            // Save any missing preferences
            if (calculationSettings == null) {
                saveCalculationSettings(finalCalcSettings)
            }
            if (locationPreferences == null) {
                saveLocationPreferences(finalLocPrefs)
            }
            if (notificationPreferences == null) {
                saveNotificationPreferences(finalNotificationPrefs)
            }

            // Update flows
            _calculationSettingsFlow.value = finalCalcSettings
            _locationPreferencesFlow.value = finalLocPrefs
            _notificationPreferencesFlow.value = finalNotificationPrefs
            _settingsFlow.value = combineToLegacySettings(finalCalcSettings, finalLocPrefs, finalNotificationPrefs)

            android.util.Log.w("PrayerSettingsRepository", "✅ Preferences initialized successfully")
        }
    }
    
    /**
     * LOAD DEFAULT SETTINGS: Set all flows to default values
     */
    private fun loadDefaultSettings() {
        _calculationSettingsFlow.value = getDefaultCalculationSettings()
        _locationPreferencesFlow.value = getDefaultLocationPreferences()
        _notificationPreferencesFlow.value = getDefaultNotificationPreferences()
        _settingsFlow.value = getDefaultSettings()
    }
    
    /**
     * LOAD INDIVIDUAL PREFERENCE TYPES
     */
    private fun loadCalculationSettings(): PrayerCalculationSettings? {
        return try {
            val settingsJson = prefs.getString(KEY_CALCULATION_SETTINGS_JSON, null)
            logPrefReadJson(KEY_CALCULATION_SETTINGS_JSON, settingsJson, "prayer calculation settings")
            
            // Check if JSON is empty or just contains empty object "{}"
            if (settingsJson != null && settingsJson.trim() != "{}" && settingsJson.trim().isNotEmpty()) {
                val settings = json.decodeFromString<PrayerCalculationSettings>(settingsJson)
                
                // ENHANCED LOADING LOGGING
                Log.i(TAG, "")
                Log.i(TAG, "📥 DETAILED LOADING OPERATION")
                Log.i(TAG, "=".repeat(60))
                Log.i(TAG, "🗂️ STORAGE KEY: '$KEY_CALCULATION_SETTINGS_JSON'")
                Log.i(TAG, "📦 RAW JSON FROM STORAGE:")
                Log.i(TAG, "   $settingsJson")
                Log.i(TAG, "🔍 PARSED PRAYER OFFSETS:")
                Log.i(TAG, "   🌅 Fajr: ${settings.timeOffsets.fajr} minutes")
                Log.i(TAG, "   🌄 Sunrise: ${settings.timeOffsets.sunrise} minutes") 
                Log.i(TAG, "   🌞 Dhuhr: ${settings.timeOffsets.dhuhr} minutes")
                Log.i(TAG, "   🌇 Asr: ${settings.timeOffsets.asr} minutes")
                Log.i(TAG, "   🌆 Maghrib: ${settings.timeOffsets.maghrib} minutes")
                Log.i(TAG, "   🌙 Isha: ${settings.timeOffsets.isha} minutes")
                Log.i(TAG, "📊 TOTAL NON-ZERO OFFSETS: ${listOf(settings.timeOffsets.fajr, settings.timeOffsets.sunrise, settings.timeOffsets.dhuhr, settings.timeOffsets.asr, settings.timeOffsets.maghrib, settings.timeOffsets.isha).count { it != 0 }}")
                Log.i(TAG, "=".repeat(60))
                Log.i(TAG, "")
                
                settings
            } else {
                Log.i(TAG, "📭 NO DATA FOUND: '$KEY_CALCULATION_SETTINGS_JSON' is empty or contains empty JSON object - will trigger auto-initialization")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR LOADING: Failed to parse '$KEY_CALCULATION_SETTINGS_JSON'", e)
            null
        }
    }
    
    private fun loadLocationPreferences(): PrayerLocationPreferences? {
        return try {
            val settingsJson = prefs.getString(KEY_LOCATION_PREFERENCES_JSON, null)
            logPrefReadJson(KEY_LOCATION_PREFERENCES_JSON, settingsJson, "location preferences")
            // Check if JSON is empty or just contains empty object "{}"
            if (settingsJson != null && settingsJson.trim() != "{}" && settingsJson.trim().isNotEmpty()) {
                json.decodeFromString<PrayerLocationPreferences>(settingsJson)
            } else {
                Log.i(TAG, "📭 NO DATA FOUND: '$KEY_LOCATION_PREFERENCES_JSON' is empty or contains empty JSON object - will trigger auto-initialization")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PrayerSettingsRepository", "Error loading location preferences", e)
            null
        }
    }
    
    private fun loadNotificationPreferences(): PrayerNotificationPreferences? {
        return try {
            val settingsJson = prefs.getString(KEY_NOTIFICATION_PREFERENCES_JSON, null)
            logPrefReadJson(KEY_NOTIFICATION_PREFERENCES_JSON, settingsJson, "notification preferences")
            // Check if JSON is empty or just contains empty object "{}"
            if (settingsJson != null && settingsJson.trim() != "{}" && settingsJson.trim().isNotEmpty()) {
                json.decodeFromString<PrayerNotificationPreferences>(settingsJson)
            } else {
                Log.i(TAG, "📭 NO DATA FOUND: '$KEY_NOTIFICATION_PREFERENCES_JSON' is empty or contains empty JSON object - will trigger auto-initialization")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PrayerSettingsRepository", "Error loading notification preferences", e)
            null
        }
    }

    /**
     * SAVE INDIVIDUAL PREFERENCE TYPES
     */
    private fun saveCalculationSettings(settings: PrayerCalculationSettings) {
        val settingsJson = json.encodeToString(settings)
        logPrefWriteJson(KEY_CALCULATION_SETTINGS_JSON, settingsJson, "prayer calculation settings")
        
        // ENHANCED STORAGE LOGGING
        Log.i(TAG, "")
        Log.i(TAG, "💾 DETAILED STORAGE OPERATION")
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "🗂️ STORAGE KEY: '$KEY_CALCULATION_SETTINGS_JSON'")
        Log.i(TAG, "📦 COMPLETE JSON BEING STORED:")
        Log.i(TAG, "   $settingsJson")
        Log.i(TAG, "🔍 PRAYER OFFSETS IN STORAGE:")
        Log.i(TAG, "   🌅 Fajr: ${settings.timeOffsets.fajr} minutes")
        Log.i(TAG, "   🌄 Sunrise: ${settings.timeOffsets.sunrise} minutes") 
        Log.i(TAG, "   🌞 Dhuhr: ${settings.timeOffsets.dhuhr} minutes")
        Log.i(TAG, "   🌇 Asr: ${settings.timeOffsets.asr} minutes")
        Log.i(TAG, "   🌆 Maghrib: ${settings.timeOffsets.maghrib} minutes")
        Log.i(TAG, "   🌙 Isha: ${settings.timeOffsets.isha} minutes")
        Log.i(TAG, "📊 TOTAL NON-ZERO OFFSETS: ${listOf(settings.timeOffsets.fajr, settings.timeOffsets.sunrise, settings.timeOffsets.dhuhr, settings.timeOffsets.asr, settings.timeOffsets.maghrib, settings.timeOffsets.isha).count { it != 0 }}")
        Log.i(TAG, "=".repeat(60))
        
        prefs.edit().putString(KEY_CALCULATION_SETTINGS_JSON, settingsJson).apply()
        
        // Verify the save operation was successful
        verifyPrefWrite(KEY_CALCULATION_SETTINGS_JSON, settingsJson, "calculation settings JSON")
        
        Log.i(TAG, "✅ STORAGE COMPLETE: Data written to SharedPreferences")
        Log.i(TAG, "")
    }
    
    private fun saveLocationPreferences(preferences: PrayerLocationPreferences) {
        val settingsJson = json.encodeToString(preferences)
        logPrefWriteJson(KEY_LOCATION_PREFERENCES_JSON, settingsJson, "location preferences")
        
        Log.i(TAG, "")
        Log.i(TAG, "📍 LOCATION PREFERENCES STORAGE")
        Log.i(TAG, "=".repeat(50))
        Log.i(TAG, "🗂️ Storage Key: '$KEY_LOCATION_PREFERENCES_JSON'")
        Log.i(TAG, "📊 Data: GPS=${preferences.useGpsLocation}, Location=${preferences.location?.getDisplayName() ?: "null"}")
        
        prefs.edit().putString(KEY_LOCATION_PREFERENCES_JSON, settingsJson).apply()
        
        verifyPrefWrite(KEY_LOCATION_PREFERENCES_JSON, settingsJson, "location preferences")
        Log.i(TAG, "✅ Location preferences saved successfully")
        
        Log.i(TAG, "")
    }
    
    private fun saveNotificationPreferences(preferences: PrayerNotificationPreferences) {
        val settingsJson = json.encodeToString(preferences)
        logPrefWriteJson(KEY_NOTIFICATION_PREFERENCES_JSON, settingsJson, "notification preferences")
        
        Log.i(TAG, "")
        Log.i(TAG, "🔔 NOTIFICATION PREFERENCES STORAGE")
        Log.i(TAG, "=".repeat(50))
        Log.i(TAG, "🗂️ Storage Key: '$KEY_NOTIFICATION_PREFERENCES_JSON'")
        Log.i(TAG, "📊 Data: Enabled=${preferences.notificationsEnabled}, Sound=${preferences.notificationSound}, Vibration=${preferences.vibrationEnabled}")
        
        prefs.edit().putString(KEY_NOTIFICATION_PREFERENCES_JSON, settingsJson).apply()
        
        verifyPrefWrite(KEY_NOTIFICATION_PREFERENCES_JSON, settingsJson, "notification preferences")
        Log.i(TAG, "✅ Notification preferences saved successfully")
        Log.i(TAG, "")
    }
    
    /**
     * COMBINE TO LEGACY SETTINGS: Create legacy PrayerSettings from separate preferences
     */
    private fun combineToLegacySettings(
        calculation: PrayerCalculationSettings? = null,
        location: PrayerLocationPreferences? = null,
        notification: PrayerNotificationPreferences? = null
    ): PrayerSettings {
        val calc = calculation ?: getCalculationSettings()
        val loc = location ?: getLocationPreferences()
        val notif = notification ?: getNotificationPreferences()
        
        return PrayerSettings(
            calculationMethod = calc.calculationMethod,
            asrMadhhab = calc.asrMadhhab,
            highLatitudeAdjustment = calc.highLatitudeAdjustment,
            customFajrAngle = calc.customFajrAngle,
            customIshaAngle = calc.customIshaAngle,
            customIshaDelay = calc.customIshaDelay,
            timeOffsets = calc.timeOffsets,
            location = loc.location,
            useGpsLocation = loc.useGpsLocation,
            notificationsEnabled = notif.notificationsEnabled,
            notificationSound = notif.notificationSound,
            vibrationEnabled = notif.vibrationEnabled
        )
    }
    
    /**
     * UPDATE LEGACY COMBINED FLOW: Update the legacy flow when separate preferences change
     */
    private fun updateLegacyCombinedFlow() {
        val combined = combineToLegacySettings()

        Log.i(TAG, "🔄 UPDATE LEGACY COMBINED FLOW:")
        Log.i(TAG, "   - Method: ${combined.calculationMethod.displayName}")
        Log.i(TAG, "   - Custom Fajr: ${combined.customFajrAngle}")
        Log.i(TAG, "   - Custom Isha: ${combined.customIshaAngle}")
        Log.i(TAG, "   - Fajr Offset: ${combined.timeOffsets.fajr}m")
        Log.i(TAG, "   - Isha Offset: ${combined.timeOffsets.isha}m")

        _settingsFlow.value = combined
        val emitted = _settingsFlow.tryEmit(combined)

        Log.i(TAG, "   ✅ Flow updated: value=${_settingsFlow.value != null}, emitted=$emitted")
        Log.i(TAG, "   📊 Subscriber count: ${_settingsFlow.subscriptionCount.value}")
    }
    
    /**
     * DEFAULT SETTINGS PROVIDERS: For new separate preference system
     */
    private fun getDefaultCalculationSettings(): PrayerCalculationSettings {
        return PrayerCalculationSettings(
            calculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
            asrMadhhab = AsrMadhhab.STANDARD,
            highLatitudeAdjustment = HighLatitudeAdjustment.NONE,
            customFajrAngle = null,
            customIshaAngle = null,
            customIshaDelay = null,
            timeOffsets = PrayerTimeOffsets()
        )
    }
    
    private fun getDefaultLocationPreferences(): PrayerLocationPreferences {
        return PrayerLocationPreferences(
            location = null,
            useGpsLocation = true
        )
    }
    
    private fun getDefaultNotificationPreferences(): PrayerNotificationPreferences {
        return PrayerNotificationPreferences(
            notificationsEnabled = true,
            notificationSound = "default",
            vibrationEnabled = true,
            silentDuringPrayerEnabled = true
        )
    }

    // "Silent During Prayer" is on by default but needs Do Not Disturb access to work. Ask for
    // it once PER APP SESSION while it's still missing — not once-ever. (The old persisted flag
    // meant anyone who dismissed the first prompt was never asked again, so silent mode silently
    // never worked.) In-memory so it re-prompts on the next launch until access is granted;
    // granting access makes the caller's `!dndGranted` guard stop it, and turning the feature
    // off also stops it.
    private var dndPromptShownThisSession = false

    /** Whether the "grant Do Not Disturb access" prompt has been shown this app session. */
    fun hasShownDndPrompt(): Boolean = dndPromptShownThisSession

    /** Mark the DND-access prompt as shown for this session (re-asks next launch if still ungranted). */
    fun markDndPromptShown() {
        dndPromptShownThisSession = true
    }
}
