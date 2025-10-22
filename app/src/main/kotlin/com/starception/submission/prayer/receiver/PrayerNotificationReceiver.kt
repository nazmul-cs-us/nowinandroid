package com.starception.submission.prayer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
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
    
    companion object {
        private const val TAG = "PrayerNotificationReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.d(TAG, "📱 PrayerNotificationReceiver triggered")
            
            val prayerName = intent.getStringExtra(PrayerNotificationWorker.PRAYER_NAME_KEY) ?: "Prayer"
            val prayerTime = intent.getStringExtra(PrayerNotificationWorker.PRAYER_TIME_KEY) ?: ""
            val notificationType = intent.getStringExtra(PrayerNotificationWorker.NOTIFICATION_TYPE_KEY) 
                ?: PrayerNotificationWorker.TYPE_PRAYER_TIME
            
            Log.d(TAG, "Processing: $prayerName at $prayerTime (type: $notificationType)")
            
            // Convert to WorkManager job for better reliability
            scheduleWorkManagerJob(context, prayerName, prayerTime, notificationType)
            
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
}

