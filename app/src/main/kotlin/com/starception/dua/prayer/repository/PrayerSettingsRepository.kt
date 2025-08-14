package com.starception.dua.prayer.repository

import android.content.Context
import android.content.SharedPreferences
import com.starception.dua.prayer.model.*
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
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing prayer settings and preferences
 */
@Singleton
class PrayerSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "prayer_settings"
        private const val KEY_CALCULATION_METHOD = "calculation_method"
        private const val KEY_ASR_MADHHAB = "asr_madhhab"
        private const val KEY_HIGH_LATITUDE_ADJUSTMENT = "high_latitude_adjustment"
        private const val KEY_CUSTOM_FAJR_ANGLE = "custom_fajr_angle"
        private const val KEY_CUSTOM_ISHA_ANGLE = "custom_isha_angle"
        private const val KEY_CUSTOM_ISHA_DELAY = "custom_isha_delay"
        private const val KEY_USE_GPS_LOCATION = "use_gps_location"
        private const val KEY_MANUAL_LATITUDE = "manual_latitude"
        private const val KEY_MANUAL_LONGITUDE = "manual_longitude"
        private const val KEY_MANUAL_CITY = "manual_city"
        private const val KEY_MANUAL_COUNTRY = "manual_country"
        private const val KEY_MANUAL_TIMEZONE_OFFSET = "manual_timezone_offset"
        
        // Time offset keys
        private const val KEY_OFFSET_FAJR = "offset_fajr"
        private const val KEY_OFFSET_SUNRISE = "offset_sunrise"
        private const val KEY_OFFSET_DHUHR = "offset_dhuhr"
        private const val KEY_OFFSET_ASR = "offset_asr"
        private const val KEY_OFFSET_MAGHRIB = "offset_maghrib"
        private const val KEY_OFFSET_ISHA = "offset_isha"
        
        // Notification settings
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_NOTIFY_BEFORE_MINUTES = "notify_before_minutes"
        
        // Prayer times cache keys
        private const val KEY_CACHED_PRAYER_DATE = "cached_prayer_date"
        private const val KEY_CACHED_FAJR = "cached_fajr"
        private const val KEY_CACHED_SUNRISE = "cached_sunrise"
        private const val KEY_CACHED_DHUHR = "cached_dhuhr"
        private const val KEY_CACHED_ASR = "cached_asr"
        private const val KEY_CACHED_MAGHRIB = "cached_maghrib"
        private const val KEY_CACHED_ISHA = "cached_isha"
        private const val KEY_CACHED_LOCATION_LAT = "cached_location_lat"
        private const val KEY_CACHED_LOCATION_LON = "cached_location_lon"
        private const val KEY_CACHED_LOCATION_CITY = "cached_location_city"
        private const val KEY_CACHED_LOCATION_COUNTRY = "cached_location_country"
        private const val KEY_CACHED_LOCATION_TIMEZONE = "cached_location_timezone"
    }
    
    // Use lazy initialization to prevent main thread blocking during repository creation
    private val prefs: SharedPreferences by lazy { 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val _settingsFlow = MutableStateFlow<PrayerSettings?>(null)
    val settingsFlow: StateFlow<PrayerSettings> = _settingsFlow
        .filterNotNull()
        .stateIn(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            started = SharingStarted.Eagerly, // Back to Eagerly to load settings immediately
            initialValue = getDefaultSettings()
        )
    
    // Initialize settings on startup in background
    init {
        // Load settings in background to avoid StrictMode issues
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            _settingsFlow.value = loadSettings()
        }
    }
    
    /**
     * Gets current prayer settings
     * Returns default settings if async loading is not complete yet to prevent main thread blocking
     */
    fun getSettings(): PrayerSettings {
        return _settingsFlow.value ?: PrayerSettings() // Return default settings instead of blocking main thread
    }
    
    /**
     * Updates prayer settings
     */
    fun updateSettings(settings: PrayerSettings) {
        android.util.Log.d("PrayerSettingsRepository", "Updating settings - ASR: ${settings.asrMadhhab}")
        saveSettings(settings)
        _settingsFlow.value = settings
        // Force trigger flow for UI updates
        _settingsFlow.tryEmit(settings)
    }
    
    /**
     * Updates calculation method
     */
    fun updateCalculationMethod(method: CalculationMethod) {
        val updated = getSettings().copy(calculationMethod = method)
        updateSettings(updated)
    }
    
    /**
     * Updates Asr madhhab
     */
    fun updateAsrMadhhab(madhhab: AsrMadhhab) {
        val updated = getSettings().copy(asrMadhhab = madhhab)
        updateSettings(updated)
    }
    
    /**
     * Updates high latitude adjustment method
     */
    fun updateHighLatitudeAdjustment(adjustment: HighLatitudeAdjustment) {
        val updated = getSettings().copy(highLatitudeAdjustment = adjustment)
        updateSettings(updated)
    }
    
    /**
     * Updates time offsets
     */
    fun updateTimeOffsets(offsets: PrayerTimeOffsets) {
        val updated = getSettings().copy(timeOffsets = offsets)
        updateSettings(updated)
    }
    
    /**
     * Updates location settings
     */
    fun updateLocationSettings(useGps: Boolean, location: Location? = null) {
        val currentSettings = getSettings()
        val updated = currentSettings.copy(
            useGpsLocation = useGps,
            location = location ?: currentSettings.location
        )
        updateSettings(updated)
    }
    
    /**
     * Updates notification settings
     */
    fun updateNotificationSettings(enabled: Boolean, beforeMinutes: Int = 10) {
        prefs.edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
            .putInt(KEY_NOTIFY_BEFORE_MINUTES, beforeMinutes)
            .apply()
    }
    
    /**
     * Gets notification settings
     */
    fun isNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
    fun getNotifyBeforeMinutes(): Int = prefs.getInt(KEY_NOTIFY_BEFORE_MINUTES, 10)
    
    /**
     * Loads settings from SharedPreferences
     */
    private fun loadSettings(): PrayerSettings {
        android.util.Log.d("PrayerSettingsRepository", "Loading settings from SharedPreferences")
        val calculationMethod = try {
            CalculationMethod.valueOf(prefs.getString(KEY_CALCULATION_METHOD, CalculationMethod.MUSLIM_WORLD_LEAGUE.name) ?: CalculationMethod.MUSLIM_WORLD_LEAGUE.name)
        } catch (e: Exception) {
            CalculationMethod.MUSLIM_WORLD_LEAGUE
        }
        
        val asrMadhhab = try {
            val asrValue = prefs.getString(KEY_ASR_MADHHAB, AsrMadhhab.STANDARD.name) ?: AsrMadhhab.STANDARD.name
            android.util.Log.d("PrayerSettingsRepository", "Loading ASR madhhab: $asrValue")
            AsrMadhhab.valueOf(asrValue)
        } catch (e: Exception) {
            android.util.Log.e("PrayerSettingsRepository", "Error loading ASR madhhab: ${e.message}")
            AsrMadhhab.STANDARD
        }
        
        val highLatitudeAdjustment = try {
            HighLatitudeAdjustment.valueOf(prefs.getString(KEY_HIGH_LATITUDE_ADJUSTMENT, HighLatitudeAdjustment.NONE.name) ?: HighLatitudeAdjustment.NONE.name)
        } catch (e: Exception) {
            HighLatitudeAdjustment.NONE
        }
        
        val timeOffsets = PrayerTimeOffsets(
            fajr = prefs.getInt(KEY_OFFSET_FAJR, 0),
            sunrise = prefs.getInt(KEY_OFFSET_SUNRISE, 0),
            dhuhr = prefs.getInt(KEY_OFFSET_DHUHR, 0),
            asr = prefs.getInt(KEY_OFFSET_ASR, 0),
            maghrib = prefs.getInt(KEY_OFFSET_MAGHRIB, 0),
            isha = prefs.getInt(KEY_OFFSET_ISHA, 0)
        )
        
        val location = if (prefs.contains(KEY_MANUAL_LATITUDE) && prefs.contains(KEY_MANUAL_LONGITUDE)) {
            Location(
                latitude = prefs.getFloat(KEY_MANUAL_LATITUDE, 0f).toDouble(),
                longitude = prefs.getFloat(KEY_MANUAL_LONGITUDE, 0f).toDouble(),
                city = prefs.getString(KEY_MANUAL_CITY, "") ?: "",
                country = prefs.getString(KEY_MANUAL_COUNTRY, "") ?: "",
                timeZoneOffset = prefs.getFloat(KEY_MANUAL_TIMEZONE_OFFSET, 0f).toDouble()
            )
        } else null
        
        return PrayerSettings(
            calculationMethod = calculationMethod,
            asrMadhhab = asrMadhhab,
            highLatitudeAdjustment = highLatitudeAdjustment,
            customFajrAngle = if (prefs.contains(KEY_CUSTOM_FAJR_ANGLE)) prefs.getFloat(KEY_CUSTOM_FAJR_ANGLE, 0f).toDouble() else null,
            customIshaAngle = if (prefs.contains(KEY_CUSTOM_ISHA_ANGLE)) prefs.getFloat(KEY_CUSTOM_ISHA_ANGLE, 0f).toDouble() else null,
            customIshaDelay = if (prefs.contains(KEY_CUSTOM_ISHA_DELAY)) prefs.getInt(KEY_CUSTOM_ISHA_DELAY, 0) else null,
            timeOffsets = timeOffsets,
            useGpsLocation = prefs.getBoolean(KEY_USE_GPS_LOCATION, true),
            location = location
        )
    }
    
    /**
     * Gets default prayer settings without disk I/O
     */
    private fun getDefaultSettings(): PrayerSettings {
        return PrayerSettings(
            calculationMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
            asrMadhhab = AsrMadhhab.STANDARD,
            highLatitudeAdjustment = HighLatitudeAdjustment.NONE,
            customFajrAngle = null,
            customIshaAngle = null,
            customIshaDelay = null,
            timeOffsets = PrayerTimeOffsets(),
            useGpsLocation = true,
            location = null
        )
    }
    
    /**
     * Saves settings to SharedPreferences
     */
    private fun saveSettings(settings: PrayerSettings) {
        android.util.Log.d("PrayerSettingsRepository", "Saving settings - ASR: ${settings.asrMadhhab.name}")
        prefs.edit().apply {
            putString(KEY_CALCULATION_METHOD, settings.calculationMethod.name)
            putString(KEY_ASR_MADHHAB, settings.asrMadhhab.name)
            putString(KEY_HIGH_LATITUDE_ADJUSTMENT, settings.highLatitudeAdjustment.name)
            putBoolean(KEY_USE_GPS_LOCATION, settings.useGpsLocation)
            
            // Custom angles
            settings.customFajrAngle?.let { putFloat(KEY_CUSTOM_FAJR_ANGLE, it.toFloat()) } ?: remove(KEY_CUSTOM_FAJR_ANGLE)
            settings.customIshaAngle?.let { putFloat(KEY_CUSTOM_ISHA_ANGLE, it.toFloat()) } ?: remove(KEY_CUSTOM_ISHA_ANGLE)
            settings.customIshaDelay?.let { putInt(KEY_CUSTOM_ISHA_DELAY, it) } ?: remove(KEY_CUSTOM_ISHA_DELAY)
            
            // Time offsets
            putInt(KEY_OFFSET_FAJR, settings.timeOffsets.fajr)
            putInt(KEY_OFFSET_SUNRISE, settings.timeOffsets.sunrise)
            putInt(KEY_OFFSET_DHUHR, settings.timeOffsets.dhuhr)
            putInt(KEY_OFFSET_ASR, settings.timeOffsets.asr)
            putInt(KEY_OFFSET_MAGHRIB, settings.timeOffsets.maghrib)
            putInt(KEY_OFFSET_ISHA, settings.timeOffsets.isha)
            
            // Location
            settings.location?.let { location ->
                putFloat(KEY_MANUAL_LATITUDE, location.latitude.toFloat())
                putFloat(KEY_MANUAL_LONGITUDE, location.longitude.toFloat())
                putString(KEY_MANUAL_CITY, location.city)
                putString(KEY_MANUAL_COUNTRY, location.country)
                putFloat(KEY_MANUAL_TIMEZONE_OFFSET, location.timeZoneOffset.toFloat())
            } ?: run {
                remove(KEY_MANUAL_LATITUDE)
                remove(KEY_MANUAL_LONGITUDE)
                remove(KEY_MANUAL_CITY)
                remove(KEY_MANUAL_COUNTRY)
                remove(KEY_MANUAL_TIMEZONE_OFFSET)
            }
            
            apply()
        }.also {
            android.util.Log.d("PrayerSettingsRepository", "Settings saved to SharedPreferences - ASR: ${settings.asrMadhhab.name}")
        }
    }
    
    /**
     * Resets all settings to defaults
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _settingsFlow.value = loadSettings()
    }
    
    /**
     * Force sets ASR method to Standard (for debugging/fixing incorrect settings)
     */
    fun forceSetAsrToStandard() {
        val updated = getSettings().copy(asrMadhhab = AsrMadhhab.STANDARD)
        updateSettings(updated)
    }
    
    /**
     * Caches prayer times for quick loading on app startup
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
            
            apply()
        }
    }
    
    /**
     * Gets cached prayer times if available and valid for today
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
     * Clears cached prayer times
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
     * Checks if cached prayer times are available and valid for today
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