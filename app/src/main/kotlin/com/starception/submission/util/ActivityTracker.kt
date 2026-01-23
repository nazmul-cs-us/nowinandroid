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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.starception.submission.sensor.ActivityDetectionService
import com.starception.submission.config.ActivityDetectionConfig
import com.starception.submission.config.TravelDuaSettings

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

    private val _notificationMode = MutableStateFlow(NotificationMode.MUTE)  // Default to silent mode
    val notificationMode: StateFlow<NotificationMode> = _notificationMode.asStateFlow()

    // Deprecated - kept for backwards compatibility
    @Deprecated("Use notificationMode instead", ReplaceWith("notificationMode"))
    private val _isBeepEnabled = MutableStateFlow(false)  // Default false since MUTE is default
    @Deprecated("Use notificationMode instead", ReplaceWith("notificationMode"))
    val isBeepEnabled: StateFlow<Boolean> = _isBeepEnabled.asStateFlow()
    
    private var activityDetectionService: ActivityDetectionService? = null
    private var isInitialized = false
    private var context: Context? = null
    private var toneGenerator: ToneGenerator? = null
    private var mediaPlayer: MediaPlayer? = null
    private var previousActivity: String = ""

    // Handler for sensor callbacks from foreground service
    // CRITICAL: Using a Handler from a foreground service ensures sensors deliver
    // updates even when the app is in background (Android 9+ throttles background sensors)
    private var sensorHandler: android.os.Handler? = null

    // Foreground state tracking - only play sound/vibration when app is visible
    private var isAppInForeground = false
    private var lifecycleObserverRegistered = false

    // Smart Activity tile focus tracking - only play sound/vibration when tile is visible
    private var isSmartActivityTileInFocus = false

    /**
     * Set whether the Smart Activity tile is currently visible/in focus
     * When not in focus, sound/vibration notifications are suppressed
     */
    fun setSmartActivityTileInFocus(inFocus: Boolean) {
        isSmartActivityTileInFocus = inFocus
        Log.d("ActivityTracker", "📍 Smart Activity tile ${if (inFocus) "IN FOCUS" else "OUT OF FOCUS"} - notifications ${if (inFocus) "enabled" else "suppressed"}")
    }

    /**
     * Check if Smart Activity tile is currently in focus
     */
    fun isSmartActivityTileInFocus(): Boolean = isSmartActivityTileInFocus

    /**
     * Set the sensor handler from a foreground service
     * CRITICAL FOR BACKGROUND OPERATION:
     * Android 9+ throttles sensor delivery for background apps. By passing a Handler
     * from a foreground service, sensors will continue to deliver at full rate
     * even when the app is closed.
     *
     * @param handler Handler from foreground service's HandlerThread
     */
    fun setSensorHandler(handler: android.os.Handler?) {
        sensorHandler = handler
        Log.i("ActivityTracker", "🔧 Sensor handler ${if (handler != null) "SET from foreground service" else "CLEARED"}")
    }

    // Callback for activity change (used to update notification immediately)
    private var activityChangeCallback: ((String) -> Unit)? = null
    
    // Dua cooldown tracking
    private var lastDuaPlayTime: Long = 0L
    private var lastDrivingTime: Long = 0L

    // Gap tolerance tracking - continue countdown if driving resumes within gap tolerance
    private var drivingStopTime: Long = 0L  // When driving stopped
    private var accumulatedDrivingTime: Long = 0L  // Accumulated driving time in ms
    private var drivingStartTime: Long = 0L  // When current driving session started

    // Travel dua settings (loaded from SharedPreferences, can be updated from settings screen)
    private var travelDuaEnabled: Boolean = true
    private var travelDuaCooldownMillis: Long = TravelDuaSettings.DEFAULT_COOLDOWN_MINUTES * 60 * 1000L
    private var travelDuaPlaybackDelayMillis: Long = TravelDuaSettings.DEFAULT_PLAYBACK_DELAY_SECONDS * 1000L
    private var travelDuaGapToleranceMillis: Long = TravelDuaSettings.DEFAULT_GAP_TOLERANCE_MINUTES * 60 * 1000L

    // Handler for delayed dua playback
    private val duaHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingDuaRunnable: Runnable? = null
    
    /**
     * Lifecycle observer to track app foreground/background state
     * Used to suppress sound/vibration notifications when app is in background
     */
    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            isAppInForeground = true
            Log.d("ActivityTracker", "📱 App moved to FOREGROUND - sound/vibration enabled")
        }

        override fun onStop(owner: LifecycleOwner) {
            isAppInForeground = false
            Log.d("ActivityTracker", "📱 App moved to BACKGROUND - sound/vibration disabled")
        }
    }

    /**
     * Initialize the activity tracker with sensor-based detection
     * Also loads saved notification mode preference from storage
     *
     * @param context Application context
     * @param startDetectionNow If true, starts detection immediately (default: true for background operation)
     */
    fun initialize(context: Context, startDetectionNow: Boolean = true) {
        try {
            this.context = context.applicationContext

            // Only create service instance once
            if (activityDetectionService == null) {
                activityDetectionService = ActivityDetectionService(context.applicationContext)
            }

            // Register lifecycle observer to track foreground state (only once)
            if (!lifecycleObserverRegistered) {
                try {
                    ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
                    lifecycleObserverRegistered = true
                    // Assume foreground on initialization since it's called from Activity
                    isAppInForeground = true
                    Log.d("ActivityTracker", "📱 Lifecycle observer registered for foreground tracking")
                } catch (e: Exception) {
                    Log.w("ActivityTracker", "Failed to register lifecycle observer: ${e.message}")
                }
            }

            // Load saved notification mode preference
            val savedMode = loadNotificationMode(context)
            _notificationMode.value = savedMode
            _isBeepEnabled.value = (savedMode != NotificationMode.MUTE)
            Log.d("ActivityTracker", "📱 Loaded saved notification mode: $savedMode")

            // Load saved travel dua settings
            loadTravelDuaSettings(context)

            // Initialize ToneGenerator for beep sounds
            if (toneGenerator == null) {
                try {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                } catch (e: Exception) {
                    Log.w("ActivityTracker", "Failed to initialize ToneGenerator: ${e.message}")
                }
            }

            // Start detection (default: always start for continuous background operation)
            if (startDetectionNow) {
                startDetection()
            }

            isInitialized = true
        } catch (e: Exception) {
            _currentActivity.value = "Detection error"
            Log.e("ActivityTracker", "Error initializing activity tracker", e)
        }
    }

    /**
     * Start activity detection - called when user navigates to Smart Tracking tile
     */
    fun startDetection() {
        if (context == null) {
            Log.w("ActivityTracker", "Cannot start detection - not initialized")
            return
        }

        // Already running?
        if (activityDetectionService?.isRunning() == true) {
            Log.d("ActivityTracker", "📍 Detection already running")
            return
        }

        // Check if we have required permissions
        if (activityDetectionService?.hasRequiredPermissions() == true) {
            // Create callback for activity changes
            val callback = object : ActivityDetectionService.ActivityChangeCallback {
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
            }

            // Start activity detection with handler for background operation
            // If sensorHandler is set (from foreground service), sensors will work in background
            if (sensorHandler != null) {
                activityDetectionService?.startDetection(callback, sensorHandler)
                Log.i("ActivityTracker", "🚀 Activity detection STARTED with FOREGROUND SERVICE handler (background-ready)")
            } else {
                // Fallback to internal handler (may not work reliably in background)
                activityDetectionService?.startDetection(callback)
                Log.w("ActivityTracker", "⚠️ Activity detection STARTED with INTERNAL handler (may not work in background)")
            }
            _currentActivity.value = "Detecting..."
        } else {
            // Provide more specific feedback about which permissions are missing
            val missingPermissions = getMissingPermissions(context!!)
            _currentActivity.value = "Need: $missingPermissions"
            Log.w("ActivityTracker", "Missing permissions: $missingPermissions")
        }
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

        // Trigger callback if activity actually changed (for notification icon update)
        if (oldActivity != activity && activityChangeCallback != null) {
            Log.d("ActivityTracker", "🔄 Activity changed: $oldActivity → $activity - triggering notification update")
            activityChangeCallback?.invoke(activity)
        }

        // ========== DRIVING STARTED ==========
        if (activity == "Driving" && oldActivity != "Driving") {
            // Check if travel dua feature is enabled
            if (!travelDuaEnabled) {
                Log.d("ActivityTracker", "🚗 Travel dua disabled in settings")
                lastDrivingTime = currentTime
                return
            }

            val timeSinceLastDua = currentTime - lastDuaPlayTime
            val gapSinceLastDriving = currentTime - drivingStopTime

            // Check if dua cooldown has passed
            if (timeSinceLastDua < travelDuaCooldownMillis) {
                Log.d("ActivityTracker", "🚗 Dua cooldown active - ${(travelDuaCooldownMillis - timeSinceLastDua) / 1000}s remaining")
                lastDrivingTime = currentTime
                return
            }

            // Check if this is a resume within gap tolerance (e.g., after traffic light)
            if (drivingStopTime > 0 && gapSinceLastDriving < travelDuaGapToleranceMillis && accumulatedDrivingTime > 0) {
                // RESUME: Continue countdown with accumulated time
                val remainingTime = travelDuaPlaybackDelayMillis - accumulatedDrivingTime
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
                    // Set driving start time since user is now driving
                    drivingStartTime = currentTime
                }
            } else {
                // FRESH START: New driving session (gap exceeds tolerance or first time)
                val delaySeconds = travelDuaPlaybackDelayMillis / 1000
                Log.i("ActivityTracker", "🚗 New driving session started - scheduling travel dua in ${delaySeconds}s")
                if (gapSinceLastDriving >= travelDuaGapToleranceMillis && drivingStopTime > 0) {
                    val gapToleranceMinutes = travelDuaGapToleranceMillis / 60000
                    Log.d("ActivityTracker", "   Gap was ${gapSinceLastDriving / 1000}s (> ${gapToleranceMinutes}min) - resetting accumulation")
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

            val delaySeconds = travelDuaPlaybackDelayMillis / 1000
            Log.i("ActivityTracker", "🛑 Driving stopped after ${sessionDrivingTime / 1000}s")
            Log.i("ActivityTracker", "   Total accumulated: ${accumulatedDrivingTime / 1000}s / ${delaySeconds}s needed")

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
     * Set callback for activity changes (used to trigger notification updates)
     */
    fun setActivityChangeCallback(callback: ((String) -> Unit)?) {
        activityChangeCallback = callback
        Log.d("ActivityTracker", "📱 Activity change callback ${if (callback != null) "registered" else "cleared"}")
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
     *
     * @param releaseResources If true, releases audio resources (for cleanup).
     *                         If false, just pauses detection (can resume quickly)
     */
    fun stopDetection(releaseResources: Boolean = false) {
        if (activityDetectionService?.isRunning() != true) {
            Log.d("ActivityTracker", "📍 Detection not running, nothing to stop")
            return
        }

        activityDetectionService?.stopDetection()
        _currentActivity.value = "Stopped"
        Log.i("ActivityTracker", "⏹️ Activity detection STOPPED")

        // Cancel any pending dua
        cancelPendingDua()

        // Only release resources if explicitly requested (e.g., app closing)
        if (releaseResources) {
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
            Log.d("ActivityTracker", "🧹 Released audio resources")
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
     * Only plays sound/vibration when:
     * 1. App is in foreground
     * 2. Smart Activity tile is currently visible/in focus
     */
    private fun playActivityChangeBeep() {
        // Skip sound/vibration when app is in background
        if (!isAppInForeground) {
            Log.d("ActivityTracker", "🔕 Activity changed but app in BACKGROUND - suppressing sound/vibration")
            return
        }

        // Skip sound/vibration when Smart Activity tile is not in focus
        if (!isSmartActivityTileInFocus) {
            Log.d("ActivityTracker", "🔕 Activity changed but Smart Activity tile NOT IN FOCUS - suppressing sound/vibration")
            return
        }

        when (_notificationMode.value) {
            NotificationMode.MUTE -> {
                Log.d("ActivityTracker", "🔇 Activity change notification muted")
                return
            }
            NotificationMode.SPEAKER -> {
                playSound()
                playVibration()
                Log.d("ActivityTracker", "🔊 Activity change: Sound + Vibrate (app in foreground)")
            }
            NotificationMode.VIBRATE -> {
                playVibration()
                Log.d("ActivityTracker", "📳 Activity change: Vibrate only (app in foreground)")
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
        scheduleDrivingDuaWithRemainingTime(travelDuaPlaybackDelayMillis)
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
                // IMPORTANT: Reset driving start time since user is still driving
                // This prevents session time from becoming huge when they stop later
                drivingStartTime = System.currentTimeMillis()
                Log.d("ActivityTracker", "🔄 Reset driving start time for continued driving session")
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
                    val cooldownMinutes = travelDuaCooldownMillis / 60000
                    Log.d("ActivityTracker", "🎵 Playing travel dua audio (cooldown started: ${cooldownMinutes}min)")
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
     * Default is MUTE (silent mode) for new users
     */
    private fun loadNotificationMode(context: Context): NotificationMode {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val modeName = prefs.getString(KEY_NOTIFICATION_MODE, NotificationMode.MUTE.name)  // Default to silent
            val mode = NotificationMode.valueOf(modeName ?: NotificationMode.MUTE.name)
            Log.d("ActivityTracker", "📥 Loaded notification mode from storage: $mode")
            mode
        } catch (e: Exception) {
            Log.e("ActivityTracker", "❌ Failed to load notification mode, using default: ${e.message}")
            NotificationMode.MUTE  // Default to silent mode
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

    // ============= Travel Dua Settings =============

    /**
     * Load travel dua settings from SharedPreferences
     * Called during initialization to restore user preferences
     */
    private fun loadTravelDuaSettings(context: Context) {
        try {
            val prefs = context.getSharedPreferences(TravelDuaSettings.PREFS_NAME, Context.MODE_PRIVATE)
            travelDuaEnabled = prefs.getBoolean(TravelDuaSettings.KEY_ENABLED, true)
            val cooldownMinutes = prefs.getInt(TravelDuaSettings.KEY_COOLDOWN_MINUTES, TravelDuaSettings.DEFAULT_COOLDOWN_MINUTES)
            val playbackDelaySeconds = prefs.getInt(TravelDuaSettings.KEY_PLAYBACK_DELAY_SECONDS, TravelDuaSettings.DEFAULT_PLAYBACK_DELAY_SECONDS)
            val gapToleranceMinutes = prefs.getInt(TravelDuaSettings.KEY_GAP_TOLERANCE_MINUTES, TravelDuaSettings.DEFAULT_GAP_TOLERANCE_MINUTES)

            travelDuaCooldownMillis = cooldownMinutes * 60 * 1000L
            travelDuaPlaybackDelayMillis = playbackDelaySeconds * 1000L
            travelDuaGapToleranceMillis = gapToleranceMinutes * 60 * 1000L

            Log.i("ActivityTracker", "🚗 Travel dua settings loaded: enabled=$travelDuaEnabled, cooldown=${cooldownMinutes}min, delay=${playbackDelaySeconds}s, gap=${gapToleranceMinutes}min")
        } catch (e: Exception) {
            Log.e("ActivityTracker", "❌ Failed to load travel dua settings, using defaults: ${e.message}")
        }
    }

    /**
     * Update travel dua settings from the settings screen
     * Called when user changes settings in UnifiedSettingsScreen
     */
    fun updateTravelDuaSettings(settings: TravelDuaSettings) {
        travelDuaEnabled = settings.enabled
        travelDuaCooldownMillis = settings.cooldownMillis
        travelDuaPlaybackDelayMillis = settings.playbackDelayMillis
        travelDuaGapToleranceMillis = settings.gapToleranceMillis

        Log.i("ActivityTracker", "🚗 Travel dua settings updated: enabled=${settings.enabled}, cooldown=${settings.cooldownMinutes}min, delay=${settings.playbackDelaySeconds}s, gap=${settings.gapToleranceMinutes}min")

        // Reset accumulation when settings change to avoid confusion
        resetDrivingAccumulation()
        cancelPendingDua()
        Log.d("ActivityTracker", "🔄 Reset driving accumulation due to settings change")
    }

    /**
     * Check if travel dua feature is enabled
     */
    fun isTravelDuaEnabled(): Boolean = travelDuaEnabled

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