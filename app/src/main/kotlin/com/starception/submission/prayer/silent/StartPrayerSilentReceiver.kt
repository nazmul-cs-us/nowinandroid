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

package com.starception.submission.prayer.silent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * AlarmManager callback that actually flips DND on. Scheduled by
 * [PrayerSilentModeController.scheduleStartAfter] so silent mode starts only
 * once the user-configured "Go to Mosque" window has elapsed — NOT at the very
 * instant prayer time hits.
 */
class StartPrayerSilentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerSilentModeController.ACTION_START) return
        val prayerName = intent.getStringExtra(PrayerSilentModeController.EXTRA_PRAYER_NAME)
            ?: return
        val durationMinutes = intent.getIntExtra(PrayerSilentModeController.EXTRA_DURATION_MIN, 0)
        if (durationMinutes <= 0) return
        Log.i(TAG, "Go-to-mosque phase ended — enabling silent mode for $prayerName for ${durationMinutes}m")
        PrayerSilentModeController(context.applicationContext)
            .enableForPrayer(prayerName, durationMinutes)
    }

    private companion object {
        const val TAG = "StartPrayerSilent"
    }
}
