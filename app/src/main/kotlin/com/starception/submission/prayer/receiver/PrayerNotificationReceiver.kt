package com.starception.submission.prayer.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.starception.submission.MainActivity
import com.starception.submission.feature.prayertimes.getPrayerDisplayName
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.work.*
import com.starception.submission.R
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.silent.PrayerSilentModeController
import com.starception.submission.prayer.worker.PrayerNotificationWorker
import com.starception.submission.prayer.util.FileLogger
import kotlinx.serialization.json.Json
import java.time.LocalDate
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
        val receiveTime = System.currentTimeMillis()
        try {
            Log.d(TAG, "📱 PrayerNotificationReceiver triggered")

            val prayerName = intent.getStringExtra(PrayerNotificationWorker.PRAYER_NAME_KEY) ?: "Prayer"
            val prayerTime = intent.getStringExtra(PrayerNotificationWorker.PRAYER_TIME_KEY) ?: ""
            val notificationType = intent.getStringExtra(PrayerNotificationWorker.NOTIFICATION_TYPE_KEY)
                ?: PrayerNotificationWorker.TYPE_PRAYER_TIME
            val priorMinutes = intent.getIntExtra(PrayerNotificationWorker.PRIOR_MINUTES_KEY, PrayerNotificationWorker.DEFAULT_PRIOR_MINUTES)

            Log.d(TAG, "Processing: $prayerName at $prayerTime (type: $notificationType, priorMinutes: $priorMinutes)")

            // Log adhan fired with FileLogger
            FileLogger.logAdhanFired(
                prayerName = prayerName,
                actualTimeMillis = receiveTime,
                details = mapOf(
                    "notificationType" to notificationType,
                    "scheduledPrayerTime" to prayerTime,
                    "source" to "AlarmManager"
                )
            )

            // The alarm fires whenever EITHER notification or silent-mode is enabled.
            // Only show the notification banner if this prayer's notification toggle is on.
            if (isNotificationEnabledForPrayer(context, prayerName)) {
                showPrayerNotification(context, prayerName, prayerTime, notificationType, priorMinutes)
            } else {
                Log.d(TAG, "🔕 Notifications off for $prayerName — skipping banner (silent mode may still fire)")
            }

            if (notificationType == PrayerNotificationWorker.TYPE_PRAYER_TIME) {
                maybeEnableSilentMode(context, prayerName)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ PrayerNotificationReceiver failed", e)
            FileLogger.e(TAG, "PrayerNotificationReceiver failed", e)
        }
    }
    
    private fun isNotificationEnabledForPrayer(context: Context, prayerName: String): Boolean {
        return try {
            val prefs = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
            val json = prefs.getString("notification_preferences_json", null) ?: return true
            val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(
                PrayerNotificationPreferences.serializer(),
                json,
            )
            parsed.isNotificationEnabledForPrayer(prayerName)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading per-prayer notification pref", e)
            true
        }
    }

    private fun maybeEnableSilentMode(context: Context, prayerName: String) {
        val prefs = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
        val json = prefs.getString("notification_preferences_json", null) ?: return
        val preferences = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString(
                PrayerNotificationPreferences.serializer(),
                json,
            )
        }.getOrNull() ?: return
        if (!preferences.silentDuringPrayerEnabled) return
        // "Go to Mosque" phase delays the silent window so the user has time to
        // travel before their phone goes silent. Once the delay elapses, the
        // scheduled StartPrayerSilentReceiver flips DND on for the configured
        // silent-during-prayer duration.
        val delayMin = preferences.goToMosqueDurationFor(prayerName)
        PrayerSilentModeController(context.applicationContext).scheduleStartAfter(
            prayerName = prayerName,
            delayMinutes = delayMin,
            durationMinutes = preferences.silentDuringPrayerMinutes,
        )
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
        notificationType: String,
        priorMinutes: Int = PrayerNotificationWorker.DEFAULT_PRIOR_MINUTES
    ) {
        try {
            // Create notification channel
            createNotificationChannel(context)

            // On Fridays the midday (Dhuhr) prayer is Jumu'ah — show that name to the user.
            // The notification fires on the prayer's own day, so today's date is the right key.
            val displayName = getPrayerDisplayName(prayerName, LocalDate.now())

            val notificationId = if (notificationType == PrayerNotificationWorker.TYPE_PRAYER_TIME) 2001 else 2002

            // Create PendingIntent to open app when notification is tapped
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 100,  // Unique request code
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create large icon from app launcher icon
            val largeIcon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap()

            val notification = if (notificationType == PrayerNotificationWorker.TYPE_PRAYER_TIME) {
                // Prayer time notification - when it's actually prayer time
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("$displayName Prayer")
                    .setContentText("It's time for $displayName • $prayerTime")
                    .setStyle(NotificationCompat.BigTextStyle()
                        .bigText("$displayName Prayer Time\n\n" +
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
                    .setContentIntent(contentPendingIntent)  // Open app when tapped
                    .build()
            } else {
                // Prayer reminder notification - X minutes before prayer (user configurable)
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("$displayName in $priorMinutes min")
                    .setContentText("Starts at $prayerTime")
                    .setStyle(NotificationCompat.BigTextStyle()
                        .bigText("$displayName Prayer in $priorMinutes minutes\n\n" +
                                "Time: $prayerTime\n"))
                    .setSmallIcon(R.drawable.ic_prayer)
                    .setLargeIcon(largeIcon)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                    .setContentIntent(contentPendingIntent)  // Open app when tapped
                    .build()
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, notification)

            Log.d(TAG, "📱 Posted prayer notification: $prayerName ($notificationType) at $prayerTime (priorMinutes: $priorMinutes)")

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

