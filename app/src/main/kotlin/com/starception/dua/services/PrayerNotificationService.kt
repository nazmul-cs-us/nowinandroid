package com.starception.dua.services

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.starception.dua.prayer.model.Location
import com.starception.dua.prayer.model.PrayerSettings
import com.starception.dua.prayer.service.PrayerTimeCalculatorService
import com.starception.dua.prayer.service.SmartPrayerNotificationService
import com.starception.dua.prayer.repository.PrayerSettingsRepository
import com.starception.dua.util.PrayerNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class PrayerNotificationService : Service() {
    
    companion object {
        private const val TAG = "PrayerNotificationService"
        private const val FOREGROUND_ID = 1001
        private const val UPDATE_INTERVAL_MS = 60000L // Update every minute
    }
    
    @Inject
    lateinit var prayerCalculatorService: PrayerTimeCalculatorService
    
    @Inject
    lateinit var smartNotificationService: SmartPrayerNotificationService
    
    @Inject
    lateinit var settingsRepository: PrayerSettingsRepository
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Prayer notification service created")
        
        // Initialize the notification manager
        PrayerNotificationManager.initialize(this)
        
        // Start as a foreground service with an initial notification
        startForegroundWithInitialNotification()
        
        // Start real prayer time updates
        startRealPrayerTimeUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Prayer notification service started")
        return START_STICKY
    }

    private fun startForegroundWithInitialNotification() {
        try {
            // Create initial notification while loading prayer times
            PrayerNotificationManager.postPrayerNotification("Loading", 0, true)
            
            // Get the notification for foreground service
            if (PrayerNotificationManager.supportsLiveUpdates()) {
                Log.d(TAG, "Using Live Update notifications (Android 16+)")
                Log.d(TAG, "Has promotable characteristics: ${PrayerNotificationManager.hasPromotableCharacteristics()}")
            } else {
                Log.d(TAG, "Using regular notifications (pre-Android 16)")
            }
            
            // Start foreground with a basic notification (the Live Update will be posted separately)
            val basicNotification = androidx.core.app.NotificationCompat.Builder(this, "prayer_live_update_channel")
                .setContentTitle("Prayer Time Tracker")
                .setContentText("Loading prayer times...")
                .setSmallIcon(com.starception.dua.R.drawable.ic_prayer_hands)
                .setOngoing(true)
                .build()
            
            startForeground(FOREGROUND_ID, basicNotification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }
    
    /**
     * Start real prayer time updates using actual calculation service
     */
    private fun startRealPrayerTimeUpdates() {
        serviceScope.launch {
            while (true) {
                try {
                    updatePrayerNotification()
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating prayer notification", e)
                    // Fallback notification on error
                    PrayerNotificationManager.postPrayerNotification("Error loading times", 0, true)
                }
                
                // Wait before next update (every minute) - on background thread
                kotlinx.coroutines.delay(UPDATE_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Update prayer notification with current smart status
     */
    private suspend fun updatePrayerNotification() {
        try {
            // Get current settings and location
            val settings = settingsRepository.getSettings()
            val location = settings.location
            
            // Validate location
            if (location == null || !location.isValid()) {
                PrayerNotificationManager.postPrayerNotification("Location needed", 0, true)
                Log.w(TAG, "Invalid or missing location for prayer calculation")
                return
            }
            
            // Calculate today's prayer times
            val today = LocalDate.now()
            val prayerTimes = prayerCalculatorService.calculatePrayerTimes(today, location, settings)
            
            if (prayerTimes == null) {
                PrayerNotificationManager.postPrayerNotification("Calculation error", 0, true)
                Log.w(TAG, "Could not calculate prayer times")
                return
            }
            
            // Get smart prayer status
            val status = smartNotificationService.calculatePrayerStatus(prayerTimes)
            
            // Update notification with smart content
            updateNotificationWithStatus(status)
            
            Log.d(TAG, "Updated prayer notification: ${status.timeText}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in updatePrayerNotification", e)
            PrayerNotificationManager.postPrayerNotification("Update error", 0, true)
        }
    }
    
    /**
     * Update notification using smart prayer status
     */
    private fun updateNotificationWithStatus(status: SmartPrayerNotificationService.PrayerStatus) {
        // Use the detailed message for notification content
        val notificationTitle = if (status.isInPrayerWindow && status.currentPrayer != null) {
            "${status.currentPrayer} Prayer Time"
        } else {
            "Next: ${status.nextPrayer}"
        }
        
        // Post detailed notification with smart content
        PrayerNotificationManager.postDetailedPrayerNotification(
            title = notificationTitle,
            content = status.timeText,
            detailedMessage = status.detailedMessage,
            progress = status.progressPercentage,
            isOngoing = true
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Prayer notification service destroyed")
        serviceScope.cancel()
        PrayerNotificationManager.cancelPrayerNotification()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
