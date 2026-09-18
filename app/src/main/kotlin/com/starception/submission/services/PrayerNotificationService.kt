package com.starception.submission.services

import com.starception.submission.feature.prayertimes.getPrayerDisplayName

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import com.starception.submission.R
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import com.starception.submission.prayer.model.DayPrayerTimes
import java.time.LocalDateTime
import com.starception.submission.prayer.model.PrayerTime
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.util.PrayerNotificationManager
import com.starception.submission.feature.prayertimes.weather.prayerWeatherNotificationBitmap
import com.starception.submission.feature.prayertimes.weather.prayerWeatherNotificationTrackerIcon
import com.starception.submission.util.GoogleSampleNotificationManager
import com.starception.submission.util.AnrPreventionConfig
import com.starception.submission.util.ActivityTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import com.starception.submission.feature.prayertimes.weather.CurrentWeatherRepository
import com.starception.submission.feature.prayertimes.weather.PrayerWeatherThresholdStore
import javax.inject.Inject
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.starception.submission.prayer.util.FileLogger

/**
 * Prayer Notification Service with ANR Protection
 * Provides live prayer time updates with real prayer data
 */
@AndroidEntryPoint
class PrayerNotificationService : Service() {
    
    @Inject
    lateinit var prayerTimeCalculatorService: PrayerTimeCalculatorService

    @Inject
    lateinit var prayerSettingsRepository: PrayerSettingsRepository

    @Inject
    lateinit var locationService: com.starception.submission.prayer.service.EnhancedLocationService
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isServiceRunning = false
    private var isInitializing = false
    private var previousPrayerPhase: String? = null // Track previous phase for smart notifications
    
    // Live Update notification manager (separate from foreground service)
    private lateinit var notificationManager: NotificationManager
    
    // Activity Recognition
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var activityTransitionPendingIntent: PendingIntent? = null
    private var currentActivity: String = "UNKNOWN"
    private var hasLocationForegroundType = false
    private var lastForegroundNotification: Notification? = null
    private var toneGenerator: ToneGenerator? = null
    private var timezoneReceiver: BroadcastReceiver? = null

    // HandlerThread for sensor callbacks - CRITICAL for background activity detection
    // Android 9+ throttles sensors for background apps, but foreground service handlers work
    private var sensorHandlerThread: android.os.HandlerThread? = null
    private var sensorHandler: android.os.Handler? = null
    
