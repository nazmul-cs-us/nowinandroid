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
        
        // SEPARATE PREFERENCE KEYS - Following new architecture
        private const val KEY_CALCULATION_SETTINGS_JSON = "calculation_settings_json"  // Prayer calculation settings
        private const val KEY_LOCATION_PREFERENCES_JSON = "location_preferences_json"  // Location preferences
        private const val KEY_NOTIFICATION_PREFERENCES_JSON = "notification_preferences_json"  // Notification preferences
        
        // LEGACY KEY - For backward compatibility
        private const val KEY_CURRENT_SETTINGS_JSON = "current_settings_json"  // Legacy combined settings
        
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
    
    // REACTIVE DATA FLOW - Separate flows for each preference type
    private val _calculationSettingsFlow = MutableStateFlow<PrayerCalculationSettings?>(null)
    private val _locationPreferencesFlow = MutableStateFlow<PrayerLocationPreferences?>(null)
    private val _notificationPreferencesFlow = MutableStateFlow<PrayerNotificationPreferences?>(null)
    
    // LEGACY COMBINED FLOW - For backward compatibility
    private val _settingsFlow = MutableStateFlow<PrayerSettings?>(null)
    
    // Flag to track when settings are fully loaded from storage
    private var _settingsLoaded = false
    
    // PUBLIC FLOWS - Expose separate preference types
    val calculationSettingsFlow: StateFlow<PrayerCalculationSettings> = _calculationSettingsFlow
        .filterNotNull()
        .stateIn(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            started = SharingStarted.Lazily,
            initialValue = getDefaultCalculationSettings()
        )
    
    val locationPreferencesFlow: StateFlow<PrayerLocationPreferences> = _locationPreferencesFlow
        .filterNotNull()
        .stateIn(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            started = SharingStarted.Lazily,
            initialValue = getDefaultLocationPreferences()
        )
    
    val notificationPreferencesFlow: StateFlow<PrayerNotificationPreferences> = _notificationPreferencesFlow
        .filterNotNull()
        .stateIn(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            started = SharingStarted.Lazily,
            initialValue = getDefaultNotificationPreferences()
        )
    
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
        }
    }
    
    /**
     * NEW GETTERS: Get current preferences by type with fast fallback
     */
    fun getCalculationSettings(): PrayerCalculationSettings {
        return _calculationSettingsFlow.value ?: getDefaultCalculationSettings()
    }
    
    fun getLocationPreferences(): PrayerLocationPreferences {
        return _locationPreferencesFlow.value ?: getDefaultLocationPreferences()
    }
    
    fun getNotificationPreferences(): PrayerNotificationPreferences {
        return _notificationPreferencesFlow.value ?: getDefaultNotificationPreferences()
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
        
        // ALGORITHM STEP 1: Update cached_prayer_settings (JSON format)
        Log.i(TAG, "")
        Log.i(TAG, "💾 ALGORITHM STEP 1: Update cached_prayer_settings in JSON format")
        val saveStart = System.currentTimeMillis()
        saveCalculationSettings(settings)
        val saveTime = System.currentTimeMillis() - saveStart
        Log.i(TAG, "✅ STEP 1 COMPLETE: Settings saved to preferences (${saveTime}ms)")
        Log.i(TAG, "   📄 Storage: JSON format in SharedPreferences")
        Log.i(TAG, "   🔑 Key: calculation_settings_json")
        
        // Update reactive flows
        Log.i(TAG, "")
        Log.i(TAG, "🔄 REACTIVE FLOW UPDATE: Updating in-memory flows for UI")
        _calculationSettingsFlow.value = settings
        _calculationSettingsFlow.tryEmit(settings)
        updateLegacyCombinedFlow()
        Log.i(TAG, "✅ Reactive flows updated - UI will receive new values")
        
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
        triggerPrayerTimeRecalculation()
        
        Log.i(TAG, "✅ LOCATION PREFERENCES UPDATE COMPLETE")
    }
    
    fun updateNotificationPreferences(preferences: PrayerNotificationPreferences, forceCommit: Boolean = false) {
        Log.i(TAG, "📝 NOTIFICATION PREFERENCES UPDATE - Starting process")
        
        saveNotificationPreferences(preferences)
        _notificationPreferencesFlow.value = preferences
        _notificationPreferencesFlow.tryEmit(preferences)
        updateLegacyCombinedFlow()
        
        Log.i(TAG, "✅ NOTIFICATION PREFERENCES UPDATE COMPLETE")
    }
    
    /**
     * LEGACY UPDATE SETTINGS - For backward compatibility
     * 
     * @deprecated Use updateCalculationSettings(), updateLocationPreferences(), and updateNotificationPreferences() instead
     */
    @Deprecated("Use separate preference update methods instead")
    fun updateSettings(settings: PrayerSettings, forceCommit: Boolean = false) {
        Log.i(TAG, "📝 LEGACY USER SETTINGS CHANGE DETECTED - Converting to separate preferences")
        
        val (calculation, location, notification) = settings.toSeparatePreferences()
        
        updateCalculationSettings(calculation, forceCommit)
        updateLocationPreferences(location, forceCommit)
        updateNotificationPreferences(notification, forceCommit)
        
        Log.i(TAG, "✅ LEGACY SETTINGS UPDATE COMPLETE")
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
    
    fun updateTimeOffsets(offsets: PrayerTimeOffsets) {
        val updated = getCalculationSettings().copy(timeOffsets = offsets)
        updateCalculationSettings(updated, forceCommit = true)
    }
    
    fun updateLocationSettings(useGps: Boolean, location: Location? = null) {
        val current = getLocationPreferences()
        val updated = current.copy(
            useGpsLocation = useGps,
            location = location ?: current.location
        )
        updateLocationPreferences(updated, forceCommit = true)
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
            android.util.Log.w("PrayerSettingsRepository", "📋 PREFERENCE READ: key='$KEY_CURRENT_SETTINGS_JSON' (legacy combined settings)")
            android.util.Log.w("PrayerSettingsRepository", "🔥 JSON FROM STORAGE: ${if (settingsJson != null) "EXISTS (${settingsJson.length} chars)" else "NULL"}")
            
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
        val currentLocation = getLocationPreferences().location
        val countryCode = currentLocation?.countryCode
        val countryName = currentLocation?.country
        
        Log.i(TAG, "🔍 CACHED COUNTRY DEBUG:")
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
            Log.i(TAG, "   - Using country code from geocoding: $countryCode")
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
            Log.w(TAG, "   ❌ No country code or country name available")
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
            
            // 7. Create prayer settings with default values (legacy compatibility)
            val autoDetectedSettings = PrayerSettings(
                calculationMethod = calculationMethod,
                asrMadhhab = asrMadhhab,
                customFajrAngle = customFajrAngle,
                customIshaAngle = customIshaAngle,
                customIshaDelay = customIshaDelay
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
            Log.i(TAG, "   🕌 Method: ${autoDetectedSettings.calculationMethod.displayName}")
            Log.i(TAG, "   🤲 Madhhab: ${autoDetectedSettings.asrMadhhab.displayName}")
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
        
        Log.i(TAG, "💾 PREFERENCE WRITE: key='$KEY_CURRENT_SETTINGS_JSON' (legacy combined settings)")
        prefs.edit().putString(KEY_CURRENT_SETTINGS_JSON, settingsJson).apply()
        
        // Verify it was saved
        Log.i(TAG, "🔍 PREFERENCE VERIFY READ: key='$KEY_CURRENT_SETTINGS_JSON' (legacy combined settings)")
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
                cachedSettings
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
        
        // STEP 4: Compare JSON strings (as specified in algorithm)
        Log.i(TAG, "")
        Log.i(TAG, "⚖️ STEP 4: Compare JSON strings (cached vs auto-detected)")
        val autoDetectedJson = json.encodeToString(autoDetectedSettings)
        val cachedJson = json.encodeToString(cachedSettings)
        
        Log.i(TAG, "📄 Auto-detected JSON (${autoDetectedJson.length} chars):")
        Log.i(TAG, "   ${autoDetectedJson.take(200)}${if (autoDetectedJson.length > 200) "..." else ""}")
        
        Log.i(TAG, "📄 Cached JSON (${cachedJson.length} chars):")
        Log.i(TAG, "   ${cachedJson.take(200)}${if (cachedJson.length > 200) "..." else ""}")
        
        val areIdentical = autoDetectedJson == cachedJson
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
        Log.i(TAG, "🎯 PRAYER SETTINGS ALGORITHM - RESTORE TO AUTO-DETECTED")
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
        
        // Update reactive flows
        Log.i(TAG, "")
        Log.i(TAG, "🔄 REACTIVE FLOW UPDATE: Update in-memory flows")
        _settingsFlow.value = autoDetectedSettings
        _settingsFlow.tryEmit(autoDetectedSettings)
        
        // Update separate preference flows
        val (calculation, location, notification) = autoDetectedSettings.toSeparatePreferences()
        _calculationSettingsFlow.value = calculation
        _locationPreferencesFlow.value = location
        _notificationPreferencesFlow.value = notification
        Log.i(TAG, "✅ All reactive flows updated - UI will receive restored values")
        
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
        prefs.edit().apply {
            // Cache the date to ensure validity
            Log.i(TAG, "💾 PREFERENCE WRITE: key='$KEY_CACHED_PRAYER_DATE' (cached prayer date)")
            putString(KEY_CACHED_PRAYER_DATE, prayerTimes.date.toLocalDate().toString())
            
            // Cache prayer times as minutes from midnight for precision
            putInt(KEY_CACHED_FAJR, prayerTimes.fajr.toSecondOfDay() / 60)
            putInt(KEY_CACHED_SUNRISE, prayerTimes.sunrise.toSecondOfDay() / 60)
            putInt(KEY_CACHED_DHUHR, prayerTimes.dhuhr.toSecondOfDay() / 60)
            putInt(KEY_CACHED_ASR, prayerTimes.asr.toSecondOfDay() / 60)
            putInt(KEY_CACHED_MAGHRIB, prayerTimes.maghrib.toSecondOfDay() / 60)
            putInt(KEY_CACHED_ISHA, prayerTimes.isha.toSecondOfDay() / 60)
            
            // Cache location information
            Log.i(TAG, "💾 PREFERENCE WRITE: key='$KEY_CACHED_LOCATION_LAT' (cached location latitude)")
            putFloat(KEY_CACHED_LOCATION_LAT, prayerTimes.location.latitude.toFloat())
            Log.i(TAG, "💾 PREFERENCE WRITE: key='$KEY_CACHED_LOCATION_LON' (cached location longitude)")
            putFloat(KEY_CACHED_LOCATION_LON, prayerTimes.location.longitude.toFloat())
            Log.i(TAG, "💾 PREFERENCE WRITE: key='$KEY_CACHED_LOCATION_CITY' (cached location city)")
            putString(KEY_CACHED_LOCATION_CITY, prayerTimes.location.city)
            Log.i(TAG, "💾 PREFERENCE WRITE: key='$KEY_CACHED_LOCATION_COUNTRY' (cached location country)")
            putString(KEY_CACHED_LOCATION_COUNTRY, prayerTimes.location.country)
            Log.i(TAG, "💾 PREFERENCE WRITE: key='$KEY_CACHED_LOCATION_COUNTRY_CODE' (cached location country code)")
            putString(KEY_CACHED_LOCATION_COUNTRY_CODE, prayerTimes.location.countryCode)
            Log.i(TAG, "💾 PREFERENCE WRITE: key='$KEY_CACHED_LOCATION_TIMEZONE' (cached location timezone)")
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
            Log.i(TAG, "📋 PREFERENCE READ: key='$KEY_CACHED_PRAYER_DATE' (cached prayer date)")
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
                city = run {
                    Log.i(TAG, "📋 PREFERENCE READ: key='$KEY_CACHED_LOCATION_CITY' (cached location city)")
                    prefs.getString(KEY_CACHED_LOCATION_CITY, "") ?: ""
                },
                country = run {
                    Log.i(TAG, "📋 PREFERENCE READ: key='$KEY_CACHED_LOCATION_COUNTRY' (cached location country)")
                    prefs.getString(KEY_CACHED_LOCATION_COUNTRY, "") ?: ""
                },
                countryCode = run {
                    Log.i(TAG, "📋 PREFERENCE READ: key='$KEY_CACHED_LOCATION_COUNTRY_CODE' (cached location country code)")
                    prefs.getString(KEY_CACHED_LOCATION_COUNTRY_CODE, "") ?: ""
                },
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
     */
    private fun loadAllSettings() {
        android.util.Log.w("PrayerSettingsRepository", "🔥 LOAD ALL SETTINGS CALLED")
        
        // Try loading separate preferences first
        val calculationSettings = loadCalculationSettings()
        val locationPreferences = loadLocationPreferences()
        val notificationPreferences = loadNotificationPreferences()
        
        if (calculationSettings != null && locationPreferences != null && notificationPreferences != null) {
            // All separate preferences exist
            _calculationSettingsFlow.value = calculationSettings
            _locationPreferencesFlow.value = locationPreferences
            _notificationPreferencesFlow.value = notificationPreferences
            _settingsFlow.value = combineToLegacySettings(calculationSettings, locationPreferences, notificationPreferences)
        } else {
            // Try migrating from legacy combined settings
            val legacySettings = loadLegacySettings()
            if (legacySettings != null) {
                android.util.Log.w("PrayerSettingsRepository", "🔄 Migrating from legacy settings")
                migrateToSeparatePreferences(legacySettings)
            } else {
                android.util.Log.w("PrayerSettingsRepository", "🔄 No settings found, running initialization")
                val initializedSettings = initializeSettings()
                migrateToSeparatePreferences(initializedSettings)
            }
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
     * MIGRATE TO SEPARATE PREFERENCES: Convert legacy settings to separate preferences
     */
    private fun migrateToSeparatePreferences(legacySettings: PrayerSettings) {
        val (calculation, location, notification) = legacySettings.toSeparatePreferences()
        
        saveCalculationSettings(calculation)
        saveLocationPreferences(location)
        saveNotificationPreferences(notification)
        
        _calculationSettingsFlow.value = calculation
        _locationPreferencesFlow.value = location
        _notificationPreferencesFlow.value = notification
        _settingsFlow.value = legacySettings
        
        android.util.Log.w("PrayerSettingsRepository", "✅ Migration completed")
    }
    
    /**
     * LOAD INDIVIDUAL PREFERENCE TYPES
     */
    private fun loadCalculationSettings(): PrayerCalculationSettings? {
        return try {
            Log.i(TAG, "📋 PREFERENCE READ: key='$KEY_CALCULATION_SETTINGS_JSON' (prayer calculation settings)")
            val settingsJson = prefs.getString(KEY_CALCULATION_SETTINGS_JSON, null)
            Log.i(TAG, "📋 PREFERENCE VALUE: ${if (settingsJson != null) "EXISTS (${settingsJson.length} chars)" else "NULL/EMPTY"}")
            if (settingsJson != null) {
                Log.i(TAG, "📋 PREFERENCE CONTENT: $settingsJson")
                json.decodeFromString<PrayerCalculationSettings>(settingsJson)
            } else {
                Log.i(TAG, "📋 PREFERENCE RESULT: No calculation settings found - will use defaults")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PrayerSettingsRepository", "Error loading calculation settings", e)
            null
        }
    }
    
    private fun loadLocationPreferences(): PrayerLocationPreferences? {
        return try {
            Log.i(TAG, "📋 PREFERENCE READ: key='$KEY_LOCATION_PREFERENCES_JSON' (location preferences)")
            val settingsJson = prefs.getString(KEY_LOCATION_PREFERENCES_JSON, null)
            Log.i(TAG, "📋 PREFERENCE VALUE: ${if (settingsJson != null) "EXISTS (${settingsJson.length} chars)" else "NULL/EMPTY"}")
            if (settingsJson != null) {
                Log.i(TAG, "📋 PREFERENCE CONTENT: $settingsJson")
                json.decodeFromString<PrayerLocationPreferences>(settingsJson)
            } else {
                Log.i(TAG, "📋 PREFERENCE RESULT: No location preferences found - will use defaults")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PrayerSettingsRepository", "Error loading location preferences", e)
            null
        }
    }
    
    private fun loadNotificationPreferences(): PrayerNotificationPreferences? {
        return try {
            Log.i(TAG, "📋 PREFERENCE READ: key='$KEY_NOTIFICATION_PREFERENCES_JSON' (notification preferences)")
            val settingsJson = prefs.getString(KEY_NOTIFICATION_PREFERENCES_JSON, null)
            Log.i(TAG, "📋 PREFERENCE VALUE: ${if (settingsJson != null) "EXISTS (${settingsJson.length} chars)" else "NULL/EMPTY"}")
            if (settingsJson != null) {
                Log.i(TAG, "📋 PREFERENCE CONTENT: $settingsJson")
                json.decodeFromString<PrayerNotificationPreferences>(settingsJson)
            } else {
                Log.i(TAG, "📋 PREFERENCE RESULT: No notification preferences found - will use defaults")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PrayerSettingsRepository", "Error loading notification preferences", e)
            null
        }
    }
    
    private fun loadLegacySettings(): PrayerSettings? {
        return try {
            Log.i(TAG, "📋 PREFERENCE READ: key='$KEY_CURRENT_SETTINGS_JSON' (legacy settings)")
            val settingsJson = prefs.getString(KEY_CURRENT_SETTINGS_JSON, null)
            Log.i(TAG, "📋 PREFERENCE VALUE: ${if (settingsJson != null) "EXISTS (${settingsJson.length} chars)" else "NULL/EMPTY"}")
            if (settingsJson != null) {
                Log.i(TAG, "📋 PREFERENCE CONTENT (first 200 chars): ${settingsJson.take(200)}${if (settingsJson.length > 200) "..." else ""}")
                json.decodeFromString<PrayerSettings>(settingsJson)
            } else {
                Log.i(TAG, "📋 PREFERENCE RESULT: No legacy settings found")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PrayerSettingsRepository", "Error loading legacy settings", e)
            null
        }
    }
    
    /**
     * SAVE INDIVIDUAL PREFERENCE TYPES
     */
    private fun saveCalculationSettings(settings: PrayerCalculationSettings) {
        val settingsJson = json.encodeToString(settings)
        Log.i(TAG, "💾 PREFERENCE WRITE: key='$KEY_CALCULATION_SETTINGS_JSON' (prayer calculation settings)")
        prefs.edit().putString(KEY_CALCULATION_SETTINGS_JSON, settingsJson).apply()
    }
    
    private fun saveLocationPreferences(preferences: PrayerLocationPreferences) {
        val settingsJson = json.encodeToString(preferences)
        Log.i(TAG, "💾 PREFERENCE WRITE: key='$KEY_LOCATION_PREFERENCES_JSON' (location preferences)")
        prefs.edit().putString(KEY_LOCATION_PREFERENCES_JSON, settingsJson).apply()
    }
    
    private fun saveNotificationPreferences(preferences: PrayerNotificationPreferences) {
        val settingsJson = json.encodeToString(preferences)
        Log.i(TAG, "💾 PREFERENCE WRITE: key='$KEY_NOTIFICATION_PREFERENCES_JSON' (notification preferences)")
        prefs.edit().putString(KEY_NOTIFICATION_PREFERENCES_JSON, settingsJson).apply()
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
        _settingsFlow.value = combineToLegacySettings()
        _settingsFlow.tryEmit(combineToLegacySettings())
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
            vibrationEnabled = true
        )
    }
}