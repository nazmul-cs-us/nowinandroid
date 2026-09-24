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

package com.starception.submission.feature.prayertimes.wobble

/**
 * State for the prayer alert banner shown in PullToSyncContainer.
 * Displays countdown alerts in two phases:
 * 1. BEFORE_PRAYER: "Fajr in 15m" - countdown to prayer time
 * 2. GO_TO_MOSQUE: "Go to the mosque now, 18m left" - window to leave for mosque
 */
data class PrayerAlertState(
    val isActive: Boolean = false,
    val prayerName: String = "",
    val phase: AlertPhase = AlertPhase.NONE,
    val countdownMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val displayText: String = "",
) {
    // 0f = just started showing, 1f = prayer time reached
    val fillProgress: Float get() = if (totalMinutes > 0) {
        ((totalMinutes - countdownMinutes).toFloat() / totalMinutes).coerceIn(0f, 1f)
    } else {
        1f
    }
}

enum class AlertPhase {
    NONE,
    BEFORE_PRAYER,
    GO_TO_MOSQUE,
}
