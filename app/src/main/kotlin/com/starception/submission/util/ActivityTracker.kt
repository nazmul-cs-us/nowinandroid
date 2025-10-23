package com.starception.submission.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.media.ToneGenerator
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.starception.submission.sensor.ActivityDetectionService

/**
 * Notification mode for activity changes
 */
enum class NotificationMode {
    SPEAKER,   // Sound + Vibrate
    VIBRATE,   // Vibrate only
    MUTE       // No notification
}

/**
 * ActivityTracker - Singleton to track current user activity using sensor-based detection
 *
 * This provides a way for the ActivityDetectionService to update the current activity
 * and for UI components to observe the current activity state. It integrates with
 * our new sensor-based activity detection system.
 * 
 * Notification Mode Persistence:
 * - Stores user's notification preference (SPEAKER/VIBRATE/MUTE)
 * - Persists across app restarts using SharedPreferences
 * - Automatically loads saved preference on initialization
 */
object ActivityTracker {
    private const val PREFS_NAME = "activity_tracker_prefs"
    private const val KEY_NOTIFICATION_MODE = "notification_mode"
    
    // Travel Dua cooldown settings
    private const val DUA_COOLDOWN_MILLIS = 5 * 60 * 1000L // 5 minutes in milliseconds
    
    private val _currentActivity = MutableStateFlow("Initializing...")
    val currentActivity: StateFlow<String> = _currentActivity.asStateFlow()

    private val _notificationMode = MutableStateFlow(NotificationMode.SPEAKER)
    val notificationMode: StateFlow<NotificationMode> = _notificationMode.asStateFlow()

    // Deprecated - kept for backwards compatibility
    @Deprecated("Use notificationMode instead", ReplaceWith("notificationMode"))
    private val _isBeepEnabled = MutableStateFlow(true)
    @Deprecated("Use notificationMode instead", ReplaceWith("notificationMode"))
    val isBeepEnabled: StateFlow<Boolean> = _isBeepEnabled.asStateFlow()
    
    private var activityDetectionService: ActivityDetectionService? = null
    private var isInitialized = false
    private var context: Context? = null
    private var toneGenerator: ToneGenerator? = null
    private var mediaPlayer: MediaPlayer? = null
    private var previousActivity: String = ""
    
    // Dua cooldown tracking
    private var lastDuaPlayTime: Long = 0L
    private var lastDrivingTime: Long = 0L
    
