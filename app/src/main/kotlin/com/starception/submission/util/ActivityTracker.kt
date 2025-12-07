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
import com.starception.submission.config.ActivityDetectionConfig

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
    
    private val _currentActivity = MutableStateFlow("Initializing...")
    val currentActivity: StateFlow<String> = _currentActivity.asStateFlow()
    
    private val _phonePosition = MutableStateFlow("UNKNOWN")
    val phonePosition: StateFlow<String> = _phonePosition.asStateFlow()

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

    // Gap tolerance tracking - continue countdown if driving resumes within 5 minutes
    private var drivingStopTime: Long = 0L  // When driving stopped
    private var accumulatedDrivingTime: Long = 0L  // Accumulated driving time in ms
    private var drivingStartTime: Long = 0L  // When current driving session started
    private const val GAP_TOLERANCE_MILLIS = 5 * 60 * 1000L  // 5 minutes gap tolerance

    // Handler for delayed dua playback
    private val duaHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingDuaRunnable: Runnable? = null
    
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
                        // Update phone position
                        updatePhonePosition()
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
     * IMPROVED: Smart gap tolerance for travel dua
     * - If driving stops and resumes within 5 minutes, continue countdown
     * - Accumulated driving time is tracked across brief gaps
     */
    fun updateActivity(activity: String) {
        val currentTime = System.currentTimeMillis()
        val oldActivity = previousActivity
        previousActivity = _currentActivity.value
        _currentActivity.value = activity

        // ========== DRIVING STARTED ==========
        if (activity == "Driving" && oldActivity != "Driving") {
            val timeSinceLastDua = currentTime - lastDuaPlayTime
            val gapSinceLastDriving = currentTime - drivingStopTime

            // Check if dua cooldown has passed (5 minutes since last dua played)
            if (timeSinceLastDua < ActivityDetectionConfig.DUA_COOLDOWN_MILLIS) {
                Log.d("ActivityTracker", "🚗 Dua cooldown active - ${(ActivityDetectionConfig.DUA_COOLDOWN_MILLIS - timeSinceLastDua) / 1000}s remaining")
                lastDrivingTime = currentTime
                return
            }

            // Check if this is a resume within gap tolerance (e.g., after traffic light)
            if (drivingStopTime > 0 && gapSinceLastDriving < GAP_TOLERANCE_MILLIS && accumulatedDrivingTime > 0) {
                // RESUME: Continue countdown with accumulated time
                val remainingTime = ActivityDetectionConfig.DUA_PLAYBACK_DELAY_MILLIS - accumulatedDrivingTime
                if (remainingTime > 0) {
                    Log.i("ActivityTracker", "🚦 Driving resumed within ${gapSinceLastDriving / 1000}s gap - continuing countdown")
                    Log.i("ActivityTracker", "   Accumulated: ${accumulatedDrivingTime / 1000}s, Remaining: ${remainingTime / 1000}s")
                    drivingStartTime = currentTime
                    scheduleDrivingDuaWithRemainingTime(remainingTime)
                } else {
                    // Already accumulated enough time, play dua now
                    Log.i("ActivityTracker", "🎵 Accumulated ${accumulatedDrivingTime / 1000}s driving - playing travel dua now!")
                    playDrivingAudio()
                    resetDrivingAccumulation()
                }
            } else {
                // FRESH START: New driving session (gap > 5 minutes or first time)
                Log.i("ActivityTracker", "🚗 New driving session started - scheduling travel dua in ${ActivityDetectionConfig.DUA_PLAYBACK_DELAY_SECONDS}s")
                if (gapSinceLastDriving >= GAP_TOLERANCE_MILLIS && drivingStopTime > 0) {
                    Log.d("ActivityTracker", "   Gap was ${gapSinceLastDriving / 1000}s (> 5min) - resetting accumulation")
                }
                resetDrivingAccumulation()
                drivingStartTime = currentTime
                scheduleDrivingDuaWithDelay()
            }

            lastDrivingTime = currentTime
        }

        // ========== DRIVING STOPPED ==========
        if (activity != "Driving" && oldActivity == "Driving") {
            // Calculate how long we were driving in this session
            val sessionDrivingTime = currentTime - drivingStartTime
            accumulatedDrivingTime += sessionDrivingTime
            drivingStopTime = currentTime

            Log.i("ActivityTracker", "🛑 Driving stopped after ${sessionDrivingTime / 1000}s")
            Log.i("ActivityTracker", "   Total accumulated: ${accumulatedDrivingTime / 1000}s / ${ActivityDetectionConfig.DUA_PLAYBACK_DELAY_SECONDS}s needed")

            // Cancel pending dua - we'll reschedule with remaining time if driving resumes
            cancelPendingDua()
        }

        // Track driving time
        if (activity == "Driving") {
            lastDrivingTime = currentTime
        }
    }

    /**
     * Reset driving time accumulation (for fresh start)
     */
    private fun resetDrivingAccumulation() {
        accumulatedDrivingTime = 0L
        drivingStopTime = 0L
        drivingStartTime = 0L
    }
    
    /**
     * Update phone position from service (NEW - research paper method)
     */
    private fun updatePhonePosition() {
        activityDetectionService?.let { service ->
            if (service.isRunning()) {
                val position = service.getCurrentPosition()
                _phonePosition.value = position.name
            }
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
     * Get current phone position synchronously for UI (NEW)
     */
    fun getCurrentPhonePosition(): String {
        // If we have an active service, try to get the current position from it
        activityDetectionService?.let { service ->
            if (service.isRunning()) {
                return service.getCurrentPosition().name
            }
        }
        
        return _phonePosition.value
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
     * Schedule driving dua with full delay - user must stay in driving mode
     */
    private fun scheduleDrivingDuaWithDelay() {
        scheduleDrivingDuaWithRemainingTime(ActivityDetectionConfig.DUA_PLAYBACK_DELAY_MILLIS)
    }

    /**
     * Schedule driving dua with remaining time - for resuming after brief stop
     */
    private fun scheduleDrivingDuaWithRemainingTime(remainingTimeMillis: Long) {
        // Cancel any existing pending dua
        cancelPendingDua()

        val scheduledTime = System.currentTimeMillis()
        val delaySeconds = remainingTimeMillis / 1000

        // Create new runnable
        pendingDuaRunnable = Runnable {
            val actualDelay = (System.currentTimeMillis() - scheduledTime) / 1000
            // Verify user is still driving before playing
            if (_currentActivity.value == "Driving") {
                val totalDrivingTime = (accumulatedDrivingTime + (System.currentTimeMillis() - drivingStartTime)) / 1000
                Log.i("ActivityTracker", "✅ Total driving time: ${totalDrivingTime}s - playing travel dua now!")
                playDrivingAudio()
                resetDrivingAccumulation()  // Reset after playing
            } else {
                Log.w("ActivityTracker", "⏭️ Skipping travel dua - user stopped driving after ${actualDelay}s (current: ${_currentActivity.value})")
            }
            pendingDuaRunnable = null
        }

        // Schedule for remaining time
        duaHandler.postDelayed(pendingDuaRunnable!!, remainingTimeMillis)
        Log.i("ActivityTracker", "⏰ Travel dua scheduled - will play in ${delaySeconds}s if user stays driving")
    }
    
    /**
     * Cancel pending dua playback
     */
    private fun cancelPendingDua() {
        pendingDuaRunnable?.let {
            duaHandler.removeCallbacks(it)
            pendingDuaRunnable = null
            Log.d("ActivityTracker", "❌ Cancelled pending travel dua - user stopped driving")
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

                    // Set cooldown timestamp ONLY when dua actually plays
                    // This ensures if user stops driving before dua plays, they can retry
                    lastDuaPlayTime = System.currentTimeMillis()
                    Log.d("ActivityTracker", "🎵 Playing travel dua audio (cooldown started: ${ActivityDetectionConfig.DUA_COOLDOWN_MINUTES}min)")
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