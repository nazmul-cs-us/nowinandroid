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
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.work.*
import com.starception.submission.MainActivity
import com.starception.submission.R
import com.starception.submission.feature.prayertimes.getPrayerDisplayName
import com.starception.submission.feature.prayertimes.weather.getPrayerWeatherInsightForNotification
import com.starception.submission.feature.prayertimes.weather.prayerWeatherNotificationBitmap
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.service.AdhanPlaybackService
import com.starception.submission.prayer.silent.PrayerSilentModeController
import com.starception.submission.prayer.util.FileLogger
import com.starception.submission.prayer.worker.PrayerNotificationWorker
import com.starception.submission.sync.workers.DelegatingWorker
import com.starception.submission.sync.workers.delegatedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            handleNotification(context.applicationContext, intent)
            pendingResult.finish()
        }
    }

    private suspend fun handleNotification(context: Context, intent: Intent) {
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
                    "source" to "AlarmManager",
                ),
            )

            // The alarm fires whenever EITHER notification or silent-mode is enabled.
            // Only show the notification banner if this prayer's notification toggle is on.
            val preferences = readNotificationPreferences(context)
            if (isNotificationEnabledForPrayer(context, prayerName)) {
                showPrayerNotification(context, prayerName, prayerTime, notificationType, priorMinutes)
                // The adhan is played by AdhanPlaybackService (volume-controlled,
                // mutable via volume keys / Mute action). Unreadable preferences
                // default to PLAYING — the legacy behaviour before the toggle
                // existed — so a storage hiccup can never silence the adhan.
                val adhanWanted = notificationType == PrayerNotificationWorker.TYPE_PRAYER_TIME &&
                    (preferences?.isAdhanEnabledForPrayer(prayerName) ?: true)
                if (adhanWanted) {
                    val volumePercent = preferences?.getAdhanVolumeForPrayer(prayerName) ?: 100
                    FileLogger.log(
                        "INFO", "PrayerNotificationReceiver",
                        "ADHAN_TRIGGER: starting playback service for $prayerName at $volumePercent%",
                    )
                    AdhanPlaybackService.startOrFallback(
                        context = context,
                        prayerName = prayerName,
                        prayerTime = prayerTime,
                        volumePercent = volumePercent,
                    )
                } else {
                    FileLogger.log(
                        "INFO", "PrayerNotificationReceiver",
                        "ADHAN_SKIPPED: adhan toggle off for $prayerName",
                    )
                    Log.d(TAG, "🔇 Adhan off for $prayerName — playing silent notification only")
                }
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
        return readNotificationPreferences(context)?.isNotificationEnabledForPrayer(prayerName) ?: true
    }

    private fun readNotificationPreferences(context: Context): PrayerNotificationPreferences? {
        return try {
            val prefs = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
            val json = prefs.getString("notification_preferences_json", null) ?: return null
            Json { ignoreUnknownKeys = true }.decodeFromString(
                PrayerNotificationPreferences.serializer(),
                json,
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading notification preferences", e)
            null
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
        // Keep the phone audible during the configured "Go to Mosque" phase, then
        // enable DND for the separate silent-during-prayer duration.
        PrayerSilentModeController(context.applicationContext).scheduleStartAfter(
            prayerName = prayerName,
            delayMinutes = preferences.getGoToMosqueDurationForPrayer(prayerName),
            durationMinutes = preferences.silentDuringPrayerMinutes,
        )
    }

    private fun scheduleWorkManagerJob(
        context: Context,
        prayerName: String,
        prayerTime: String,
        notificationType: String,
    ) {
        val inputData = Data.Builder()
            .putAll(PrayerNotificationWorker::class.delegatedData())
            .putString(PrayerNotificationWorker.PRAYER_NAME_KEY, prayerName)
            .putString(PrayerNotificationWorker.PRAYER_TIME_KEY, prayerTime)
            .putString(PrayerNotificationWorker.NOTIFICATION_TYPE_KEY, notificationType)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DelegatingWorker>()
            .setInputData(inputData)
            .setInitialDelay(0, TimeUnit.MILLISECONDS) // Execute immediately
            .addTag("prayer_notification")
            .addTag("prayer_$prayerName")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(false)
                    .setRequiresCharging(false)
                    .build(),
            )
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(workRequest)

        Log.d(TAG, "✅ Scheduled WorkManager job for $prayerName")
    }

    private suspend fun showPrayerNotification(
        context: Context,
        prayerName: String,
        prayerTime: String,
        notificationType: String,
        priorMinutes: Int = PrayerNotificationWorker.DEFAULT_PRIOR_MINUTES,
    ) {
        try {
            // Create notification channel
            createNotificationChannel(context)

            // On Fridays the midday (Dhuhr) prayer is Jumu'ah — show that name to the user.
            // The notification fires on the prayer's own day, so today's date is the right key.
            val displayName = getPrayerDisplayName(prayerName, LocalDate.now())
            val weatherInsight = getPrayerWeatherInsightForNotification(
                context = context,
                prayerName = displayName,
                prayerTimeText = prayerTime,
            )
            val notificationId = if (notificationType == PrayerNotificationWorker.TYPE_PRAYER_TIME) 2001 else 2002

            // Create PendingIntent to open app when notification is tapped
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 100, // Unique request code
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            // Create large icon from app launcher icon
            val largeIcon = prayerWeatherNotificationBitmap(
                context = context,
                summary = weatherInsight?.summary,
            ) ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap()

            val notification = if (notificationType == PrayerNotificationWorker.TYPE_PRAYER_TIME) {
                // Prayer time notification - when it's actually prayer time
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
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("It's time for $displayName")
                    .setContentText(compactContent)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(expandedContent))
                    .setSmallIcon(R.drawable.ic_prayer)
                    .setLargeIcon(largeIcon)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    // The adhan plays via AdhanPlaybackService; the channel and
                    // the notification itself stay silent on every Android
                    // version so the two never double up.
                    .setSound(null)
                    .setDefaults(
                        NotificationCompat.DEFAULT_VIBRATE or
                            NotificationCompat.DEFAULT_LIGHTS,
                    )
                    .setContentIntent(contentPendingIntent) // Open app when tapped
                    .build()
            } else {
                // Prayer reminder notification - X minutes before prayer (user configurable)
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
                NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                    .setContentTitle("$displayName in $reminderLeadTime")
                    .setContentText(compactContent)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(expandedContent))
                    .setSmallIcon(R.drawable.ic_prayer)
                    .setLargeIcon(largeIcon)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                    .setContentIntent(contentPendingIntent) // Open app when tapped
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
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // The adhan now plays through AdhanPlaybackService, so the prayer
            // channel itself is silent. Channel settings are immutable after
            // creation, so existing installs — whose legacy channel has the
            // adhan hard-wired as its sound — are migrated to a fresh silent
            // channel ID and the legacy loud channel is deleted.
            notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)

            val prayerChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Prayer time notifications (adhan plays separately)"
                enableLights(true)
                enableVibration(true)
                setSound(null, null)
            }
            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                REMINDER_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminders before prayer times"
                enableLights(true)
                enableVibration(true)
                setSound(null, null)
            }

            notificationManager.createNotificationChannels(
                listOf(prayerChannel, reminderChannel),
            )
        }
    }

    companion object {
        private const val TAG = "PrayerNotificationReceiver"

        // v2: silent channel — the adhan plays through AdhanPlaybackService
        private const val CHANNEL_ID = "prayer_scheduled_notifications_v2"
        private const val LEGACY_CHANNEL_ID = "prayer_scheduled_notifications"
        private const val CHANNEL_NAME = "Scheduled Prayer Notifications"
        private const val REMINDER_CHANNEL_ID = "prayer_reminder_notifications"
        private const val REMINDER_CHANNEL_NAME = "Prayer Reminders"
    }
}
