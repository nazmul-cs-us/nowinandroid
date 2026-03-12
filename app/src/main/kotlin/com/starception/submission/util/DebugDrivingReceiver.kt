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

class DebugDrivingReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DebugDrivingReceiver"
        const val ACTION = "com.starception.submission.DEBUG_SIMULATE_DRIVING"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val force = intent.getBooleanExtra("force", false)

        // Ensure ActivityTracker is initialized (receiver may fire before app launch)
        ActivityTracker.initialize(context.applicationContext, startDetectionNow = false)

        if (force) {
            // Force-trigger the full audio chain immediately (same path as driving detection).
            // This calls DrivingAudioService with travel dua + hadith, bypassing
            // cooldowns and driving-time accumulation.
            Log.i(TAG, "🧪 FORCE TRIGGER: Starting full audio chain immediately")
            ActivityTracker.triggerFullAudioChain()
        } else {
            // Simulate an activity change so the normal cooldown/delay logic runs.
            val activity = intent.getStringExtra("activity") ?: "Driving"
            Log.i(TAG, "🧪 SIMULATING activity: $activity")
            ActivityTracker.updateActivity(activity)
            Log.i(TAG, "🧪 Activity updated to: $activity")
        }
    }
}
