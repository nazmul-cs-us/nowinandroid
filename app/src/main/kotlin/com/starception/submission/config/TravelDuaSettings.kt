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
    val gapToleranceMinutes: Int = 5
) {
    // Computed properties for milliseconds
    val cooldownMillis: Long get() = cooldownMinutes * 60 * 1000L
    val playbackDelayMillis: Long get() = playbackDelaySeconds * 1000L
    val gapToleranceMillis: Long get() = gapToleranceMinutes * 60 * 1000L

    companion object {
        // Keys for SharedPreferences
        const val PREFS_NAME = "travel_dua_settings"
        const val KEY_ENABLED = "travel_dua_enabled"
        const val KEY_COOLDOWN_MINUTES = "travel_dua_cooldown_minutes"
        const val KEY_PLAYBACK_DELAY_SECONDS = "travel_dua_playback_delay_seconds"
        const val KEY_GAP_TOLERANCE_MINUTES = "travel_dua_gap_tolerance_minutes"

        // Default values
        const val DEFAULT_COOLDOWN_MINUTES = 5
        const val DEFAULT_PLAYBACK_DELAY_SECONDS = 60
        const val DEFAULT_GAP_TOLERANCE_MINUTES = 5
    }
}
