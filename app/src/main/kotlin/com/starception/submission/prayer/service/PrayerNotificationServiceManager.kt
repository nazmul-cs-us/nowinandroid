package com.starception.submission.prayer.service

import android.content.Context
import android.util.Log
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.repository.PrayerSettingsRepository
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
    private val prayerTimeCalculatorService: PrayerTimeCalculatorService,
    private val prayerSettingsRepository: PrayerSettingsRepository
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
            Log.d(TAG, "📅 Scheduling backup notifications with REAL prayer times")
            
            // Cancel any existing backup notifications
            PrayerNotificationScheduler.cancelAllPrayerNotifications(context)
            
            // Get actual prayer times for today
            val prayerTimes = getPrayerTimesForToday()
            
            if (prayerTimes.isEmpty()) {
                Log.w(TAG, "⚠️ No prayer times available, cannot schedule backup notifications")
                return
            }
            
            Log.d(TAG, "📋 Prayer times retrieved:")
            prayerTimes.forEach { (name, time) ->
                Log.d(TAG, "   $name: $time")
            }
            
            // Schedule all prayer notifications with 15-minute reminders
            PrayerNotificationScheduler.scheduleAllPrayerNotifications(
                context = context,
                prayerTimes = prayerTimes,
                reminderMinutes = 15
            )
            
            Log.d(TAG, "✅ Scheduled ${prayerTimes.size} backup prayer notifications")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule backup notifications", e)
        }
    }
    
    /**
     * Get prayer times for today from the calculator service
     */
    private suspend fun getPrayerTimesForToday(): Map<String, String> {
        return try {
            val today = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            
            // Get current settings
            val settings = prayerSettingsRepository.getSettings()
            
            // Calculate prayer times for today
            val dayPrayerTimes = prayerTimeCalculatorService.calculatePrayerTimes(
                date = today,
                location = settings.location ?: return emptyMap(),
                settings = settings
            )
            
            // Check if dayPrayerTimes is null
            if (dayPrayerTimes == null) {
                Log.w(TAG, "⚠️ Prayer times calculation returned null")
                return emptyMap()
            }
            
            // Convert to map of name -> time string
            val prayerTimesMap = mutableMapOf<String, String>()
            
            prayerTimesMap["Fajr"] = dayPrayerTimes.fajr.format(formatter)
            prayerTimesMap["Dhuhr"] = dayPrayerTimes.dhuhr.format(formatter)
            prayerTimesMap["Asr"] = dayPrayerTimes.asr.format(formatter)
            prayerTimesMap["Maghrib"] = dayPrayerTimes.maghrib.format(formatter)
            prayerTimesMap["Isha"] = dayPrayerTimes.isha.format(formatter)
            
            prayerTimesMap
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get prayer times for today", e)
            emptyMap()
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
