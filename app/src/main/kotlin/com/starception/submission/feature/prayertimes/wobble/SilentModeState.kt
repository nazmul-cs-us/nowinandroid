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

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.starception.submission.prayer.silent.PrayerSilentModeController
import kotlinx.coroutines.delay

// The state itself lives in :core:components so the shared sync banner can render
// it; only this reader is Android-specific.

/**
 * Surfaces the prayer-driven silent window to the pull-to-sync banner. The
 * banner appears only when [PrayerSilentModeController] has an active session —
 * i.e. after the "go to mosque" phase ends, for the duration configured in
 * Settings. Generic system DND (toggled via Quick Settings, Pixel Modes,
 * Bedtime, etc.) is intentionally ignored so the banner doesn't lie about why
 * the phone is silent.
 */
@Composable
fun rememberSilentModeState(): State<SilentModeState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(computeSilentState(context)) }
    // The session is timestamp-based — poll so the "Xm left" countdown stays
    // current and the banner clears as soon as the configured window ends.
    LaunchedEffect(Unit) {
        while (true) {
            state.value = computeSilentState(context)
            delay(15_000L)
        }
    }
    return state
}

private fun computeSilentState(context: Context): SilentModeState {
    val session = PrayerSilentModeController.currentSession(context) ?: return SilentModeState()
    val prayer = session.prayerName.replaceFirstChar { it.uppercase() }
    return SilentModeState(
        isActive = true,
        displayText = "Silent for $prayer · ${session.minutesLeft()}m left",
    )
}
