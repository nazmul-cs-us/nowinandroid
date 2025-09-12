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
    fun updateSettings(settings: PrayerSettings, forceCommit: Boolean = false) {
        android.util.Log.w("PrayerSettingsRepository", "🔥🔥 UPDATE SETTINGS CALLED:")
        android.util.Log.w("PrayerSettingsRepository", "  ASR: ${settings.asrMadhhab}")
        android.util.Log.w("PrayerSettingsRepository", "  Custom Isha: ${settings.customIshaAngle}")
        android.util.Log.w("PrayerSettingsRepository", "  Custom Fajr: ${settings.customFajrAngle}")
        android.util.Log.w("PrayerSettingsRepository", "  Force Commit: $forceCommit")
        
        // STACK TRACE TO IDENTIFY CALLER
        android.util.Log.w("PrayerSettingsRepository", "🔥 CALLER STACK TRACE:")
        Thread.currentThread().stackTrace.take(8).forEach { element ->
            android.util.Log.w("PrayerSettingsRepository", "  at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})")
        }
        
        saveSettings(settings, forceCommit)
        _settingsFlow.value = settings
        // Force trigger flow for UI updates
        _settingsFlow.tryEmit(settings)
        
        // ROBUST LOGGING: Track flow emission
        Log.i(TAG, "🔄 FLOW EMISSION TRIGGERED:")
        Log.i(TAG, "   - Settings flow updated with method: ${settings.calculationMethod.displayName}")
        Log.i(TAG, "   - Flow has ${_settingsFlow.subscriptionCount.value} subscribers")
        Log.i(TAG, "   - Current flow value: ${_settingsFlow.value?.calculationMethod?.displayName}")
        Log.i(TAG, "   - Force commit: $forceCommit")
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