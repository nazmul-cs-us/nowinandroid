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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Apply offset to a base prayer time (duplicated from feature module to avoid circular dependency)
 */
private fun applyOffsetToTime(baseTime: LocalTime, offsetMinutes: Int): LocalTime {
    if (offsetMinutes == 0) return baseTime
    val adjustedDateTime = LocalDateTime.of(LocalDate.now(), baseTime)
        .plusMinutes(offsetMinutes.toLong())
    return adjustedDateTime.toLocalTime()
}

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

        /**
         * Reschedule notifications when settings change
         * This is a static helper that can be called from UI without needing the full singleton
         */
        fun rescheduleNotificationsWithNewSettings(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d(TAG, "🔄 Rescheduling notifications due to settings change")

                    // Get repository from Hilt
                    val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        com.starception.submission.feature.prayertimes.data.PrayerTimeCalculatorEntryPoint::class.java
                    )
                    val repository = entryPoint.prayerSettingsRepository()
                    val notificationPrefs = repository.getNotificationPreferences()

                    // Cancel existing scheduled notifications
                    PrayerNotificationScheduler.cancelAllPrayerNotifications(context)

                    // Silent-during-prayer is independent from banners and Adhan.
                    // Keep the prayer-boundary alarms scheduled even when the master
                    // notification switch is off so DND can still start on time.
                    if (notificationPrefs.notificationsEnabled ||
                        notificationPrefs.silentDuringPrayerEnabled
                    ) {
                        // Get prayer times from repository
                        val settings = repository.getSettings()
                        val location = settings.location

                        if (location != null) {
                            // Use the calculator entry point
                            val calculatorService = entryPoint.prayerTimeCalculatorService()
                            val today = LocalDate.now()
                            val dayPrayerTimes = calculatorService.calculatePrayerTimes(
                                date = today,
                                location = location,
                                settings = settings
                            )

                            if (dayPrayerTimes != null) {
                                val formatter = DateTimeFormatter.ofPattern("h:mm a")
                                val offsets = settings.timeOffsets

                                // Build prayer times map with offsets applied
                                val prayerTimes = mapOf(
                                    "Fajr" to applyOffsetToTime(dayPrayerTimes.fajr, offsets.fajr).format(formatter),
                                    "Dhuhr" to applyOffsetToTime(dayPrayerTimes.dhuhr, offsets.dhuhr).format(formatter),
                                    "Asr" to applyOffsetToTime(dayPrayerTimes.asr, offsets.asr).format(formatter),
                                    "Maghrib" to applyOffsetToTime(dayPrayerTimes.maghrib, offsets.maghrib).format(formatter),
                                    "Isha" to applyOffsetToTime(dayPrayerTimes.isha, offsets.isha).format(formatter)
                                )

                                // Log all prior notification settings for debugging
                                Log.d(TAG, "📋 Prior notification settings:")
                                Log.d(TAG, "   Fajr: ${notificationPrefs.fajrPriorMinutes} min")
                                Log.d(TAG, "   Dhuhr: ${notificationPrefs.dhuhrPriorMinutes} min")
                                Log.d(TAG, "   Asr: ${notificationPrefs.asrPriorMinutes} min")
                                Log.d(TAG, "   Maghrib: ${notificationPrefs.maghribPriorMinutes} min")
                                Log.d(TAG, "   Isha: ${notificationPrefs.ishaPriorMinutes} min")

                                // Get per-prayer reminder minutes and schedule
                                prayerTimes.forEach { (prayerName, prayerTime) ->
                                    val reminderMinutes = notificationPrefs.getPriorMinutesForPrayer(prayerName)
                                    Log.d(TAG, "⏰ Scheduling $prayerName at $prayerTime with $reminderMinutes min prior reminder")
                                    PrayerNotificationScheduler.schedulePrayerNotification(
                                        context = context,
                                        prayerName = prayerName,
                                        prayerTime = prayerTime,
                                        reminderMinutes = reminderMinutes
                                    )
                                }

                                Log.d(TAG, "✅ Rescheduled notifications with new settings")
                            }
                        }
                    } else {
                        Log.d(TAG, "🔕 Notifications and silent-during-prayer disabled, not rescheduling")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to reschedule notifications", e)
                }
            }
        }

        /**
         * Helper to apply time offset (duplicated to avoid dependency on private function)
         */
        private fun applyOffsetToTime(baseTime: LocalTime, offsetMinutes: Int): LocalTime {
            if (offsetMinutes == 0) return baseTime
            val adjustedDateTime = LocalDateTime.of(LocalDate.now(), baseTime)
                .plusMinutes(offsetMinutes.toLong())
            return adjustedDateTime.toLocalTime()
        }
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
                
                // Check if we can schedule exact alarms
                val canScheduleExact = PrayerNotificationScheduler.canScheduleExactAlarms(context)
                if (!canScheduleExact) {
                    Log.w(TAG, "⚠️ Cannot schedule exact alarms - permission not granted")
                    Log.w(TAG, "📝 User must enable 'Alarms & reminders' in app settings")
                }
                
                // 1. Start the foreground service for live updates
                startForegroundService()
                
                // 2. Schedule backup notifications for exact timing
                scheduleBackupNotifications()
                
                // 3. Test notification disabled - system is working!
                // if (canScheduleExact) {
                //     PrayerNotificationScheduler.scheduleTestNotification(context, delaySeconds = 120)
                // }
                
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
            if (PrayerNotificationService.isServiceRunningInAnotherProcess(context)) {
                Log.d(TAG, "📱 Prayer foreground service already running; skipping duplicate start")
                return
            }

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

            // Get notification preferences for per-prayer reminder times
            val notificationPrefs = prayerSettingsRepository.getNotificationPreferences()

            // Log all prior notification settings for debugging
            Log.d(TAG, "📋 Prior notification settings:")
            Log.d(TAG, "   Fajr: ${notificationPrefs.fajrPriorMinutes} min")
            Log.d(TAG, "   Dhuhr: ${notificationPrefs.dhuhrPriorMinutes} min")
            Log.d(TAG, "   Asr: ${notificationPrefs.asrPriorMinutes} min")
            Log.d(TAG, "   Maghrib: ${notificationPrefs.maghribPriorMinutes} min")
            Log.d(TAG, "   Isha: ${notificationPrefs.ishaPriorMinutes} min")

            // Schedule each prayer notification with its own reminder time
            prayerTimes.forEach { (prayerName, prayerTime) ->
                val reminderMinutes = notificationPrefs.getPriorMinutesForPrayer(prayerName)
                Log.d(TAG, "⏰ Scheduling $prayerName at $prayerTime with $reminderMinutes min prior reminder")
                PrayerNotificationScheduler.schedulePrayerNotification(
                    context = context,
                    prayerName = prayerName,
                    prayerTime = prayerTime,
                    reminderMinutes = reminderMinutes
                )
            }

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
            val formatter = DateTimeFormatter.ofPattern("h:mm a")
            
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
            
            // Convert to map of name -> time string with offset applied
            val prayerTimesMap = mutableMapOf<String, String>()
            val offsets = settings.timeOffsets
            
            // Apply user offsets to base prayer times before scheduling notifications
            val fajrAdjusted = applyOffsetToTime(dayPrayerTimes.fajr, offsets.fajr)
            val dhuhrAdjusted = applyOffsetToTime(dayPrayerTimes.dhuhr, offsets.dhuhr)
            val asrAdjusted = applyOffsetToTime(dayPrayerTimes.asr, offsets.asr)
            val maghribAdjusted = applyOffsetToTime(dayPrayerTimes.maghrib, offsets.maghrib)
            val ishaAdjusted = applyOffsetToTime(dayPrayerTimes.isha, offsets.isha)
            
            prayerTimesMap["Fajr"] = fajrAdjusted.format(formatter)
            prayerTimesMap["Dhuhr"] = dhuhrAdjusted.format(formatter)
            prayerTimesMap["Asr"] = asrAdjusted.format(formatter)
            prayerTimesMap["Maghrib"] = maghribAdjusted.format(formatter)
            prayerTimesMap["Isha"] = ishaAdjusted.format(formatter)
            
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
