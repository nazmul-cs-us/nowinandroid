package com.starception.submission.prayer.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.work.*
import com.starception.submission.R
import com.starception.submission.prayer.worker.PrayerNotificationWorker
import java.util.concurrent.TimeUnit

/**
 * Prayer Notification Broadcast Receiver
 * 
 * This receiver handles AlarmManager-triggered prayer notifications
 * and converts them to WorkManager jobs for better reliability.
 * 
 * This is particularly useful for:
 * - Android versions below 6.0 (API 23)
 * - Exact timing requirements
 * - Fallback when WorkManager fails
 */
class PrayerNotificationReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.d(TAG, "📱 PrayerNotificationReceiver triggered")
            
            val prayerName = intent.getStringExtra(PrayerNotificationWorker.PRAYER_NAME_KEY) ?: "Prayer"
            val prayerTime = intent.getStringExtra(PrayerNotificationWorker.PRAYER_TIME_KEY) ?: ""
            val notificationType = intent.getStringExtra(PrayerNotificationWorker.NOTIFICATION_TYPE_KEY) 
                ?: PrayerNotificationWorker.TYPE_PRAYER_TIME
            
            Log.d(TAG, "Processing: $prayerName at $prayerTime (type: $notificationType)")
            
            // Show notification directly (WorkManager has issues with Hilt)
            showPrayerNotification(context, prayerName, prayerTime, notificationType)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ PrayerNotificationReceiver failed", e)
        }
    }
    
    private fun scheduleWorkManagerJob(
        context: Context,
        prayerName: String,
        prayerTime: String,
        notificationType: String
    ) {
        val inputData = Data.Builder()
            .putString(PrayerNotificationWorker.PRAYER_NAME_KEY, prayerName)
            .putString(PrayerNotificationWorker.PRAYER_TIME_KEY, prayerTime)
            .putString(PrayerNotificationWorker.NOTIFICATION_TYPE_KEY, notificationType)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<PrayerNotificationWorker>()
            .setInputData(inputData)
            .setInitialDelay(0, TimeUnit.MILLISECONDS) // Execute immediately
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
        
        Log.d(TAG, "✅ Scheduled WorkManager job for $prayerName")
    }
    
    private fun showPrayerNotification(
        context: Context,
        prayerName: String,
        prayerTime: String,
        notificationType: String
    ) {
        try {
            // Create notification channel
            createNotificationChannel(context)
            
            val notificationId = if (notificationType == PrayerNotificationWorker.TYPE_PRAYER_TIME) 2001 else 2002
            
            // Create large icon bitmap for notification
            val largeIcon = ContextCompat.getDrawable(context, R.drawable.ic_prayer_time_24)?.toBitmap()
            
            val notification = if (notificationType == PrayerNotificationWorker.TYPE_PRAYER_TIME) {
                // Prayer time notification - when it's actually prayer time
                NotificationCompat.Builder(context, CHANNEL_ID)
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
            } else {
                // Prayer reminder notification - 20 minutes before prayer
                NotificationCompat.Builder(context, CHANNEL_ID)
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
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, notification)
            
            Log.d(TAG, "📱 Posted prayer notification: $prayerName ($notificationType) at $prayerTime")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show prayer notification", e)
        }
    }
    
    private fun createNotificationChannel(context: Context) {
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
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    companion object {
        private const val TAG = "PrayerNotificationReceiver"
        private const val CHANNEL_ID = "prayer_scheduled_notifications"
        private const val CHANNEL_NAME = "Scheduled Prayer Notifications"
    }
}

