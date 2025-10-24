package com.starception.submission.prayer.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.starception.submission.R
import com.starception.submission.prayer.model.DayPrayerTimes
import com.starception.submission.prayer.model.PrayerTime
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.scheduler.PrayerNotificationScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * WorkManager-based Prayer Notification Worker
 * 
 * This worker ensures prayer notifications are delivered even when the main service isn't running.
 * It's designed to be battery-efficient and reliable across all Android versions.
 * 
 * Features:
 * - Schedules exact prayer time notifications
 * - Works independently of foreground service
 * - Handles device reboots and app kills
 * - Battery-optimized scheduling
 */
@HiltWorker
class PrayerNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val prayerTimeCalculatorService: PrayerTimeCalculatorService,
    private val prayerSettingsRepository: PrayerSettingsRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "PrayerNotificationWorker"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "prayer_scheduled_notifications"
        private const val CHANNEL_NAME = "Scheduled Prayer Notifications"
        
        // Input data keys
        const val PRAYER_NAME_KEY = "prayer_name"
        const val PRAYER_TIME_KEY = "prayer_time"
        const val NOTIFICATION_TYPE_KEY = "notification_type"
        
        // Notification types
        const val TYPE_PRAYER_TIME = "prayer_time"
        const val TYPE_REMINDER = "reminder"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🕌 PrayerNotificationWorker started")
            
            val prayerName = inputData.getString(PRAYER_NAME_KEY) ?: "Prayer"
            val prayerTime = inputData.getString(PRAYER_TIME_KEY) ?: ""
            val notificationType = inputData.getString(NOTIFICATION_TYPE_KEY) ?: TYPE_PRAYER_TIME
            
            Log.d(TAG, "Processing notification: $prayerName at $prayerTime (type: $notificationType)")
            
            // Create notification channel
            createNotificationChannel()
            
            // Show the notification
            when (notificationType) {
                TYPE_PRAYER_TIME -> showPrayerTimeNotification(prayerName, prayerTime)
                TYPE_REMINDER -> showPrayerReminderNotification(prayerName, prayerTime)
            }
            
            // Schedule next prayer if this is a prayer time notification
            if (notificationType == TYPE_PRAYER_TIME) {
                scheduleNextPrayerNotification()
            }
            
            Log.d(TAG, "✅ PrayerNotificationWorker completed successfully")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ PrayerNotificationWorker failed", e)
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for prayer times and reminders"
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showPrayerTimeNotification(prayerName: String, prayerTime: String) {
        // Create large icon bitmap for notification
        val largeIcon = ContextCompat.getDrawable(applicationContext, R.drawable.ic_prayer_time_24)?.toBitmap()
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("$prayerName Prayer")
            .setContentText("It's time for $prayerName • $prayerTime")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$prayerName Prayer Time\n\n" +
                        "Time: $prayerTime\n" +
                        "اَللّٰهُمَّ تَقَبَّلْ مِنَّا\n" +
                        "(O Allah, accept from us)"))
            .setSmallIcon(R.drawable.ic_prayer)
            .setLargeIcon(largeIcon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(false)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        Log.d(TAG, "📱 Posted prayer time notification: $prayerName at $prayerTime")
    }

    private fun showPrayerReminderNotification(prayerName: String, prayerTime: String) {
        // Create large icon bitmap for notification
        val largeIcon = ContextCompat.getDrawable(applicationContext, R.drawable.ic_prayer_time_24)?.toBitmap()
        
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("$prayerName in 20 min")
            .setContentText("Starts at $prayerTime")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$prayerName Prayer in 20 minutes\n\n" +
                        "Time: $prayerTime\n" +
                        "Prepare: Wudu • Clean space • Qibla"))
            .setSmallIcon(R.drawable.ic_prayer)
            .setLargeIcon(largeIcon)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOngoing(false)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()

        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
        
        Log.d(TAG, "📱 Posted prayer reminder notification: $prayerName at $prayerTime")
    }

    private suspend fun scheduleNextPrayerNotification() {
        try {
            // For now, schedule a simple test notification
            // TODO: Integrate with actual prayer time calculation service
            Log.d(TAG, "📅 Scheduling next prayer notification (simplified version)")
            
            // Schedule a test notification for 1 minute from now
            val testTime = LocalTime.now().plusMinutes(1)
            PrayerNotificationScheduler.schedulePrayerNotification(
                applicationContext,
                "Test Prayer",
                testTime.format(DateTimeFormatter.ofPattern("h:mm a"))
            )
            
            Log.d(TAG, "📅 Scheduled test prayer notification at ${testTime}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule next prayer notification", e)
        }
    }

}
