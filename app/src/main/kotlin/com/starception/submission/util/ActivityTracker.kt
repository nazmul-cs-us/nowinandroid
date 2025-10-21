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
 * ActivityTracker - Singleton to track current user activity using sensor-based detection
 * 
 * This provides a way for the ActivityDetectionService to update the current activity
 * and for UI components to observe the current activity state. It integrates with
 * our new sensor-based activity detection system.
 */
object ActivityTracker {
    private val _currentActivity = MutableStateFlow("Initializing...")
    val currentActivity: StateFlow<String> = _currentActivity.asStateFlow()
    
    private val _isBeepEnabled = MutableStateFlow(true)
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
     * Play beep sound when activity changes (only if enabled)
     */
    private fun playActivityChangeBeep() {
        // Only play beep if it's enabled
        if (!_isBeepEnabled.value) {
            Log.d("ActivityTracker", "🔇 Activity change beep muted")
            return
        }
        
        try {
            // Play a short beep tone
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            
            // Also vibrate briefly if available
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
            
            Log.d("ActivityTracker", "🔊 Activity change beep played")
        } catch (e: Exception) {
            Log.e("ActivityTracker", "Failed to play activity change beep: ${e.message}")
        }
    }
    
    /**
     * Toggle beep sound on/off
     */
    fun toggleBeepSound() {
        _isBeepEnabled.value = !_isBeepEnabled.value
        Log.d("ActivityTracker", "🔊 Beep sound ${if (_isBeepEnabled.value) "enabled" else "disabled"}")
    }
    
    /**
     * Set beep sound state
     */
    fun setBeepEnabled(enabled: Boolean) {
        _isBeepEnabled.value = enabled
        Log.d("ActivityTracker", "🔊 Beep sound ${if (enabled) "enabled" else "disabled"}")
    }
}