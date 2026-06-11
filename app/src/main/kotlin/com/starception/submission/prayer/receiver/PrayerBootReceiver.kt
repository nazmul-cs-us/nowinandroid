package com.starception.submission.prayer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.starception.submission.prayer.scheduler.PrayerNotificationScheduler
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
import com.starception.submission.util.ActivityTracker
import com.starception.submission.services.PrayerNotificationService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Prayer Boot Receiver
 *
 * This receiver automatically reschedules all prayer notifications
 * and restarts activity detection when the device boots up, ensuring
 * notifications and activity tracking work even after device restarts or app kills.
 *
 * Features:
 * - Automatic rescheduling on boot with real calculated prayer times
 * - Restart activity detection service
 * - Schedules all 5 daily prayers with per-prayer reminder offsets
 * - Handles timezone changes
 */
@AndroidEntryPoint
class PrayerBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prayerTimeCalculatorService: PrayerTimeCalculatorService

    @Inject
    lateinit var prayerSettingsRepository: com.starception.submission.prayer.repository.PrayerSettingsRepository

    @Inject
    lateinit var locationService: com.starception.submission.prayer.service.EnhancedLocationService

    companion object {
        private const val TAG = "PrayerBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "🚀 Device booted or app updated - rescheduling prayer notifications and restarting activity detection")
                // goAsync keeps the process alive until scheduling finishes —
                // otherwise the system may kill us right after onReceive returns
                // and the alarms are never registered.
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        reschedulePrayerNotifications(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
                restartActivityDetection(context)
            }
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.d(TAG, "🌍 Timezone changed - recalculating prayer times and rescheduling notifications")
                handleTimezoneOrTimeChangeAsync(context)
            }
            Intent.ACTION_TIME_CHANGED -> {
                Log.d(TAG, "⏰ System time changed - recalculating prayer times and rescheduling notifications")
                handleTimezoneOrTimeChangeAsync(context)
            }
        }
    }

    /**
     * Restart activity detection after boot
     * This ensures travel dua and activity tracking work even after device restart
     */
    private fun restartActivityDetection(context: Context) {
        try {
            Log.d(TAG, "🏃 Restarting activity detection after boot...")

            // Start the prayer notification service (which also starts activity detection).
            // Note: prayer alarms are scheduled independently above, so notifications
            // still fire even if the OS rejects this foreground-service start.
            val serviceIntent = Intent(context, PrayerNotificationService::class.java)
            context.startForegroundService(serviceIntent)

            // Also directly initialize ActivityTracker
            ActivityTracker.initialize(context, startDetectionNow = true)

            Log.d(TAG, "✅ Activity detection restarted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to restart activity detection: ${e.message}", e)
        }
    }

    /**
     * Handle timezone or time changes
     * Recalculates prayer times and reschedules notifications
     */
    private fun handleTimezoneOrTimeChangeAsync(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newTimezone = java.util.TimeZone.getDefault()
                Log.d(TAG, "🌍 New timezone: ${newTimezone.id} (${newTimezone.displayName})")

                // Step 1: Re-initialize prayer settings based on new location/country
                Log.d(TAG, "📍 Force re-initializing prayer calculation settings for new timezone...")
                prayerSettingsRepository.forceReinitializeForTimezoneChange(newTimezone.id)
                Log.d(TAG, "✅ Prayer settings force re-initialized for ${newTimezone.id}")

                // Step 2: Recalculate and reschedule prayer times with new settings
                // (reschedulePrayerNotifications cancels existing ones first)
                reschedulePrayerNotifications(context)

                // Send broadcast to update UI
                val updateIntent = Intent("com.starception.submission.PRAYER_TIMES_UPDATED")
                updateIntent.putExtra("reason", "timezone_changed")
                updateIntent.putExtra("timezone", newTimezone.id)
                context.sendBroadcast(updateIntent)

                Log.d(TAG, "✅ Prayer times and settings recalculated for new timezone: ${newTimezone.id}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to handle timezone change: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Recalculate today's real prayer times from cached location/settings and
     * schedule alarms for each prayer, mirroring the in-app scheduling path
     * (PrayerNotificationServiceManager).
     */
    private suspend fun reschedulePrayerNotifications(context: Context) {
        try {
            Log.d(TAG, "📅 Rescheduling prayer notifications with real calculated times")

            // Cancel any existing notifications
            PrayerNotificationScheduler.cancelAllPrayerNotifications(context)

            // getLoadedSettings waits for the repository's async load — in a
            // cold-started broadcast process the plain getter races it and
            // returns defaults with a null location.
            @Suppress("DEPRECATION")
            val settings = prayerSettingsRepository.getLoadedSettings()
            var location = settings.location
            if (location == null) {
                Log.w(TAG, "⚠️ No persisted location - attempting to fetch current location")
                location = try {
                    kotlinx.coroutines.withTimeoutOrNull(5000L) {
                        locationService.getBestAvailableLocation().getOrNull()
                    }?.let { fetched ->
                        val offsetHours = java.util.TimeZone.getDefault()
                            .getOffset(System.currentTimeMillis()) / (1000.0 * 60 * 60)
                        locationService.getLocationDetails(fetched).copy(timeZoneOffset = offsetHours)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Location fetch failed: ${e.message}", e)
                    null
                }
            }
            if (location == null) {
                Log.w(TAG, "⚠️ No location available - cannot reschedule prayer notifications")
                return
            }

            val dayPrayerTimes = prayerTimeCalculatorService.calculatePrayerTimes(
                date = LocalDate.now(),
                location = location,
                settings = settings,
            )
            if (dayPrayerTimes == null) {
                Log.e(TAG, "❌ Prayer time calculation failed - cannot reschedule")
                return
            }

            val formatter = DateTimeFormatter.ofPattern("h:mm a")
            val offsets = settings.timeOffsets
            val notificationPrefs = prayerSettingsRepository.getNotificationPreferences()

            val prayerTimes = mapOf(
                "Fajr" to applyOffsetToTime(dayPrayerTimes.fajr, offsets.fajr).format(formatter),
                "Dhuhr" to applyOffsetToTime(dayPrayerTimes.dhuhr, offsets.dhuhr).format(formatter),
                "Asr" to applyOffsetToTime(dayPrayerTimes.asr, offsets.asr).format(formatter),
                "Maghrib" to applyOffsetToTime(dayPrayerTimes.maghrib, offsets.maghrib).format(formatter),
                "Isha" to applyOffsetToTime(dayPrayerTimes.isha, offsets.isha).format(formatter),
            )

            prayerTimes.forEach { (prayerName, prayerTime) ->
                val reminderMinutes = notificationPrefs.getPriorMinutesForPrayer(prayerName)
                Log.d(TAG, "⏰ Scheduling $prayerName at $prayerTime with $reminderMinutes min prior reminder")
                PrayerNotificationScheduler.schedulePrayerNotification(
                    context = context,
                    prayerName = prayerName,
                    prayerTime = prayerTime,
                    reminderMinutes = reminderMinutes,
                )
            }

            Log.d(TAG, "✅ Successfully rescheduled ${prayerTimes.size} prayer notifications")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to reschedule prayer notifications", e)
        }
    }

    private fun applyOffsetToTime(baseTime: LocalTime, offsetMinutes: Int): LocalTime {
        if (offsetMinutes == 0) return baseTime
        return LocalDateTime.of(LocalDate.now(), baseTime)
            .plusMinutes(offsetMinutes.toLong())
            .toLocalTime()
    }
}
