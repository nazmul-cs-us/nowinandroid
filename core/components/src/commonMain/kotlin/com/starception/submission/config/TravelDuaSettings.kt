package com.starception.submission.config

/**
 * TRAVEL DUA SETTINGS: User-configurable settings for travel dua playback
 *
 * These settings control when and how the travel dua is played during driving.
 */
data class TravelDuaSettings(
    /**
     * Whether travel dua feature is enabled
     * Default: true
     */
    val enabled: Boolean = true,

    /**
     * Cooldown period in minutes before travel dua can be played again
     * This prevents the dua from replaying during the same journey
     * Default: 5 minutes
     */
    val cooldownMinutes: Int = 5,

    /**
     * How long user must be driving (in seconds) before dua plays
     * This ensures user is actually traveling, not just moving the car
     * Default: 60 seconds (1 minute)
     */
    val playbackDelaySeconds: Int = 60,

    /**
     * Maximum gap (in minutes) allowed between driving sessions to continue accumulating time
     * This handles traffic lights and brief stops
     * Default: 5 minutes
     */
    val gapToleranceMinutes: Int = 5,

    /**
     * Minimum speed in km/h required to detect driving activity
     * Lower values detect slower driving (traffic, parking lots)
     * Higher values avoid false positives from GPS drift
     * Default: 10 km/h (sensitive - detects slow driving in traffic/parking)
     * Range: 10-40 km/h
     */
    val drivingSpeedThresholdKmh: Int = 10
) {
    // Computed properties for milliseconds
    val cooldownMillis: Long get() = cooldownMinutes * 60 * 1000L
    val playbackDelayMillis: Long get() = playbackDelaySeconds * 1000L
    val gapToleranceMillis: Long get() = gapToleranceMinutes * 60 * 1000L

    // Computed property for speed threshold in m/s (used by ActivityDetectionService)
    val drivingSpeedThresholdMps: Double get() = drivingSpeedThresholdKmh / 3.6

    companion object {
        // Keys for SharedPreferences
        const val PREFS_NAME = "travel_dua_settings"
        const val KEY_ENABLED = "travel_dua_enabled"
        const val KEY_COOLDOWN_MINUTES = "travel_dua_cooldown_minutes"
        const val KEY_PLAYBACK_DELAY_SECONDS = "travel_dua_playback_delay_seconds"
        const val KEY_GAP_TOLERANCE_MINUTES = "travel_dua_gap_tolerance_minutes"
        const val KEY_DRIVING_SPEED_THRESHOLD_KMH = "travel_dua_driving_speed_threshold_kmh"
        const val KEY_PENDING_ALARM_TOKEN = "travel_dua_pending_alarm_token"
        const val KEY_PENDING_ALARM_TRIGGER_ELAPSED = "travel_dua_pending_alarm_trigger_elapsed"
        const val KEY_IS_DRIVING = "travel_dua_is_driving"
        const val KEY_DUA_PLAYED_FOR_CURRENT_TRIP = "travel_dua_played_for_current_trip"
        const val KEY_DRIVING_STOP_TIME = "travel_dua_driving_stop_time"
        const val KEY_LAST_RELIABLE_SPEED_ELAPSED = "travel_dua_last_reliable_speed_elapsed"
        const val KEY_LAST_RELIABLE_SPEED_MPS = "travel_dua_last_reliable_speed_mps"
        const val KEY_GOOGLE_DRIVING_CONFIRMED = "google_driving_confirmed"
        const val KEY_GOOGLE_DRIVING_CONFIRMED_ELAPSED =
            "google_driving_confirmed_elapsed"

        // Default values
        const val DEFAULT_COOLDOWN_MINUTES = 5
        const val DEFAULT_PLAYBACK_DELAY_SECONDS = 60
        const val DEFAULT_GAP_TOLERANCE_MINUTES = 5
        const val DEFAULT_DRIVING_SPEED_THRESHOLD_KMH = 10
    }
}
