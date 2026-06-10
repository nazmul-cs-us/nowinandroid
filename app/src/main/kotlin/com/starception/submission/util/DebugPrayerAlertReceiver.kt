/*
 * Debug-only BroadcastReceiver to simulate a prayer alert in PullToSyncContainer.
 * Lets us verify the stacked layout when both media (driving Surah) and a prayer
 * alert (Go to mosque) are live without waiting for a real prayer window.
 *
 * Usage:
 *   Show a "Go to mosque" alert for Maghrib, 18 min left of a 30 min window:
 *     adb shell am broadcast -a com.starception.submission.DEBUG_SIMULATE_PRAYER_ALERT \
 *       --es prayer Maghrib --es phase GO_TO_MOSQUE --ei countdown 18 --ei total 30 \
 *       -n com.starception.submission.demo.debug/com.starception.submission.util.DebugPrayerAlertReceiver
 *
 *   Show "Fajr in 15m" countdown:
 *     adb shell am broadcast -a com.starception.submission.DEBUG_SIMULATE_PRAYER_ALERT \
 *       --es prayer Fajr --es phase BEFORE_PRAYER --ei countdown 15 --ei total 30 \
 *       -n com.starception.submission.demo.debug/com.starception.submission.util.DebugPrayerAlertReceiver
 *
 *   Clear the simulated alert:
 *     adb shell am broadcast -a com.starception.submission.DEBUG_SIMULATE_PRAYER_ALERT --ez clear true \
 *       -n com.starception.submission.demo.debug/com.starception.submission.util.DebugPrayerAlertReceiver
 */
package com.starception.submission.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.starception.submission.feature.prayertimes.wobble.AlertPhase
import com.starception.submission.feature.prayertimes.wobble.PrayerAlertState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide singleton holding the most recent debug-injected prayer alert.
 * MainActivityViewModel collects this and forwards into its own _prayerAlertState
 * so the rest of the UI doesn't need to know about the debug path.
 */
object DebugPrayerAlertBus {
    val state = MutableStateFlow<PrayerAlertState?>(null)
}

class DebugPrayerAlertReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DebugPrayerAlert"
        const val ACTION = "com.starception.submission.DEBUG_SIMULATE_PRAYER_ALERT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        if (intent.getBooleanExtra("clear", false)) {
            Log.i(TAG, "🧪 CLEAR simulated prayer alert")
            DebugPrayerAlertBus.state.value = null
            return
        }

        val prayer = intent.getStringExtra("prayer") ?: "Maghrib"
        val phaseName = intent.getStringExtra("phase") ?: AlertPhase.GO_TO_MOSQUE.name
        val phase = runCatching { AlertPhase.valueOf(phaseName) }.getOrDefault(AlertPhase.GO_TO_MOSQUE)
        val countdown = intent.getIntExtra("countdown", 15)
        val total = intent.getIntExtra("total", 30)

        val displayText = when (phase) {
            AlertPhase.BEFORE_PRAYER -> "$prayer in ${countdown}m"
            AlertPhase.GO_TO_MOSQUE -> "Go now to mosque, ${countdown}m left"
            AlertPhase.NONE -> "$prayer alert"
        }

        val state = PrayerAlertState(
            isActive = true,
            prayerName = prayer,
            phase = phase,
            countdownMinutes = countdown,
            totalMinutes = total,
            displayText = displayText,
        )
        Log.i(TAG, "🧪 SIMULATE prayer alert: $state")
        DebugPrayerAlertBus.state.value = state
    }
}
