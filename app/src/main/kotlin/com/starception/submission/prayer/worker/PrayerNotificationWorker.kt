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
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.starception.submission.R
import com.starception.submission.feature.prayertimes.getPrayerDisplayName
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
        private const val REMINDER_CHANNEL_ID = "prayer_reminder_notifications"
        private const val REMINDER_CHANNEL_NAME = "Prayer Reminders"

        // Input data keys
        const val PRAYER_NAME_KEY = "prayer_name"
        const val PRAYER_TIME_KEY = "prayer_time"
        const val NOTIFICATION_TYPE_KEY = "notification_type"
        const val PRIOR_MINUTES_KEY = "prior_minutes"

        // Notification types
        const val TYPE_PRAYER_TIME = "prayer_time"
        const val TYPE_REMINDER = "reminder"

        // Default prior minutes (fallback)
        const val DEFAULT_PRIOR_MINUTES = 10
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🕌 PrayerNotificationWorker started")

            val prayerName = inputData.getString(PRAYER_NAME_KEY) ?: "Prayer"
            val prayerTime = inputData.getString(PRAYER_TIME_KEY) ?: ""
            val notificationType = inputData.getString(NOTIFICATION_TYPE_KEY) ?: TYPE_PRAYER_TIME

            // Get prior minutes from input data, or fetch from settings
            val priorMinutes = inputData.getInt(PRIOR_MINUTES_KEY, -1).let { inputMinutes ->
                if (inputMinutes > 0) {
                    inputMinutes
                } else {
                    // Fetch from notification preferences
                    prayerSettingsRepository.getNotificationPreferences()
                        .getPriorMinutesForPrayer(prayerName)
                }
            }

            Log.d(TAG, "Processing notification: $prayerName at $prayerTime (type: $notificationType, prior: ${priorMinutes}min)")

            // Create notification channel with dynamic description
            createNotificationChannel(priorMinutes)

            // Show the notification
            when (notificationType) {
                TYPE_PRAYER_TIME -> showPrayerTimeNotification(prayerName, prayerTime)
                TYPE_REMINDER -> showPrayerReminderNotification(prayerName, prayerTime, priorMinutes)
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

    private fun createNotificationChannel(priorMinutes: Int = DEFAULT_PRIOR_MINUTES) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Channel 1: Prayer Time Notifications with Adhan sound
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            // Get the URI for the adhan sound
            val adhanSoundUri = Uri.parse("android.resource://${applicationContext.packageName}/${R.raw.short_adhan}")

            val prayerChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prayer time notifications with Adhan sound"
                enableLights(true)
                enableVibration(true)
                setSound(adhanSoundUri, audioAttributes)  // Adhan sound for prayer time
            }

            // Channel 2: Reminder Notifications (no Adhan, just vibration)
            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders before prayer times (configurable timing)"
                enableLights(true)
                enableVibration(true)
                setSound(null, null)  // No sound for reminders
            }

            // Create both channels
            notificationManager.createNotificationChannel(prayerChannel)
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }

    private fun showPrayerTimeNotification(prayerName: String, prayerTime: String) {
        // Play Adhan sound
        playAdhanSound()

        // On Fridays the midday (Dhuhr) prayer is Jumu'ah — show that name to the user.
        // The notification fires on the prayer's own day, so today's date is the right key.
        val displayName = getPrayerDisplayName(prayerName, LocalDate.now())

        // Create large icon from app launcher icon
        val largeIcon = ContextCompat.getDrawable(applicationContext, R.mipmap.ic_launcher)?.toBitmap()

        // Get the URI for the adhan sound
        val adhanSoundUri = Uri.parse("android.resource://${applicationContext.packageName}/${R.raw.short_adhan}")

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
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
            .setSound(adhanSoundUri) // Set custom sound for the notification
            .build()

        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        Log.d(TAG, "📱 Posted prayer time notification with Adhan: $prayerName at $prayerTime")
    }

    private fun playAdhanSound() {
        try {
            val adhanUri = Uri.parse("android.resource://${applicationContext.packageName}/${R.raw.short_adhan}")
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setDataSource(applicationContext, adhanUri)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    Log.d(TAG, "🔊 Playing Adhan sound")
                }
                setOnCompletionListener { mp ->
                    mp.release()
                    Log.d(TAG, "✅ Adhan sound playback completed")
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "❌ Error playing Adhan: what=$what, extra=$extra")
                    mp.release()
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to play Adhan sound", e)
        }
    }

    private fun showPrayerReminderNotification(prayerName: String, prayerTime: String, priorMinutes: Int = DEFAULT_PRIOR_MINUTES) {
        // On Fridays the midday (Dhuhr) reminder is for Jumu'ah — show that name to the user.
        val displayName = getPrayerDisplayName(prayerName, LocalDate.now())

        // Create large icon from app launcher icon
        val largeIcon = ContextCompat.getDrawable(applicationContext, R.mipmap.ic_launcher)?.toBitmap()

        val notification = NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL_ID)  // Use reminder channel
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
            // No need to set defaults - channel handles vibration, no sound
            .build()

        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)

        Log.d(TAG, "📱 Posted prayer reminder notification: $prayerName at $prayerTime ($priorMinutes min before)")
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
