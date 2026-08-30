package com.starception.submission.util

import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.media.ToneGenerator
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.speech.tts.UtteranceProgressListener
import com.starception.submission.download.AssetRepository
import com.starception.submission.download.AudioDownloadHelper
import com.starception.submission.sensor.ActivityDetectionService
import com.starception.submission.config.ActivityDetectionConfig
import com.starception.submission.config.TravelDuaSettings
import com.starception.submission.feature.course.CourseProgressTracker
import com.starception.submission.core.hadithdatabase.HadithDatabase
import com.starception.submission.core.hadithdatabase.HadithRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.starception.submission.core.translation.TranslationService
import com.starception.submission.voice.VoiceCompletionManager
import com.starception.submission.voice.WhisperVoiceService
import com.starception.submission.voice.SherpaOnnxTtsService
import com.starception.submission.voice.SherpaOnnxKwsService
import com.starception.submission.voice.SherpaOnnxTtsEntryPoint
import com.starception.submission.voice.EnglishTtsTextNormalizer
import com.starception.submission.feature.course.QuranListeningProgress
import com.starception.submission.feature.quran.QuranPlaybackService
import com.starception.submission.feature.quran.QuranData
import com.starception.submission.prayer.util.FileLogger
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.starception.submission.services.DrivingAudioService
import com.google.android.gms.location.ActivityTransition
import java.util.Locale

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

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AssetRepoEntryPoint {
        fun assetRepository(): AssetRepository
        fun audioDownloadHelper(): AudioDownloadHelper
    }

    private fun getAssetRepository(ctx: Context): AssetRepository =
        EntryPointAccessors.fromApplication(ctx.applicationContext, AssetRepoEntryPoint::class.java)
            .assetRepository()

    private fun getAudioDownloadHelper(ctx: Context): AudioDownloadHelper =
        EntryPointAccessors.fromApplication(ctx.applicationContext, AssetRepoEntryPoint::class.java)
            .audioDownloadHelper()

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
    private var hadithMediaPlayer: MediaPlayer? = null  // Separate player for hadith audio

    // TextToSpeech for hadith playback after travel dua
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    // Prayer detection announcement state
    private var wasPrayerConfirmed = false
    private var lastPrayerAnnouncementTime = 0L
    private const val PRAYER_ANNOUNCEMENT_COOLDOWN_MS = 60_000L

    // Voice completion for hands-free lesson completion
    private var whisperVoiceService: WhisperVoiceService? = null
    private var sherpaOnnxTtsService: SherpaOnnxTtsService? = null
    private var sherpaOnnxKwsService: SherpaOnnxKwsService? = null  // Fast keyword spotting (~100ms vs 26s Whisper)
    private var voiceCompletionManager: VoiceCompletionManager? = null
    private var currentHadithNumber: Int = 0  // Track current hadith for TTS completion

    // Quran Listening Course - Service binding
    private var quranService: QuranPlaybackService? = null
    private var isQuranServiceBound = false
    private var quranServiceConnection: ServiceConnection? = null

    // Driving Audio Service - for travel dua, hadith, TTS with notification controls
    private var drivingAudioService: DrivingAudioService? = null
    private var isDrivingServiceBound = false
    private var drivingServiceConnection: ServiceConnection? = null

    // Coroutine scope for async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Handler for sensor callbacks from foreground service
    // CRITICAL: Using a Handler from a foreground service ensures sensors deliver
    // updates even when the app is in background (Android 9+ throttles background sensors)
    private var sensorHandler: android.os.Handler? = null

    // Foreground state tracking - only play sound/vibration when app is visible
    private var isAppInForeground = false
    private var lifecycleObserverRegistered = false

    // Smart Activity tile focus tracking - only play sound/vibration when tile is visible
    private var isSmartActivityTileInFocus = false

    // Manual trigger mode - bypasses driving check for voice completion
    private var isManualTriggerMode = false

    /**
     * Get the shared Sherpa-ONNX TTS service from Hilt singleton.
     * This ensures we use the same TTS instance as VoiceCompletionManager
     * and respects user-selected voice settings.
     */
    private fun getSharedTtsService(ctx: Context): SherpaOnnxTtsService {
        if (sherpaOnnxTtsService == null) {
            val entryPoint = EntryPointAccessors.fromApplication(
                ctx.applicationContext,
                SherpaOnnxTtsEntryPoint::class.java
            )
            sherpaOnnxTtsService = entryPoint.sherpaOnnxTtsService()
            Log.i("ActivityTracker", "🔊 Using shared Hilt TTS service (same as VoiceCompletionManager)")
        }
        return sherpaOnnxTtsService!!
    }

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
        val shouldMoveRunningDetector = handler != null &&
            sensorHandler !== handler &&
            activityDetectionService?.isRunning == true

        sensorHandler = handler
        Log.i("ActivityTracker", "🔧 Sensor handler ${if (handler != null) "SET from foreground service" else "CLEARED"}")

        // The app process can initialize ActivityTracker before the foreground prayer service.
        // In that case detection is already registered on its fallback internal thread. Restart
        // once so the listeners are registered on the foreground-service handler and continue
        // receiving sensor events while the app is backgrounded or the screen is off.
        if (shouldMoveRunningDetector) {
            Log.i("ActivityTracker", "🔄 Moving active sensor detection to foreground-service handler")
            activityDetectionService?.stopDetection()
            startDetection()
        }
    }

    // Callback for activity change (used to update notification immediately)
    private var activityChangeCallback: ((String) -> Unit)? = null
    
    // Dua cooldown tracking - MUST be persisted to survive app restarts
    private var lastDuaPlayTime: Long = 0L
    private var lastDrivingTime: Long = 0L
    private const val KEY_LAST_DUA_PLAY_TIME = "last_dua_play_time"
    private var googleDrivingConfirmed = false

    // Gap tolerance tracking - continue countdown if driving resumes within gap tolerance
    private var drivingStopTime: Long = 0L  // When driving stopped
    private var accumulatedDrivingTime: Long = 0L  // Accumulated driving time in ms
    private var drivingStartTime: Long = 0L  // When current driving session started
    private var duaPlayedForCurrentSession: Boolean = false  // Whether dua already played for current accumulation

    // Travel dua settings (loaded from SharedPreferences, can be updated from settings screen)
    private var travelDuaEnabled: Boolean = true
    private var travelDuaCooldownMillis: Long = TravelDuaSettings.DEFAULT_COOLDOWN_MINUTES * 60 * 1000L
    private var travelDuaPlaybackDelayMillis: Long = TravelDuaSettings.DEFAULT_PLAYBACK_DELAY_SECONDS * 1000L
    private var travelDuaGapToleranceMillis: Long = TravelDuaSettings.DEFAULT_GAP_TOLERANCE_MINUTES * 60 * 1000L

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
                activityDetectionService = ActivityDetectionService(context.applicationContext).also { service ->
                    // Google Activity Recognition cannot report Salah as an activity. Prayer is
                    // inferred by our on-device posture model, so its sensor pipeline must be
                    // explicitly enabled when the unified activity tracker is created.
                    service.setSalahDetectionEnabled(true)
                }
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
                    updateActivityFromSensor(activityToString(newActivity))
                    // Update phone position
                    updatePhonePosition()
                    // Play beep sound when activity changes
                    playActivityChangeBeep()
                }
            }

            // Set up salah posture callback for real-time posture updates
            activityDetectionService?.setSalahPostureCallback(
                object : ActivityDetectionService.SalahPostureCallback {
                    override fun onPostureChanged(
                        posture: com.starception.submission.ml.SalahPosture,
                        confidence: Float,
                        prayerState: com.starception.submission.ml.SalahSequenceValidator.PrayerState,
                        rakahCount: Int
                    ) {
                        // Update activity display with current posture and rak'ah count
                        val confirmed = prayerState == com.starception.submission.ml.SalahSequenceValidator.PrayerState.CONFIRMED
                        if (confirmed) {
                            val rakahText = if (rakahCount > 0) " - Rak'ah $rakahCount" else ""
                            _currentActivity.value = "Praying (${posture.displayName})$rakahText"
                            if (!wasPrayerConfirmed) {
                                announcePrayerDetected()
                            }
                        }
                        wasPrayerConfirmed = confirmed
                    }
                }
            )

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
        updateActivityFromSensor(activity)
    }

    /** Apply an activity update delivered by Google Play services. */
    fun updateActivityFromTransition(activity: String, inVehicleTransitionType: Int) {
        when (inVehicleTransitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                googleDrivingConfirmed = true
                persistGoogleDrivingConfirmation(true)
                Log.i("ActivityTracker", "🚗 Google IN_VEHICLE ENTER confirmed")
            }
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                googleDrivingConfirmed = false
                persistGoogleDrivingConfirmation(false)
                Log.i("ActivityTracker", "🛑 Google IN_VEHICLE EXIT confirmed")
            }
        }

        val normalizedActivity = normalizeActivityName(activity)
        if (googleDrivingConfirmed && normalizedActivity != "Driving") {
            Log.d(
                "ActivityTracker",
                "Ignoring non-vehicle transition '$normalizedActivity' while Google driving is confirmed",
            )
            return
        }
        updateActivityInternal(normalizedActivity)
    }

    private fun updateActivityFromSensor(activity: String) {
        val normalizedActivity = normalizeActivityName(activity)
        if (googleDrivingConfirmed && normalizedActivity != "Driving") {
            val definitelyNotDriving = normalizedActivity in setOf(
                "Still",
                "Walking",
                "Running",
                "Cycling",
                "On Foot",
            )
            if (definitelyNotDriving) {
                Log.w(
                    "ActivityTracker",
                    "Sensor '$normalizedActivity' vetoed Google IN_VEHICLE confirmation",
                )
                googleDrivingConfirmed = false
                persistGoogleDrivingConfirmation(false)
                updateActivityInternal(normalizedActivity)
                return
            }
            Log.d(
                "ActivityTracker",
                "Ignoring sensor activity '$normalizedActivity' while Google driving is confirmed",
            )
            return
        }
        updateActivityInternal(normalizedActivity)
    }

    private fun updateActivityInternal(activity: String) {
        val currentTime = System.currentTimeMillis()
        // Sensor detection uses title case ("Driving") while Google Play Services
        // reports upper case ("DRIVING"). Keep one canonical value so background
        // transitions follow the same Travel Dua path as in-app sensor updates.
        val normalizedActivity = normalizeActivityName(activity)
        val oldActivity = normalizeActivityName(_currentActivity.value)
        _currentActivity.value = normalizedActivity
        persistDrivingState(normalizedActivity == "Driving")

        // Trigger callback if activity actually changed (for notification icon update)
        if (oldActivity != normalizedActivity && activityChangeCallback != null) {
            Log.d("ActivityTracker", "🔄 Activity changed: $oldActivity → $normalizedActivity - triggering notification update")
            activityChangeCallback?.invoke(normalizedActivity)
        }

        // ========== DRIVING STARTED ==========
        if (normalizedActivity == "Driving" && oldActivity != "Driving") {
            // DrivingAudioService owns the authoritative cooldown timestamp and writes
            // it only after playback starts. Refresh it before every eligibility check.
            refreshLastDuaPlayTime()
            refreshTripState()
            drivingStartTime = currentTime

            // Check if travel dua feature is enabled
            if (!travelDuaEnabled) {
                Log.d("ActivityTracker", "🚗 Travel dua disabled in settings")
                lastDrivingTime = currentTime
                return
            }

            val timeSinceLastDua = currentTime - lastDuaPlayTime
            val gapSinceLastDriving = currentTime - drivingStopTime
            val cooldownMinutes = travelDuaCooldownMillis / 60000
            val resumedWithinTripGap = TravelDuaPolicy.isWithinTripGap(
                drivingStopTimeMillis = drivingStopTime,
                nowMillis = currentTime,
                gapToleranceMillis = travelDuaGapToleranceMillis,
            )

            // Enhanced diagnostic logging for cooldown debugging
            Log.i("ActivityTracker", "🚗 ========== DRIVING DETECTED ==========")
            Log.i("ActivityTracker", "🚗 Transition: $oldActivity → $normalizedActivity")
            Log.i("ActivityTracker", "🚗 Last dua played: ${if (lastDuaPlayTime == 0L) "NEVER" else "${timeSinceLastDua / 1000}s ago"}")
            Log.i("ActivityTracker", "🚗 Cooldown setting: ${cooldownMinutes}min (${travelDuaCooldownMillis}ms)")
            Log.i("ActivityTracker", "🚗 Cooldown status: ${if (timeSinceLastDua < travelDuaCooldownMillis) "ACTIVE ⏳" else "EXPIRED ✅"}")
            FileLogger.i(
                "ActivityTracker",
                "Driving entered: previous=$oldActivity, gapMs=${gapSinceLastDriving.takeIf { drivingStopTime > 0L } ?: "none"}, " +
                    "sameTrip=$resumedWithinTripGap, duaPlayedThisTrip=$duaPlayedForCurrentSession",
            )

            // The configurable gap is the journey boundary. A traffic stop shorter than
            // this remains the same trip, regardless of whether the playback cooldown has
            // expired since the dua originally played.
            if (resumedWithinTripGap && duaPlayedForCurrentSession) {
                FileLogger.i(
                    "ActivityTracker",
                    "Travel Dua suppressed: resumed same trip after ${gapSinceLastDriving / 1000}s",
                )
                lastDrivingTime = currentTime
                return
            }

            if (drivingStopTime > 0L && !resumedWithinTripGap) {
                FileLogger.i(
                    "ActivityTracker",
                    "Previous trip ended after ${gapSinceLastDriving / 1000}s gap; clearing trip playback state",
                )
                resetDrivingAccumulation(clearPlayedState = true)
                drivingStartTime = currentTime
            }

            // Check if dua cooldown has passed
            if (timeSinceLastDua < travelDuaCooldownMillis) {
                val remainingSeconds = (travelDuaCooldownMillis - timeSinceLastDua) / 1000
                Log.w("ActivityTracker", "🚗 ⏳ COOLDOWN BLOCKING - ${remainingSeconds}s remaining (${remainingSeconds / 60}min)")
                Log.w("ActivityTracker", "🚗 ⏳ Dua will NOT play until cooldown expires")
                lastDrivingTime = currentTime
                return
            }
            Log.i("ActivityTracker", "🚗 ✅ Cooldown check PASSED - proceeding to schedule dua")

            // Check if this is a resume within gap tolerance (e.g., after traffic light)
            if (resumedWithinTripGap) {

                // If dua already played for this trip/session, never replay within gap tolerance.
                if (duaPlayedForCurrentSession) {
                    Log.i(
                        "ActivityTracker",
                        "🚦 Driving resumed within ${gapSinceLastDriving / 1000}s gap - dua already played for this session, skipping replay"
                    )
                } else if (accumulatedDrivingTime > 0) {
                    // RESUME: Continue countdown with accumulated time
                    val remainingTime = travelDuaPlaybackDelayMillis - accumulatedDrivingTime
                    if (remainingTime > 0) {
                        Log.i("ActivityTracker", "🚦 Driving resumed within ${gapSinceLastDriving / 1000}s gap - continuing countdown")
                        Log.i("ActivityTracker", "   Accumulated: ${accumulatedDrivingTime / 1000}s, Remaining: ${remainingTime / 1000}s")
                        scheduleDrivingDuaWithRemainingTime(remainingTime)
                    } else {
                        // Already accumulated enough time, play dua now
                        Log.i("ActivityTracker", "🎵 Accumulated ${accumulatedDrivingTime / 1000}s driving - scheduling immediate wake-up")
                        scheduleDrivingDuaWithRemainingTime(0L)
                        resetDrivingAccumulation(clearPlayedState = false)
                        // Set driving start time since user is now driving
                        drivingStartTime = currentTime
                    }
                } else {
                    // No prior accumulation in this resumed window; start a normal delay.
                    val delaySeconds = travelDuaPlaybackDelayMillis / 1000
                    Log.i("ActivityTracker", "🚦 Driving resumed within gap - scheduling travel dua in ${delaySeconds}s")
                    scheduleDrivingDuaWithDelay()
                }
            } else {
                // FRESH START: New driving session (first time or gap exceeds tolerance)
                val delaySeconds = travelDuaPlaybackDelayMillis / 1000
                Log.i("ActivityTracker", "🚗 New driving session started - scheduling travel dua in ${delaySeconds}s")
                if (gapSinceLastDriving >= travelDuaGapToleranceMillis && drivingStopTime > 0) {
                    val gapToleranceMinutes = travelDuaGapToleranceMillis / 60000
                    Log.d("ActivityTracker", "   Gap was ${gapSinceLastDriving / 1000}s (> ${gapToleranceMinutes}min) - resetting accumulation")
                }
                resetDrivingAccumulation(clearPlayedState = true)
                drivingStartTime = currentTime
                scheduleDrivingDuaWithDelay()
            }

            lastDrivingTime = currentTime
        }

        // ========== DRIVING STOPPED ==========
        if (normalizedActivity != "Driving" && oldActivity == "Driving") {
            refreshTripState()
            // Calculate how long we were driving in this session
            val sessionDrivingTime = (currentTime - drivingStartTime).coerceAtLeast(0L)
            accumulatedDrivingTime += sessionDrivingTime
            drivingStopTime = currentTime
            persistTripStopTime(currentTime)

            val delaySeconds = travelDuaPlaybackDelayMillis / 1000
            Log.i("ActivityTracker", "🛑 Driving stopped after ${sessionDrivingTime / 1000}s")
            Log.i("ActivityTracker", "   Total accumulated: ${accumulatedDrivingTime / 1000}s / ${delaySeconds}s needed")
            FileLogger.i(
                "ActivityTracker",
                "Driving stopped: sessionMs=$sessionDrivingTime, accumulatedMs=$accumulatedDrivingTime, " +
                    "duaPlayedThisTrip=$duaPlayedForCurrentSession",
            )

            // Cancel pending dua - we'll reschedule with remaining time if driving resumes
            cancelPendingDua()
        }

        // Track driving time
        if (normalizedActivity == "Driving") {
            lastDrivingTime = currentTime
        }
    }

    private fun normalizeActivityName(activity: String): String = when {
        activity.equals("driving", ignoreCase = true) -> "Driving"
        activity.equals("still", ignoreCase = true) -> "Still"
        activity.equals("walking", ignoreCase = true) -> "Walking"
        activity.equals("running", ignoreCase = true) -> "Running"
        activity.equals("cycling", ignoreCase = true) -> "Cycling"
        activity.equals("on_foot", ignoreCase = true) -> "On Foot"
        activity.equals("tilting", ignoreCase = true) -> "Tilting"
        activity.equals("unknown", ignoreCase = true) -> "Unknown"
        activity.equals("other", ignoreCase = true) -> "Other"
        else -> activity
    }

    /**
     * Reset driving time accumulation (for fresh start)
     */
    private fun resetDrivingAccumulation(clearPlayedState: Boolean = true) {
        accumulatedDrivingTime = 0L
        drivingStopTime = 0L
        drivingStartTime = 0L
        if (clearPlayedState) {
            duaPlayedForCurrentSession = false
        }
        context?.getSharedPreferences(TravelDuaSettings.PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(TravelDuaSettings.KEY_DRIVING_STOP_TIME)
            ?.apply {
                if (clearPlayedState) {
                    putBoolean(TravelDuaSettings.KEY_DUA_PLAYED_FOR_CURRENT_TRIP, false)
                }
            }
            ?.apply()
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
    fun stopDetection(
        releaseResources: Boolean = false,
        preserveDrivingSession: Boolean = false,
    ) {
        if (!preserveDrivingSession) {
            googleDrivingConfirmed = false
            persistGoogleDrivingConfirmation(false)
            persistDrivingState(false)
            cancelPendingDua()
        } else {
            Log.i("ActivityTracker", "Preserving active driving session while detector restarts")
        }

        if (activityDetectionService?.isRunning() != true) {
            Log.d("ActivityTracker", "📍 Detection not running, nothing to stop")
            return
        }

        activityDetectionService?.stopDetection()
        _currentActivity.value = "Stopped"
        Log.i("ActivityTracker", "⏹️ Activity detection STOPPED")

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

            // Clean up hadith MediaPlayer
            try {
                hadithMediaPlayer?.release()
                hadithMediaPlayer = null
            } catch (e: Exception) {
                Log.w("ActivityTracker", "Error releasing hadith MediaPlayer: ${e.message}")
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
            ActivityDetectionService.ActivityType.PRAYING -> {
                // Include posture details and rak'ah count if available
                val posture = activityDetectionService?.currentSalahPosture
                val rakahCount = activityDetectionService?.currentRakahCount ?: 0
                if (posture != null) {
                    val rakahText = if (rakahCount > 0) " - Rak'ah $rakahCount" else ""
                    "Praying (${posture.displayName})$rakahText"
                } else {
                    "Praying"
                }
            }
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
     * Announce via TTS when prayer is first confirmed by the sequence validator.
     *
     * Unlike activity-change beeps, this is NOT suppressed in the background or when
     * the Smart Activity tile is out of focus — the phone is in the user's pocket
     * while praying, so background is exactly when this announcement matters.
     * A cooldown guards against repeat announcements if the validator oscillates
     * in and out of CONFIRMED during the same prayer.
     */
    private fun announcePrayerDetected() {
        val ctx = context ?: return
        val now = System.currentTimeMillis()
        if (now - lastPrayerAnnouncementTime < PRAYER_ANNOUNCEMENT_COOLDOWN_MS) {
            Log.d("ActivityTracker", "🕌 Prayer detected again within cooldown - skipping announcement")
            return
        }
        lastPrayerAnnouncementTime = now

        when (_notificationMode.value) {
            NotificationMode.MUTE -> {
                Log.d("ActivityTracker", "🕌 Prayer detected - announcement muted")
            }
            NotificationMode.VIBRATE -> {
                Log.d("ActivityTracker", "🕌 Prayer detected - vibrate only")
                playVibration()
            }
            NotificationMode.SPEAKER -> {
                Log.i("ActivityTracker", "🕌 Prayer detected - announcing via TTS")
                speakPrayerAnnouncement(ctx)
            }
        }
    }

    private fun speakPrayerAnnouncement(ctx: Context) {
        val text = "Praying"
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(ctx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale.US
                    textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prayer_detected")
                } else {
                    Log.e("ActivityTracker", "🕌 TTS init failed for prayer announcement")
                }
            }
        } else {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prayer_detected")
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
        val ctx = context ?: run {
            Log.w("ActivityTracker", "Cannot schedule Travel Dua alarm: context is unavailable")
            return
        }

        cancelPendingDua()

        val safeDelay = remainingTimeMillis.coerceAtLeast(0L)
        val triggerAtElapsed = SystemClock.elapsedRealtime() + safeDelay
        val sessionToken = System.currentTimeMillis()
        val pendingIntent = TravelDuaAlarmReceiver.pendingIntent(ctx, sessionToken)
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val alarmStatePersisted = ctx.getSharedPreferences(
            TravelDuaSettings.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
            .edit()
            .putBoolean(TravelDuaSettings.KEY_IS_DRIVING, true)
            .putLong(TravelDuaSettings.KEY_PENDING_ALARM_TOKEN, sessionToken)
            .putLong(TravelDuaSettings.KEY_PENDING_ALARM_TRIGGER_ELAPSED, triggerAtElapsed)
            .commit()
        if (!alarmStatePersisted) {
            Log.e("ActivityTracker", "Failed to persist Travel Dua alarm state")
            return
        }

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsed,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsed,
                    pendingIntent,
                )
                Log.w("ActivityTracker", "Exact alarms unavailable; using an inexact wake-up alarm")
            }
            Log.i(
                "ActivityTracker",
                "⏰ Travel Dua wake-up alarm scheduled in ${safeDelay / 1000}s " +
                    "(token=$sessionToken)",
            )
            FileLogger.i(
                "ActivityTracker",
                "Travel Dua alarm scheduled: delayMs=$safeDelay, token=$sessionToken, " +
                    "duaPlayedThisTrip=$duaPlayedForCurrentSession",
            )
        } catch (e: Exception) {
            Log.e("ActivityTracker", "Failed to schedule Travel Dua wake-up alarm", e)
            FileLogger.e("ActivityTracker", "Failed to schedule Travel Dua wake-up alarm", e)
            clearPendingAlarmState(ctx)
        }
    }
    
    /**
     * Cancel pending dua playback
     */
    private fun cancelPendingDua() {
        context?.let { ctx ->
            try {
                val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(TravelDuaAlarmReceiver.pendingIntent(ctx, 0L))
                clearPendingAlarmState(ctx)
                Log.d("ActivityTracker", "❌ Cancelled pending Travel Dua wake-up alarm")
            } catch (e: Exception) {
                Log.w("ActivityTracker", "Unable to cancel pending Travel Dua alarm", e)
            }
        }
    }

    private fun clearPendingAlarmState(ctx: Context) {
        ctx.getSharedPreferences(TravelDuaSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(TravelDuaSettings.KEY_PENDING_ALARM_TOKEN)
            .remove(TravelDuaSettings.KEY_PENDING_ALARM_TRIGGER_ELAPSED)
            .apply()
    }

    private fun persistDrivingState(isDriving: Boolean) {
        context?.getSharedPreferences(TravelDuaSettings.PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(TravelDuaSettings.KEY_IS_DRIVING, isDriving)
            ?.apply()
    }

    private fun persistGoogleDrivingConfirmation(isConfirmed: Boolean) {
        context?.getSharedPreferences(TravelDuaSettings.PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(TravelDuaSettings.KEY_GOOGLE_DRIVING_CONFIRMED, isConfirmed)
            ?.apply {
                if (isConfirmed) {
                    putLong(
                        TravelDuaSettings.KEY_GOOGLE_DRIVING_CONFIRMED_ELAPSED,
                        SystemClock.elapsedRealtime(),
                    )
                } else {
                    remove(TravelDuaSettings.KEY_GOOGLE_DRIVING_CONFIRMED_ELAPSED)
                }
            }
            ?.apply()
    }

    private fun refreshLastDuaPlayTime() {
        context?.let { ctx ->
            lastDuaPlayTime = ctx.getSharedPreferences(
                TravelDuaSettings.PREFS_NAME,
                Context.MODE_PRIVATE,
            ).getLong(KEY_LAST_DUA_PLAY_TIME, lastDuaPlayTime)
        }
    }

    private fun refreshTripState() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences(
                TravelDuaSettings.PREFS_NAME,
                Context.MODE_PRIVATE,
            )
            duaPlayedForCurrentSession = prefs.getBoolean(
                TravelDuaSettings.KEY_DUA_PLAYED_FOR_CURRENT_TRIP,
                duaPlayedForCurrentSession,
            )
            drivingStopTime = prefs.getLong(
                TravelDuaSettings.KEY_DRIVING_STOP_TIME,
                drivingStopTime,
            )
        }
    }

    private fun persistTripStopTime(stopTimeMillis: Long) {
        context?.getSharedPreferences(TravelDuaSettings.PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putLong(TravelDuaSettings.KEY_DRIVING_STOP_TIME, stopTimeMillis)
            ?.apply()
    }

    /**
     * Get the next hadith information for playback.
     * Returns Triple(hadithNumber, hadithText, useBengaliAudio) or null if not enrolled.
     */
    private suspend fun getNextHadithInfo(ctx: Context): Triple<Int, String?, Boolean>? {
        try {
            val prefs = ctx.getSharedPreferences("course_progress", Context.MODE_PRIVATE)
            val enrolledCourses = prefs.getStringSet("enrolled_courses", emptySet()) ?: emptySet()

            if ("daily_bukhari" !in enrolledCourses) {
                Log.d("ActivityTracker", "📚 User not enrolled in Daily Hadith course")
                return null
            }

            if (!prefs.getBoolean("play_daily_bukhari_after_travel_dua", true)) {
                Log.d("ActivityTracker", "📚 Daily Hadith travel playback opted out")
                return null
            }

            // Find next uncompleted hadith
            val completedLessons = CourseProgressTracker.getCompletedLessons(ctx, "daily_bukhari")
            var nextHadithNumber = 1
            for (i in 1..365) {
                if ("hadith_$i" !in completedLessons) {
                    nextHadithNumber = i
                    break
                }
            }

            if (!HadithDatabase.isDatabaseAvailable(ctx, "sahih_bukhari.db")) {
                ctx.getSharedPreferences("content_prompt_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("missing_bukhari_prompt", true)
                    .apply()
                Log.w("ActivityTracker", "📚 Daily Bukhari database missing - prompting user to download assets")
                return null
            }

            // Check user's selected language for audio vs TTS
            val translationService = TranslationService.getInstance(ctx)
            val selectedLang = translationService.getSelectedLanguage()
            val useBengaliAudio = selectedLang == "bn"

            // Always carry the exact English entry. Bengali normally uses its matching
            // recording, but the driving service needs this text immediately when that
            // individual file is absent or cannot be played.
            val localTranslationRepository =
                com.starception.submission.core.hadithdatabase.BukhariLocalTranslationRepository
                    .getInstance(ctx)
            localTranslationRepository.loadTranslations()
            val hadithText = localTranslationRepository.getEnglishText(nextHadithNumber)
                ?: HadithRepository.getInstance(ctx)
                    .getHadith("sahih_bukhari.db", nextHadithNumber)
                    ?.let { it.textPlain ?: it.elaboration }

            Log.i("ActivityTracker", "📚 Next hadith: #$nextHadithNumber, useBengaliAudio=$useBengaliAudio")
            return Triple(nextHadithNumber, hadithText, useBengaliAudio)

        } catch (e: Exception) {
            Log.e("ActivityTracker", "Error getting hadith info: ${e.message}")
            return null
        }
    }

    /**
     * Play the next uncompleted daily hadith if user is enrolled in the Daily Hadith course
     */
    private fun playDailyHadithIfEnrolled(ctx: Context) {
        try {
            // Check if user is enrolled in Daily Hadith course
            val prefs = ctx.getSharedPreferences("course_progress", Context.MODE_PRIVATE)
            val enrolledCourses = prefs.getStringSet("enrolled_courses", emptySet()) ?: emptySet()

            if ("daily_bukhari" !in enrolledCourses) {
                Log.d("ActivityTracker", "📚 User not enrolled in Daily Hadith course - skipping hadith playback")
                // If not enrolled in hadith but enrolled in Quran listening, play Quran directly
                if (CourseProgressTracker.isEnrolledInQuranListening(ctx)) {
                    Log.i("ActivityTracker", "📚➡️🕌 No hadith enrollment - going directly to Quran listening")
                    playQuranListeningIfEnrolled(ctx)
                }
                return
            }

            Log.i("ActivityTracker", "📚 ========== DAILY HADITH PLAYBACK ==========")
            Log.i("ActivityTracker", "📚 User enrolled in Daily Hadith course")

            // Get completed lessons to find the next uncompleted hadith
            val completedLessons = CourseProgressTracker.getCompletedLessons(ctx, "daily_bukhari")

            // Find the next uncompleted hadith (1-365)
            var nextHadithNumber = 1
            for (i in 1..365) {
                if ("hadith_$i" !in completedLessons) {
                    nextHadithNumber = i
                    break
                }
            }

            Log.i("ActivityTracker", "📚 Next hadith to learn: #$nextHadithNumber (${completedLessons.size} completed)")

            // Fetch and play the hadith
            scope.launch {
                try {
                    val hadithRepository = HadithRepository.getInstance(ctx)
                    val hadith = hadithRepository.getHadith("sahih_bukhari.db", nextHadithNumber)

                    if (hadith != null) {
                        val textToSpeak = hadith.textPlain ?: hadith.elaboration ?: "Hadith number $nextHadithNumber"
                        Log.i("ActivityTracker", "📚 Playing hadith #$nextHadithNumber: ${textToSpeak.take(100)}...")

                        speakHadith(ctx, nextHadithNumber, textToSpeak)
                    } else {
                        Log.w("ActivityTracker", "📚 Could not fetch hadith #$nextHadithNumber")
                    }
                } catch (e: Exception) {
                    Log.e("ActivityTracker", "📚 Error fetching hadith: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("ActivityTracker", "📚 Error in playDailyHadithIfEnrolled: ${e.message}")
        }
    }

    /**
     * Speak hadith text using TextToSpeech in user's selected language
     * For Bengali language, uses pre-recorded audio files from assets
     */
    private fun speakHadith(ctx: Context, hadithNumber: Int, text: String) {
        // Get user's selected language
        val translationService = TranslationService.getInstance(ctx)
        val selectedLang = translationService.getSelectedLanguage()
        val ttsLocale = getLocaleForLanguage(selectedLang)

        Log.d("ActivityTracker", "📚 User's selected language: $selectedLang -> TTS Locale: ${ttsLocale.displayLanguage}")

        // For Bengali language, try to use pre-recorded audio files
        if (selectedLang == "bn") {
            Log.i("ActivityTracker", "📚 Bengali language selected - attempting to play audio file")
            if (playBukhariAudioFromAssets(ctx, hadithNumber)) {
                Log.i("ActivityTracker", "📚 Successfully playing Bengali audio for hadith #$hadithNumber")
                return
            } else {
                Log.w("ActivityTracker", "📚 Bengali audio not found for hadith #$hadithNumber - falling back to TTS")
            }
        }

        // No matching recording: read the exact English hadith with the selected Sherpa
        // model. Device-provided Bengali/translated TTS is intentionally never used.
        speakWithTts(ctx, hadithNumber, text, Locale.US, "en")
    }

    /**
     * Play Sahih Bukhari audio (Bengali) via AudioDownloadHelper (CDN first, then legacy SD card).
     * Kicks off a background download if missing so the next call can play it.
     *
     * @return true if a local file was found and playback started, false otherwise
     */
    private fun playBukhariAudioFromAssets(ctx: Context, hadithNumber: Int): Boolean {
        hadithMediaPlayer?.release()
        hadithMediaPlayer = null

        val helper = getAudioDownloadHelper(ctx)
        val audioFile = helper.resolveHadithAudioFile(hadithNumber)

        if (audioFile == null) {
            val cdnKey = helper.getHadithCdnKey(hadithNumber)
            Log.w("ActivityTracker", "📚 No Bengali audio for hadith #$hadithNumber locally; background-downloading $cdnKey")
            scope.launch {
                try {
                    helper.downloadAudio(cdnKey)
                } catch (e: Exception) {
                    Log.w("ActivityTracker", "📚 Background download failed for $cdnKey", e)
                }
            }
            return false
        }

        return try {
            hadithMediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)

                setOnCompletionListener { mp ->
                    mp.release()
                    hadithMediaPlayer = null
                    Log.d("ActivityTracker", "📚 Bengali hadith audio completed")
                    triggerVoiceCompletionPrompt(
                        ctx,
                        "daily_bukhari",
                        "hadith_$hadithNumber",
                        "Hadith #$hadithNumber",
                        isManualTriggerMode
                    )
                }

                setOnErrorListener { mp, what, extra ->
                    Log.e("ActivityTracker", "📚 MediaPlayer error: what=$what, extra=$extra")
                    mp.release()
                    hadithMediaPlayer = null
                    true
                }

                prepare()
                start()
            }
            Log.i("ActivityTracker", "📚 ▶️ Playing Bengali Bukhari audio: ${audioFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("ActivityTracker", "📚 Error playing audio file ${audioFile.absolutePath}: ${e.message}")
            false
        }
    }

    /**
     * Actually speak the text using TTS
     * Uses the user-selected Sherpa-ONNX model for exact English Bukhari fallback.
     */
    private fun speakWithTts(ctx: Context, hadithNumber: Int, text: String, locale: Locale, langCode: String) {
        // Get intro text in the appropriate language
        val introText = getHadithIntro(hadithNumber, langCode)
        val fullText = "$introText $text"

        // Store current hadith number for completion callback
        currentHadithNumber = hadithNumber

        val ttsPrefs = ctx.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        val selectedVoiceName = ttsPrefs.getString(
            "selected_voice",
            com.starception.submission.settings.components.TtsVoice.KOKORO_EN.name,
        ) ?: com.starception.submission.settings.components.TtsVoice.KOKORO_EN.name
        val selectedSpeakerId = ttsPrefs.getInt("selected_speaker_id", 0)
        Log.i("ActivityTracker", "📚 Using Sherpa-ONNX TTS: $selectedVoiceName, speaker $selectedSpeakerId")
        speakWithSherpaOnnxTts(ctx, hadithNumber, fullText, selectedVoiceName, selectedSpeakerId)
    }

    /**
     * Speak using Sherpa-ONNX TTS (offline, high-quality)
     */
    private fun speakWithSherpaOnnxTts(ctx: Context, hadithNumber: Int, text: String, voiceName: String, speakerId: Int) {
        scope.launch {
            try {
                // Get shared TTS service (uses Hilt singleton)
                val ttsService = getSharedTtsService(ctx)

                // Set voice from user settings
                val voice = try {
                    com.starception.submission.settings.components.TtsVoice.valueOf(voiceName)
                } catch (e: Exception) {
                    com.starception.submission.settings.components.TtsVoice.KOKORO_EN
                }
                ttsService.setVoice(voice)

                Log.i("ActivityTracker", "📚 Sherpa-ONNX TTS speaking hadith #$hadithNumber with ${voice.displayName}")

                val success = ttsService.speak(
                    text = text,
                    speakerId = speakerId,
                    onComplete = {
                        Log.d("ActivityTracker", "📚 Sherpa-ONNX TTS completed hadith #$hadithNumber")
                        // Trigger voice completion prompt
                        triggerVoiceCompletionPrompt(
                            ctx,
                            "daily_bukhari",
                            "hadith_$hadithNumber",
                            "Hadith #$hadithNumber",
                            isManualTriggerMode
                        )
                    }
                )

                if (!success) {
                    Log.e("ActivityTracker", "📚 Sherpa-ONNX TTS failed; system TTS fallback is disabled")
                }
            } catch (e: Exception) {
                Log.e("ActivityTracker", "📚 Sherpa-ONNX TTS error: ${e.message}")
            }
        }
    }

    /**
     * Speak using Android's built-in TextToSpeech
     */
    private fun speakWithAndroidTts(ctx: Context, hadithNumber: Int, text: String, locale: Locale) {
        val speechText = if (locale.language == Locale.ENGLISH.language) {
            EnglishTtsTextNormalizer.normalize(text)
        } else {
            text
        }
        // Initialize TTS if not already done
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(ctx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    val result = textToSpeech?.setLanguage(locale)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w("ActivityTracker", "📚 TTS language $locale not supported, falling back to US English")
                        textToSpeech?.language = Locale.US
                    }
                    Log.d("ActivityTracker", "📚 Android TTS initialized with language: ${textToSpeech?.language}")

                    // Set up utterance progress listener for completion callback
                    setupTtsCompletionListener(ctx)

                    textToSpeech?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "hadith_$hadithNumber")
                } else {
                    Log.e("ActivityTracker", "📚 Android TTS initialization failed with status: $status")
                }
            }
        } else if (isTtsInitialized) {
            // TTS already initialized, set language and speak
            val result = textToSpeech?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("ActivityTracker", "📚 TTS language $locale not supported, using current")
            }

            // Ensure listener is set up
            setupTtsCompletionListener(ctx)

            textToSpeech?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "hadith_$hadithNumber")
        }
    }

    /**
     * Set up TTS utterance progress listener for hadith completion callback.
     * Triggers voice completion prompt when hadith TTS playback finishes.
     */
    private fun setupTtsCompletionListener(ctx: Context) {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("ActivityTracker", "📚 TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d("ActivityTracker", "📚 TTS completed: $utteranceId")

                // Check if this is a hadith utterance
                if (utteranceId?.startsWith("hadith_") == true) {
                    val hadithNum = utteranceId.removePrefix("hadith_").toIntOrNull()
                    if (hadithNum != null) {
                        // Trigger voice completion prompt
                        triggerVoiceCompletionPrompt(
                            ctx,
                            "daily_bukhari",
                            "hadith_$hadithNum",
                            "Hadith #$hadithNum",
                            isManualTriggerMode
                        )
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("ActivityTracker", "📚 TTS error: $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("ActivityTracker", "📚 TTS error ($errorCode): $utteranceId")
            }
        })
    }

    /**
     * Get hadith intro text in the specified language
     */
    private fun getHadithIntro(hadithNumber: Int, langCode: String): String {
        return when (langCode) {
            "bn" -> "সহীহ আল-বুখারী থেকে হাদিস নম্বর $hadithNumber।"
            "ar" -> "حديث رقم $hadithNumber من صحيح البخاري."
            "es" -> "Hadiz número $hadithNumber de Sahih Al-Bujari."
            "fr" -> "Hadith numéro $hadithNumber de Sahih Al-Boukhari."
            "id" -> "Hadis nomor $hadithNumber dari Sahih Al-Bukhari."
            "ru" -> "Хадис номер $hadithNumber из Сахих аль-Бухари."
            "tr" -> "Sahih-i Buhari'den $hadithNumber numaralı hadis."
            "ur" -> "صحیح البخاری سے حدیث نمبر $hadithNumber۔"
            "zh" -> "来自《布哈里圣训》的第 $hadithNumber 条圣训。"
            else -> EnglishTtsTextNormalizer.bukhariIntro(hadithNumber)
        }
    }

    /**
     * Map language code to TTS Locale
     */
    private fun getLocaleForLanguage(langCode: String): Locale {
        return when (langCode) {
            "en" -> Locale.US
            "ar" -> Locale("ar")
            "bn" -> Locale("bn", "BD")
            "es" -> Locale("es", "ES")
            "fr" -> Locale.FRANCE
            "id" -> Locale("id", "ID")
            "ru" -> Locale("ru", "RU")
            "sv" -> Locale("sv", "SE")
            "tr" -> Locale("tr", "TR")
            "ur" -> Locale("ur", "PK")
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.US
        }
    }

    /**
     * Release TextToSpeech resources
     */
    fun releaseTts() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsInitialized = false
    }

    /**
     * Get the user-selected TTS voice from settings.
     * Returns the voice name and speaker ID, or null if not set.
     */
    private fun getUserSelectedTtsVoice(ctx: Context): Pair<com.starception.submission.settings.components.TtsVoice, Int> {
        val ttsPrefs = ctx.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)
        val selectedVoiceName = ttsPrefs.getString("selected_voice", null)
        val selectedSpeakerId = ttsPrefs.getInt("selected_speaker_id", 0)

        val voice = if (selectedVoiceName != null) {
            try {
                com.starception.submission.settings.components.TtsVoice.valueOf(selectedVoiceName)
            } catch (e: Exception) {
                com.starception.submission.settings.components.TtsVoice.KOKORO_EN
            }
        } else {
            com.starception.submission.settings.components.TtsVoice.KOKORO_EN
        }

        return Pair(voice, selectedSpeakerId)
    }

    /**
     * Speak short feedback message using Sherpa-ONNX TTS.
     * Used to give audio confirmation to user during hands-free operation.
     * Uses the user-selected TTS voice from settings.
     *
     * @param ctx Android context
     * @param message Short message to speak
     * @param onComplete Called after speaking completes
     */
    private fun speakFeedback(ctx: Context, message: String, onComplete: () -> Unit = {}) {
        Log.i("ActivityTracker", "🗣️ Speaking feedback: \"$message\"")
        scope.launch {
            try {
                // Get shared TTS service (uses Hilt singleton)
                val ttsService = getSharedTtsService(ctx)

                // Use user-selected TTS voice from settings
                val (voice, speakerId) = getUserSelectedTtsVoice(ctx)
                ttsService.setVoice(voice)
                Log.d("ActivityTracker", "🗣️ Using TTS voice: ${voice.displayName}, speaker $speakerId")

                val success = ttsService.speak(
                    text = message,
                    speakerId = speakerId,
                    onComplete = {
                        Log.d("ActivityTracker", "🗣️ Feedback complete: \"$message\"")
                        onComplete()
                    }
                )

                if (!success) {
                    Log.w("ActivityTracker", "🗣️ TTS failed, continuing without feedback")
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("ActivityTracker", "🗣️ Error speaking feedback: ${e.message}")
                onComplete()
            }
        }
    }

    /**
     * Trigger voice-based lesson completion prompt after hadith playback.
     * Only triggers if user is currently driving (unless bypassDrivingCheck is true).
     *
     * @param ctx Android context
     * @param courseId The course ID (e.g., "daily_bukhari")
     * @param lessonId The lesson ID (e.g., "hadith_1")
     * @param lessonTitle Human-readable title (e.g., "Hadith #1")
     * @param bypassDrivingCheck If true, will trigger even when not driving (for manual tests)
     */
    private fun triggerVoiceCompletionPrompt(
        ctx: Context,
        courseId: String,
        lessonId: String,
        lessonTitle: String,
        bypassDrivingCheck: Boolean = false
    ) {
        // Only trigger if currently driving (unless bypassing for manual test)
        if (!bypassDrivingCheck && _currentActivity.value != "Driving") {
            Log.d("ActivityTracker", "🎤 Skipping voice completion - not driving (${_currentActivity.value})")
            return
        }

        if (!CourseProgressTracker.isCompletionConfirmationMandatory(ctx, courseId)) {
            Log.i("ActivityTracker", "✅ Completion confirmation optional for $lessonId - auto-marking complete")
            CourseProgressTracker.markLessonCompleted(ctx, courseId, lessonId)
            speakFeedback(ctx, "Lesson marked complete!") {
                if (courseId == "daily_bukhari") {
                    Log.i("ActivityTracker", "📚➡️🕌 Hadith auto-complete - chaining to Quran listening")
                    playQuranListeningIfEnrolled(ctx)
                } else {
                    isManualTriggerMode = false
                }
            }
            return
        }

        scope.launch {
            try {
                // Initialize voice services if needed
                if (whisperVoiceService == null) {
                    Log.i("ActivityTracker", "🎤 Initializing Whisper voice service...")
                    whisperVoiceService = WhisperVoiceService(ctx, getAssetRepository(ctx))
                    val modelLoaded = whisperVoiceService?.loadModel() ?: false
                    if (!modelLoaded) {
                        Log.e("ActivityTracker", "🎤 Failed to load Whisper model")
                        return@launch
                    }
                }

                // Get shared TTS service (uses Hilt singleton)
                val ttsService = getSharedTtsService(ctx)

                if (sherpaOnnxKwsService == null) {
                    sherpaOnnxKwsService = SherpaOnnxKwsService(ctx, getAssetRepository(ctx))
                }

                if (voiceCompletionManager == null) {
                    voiceCompletionManager = VoiceCompletionManager(ctx, whisperVoiceService!!, sherpaOnnxKwsService!!, ttsService)
                }

                Log.i("ActivityTracker", "🎤 Triggering voice completion prompt for: $lessonTitle")

                voiceCompletionManager?.promptForCompletion(
                    courseId = courseId,
                    lessonId = lessonId,
                    lessonTitle = lessonTitle,
                    onComplete = {
                        Log.i("ActivityTracker", "✅ Voice completion: User said YES - marking lesson complete")
                        CourseProgressTracker.markLessonCompleted(ctx, courseId, lessonId)

                        // Give audio feedback that lesson was marked complete
                        speakFeedback(ctx, "Lesson marked complete!") {
                            // Chain Quran listening after hadith completion
                            if (courseId == "daily_bukhari") {
                                Log.i("ActivityTracker", "📚➡️🕌 Hadith complete - chaining to Quran listening")
                                playQuranListeningIfEnrolled(ctx)
                            } else {
                                // Reset manual trigger mode when chain ends
                                isManualTriggerMode = false
                            }
                        }
                    },
                    onSkipped = {
                        Log.i("ActivityTracker", "⏭️ Voice completion: User said NO - skipped")

                        speakFeedback(ctx, "Lesson skipped.") {
                            if (courseId == "daily_bukhari") {
                                Log.i("ActivityTracker", "📚➡️🕌 Hadith skipped - chaining to Quran listening anyway")
                                playQuranListeningIfEnrolled(ctx)
                            } else {
                                isManualTriggerMode = false
                            }
                        }
                    },
                    onInconclusive = { reason ->
                        Log.w("ActivityTracker", "⚠️ Voice completion inconclusive: $reason")
                        CourseProgressTracker.setPendingCompletion(ctx, courseId, lessonId, lessonTitle)
                        speakFeedback(ctx, "Could not confirm by voice. Please open the app later to confirm.") {
                            if (courseId == "daily_bukhari") {
                                Log.i("ActivityTracker", "📚➡️🕌 Hadith confirmation pending - chaining to Quran listening")
                                playQuranListeningIfEnrolled(ctx)
                            } else {
                                isManualTriggerMode = false
                            }
                        }
                    },
                    onError = { error ->
                        Log.e("ActivityTracker", "❌ Voice completion error: $error")

                        // Give audio feedback about the error, then continue
                        speakFeedback(ctx, "Could not hear response. Continuing to next lesson.") {
                            // Still chain Quran listening on error
                            if (courseId == "daily_bukhari") {
                                Log.i("ActivityTracker", "📚➡️🕌 Hadith error - chaining to Quran listening")
                                playQuranListeningIfEnrolled(ctx)
                            } else {
                                // Reset manual trigger mode when chain ends
                                isManualTriggerMode = false
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("ActivityTracker", "❌ Error in voice completion: ${e.message}", e)
            }
        }
    }

    // ==================== Complete Quran Listening Course Methods ====================

    /**
     * Bind to QuranPlaybackService for course playback
     */
    private fun bindQuranService(ctx: Context) {
        if (isQuranServiceBound) {
            Log.d("ActivityTracker", "🕌 Quran service already bound")
            return
        }

        quranServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? QuranPlaybackService.QuranBinder
                quranService = binder?.getService()
                isQuranServiceBound = true
                Log.i("ActivityTracker", "🕌 Quran service connected")

                // Set up callbacks for course mode
                quranService?.onPositionChanged = { position ->
                    CourseProgressTracker.updateQuranPosition(ctx, position)
                }

                quranService?.onSurahCompleted = { surahIndex ->
                    handleQuranSurahCompletion(ctx, surahIndex)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                quranService = null
                isQuranServiceBound = false
                Log.w("ActivityTracker", "🕌 Quran service disconnected")
            }
        }

        val intent = android.content.Intent(ctx, QuranPlaybackService::class.java)
        ctx.bindService(intent, quranServiceConnection!!, Context.BIND_AUTO_CREATE)
    }

    /**
     * Unbind from QuranPlaybackService
     */
    private fun unbindQuranService(ctx: Context) {
        if (isQuranServiceBound && quranServiceConnection != null) {
            try {
                ctx.unbindService(quranServiceConnection!!)
            } catch (e: Exception) {
                Log.e("ActivityTracker", "🕌 Error unbinding Quran service: ${e.message}")
            }
            quranService = null
            isQuranServiceBound = false
            quranServiceConnection = null
        }
    }

    /**
     * Play Quran listening if user is enrolled in the Complete Quran Listening course.
     * Called after Bukhari hadith playback completes.
     */
    private fun playQuranListeningIfEnrolled(ctx: Context) {
        try {
            // Check enrollment
            if (!CourseProgressTracker.isEnrolledInQuranListening(ctx)) {
                Log.d("ActivityTracker", "🕌 User not enrolled in Complete Quran Listening course - skipping")
                // Give feedback that audio chain is complete (in manual mode only)
                if (isManualTriggerMode) {
                    speakFeedback(ctx, "Audio chain complete. Enroll in Complete Quran Listening course to continue with Quran playback.") {
                        isManualTriggerMode = false
                    }
                } else {
                    isManualTriggerMode = false
                }
                return
            }

            // Only play if currently driving (unless in manual trigger mode)
            if (!isManualTriggerMode && _currentActivity.value != "Driving") {
                Log.d("ActivityTracker", "🕌 Not driving - skipping Quran listening")
                return
            }

            Log.i("ActivityTracker", "🕌 ========== QURAN LISTENING PLAYBACK ==========")
            Log.i("ActivityTracker", "🕌 User enrolled in Complete Quran Listening course")

            // Get current progress
            val progress = CourseProgressTracker.getQuranListeningProgress(ctx)
            val surahName = if (progress.currentSurahIndex < QuranData.surahs.size) {
                QuranData.surahs[progress.currentSurahIndex].nameEnglish
            } else {
                "Surah ${progress.currentSurahNumber}"
            }

            Log.i("ActivityTracker", "🕌 Resuming: Surah ${progress.currentSurahNumber} ($surahName)")
            Log.i("ActivityTracker", "🕌 Position: ${progress.currentPositionMs / 1000}s")

            // Bind to service if not already bound
            if (!isQuranServiceBound) {
                bindQuranService(ctx)
                // Wait briefly for service to bind, then start playback
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startQuranPlayback(ctx, progress)
                }, 500)
            } else {
                startQuranPlayback(ctx, progress)
            }

        } catch (e: Exception) {
            Log.e("ActivityTracker", "🕌 Error in playQuranListeningIfEnrolled: ${e.message}", e)
        }
    }

    /**
     * Start Quran playback from saved progress
     */
    private fun startQuranPlayback(ctx: Context, progress: QuranListeningProgress) {
        if (quranService == null) {
            Log.e("ActivityTracker", "🕌 Quran service not available")
            // Give feedback and reset
            speakFeedback(ctx, "Quran playback service not available.") {
                isManualTriggerMode = false
            }
            return
        }

        // Get surah name for announcement
        val surahName = if (progress.currentSurahIndex < QuranData.surahs.size) {
            QuranData.surahs[progress.currentSurahIndex].nameEnglish
        } else {
            "Surah ${progress.currentSurahNumber}"
        }

        // Announce what's playing
        speakFeedback(ctx, "Now playing Surah $surahName") {
            // Start listening session
            CourseProgressTracker.startQuranListeningSession(ctx)

            // Start playback from saved position
            quranService?.playSurahForCourse(
                surahIndex = progress.currentSurahIndex,
                startPosition = progress.currentPositionMs,
                forCourse = true
            )

            Log.i("ActivityTracker", "🕌 ▶️ Started Quran playback for course mode")
        }
    }

    /**
     * Handle surah completion - trigger voice confirmation
     */
    private fun handleQuranSurahCompletion(ctx: Context, surahIndex: Int) {
        val surahName = if (surahIndex < QuranData.surahs.size) {
            QuranData.surahs[surahIndex].nameEnglish
        } else {
            "Surah ${surahIndex + 1}"
        }
        val lessonId = "surah_${surahIndex + 1}"
        val lessonTitle = "Surah $surahName"

        Log.i("ActivityTracker", "🕌 Surah completed: $lessonTitle")

        // Trigger voice completion prompt with Quran-specific callbacks
        triggerQuranVoiceCompletionPrompt(ctx, lessonId, lessonTitle, surahIndex)
    }

    /**
     * Trigger voice completion prompt specifically for Quran listening course.
     * Handles YES/NO with appropriate follow-up actions.
     */
    private fun triggerQuranVoiceCompletionPrompt(
        ctx: Context,
        lessonId: String,
        lessonTitle: String,
        surahIndex: Int
    ) {
        // Only trigger if currently driving (unless in manual trigger mode)
        if (!isManualTriggerMode && _currentActivity.value != "Driving") {
            Log.d("ActivityTracker", "🕌 Skipping voice completion - not driving")
            // Still save progress even if not driving
            return
        }

        if (!CourseProgressTracker.isCompletionConfirmationMandatory(ctx, "complete_quran_listening")) {
            Log.i("ActivityTracker", "🕌 Completion confirmation optional for $lessonId - auto-marking complete")
            speakFeedback(ctx, "Surah marked complete!") {
                completeQuranLessonAndContinue(ctx, surahIndex)
            }
            return
        }

        scope.launch {
            try {
                // Initialize voice services if needed
                if (whisperVoiceService == null) {
                    Log.i("ActivityTracker", "🕌 Initializing Whisper voice service...")
                    whisperVoiceService = WhisperVoiceService(ctx, getAssetRepository(ctx))
                    val modelLoaded = whisperVoiceService?.loadModel() ?: false
                    if (!modelLoaded) {
                        Log.e("ActivityTracker", "🕌 Failed to load Whisper model - auto-completing")
                        // Auto-complete on voice service failure
                        completeQuranLessonAndContinue(ctx, surahIndex)
                        return@launch
                    }
                }

                // Get shared TTS service (uses Hilt singleton)
                val ttsService = getSharedTtsService(ctx)

                if (sherpaOnnxKwsService == null) {
                    sherpaOnnxKwsService = SherpaOnnxKwsService(ctx, getAssetRepository(ctx))
                }

                if (voiceCompletionManager == null) {
                    voiceCompletionManager = VoiceCompletionManager(ctx, whisperVoiceService!!, sherpaOnnxKwsService!!, ttsService)
                }

                Log.i("ActivityTracker", "🕌 Triggering voice completion prompt for: $lessonTitle")

                voiceCompletionManager?.promptForCompletion(
                    courseId = "complete_quran_listening",
                    lessonId = lessonId,
                    lessonTitle = lessonTitle,
                    onComplete = {
                        Log.i("ActivityTracker", "🕌 ✅ Voice completion: User said YES - marking lesson complete")
                        // Give audio feedback and continue
                        speakFeedback(ctx, "Surah marked complete!") {
                            completeQuranLessonAndContinue(ctx, surahIndex)
                        }
                    },
                    onSkipped = {
                        Log.i("ActivityTracker", "🕌 ⏭️ Voice completion: User said NO - saving position and stopping")
                        speakFeedback(ctx, "Quran listening session ended.") {
                            CourseProgressTracker.endQuranListeningSession(ctx)
                            quranService?.stopCourseMode()
                            isManualTriggerMode = false
                        }
                    },
                    onInconclusive = { reason ->
                        Log.w("ActivityTracker", "🕌 ⚠️ Voice completion inconclusive: $reason")
                        CourseProgressTracker.setPendingCompletion(ctx, "complete_quran_listening", lessonId, lessonTitle)
                        speakFeedback(ctx, "Could not confirm by voice. Please open the app later to confirm.") {
                            CourseProgressTracker.endQuranListeningSession(ctx)
                            quranService?.stopCourseMode()
                            isManualTriggerMode = false
                        }
                    },
                    onError = { error ->
                        Log.e("ActivityTracker", "🕌 ❌ Voice completion error: $error - auto-completing")
                        // Give feedback about error and auto-continue
                        speakFeedback(ctx, "Could not hear response. Continuing to next surah.") {
                            completeQuranLessonAndContinue(ctx, surahIndex)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("ActivityTracker", "🕌 ❌ Error in Quran voice completion: ${e.message}", e)
                // Auto-complete on exception
                completeQuranLessonAndContinue(ctx, surahIndex)
            }
        }
    }

    /**
     * Complete current Quran lesson and continue to next surah
     */
    private fun completeQuranLessonAndContinue(ctx: Context, completedSurahIndex: Int) {
        // Mark lesson as complete and get next surah index
        val nextSurahIndex = CourseProgressTracker.completeCurrentSurah(ctx)

        // Check if still driving (unless in manual trigger mode)
        if (!isManualTriggerMode && _currentActivity.value != "Driving") {
            Log.i("ActivityTracker", "🕌 No longer driving - ending Quran session after completion")
            CourseProgressTracker.endQuranListeningSession(ctx)
            quranService?.stopCourseMode()
            return
        }

        // Continue to next surah
        val nextSurahName = if (nextSurahIndex < QuranData.surahs.size) {
            QuranData.surahs[nextSurahIndex].nameEnglish
        } else {
            "Surah ${nextSurahIndex + 1}"
        }

        Log.i("ActivityTracker", "🕌 ▶️ Continuing to Surah ${nextSurahIndex + 1} ($nextSurahName)")

        quranService?.playSurahForCourse(
            surahIndex = nextSurahIndex,
            startPosition = 0,
            forCourse = true
        )
    }

    /**
     * Stop Quran listening course playback
     * Called when user stops driving or manually stops
     */
    fun stopQuranListening() {
        context?.let { ctx ->
            if (quranService?.isInCourseMode() == true) {
                Log.i("ActivityTracker", "🕌 Stopping Quran listening session")
                CourseProgressTracker.endQuranListeningSession(ctx)
                quranService?.stopCourseMode()
                quranService?.togglePlayPause() // Pause playback
            }
        }
    }

    /**
     * TEST FUNCTION: Test Quran listening course playback
     */
    fun testQuranListening() {
        Log.i("ActivityTracker", "🧪 ========== TEST: Quran Listening Course ==========")
        context?.let { ctx ->
            // Temporarily set activity to driving for test
            val originalActivity = _currentActivity.value
            _currentActivity.value = "Driving"
            playQuranListeningIfEnrolled(ctx)
            // Note: Don't reset activity - leave as driving for full test
        } ?: Log.e("ActivityTracker", "🧪 TEST FAILED: Context is null - call initialize() first")
    }

    // ==================== End Complete Quran Listening Methods ====================

    /**
     * TEST FUNCTION: Demo the daily hadith playback feature
     * Call this to test hadith TTS without needing to drive
     */
    fun testDailyHadithPlayback() {
        Log.i("ActivityTracker", "🧪 ========== TEST: Daily Hadith Playback ==========")
        isManualTriggerMode = true
        context?.let { ctx ->
            playDailyHadithIfEnrolled(ctx)
        } ?: run {
            isManualTriggerMode = false
            Log.e("ActivityTracker", "🧪 TEST FAILED: Context is null - call initialize() first")
        }
    }

    /**
     * Manually trigger the full audio chain: Travel Dua → Hadith → Quran
     * This plays the same sequence that would play during driving, but manually triggered.
     * Uses DrivingAudioService for proper MediaSession and notification controls.
     * Useful for testing or when user wants to listen without driving.
     */
    fun triggerFullAudioChain() {
        Log.i("ActivityTracker", "🎵 ========== MANUAL TRIGGER: Full Audio Chain ==========")
        Log.i("ActivityTracker", "🎵 Sequence: Travel Dua → Hadith (if enrolled) → Quran (if enrolled)")
        // Set manual trigger mode to bypass driving check for voice completion
        isManualTriggerMode = true
        context?.let { ctx ->
            try {
                // Use DrivingAudioService for proper MediaSession notification
                scope.launch {
                    val hadithInfo = getNextHadithInfo(ctx)

                    // Start DrivingAudioService with travel dua
                    val serviceIntent = Intent(ctx, DrivingAudioService::class.java).apply {
                        putExtra(DrivingAudioService.EXTRA_AUDIO_TYPE, DrivingAudioService.TYPE_TRAVEL_DUA)

                        // Pass hadith info if enrolled
                        hadithInfo?.let { (number, text, useAudio) ->
                            putExtra(DrivingAudioService.EXTRA_HADITH_NUMBER, number)
                            if (text != null) {
                                putExtra(DrivingAudioService.EXTRA_HADITH_TEXT, text)
                            }
                            putExtra(DrivingAudioService.EXTRA_COURSE_ID, "daily_bukhari")
                            putExtra(DrivingAudioService.EXTRA_LESSON_ID, "hadith_$number")
                        }
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        ctx.startForegroundService(serviceIntent)
                    } else {
                        ctx.startService(serviceIntent)
                    }

                    Log.i("ActivityTracker", "🎵 DrivingAudioService started with notification controls")
                }
            } catch (e: Exception) {
                Log.e("ActivityTracker", "🎵 Error triggering audio chain: ${e.message}")
            }
        } ?: Log.e("ActivityTracker", "🎵 FAILED: Context is null - call initialize() first")
    }

    /**
     * TEST FUNCTION: Test voice completion flow (Whisper STT + TTS)
     * Tests: TTS prompt → Whisper listening → Confirmation feedback
     * Say "YES" or "NO" when prompted!
     */
    fun testVoiceCompletion() {
        Log.i("ActivityTracker", "🧪 ========== TEST: Voice Completion ==========")
        context?.let { ctx ->
            scope.launch {
                try {
                    // Initialize voice services if needed
                    if (whisperVoiceService == null) {
                        Log.i("ActivityTracker", "🧪 Initializing Whisper...")
                        whisperVoiceService = WhisperVoiceService(ctx, getAssetRepository(ctx))
                        val modelLoaded = whisperVoiceService?.loadModel() ?: false
                        if (!modelLoaded) {
                            Log.e("ActivityTracker", "🧪 Failed to load Whisper model")
                            return@launch
                        }
                        Log.i("ActivityTracker", "🧪 Whisper model loaded!")
                    }

                    // Get shared TTS service (uses Hilt singleton)
                    val ttsService = getSharedTtsService(ctx)
                    Log.i("ActivityTracker", "🧪 Using shared Sherpa-ONNX TTS service")

                    if (sherpaOnnxKwsService == null) {
                        sherpaOnnxKwsService = SherpaOnnxKwsService(ctx, getAssetRepository(ctx))
                    }

                    if (voiceCompletionManager == null) {
                        voiceCompletionManager = VoiceCompletionManager(ctx, whisperVoiceService!!, sherpaOnnxKwsService!!, ttsService)
                    }

                    Log.i("ActivityTracker", "🧪 Starting voice completion test...")
                    Log.i("ActivityTracker", "🧪 🎤 Say 'YES' or 'NO' when prompted!")
                    voiceCompletionManager?.runTest()

                } catch (e: Exception) {
                    Log.e("ActivityTracker", "🧪 TEST FAILED: ${e.message}", e)
                }
            }
        } ?: Log.e("ActivityTracker", "🧪 TEST FAILED: Context is null - call initialize() first")
    }

    /**
     * TEST FUNCTION: Test FAST keyword spotting (Sherpa-ONNX KWS)
     * This is ~100ms vs ~26 seconds for Whisper!
     * Say "YES" or "NO" when prompted!
     */
    fun testFastKws() {
        Log.i("ActivityTracker", "🧪 ========== TEST: FAST Keyword Spotting (KWS) ==========")
        Log.i("ActivityTracker", "🧪 This uses Sherpa-ONNX KWS (~100ms) instead of Whisper (~26s)")
        context?.let { ctx ->
            scope.launch {
                try {
                    // Initialize KWS service if needed
                    if (sherpaOnnxKwsService == null) {
                        Log.i("ActivityTracker", "🧪 Initializing Sherpa-ONNX KWS...")
                        sherpaOnnxKwsService = SherpaOnnxKwsService(ctx, getAssetRepository(ctx))
                        val modelLoaded = sherpaOnnxKwsService?.loadModel() ?: false
                        if (!modelLoaded) {
                            Log.e("ActivityTracker", "🧪 Failed to load KWS model")
                            return@launch
                        }
                        Log.i("ActivityTracker", "🧪 KWS model loaded!")
                    }

                    Log.i("ActivityTracker", "🧪 🎤 Say 'YES' or 'NO' within 5 seconds!")
                    sherpaOnnxKwsService?.runTest()

                } catch (e: Exception) {
                    Log.e("ActivityTracker", "🧪 TEST FAILED: ${e.message}", e)
                }
            }
        } ?: Log.e("ActivityTracker", "🧪 TEST FAILED: Context is null - call initialize() first")
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

            // CRITICAL: Load persisted cooldown timestamp to survive app restarts
            lastDuaPlayTime = prefs.getLong(KEY_LAST_DUA_PLAY_TIME, 0L)
            duaPlayedForCurrentSession = prefs.getBoolean(
                TravelDuaSettings.KEY_DUA_PLAYED_FOR_CURRENT_TRIP,
                false,
            )
            drivingStopTime = prefs.getLong(TravelDuaSettings.KEY_DRIVING_STOP_TIME, 0L)
            // Only restore Google authority when a Travel Dua is still pending. This
            // preserves the countdown across a sticky service/process restart without
            // allowing a missed EXIT to leave driving latched after playback completes.
            googleDrivingConfirmed = prefs.getBoolean(
                TravelDuaSettings.KEY_GOOGLE_DRIVING_CONFIRMED,
                false,
            ) &&
                prefs.contains(TravelDuaSettings.KEY_PENDING_ALARM_TOKEN)
            if (googleDrivingConfirmed) {
                _currentActivity.value = "Driving"
                Log.i("ActivityTracker", "🚗 Restored Google-confirmed driving session with pending alarm")
            } else {
                prefs.edit()
                    .putBoolean(TravelDuaSettings.KEY_GOOGLE_DRIVING_CONFIRMED, false)
                    .remove(TravelDuaSettings.KEY_GOOGLE_DRIVING_CONFIRMED_ELAPSED)
                    .apply()
            }
            val timeSinceLastDua = if (lastDuaPlayTime > 0) (System.currentTimeMillis() - lastDuaPlayTime) / 1000 else -1L

            Log.i("ActivityTracker", "🚗 Travel dua settings loaded: enabled=$travelDuaEnabled, cooldown=${cooldownMinutes}min, delay=${playbackDelaySeconds}s, gap=${gapToleranceMinutes}min")
            Log.i("ActivityTracker", "🚗 Persisted cooldown: ${if (lastDuaPlayTime == 0L) "NEVER played" else "Last played ${timeSinceLastDua}s ago"}")

            // Check if cooldown is still active from previous session
            if (lastDuaPlayTime > 0 && timeSinceLastDua >= 0 && timeSinceLastDua < cooldownMinutes * 60) {
                val remainingSeconds = (cooldownMinutes * 60) - timeSinceLastDua
                Log.w("ActivityTracker", "🚗 ⏳ COOLDOWN STILL ACTIVE from previous session - ${remainingSeconds}s remaining")
            }
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

        Log.i("ActivityTracker", "🚗 Travel dua settings updated: enabled=${settings.enabled}, cooldown=${settings.cooldownMinutes}min, delay=${settings.playbackDelaySeconds}s, gap=${settings.gapToleranceMinutes}min, speedThreshold=${settings.drivingSpeedThresholdKmh}km/h")

        // Update driving speed threshold in ActivityDetectionService
        activityDetectionService?.updateDrivingSpeedThreshold(settings.drivingSpeedThresholdKmh)

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
