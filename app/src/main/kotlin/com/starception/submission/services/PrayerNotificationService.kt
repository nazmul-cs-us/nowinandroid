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
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import javax.inject.Inject
import android.graphics.Color

/**
 * Simplified Prayer Notification Service
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
        
        // Check if service is running in another process
        fun isServiceRunningInAnotherProcess(context: android.content.Context): Boolean {
            val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningServices = manager.getRunningServices(Integer.MAX_VALUE)
            val ourServiceCount = runningServices.count {
                it.service.className == PrayerNotificationService::class.java.name
            }
            
            Log.d(TAG, "Found $ourServiceCount instances of PrayerNotificationService running")
            
            return ourServiceCount > 0
        }
        
        // Get current instance count for debugging
        fun getServiceInstanceCount(context: android.content.Context): Int {
            val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return manager.getRunningServices(Integer.MAX_VALUE).count {
                it.service.className == PrayerNotificationService::class.java.name
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Prayer notification service created")
        
        // Create notification channel
        createNotificationChannel()
        
        // Initialize PrayerNotificationManager
        PrayerNotificationManager.initialize(this)
        
        // Check Live Update status for debugging
        if (PrayerNotificationManager.supportsLiveUpdates()) {
            val status = PrayerNotificationManager.checkLiveUpdateStatus()
            Log.i(TAG, "Live Update Status: $status")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Prayer notification service onStartCommand - startId: $startId, isServiceRunning: $isServiceRunning")
        
        if (isServiceRunning) {
            Log.w(TAG, "Service already running, ignoring duplicate start - startId: $startId")
            return START_STICKY
        }
        
        // Log current service count for debugging
        val serviceCount = getServiceInstanceCount(this)
        Log.d(TAG, "Starting service - current instance count: $serviceCount, startId: $startId")
        
        // Start foreground service immediately
        startForeground(NOTIFICATION_ID, createInitialNotification())
        
        // Mark service as running
        isServiceRunning = true
        
        // Start prayer time updates in background
        startRealPrayerTimeUpdates()
        
        Log.d(TAG, "Service started successfully - startId: $startId")
        return START_STICKY
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
    private fun startRealPrayerTimeUpdates() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Starting real prayer time updates")
                
                // Check if device supports Live Updates
                if (PrayerNotificationManager.supportsLiveUpdates()) {
                    Log.d(TAG, "Device supports Live Updates")
                    Log.d(TAG, "Has promotable characteristics: ${PrayerNotificationManager.hasPromotableCharacteristics()}")
                    
                    // Post initial notification
                    PrayerNotificationManager.postPrayerNotification("Prayer Time Tracker Active", 0, true)
                }
                
                // Start simplified prayer time updates
                startPrayerTimeUpdateLoop()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting prayer time updates", e)
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
     * NOTIFICATION UPDATE ENGINE: Updates the notification with current prayer data
     * 
     * This is where the actual prayer information gets calculated and displayed.
     * 
     * KEY PROCESSES:
     * 1. Gets current prayer data (which prayer is active, time remaining, etc.)
     * 2. Calculates progress percentage for progress bar
     * 3. Determines prayer phase (Go to Mosque/Best Time/Make Time)
     * 4. Uses smart notification system (only alerts on phase changes)
     * 
     * EDIT THIS SECTION TO:
     * - Change how prayer data is calculated
     * - Modify notification content format
     * - Adjust smart notification behavior
     * - Add new notification features
     */
    private suspend fun updatePrayerNotificationWithRealData() {
        val updateStartTime = System.currentTimeMillis()
        Log.d(TAG, "=== UPDATING PRAYER NOTIFICATION WITH REAL DATA ===")
        Log.d(TAG, "Update time: ${java.time.LocalTime.now()}")
        
        try {
            // SAFETY CHECK - Ensure notification manager is ready
            if (!PrayerNotificationManager.isInitialized()) {
                Log.d(TAG, "PrayerNotificationManager not initialized, initializing...")
                PrayerNotificationManager.initialize(applicationContext)
                Log.d(TAG, "✓ PrayerNotificationManager initialized")
            } else {
                Log.d(TAG, "✓ PrayerNotificationManager already initialized")
            }
            
            // Get current prayer data
            Log.d(TAG, "Getting current prayer data...")
            val prayerDataStartTime = System.currentTimeMillis()
            val prayerData = getCurrentPrayerData()
            val prayerDataDuration = System.currentTimeMillis() - prayerDataStartTime
            Log.d(TAG, "✓ Prayer data retrieved in ${prayerDataDuration}ms")
            if (prayerData != null) {
                val (title, content, detailedMessage) = prayerData
                
                // Calculate progress for progress bar
                val progress = calculateNotificationProgress(prayerData)
                
                // Get current prayer phase for smart notifications
                val currentPhase = getCurrentPrayerPhase(progress)
                
                // SMART NOTIFICATION SYSTEM - Only alerts when prayer phase changes
                // 
                // BEHAVIOR:
                // - Silent updates when in same phase (no sound/vibration)
                // - Alert with sound/vibration when phase changes
                // - Phases: Go to Mosque (0-20min) → Best Time (20min-halfway) → Make Time (halfway+)
                // 
                // EDIT updatePrayerProgressSmart() in PrayerNotificationManager to change this behavior
                PrayerNotificationManager.updatePrayerProgressSmart(
                    prayerName = if (content.contains(" since ")) {
                        // Extract prayer name from content (e.g., "15 minutes since Dhuhr" → "Dhuhr")
                        content.split(" since ").lastOrNull()?.split(" • ")?.firstOrNull() ?: "Prayer"
                    } else {
                        "Prayer"
                    },
                    progress = progress,
                    previousPhase = previousPrayerPhase, // Used to detect phase changes
                    title = title,
                    content = content,
                    detailedMessage = detailedMessage
                )
                
                // Update previous phase for next comparison
                previousPrayerPhase = currentPhase
                
                Log.d(TAG, "Updated prayer notification with smart system: $title (progress: $progress%, phase: $currentPhase)")
            } else {
                // Fallback if prayer data is not available
                PrayerNotificationManager.postPrayerNotification("Prayer times unavailable", 0, true)
                Log.d(TAG, "Prayer data not available, using fallback notification")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in updatePrayerNotificationWithRealData", e)
            PrayerNotificationManager.postPrayerNotification("Prayer tracker active", 0, true)
        }
    }
    
    /**
     * Get current prayer data for notification with detailed location debugging
     */
    private suspend fun getCurrentPrayerData(): Triple<String, String, String>? {
        return try {
            Log.d(TAG, "=== NOTIFICATION PRAYER DATA DEBUG START ===")
            
            // Get current location and settings with detailed logging
            Log.d(TAG, "STEP 1: Getting prayer settings from repository...")
            val settings = prayerSettingsRepository.getSettings()
            Log.d(TAG, "Settings retrieved: calculation method=${settings.calculationMethod.name}")
            
            val location = settings.location
            Log.d(TAG, "STEP 2: Location check - Location is ${if (location != null) "AVAILABLE" else "NULL"}")
            
            // Enhanced location debugging
            if (location == null) {
                Log.w(TAG, "❌ CRITICAL: No location set in settings")
                Log.w(TAG, "This means:")
                Log.w(TAG, "  - User hasn't granted location permission OR")
                Log.w(TAG, "  - Location services are disabled OR") 
                Log.w(TAG, "  - GPS couldn't get a fix OR")
                Log.w(TAG, "  - Location cache is empty")
                Log.w(TAG, "Notification will show fallback message")
                return null
            }
            
            // Log detailed location information
            Log.d(TAG, "✓ Location found: ${location.getDisplayName()}")
            Log.d(TAG, "  Coordinates: ${location.latitude}, ${location.longitude}")
            Log.d(TAG, "  Location type: ${location::class.java.simpleName}")
            Log.d(TAG, "  Location source: ${if (location.getDisplayName().contains("GPS")) "GPS" else "Manual/Default"}")
            
            // Calculate today's prayer times with detailed logging
            val today = LocalDate.now()
            Log.d(TAG, "STEP 3: Calculating prayer times for date: $today")
            Log.d(TAG, "Using location: ${location.getDisplayName()} (${location.latitude}, ${location.longitude})")
            
            val calculationStartTime = System.currentTimeMillis()
            val prayerTimes = prayerTimeCalculatorService.calculatePrayerTimes(today, location, settings)
            val calculationTime = System.currentTimeMillis() - calculationStartTime
            
            if (prayerTimes == null) {
                Log.e(TAG, "❌ PRAYER CALCULATION FAILED after ${calculationTime}ms")
                Log.e(TAG, "Location: ${location.getDisplayName()}")
                Log.e(TAG, "Calculation method: ${settings.calculationMethod.name}")
                Log.e(TAG, "This indicates:")
                Log.e(TAG, "  - Astronomical calculation error OR")
                Log.e(TAG, "  - Invalid coordinates OR")
                Log.e(TAG, "  - Internal calculation service failure")
                Log.e(TAG, "Notification will use fallback message")
                return null
            }
            
            Log.d(TAG, "✓ Prayer times calculated successfully in ${calculationTime}ms")
            Log.d(TAG, "Prayer times for ${location.getDisplayName()}:")
            prayerTimes.getActualPrayers().forEach { prayer ->
                Log.d(TAG, "  ${prayer.name}: ${prayer.time} ${if (prayer.isCurrently) "[CURRENT]" else ""}")
            }
            
            // Get current and next prayer
            val currentPrayer = prayerTimes.getActualPrayers().find { it.isCurrently }
            val nextPrayer = prayerTimes.getNextPrayer()
            
            if (currentPrayer == null) {
                // No current prayer, show next prayer info
                val title = "⏰ Next Prayer: ${nextPrayer?.name ?: "Unknown"}"
                val content = nextPrayer?.let { "Next prayer in ${formatTimeRemaining(it.time)}" } ?: "Prayer times calculated"
                val detailedMessage = buildNextPrayerMessage(prayerTimes, nextPrayer)
                return Triple(title, content, detailedMessage)
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
            
            // Keep title simple - don't add next prayer info here to avoid duplication
            // The detailedMessage already shows the next prayer countdown
            val enhancedTitle = title
            
            Triple(enhancedTitle, content, detailedMessage)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current prayer data", e)
            return null
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
     * This shows the progress through the current prayer time using phase-based calculation.
     */
    private fun calculateNotificationProgress(prayerData: Triple<String, String, String>): Int {
        return try {
            // Get current location and settings
            val settings = prayerSettingsRepository.getSettings()
            val location = settings.location
            
            if (location == null) return 0
            
            // Calculate today's prayer times
            val today = LocalDate.now()
            val prayerTimes = prayerTimeCalculatorService.calculatePrayerTimes(today, location, settings)
            
            if (prayerTimes == null) return 0
            
            // Get current prayer and next prayer
            val currentPrayer = prayerTimes.getActualPrayers().find { it.isCurrently }
            val nextPrayer = prayerTimes.getNextPrayer()
            
            if (currentPrayer == null && nextPrayer == null) return 0
            
            val now = LocalTime.now()
            
            // If we're in a current prayer time, calculate progress through it
            if (currentPrayer != null) {
                val prayerStart = currentPrayer.time
                val elapsedMinutes = Duration.between(prayerStart, now).toMinutes()
                
                // Get next prayer for duration calculation
                val prayerEnd = nextPrayer?.time ?: prayerStart.plusHours(2) // Default 2 hours if no next prayer
                val totalPrayerDuration = Duration.between(prayerStart, prayerEnd).toMinutes()
                val halfDuration = totalPrayerDuration / 2
                
                // Calculate overall progress across all segments (0-100%)
                // Ensure minimum phase durations and handle edge cases
                val minPhaseDuration = 20L // Minimum 20 minutes per phase
                val adjustedHalfDuration = maxOf(halfDuration, minPhaseDuration * 2) // At least 40 minutes total
                
                val overallProgress = when {
                    elapsedMinutes <= minPhaseDuration -> {
                        // Go to mosque phase: 0-20 minutes
                        // First segment: 0-20% of total progress
                        (elapsedMinutes.toFloat() / minPhaseDuration.toFloat() * 20f).coerceIn(0f, 20f)
                    }
                    elapsedMinutes <= adjustedHalfDuration -> {
                        // Best time phase: 20 minutes to adjusted halfway
                        // Second segment: 20-60% of total progress
                        val bestTimePhaseDuration = adjustedHalfDuration - minPhaseDuration
                        val progressInBestTime = elapsedMinutes - minPhaseDuration
                        val segmentProgress = (progressInBestTime.toFloat() / bestTimePhaseDuration.toFloat() * 40f).coerceIn(0f, 40f)
                        20f + segmentProgress // 20% + progress within second segment
                    }
                    else -> {
                        // Make time phase: adjusted halfway to end
                        // Third segment: 60-100% of total progress
                        val makeTimePhaseDuration = maxOf(totalPrayerDuration - adjustedHalfDuration, minPhaseDuration)
                        val progressInMakeTime = elapsedMinutes - adjustedHalfDuration
                        val segmentProgress = (progressInMakeTime.toFloat() / makeTimePhaseDuration.toFloat() * 40f).coerceIn(0f, 40f)
                        60f + segmentProgress // 60% + progress within third segment
                    }
                }
                
                // Return the overall progress (0-100) across all segments
                val finalProgress = overallProgress.toInt()
                
                // Determine which phase we're in for logging
                val currentPhase = when {
                    elapsedMinutes <= minPhaseDuration -> "Go to mosque (0-20%)"
                    elapsedMinutes <= adjustedHalfDuration -> "Best time (20-60%)"
                    else -> "Make time (60-100%)"
                }
                
                Log.d(TAG, "Current prayer progress: elapsed=${elapsedMinutes}m, total=${totalPrayerDuration}m, adjustedHalf=${adjustedHalfDuration}m, phase=$currentPhase, progress=$finalProgress%")
                return finalProgress
            }
            
            // If no current prayer, calculate progress towards next prayer
            if (nextPrayer != null) {
                val timeUntilNext = Duration.between(now, nextPrayer.time)
                
                // If next prayer is today, calculate progress towards it
                if (timeUntilNext.isNegative.not()) {
                    // Calculate progress based on time since last prayer (or start of day)
                    val lastPrayer = prayerTimes.getActualPrayers().lastOrNull { it.time.isBefore(now) }
                    val startTime = lastPrayer?.time ?: LocalTime.MIN
                    val totalTime = Duration.between(startTime, nextPrayer.time)
                    val elapsedTime = Duration.between(startTime, now)
                    
                    if (totalTime.toMinutes() > 0) {
                        val progress = (elapsedTime.toMinutes().toFloat() / totalTime.toMinutes().toFloat() * 100f).coerceIn(0f, 100f)
                        Log.d(TAG, "Next prayer progress: elapsed=${elapsedTime.toMinutes()}m, total=${totalTime.toMinutes()}m, progress=${progress.toInt()}%")
                        return progress.toInt()
                    }
                } else {
                    // Next prayer is tomorrow, show 0% progress
                    Log.d(TAG, "Next prayer is tomorrow, showing 0% progress")
                    return 0
                }
            }
            
            // Fallback: no progress to show
            Log.d(TAG, "No prayer progress to calculate")
            0
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating notification progress", e)
            0
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
