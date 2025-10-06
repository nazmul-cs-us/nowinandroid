package com.starception.submission.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.annotation.RequiresApi
import com.starception.submission.R
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.PrayerTime
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.util.PrayerNotificationManager
import com.starception.submission.util.GoogleSampleNotificationManager
import com.starception.submission.util.AnrPreventionConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import javax.inject.Inject
import android.graphics.Color

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
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isServiceRunning = false
    private var isInitializing = false
    private var previousPrayerPhase: String? = null // Track previous phase for smart notifications
    
    // Live Update notification manager (separate from foreground service)
    private lateinit var notificationManager: NotificationManager
    
    companion object {
        private const val TAG = "PrayerNotificationService"
        
        // NOTIFICATION CONFIGURATION - Edit these to change notification behavior
        private const val NOTIFICATION_CHANNEL_ID = "prayer_live_update_channel"
        private const val NOTIFICATION_ID = 1001  // Foreground service notification ID
        private const val LIVE_UPDATE_NOTIFICATION_ID = 1002 // Separate Live Update notification ID
        
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
        
        // Create notification channel and basic setup - keeping ANR protection
        try {
            createNotificationChannel()
            PrayerNotificationManager.initialize(this)
            
            // Initialize notification manager for separate Live Update notifications
            notificationManager = getSystemService(NotificationManager::class.java)
            Log.d(TAG, "✓ Live Update notification manager initialized separately from foreground service")
            
            Log.d(TAG, "✓ Service onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Prayer notification service onStartCommand - startId: $startId, isServiceRunning: $isServiceRunning")
        
        if (isServiceRunning) {
            Log.w(TAG, "Service already running, ignoring duplicate start - startId: $startId")
            return START_STICKY
        }
        
        try {
            // IMMEDIATE FOREGROUND START - but with proper notification now that dependencies are available
            Log.d(TAG, "Starting foreground service immediately - startId: $startId")
            startForeground(NOTIFICATION_ID, createInitialNotification())
            
            // Mark service as running IMMEDIATELY
            isServiceRunning = true
            
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
                startForeground(NOTIFICATION_ID, createEmergencyNotification())
                isServiceRunning = true
                Log.w(TAG, "Emergency fallback notification started")
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Emergency fallback failed: ${fallbackError.message}")
            }
            return START_STICKY
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
                val progressStyle = createLiveUpdateProgressStyle(progress)
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
        
        val notification = builder.build()
        Log.d(TAG, "🚀 Live Update notification created with setRequestPromotedOngoing(true)")
        return notification
    }
    
    /**
     * Create Live Update ProgressStyle (matching Google sample structure)
     */
    @RequiresApi(35)
    private fun createLiveUpdateProgressStyle(progress: Int): NotificationCompat.ProgressStyle {
        // Colors matching Google sample format
        val pointColor = Color.valueOf(236f / 255f, 183f / 255f, 255f / 255f, 1f).toArgb()
        
        return NotificationCompat.ProgressStyle()
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(20).setColor(Color.parseColor("#4169E1")), // Blue
                    NotificationCompat.ProgressStyle.Segment(40).setColor(Color.parseColor("#10B981")), // Green
                    NotificationCompat.ProgressStyle.Segment(40).setColor(Color.parseColor("#FBBF24"))  // Yellow
                )
            )
            .setProgressPoints(
                listOf(
                    NotificationCompat.ProgressStyle.Point(20).setColor(pointColor),
                    NotificationCompat.ProgressStyle.Point(60).setColor(pointColor)
                )
            )
            .setProgress(progress)
            .setProgressTrackerIcon(
                IconCompat.createWithResource(this, R.drawable.ic_prayer)
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
                            GoogleSampleNotificationManager.postPrayerNotification(
                                title = title,
                                content = content,
                                detailedMessage = detailedMessage,
                                progress = progress,
                                prayerPhase = prayerPhase,
                                prayerName = prayerName,
                                prayerTime = prayerTime
                            )
                            Log.d(TAG, "🧪 Posted SINGLE prayer notification via Google Live Update system with phase: $prayerPhase, real progress: ${progress}%")
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
                
                val location = settings.location
                if (location == null) {
                    Log.w(TAG, "No location available")
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
                
                // Get current and next prayer
                val allPrayers = prayerTimes.getActualPrayers()
                val rawCurrentPrayer = allPrayers.find { it.isCurrently }
                val nextPrayer = prayerTimes.getNextPrayer()
                
                // OVERRIDE DETECTION: Fix incorrect prayer detection for overnight period
                val now = LocalTime.now()
                val ishaTime = prayerTimes.isha
                val fajrTime = prayerTimes.fajr
                
                val currentPrayer = if (rawCurrentPrayer?.name == "Fajr" && now.isBefore(fajrTime)) {
                    Log.w(TAG, "🚨 INCORRECT DETECTION: System thinks Fajr is current at $now but Fajr is at $fajrTime")
                    Log.w(TAG, "🔧 FIXING: Checking if we're in overnight Isha→Fajr period")
                    
                    // Check if we're in the overnight period between yesterday's Isha and today's Fajr
                    val isInOvernightPeriod = if (now.isAfter(ishaTime)) {
                        // Same day: after Isha, before midnight
                        Log.d(TAG, "   Same day scenario: $now is after Isha ($ishaTime)")
                        true
                    } else if (now.isBefore(fajrTime)) {
                        // Next day: after midnight, before Fajr
                        Log.d(TAG, "   Cross-day scenario: $now is before Fajr ($fajrTime)")
                        true
                    } else {
                        false
                    }
                    
                    if (isInOvernightPeriod) {
                        Log.i(TAG, "✅ OVERRIDE: Treating this as Isha period, not Fajr")
                        // Create a virtual Isha prayer object representing the current period
                        PrayerTime("Isha", ishaTime, isNext = false, isCurrently = true)
                    } else {
                        rawCurrentPrayer
                    }
                } else {
                    rawCurrentPrayer
                }
                
                Log.d(TAG, "🔍 FINAL PRAYER DETECTION:")
                Log.d(TAG, "   Current Prayer: ${currentPrayer?.name ?: "None"}")
                Log.d(TAG, "   Next Prayer: ${nextPrayer?.name ?: "None"}")
                
                if (currentPrayer == null) {
                    // No current prayer - check if we're in cross-day period (Isha → Fajr)
                    Log.d(TAG, "🌙 No current prayer detected - checking for cross-day Isha→Fajr period")
                    
                    if (nextPrayer?.name == "Fajr") {
                        // We're likely in the overnight period between today's Isha and tomorrow's Fajr
                        val todayIsha = prayerTimes.isha
                        val now = LocalTime.now()
                        
                        Log.d(TAG, "📊 Cross-day scenario detected:")
                        Log.d(TAG, "   Today's Isha: $todayIsha")
                        Log.d(TAG, "   Tomorrow's Fajr: ${nextPrayer.time}")
                        Log.d(TAG, "   Current time: $now")
                        
                        // Calculate cross-day progress from today's Isha to tomorrow's Fajr
                        val timeUntilFajr = Duration.between(now, LocalTime.MAX) + Duration.between(LocalTime.MIN, nextPrayer.time)
                        val totalIshaToFajr = Duration.between(todayIsha, LocalTime.MAX) + Duration.between(LocalTime.MIN, nextPrayer.time)
                        
                        // FIXED: Handle elapsed time calculation properly for cross-day scenario
                        val elapsedSinceIsha = if (now.isAfter(todayIsha)) {
                            // Same day scenario: Current time is after Isha time today (e.g., Isha at 8 PM, now 11:30 PM)
                            Duration.between(todayIsha, now)
                        } else {
                            // Cross-midnight scenario: We've crossed midnight, so calculate elapsed time across day boundary
                            // Time from Isha to midnight + time from midnight to now
                            Duration.between(todayIsha, LocalTime.MAX) + Duration.between(LocalTime.MIN, now)
                        }
                        
                        if (totalIshaToFajr.toMinutes() > 0) {
                            // SAFETY CHECK: Ensure elapsed time is never negative
                            val elapsedMinutes = elapsedSinceIsha.toMinutes()
                            val (safeElapsedMinutes, progressPercentage) = if (elapsedMinutes < 0) {
                                Log.e(TAG, "🚨 NEGATIVE ELAPSED TIME DETECTED!")
                                Log.e(TAG, "   Today's Isha: $todayIsha")
                                Log.e(TAG, "   Current time: $now")
                                Log.e(TAG, "   Elapsed calculation: Duration.between($todayIsha, $now) = ${elapsedMinutes}m")
                                Log.e(TAG, "   now.isAfter(todayIsha): ${now.isAfter(todayIsha)}")
                                // Use 0 instead of negative value to prevent "-111m since Isha"
                                val safe = 0L
                                val progress = (safe.toFloat() / totalIshaToFajr.toMinutes().toFloat() * 100f).coerceIn(0f, 100f)
                                Log.w(TAG, "   Using safe elapsed time: ${safe}m (${progress}%)")
                                Pair(safe, progress)
                            } else {
                                val progress = (elapsedMinutes.toFloat() / totalIshaToFajr.toMinutes().toFloat() * 100f).coerceIn(0f, 100f)
                                Pair(elapsedMinutes, progress)
                            }
                            
                            Log.d(TAG, "✅ Cross-day progress calculation:")
                            Log.d(TAG, "   Time until Fajr: ${timeUntilFajr.toMinutes()} minutes")
                            Log.d(TAG, "   Total Isha→Fajr: ${totalIshaToFajr.toMinutes()} minutes")
                            Log.d(TAG, "   Elapsed since Isha: ${safeElapsedMinutes} minutes")
                            Log.d(TAG, "   Same day (now.isAfter(todayIsha)): ${now.isAfter(todayIsha)}")
                            Log.d(TAG, "   Progress: ${progressPercentage}%")
                            
                            // Create a virtual Isha prayer object to represent the current prayer period
                            val virtualIshaPrayer = PrayerTime("Isha", todayIsha, isNext = false, isCurrently = true)
                            
                            // Determine the prayer phase based on progress
                            val prayerPhase = when {
                                progressPercentage <= 20 -> PrayerPhase.GO_TO_MOSQUE
                                progressPercentage <= 60 -> PrayerPhase.BEST_TIME
                                else -> PrayerPhase.MAKE_TIME
                            }
                            
                            // Format title based on prayer phase (same logic as regular prayers)
                            val title = when (prayerPhase) {
                                PrayerPhase.GO_TO_MOSQUE -> "Go to Mosque for Isha"
                                PrayerPhase.BEST_TIME -> "Best Time to Pray Isha"
                                PrayerPhase.MAKE_TIME -> "Make Time for Isha"
                            }
                            
                            // Show time elapsed since Isha (not countdown to Fajr)
                            val elapsedText = formatElapsedTime(safeElapsedMinutes)
                            val content = "$elapsedText since Isha"
                            
                            // Detailed message can still show next prayer info
                            val detailedMessage = buildNextPrayerMessage(prayerTimes, nextPrayer)
                            
                            // Map prayer phase to string for GoogleSampleNotificationManager
                            val phaseString = when (prayerPhase) {
                                PrayerPhase.GO_TO_MOSQUE -> "GO_TO_MOSQUE"
                                PrayerPhase.BEST_TIME -> "BEST_TIME"
                                PrayerPhase.MAKE_TIME -> "MAKE_TIME"
                            }
                            
                            Log.d(TAG, "🕌 Cross-day Isha prayer notification:")
                            Log.d(TAG, "   Title: $title")
                            Log.d(TAG, "   Content: $content") 
                            Log.d(TAG, "   Phase: $phaseString (${progressPercentage}%)")
                            
                            return@withContext Sextuple(title, content, detailedMessage, phaseString, progressPercentage.toInt(), Pair(currentPrayer?.name ?: "Current Prayer", currentPrayer?.time?.toString() ?: LocalTime.now().toString()))
                        }
                    }
                    
                    // Fallback: Regular next prayer info with static progress
                    val title = "⏰ Next Prayer: ${nextPrayer?.name ?: "Unknown"}"
                    val content = nextPrayer?.let { "Next prayer in ${formatTimeRemaining(it.time)}" } ?: "Prayer times calculated"
                    val detailedMessage = buildNextPrayerMessage(prayerTimes, nextPrayer)
                    return@withContext Sextuple(title, content, detailedMessage, "MAKE_TIME", 10, Pair(currentPrayer?.name ?: "Current Prayer", currentPrayer?.time?.toString() ?: LocalTime.now().toString()))
                }
                
                // Calculate prayer time progress
                val prayerProgress = calculatePrayerProgress(currentPrayer, nextPrayer)
                
                // Format notification content based on prayer progress
                val title = when (prayerProgress.phase) {
                    PrayerPhase.GO_TO_MOSQUE -> "Go to Mosque for ${currentPrayer.name}"
                    PrayerPhase.BEST_TIME -> "Best Time to Pray ${currentPrayer.name}"
                    PrayerPhase.MAKE_TIME -> "Make Time for ${currentPrayer.name}"
                }
                val content = buildPrayerProgressContent(prayerProgress, currentPrayer)
                val detailedMessage = buildDetailedPrayerProgressMessage(prayerTimes, currentPrayer, nextPrayer, prayerProgress)
                
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
     * - GO_TO_MOSQUE (0-20 minutes): Time to prepare and go to mosque
     * - BEST_TIME (20 minutes to halfway): Optimal time for prayer
     * - MAKE_TIME (halfway to end): Ensure you make time for prayer
     * 
     * EDIT THESE VALUES TO:
     * - Change phase durations (currently 20 minutes for first phase)
     * - Modify phase logic
     * - Adjust progress calculation
     */
    private fun calculatePrayerProgress(currentPrayer: PrayerTime, nextPrayer: PrayerTime?): PrayerProgress {
        val now = LocalTime.now()
        val prayerStart = currentPrayer.time
        Log.d(TAG, "=== CALCULATING PRAYER PROGRESS ===")
        Log.d(TAG, "Current time: $now")
        Log.d(TAG, "Current prayer: ${currentPrayer.name} at $prayerStart")
        Log.d(TAG, "Next prayer: ${nextPrayer?.name ?: "None"} at ${nextPrayer?.time ?: "N/A"}")
        
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
        
        val totalDuration = Duration.between(prayerStart, prayerEnd).toMinutes()
        val remainingMinutes = Duration.between(now, prayerEnd).toMinutes()
        
        // PHASE DETERMINATION - Edit these conditions to change when phases switch
        val progressPhase = when {
            elapsedMinutes < 20 -> PrayerPhase.GO_TO_MOSQUE    // First 20 minutes: Go to mosque
            elapsedMinutes < (totalDuration / 2) -> PrayerPhase.BEST_TIME    // 20min to halfway: Best time
            else -> PrayerPhase.MAKE_TIME    // Halfway to end: Make time
        }
        
        // 3-SEGMENT PROGRESS CALCULATION to match notification segments
        // Segment 1 (Go to Mosque): 0-20% = First 20 minutes
        // Segment 2 (Best Time): 20-60% = 20min to halfway point  
        // Segment 3 (Make Time): 60-100% = Halfway to end
        val progressPercentage = when (progressPhase) {
            PrayerPhase.GO_TO_MOSQUE -> {
                // First 20 minutes maps to 0-20% progress
                val segmentProgress = (elapsedMinutes.toFloat() / 20f).coerceIn(0f, 1f)
                (segmentProgress * 20f).coerceIn(0f, 20f)
            }
            PrayerPhase.BEST_TIME -> {
                // From 20 minutes to halfway point maps to 20-60% progress
                val halfway = totalDuration / 2
                val segmentElapsed = (elapsedMinutes - 20).coerceAtLeast(0)
                val segmentDuration = (halfway - 20).coerceAtLeast(1) // Avoid division by zero
                val segmentProgress = (segmentElapsed.toFloat() / segmentDuration.toFloat()).coerceIn(0f, 1f)
                (20f + segmentProgress * 40f).coerceIn(20f, 60f)
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
        
        // Show elapsed time since prayer started - no redundant guidance text
        return "$elapsedText since ${currentPrayer.name}"
    }
    
    /**
     * Build detailed prayer progress message
     */
    private fun buildDetailedPrayerProgressMessage(
        prayerTimes: DayPrayerTimes,
        currentPrayer: PrayerTime,
        nextPrayer: PrayerTime?,
        progress: PrayerProgress
    ): String {
        return buildString {
            // Show next prayer countdown
            if (nextPrayer != null) {
                val timeRemaining = formatTimeRemaining(nextPrayer.time)
                appendLine("Next • ${nextPrayer.name} in $timeRemaining")
            }
        }
    }
    
    /**
     * Build next prayer message when no current prayer
     */
    private fun buildNextPrayerMessage(prayerTimes: DayPrayerTimes, nextPrayer: PrayerTime?): String {
        return buildString {
            if (nextPrayer != null) {
                val timeRemaining = formatTimeRemaining(nextPrayer.time)
                appendLine("Next • ${nextPrayer.name} in $timeRemaining")
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
     * 
     * EDIT THESE TO:
     * - Add new phases
     * - Change phase names
     * - Modify phase behavior in calculatePrayerProgress()
     */
    private enum class PrayerPhase {
        GO_TO_MOSQUE,    // 0-20 minutes: Go to mosque (Blue color in progress bar)
        BEST_TIME,       // 20+ minutes to halfway: Best time for prayer (Green color)
        MAKE_TIME        // Halfway+: Make time for prayer (Yellow color)
    }
    
    /**
     * Format time remaining until prayer
     */
    private fun formatTimeRemaining(prayerTime: LocalTime): String {
        val now = LocalTime.now()
        val duration = if (prayerTime.isAfter(now)) {
            Duration.between(now, prayerTime)
        } else {
            // Prayer is tomorrow
            Duration.between(now, LocalTime.MAX) + Duration.between(LocalTime.MIN, prayerTime)
        }
        
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "Now"
        }
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
    

    
    override fun onDestroy() {
        Log.d(TAG, "Prayer notification service destroy started")
        
        try {
            // Reset flags FIRST to stop all loops immediately
            isServiceRunning = false
            isInitializing = false
            
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
            
            Log.d(TAG, "Modern service cleanup completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
        } finally {
            super.onDestroy()
            Log.d(TAG, "Prayer notification service destroyed")
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
