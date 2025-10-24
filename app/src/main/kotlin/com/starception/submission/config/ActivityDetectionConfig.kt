package com.starception.submission.config

/**
 * ACTIVITY DETECTION CONFIGURATION
 * 
 * Centralized configuration for all activity detection and dua playback settings.
 * Modify these values to tune the behavior of the activity detection system.
 */
object ActivityDetectionConfig {
    
    // ==================== TRAVEL DUA SETTINGS ====================
    
    /**
     * Cooldown period before travel dua can be played again
     * Default: 5 minutes (prevents replaying at traffic lights)
     */
    const val DUA_COOLDOWN_MINUTES = 5
    const val DUA_COOLDOWN_MILLIS = DUA_COOLDOWN_MINUTES * 60 * 1000L
    
    /**
     * Delay before playing travel dua after driving is detected
     * User MUST remain in driving mode for this entire duration
     * Default: 1 minute (ensures user is actually traveling, not just moving the car)
     */
    const val DUA_PLAYBACK_DELAY_SECONDS = 60
    const val DUA_PLAYBACK_DELAY_MILLIS = DUA_PLAYBACK_DELAY_SECONDS * 1000L
    
    // ==================== ACTIVITY DETECTION THRESHOLDS ====================
    
    /**
     * Number of consecutive detections required before changing activity
     * Lower = faster response, but more false positives
     * Higher = more stable, but slower to detect changes
     * Default: 2 (4 seconds with 2-second analysis interval)
     */
    const val STABLE_DETECTION_COUNT = 2
    
    /**
     * Time window for collecting sensor data (seconds)
     * Default: 3 seconds
     */
    const val DATA_WINDOW_SIZE = 3
    
    /**
     * How often to analyze sensor data and detect activity (milliseconds)
     * Default: 2000ms (2 seconds)
     */
    const val ANALYSIS_INTERVAL_MILLIS = 2000
    
    // ==================== WALKING DETECTION ====================
    
    /**
     * Minimum variance for walking detection
     * Lower = more sensitive (may detect slow walking or phone pickup)
     * Higher = less sensitive (only detects clear walking)
     * Default: 0.4
     */
    const val WALKING_VARIANCE_MIN = 0.4
    
    /**
     * Maximum variance for walking (above this = running)
     * Default: 2.5
     */
    const val WALKING_VARIANCE_MAX = 2.5
    
    /**
     * Minimum gyroscope value for walking
     * Default: 0.3 rad/s
     */
    const val WALKING_GYRO_MIN = 0.3
    
    /**
     * Maximum gyroscope value for walking (above this = running)
     * Default: 1.8 rad/s
     */
    const val WALKING_GYRO_MAX = 1.8
    
    /**
     * Minimum speed for walking (m/s)
     * Default: 0.3 m/s (~1 km/h)
     */
    const val WALKING_SPEED_MIN = 0.3
    
    /**
     * Maximum speed for walking (m/s)
     * Default: 2.5 m/s (~9 km/h)
     */
    const val WALKING_SPEED_MAX = 2.5
    
    // ==================== RUNNING DETECTION ====================
    
    /**
     * Minimum variance for running detection
     * Default: 2.0
     */
    const val RUNNING_VARIANCE_MIN = 2.0
    
    /**
     * Minimum gyroscope value for running
     * Default: 1.5 rad/s
     */
    const val RUNNING_GYRO_MIN = 1.5
    
    /**
     * Minimum speed for running (m/s)
     * Default: 2.0 m/s (~7 km/h)
     */
    const val RUNNING_SPEED_MIN = 2.0
    
    // ==================== DRIVING DETECTION ====================
    
    /**
     * Minimum speed to detect driving (m/s)
     * Default: 5.0 m/s (~18 km/h)
     */
    const val DRIVING_SPEED_THRESHOLD = 5.0
    
    // ==================== STATIONARY DETECTION ====================
    
    /**
     * Maximum variance for stationary detection
     * Default: 0.15
     */
    const val STATIONARY_VARIANCE_THRESHOLD = 0.15
    
    /**
     * Maximum acceleration threshold for stationary
     * Default: 0.3
     */
    const val STATIONARY_ACCEL_THRESHOLD = 0.3
    
    /**
     * Very strict variance threshold for perfectly still phone
     * Default: 0.0015 (sensor noise level)
     */
    const val PERFECTLY_STILL_VARIANCE = 0.0015
    
    /**
     * Very strict gyroscope threshold for perfectly still phone
     * Default: 0.0015 rad/s (sensor noise level)
     */
    const val PERFECTLY_STILL_GYRO = 0.0015
    
    // ==================== PHONE POSITION DETECTION ====================
    
    /**
     * Maximum orientation variance for desk detection
     * Phone on desk should have almost zero orientation change
     * Default: 5 degrees^2
     */
    const val DESK_ORIENTATION_VARIANCE_MAX = 5.0
    
    /**
     * Minimum pitch angle for viewing (degrees)
     * Below this = likely flat on desk
     * Default: 30 degrees
     */
    const val VIEWING_ANGLE_MIN = 30.0
    
    /**
     * Maximum pitch angle for viewing (degrees)
     * Above this = likely in pocket (vertical)
     * Default: 80 degrees
     */
    const val VIEWING_ANGLE_MAX = 80.0
    
    /**
     * Minimum pitch angle for pocket detection (degrees)
     * Default: 60 degrees (mostly vertical)
     */
    const val POCKET_PITCH_MIN = 60.0
    
    /**
     * Maximum pitch angle for pocket detection (degrees)
     * Default: 100 degrees
     */
    const val POCKET_PITCH_MAX = 100.0
    
    /**
     * Maximum gyroscope for pocket detection
     * Phone in pocket has limited rotation
     * Default: 0.8 rad/s
     */
    const val POCKET_GYRO_MAX = 0.8
    
    /**
     * Minimum variance for pocket walking pattern
     * Default: 0.3 (rhythmic walking motion)
     */
    const val POCKET_VARIANCE_MIN = 0.3
    
    /**
     * Maximum orientation variance for stable pocket
     * Default: 200 degrees^2
     */
    const val POCKET_ORIENTATION_VARIANCE_MAX = 200.0
    
    /**
     * Minimum orientation variance for hand detection
     * Hand-held phone has frequent angle changes
     * Default: 150 degrees^2
     */
    const val HAND_ORIENTATION_VARIANCE_MIN = 150.0
    
    // ==================== ON_PHONE DETECTION ====================
    
    /**
     * Threshold for detecting phone is being held/used
     * Default: 0.4
     */
    const val ON_PHONE_VARIANCE_THRESHOLD = 0.4
    
    /**
     * Threshold for detecting phone rotation when in use
     * Default: 0.3 rad/s
     */
    const val ON_PHONE_GYRO_THRESHOLD = 0.3
}

