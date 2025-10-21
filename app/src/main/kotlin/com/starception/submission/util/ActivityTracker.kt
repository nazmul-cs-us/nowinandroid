package com.starception.submission.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.media.ToneGenerator
import android.media.AudioManager
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
 */
object ActivityTracker {
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
    
    /**
     * Initialize the activity tracker with sensor-based detection
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        
        try {
            this.context = context.applicationContext
            activityDetectionService = ActivityDetectionService(context.applicationContext)
            
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
     */
    fun updateActivity(activity: String) {
        _currentActivity.value = activity
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
     * Cycle through notification modes: Speaker → Vibrate → Mute → Speaker
     */
    fun cycleNotificationMode() {
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
        Log.d("ActivityTracker", "🔔 Notification mode: $modeText")
    }

    /**
     * Set notification mode directly
     */
    fun setNotificationMode(mode: NotificationMode) {
        _notificationMode.value = mode
        // Update deprecated field for backwards compatibility
        _isBeepEnabled.value = (mode != NotificationMode.MUTE)
        Log.d("ActivityTracker", "🔔 Notification mode set to: $mode")
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