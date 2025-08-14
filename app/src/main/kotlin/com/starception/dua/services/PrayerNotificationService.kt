package com.starception.dua.services

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
import com.starception.dua.R
import com.starception.dua.prayer.model.DayPrayerTimes
import com.starception.dua.prayer.model.PrayerTime
import com.starception.dua.prayer.service.PrayerTimeCalculatorService
import com.starception.dua.prayer.repository.PrayerSettingsRepository
import com.starception.dua.util.PrayerNotificationManager
import com.starception.dua.util.AnrPreventionConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import javax.inject.Inject

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
    
    companion object {
        private const val TAG = "PrayerNotificationService"
        private const val NOTIFICATION_CHANNEL_ID = "prayer_live_update_channel"
        private const val NOTIFICATION_ID = 1001
        
        // Check if service is running in another process
        fun isServiceRunningInAnotherProcess(context: android.content.Context): Boolean {
            val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return manager.getRunningServices(Integer.MAX_VALUE).any {
                it.service.className == PrayerNotificationService::class.java.name &&
                it.pid != android.os.Process.myPid()
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
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Prayer notification service onStartCommand - startId: $startId")
        
        if (isServiceRunning) {
            Log.d(TAG, "Service already running, ignoring duplicate start")
            return START_STICKY
        }
        
        // Start foreground service immediately
        startForeground(NOTIFICATION_ID, createInitialNotification())
        
        // Mark service as running
        isServiceRunning = true
        
        // Start prayer time updates in background
        startRealPrayerTimeUpdates()
        
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
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Prayer Time Tracker")
            .setContentText("Starting prayer time updates...")
            .setSmallIcon(R.drawable.ic_prayer_hands)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, com.starception.dua.MainActivity::class.java),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0
                )
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }
    
    /**
     * Initialize background prayer notification updates after foreground service is started
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
     * Start simplified prayer time updates
     */
    private suspend fun startPrayerTimeUpdateLoop() {
        val startTime = System.currentTimeMillis()
        val maxServiceTime = 24 * 60 * 60 * 1000L // 24 hours max
        val maxUpdates = 60 // Max 60 updates
        var updateCount = 0
        
        try {
            while (isServiceRunning && updateCount < maxUpdates) {
                try {
                    // Check if service has been running too long
                    if (System.currentTimeMillis() - startTime > maxServiceTime) {
                        Log.d(TAG, "Service exceeded max time limit (${maxServiceTime / 1000}s), stopping automatically")
                        break
                    }
                    
                    updatePrayerNotificationWithRealData()
                    updateCount++
                    if (AnrPreventionConfig.LOG_SERVICE_LIFECYCLE) {
                        Log.d(TAG, "Prayer update #$updateCount/$maxUpdates")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating prayer notification", e)
                    // Fallback notification on error
                    if (PrayerNotificationManager.isInitialized()) {
                        PrayerNotificationManager.postPrayerNotification("Prayer tracker active", 0, true)
                    }
                }
                
                // Wait before next update (every minute) - on background thread
                delay(AnrPreventionConfig.SERVICE_UPDATE_INTERVAL_MS)
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
     * Update prayer notification with real prayer data
     */
    private suspend fun updatePrayerNotificationWithRealData() {
        try {
            // Ensure PrayerNotificationManager is initialized before use
            if (!PrayerNotificationManager.isInitialized()) {
                PrayerNotificationManager.initialize(applicationContext)
            }
            
            // Get current prayer data
            val prayerData = getCurrentPrayerData()
            if (prayerData != null) {
                val (title, content, detailedMessage) = prayerData
                
                // Calculate progress for progress bar
                val progress = calculateNotificationProgress(prayerData)
                
                PrayerNotificationManager.postDetailedPrayerNotification(
                    title = title,
                    content = content,
                    detailedMessage = detailedMessage,
                    progress = progress,
                    isOngoing = true
                )
                Log.d(TAG, "Updated prayer notification with real data: $title (progress: $progress%)")
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
     * Get current prayer data for notification
     */
    private suspend fun getCurrentPrayerData(): Triple<String, String, String>? {
        return try {
            // Get current location and settings
            val settings = prayerSettingsRepository.getSettings()
            val location = settings.location
            
            if (location == null) {
                return null
            }
            
            // Calculate today's prayer times
            val today = LocalDate.now()
            val prayerTimes = prayerTimeCalculatorService.calculatePrayerTimes(today, location, settings)
            
            if (prayerTimes == null) {
                return null
            }
            
            // Get current and next prayer
            val currentPrayer = prayerTimes.getAllPrayers().find { it.isCurrently }
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
            val title = "🕌 ${currentPrayer.name} Prayer Time"
            val content = buildPrayerProgressContent(prayerProgress)
            val detailedMessage = buildDetailedPrayerProgressMessage(prayerTimes, currentPrayer, nextPrayer, prayerProgress)
            
            Triple(title, content, detailedMessage)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current prayer data", e)
            null
        }
    }
    
    /**
     * Calculate prayer time progress and phase
     */
    private fun calculatePrayerProgress(currentPrayer: PrayerTime, nextPrayer: PrayerTime?): PrayerProgress {
        val now = LocalTime.now()
        val prayerStart = currentPrayer.time
        val prayerEnd = nextPrayer?.time ?: prayerStart.plusHours(1) // Default 1 hour if no next prayer
        
        val elapsedMinutes = Duration.between(prayerStart, now).toMinutes()
        val totalDuration = Duration.between(prayerStart, prayerEnd).toMinutes()
        val remainingMinutes = Duration.between(now, prayerEnd).toMinutes()
        
        val progressPhase = when {
            elapsedMinutes < 20 -> PrayerPhase.GO_TO_MOSQUE
            elapsedMinutes < (totalDuration / 2) -> PrayerPhase.BEST_TIME
            else -> PrayerPhase.MAKE_TIME
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
    private fun buildPrayerProgressContent(progress: PrayerProgress): String {
        val elapsedText = formatElapsedTime(progress.elapsedMinutes)
        
        val guidanceText = when (progress.phase) {
            PrayerPhase.GO_TO_MOSQUE -> "Please go to mosque"
            PrayerPhase.BEST_TIME -> "Best time for Prayer"
            PrayerPhase.MAKE_TIME -> "Please make time for prayer"
        }
        
        return "$elapsedText, $guidanceText"
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
        val now = LocalTime.now()
        val prayers = prayerTimes.getAllPrayers()
        
        return buildString {
            appendLine("🕌 ${currentPrayer.name} Prayer Progress")
            appendLine()
            
            // Current prayer progress
            appendLine("⏰ Started: ${currentPrayer.time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}")
            appendLine("📊 Progress: ${progress.progressPercentage.toInt()}% complete")
            appendLine()
            
            // Phase guidance
            appendLine("📋 Current Phase:")
            when (progress.phase) {
                PrayerPhase.GO_TO_MOSQUE -> {
                    appendLine("🕌 0-20 minutes: Please go to mosque")
                    appendLine("   ⏰ Time elapsed: ${formatElapsedTime(progress.elapsedMinutes)}")
                    appendLine("   🎯 Goal: Reach mosque within 20 minutes")
                }
                PrayerPhase.BEST_TIME -> {
                    appendLine("🌟 20+ minutes: Best time for Prayer")
                    appendLine("   ⏰ Time elapsed: ${formatElapsedTime(progress.elapsedMinutes)}")
                    appendLine("   🎯 This is the optimal prayer window")
                }
                PrayerPhase.MAKE_TIME -> {
                    appendLine("⚠️  Halfway+: Please make time for prayer")
                    appendLine("   ⏰ Time elapsed: ${formatElapsedTime(progress.elapsedMinutes)}")
                    appendLine("   🎯 Don't delay further")
                }
            }
            appendLine()
            
            // Progress bar visualization
            appendLine("📈 Progress Bar:")
            appendLine("${currentPrayer.time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm"))}──🕌──⏰20min──🕌──⏰${(progress.totalDuration/2).toInt()}min──🕌──${nextPrayer?.time?.format(java.time.format.DateTimeFormatter.ofPattern("h:mm")) ?: "??:??"}")
            appendLine("   Start    Mosque   Best Time   End")
            appendLine()
            
            // Next prayer info
            if (nextPrayer != null) {
                appendLine("⏭️ Next Prayer: ${nextPrayer.name}")
                appendLine("   ⏰ Time: ${nextPrayer.time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}")
                appendLine("   ⏳ Remaining: ${formatTimeRemaining(nextPrayer.time)}")
            }
            
            // Daily schedule
            appendLine()
            appendLine("📅 Today's Schedule:")
            prayers.forEach { prayer ->
                val timeStr = prayer.time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                val status = when {
                    prayer.isCurrently -> "🟢 NOW (${progress.progressPercentage.toInt()}%)"
                    prayer.isNext -> "⏭️ NEXT"
                    prayer.time.isBefore(now) -> "✅ Done"
                    else -> "⏳ Upcoming"
                }
                
                appendLine("${prayer.name}: $timeStr $status")
            }
        }
    }
    
    /**
     * Build next prayer message when no current prayer
     */
    private fun buildNextPrayerMessage(prayerTimes: DayPrayerTimes, nextPrayer: PrayerTime?): String {
        val prayers = prayerTimes.getAllPrayers()
        
        return buildString {
            appendLine("⏰ Next Prayer Information")
            appendLine()
            
            if (nextPrayer != null) {
                appendLine("⏭️ Next: ${nextPrayer.name}")
                appendLine("   ⏰ Time: ${nextPrayer.time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))}")
                appendLine("   ⏳ Remaining: ${formatTimeRemaining(nextPrayer.time)}")
            }
            
            appendLine()
            appendLine("📅 Today's Schedule:")
            prayers.forEach { prayer ->
                val timeStr = prayer.time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                val status = when {
                    prayer.isNext -> "⏭️ NEXT"
                    prayer.time.isBefore(LocalTime.now()) -> "✅ Done"
                    else -> "⏳ Upcoming"
                }
                
                appendLine("${prayer.name}: $timeStr $status")
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
    
    private enum class PrayerPhase {
        GO_TO_MOSQUE,    // 0-20 minutes: Go to mosque
        BEST_TIME,       // 20+ minutes to halfway: Best time for prayer
        MAKE_TIME        // Halfway+: Make time for prayer
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
            elapsedMinutes == 1L -> "1 minute since adhan"
            elapsedMinutes < 60 -> "${elapsedMinutes} minutes since adhan"
            else -> {
                val hours = elapsedMinutes / 60
                val minutes = elapsedMinutes % 60
                when {
                    minutes == 0L -> "${hours}h since adhan"
                    else -> "${hours}h ${minutes}m since adhan"
                }
            }
        }
    }

    /**
     * Calculate progress for the notification progress bar.
     * This shows the progress through the current prayer time.
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
            
            // Get current prayer
            val currentPrayer = prayerTimes.getAllPrayers().find { it.isCurrently } ?: return 0
            val nextPrayer = prayerTimes.getNextPrayer()
            
            // Calculate progress through current prayer
            val now = LocalTime.now()
            val prayerStart = currentPrayer.time
            val prayerEnd = nextPrayer?.time ?: prayerStart.plusHours(1) // Default 1 hour if no next prayer
            
            val elapsedMinutes = Duration.between(prayerStart, now).toMinutes()
            val totalDuration = Duration.between(prayerStart, prayerEnd).toMinutes()
            
            if (totalDuration <= 0) return 0
            
            val progressPercentage = (elapsedMinutes.toFloat() / totalDuration.toFloat() * 100).coerceIn(0f, 100f)
            progressPercentage.toInt()
            
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
