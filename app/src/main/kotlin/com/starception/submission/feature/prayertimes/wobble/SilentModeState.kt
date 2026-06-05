package com.starception.submission.feature.prayertimes.wobble

import android.app.NotificationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import com.starception.submission.prayer.silent.PrayerSilentModeController
import kotlinx.coroutines.delay

data class SilentModeState(
    val isActive: Boolean = false,
    val displayText: String = "",
)

/**
 * Surfaces "do not disturb" state to the pull-to-sync banner. Only true DND
 * (NotificationManager interruption filter ≠ ALL) and prayer-driven silence
 * count — ringer mode (vibrate/silent) is intentionally ignored, since most
 * phones live in vibrate and we don't want the whole-screen `tertiaryContainer`
 * background tint baked into PullToSyncContainer to be permanently on.
 */
@Composable
fun rememberSilentModeState(): State<SilentModeState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(computeSilentState(context)) }
    // Interruption filter has no public broadcast; poll on a short timer so
    // the banner picks up DND toggles from Quick Settings.
    LaunchedEffect(Unit) {
        while (true) {
            state.value = computeSilentState(context)
            delay(15_000L)
        }
    }
    return state
}

private fun computeSilentState(context: Context): SilentModeState {
    PrayerSilentModeController.currentSession(context)?.let { session ->
        val prayer = session.prayerName.replaceFirstChar { it.uppercase() }
        return SilentModeState(
            isActive = true,
            displayText = "Silent for $prayer · ${session.minutesLeft()}m left",
        )
    }
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
        return SilentModeState(isActive = true, displayText = "Do Not Disturb")
    }
    return SilentModeState()
}
