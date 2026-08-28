package com.starception.submission.feature.prayertimes.wobble

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.content.Context
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