    companion object {
        private const val TAG = "PrayerNotificationService"
        
        // NOTIFICATION CONFIGURATION - Edit these to change notification behavior
        private const val NOTIFICATION_CHANNEL_ID = "prayer_live_update_channel"
        private const val NOTIFICATION_ID = 1001  // Single notification ID for both foreground service and live update
        private const val LIVE_UPDATE_NOTIFICATION_ID = 1001 // Same ID as foreground - replaces it with live update
        
        const val ACTION_ACTIVITY_TRANSITION =
            "com.starception.submission.action.ACTIVITY_TRANSITION"
        const val EXTRA_DETECTED_ACTIVITY = "detected_activity"
        const val EXTRA_TRANSITION_TYPE = "transition_type"
        const val EXTRA_IN_VEHICLE_TRANSITION_TYPE = "in_vehicle_transition_type"
        
        // Check if service is running in another process (NON-BLOCKING with timeout)
        fun isServiceRunningInAnotherProcess(context: android.content.Context): Boolean {
            return try {
                // Quick timeout to prevent ANR - getRunningServices can be very slow
                val startTime = System.currentTimeMillis()
                val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                
                // Use smaller limit to prevent blocking - we only need to check if ANY instance exists
                val runningServices = manager.getRunningServices(50) // Much smaller limit
                val endTime = System.currentTimeMillis()
                
                val ourServiceCount = runningServices.count {
                    it.service.className == PrayerNotificationService::class.java.name
                }
                
                Log.d(TAG, "Found $ourServiceCount instances of PrayerNotificationService running (took ${endTime - startTime}ms)")
                
                if (endTime - startTime > 1000) {
                    Log.w(TAG, "⚠️ getRunningServices took ${endTime - startTime}ms - this could cause ANR!")
                }
                
                return ourServiceCount > 0
            } catch (e: Exception) {
                Log.w(TAG, "Error checking service status: ${e.message}")
                false // Assume not running to allow restart
            }
        }
        
        // Get current instance count for debugging (NON-BLOCKING)
        fun getServiceInstanceCount(context: android.content.Context): Int {
            return try {
                val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                manager.getRunningServices(50).count { // Smaller limit to prevent ANR
                    it.service.className == PrayerNotificationService::class.java.name
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error getting service count: ${e.message}")
                0 // Safe default
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Prayer notification service created")
        FileLogger.i(TAG, "Service onCreate - Prayer notification service created")
        
        // Create notification channel and basic setup - keeping ANR protection
        try {
            createNotificationChannel()
            PrayerNotificationManager.initialize(this)

            // Initialize notification manager for separate Live Update notifications
            notificationManager = getSystemService(NotificationManager::class.java)

            // The Live Updates builder is normally initialized by MainActivity.
            // When this service starts from boot the app never ran, so
            // buildPrayerNotification would throw (lateinit appContext) and the
            // notification would stay stuck on the minimal placeholder.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !GoogleSampleNotificationManager.isInitialized()
            ) {
                GoogleSampleNotificationManager.initialize(applicationContext, notificationManager)
                Log.d(TAG, "✓ GoogleSampleNotificationManager initialized from service (boot path)")
            }
            Log.d(TAG, "✓ Live Update notification manager initialized separately from foreground service")

            // Register timezone change receiver for dynamic updates
            registerTimezoneReceiver()

            // Initialize Google Play Services Activity Recognition (runs async to prevent ANR)
            // This provides backup activity detection alongside sensor-based detection
            serviceScope.launch(Dispatchers.IO) {
                try {
                    initializeActivityRecognitionAsync()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize activity recognition: ${e.message}")
                }
            }
            
            // Initialize ActivityTracker with sensor-based detection
            // CRITICAL: Create HandlerThread for sensor callbacks FIRST
            // This ensures sensors deliver updates even when app is in background (Android 9+)
            try {
                // Create HandlerThread for sensor callbacks within foreground service context
                if (sensorHandlerThread == null) {
                    sensorHandlerThread = android.os.HandlerThread("ForegroundSensorThread", Thread.MAX_PRIORITY)
                    sensorHandlerThread!!.start()
                    sensorHandler = android.os.Handler(sensorHandlerThread!!.looper)
                    Log.i(TAG, "✅ Created foreground service HandlerThread for sensor callbacks")
                }

                // Pass handler to ActivityTracker BEFORE starting detection
                ActivityTracker.setSensorHandler(sensorHandler)
                Log.d(TAG, "✓ Sensor handler passed to ActivityTracker")

                // Now initialize ActivityTracker - it will use our foreground service handler
                ActivityTracker.initialize(this, startDetectionNow = true)
                Log.d(TAG, "✓ ActivityTracker initialized with FOREGROUND SERVICE sensor handler (background-ready)")

                // Register callback to update notification icon when activity changes
                ActivityTracker.setActivityChangeCallback { newActivity ->
                    Log.d(TAG, "🏃 Activity changed to: $newActivity - triggering notification refresh")
                    serviceScope.launch {
                        try {
                            updatePrayerNotificationWithRealData()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating notification for activity change", e)
                        }
                    }
                }
                Log.d(TAG, "✓ Activity change callback registered for notification updates")

                // Schedule WorkManager to keep activity detection alive (survives Doze mode)
                com.starception.submission.worker.ActivityDetectionWorker.schedule(this)
                Log.d(TAG, "✓ Activity detection keep-alive worker scheduled")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing ActivityTracker", e)
                ActivityTracker.updateActivity("Detection Error")
            }
            
            Log.d(TAG, "✓ Service onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
        }
    }
    
    /**
     * Starts/updates the foreground notification with types valid for the current
     * state. On Android 14+, a visible app start can use while-in-use location and
     * retain that foreground capability after the UI closes. A background cold start
     * without "Allow all the time" falls back to specialUse so boot remains reliable.
     */
    private fun startForegroundWithSafeTypes(notification: Notification) {
        // Retain the current notification so a later duplicate foreground-service
        // start can acknowledge Android's new promotion deadline without replacing
        // the live prayer content with the startup placeholder.
        lastForegroundNotification = notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasFineLocation = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarseLocation = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            // When this service is started while an Activity is visible, while-in-use
            // location permission is sufficient and the location FGS remains valid after
            // the UI is backgrounded. The old ACCESS_BACKGROUND_LOCATION-only check kept
            // the service as specialUse even on this valid path, so Android throttled the
            // detector's one-second GPS request to roughly one update every ten minutes.
            if (hasFineLocation || hasCoarseLocation) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                    )
                    hasLocationForegroundType = true
                    Log.i(TAG, "Foreground service running with location + specialUse")
                    return
                } catch (e: SecurityException) {
                    // A boot/activity-transition cold start cannot use a while-in-use
                    // permission. Keep prayer updates alive and let a later visible app
                    // start upgrade the already-running service to the location type.
                    Log.w(TAG, "Location FGS unavailable from this background start; using specialUse", e)
                }
            }

            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Prayer notification service onStartCommand - startId: $startId, isServiceRunning: $isServiceRunning")

        if (isServiceRunning) {
            // Every startForegroundService() request creates a fresh promotion
            // deadline on affected Samsung/Android builds, even when this Service
            // instance is already foreground. Always acknowledge the duplicate start.
            // Reuse the current live notification so app resume does not flash the
            // generic startup notification.
            startForegroundWithSafeTypes(
                lastForegroundNotification ?: createInitialNotification(),
            )
            handleActivityTransitionIntent(intent)
            Log.i(TAG, "Service already running; foreground start re-acknowledged - startId: $startId")
            return START_STICKY
        }

        try {
            // IMMEDIATE FOREGROUND START - but with proper notification now that dependencies are available
            Log.d(TAG, "Starting foreground service immediately - startId: $startId")
            startForegroundWithSafeTypes(createInitialNotification())

            // Clear any old/legacy notifications that may be lingering
            // ID 1003 was previously used for separate activity change notifications (now merged into main)
            // ID 3001 was used by ActivityBasedDuaService (now merged into this service)
            notificationManager.cancel(1003)
            notificationManager.cancel(3001)
            Log.d(TAG, "🧹 Cleared legacy notifications (ID 1003 and 3001)")

            // Mark service as running IMMEDIATELY
            isServiceRunning = true

            // A manifest activity-transition receiver can cold-start this service while
            // the UI is absent. Apply that event only after startForeground(), keeping
            // Android's foreground-service startup contract intact.
            handleActivityTransitionIntent(intent)
            
            // Background initialization with ANR protection - dependencies should be available now
            serviceScope.launch(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Background initialization starting...")
                    delay(100) // Small delay to ensure service startup completes
                    
                    // Start prayer updates with extended timeout protection
                    // Start prayer updates without timeout restriction
                    startRealPrayerTimeUpdates()
                    Log.d(TAG, "✓ Prayer updates started successfully")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Background initialization failed: ${e.message}")
                    // Service continues running with basic notification
                }
            }
            
            Log.d(TAG, "✅ Service onStartCommand completed - startId: $startId")
            return START_STICKY
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}")
            // Emergency fallback
            try {
                startForegroundWithSafeTypes(createEmergencyNotification())
                isServiceRunning = true
                Log.w(TAG, "Emergency fallback notification started")
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Emergency fallback failed: ${fallbackError.message}")
            }
            return START_STICKY
        }
    }

    private fun handleActivityTransitionIntent(intent: Intent?) {
        if (intent?.action != ACTION_ACTIVITY_TRANSITION) return

        val detectedActivity = intent.getStringExtra(EXTRA_DETECTED_ACTIVITY) ?: return
        val transitionType = intent.getIntExtra(EXTRA_TRANSITION_TYPE, -1)
        val inVehicleTransitionType = intent.getIntExtra(
            EXTRA_IN_VEHICLE_TRANSITION_TYPE,
            -1,
        )
        val previousActivity = currentActivity
        currentActivity = detectedActivity

        Log.i(
            TAG,
            "🏃 Background activity transition: $previousActivity → $detectedActivity " +
                "(${getTransitionString(transitionType)})",
        )
        ActivityTracker.updateActivityFromTransition(
            detectedActivity,
            inVehicleTransitionType,
        )
        serviceScope.launch {
            runCatching { updatePrayerNotificationWithRealData() }
                .onFailure { Log.e(TAG, "Failed to refresh live update after activity transition", it) }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Prayer Live Updates",
                NotificationManager.IMPORTANCE_HIGH // High importance for lock screen visibility
            ).apply {
                description = "Prayer time notifications and live updates - shows on lock screen"
                enableVibration(true) // Enable vibration for prayer notifications
                setShowBadge(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // Explicitly enable lock screen visibility
                setBypassDnd(false) // Respect Do Not Disturb for silent updates
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Prayer Live Updates notification channel created with lock screen visibility")
        }
    }
    
    private fun createAbsoluteMinimumNotification(): Notification {
        return try {
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Prayer Service")
                .setContentText("Active")
                .setSmallIcon(R.drawable.ic_prayer)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN) // Absolute minimum priority
                .setSilent(true) // No sound/vibration
                .build()
        } catch (e: Exception) {
            // If even this fails, create with system icon
            Log.w(TAG, "Using system icon for emergency notification: ${e.message}")
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Service")
                .setContentText("Running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .build()
        }
    }
    
    private fun createMinimalNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Prayer Tracker")
            .setContentText("Starting...")
            .setSmallIcon(R.drawable.ic_prayer)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority to avoid blocking
            .build()
    }
    
    private fun createEmergencyNotification(): Notification {
        // Emergency simple foreground service notification - ONLY for keeping service alive
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Prayer Service")
            .setContentText("Emergency mode")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Use system icon as fallback
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hide this simple notification
            .setPriority(NotificationCompat.PRIORITY_MIN) // Minimal priority
            .setSilent(true)
            .build()
    }
    
    private fun createInitialNotification(): Notification {
        // Simple foreground service notification - ONLY for keeping service alive
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Prayer Service")
            .setContentText("Background service running")
            .setSmallIcon(R.drawable.ic_prayer)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hide this simple notification
            .setPriority(NotificationCompat.PRIORITY_MIN) // Minimal priority
            .setShowWhen(false)
            .setSilent(true)
            .build()
    }
    
    /**
     * Create Live Update notification directly (separate from foreground service)
     * This ensures PROMOTED_ONGOING flag without FOREGROUND_SERVICE flag
     */
    @RequiresApi(35)
    private fun createLiveUpdateNotification(
        title: String,
        content: String,
        detailedMessage: String = "",
        progress: Int = 0
    ): Notification {
        Log.d(TAG, "🎯 Creating direct Live Update notification: $title")

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_prayer)
            .setOngoing(true)
            .setRequestPromotedOngoing(true) // CRITICAL: This enables Live Updates!
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis()) // Always update timestamp
            .setAutoCancel(false)
            .setSilent(true)
            .setLocalOnly(false)
            .setTimeoutAfter(0)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Add prayer phase color based on progress
        if (progress > 0) {
            val phaseColor = when {
                progress <= 20 -> Color.parseColor("#4169E1")    // Blue for Go to Mosque
                progress <= 60 -> Color.parseColor("#10B981")    // Green for Best Time
                else -> Color.parseColor("#FBBF24")              // Yellow for Make Time
            }
            builder.setColor(phaseColor)
            builder.setColorized(true)
            
            // Add Live Update ProgressStyle
            try {
                val progressStyle = createLiveUpdateProgressStyle(progress, detailedMessage)
                builder.setStyle(progressStyle)
                Log.d(TAG, "✨ Applied Live Update ProgressStyle with segments")
            } catch (e: Exception) {
                Log.w(TAG, "ProgressStyle not available, using basic progress: ${e.message}")
                builder.setProgress(100, progress, false)
            }
        }
        
        // Add detailed message if provided
        if (detailedMessage.isNotBlank()) {
            if (progress == 0) {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(detailedMessage))
            }
        }
        
        // Add activity icon as large icon (shows in top right corner)
        val activityIcon = prayerWeatherNotificationBitmap(
            context = this,
            summary = detailedMessage,
        ) ?: getActivityIconBitmap()
        if (activityIcon != null) {
            builder.setLargeIcon(activityIcon)
            Log.d(TAG, "🏃 Added activity icon: ${ActivityTracker.getCurrentActivity()}")
        }

        val notification = builder.build()
        Log.d(TAG, "🚀 Live Update notification created with setRequestPromotedOngoing(true)")
        return notification
    }

    /**
     * Get resource ID for current detected activity SMALL icon (monochrome, for AOD)
     * Returns appropriate monochrome icon for notification small icon
     */
    private fun getActivitySmallIconResId(): Int {
        val currentActivity = ActivityTracker.getCurrentActivity()
        return when {
            currentActivity.contains("Driving", ignoreCase = true) -> R.drawable.ic_notif_driving
            currentActivity.contains("Walking", ignoreCase = true) -> R.drawable.ic_notif_walking
            currentActivity.contains("Running", ignoreCase = true) -> R.drawable.ic_notif_running
            currentActivity.contains("Phone", ignoreCase = true) -> R.drawable.ic_notif_phone
            currentActivity.contains("Still", ignoreCase = true) -> R.drawable.ic_notif_still
            else -> R.drawable.ic_notif_still // Default to still
        }
    }

    /**
     * Get bitmap icon for current detected activity
     * Returns appropriate icon based on: Still, Walking, Running, Driving, On Phone
     */
    private fun getActivityIconBitmap(): Bitmap? {
        return try {
            val currentActivity = ActivityTracker.getCurrentActivity()
            val iconResId = when {
                currentActivity.contains("Driving", ignoreCase = true) -> R.drawable.ic_activity_driving
                currentActivity.contains("Walking", ignoreCase = true) -> R.drawable.ic_activity_walking
                currentActivity.contains("Running", ignoreCase = true) -> R.drawable.ic_activity_running
                currentActivity.contains("Phone", ignoreCase = true) -> R.drawable.ic_activity_phone
                currentActivity.contains("Still", ignoreCase = true) -> R.drawable.ic_activity_still
                else -> R.drawable.ic_activity_still // Default to still
            }

            // Convert vector drawable to bitmap
            val drawable = ResourcesCompat.getDrawable(resources, iconResId, null)
            drawable?.let { vectorToBitmap(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting activity icon: ${e.message}")
            null
        }
    }

    /**
     * Convert vector drawable to bitmap for notification large icon
     */
    private fun vectorToBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Create Live Update ProgressStyle (matching Google sample structure)
     */
    @RequiresApi(35)
    private fun createLiveUpdateProgressStyle(
        progress: Int,
        weatherSummary: String = "",
    ): NotificationCompat.ProgressStyle {
        // Colors matching Google sample format
        return NotificationCompat.ProgressStyle()
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(20).setColor(Color.parseColor("#4169E1")), // Blue
                    NotificationCompat.ProgressStyle.Segment(40).setColor(Color.parseColor("#10B981")), // Green
                    NotificationCompat.ProgressStyle.Segment(40).setColor(Color.parseColor("#FBBF24"))  // Yellow
                )
            )
            .setProgress(progress)
            .setProgressTrackerIcon(
                prayerWeatherNotificationTrackerIcon(this, weatherSummary)
            )
    }
    
    /**
     * Post Live Update notification directly (bypassing PrayerNotificationManager)
     */
    private fun postLiveUpdateNotification(
        title: String,
        content: String,
        detailedMessage: String = "",
        progress: Int = 0
    ) {
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                val notification = createLiveUpdateNotification(title, content, detailedMessage, progress)
                notificationManager.notify(LIVE_UPDATE_NOTIFICATION_ID, notification)
                Log.d(TAG, "📱 Posted Live Update notification (ID: $LIVE_UPDATE_NOTIFICATION_ID) - should have PROMOTED_ONGOING flag")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post Live Update notification: ${e.message}")
            }
        } else {
            Log.w(TAG, "Live Updates not supported on API level ${Build.VERSION.SDK_INT}")
        }
    }
    
    /**
     * MAIN FEATURE: Initialize background prayer notification updates
     * 
     * This is the core function that starts the prayer tracking system.
     * 
     * KEY FEATURES:
     * - Checks for Android 16+ Live Update support
     * - Starts the main prayer time update loop
     * - Handles service initialization errors gracefully
     * 
     * EDIT THIS SECTION TO:
     * - Change Live Update detection logic
     * - Modify initialization behavior
     * - Add new notification features
     */
    private suspend fun startRealPrayerTimeUpdates() {
        withContext(Dispatchers.IO) { // Ensure background thread
            try {
                Log.d(TAG, "Starting real prayer time updates on background thread")
                
                // Add delay to prevent ANR during app startup
                delay(3000) // Wait 3 seconds for app to be fully stable
                
                // Check service is still supposed to be running
                if (!isServiceRunning) {
                    Log.d(TAG, "Service stopped during initialization, aborting updates")
                    return@withContext
                }
                
                // Initialize separate Live Update notification (not tied to foreground service)
                withTimeoutOrNull(10000L) { // Increased from 5s to 10s
                    // Check if device supports Live Updates
                    if (PrayerNotificationManager.supportsLiveUpdates()) {
                        Log.d(TAG, "🚀 Device supports Live Updates - using separate notification")
                        Log.d(TAG, "Has promotable characteristics: ${PrayerNotificationManager.hasPromotableCharacteristics()}")
                        
                        // Don't post initial notification - Google system will handle it
                        Log.d(TAG, "🎯 Initial notification will be posted by Google Live Update system")
                    } else {
                        Log.w(TAG, "Device does not support Live Updates, using standard notifications")
                    }
                } ?: Log.w(TAG, "Live Update notification initialization timed out, continuing")
                
                // Start observing settings changes for automatic recalculation
                observeSettingsChanges()
                
                // Start simplified prayer time updates (no timeout - runs continuously)
                startPrayerTimeUpdateLoop() // Remove timeout wrapper to allow continuous updates
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting prayer time updates - service continues", e)
            }
        }
    }
    
    /**
     * Observe settings changes and trigger prayer time recalculation
     */
    private fun observeSettingsChanges() {
        serviceScope.launch {
            try {
                Log.d(TAG, "🔄 Starting to observe prayer settings changes...")
                prayerSettingsRepository.settingsFlow
                    .drop(1) // Skip initial value to avoid immediate recalculation
                    .distinctUntilChanged() // Only react to actual changes
                    .collect { newSettings ->
                        Log.i(TAG, "🚨 PRAYER SERVICE: SETTINGS FLOW UPDATE RECEIVED!")
                        Log.i(TAG, "   - Method: ${newSettings.calculationMethod.displayName}")
                        Log.i(TAG, "   - Auto-detected: ${newSettings.isMethodAutoDetected}")
                        Log.i(TAG, "   - Custom Fajr Angle: ${newSettings.customFajrAngle}")
                        Log.i(TAG, "   - Custom Isha Angle: ${newSettings.customIshaAngle}")
                        Log.i(TAG, "   - Time: ${java.time.LocalDateTime.now()}")
                        
                        // Trigger immediate prayer time update with new settings
                        withContext(Dispatchers.IO) {
                            try {
                                Log.i(TAG, "📤 Starting prayer time recalculation...")
                                val startTime = System.currentTimeMillis()
                                updatePrayerNotificationWithRealData()
                                val duration = System.currentTimeMillis() - startTime
                                Log.i(TAG, "✅ Prayer times recalculated successfully in ${duration}ms")
                                Log.i(TAG, "✅ Notification and UI should now show updated times")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Failed to recalculate prayer times after settings change", e)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error observing settings changes", e)
            }
        }
    }
    
    /**
     * CORE UPDATE LOOP: The heart of the prayer notification system
     * 
     * This function runs continuously to update prayer notifications.
     * 
     * SAFETY FEATURES:
     * - Auto-stops after 24 hours to prevent battery drain
     * - Limits to 60 updates maximum
     * - Smart timing: 1-minute updates for notifications, 6-minute for always-on display
     * 
     * EDIT THESE VALUES TO:
     * - Change service runtime limit (maxServiceTime)
     * - Adjust maximum update count (maxUpdates) 
     * - Modify update intervals in AnrPreventionConfig
     */
    private suspend fun startPrayerTimeUpdateLoop() {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "=== STARTING PRAYER NOTIFICATION UPDATE LOOP ===")
        Log.d(TAG, "Loop start time: ${java.time.LocalDateTime.now()}")
        
        // CONFIGURABLE LIMITS - Edit these values to change service behavior
        val maxServiceTime = 24 * 60 * 60 * 1000L // 24 hours max - prevents battery drain
        val maxUpdates = 1440 // Max 1440 updates (24 hours * 60 minutes) - allows continuous updates
        var updateCount = 0
        
        Log.d(TAG, "Service limits configured: maxTime=${maxServiceTime/1000}s, maxUpdates=$maxUpdates")
        
        try {
            while (isServiceRunning && updateCount < maxUpdates) {
                val iterationStartTime = System.currentTimeMillis()
                Log.d(TAG, "--- Update Loop Iteration #${updateCount + 1} ---")
                
                try {
                    // Check if service has been running too long
                    val currentRuntime = System.currentTimeMillis() - startTime
                    if (currentRuntime > maxServiceTime) {
                        Log.w(TAG, "Service exceeded max time limit (${maxServiceTime / 1000}s), stopping automatically")
                        Log.w(TAG, "Current runtime: ${currentRuntime/1000}s")
                        break
                    }
                    
                    Log.d(TAG, "Calling updatePrayerNotificationWithRealData()...")
                    updatePrayerNotificationWithRealData()
                    updateCount++

                    // Safety net for silent-during-prayer: if the window has ended but DND is
                    // still on (e.g. the one-shot restore alarm was dropped), turn it back off.
                    // This runs every ~minute in the always-on service, so DND is recovered even
                    // when the user never reopens the app.
                    runCatching {
                        com.starception.submission.prayer.silent.PrayerSilentModeController(applicationContext)
                            .recoverIfNeeded()
                    }.onFailure { Log.e(TAG, "Silent-mode recovery check failed", it) }
                    
                    val iterationDuration = System.currentTimeMillis() - iterationStartTime
                    Log.d(TAG, "✓ Prayer update #$updateCount/$maxUpdates completed in ${iterationDuration}ms")
                    
                    if (AnrPreventionConfig.LOG_SERVICE_LIFECYCLE) {
                        Log.d(TAG, "Service runtime: ${(System.currentTimeMillis() - startTime)/1000}s")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating prayer notification", e)
                    // Don't create fallback notifications - user wants only single AOD notification
                    Log.d(TAG, "⚠️ Error occurred but not creating fallback notification to avoid duplicates")
                }
                
                // SMART UPDATE STRATEGY - Edit this logic to change update timing
                // 
                // CURRENT BEHAVIOR:
                // - Every 1 minute: Update notification content (battery efficient)
                // - Every 6 minutes: Update always-on display (prevents color flashing)
                // 
                // EDIT THIS TO:
                // - Change update frequencies by modifying the modulo operation (updateCount % 6)
                // - Adjust intervals in AnrPreventionConfig class
                val updateInterval = if (updateCount % 6 == 0) {
                    // Every 6th update (6 minutes), update the always-on display content
                    AnrPreventionConfig.ALWAYS_ON_DISPLAY_UPDATE_INTERVAL_MS
                } else {
                    // Regular updates (1 minute) for notification content
                    AnrPreventionConfig.SERVICE_UPDATE_INTERVAL_MS
                }
                delay(updateInterval)
            }
            
            // Auto-restart service after max updates or time limit
            val runtimeSeconds = (System.currentTimeMillis() - startTime) / 1000
            Log.d(TAG, "Service restarting automatically - updates: $updateCount, runtime: ${runtimeSeconds}s")
            
            // Restart the service instead of stopping it
            isServiceRunning = false
            delay(2000) // Wait 2 seconds before restart
            isServiceRunning = true
            
            // Restart the update loop
            Log.d(TAG, "🔄 Restarting prayer notification update loop...")
            startPrayerTimeUpdateLoop()
            
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in prayer update loop", e)
        }
        Log.d(TAG, "Prayer time updates stopped")
    }
    
    /**
     * NOTIFICATION UPDATE ENGINE: Updates the notification with timeout protection
     */
    private suspend fun updatePrayerNotificationWithRealData() {
        val updateStartTime = System.currentTimeMillis()
        Log.d(TAG, "=== UPDATING PRAYER NOTIFICATION (ANR PROTECTED) ===")
        Log.d(TAG, "Update time: ${java.time.LocalTime.now()}")
        
        try {
            // Wrap entire update in timeout to prevent ANR
            withTimeoutOrNull(5000L) { // 5 second max for entire update
                // SAFETY CHECK - Quick initialization
                if (!PrayerNotificationManager.isInitialized()) {
                    Log.d(TAG, "Initializing PrayerNotificationManager...")
                    PrayerNotificationManager.initialize(applicationContext)
                }
                
                // Get prayer data with timeout
                Log.d(TAG, "Getting prayer data...")
                val prayerDataStartTime = System.currentTimeMillis()
                val prayerData = getCurrentPrayerData() // Already has internal timeouts
                val prayerDataDuration = System.currentTimeMillis() - prayerDataStartTime
                Log.d(TAG, "✓ Prayer data retrieved in ${prayerDataDuration}ms")
                
                if (prayerData != null) {
                    val (title, content, detailedMessage, prayerPhase, realProgress, prayerInfo) = prayerData
                    val (prayerName, prayerTime) = prayerInfo
                    
                    // Use the REAL progress percentage from prayer calculation, not the fake hour-based one
                    val progress = realProgress
                    val currentPhase = getCurrentPrayerPhase(progress)
                    
                    Log.d(TAG, "🎯 Using REAL prayer progress: ${progress}% (phase: $prayerPhase)")
                    
                    // ONLY use Google's proven Live Update system for Android 16+
                    if (Build.VERSION.SDK_INT >= 35) {
                        try {
                            // ProgressStyle only exposes two content lines on Samsung.
                            // Keep the visible payload focused on elapsed time + next
                            // prayer; the complete detail still feeds the weather
                            // tracker icon without appending weather/advice as text.
                            val nextPrayerDetail = detailedMessage
                                .lineSequence()
                                .firstOrNull { it.isNotBlank() }
                                .orEmpty()
                            // FIX: Build notification and use startForeground() to maintain foreground service priority
                            // Previously using postPrayerNotification() which called notify() directly,
                            // removing the FOREGROUND_SERVICE flag and causing OOM adj to spike to 915
                            val liveUpdateNotification = GoogleSampleNotificationManager.buildPrayerNotification(
                                title = title,
                                content = content,
                                detailedMessage = nextPrayerDetail,
                                progress = progress,
                                prayerPhase = prayerPhase,
                                prayerName = prayerName,
                                prayerTime = prayerTime,
                                nextPrayerCountdown = nextPrayerDetail,
                                weatherSummary = detailedMessage,
                            )

                            // Use startForeground() to update notification while maintaining foreground service priority
                            // This keeps OOM adj at ~200 instead of 915, preventing system from killing the service
                            startForegroundWithSafeTypes(liveUpdateNotification)
                            Log.d(TAG, "🧪 Updated foreground notification via startForeground() with phase: $prayerPhase, real progress: ${progress}%")

                            // Log notification update to file - now using startForeground() to maintain priority
                            FileLogger.logPrayerEvent(
                                event = "FOREGROUND_NOTIFICATION_UPDATED",
                                prayerName = prayerName,
                                actualTime = java.time.LocalTime.now().toString(),
                                details = mapOf(
                                    "phase" to prayerPhase,
                                    "progress" to progress,
                                    "title" to title,
                                    "content" to content,
                                    "prayerTime" to prayerTime,
                                    "method" to "startForeground"
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Google Live Update failed: ${e.message}")
                            // Don't create fallback notifications - better to have no notification than duplicates
                        }
                    } else {
                        Log.d(TAG, "⚠️ Android 16+ required for Live Update notifications")
                        // For older Android versions, don't create any notification
                        // The user specifically wants Android 16 Live Update functionality
                    }
                    
                    previousPrayerPhase = currentPhase
                    
                    val totalDuration = System.currentTimeMillis() - updateStartTime
                    Log.d(TAG, "✓ Notification updated successfully in ${totalDuration}ms")
                } else {
                    // Don't create quick fallback - user wants only single AOD notification
                    Log.d(TAG, "📱 Skipping quick fallback to avoid duplicate notifications")
                }
                
            } ?: run {
                // Timeout occurred - use emergency Live Update fallback (separate from foreground service)
                Log.w(TAG, "⚠️ Update timed out after 5s - using emergency Live Update fallback")
                // Don't create emergency fallback - user wants only single AOD notification
                Log.d(TAG, "🚨 Update timed out but not creating emergency notification to avoid duplicates")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in update: ${e.message}")
            // Don't create final fallback - user wants only single AOD notification
            Log.d(TAG, "💾 Service error but not creating final fallback to avoid duplicates")
        }
    }
    
    /**
     * Get current prayer data for notification with ANR prevention
     */
    private suspend fun getCurrentPrayerData(): Sextuple<String, String, String, String, Int, Pair<String, String>>? {
        return withContext(Dispatchers.IO) { // Ensure background thread
            try {
                Log.d(TAG, "=== GETTING PRAYER DATA (BACKGROUND THREAD) ===")
                
                // Quick settings check with very short timeout (using injected dependency)
                val settings = withTimeoutOrNull(1000L) { // 1 second max
                    prayerSettingsRepository.getSettings()
                }
                if (settings == null) {
                    Log.w(TAG, "Settings unavailable, using fallback")
                    return@withContext Sextuple("Prayer Time Tracker", "Loading...", "Initializing prayer data", "MAKE_TIME", 25, Pair("Current Prayer", LocalTime.now().toString()))
                }
                
                // ROBUST LOGGING: Check what settings we're actually using for calculation
                Log.i(TAG, "📋 SETTINGS BEING USED FOR PRAYER CALCULATION:")
                Log.i(TAG, "   - Method: ${settings.calculationMethod.displayName}")
                Log.i(TAG, "   - Custom Fajr: ${settings.customFajrAngle}")
                Log.i(TAG, "   - Custom Isha: ${settings.customIshaAngle}")
                Log.i(TAG, "   - Fajr Offset: ${settings.timeOffsets.fajr}")
                Log.i(TAG, "   - Dhuhr Offset: ${settings.timeOffsets.dhuhr}")
                Log.i(TAG, "   - Asr Offset: ${settings.timeOffsets.asr}")
                Log.i(TAG, "   - Auto-detected: ${settings.isMethodAutoDetected}")

                var location = settings.location
                if (location == null) {
                    Log.w(TAG, "⚠️ No location in settings, attempting to fetch...")

                    // Try to fetch location automatically if permission is granted
                    try {
                        val fetchedLocation: android.location.Location? = withTimeoutOrNull(2000L) {
                            locationService.getBestAvailableLocation().getOrNull()
                        }

                        if (fetchedLocation != null) {
                            Log.i(TAG, "✅ Location fetched successfully: ${fetchedLocation.latitude}, ${fetchedLocation.longitude}")

                            // Get timezone offset in hours
                            val timeZone = java.util.TimeZone.getDefault()
                            val offsetMs = timeZone.getOffset(System.currentTimeMillis())
                            val offsetHours = offsetMs / (1000.0 * 60 * 60)

                            // Convert to Location model
                            // Get proper location details with city name from geocoding
                            val detailedLocation = locationService.getLocationDetails(fetchedLocation)
                            val locationModel = detailedLocation.copy(
                                timeZoneOffset = offsetHours
                            )

                            // Save to settings for future use
                            prayerSettingsRepository.updateLocationSettings(useGps = true, location = locationModel)
                            location = locationModel

                            Log.i(TAG, "✅ Location saved to settings for future notifications")
                        } else {
                            Log.w(TAG, "❌ Failed to fetch location - permission may not be granted")
                            return@withContext Sextuple("Prayer Time Tracker", "Location needed", "Grant location permission to see prayer times", "MAKE_TIME", 30, Pair("Current Prayer", LocalTime.now().toString()))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error fetching location: ${e.message}", e)
                        return@withContext Sextuple("Prayer Time Tracker", "Location needed", "Grant location permission to see prayer times", "MAKE_TIME", 30, Pair("Current Prayer", LocalTime.now().toString()))
                    }
                }

                // Ensure location is not null before proceeding
                if (location == null) {
                    Log.e(TAG, "❌ Location is still null after fetch attempt")
                    return@withContext Sextuple("Prayer Time Tracker", "Location needed", "Grant location permission to see prayer times", "MAKE_TIME", 30, Pair("Current Prayer", LocalTime.now().toString()))
                }

                Log.d(TAG, "Location: ${location.getDisplayName()}")

                // Quick prayer calculation with aggressive timeout (using injected dependency)
                val today = LocalDate.now()
                val calculationStartTime = System.currentTimeMillis()
                val prayerTimes = withTimeoutOrNull(1500L) { // 1.5 second max
                    prayerTimeCalculatorService.calculatePrayerTimes(today, location, settings)
                }
                val calculationTime = System.currentTimeMillis() - calculationStartTime
                
                if (prayerTimes == null) {
                    Log.w(TAG, "Prayer calculation timed out after ${calculationTime}ms")
                    return@withContext Sextuple("Prayer Time Tracker", "Calculating...", "Prayer times being calculated", "MAKE_TIME", 40, Pair("Current Prayer", LocalTime.now().toString()))
                }
                
                Log.d(TAG, "✓ Prayer times calculated in ${calculationTime}ms")
                
                // Get offsets from settings to apply to base times
                val offsets = settings.timeOffsets
                Log.d(TAG, "⏰ Applying user offsets: Fajr=${offsets.fajr}, Dhuhr=${offsets.dhuhr}, Asr=${offsets.asr}, Maghrib=${offsets.maghrib}, Isha=${offsets.isha}")
                
                // Apply offsets to base prayer times (to match small tiles and smart prediction)
                fun applyOffsetToTime(baseTime: LocalTime, offsetMinutes: Int): LocalTime {
                    if (offsetMinutes == 0) return baseTime
                    val adjustedDateTime = LocalDateTime.of(LocalDate.now(), baseTime)
                        .plusMinutes(offsetMinutes.toLong())
                    return adjustedDateTime.toLocalTime()
                }
                
                fun adjustPrayerTime(prayer: PrayerTime): PrayerTime {
                    val offset = when (prayer.name.lowercase()) {
                        "fajr" -> offsets.fajr
                        "dhuhr" -> offsets.dhuhr
                        "asr" -> offsets.asr
                        "maghrib" -> offsets.maghrib
                        "isha" -> offsets.isha
                        else -> 0
                    }
                    val adjustedTime = applyOffsetToTime(prayer.time, offset)
                    return prayer.copy(time = adjustedTime)
                }
                
                // CRITICAL FIX: Use same logic as Smart Prediction
                // Apply offsets FIRST, then find current/next prayer from adjusted times
                val adjustedFajr = applyOffsetToTime(prayerTimes.fajr, offsets.fajr)
                val adjustedDhuhr = applyOffsetToTime(prayerTimes.dhuhr, offsets.dhuhr)
                val adjustedAsr = applyOffsetToTime(prayerTimes.asr, offsets.asr)
                val adjustedMaghrib = applyOffsetToTime(prayerTimes.maghrib, offsets.maghrib)
                val adjustedIsha = applyOffsetToTime(prayerTimes.isha, offsets.isha)

                Log.d(TAG, "📅 ADJUSTED Prayer times (base + offset):")
                Log.d(TAG, "   Fajr=$adjustedFajr, Dhuhr=$adjustedDhuhr, Asr=$adjustedAsr, Maghrib=$adjustedMaghrib, Isha=$adjustedIsha")

                // Build list of all adjusted prayers
                val allAdjustedPrayers = listOf(
                    PrayerTime("Fajr", adjustedFajr, isNext = false, isCurrently = false),
                    PrayerTime("Dhuhr", adjustedDhuhr, isNext = false, isCurrently = false),
                    PrayerTime("Asr", adjustedAsr, isNext = false, isCurrently = false),
                    PrayerTime("Maghrib", adjustedMaghrib, isNext = false, isCurrently = false),
                    PrayerTime("Isha", adjustedIsha, isNext = false, isCurrently = false)
                )

                val now = LocalTime.now()

                // Find current prayer (the most recent prayer that has passed TODAY)
                var rawCurrentPrayer = allAdjustedPrayers.findLast { prayer ->
                    now.isAfter(prayer.time)
                }

                Log.d(TAG, "🕌 Current prayer (last passed today): ${rawCurrentPrayer?.name ?: "None"}")

                // CROSS-MIDNIGHT FIX: If no prayer has passed today yet (we're before Fajr),
                // use yesterday's Isha as the current prayer
                if (rawCurrentPrayer == null && now.isBefore(adjustedFajr)) {
                    Log.d(TAG, "🌙 Cross-midnight scenario: Before Fajr, using yesterday's Isha")
                    rawCurrentPrayer = PrayerTime("Isha", adjustedIsha, isNext = false, isCurrently = true)
                }

                // Find next prayer (the next upcoming prayer)
                val nextPrayerToday = allAdjustedPrayers.find { prayer ->
                    now.isBefore(prayer.time)
                }

                val nextPrayer: PrayerTime?
                if (nextPrayerToday != null) {
                    // There's a prayer left today
                    nextPrayer = nextPrayerToday.copy(isNext = true)
                    Log.d(TAG, "📍 Next prayer TODAY: ${nextPrayer.name} at ${nextPrayer.time}")
                } else {
                    // All prayers passed, next is tomorrow's Fajr
                    nextPrayer = allAdjustedPrayers.first().copy(isNext = true)
                    Log.d(TAG, "📍 Next prayer TOMORROW: ${nextPrayer.name} at ${nextPrayer.time}")
                }

                // Use the detected prayers (already correct from logic above)
                val currentPrayer = rawCurrentPrayer?.copy(isCurrently = true)

                Log.d(TAG, "🔍 FINAL PRAYER DETECTION:")
                Log.d(TAG, "   Current Prayer: ${currentPrayer?.name ?: "None"} at ${currentPrayer?.time}")
                Log.d(TAG, "   Next Prayer: ${nextPrayer?.name ?: "None"} at ${nextPrayer?.time}")

                // Log prayer detection to file for debugging
                FileLogger.logPrayerEvent(
                    event = "PRAYER_DETECTION",
                    prayerName = currentPrayer?.name ?: "None",
                    actualTime = now.toString(),
                    details = mapOf(
                        "currentPrayer" to (currentPrayer?.name ?: "None"),
                        "currentPrayerTime" to (currentPrayer?.time?.toString() ?: "N/A"),
                        "nextPrayer" to (nextPrayer?.name ?: "None"),
                        "nextPrayerTime" to (nextPrayer?.time?.toString() ?: "N/A"),
                        "fajr" to adjustedFajr.toString(),
                        "dhuhr" to adjustedDhuhr.toString(),
                        "asr" to adjustedAsr.toString(),
                        "maghrib" to adjustedMaghrib.toString(),
                        "isha" to adjustedIsha.toString(),
                        "calculationMethod" to settings.calculationMethod.displayName
                    )
                )

                // Handle fallback case (should rarely happen with new logic)
                if (currentPrayer == null || nextPrayer == null) {
                    Log.w(TAG, "⚠️ Unexpected: Missing prayer data - using fallback")
                    val nextDisplayName = nextPrayer?.name?.let { getPrayerDisplayName(it, LocalDate.now()) } ?: "Unknown"
                    val title = "⏰ Next Prayer: $nextDisplayName"
                    val content = nextPrayer?.let { "Next prayer in ${formatTimeRemaining(it.time)}" } ?: "Prayer times calculated"
                    val detailedMessage = buildNextPrayerMessage(prayerTimes, nextPrayer)
                    return@withContext Sextuple(title, content, detailedMessage, "MAKE_TIME", 10, Pair(currentPrayer?.name ?: "Current Prayer", currentPrayer?.time?.toString() ?: LocalTime.now().toString()))
                }

                // Now calculate prayer progress (works for both same-day and cross-day scenarios)
                
                // Calculate prayer time progress
                val prayerProgress = calculatePrayerProgress(currentPrayer, nextPrayer)
                
                // Format notification content based on prayer progress.
                // On Fridays the midday (Dhuhr) prayer is Jumu'ah — show that name to the user.
                val currentDisplayName = getPrayerDisplayName(currentPrayer.name, LocalDate.now())
                val title = when (prayerProgress.phase) {
                    PrayerPhase.GO_TO_MOSQUE -> "Go to Mosque for $currentDisplayName"
                    PrayerPhase.BEST_TIME -> "Best Time to Pray $currentDisplayName"
                    PrayerPhase.MAKE_TIME -> "Make Time for $currentDisplayName"
                }
                val content = buildPrayerProgressContent(prayerProgress, currentPrayer)
                val prayerWeatherInsight = CurrentWeatherRepository.getPrayerInsight(
                    latitude = prayerTimes.location.latitude,
                    longitude = prayerTimes.location.longitude,
                    prayerName = currentDisplayName,
                    prayerDate = if (
                        currentPrayer.name.equals("Isha", ignoreCase = true) &&
                        now.isBefore(adjustedFajr)
                    ) {
                        LocalDate.now().minusDays(1)
                    } else {
                        LocalDate.now()
                    },
                    prayerTime = currentPrayer.time,
                    thresholds = PrayerWeatherThresholdStore.load(applicationContext),
                )
                val detailedMessage = buildDetailedPrayerProgressMessage(
                    prayerTimes = prayerTimes,
                    currentPrayer = currentPrayer,
                    nextPrayer = nextPrayer,
                    progress = prayerProgress,
                    weatherLine = prayerWeatherInsight?.let {
                        "${it.notificationLine}\n${it.advice}"
                    },
                )
                
                // Include prayer phase for accurate notification display
                val phaseString = when (prayerProgress.phase) {
                    PrayerPhase.GO_TO_MOSQUE -> "GO_TO_MOSQUE"
                    PrayerPhase.BEST_TIME -> "BEST_TIME"
                    PrayerPhase.MAKE_TIME -> "MAKE_TIME"
                }
                
                // Return tuple with phase and real progress information for GoogleSampleNotificationManager
                Sextuple(title, content, detailedMessage, phaseString, prayerProgress.progressPercentage.toInt(), Pair(currentPrayer?.name ?: "Current Prayer", currentPrayer?.time?.toString() ?: LocalTime.now().toString()))
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting prayer data: ${e.message}")
                Sextuple("Prayer Time Tracker", "Service active", "Prayer time updates running", "MAKE_TIME", 50, Pair("Current Prayer", LocalTime.now().toString()))
            }
        }
    }
    

    
    /**
     * PRAYER PROGRESS CALCULATOR: Determines how far through a prayer time we are
     * 
     * This calculates the progress percentage and determines which phase we're in.
     * 
     * PHASES EXPLAINED:
     * - GO_TO_MOSQUE (configurable per-prayer): Time to prepare and go to mosque
     * - BEST_TIME (after go-to-mosque to halfway): Optimal time for prayer
     * - MAKE_TIME (halfway to end): Ensure you make time for prayer
     *
     * Phase durations are configurable per-prayer in notification settings.
     */
    private fun calculatePrayerProgress(currentPrayer: PrayerTime, nextPrayer: PrayerTime?): PrayerProgress {
        val now = LocalTime.now()
        val prayerStart = currentPrayer.time

        // Get per-prayer go-to-mosque duration from settings (configurable by user)
        val goToMosqueDuration = prayerSettingsRepository.getNotificationPreferences()
            .getGoToMosqueDurationForPrayer(currentPrayer.name)

        Log.d(TAG, "=== CALCULATING PRAYER PROGRESS ===")
        Log.d(TAG, "Current time: $now")
        Log.d(TAG, "Current prayer: ${currentPrayer.name} at $prayerStart")
        Log.d(TAG, "Go-to-mosque duration: ${goToMosqueDuration}min (from settings)")
        Log.d(TAG, "Next prayer: ${nextPrayer?.name ?: "None"} at ${nextPrayer?.time ?: "N/A"}")

        // CROSS-MIDNIGHT FIX: Detect when we've crossed midnight (Isha → Fajr overnight period)
        // This happens when:
        // 1. Current prayer is Isha (last prayer of the day)
        // 2. Current time is BEFORE the prayer start time (meaning we crossed midnight)
        // 3. Next prayer is Fajr (or we're waiting for tomorrow's Fajr)
        val isCrossMidnightScenario = currentPrayer.name.equals("Isha", ignoreCase = true) &&
                                       now.isBefore(prayerStart) &&
                                       (nextPrayer == null || nextPrayer.name.equals("Fajr", ignoreCase = true))

        if (isCrossMidnightScenario) {
            Log.d(TAG, "🌙 CROSS-MIDNIGHT SCENARIO DETECTED!")
            Log.d(TAG, "   Current time ($now) is after midnight but Isha started before midnight ($prayerStart)")

            // Calculate elapsed time properly across midnight
            // Elapsed = time from yesterday's Isha to midnight + time from midnight to now
            val minutesFromIshaToMidnight = Duration.between(prayerStart, LocalTime.MAX).toMinutes() + 1
            val minutesFromMidnightToNow = Duration.between(LocalTime.MIN, now).toMinutes()
            val elapsedMinutes = minutesFromIshaToMidnight + minutesFromMidnightToNow

            // Calculate time until next prayer (Fajr)
            val remainingMinutes = if (nextPrayer != null && now.isBefore(nextPrayer.time)) {
                Duration.between(now, nextPrayer.time).toMinutes()
            } else {
                0L
            }

            // Total duration from yesterday's Isha to today's Fajr
            val totalDuration = if (nextPrayer != null) {
                minutesFromIshaToMidnight + Duration.between(LocalTime.MIN, nextPrayer.time).toMinutes()
            } else {
                elapsedMinutes // Use elapsed as total if no next prayer
            }

            Log.d(TAG, "   Elapsed since yesterday's Isha: ${elapsedMinutes}m")
            Log.d(TAG, "   Remaining until Fajr: ${remainingMinutes}m")
            Log.d(TAG, "   Total Isha→Fajr duration: ${totalDuration}m")

            // Determine phase based on elapsed time (cross-midnight is always MAKE_TIME since Isha passed hours ago)
            val progressPhase = when {
                elapsedMinutes < goToMosqueDuration -> PrayerPhase.GO_TO_MOSQUE
                elapsedMinutes < (totalDuration / 2) -> PrayerPhase.BEST_TIME
                else -> PrayerPhase.MAKE_TIME
            }

            // Calculate progress percentage using 3-segment logic
            val goToMosquePercent = 20f // First segment is always 0-20% of progress bar
            val progressPercentage = when (progressPhase) {
                PrayerPhase.GO_TO_MOSQUE -> {
                    val segmentProgress = (elapsedMinutes.toFloat() / goToMosqueDuration.toFloat()).coerceIn(0f, 1f)
                    (segmentProgress * goToMosquePercent).coerceIn(0f, goToMosquePercent)
                }
                PrayerPhase.BEST_TIME -> {
                    val halfway = totalDuration / 2
                    val segmentElapsed = (elapsedMinutes - goToMosqueDuration).coerceAtLeast(0)
                    val segmentDuration = (halfway - goToMosqueDuration).coerceAtLeast(1)
                    val segmentProgress = (segmentElapsed.toFloat() / segmentDuration.toFloat()).coerceIn(0f, 1f)
                    (goToMosquePercent + segmentProgress * 40f).coerceIn(goToMosquePercent, 60f)
                }
                PrayerPhase.MAKE_TIME -> {
                    val halfway = totalDuration / 2
                    val segmentElapsed = (elapsedMinutes - halfway).coerceAtLeast(0)
                    val segmentDuration = (totalDuration - halfway).coerceAtLeast(1)
                    val segmentProgress = (segmentElapsed.toFloat() / segmentDuration.toFloat()).coerceIn(0f, 1f)
                    (60f + segmentProgress * 40f).coerceIn(60f, 100f)
                }
            }

            Log.d(TAG, "   Phase: $progressPhase, Progress: ${progressPercentage.toInt()}%")

            return PrayerProgress(
                elapsedMinutes = elapsedMinutes,
                remainingMinutes = remainingMinutes.coerceAtLeast(0),
                totalDuration = totalDuration,
                progressPercentage = progressPercentage,
                phase = progressPhase
            )
        }

        // Check if the current prayer time has already passed
        val prayerEndThreshold = prayerStart.plusHours(2)
        Log.d(TAG, "Prayer time threshold (end): $prayerEndThreshold")
        Log.d(TAG, "Has prayer passed 2-hour threshold? ${now.isAfter(prayerEndThreshold)}")
        
        if (now.isAfter(prayerEndThreshold)) {
            Log.d(TAG, "Prayer time has passed 2-hour threshold, calculating progress towards next prayer")
            // Prayer time has passed, calculate progress towards next prayer
            if (nextPrayer != null) {
                
                // CROSS-DAY CALCULATION FIX: Handle Isha → Fajr overnight period
                val timeUntilNext: Duration
                val totalTime: Duration
                val elapsedTime: Duration
                
                if (nextPrayer.time.isBefore(now)) {
                    // Next prayer is tomorrow (cross-day case: Isha → Fajr)
                    Log.d(TAG, "🌙 CROSS-DAY DETECTED: Current prayer is today's ${currentPrayer.name}, next prayer is tomorrow's ${nextPrayer.name}")
                    
                    // Time until tomorrow's Fajr = time until midnight + time from midnight to Fajr
                    timeUntilNext = Duration.between(now, LocalTime.MAX) + Duration.between(LocalTime.MIN, nextPrayer.time)
                    
                    // Total time from today's Isha to tomorrow's Fajr = time until midnight + time from midnight to Fajr
                    totalTime = Duration.between(prayerStart, LocalTime.MAX) + Duration.between(LocalTime.MIN, nextPrayer.time)
                    
                    // Elapsed time since today's Isha
                    elapsedTime = Duration.between(prayerStart, now)
                    
                    Log.d(TAG, "⏰ Cross-day calculation:")
                    Log.d(TAG, "   Time until tomorrow's ${nextPrayer.name}: ${timeUntilNext.toMinutes()} minutes")
                    Log.d(TAG, "   Total Isha→Fajr duration: ${totalTime.toMinutes()} minutes") 
                    Log.d(TAG, "   Elapsed since today's ${currentPrayer.name}: ${elapsedTime.toMinutes()} minutes")
                    
                } else {
                    // Next prayer is today (normal case)
                    timeUntilNext = Duration.between(now, nextPrayer.time)
                    totalTime = Duration.between(prayerStart, nextPrayer.time)
                    elapsedTime = Duration.between(prayerStart, now)
                    
                    Log.d(TAG, "📅 Same-day calculation:")
                    Log.d(TAG, "   Time until next prayer: ${timeUntilNext.toMinutes()} minutes")
                    Log.d(TAG, "   Total time between prayers: ${totalTime.toMinutes()} minutes")
                    Log.d(TAG, "   Elapsed time since current prayer: ${elapsedTime.toMinutes()} minutes")
                }
                
                if (totalTime.toMinutes() > 0) {
                    val progressPercentage = (elapsedTime.toMinutes().toFloat() / totalTime.toMinutes().toFloat() * 100f).coerceIn(0f, 100f)
                    Log.d(TAG, "🎯 Calculated progress: ${progressPercentage}% (MAKE_TIME phase)")
                    
                    val prayerProgress = PrayerProgress(
                        elapsedMinutes = elapsedTime.toMinutes(),
                        remainingMinutes = timeUntilNext.toMinutes(),
                        totalDuration = totalTime.toMinutes(),
                        progressPercentage = progressPercentage,
                        phase = PrayerPhase.MAKE_TIME // Since prayer time has passed
                    )
                    Log.d(TAG, "✅ Returning cross-day prayer progress: $prayerProgress")
                    return prayerProgress
                } else {
                    Log.w(TAG, "⚠️ Invalid total time calculation: ${totalTime.toMinutes()} minutes")
                }
            }
            
            // Fallback: prayer time has passed, no progress to show
            val elapsedMinutes = Duration.between(prayerStart, now).toMinutes()
            
            // SAFETY CHECK: Ensure elapsed time is never negative
            val safeElapsedMinutes = if (elapsedMinutes < 0) {
                Log.e(TAG, "🚨 NEGATIVE ELAPSED TIME IN FALLBACK!")
                Log.e(TAG, "   Prayer start: $prayerStart")
                Log.e(TAG, "   Current time: $now")
                Log.e(TAG, "   Elapsed calculation: Duration.between($prayerStart, $now) = ${elapsedMinutes}m")
                Log.w(TAG, "   Using safe elapsed time: 0m instead of ${elapsedMinutes}m")
                0L
            } else {
                elapsedMinutes
            }
            
            return PrayerProgress(
                elapsedMinutes = safeElapsedMinutes,
                remainingMinutes = 0,
                totalDuration = 0,
                progressPercentage = 0f,
                phase = PrayerPhase.MAKE_TIME
            )
        }
        
        // Prayer time is still active, calculate normal progress
        val prayerEnd = nextPrayer?.time ?: prayerStart.plusHours(1) // Default 1 hour if no next prayer

        val rawElapsedMinutes = Duration.between(prayerStart, now).toMinutes()

        // SAFETY CHECK: Ensure elapsed time is never negative (for active prayer period)
        val elapsedMinutes = if (rawElapsedMinutes < 0) {
            Log.e(TAG, "🚨 NEGATIVE ELAPSED TIME IN ACTIVE PRAYER!")
            Log.e(TAG, "   Prayer start: $prayerStart")
            Log.e(TAG, "   Current time: $now")
            Log.e(TAG, "   Elapsed calculation: Duration.between($prayerStart, $now) = ${rawElapsedMinutes}m")
            Log.w(TAG, "   Using safe elapsed time: 0m instead of ${rawElapsedMinutes}m")
            0L
        } else {
            rawElapsedMinutes
        }

        // CROSS-DAY FIX: Handle when next prayer is tomorrow (e.g., Isha → Fajr overnight)
        // LocalTime.between() returns negative when end time is before start time (next day)
        val isCrossDay = prayerEnd.isBefore(prayerStart)

        val totalDuration: Long
        val remainingMinutes: Long

        if (isCrossDay) {
            // Next prayer is tomorrow - calculate properly across midnight
            Log.d(TAG, "🌙 CROSS-DAY ACTIVE PRAYER: ${nextPrayer?.name ?: "Unknown"} is tomorrow")

            // Total time = time until midnight + time from midnight to next prayer
            totalDuration = Duration.between(prayerStart, LocalTime.MAX).toMinutes() +
                           Duration.between(LocalTime.MIN, prayerEnd).toMinutes() + 1

            // Remaining time = time until midnight + time from midnight to next prayer
            remainingMinutes = Duration.between(now, LocalTime.MAX).toMinutes() +
                              Duration.between(LocalTime.MIN, prayerEnd).toMinutes() + 1

            Log.d(TAG, "   Total duration (cross-day): ${totalDuration}m")
            Log.d(TAG, "   Remaining (cross-day): ${remainingMinutes}m")
        } else {
            // Same day calculation
            totalDuration = Duration.between(prayerStart, prayerEnd).toMinutes()
            remainingMinutes = Duration.between(now, prayerEnd).toMinutes()
        }
        
        // PHASE DETERMINATION - Uses per-prayer go-to-mosque duration from settings
        val progressPhase = when {
            elapsedMinutes < goToMosqueDuration -> PrayerPhase.GO_TO_MOSQUE    // First N minutes: Go to mosque
            elapsedMinutes < (totalDuration / 2) -> PrayerPhase.BEST_TIME    // N min to halfway: Best time
            else -> PrayerPhase.MAKE_TIME    // Halfway to end: Make time
        }

        // 3-SEGMENT PROGRESS CALCULATION to match notification segments
        // Segment 1 (Go to Mosque): 0-20% = First goToMosqueDuration minutes
        // Segment 2 (Best Time): 20-60% = goToMosqueDuration to halfway point
        // Segment 3 (Make Time): 60-100% = Halfway to end
        val goToMosquePercent = 20f // First segment is always 0-20% of progress bar
        val progressPercentage = when (progressPhase) {
            PrayerPhase.GO_TO_MOSQUE -> {
                // First goToMosqueDuration minutes maps to 0-20% progress
                val segmentProgress = (elapsedMinutes.toFloat() / goToMosqueDuration.toFloat()).coerceIn(0f, 1f)
                (segmentProgress * goToMosquePercent).coerceIn(0f, goToMosquePercent)
            }
            PrayerPhase.BEST_TIME -> {
                // From goToMosqueDuration to halfway point maps to 20-60% progress
                val halfway = totalDuration / 2
                val segmentElapsed = (elapsedMinutes - goToMosqueDuration).coerceAtLeast(0)
                val segmentDuration = (halfway - goToMosqueDuration).coerceAtLeast(1) // Avoid division by zero
                val segmentProgress = (segmentElapsed.toFloat() / segmentDuration.toFloat()).coerceIn(0f, 1f)
                (goToMosquePercent + segmentProgress * 40f).coerceIn(goToMosquePercent, 60f)
            }
            PrayerPhase.MAKE_TIME -> {
                // From halfway to end maps to 60-100% progress
                val halfway = totalDuration / 2
                val segmentElapsed = (elapsedMinutes - halfway).coerceAtLeast(0)
                val segmentDuration = (totalDuration - halfway).coerceAtLeast(1) // Avoid division by zero
                val segmentProgress = (segmentElapsed.toFloat() / segmentDuration.toFloat()).coerceIn(0f, 1f)
                (60f + segmentProgress * 40f).coerceIn(60f, 100f)
            }
        }
        
        Log.d(TAG, "🎯 3-SEGMENT PROGRESS CALCULATION:")
        Log.d(TAG, "   Elapsed: ${elapsedMinutes}m, Total: ${totalDuration}m, Phase: $progressPhase")
        Log.d(TAG, "   Progress: ${progressPercentage.toInt()}% (${progressPhase.name})")
        
        return PrayerProgress(
            elapsedMinutes = elapsedMinutes,
            remainingMinutes = remainingMinutes,
            totalDuration = totalDuration,
            progressPercentage = progressPercentage,
            phase = progressPhase
        )
    }
    
    /**
     * Build prayer progress content for notification
     */
    private fun buildPrayerProgressContent(progress: PrayerProgress, currentPrayer: PrayerTime): String {
        val elapsedText = formatElapsedTime(progress.elapsedMinutes)

        // Show elapsed time since prayer started - no redundant guidance text.
        // On Fridays the midday (Dhuhr) prayer is Jumu'ah — show that name to the user.
        val displayName = getPrayerDisplayName(currentPrayer.name, LocalDate.now())
        return "$elapsedText since $displayName"
    }
    
    /**
     * Build detailed prayer progress message
     */
    private fun buildDetailedPrayerProgressMessage(
        prayerTimes: DayPrayerTimes,
        currentPrayer: PrayerTime,
        nextPrayer: PrayerTime?,
        progress: PrayerProgress,
        weatherLine: String? = null,
    ): String {
        return buildString {
            // Show next prayer countdown (Jumu'ah instead of Dhuhr on Fridays)
            if (nextPrayer != null) {
                val timeRemaining = formatTimeRemaining(nextPrayer.time)
                val nextDisplayName = getPrayerDisplayName(nextPrayer.name, LocalDate.now())
                appendLine("Next • $nextDisplayName in $timeRemaining")
            }
            weatherLine?.takeIf { it.isNotBlank() }?.let(::appendLine)
        }
    }

    /**
     * Build next prayer message when no current prayer
     */
    private fun buildNextPrayerMessage(prayerTimes: DayPrayerTimes, nextPrayer: PrayerTime?): String {
        return buildString {
            if (nextPrayer != null) {
                val timeRemaining = formatTimeRemaining(nextPrayer.time)
                val nextDisplayName = getPrayerDisplayName(nextPrayer.name, LocalDate.now())
                appendLine("Next • $nextDisplayName in $timeRemaining")
            } else {
                appendLine("No upcoming prayers")
            }
        }
    }
    
    /**
     * Data classes for prayer progress tracking
     */
    private data class PrayerProgress(
        val elapsedMinutes: Long,
        val remainingMinutes: Long,
        val totalDuration: Long,
        val progressPercentage: Float,
        val phase: PrayerPhase
    )
    
    /**
     * Data class to hold notification content with prayer phase information
     */
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
    private data class Sextuple<A, B, C, D, E, F>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E, val sixth: F)
    
    /**
     * PRAYER PHASES: The three stages of prayer time
     *
     * These phases determine the notification message and color.
     * Phase durations are configurable per-prayer in notification settings.
     */
    private enum class PrayerPhase {
        GO_TO_MOSQUE,    // 0-N minutes (configurable): Go to mosque (Blue color in progress bar)
        BEST_TIME,       // N minutes to halfway: Best time for prayer (Green color)
        MAKE_TIME        // Halfway+: Make time for prayer (Yellow color)
    }
    
    /**
     * Format time remaining until prayer
     */
    private fun formatTimeRemaining(prayerTime: LocalTime): String {
        val now = LocalTime.now()
        Log.d(TAG, "🕐 formatTimeRemaining called:")
        Log.d(TAG, "   Current time: $now")
        Log.d(TAG, "   Prayer time: $prayerTime")
        Log.d(TAG, "   Is prayer after now? ${prayerTime.isAfter(now)}")

        val duration = if (prayerTime.isAfter(now)) {
            // Prayer is today
            val result = Duration.between(now, prayerTime)
            Log.d(TAG, "   ✅ Prayer is TODAY - duration: ${result.toMinutes()} minutes")
            result
        } else {
            // Prayer is tomorrow - FIXED CALCULATION
            val minutesUntilMidnight = Duration.between(now, LocalTime.MAX).toMinutes()
            val minutesFromMidnightToPrayer = Duration.between(LocalTime.MIN, prayerTime).toMinutes()
            val totalMinutes = minutesUntilMidnight + minutesFromMidnightToPrayer + 1
            Log.d(TAG, "   ⏰ Prayer is TOMORROW:")
            Log.d(TAG, "      Minutes until midnight: $minutesUntilMidnight")
            Log.d(TAG, "      Minutes from midnight to prayer: $minutesFromMidnightToPrayer")
            Log.d(TAG, "      Total minutes: $totalMinutes")
            Duration.ofMinutes(totalMinutes)
        }

        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val result = when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "Now"
        }

        Log.d(TAG, "   📊 Formatted result: $result")
        return result
    }
    
    /**
     * Format elapsed time in a cleaner format
     */
    private fun formatElapsedTime(elapsedMinutes: Long): String {
        return when {
            elapsedMinutes == 0L -> "Just started"
            elapsedMinutes == 1L -> "1 minute"
            elapsedMinutes < 60 -> "${elapsedMinutes} minutes"
            else -> {
                val hours = elapsedMinutes / 60
                val minutes = elapsedMinutes % 60
                when {
                    minutes == 0L -> "${hours}h"
                    else -> "${hours}h ${minutes}m"
                }
            }
        }
    }

    /**
     * Get current prayer phase based on progress
     */
    private fun getCurrentPrayerPhase(progress: Int): String {
        return when {
            progress <= 20 -> "GO_TO_MOSQUE"
            progress <= 60 -> "BEST_TIME_TO_PRAY"
            else -> "MAKE_TIME_FOR_PRAYER"
        }
    }
    
    /**
     * Extract real progress from prayer data - no longer needed since we get it directly
     */
    private fun calculateNotificationProgress(prayerData: Sextuple<String, String, String, String, Int, Pair<String, String>>): Int {
        // Return the real progress percentage that was calculated by the prayer system
        return prayerData.fifth
    }
    
    /**
     * Initialize Activity Recognition (ASYNC VERSION - prevents ANR)
     *
     * IMPORTANT: This function should be called from a coroutine on Dispatchers.IO
     * to prevent ANR (Application Not Responding) issues.
     *
     * Google Play Services Activity Recognition provides reliable background activity
     * detection that complements our sensor-based detection. It works even when sensors
     * are throttled because it runs as part of Google Play Services.
     */
    private suspend fun initializeActivityRecognitionAsync() {
        try {
            Log.d(TAG, "🚀 Initializing Google Play Services Activity Recognition (async)...")

            // Initialize client on IO thread (safe)
            activityRecognitionClient = ActivityRecognition.getClient(this)
            Log.d(TAG, "✓ Activity recognition client created")

            // Initialize tone generator lazily on main thread when needed
            // Don't initialize here to avoid blocking
            withContext(Dispatchers.Main) {
                try {
                    if (toneGenerator == null) {
                        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to initialize ToneGenerator: ${e.message}")
                }
            }

            // Use an explicit manifest receiver so Play services can deliver a
            // transition even when no Activity or service instance is alive.
            val intent = Intent(this, DrivingActivityTransitionReceiver::class.java)
            activityTransitionPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Play services fills ActivityTransitionResult into this intent.
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    },
            )
            Log.d(TAG, "✓ Activity transition PendingIntent created")

            // Start activity recognition with timeout protection
            startActivityRecognitionWithTimeout()

            Log.d(TAG, "✅ Google Play Services Activity Recognition initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize activity recognition: ${e.message}", e)
        }
    }

    /**
     * Legacy synchronous initialization (kept for compatibility, but prefer async version)
     */
    @Deprecated("Use initializeActivityRecognitionAsync() instead to prevent ANR")
    private fun initializeActivityRecognition() {
        try {
            activityRecognitionClient = ActivityRecognition.getClient(this)

            // Initialize tone generator for beep notifications
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)

            // Create pending intent for activity transitions
            val intent = Intent(this, DrivingActivityTransitionReceiver::class.java)
            activityTransitionPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    },
            )

            // Start activity recognition
            startActivityRecognition()

            Log.d(TAG, "✓ Activity recognition initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize activity recognition: ${e.message}")
        }
    }

    /**
     * Start activity recognition with timeout protection
     */
    private suspend fun startActivityRecognitionWithTimeout() {
        try {
            withTimeout(5000L) { // 5 second timeout to prevent ANR
                startActivityRecognition()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "⚠️ Activity recognition request timed out (5s) - will retry later")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting activity recognition: ${e.message}")
        }
    }
    
    /**
     * Start activity recognition updates
     */
    private fun startActivityRecognition() {
        try {
            // Define activity transitions we want to detect
            // Using only commonly supported activities to avoid compatibility issues
            val transitions = mutableListOf<ActivityTransition>()
            
            // Only use the most commonly supported activity types across all devices
            val activities = listOf(
                DetectedActivity.STILL,        // Stationary (widely supported)
                DetectedActivity.WALKING,      // Walking (widely supported)
                DetectedActivity.RUNNING,      // Running (widely supported)
                DetectedActivity.IN_VEHICLE    // In vehicle (widely supported)
                // Removed: ON_BICYCLE, ON_FOOT, TILTING (not universally supported)
            )
            
            activities.forEach { activity ->
                transitions.add(
                    ActivityTransition.Builder()
                        .setActivityType(activity)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build()
                )
                transitions.add(
                    ActivityTransition.Builder()
                        .setActivityType(activity)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                        .build()
                )
            }
            
            val request = ActivityTransitionRequest(transitions)
            
            activityTransitionPendingIntent?.let { pendingIntent ->
                activityRecognitionClient.requestActivityTransitionUpdates(request, pendingIntent)
                    .addOnSuccessListener {
                        Log.i(TAG, "✅ Activity transition updates requested successfully")
                        Log.i(TAG, "📊 Tracking activities: ${activities.map { getActivityString(it) }.joinToString(", ")}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Failed to request activity transition updates: ${e.message}")
                        Log.e(TAG, "🔍 This may indicate permission issues or unsupported device configuration")
                    }
            } ?: run {
                Log.e(TAG, "❌ Cannot start activity recognition: PendingIntent is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting activity recognition: ${e.message}", e)
        }
    }
    
    /**
     * Get current activity for display
     */
    fun getCurrentActivity(): String {
        return currentActivity
    }
    
    /**
     * Play beep sound when activity changes
     */
    private fun playActivityChangeBeep() {
        try {
            // Play a short beep tone
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            
            // Also vibrate briefly if available
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(100)
                }
            }
            
            Log.d(TAG, "🔊 Activity change beep played")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play activity change beep: ${e.message}")
        }
    }
    
    /**
     * Show notification when activity changes
     */
    private fun showActivityChangeNotification(activity: String) {
        try {
            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Activity Changed")
                .setContentText("Now: $activity")
                .setSmallIcon(R.drawable.ic_prayer)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setSilent(true) // Silent since we play beep manually
                .build()
            
            notificationManager.notify(1003, notification) // Different ID from other notifications
            
            Log.d(TAG, "📱 Activity change notification shown: $activity")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show activity change notification: ${e.message}")
        }
    }
    
    /**
     * Convert activity type to readable string
     */
    private fun getActivityString(activityType: Int): String {
        return when (activityType) {
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.ON_BICYCLE -> "CYCLING"
            DetectedActivity.IN_VEHICLE -> "DRIVING"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.UNKNOWN -> "UNKNOWN"
            else -> "OTHER"
        }
    }
    
    /**
     * Convert transition type to readable string
     */
    private fun getTransitionString(transitionType: Int): String {
        return when (transitionType) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER"
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * Stop activity recognition updates
     */
    private fun stopActivityRecognition() {
        try {
            activityTransitionPendingIntent?.let { pendingIntent ->
                activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent)
                    .addOnSuccessListener {
                        Log.d(TAG, "✓ Activity transition updates removed successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to remove activity transition updates: ${e.message}")
                    }
            }
            
            toneGenerator?.release()
            toneGenerator = null
            
            Log.d(TAG, "✓ Activity recognition stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping activity recognition: ${e.message}")
        }
    }

    /**
     * Register timezone change receiver for dynamic prayer time updates
     * This is registered dynamically (not in manifest) for Android 12+ compatibility
     */
    private fun registerTimezoneReceiver() {
        try {
            timezoneReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_TIMEZONE_CHANGED -> {
                            val newTimezone = java.util.TimeZone.getDefault()
                            Log.d(TAG, "🌍 Timezone changed to: ${newTimezone.id} (${newTimezone.displayName})")
                            handleTimezoneChange()
                        }
                        Intent.ACTION_TIME_CHANGED -> {
                            Log.d(TAG, "⏰ System time changed - recalculating prayer times")
                            handleTimezoneChange()
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(timezoneReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(timezoneReceiver, filter)
            }

            Log.d(TAG, "✓ Timezone change receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to register timezone receiver: ${e.message}")
        }
    }

    /**
     * Handle timezone or time change - reload settings and recalculate prayer times
     */
    private fun handleTimezoneChange() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val newTimezone = java.util.TimeZone.getDefault()
                Log.d(TAG, "🔄 Handling timezone/time change to: ${newTimezone.id}")

                // Step 1: Force re-initialize settings based on new timezone/country
                // This will auto-detect the appropriate calculation method for the new timezone
                Log.d(TAG, "📍 Force re-initializing prayer settings for new timezone...")
                prayerSettingsRepository.forceReinitializeForTimezoneChange(newTimezone.id)

                // Step 2: Force refresh and update notification with new prayer times
                Log.d(TAG, "🕌 Recalculating prayer times with updated settings...")
                updatePrayerNotificationWithRealData()

                Log.d(TAG, "✅ Prayer times and settings updated for timezone: ${newTimezone.id}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to handle timezone change: ${e.message}", e)
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Prayer notification service destroy started")
        
        try {
            // Reset flags FIRST to stop all loops immediately
            isServiceRunning = false
            isInitializing = false
            lastForegroundNotification = null
            
            // Cancel all coroutines immediately
            serviceScope.cancel()
            
            // Stop foreground service and remove notification
            try {
                stopForeground(true)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping foreground", e)
            }
            
            // Clean up notifications
            try {
                if (PrayerNotificationManager.isInitialized()) {
                    PrayerNotificationManager.cancelPrayerNotification()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up notifications", e)
            }
            
            // Stop activity recognition
            try {
                stopActivityRecognition()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping activity recognition", e)
            }

            // Unregister timezone receiver
            try {
                timezoneReceiver?.let {
                    unregisterReceiver(it)
                    timezoneReceiver = null
                    Log.d(TAG, "✓ Timezone receiver unregistered")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering timezone receiver", e)
            }

            // Stop ActivityTracker sensor detection and clear callback
            try {
                ActivityTracker.setActivityChangeCallback(null)  // Clear callback first
                ActivityTracker.setSensorHandler(null)  // Clear sensor handler
                // The service is START_STICKY and may be recreated immediately. Do not
                // invalidate a wake-up alarm that was already scheduled for an active trip.
                ActivityTracker.stopDetection(preserveDrivingSession = true)
                Log.d(TAG, "✓ ActivityTracker detection stopped and callback cleared")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping ActivityTracker", e)
            }

            // Clean up HandlerThread for sensor callbacks
            try {
                sensorHandlerThread?.quitSafely()
                sensorHandlerThread = null
                sensorHandler = null
                Log.d(TAG, "✓ Sensor HandlerThread cleaned up")
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up sensor HandlerThread", e)
            }

            Log.d(TAG, "Modern service cleanup completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
        } finally {
            super.onDestroy()
            Log.d(TAG, "Prayer notification service destroyed")
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called on Android 14+ when the system is about to stop this foreground
     * service due to the type timeout (dataSync FGS is capped at ~6h cumulative
     * runtime per UI session). If we don't stop ourselves cleanly, the system
     * throws ForegroundServiceDidNotStopInTimeException and Crashlytics records
     * it as an app crash — which is exactly what's been showing up.
     *
     * Stop quietly here; the next prayer alarm / boot receiver / app open will
     * bring the service back up with a fresh runtime budget.
     */
    override fun onTimeout(startId: Int) {
        Log.w(TAG, "⏰ Foreground service timeout reached — stopping cleanly to avoid crash")
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground on timeout", e)
        }
        stopSelf()
    }

    /**
     * Called when the app is removed from recent tasks (user swipes it away)
     * This ensures the service restarts itself to maintain prayer notifications
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "⚠️ App removed from recent tasks - scheduling service restart")
        FileLogger.w(TAG, "App removed from recent tasks - scheduling service restart")

        try {
            // Schedule service restart using AlarmManager for reliability
            val restartServiceIntent = Intent(applicationContext, PrayerNotificationService::class.java).apply {
                setPackage(packageName)
            }

            val restartServicePendingIntent = PendingIntent.getForegroundService(
                applicationContext,
                1,
                restartServiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val triggerTime = android.os.SystemClock.elapsedRealtime() + 1000

            // Use setExactAndAllowWhileIdle to wake device from Doze mode
            // This is critical for prayer notifications to work reliably
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,  // WAKEUP to wake device
                    triggerTime,
                    restartServicePendingIntent
                )
                Log.i(TAG, "✅ Service restart scheduled with setExactAndAllowWhileIdle (Doze-safe)")
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    restartServicePendingIntent
                )
                Log.i(TAG, "✅ Service restart scheduled with setExact")
            }

            FileLogger.i(TAG, "Service restart scheduled via AlarmManager (WAKEUP, Doze-safe)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule service restart: ${e.message}")
            FileLogger.e(TAG, "Failed to schedule service restart: ${e.message}")
        }

        super.onTaskRemoved(rootIntent)
    }
}
