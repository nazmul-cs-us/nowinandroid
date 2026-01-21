package com.starception.submission.prayer.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.work.*
import com.starception.submission.prayer.worker.PrayerNotificationWorker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import com.starception.submission.prayer.util.FileLogger

/**
 * Prayer Notification Scheduler
 * 
 * This class provides a robust scheduling system for prayer time notifications
 * that works even when the main service isn't running.
 * 
 * Features:
 * - WorkManager for reliable scheduling (Android 6+)
 * - AlarmManager for exact timing (all Android versions)
 * - Automatic rescheduling of next prayer
 * - Battery optimization compliance
 * - Boot receiver integration
 */
object PrayerNotificationScheduler {
    
    private const val TAG = "PrayerNotificationScheduler"
    private const val WORK_NAME_PREFIX = "prayer_notification_"
    private const val ALARM_REQUEST_CODE_PREFIX = 1000
    
    /**
     * Schedule a prayer time notification
     *
     * @param context Application context
     * @param prayerName Name of the prayer (Fajr, Dhuhr, etc.)
     * @param prayerTime Time in h:mm a format (12-hour with AM/PM)
     * @param reminderMinutes Minutes before prayer to show reminder (optional)
     */
    fun schedulePrayerNotification(
        context: Context,
        prayerName: String,
        prayerTime: String,
        reminderMinutes: Int = 20
    ) {
        try {
            Log.d(TAG, "📅 Scheduling prayer notification: $prayerName at $prayerTime")
            FileLogger.logPrayerEvent(
                event = "SCHEDULE_REQUEST",
                prayerName = prayerName,
                scheduledTime = prayerTime,
                details = mapOf("reminderMinutes" to reminderMinutes)
            )

            // Check if notifications are enabled for this specific prayer
            val notificationsEnabled = isNotificationEnabledForPrayer(context, prayerName)
            if (!notificationsEnabled) {
                Log.d(TAG, "🔕 Notifications disabled for $prayerName - skipping scheduling")
                return
            }

            val prayerDateTime = parsePrayerTime(prayerTime)
            if (prayerDateTime == null) {
                Log.e(TAG, "❌ Failed to parse prayer time: $prayerTime")
                return
            }
            
            // Schedule main prayer notification
            scheduleExactNotification(
                context = context,
                prayerName = prayerName,
                prayerTime = prayerTime,
                notificationTime = prayerDateTime,
                notificationType = PrayerNotificationWorker.TYPE_PRAYER_TIME,
                requestCode = getRequestCode(prayerName, "main"),
                priorMinutes = reminderMinutes
            )

            // Schedule reminder notification if requested
            if (reminderMinutes > 0) {
                val reminderTime = prayerDateTime.minusMinutes(reminderMinutes.toLong())
                if (reminderTime.isAfter(LocalDateTime.now())) {
                    scheduleExactNotification(
                        context = context,
                        prayerName = prayerName,
                        prayerTime = prayerTime,
                        notificationTime = reminderTime,
                        notificationType = PrayerNotificationWorker.TYPE_REMINDER,
                        requestCode = getRequestCode(prayerName, "reminder"),
                        priorMinutes = reminderMinutes
                    )
                    Log.d(TAG, "⏰ Scheduled reminder for $prayerName ${reminderMinutes} minutes before")
                }
            }
            
            Log.d(TAG, "✅ Successfully scheduled $prayerName notification")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule prayer notification", e)
        }
    }
    
    /**
     * Schedule all prayer notifications for today
     */
    fun scheduleAllPrayerNotifications(
        context: Context,
        prayerTimes: Map<String, String>,
        reminderMinutes: Int = 20
    ) {
        Log.d(TAG, "📅 Scheduling all prayer notifications for today")
        
        prayerTimes.forEach { (prayerName, prayerTime) ->
            schedulePrayerNotification(context, prayerName, prayerTime, reminderMinutes)
        }
    }
    
