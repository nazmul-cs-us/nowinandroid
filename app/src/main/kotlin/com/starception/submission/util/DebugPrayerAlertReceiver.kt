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
            AlertPhase.GO_TO_MOSQUE -> "Go to the mosque now, ${countdown}m left"
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
