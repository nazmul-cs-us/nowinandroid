package com.starception.submission.prayer.service

import android.content.Context
import android.util.Log
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.scheduler.PrayerNotificationScheduler
import com.starception.submission.services.PrayerNotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prayer Notification Service Manager
 * 
 * This class manages the integration between the foreground service
 * and the backup notification system, ensuring users always get
 * prayer notifications regardless of service state.
 * 
 * Strategy:
 * 1. Primary: Foreground service for live updates
 * 2. Backup: WorkManager + AlarmManager for exact timing
 * 3. Recovery: Boot receiver for automatic rescheduling
 */
@Singleton
class PrayerNotificationServiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prayerTimeCalculatorService: PrayerTimeCalculatorService
) {
    
    companion object {
        private const val TAG = "PrayerNotificationServiceManager"
    }
    
    /**
     * Initialize the complete prayer notification system
     * 
     * This method sets up both the foreground service and backup
     * notification system to ensure reliable prayer notifications.
     */
    fun initializeNotificationSystem() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🚀 Initializing complete prayer notification system")
                
                // 1. Start the foreground service for live updates
                startForegroundService()
                
                // 2. Schedule backup notifications for exact timing
                scheduleBackupNotifications()
                
                Log.d(TAG, "✅ Prayer notification system initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize notification system", e)
            }
        }
    }
    
    /**
     * Start the foreground service for live prayer time updates
     */
    private fun startForegroundService() {
        try {
            val serviceIntent = android.content.Intent(context, PrayerNotificationService::class.java)
            context.startForegroundService(serviceIntent)
            Log.d(TAG, "📱 Started foreground service for live updates")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start foreground service", e)
        }
    }
    
    /**
     * Schedule backup notifications using WorkManager + AlarmManager
     */
    private suspend fun scheduleBackupNotifications() {
        try {
            Log.d(TAG, "📅 Scheduling backup notifications")
            
            // Cancel any existing backup notifications
            PrayerNotificationScheduler.cancelAllPrayerNotifications(context)
            
            // For now, schedule test notifications
            // TODO: Integrate with actual prayer time calculation service
            val testPrayerTimes = mapOf(
                "Fajr" to "05:30",
                "Dhuhr" to "12:15",
                "Asr" to "15:45",
                "Maghrib" to "18:20",
                "Isha" to "19:45"
            )
            
            // Schedule all prayer notifications with 15-minute reminders
            PrayerNotificationScheduler.scheduleAllPrayerNotifications(
                context = context,
                prayerTimes = testPrayerTimes,
                reminderMinutes = 15
            )
            
            Log.d(TAG, "✅ Scheduled ${testPrayerTimes.size} test backup prayer notifications")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule backup notifications", e)
        }
    }
    
    /**
     * Update prayer notifications when settings change
     */
    fun updatePrayerNotifications() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🔄 Updating prayer notifications due to settings change")
                
                // Cancel existing notifications
                PrayerNotificationScheduler.cancelAllPrayerNotifications(context)
                
                // Reschedule with new settings
                scheduleBackupNotifications()
                
                Log.d(TAG, "✅ Prayer notifications updated successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to update prayer notifications", e)
            }
        }
    }
    
    /**
     * Stop all prayer notifications
     */
    fun stopAllNotifications() {
        try {
            Log.d(TAG, "🛑 Stopping all prayer notifications")
            
            // Stop foreground service
            val serviceIntent = android.content.Intent(context, PrayerNotificationService::class.java)
            context.stopService(serviceIntent)
            
            // Cancel backup notifications
            PrayerNotificationScheduler.cancelAllPrayerNotifications(context)
            
            Log.d(TAG, "✅ All prayer notifications stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop notifications", e)
        }
    }
    
    /**
     * Check if the notification system is working
     */
    fun isNotificationSystemActive(): Boolean {
        // Check if foreground service is running
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        
        val isServiceRunning = runningServices.any { 
            it.service.className == PrayerNotificationService::class.java.name 
        }
        
        Log.d(TAG, "📊 Notification system status - Service running: $isServiceRunning")
        return isServiceRunning
    }
    
}