    /**
     * Cancel all scheduled prayer notifications
     */
    fun cancelAllPrayerNotifications(context: Context) {
        Log.d(TAG, "🗑️ Canceling all prayer notifications")
        
        try {
            // Cancel WorkManager jobs
            val workManager = WorkManager.getInstance(context)
            workManager.cancelAllWorkByTag("prayer_notification")
            
            // Cancel AlarmManager alarms
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val prayerNames = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
            
            prayerNames.forEach { prayerName ->
                // Cancel main prayer alarm
                val mainIntent = createNotificationIntent(context, prayerName, "", PrayerNotificationWorker.TYPE_PRAYER_TIME)
                val mainPendingIntent = PendingIntent.getBroadcast(
                    context,
                    getRequestCode(prayerName, "main"),
                    mainIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(mainPendingIntent)
                
                // Cancel reminder alarm
                val reminderIntent = createNotificationIntent(context, prayerName, "", PrayerNotificationWorker.TYPE_REMINDER)
                val reminderPendingIntent = PendingIntent.getBroadcast(
                    context,
                    getRequestCode(prayerName, "reminder"),
                    reminderIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(reminderPendingIntent)
            }
            
            Log.d(TAG, "✅ All prayer notifications canceled")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cancel prayer notifications", e)
        }
    }
    
    /**
     * Reschedule all notifications (useful after device reboot)
     */
    fun rescheduleAllNotifications(context: Context) {
        Log.d(TAG, "🔄 Rescheduling all prayer notifications")
        // This would typically be called from a BootReceiver
        // Implementation would reload prayer times and reschedule
    }
    
    /**
     * Check if exact alarm permission is granted (Android 12+)
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canSchedule = alarmManager.canScheduleExactAlarms()
            Log.d(TAG, "📱 Can schedule exact alarms: $canSchedule")
            return canSchedule
        }
        return true // Pre-Android 12 doesn't need this permission
    }
    
    /**
     * Schedule a test notification to verify the system works
     */
    fun scheduleTestNotification(context: Context, delaySeconds: Int = 60) {
        try {
            Log.d(TAG, "🧪 Scheduling TEST notification in $delaySeconds seconds")
            
            val testTime = LocalTime.now().plusSeconds(delaySeconds.toLong())
            val testDateTime = LocalDateTime.of(LocalDate.now(), testTime)
            
            schedulePrayerNotification(
                context = context,
                prayerName = "Test Prayer",
                prayerTime = testTime.format(DateTimeFormatter.ofPattern("h:mm a")),
                reminderMinutes = 0 // No reminder for test
            )
            
            Log.d(TAG, "✅ Test notification scheduled for $testDateTime")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule test notification", e)
        }
    }
    
    private fun scheduleExactNotification(
        context: Context,
        prayerName: String,
        prayerTime: String,
        notificationTime: LocalDateTime,
        notificationType: String,
        requestCode: Int,
        priorMinutes: Int = 20
    ) {
        val currentTime = LocalDateTime.now()

        // Don't schedule notifications in the past
        if (notificationTime.isBefore(currentTime)) {
            Log.d(TAG, "⏭️ Skipping past notification: $prayerName at $prayerTime")
            return
        }

        // Calculate delay in milliseconds
        val delayMillis = java.time.Duration.between(currentTime, notificationTime).toMillis()

        if (delayMillis > 0) {
            // Use BOTH systems for maximum reliability
            // 1. WorkManager (good for reliability and constraints)
            scheduleWithWorkManager(context, prayerName, prayerTime, notificationType, delayMillis, priorMinutes)

            // 2. AlarmManager (best for exact timing, works even in deep sleep)
            scheduleWithAlarmManager(context, prayerName, prayerTime, notificationType, notificationTime, requestCode, priorMinutes)
        }
    }
    
    private fun scheduleWithWorkManager(
        context: Context,
        prayerName: String,
        prayerTime: String,
        notificationType: String,
        delayMillis: Long,
        priorMinutes: Int = 20
    ) {
        val inputData = Data.Builder()
            .putString(PrayerNotificationWorker.PRAYER_NAME_KEY, prayerName)
            .putString(PrayerNotificationWorker.PRAYER_TIME_KEY, prayerTime)
            .putString(PrayerNotificationWorker.NOTIFICATION_TYPE_KEY, notificationType)
            .putInt(PrayerNotificationWorker.PRIOR_MINUTES_KEY, priorMinutes)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<PrayerNotificationWorker>()
            .setInputData(inputData)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag("prayer_notification")
            .addTag("prayer_$prayerName")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(false)
                    .setRequiresCharging(false)
                    .build()
            )
            .build()
        
        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(workRequest)
        
        Log.d(TAG, "📱 Scheduled with WorkManager: $prayerName ($notificationType) in ${delayMillis / 1000}s")
    }
    
    private fun scheduleWithAlarmManager(
        context: Context,
        prayerName: String,
        prayerTime: String,
        notificationType: String,
        notificationTime: LocalDateTime,
        requestCode: Int,
        priorMinutes: Int = 20
    ) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Check if we can schedule exact alarms (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "⚠️ Cannot schedule exact alarms - permission not granted. Opening settings...")
                    // Note: We can't open settings automatically, user must grant permission manually
                    // The app will still try to use WorkManager as fallback
                    return
                }
            }

            val intent = createNotificationIntent(context, prayerName, prayerTime, notificationType, priorMinutes)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val triggerTime = notificationTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            
            val delaySeconds = java.time.Duration.between(LocalDateTime.now(), notificationTime).seconds
            Log.d(TAG, "⏰ Scheduled with AlarmManager: $prayerName ($notificationType) in ${delaySeconds}s at $notificationTime")

            // Log adhan scheduling with FileLogger
            FileLogger.logAdhanSchedule(
                prayerName = prayerName,
                scheduledTimeMillis = triggerTime,
                alarmType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) "EXACT_ALLOW_IDLE" else "EXACT",
                details = mapOf(
                    "notificationType" to notificationType,
                    "scheduledDateTime" to notificationTime.toString(),
                    "delaySeconds" to delaySeconds
                )
            )

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException scheduling alarm - exact alarm permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule with AlarmManager", e)
        }
    }
    
    private fun createNotificationIntent(
        context: Context,
        prayerName: String,
        prayerTime: String,
        notificationType: String,
        priorMinutes: Int = 20
    ): Intent {
        return Intent(context, com.starception.submission.prayer.receiver.PrayerNotificationReceiver::class.java).apply {
            putExtra(PrayerNotificationWorker.PRAYER_NAME_KEY, prayerName)
            putExtra(PrayerNotificationWorker.PRAYER_TIME_KEY, prayerTime)
            putExtra(PrayerNotificationWorker.NOTIFICATION_TYPE_KEY, notificationType)
            putExtra(PrayerNotificationWorker.PRIOR_MINUTES_KEY, priorMinutes)
        }
    }
    
    private fun parsePrayerTime(prayerTime: String): LocalDateTime? {
        return try {
            val time = LocalTime.parse(prayerTime, DateTimeFormatter.ofPattern("h:mm a"))
            LocalDateTime.of(LocalDate.now(), time)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse prayer time: $prayerTime", e)
            null
        }
    }
    
    private fun getRequestCode(prayerName: String, type: String): Int {
        val prayerCode = when (prayerName.lowercase()) {
            "fajr" -> 1
            "dhuhr" -> 2
            "asr" -> 3
            "maghrib" -> 4
            "isha" -> 5
            else -> 0
        }
        val typeCode = if (type == "main") 0 else 1
        return ALARM_REQUEST_CODE_PREFIX + (prayerCode * 10) + typeCode
    }

    /**
     * Check if notifications are enabled for a specific prayer
     * Reads from notification preferences JSON in SharedPreferences
     */
    private fun isNotificationEnabledForPrayer(context: Context, prayerName: String): Boolean {
        try {
            val prefs = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
            val notificationPrefsJson = prefs.getString("notification_preferences_json", null)

            if (notificationPrefsJson == null) {
                // No preferences saved yet - default to enabled
                Log.d(TAG, "📱 No notification preferences found - defaulting to enabled for $prayerName")
                return true
            }

            // Parse JSON to check notification settings
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val notificationPrefs = json.decodeFromString<com.starception.submission.prayer.model.PrayerNotificationPreferences>(notificationPrefsJson)

            // Use the model's helper method to check if notification is enabled
            val isEnabled = notificationPrefs.isNotificationEnabledForPrayer(prayerName)
            Log.d(TAG, "🔔 Notification for $prayerName: ${if (isEnabled) "ENABLED" else "DISABLED"}")
            return isEnabled

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading notification preferences for $prayerName", e)
            // Default to enabled on error
            return true
        }
    }
}
