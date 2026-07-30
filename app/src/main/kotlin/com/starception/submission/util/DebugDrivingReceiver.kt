/*
 * Copyright 2024 Starception
 *
 * Debug-only BroadcastReceiver to simulate driving detection via adb.
 *
 * Usage:
 *   Simulate driving (with cooldown/delay):
 *     adb shell am broadcast -a com.starception.submission.DEBUG_SIMULATE_DRIVING --es activity Driving -n com.starception.submission.demo.debug/com.starception.submission.util.DebugDrivingReceiver
 *
 *   Force-trigger full audio chain immediately (bypasses cooldowns):
 *     adb shell am broadcast -a com.starception.submission.DEBUG_SIMULATE_DRIVING --ez force true -n com.starception.submission.demo.debug/com.starception.submission.util.DebugDrivingReceiver
 *
 *   Stop driving:
 *     adb shell am broadcast -a com.starception.submission.DEBUG_SIMULATE_DRIVING --es activity Still -n com.starception.submission.demo.debug/com.starception.submission.util.DebugDrivingReceiver
 */
package com.starception.submission.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityTransition
import com.starception.submission.services.PrayerNotificationService

class DebugDrivingReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DebugDrivingReceiver"
        const val ACTION = "com.starception.submission.DEBUG_SIMULATE_DRIVING"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val force = intent.getBooleanExtra("force", false)

        if (force) {
            // Ensure ActivityTracker is initialized for the direct audio-chain test.
            ActivityTracker.initialize(context.applicationContext, startDetectionNow = false)
            // Force-trigger the full audio chain immediately (same path as driving detection).
            // This calls DrivingAudioService with travel dua + hadith, bypassing
            // cooldowns and driving-time accumulation.
            Log.i(TAG, "🧪 FORCE TRIGGER: Starting full audio chain immediately")
            ActivityTracker.triggerFullAudioChain()
        } else {
            // Exercise the same process-independent service path used by the
            // manifest Google Activity Transition receiver. This keeps the adb
            // test representative when no Activity is open.
            val activity = intent.getStringExtra("activity") ?: "Driving"
            Log.i(TAG, "🧪 SIMULATING activity: $activity")
            val serviceIntent = Intent(context, PrayerNotificationService::class.java).apply {
                action = PrayerNotificationService.ACTION_ACTIVITY_TRANSITION
                putExtra(PrayerNotificationService.EXTRA_DETECTED_ACTIVITY, activity.uppercase())
                putExtra(
                    PrayerNotificationService.EXTRA_TRANSITION_TYPE,
                    ActivityTransition.ACTIVITY_TRANSITION_ENTER,
                )
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i(TAG, "🧪 Activity forwarded through background service path: $activity")
        }
    }
}