    /**
     * Initialize the activity tracker with sensor-based detection
     * Also loads saved notification mode preference from storage
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        
        try {
            this.context = context.applicationContext
            activityDetectionService = ActivityDetectionService(context.applicationContext)
            
            // Load saved notification mode preference
            val savedMode = loadNotificationMode(context)
            _notificationMode.value = savedMode
            _isBeepEnabled.value = (savedMode != NotificationMode.MUTE)
            Log.d("ActivityTracker", "📱 Loaded saved notification mode: $savedMode")
            
            // Initialize ToneGenerator for beep sounds
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            } catch (e: Exception) {
                Log.w("ActivityTracker", "Failed to initialize ToneGenerator: ${e.message}")
            }
            
            // Check if we have required permissions
            if (activityDetectionService?.hasRequiredPermissions() == true) {
                // Start activity detection with callback to update our state
                activityDetectionService?.startDetection(object : ActivityDetectionService.ActivityChangeCallback {
                    override fun onActivityChanged(
                        newActivity: ActivityDetectionService.ActivityType,
                        previousActivity: ActivityDetectionService.ActivityType
                    ) {
                        updateActivity(activityToString(newActivity))
                        // Play beep sound when activity changes
                        playActivityChangeBeep()
                    }
                })
                _currentActivity.value = "Detecting..."
                Log.d("ActivityTracker", "Activity detection started successfully")
            } else {
                // Provide more specific feedback about which permissions are missing
                val missingPermissions = getMissingPermissions(context)
                _currentActivity.value = "Need: $missingPermissions"
                Log.w("ActivityTracker", "Missing permissions: $missingPermissions")
            }
        } catch (e: Exception) {
            _currentActivity.value = "Detection error"
            Log.e("ActivityTracker", "Error initializing activity detection", e)
        }
        
        isInitialized = true
    }
    
    /**
     * Get a user-friendly string of missing permissions
     */
    private fun getMissingPermissions(context: Context): String {
        val missing = mutableListOf<String>()
        
        // Check location permissions
        val hasLocationFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasLocationCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (!hasLocationFine && !hasLocationCoarse) {
            missing.add("Location")
        }
        
        // Check activity recognition permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasActivityRecognition = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            if (!hasActivityRecognition) {
                missing.add("Activity")
            }
        }
        
        return if (missing.isEmpty()) "unknown" else missing.joinToString(", ")
    }
    
    /**
     * Update the current activity (called from ActivityDetectionService callback)
     * IMPROVED: Smart cooldown for travel dua to prevent replaying at traffic lights
     */
    fun updateActivity(activity: String) {
        val currentTime = System.currentTimeMillis()
        val oldActivity = previousActivity
        previousActivity = _currentActivity.value
        _currentActivity.value = activity

        // Play driving audio when transitioning to driving
        // IMPROVED LOGIC: Only play if either:
        // 1. We weren't driving recently (> 5 minutes ago)
        // 2. OR we were driving recently BUT were in a different activity for > 5 minutes
        if (activity == "Driving" && oldActivity != "Driving") {
            val timeSinceLastDua = currentTime - lastDuaPlayTime
            val timeSinceLastDriving = currentTime - lastDrivingTime
            
            // Case 1: First time driving or long time since last driving session
            if (timeSinceLastDua >= DUA_COOLDOWN_MILLIS) {
                playDrivingAudio()
                lastDuaPlayTime = currentTime
                lastDrivingTime = currentTime
                Log.d("ActivityTracker", "🚗 Driving started - playing travel dua (cooldown expired)")
            }
            // Case 2: We were driving recently, stopped briefly (e.g., traffic light), now driving again
            else if (timeSinceLastDriving < DUA_COOLDOWN_MILLIS) {
                // Within cooldown threshold - don't replay dua
                lastDrivingTime = currentTime // Update last driving time
                Log.d("ActivityTracker", "🚗 Driving resumed within ${(DUA_COOLDOWN_MILLIS - timeSinceLastDua) / 1000}s cooldown - skipping dua")
            }
            // Case 3: Long time since driving but short time since last dua (edge case)
            else {
                Log.d("ActivityTracker", "🚗 Driving started but dua cooldown active for ${(DUA_COOLDOWN_MILLIS - timeSinceLastDua) / 1000}s more")
            }
        }
        
        // Track when we're driving to detect brief stops
        if (activity == "Driving") {
            lastDrivingTime = currentTime
        }
    }
    
    /**
     * Get current activity synchronously for UI
     */
    fun getCurrentActivity(): String {
        // If we have an active service, try to get the current activity from it
        activityDetectionService?.let { service ->
            if (service.isRunning()) {
                return activityToString(service.getCurrentActivity())
            }
        }
        
        return _currentActivity.value
    }
    
    /**
     * Stop activity detection
     */
    fun stopDetection() {
        activityDetectionService?.stopDetection()
        _currentActivity.value = "Stopped"

        // Clean up ToneGenerator
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.w("ActivityTracker", "Error releasing ToneGenerator: ${e.message}")
        }

        // Clean up MediaPlayer
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w("ActivityTracker", "Error releasing MediaPlayer: ${e.message}")
        }
    }
    
    /**
     * Convert ActivityType enum to user-friendly string
     */
    private fun activityToString(activity: ActivityDetectionService.ActivityType): String {
        return when (activity) {
            ActivityDetectionService.ActivityType.STATIONARY -> "Still"
            ActivityDetectionService.ActivityType.ON_PHONE -> "On Phone"
            ActivityDetectionService.ActivityType.WALKING -> "Walking"
            ActivityDetectionService.ActivityType.RUNNING -> "Running"
            ActivityDetectionService.ActivityType.DRIVING -> "Driving"
            ActivityDetectionService.ActivityType.UNKNOWN -> "Unknown"
        }
    }
    
    /**
     * Check if activity detection is running
     */
    fun isDetectionActive(): Boolean {
        return activityDetectionService?.isRunning() ?: false
    }
    
    /**
     * Re-initialize after permissions might have been granted
     */
    fun reinitializeIfNeeded(context: Context) {
        if (!isDetectionActive() && activityDetectionService?.hasRequiredPermissions() == true) {
            Log.d("ActivityTracker", "Reinitializing after permission grant")
            isInitialized = false // Reset initialization flag
            initialize(context)
        }
    }
    
    /**
     * Check if permissions are missing
     */
    fun arePermissionsMissing(context: Context): Boolean {
        return activityDetectionService?.hasRequiredPermissions() != true
    }
    
    /**
     * Play notification when activity changes (based on selected mode)
     */
    private fun playActivityChangeBeep() {
        when (_notificationMode.value) {
            NotificationMode.MUTE -> {
                Log.d("ActivityTracker", "🔇 Activity change notification muted")
                return
            }
            NotificationMode.SPEAKER -> {
                playSound()
                playVibration()
                Log.d("ActivityTracker", "🔊 Activity change: Sound + Vibrate")
            }
            NotificationMode.VIBRATE -> {
                playVibration()
                Log.d("ActivityTracker", "📳 Activity change: Vibrate only")
            }
        }
    }

    /**
     * Play sound notification
     */
    private fun playSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        } catch (e: Exception) {
            Log.e("ActivityTracker", "Failed to play sound: ${e.message}")
        }
    }

    /**
     * Play vibration notification
     */
    private fun playVibration() {
        try {
            context?.let { ctx ->
                val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let { vib ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(100)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ActivityTracker", "Failed to vibrate: ${e.message}")
        }
    }

    /**
     * Play driving audio (travel dua) when driving starts
     */
    private fun playDrivingAudio() {
        try {
            context?.let { ctx ->
                // Release any existing MediaPlayer instance
                mediaPlayer?.release()

                // Create and play the travel dua audio
                val resId = ctx.resources.getIdentifier("travel_dua", "raw", ctx.packageName)
                if (resId != 0) {
                    mediaPlayer = MediaPlayer.create(ctx, resId)
                    mediaPlayer?.setOnCompletionListener { mp ->
                        mp.release()
                        mediaPlayer = null
                        Log.d("ActivityTracker", "🎵 Travel dua audio completed")
                    }
                    mediaPlayer?.start()
                    Log.d("ActivityTracker", "🎵 Playing travel dua audio")
                } else {
                    Log.e("ActivityTracker", "Failed to find travel_dua.wav in resources")
                }
            }
        } catch (e: Exception) {
            Log.e("ActivityTracker", "Failed to play driving audio: ${e.message}")
        }
    }
    
    /**
     * Load saved notification mode from SharedPreferences
     */
    private fun loadNotificationMode(context: Context): NotificationMode {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val modeName = prefs.getString(KEY_NOTIFICATION_MODE, NotificationMode.SPEAKER.name)
            val mode = NotificationMode.valueOf(modeName ?: NotificationMode.SPEAKER.name)
            Log.d("ActivityTracker", "📥 Loaded notification mode from storage: $mode")
            mode
        } catch (e: Exception) {
            Log.e("ActivityTracker", "❌ Failed to load notification mode, using default: ${e.message}")
            NotificationMode.SPEAKER
        }
    }
    
    /**
     * Save notification mode to SharedPreferences
     */
    private fun saveNotificationMode(context: Context, mode: NotificationMode) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_NOTIFICATION_MODE, mode.name)
                .apply()
            Log.d("ActivityTracker", "💾 Saved notification mode to storage: $mode")
        } catch (e: Exception) {
            Log.e("ActivityTracker", "❌ Failed to save notification mode: ${e.message}")
        }
    }
    
    /**
     * Cycle through notification modes: Speaker → Vibrate → Mute → Speaker
     * Now with automatic persistence!
     */
    fun cycleNotificationMode(context: Context? = null) {
        _notificationMode.value = when (_notificationMode.value) {
            NotificationMode.SPEAKER -> NotificationMode.VIBRATE
            NotificationMode.VIBRATE -> NotificationMode.MUTE
            NotificationMode.MUTE -> NotificationMode.SPEAKER
        }
        // Update deprecated field for backwards compatibility
        _isBeepEnabled.value = (_notificationMode.value != NotificationMode.MUTE)

        val modeText = when (_notificationMode.value) {
            NotificationMode.SPEAKER -> "Speaker (Sound + Vibrate)"
            NotificationMode.VIBRATE -> "Vibrate Only"
            NotificationMode.MUTE -> "Mute"
        }
        Log.d("ActivityTracker", "🔔 Notification mode changed to: $modeText")
        
        // Save to persistent storage if context provided
        context?.let { 
            saveNotificationMode(it, _notificationMode.value)
            Log.d("ActivityTracker", "💾 Preference saved and will persist across app restarts")
        }
    }

    /**
     * Set notification mode directly with automatic persistence
     */
    fun setNotificationMode(mode: NotificationMode, context: Context? = null) {
        _notificationMode.value = mode
        // Update deprecated field for backwards compatibility
        _isBeepEnabled.value = (mode != NotificationMode.MUTE)
        Log.d("ActivityTracker", "🔔 Notification mode set to: $mode")
        
        // Save to persistent storage if context provided
        context?.let { 
            saveNotificationMode(it, mode)
            Log.d("ActivityTracker", "💾 Preference saved")
        }
    }

    /**
     * Get current notification mode
     */
    fun getNotificationMode(): NotificationMode {
        return _notificationMode.value
    }

    // Deprecated methods - kept for backwards compatibility
    /**
     * Toggle beep sound on/off
     * @deprecated Use cycleNotificationMode() instead
     */
    @Deprecated("Use cycleNotificationMode() instead", ReplaceWith("cycleNotificationMode()"))
    fun toggleBeepSound() {
        cycleNotificationMode()
    }

    /**
     * Set beep sound state
     * @deprecated Use setNotificationMode() instead
     */
    @Deprecated("Use setNotificationMode() instead", ReplaceWith("setNotificationMode(if (enabled) NotificationMode.SPEAKER else NotificationMode.MUTE)"))
    fun setBeepEnabled(enabled: Boolean) {
        setNotificationMode(if (enabled) NotificationMode.SPEAKER else NotificationMode.MUTE)
    }
}