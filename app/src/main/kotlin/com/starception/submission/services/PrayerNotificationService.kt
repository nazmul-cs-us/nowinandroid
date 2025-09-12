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
import com.starception.submission.R
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.PrayerTime
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.util.PrayerNotificationManager
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
    
    companion object {
        private const val TAG = "PrayerNotificationService"
        
        // NOTIFICATION CONFIGURATION - Edit these to change notification behavior
        private const val NOTIFICATION_CHANNEL_ID = "prayer_live_update_channel"
        private const val NOTIFICATION_ID = 1001  // Single ID ensures updates replace previous notifications
        
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
                    
                    // Start prayer updates with timeout protection
                    withTimeoutOrNull(5000L) { // 5 second timeout
                        startRealPrayerTimeUpdates()
                        Log.d(TAG, "✓ Prayer updates started successfully")
                    } ?: run {
                        Log.w(TAG, "⚠️ Prayer updates startup timed out")
                    }
                    
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
                "Prayer Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prayer time notifications and live updates"
                enableVibration(true) // Enable vibration for prayer notifications
                setShowBadge(true)
                enableLights(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
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
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Prayer Service")
            .setContentText("Active")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Use system icon as fallback
            .setOngoing(true)
            .build()
    }
    
    private fun createInitialNotification(): Notification {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Prayer Time Tracker")
            .setContentText("Initializing...")
            .setSmallIcon(R.drawable.ic_prayer)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(Color.parseColor("#8B418F"))
            .setColorized(true)
            .build()
        
        // Force refresh to ensure Live Updates are activated on Android 16
        if (PrayerNotificationManager.supportsLiveUpdates()) {
            PrayerNotificationManager.forceRefreshNotification()
        }
        
        return notification
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
                
                // Initialize notification manager with timeout
                withTimeoutOrNull(5000L) {
                    // Check if device supports Live Updates
                    if (PrayerNotificationManager.supportsLiveUpdates()) {
                        Log.d(TAG, "Device supports Live Updates")
                        Log.d(TAG, "Has promotable characteristics: ${PrayerNotificationManager.hasPromotableCharacteristics()}")
                        
                        // Post initial notification
                        PrayerNotificationManager.postPrayerNotification("Prayer Time Tracker Active", 0, true)
                    }
                } ?: Log.w(TAG, "Notification manager initialization timed out, continuing")
                
                // Start observing settings changes for automatic recalculation
                observeSettingsChanges()
                
                // Start simplified prayer time updates with timeout protection
                withTimeoutOrNull(10000L) {
                    startPrayerTimeUpdateLoop()
                } ?: Log.w(TAG, "Prayer update loop initialization timed out")
                
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
        val maxUpdates = 60 // Max 60 updates - prevents excessive notifications
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
                    // Fallback notification on error
                    if (PrayerNotificationManager.isInitialized()) {
                        PrayerNotificationManager.postPrayerNotification("Prayer tracker active", 0, true)
                    }
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
            
            // Auto-stop service after max updates or time limit
            val runtimeSeconds = (System.currentTimeMillis() - startTime) / 1000
            Log.d(TAG, "Service stopping automatically - updates: $updateCount, runtime: ${runtimeSeconds}s")
            isServiceRunning = false
            stopForeground(true)
            stopSelf()
            
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
                    val (title, content, detailedMessage) = prayerData
                    
                    // Quick progress calculation
                    val progress = calculateNotificationProgress(prayerData)
                    val currentPhase = getCurrentPrayerPhase(progress)
                    
                    // Update notification
                    PrayerNotificationManager.updatePrayerProgressSmart(
                        prayerName = if (content.contains(" since ")) {
                            content.split(" since ").lastOrNull()?.split(" • ")?.firstOrNull() ?: "Prayer"
                        } else {
                            "Prayer"
                        },
                        progress = progress,
                        previousPhase = previousPrayerPhase,
                        title = title,
                        content = content,
                        detailedMessage = detailedMessage
                    )
                    
                    previousPrayerPhase = currentPhase
                    
                    val totalDuration = System.currentTimeMillis() - updateStartTime
                    Log.d(TAG, "✓ Notification updated successfully in ${totalDuration}ms")
                } else {
                    // Quick fallback
                    PrayerNotificationManager.postPrayerNotification("Prayer tracker active", 50, true)
                    Log.d(TAG, "Using fallback notification")
                }
                
            } ?: run {
                // Timeout occurred - use emergency fallback
                Log.w(TAG, "⚠️ Update timed out after 5s - using emergency fallback")
                if (PrayerNotificationManager.isInitialized()) {
                    PrayerNotificationManager.postPrayerNotification("Prayer tracker running", 0, true)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in update: ${e.message}")
            try {
                PrayerNotificationManager.postPrayerNotification("Prayer service active", 0, true)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fallback notification also failed: ${fallbackError.message}")
            }
        }
    }
    
    /**
     * Get current prayer data for notification with ANR prevention
     */
    private suspend fun getCurrentPrayerData(): Triple<String, String, String>? {
        return withContext(Dispatchers.IO) { // Ensure background thread
            try {
                Log.d(TAG, "=== GETTING PRAYER DATA (BACKGROUND THREAD) ===")
                
                // Quick settings check with very short timeout (using injected dependency)
                val settings = withTimeoutOrNull(1000L) { // 1 second max
                    prayerSettingsRepository.getSettings()
                }
                if (settings == null) {
                    Log.w(TAG, "Settings unavailable, using fallback")
                    return@withContext Triple("Prayer Time Tracker", "Loading...", "Initializing prayer data")
                }
                
                val location = settings.location
                if (location == null) {
                    Log.w(TAG, "No location available")
                    return@withContext Triple("Prayer Time Tracker", "Location needed", "Grant location permission to see prayer times")
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
                    return@withContext Triple("Prayer Time Tracker", "Calculating...", "Prayer times being calculated")
                }
                
                Log.d(TAG, "✓ Prayer times calculated in ${calculationTime}ms")
                
                // Get current and next prayer
                val currentPrayer = prayerTimes.getActualPrayers().find { it.isCurrently }
                val nextPrayer = prayerTimes.getNextPrayer()
                
                if (currentPrayer == null) {
                    // No current prayer, show next prayer info
                    val title = "⏰ Next Prayer: ${nextPrayer?.name ?: "Unknown"}"
                    val content = nextPrayer?.let { "Next prayer in ${formatTimeRemaining(it.time)}" } ?: "Prayer times calculated"
                    val detailedMessage = buildNextPrayerMessage(prayerTimes, nextPrayer)
                    return@withContext Triple(title, content, detailedMessage)
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
                
                Triple(title, content, detailedMessage)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting prayer data: ${e.message}")
                Triple("Prayer Time Tracker", "Service active", "Prayer time updates running")
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
                val timeUntilNext = Duration.between(now, nextPrayer.time)
                Log.d(TAG, "Time until next prayer: ${timeUntilNext.toMinutes()} minutes")
                
                if (timeUntilNext.isNegative.not()) {
                    // Calculate progress based on time since last prayer
                    val totalTime = Duration.between(prayerStart, nextPrayer.time)
                    val elapsedTime = Duration.between(prayerStart, now)
                    
                    Log.d(TAG, "Total time between prayers: ${totalTime.toMinutes()} minutes")
                    Log.d(TAG, "Elapsed time since current prayer: ${elapsedTime.toMinutes()} minutes")
                    
                    if (totalTime.toMinutes() > 0) {
                        val progressPercentage = (elapsedTime.toMinutes().toFloat() / totalTime.toMinutes().toFloat() * 100f).coerceIn(0f, 100f)
                        Log.d(TAG, "Calculated progress: ${progressPercentage}% (MAKE_TIME phase)")
                        
                        val prayerProgress = PrayerProgress(
                            elapsedMinutes = elapsedTime.toMinutes(),
                            remainingMinutes = timeUntilNext.toMinutes(),
                            totalDuration = totalTime.toMinutes(),
                            progressPercentage = progressPercentage,
                            phase = PrayerPhase.MAKE_TIME // Since prayer time has passed
                        )
                        Log.d(TAG, "✓ Returning prayer progress: $prayerProgress")
                        return prayerProgress
                    }
                }
            }
            
            // Fallback: prayer time has passed, no progress to show
            return PrayerProgress(
                elapsedMinutes = Duration.between(prayerStart, now).toMinutes(),
                remainingMinutes = 0,
                totalDuration = 0,
                progressPercentage = 0f,
                phase = PrayerPhase.MAKE_TIME
            )
        }
        
        // Prayer time is still active, calculate normal progress
        val prayerEnd = nextPrayer?.time ?: prayerStart.plusHours(1) // Default 1 hour if no next prayer
        
        val elapsedMinutes = Duration.between(prayerStart, now).toMinutes()
        val totalDuration = Duration.between(prayerStart, prayerEnd).toMinutes()
        val remainingMinutes = Duration.between(now, prayerEnd).toMinutes()
        
        // PHASE DETERMINATION - Edit these conditions to change when phases switch
        val progressPhase = when {
            elapsedMinutes < 20 -> PrayerPhase.GO_TO_MOSQUE    // First 20 minutes: Go to mosque
            elapsedMinutes < (totalDuration / 2) -> PrayerPhase.BEST_TIME    // 20min to halfway: Best time
            else -> PrayerPhase.MAKE_TIME    // Halfway to end: Make time
        }
        
        val progressPercentage = (elapsedMinutes.toFloat() / totalDuration.toFloat() * 100).coerceIn(0f, 100f)
        
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
            elapsedMinutes == 0L -> "just started"
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
     * Calculate progress with lightweight fallback - no heavy operations
     */
    private fun calculateNotificationProgress(prayerData: Triple<String, String, String>): Int {
        return try {
            // Simple time-based progress fallback to avoid any blocking operations
            val now = LocalTime.now()
            val currentHour = now.hour
            val currentMinute = now.minute
            
            // Simple hour-based progress (0-100%) to avoid any database/calculation delays
            // This provides a basic progress indication without any blocking operations
            val hourProgress = ((currentHour % 12) * 8) + (currentMinute / 8) // 0-100 range based on 12-hour cycle
            val safeProgress = hourProgress.coerceIn(0, 100)
            
            Log.d(TAG, "Using lightweight progress calculation: ${safeProgress}% (hour=${currentHour}, min=${currentMinute})")
            safeProgress
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in lightweight progress calculation: ${e.message}")
            50 // Default middle progress
        }
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
