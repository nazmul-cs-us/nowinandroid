/*
 * Copyright 2024 The Android Open Source Project
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
                val isDriving = activity.equals("Driving", ignoreCase = true)
                val includeVehicleEdge = intent.getBooleanExtra("vehicleEdge", true)
                val transitionType = if (isDriving || !includeVehicleEdge) {
                    ActivityTransition.ACTIVITY_TRANSITION_ENTER
                } else {
                    ActivityTransition.ACTIVITY_TRANSITION_EXIT
                }
                putExtra(PrayerNotificationService.EXTRA_TRANSITION_TYPE, transitionType)
                putExtra(
                    PrayerNotificationService.EXTRA_IN_VEHICLE_TRANSITION_TYPE,
                    if (includeVehicleEdge) transitionType else -1,
                )
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i(TAG, "🧪 Activity forwarded through background service path: $activity")
        }
    }
}
