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

package com.starception.submission.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.prayer.service.AdhanPlaybackService
import kotlinx.serialization.json.Json

class DebugAdhanReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DebugAdhanReceiver"
        const val ACTION = "com.starception.submission.DEBUG_PLAY_ADHAN"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val prayerName = intent.getStringExtra("prayer") ?: "Fajr"
        val preferences = readNotificationPreferences(context)

        // No explicit volume extra: exercise the stored preferences, exactly
        // like the real fire path would (per-prayer override, else master).
        val volume = if (intent.hasExtra("volume")) {
            intent.getIntExtra("volume", AdhanPlaybackService.DEFAULT_VOLUME_PERCENT)
        } else {
            preferences?.getAdhanVolumeForPrayer(prayerName) ?: AdhanPlaybackService.DEFAULT_VOLUME_PERCENT
        }

        Log.i(
            TAG,
            "🧪 DEBUG: starting adhan for $prayerName at $volume% " +
                "(master=${preferences?.adhanVolume}, " +
                "toggle=${preferences?.isAdhanEnabledForPrayer(prayerName)})",
        )
        AdhanPlaybackService.start(
            context = context.applicationContext,
            prayerName = prayerName,
            volumePercent = volume,
        )
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
            Log.e(TAG, "Failed to read notification preferences", e)
            null
        }
    }
}
