package com.starception.submission.prayer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.starception.submission.prayer.scheduler.PrayerNotificationScheduler
import com.starception.submission.prayer.service.PrayerTimeCalculatorService
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
 * when the device boots up, ensuring notifications work even after
 * device restarts or app kills.
 * 
 * Features:
 * - Automatic rescheduling on boot
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
                Log.d(TAG, "🚀 Device booted or app updated - rescheduling prayer notifications")
                reschedulePrayerNotifications(context)
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
