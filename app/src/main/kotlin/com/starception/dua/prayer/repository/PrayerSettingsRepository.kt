package com.starception.dua.prayer.repository

import android.content.Context
import android.content.SharedPreferences
import com.starception.dua.prayer.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<PrayerSettings> = _settingsFlow.asStateFlow()
    
    /**
     * Gets current prayer settings
     */
    fun getSettings(): PrayerSettings = _settingsFlow.value
    
    /**
     * Updates prayer settings
     */
    fun updateSettings(settings: PrayerSettings) {
        saveSettings(settings)
        _settingsFlow.value = settings
    }
    
    /**
     * Updates calculation method
     */
    fun updateCalculationMethod(method: CalculationMethod) {
        val updated = _settingsFlow.value.copy(calculationMethod = method)
        updateSettings(updated)
    }
    
    /**
     * Updates Asr madhhab
     */
    fun updateAsrMadhhab(madhhab: AsrMadhhab) {
        val updated = _settingsFlow.value.copy(asrMadhhab = madhhab)
        updateSettings(updated)
    }
    
    /**
     * Updates high latitude adjustment method
     */
    fun updateHighLatitudeAdjustment(adjustment: HighLatitudeAdjustment) {
        val updated = _settingsFlow.value.copy(highLatitudeAdjustment = adjustment)
        updateSettings(updated)
    }
    
    /**
     * Updates time offsets
     */
    fun updateTimeOffsets(offsets: PrayerTimeOffsets) {
        val updated = _settingsFlow.value.copy(timeOffsets = offsets)
        updateSettings(updated)
    }
    
    /**
     * Updates location settings
     */
    fun updateLocationSettings(useGps: Boolean, location: Location? = null) {
        val updated = _settingsFlow.value.copy(
            useGpsLocation = useGps,
            location = location ?: _settingsFlow.value.location
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
        val calculationMethod = try {
            CalculationMethod.valueOf(prefs.getString(KEY_CALCULATION_METHOD, CalculationMethod.MUSLIM_WORLD_LEAGUE.name) ?: CalculationMethod.MUSLIM_WORLD_LEAGUE.name)
        } catch (e: Exception) {
            CalculationMethod.MUSLIM_WORLD_LEAGUE
        }
        
        val asrMadhhab = try {
            AsrMadhhab.valueOf(prefs.getString(KEY_ASR_MADHHAB, AsrMadhhab.STANDARD.name) ?: AsrMadhhab.STANDARD.name)
        } catch (e: Exception) {
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
     * Saves settings to SharedPreferences
     */
    private fun saveSettings(settings: PrayerSettings) {
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
        }
    }
    
    /**
     * Resets all settings to defaults
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _settingsFlow.value = loadSettings()
    }
}