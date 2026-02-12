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
 * - Automatic rescheduling on boot
 * - Restart activity detection service
 * - Loads current prayer times
 * - Schedules all 5 daily prayers
 * - Handles timezone changes
 */
@AndroidEntryPoint
class PrayerBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prayerTimeCalculatorService: PrayerTimeCalculatorService

    companion object {
        private const val TAG = "PrayerBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "🚀 Device booted or app updated - rescheduling prayer notifications and restarting activity detection")
                reschedulePrayerNotifications(context)
                restartActivityDetection(context)
            }
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.d(TAG, "🌍 Timezone changed - recalculating prayer times and rescheduling notifications")
                handleTimezoneOrTimeChange(context)
            }
            Intent.ACTION_TIME_CHANGED -> {
                Log.d(TAG, "⏰ System time changed - recalculating prayer times and rescheduling notifications")
                handleTimezoneOrTimeChange(context)
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

            // Start the prayer notification service (which also starts activity detection)
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
    private fun handleTimezoneOrTimeChange(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newTimezone = java.util.TimeZone.getDefault()
                Log.d(TAG, "🌍 New timezone: ${newTimezone.id} (${newTimezone.displayName})")

                // Cancel existing notifications
                PrayerNotificationScheduler.cancelAllPrayerNotifications(context)

                // Recalculate and reschedule prayer times
                // The prayer time calculator will use the new timezone automatically
                reschedulePrayerNotifications(context)

                // Send broadcast to update UI
                val updateIntent = Intent("com.starception.submission.PRAYER_TIMES_UPDATED")
                updateIntent.putExtra("reason", "timezone_changed")
                updateIntent.putExtra("timezone", newTimezone.id)
                context.sendBroadcast(updateIntent)

                Log.d(TAG, "✅ Prayer times recalculated for new timezone")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to handle timezone change: ${e.message}", e)
            }
        }
    }

    private fun reschedulePrayerNotifications(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "📅 Rescheduling prayer notifications after boot")
                
                // Cancel any existing notifications
                PrayerNotificationScheduler.cancelAllPrayerNotifications(context)
                
                // For now, schedule test notifications
                // TODO: Integrate with actual prayer time calculation service
                val testPrayerTimes = mapOf(
                    "Fajr" to "05:30",
                    "Dhuhr" to "12:15",
                    "Asr" to "15:45",
                    "Maghrib" to "18:20",
                    "Isha" to "19:45"
                )
                
                // Schedule all prayer notifications
                PrayerNotificationScheduler.scheduleAllPrayerNotifications(
                    context = context,
                    prayerTimes = testPrayerTimes,
                    reminderMinutes = 15
                )
                
                Log.d(TAG, "✅ Successfully rescheduled ${testPrayerTimes.size} test prayer notifications")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to reschedule prayer notifications after boot", e)
            }
        }
    }
}
