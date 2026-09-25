/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import androidx.work.WorkerParameters
import com.starception.submission.R
import com.starception.submission.feature.prayertimes.getPrayerDisplayName
import com.starception.submission.feature.prayertimes.weather.getPrayerWeatherInsightForNotification
import com.starception.submission.feature.prayertimes.weather.prayerWeatherNotificationBitmap
import com.starception.submission.prayer.repository.PrayerSettingsRepository
import com.starception.submission.prayer.service.AdhanPlaybackService
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import com.starception.submission.prayer.silent.PrayerSilentModeController
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

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
    private val prayerSettingsRepository: PrayerSettingsRepository,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "PrayerNotificationWorker"
        private const val NOTIFICATION_ID = 2001

        // v2: silent channel — the adhan plays through AdhanPlaybackService
        private const val CHANNEL_ID = "prayer_scheduled_notifications_v2"
        private const val LEGACY_CHANNEL_ID = "prayer_scheduled_notifications"
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

            // Work may have been queued hours before the user changed the bell
            // toggle. Re-check persisted preferences at execution time before
            // creating a channel, posting a notification, or starting Adhan.
            // The storage-backed read also handles a Worker starting in a fresh
            // process before the repository's in-memory flows have initialized.
            val notificationPreferences =
                prayerSettingsRepository.getNotificationPreferencesFromStorage()
            if (notificationType == TYPE_PRAYER_TIME &&
                notificationPreferences.silentDuringPrayerEnabled
            ) {
                // WorkManager is the fallback when the prayer-boundary exact alarm is
                // unavailable. It must preserve the configured go-to-mosque phase too.
                PrayerSilentModeController(applicationContext).scheduleStartAfter(
                    prayerName = prayerName,
                    delayMinutes = notificationPreferences
                        .getGoToMosqueDurationForPrayer(prayerName),
                    durationMinutes = notificationPreferences.silentDuringPrayerMinutes,
                )
            }
            if (!notificationPreferences.isNotificationEnabledForPrayer(prayerName)) {
                Log.i(
                    TAG,
                    "🔕 $prayerName notification disabled — skipping notification and Adhan",
                )
                return@withContext Result.success()
            }

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

            // The adhan is played by AdhanPlaybackService (volume-controlled,
            // mutable via volume keys / Mute action) rather than the old
            // notification-channel sound, which could do neither.
            if (notificationType == TYPE_PRAYER_TIME &&
                notificationPreferences.isAdhanEnabledForPrayer(prayerName)
            ) {
                AdhanPlaybackService.start(
                    context = applicationContext,
                    prayerName = prayerName,
                    volumePercent = notificationPreferences.getAdhanVolumeForPrayer(prayerName),
                )
            } else {
                Log.i(TAG, "🔇 Adhan disabled for $prayerName — notification only")
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

            // The adhan now plays through AdhanPlaybackService, so the prayer
            // channel is silent. Channel settings are immutable after creation,
            // so existing installs — whose legacy channel has the adhan
            // hard-wired as its sound — are migrated to a fresh silent channel
            // and the legacy loud channel is deleted.
            notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)

            val prayerChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Prayer time notifications (adhan plays separately)"
                enableLights(true)
                enableVibration(true)
                setSound(null, null) // Silent: AdhanPlaybackService owns the audio
            }

            // Channel 2: Reminder Notifications (no Adhan, just vibration)
            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminders before prayer times (configurable timing)"
                enableLights(true)
                enableVibration(true)
                setSound(null, null) // No sound for reminders
            }

            // Create both channels
            notificationManager.createNotificationChannel(prayerChannel)
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }

    private suspend fun showPrayerTimeNotification(prayerName: String, prayerTime: String) {
        // On Fridays the midday (Dhuhr) prayer is Jumu'ah — show that name to the user.
        // The notification fires on the prayer's own day, so today's date is the right key.
        val displayName = getPrayerDisplayName(prayerName, LocalDate.now())
        val weatherInsight = getPrayerWeatherInsightForNotification(
            context = applicationContext,
            prayerName = displayName,
            prayerTimeText = prayerTime,
        )

        // Create large icon from app launcher icon
        val largeIcon = prayerWeatherNotificationBitmap(
            context = applicationContext,
            summary = weatherInsight?.summary,
        ) ?: ContextCompat.getDrawable(applicationContext, R.mipmap.ic_launcher)?.toBitmap()

        val compactWeather = weatherInsight?.summary?.substringBefore(" · ")
        val compactContent = listOfNotNull(
            "$displayName began at $prayerTime",
            compactWeather,
        ).joinToString(" · ")
        val expandedContent = buildList {
            add("$displayName began at $prayerTime.")
            weatherInsight?.let { add("${it.summary} — ${it.advice}") }
            add("")
            add("اَللّٰهُمَّ تَقَبَّلْ مِنَّا")
            add("O Allah, accept from us.")
        }.joinToString("\n")

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("It's time for $displayName")
            .setContentText(compactContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedContent))
            .setSmallIcon(R.drawable.ic_prayer)
            .setLargeIcon(largeIcon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(false)
            // The adhan plays via AdhanPlaybackService; the notification stays
            // silent on every Android version so the two never double up.
            .setSound(null)
            .build()

        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        Log.d(TAG, "📱 Posted prayer time notification with Adhan: $prayerName at $prayerTime")
    }

    private suspend fun showPrayerReminderNotification(prayerName: String, prayerTime: String, priorMinutes: Int = DEFAULT_PRIOR_MINUTES) {
        // On Fridays the midday (Dhuhr) reminder is for Jumu'ah — show that name to the user.
        val displayName = getPrayerDisplayName(prayerName, LocalDate.now())
        val weatherInsight = getPrayerWeatherInsightForNotification(
            context = applicationContext,
            prayerName = displayName,
            prayerTimeText = prayerTime,
        )

        // Create large icon from app launcher icon
        val largeIcon = prayerWeatherNotificationBitmap(
            context = applicationContext,
            summary = weatherInsight?.summary,
        ) ?: ContextCompat.getDrawable(applicationContext, R.mipmap.ic_launcher)?.toBitmap()

        val reminderLeadTime = if (priorMinutes == 1) "1 minute" else "$priorMinutes minutes"
        val compactWeather = weatherInsight?.summary?.substringBefore(" · ")
        val compactContent = listOfNotNull(
            "Prayer begins at $prayerTime",
            compactWeather,
        ).joinToString(" · ")
        val expandedContent = buildList {
            add("Prayer begins at $prayerTime.")
            weatherInsight?.let { add("${it.summary} — ${it.advice}") }
            add("Take a moment to prepare.")
        }.joinToString("\n")

        val notification = NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL_ID) // Use reminder channel
            .setContentTitle("$displayName in $reminderLeadTime")
            .setContentText(compactContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedContent))
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
}
