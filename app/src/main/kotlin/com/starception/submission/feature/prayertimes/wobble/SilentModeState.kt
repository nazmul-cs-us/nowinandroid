package com.starception.submission.feature.prayertimes.wobble

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.starception.submission.prayer.silent.PrayerSilentModeController
import kotlinx.coroutines.delay

data class SilentModeState(
    val isActive: Boolean = false,
    val displayText: String = "",
)

@Composable
fun rememberSilentModeState(): State<SilentModeState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(computeSilentState(context)) }

    // Ringer mode changes have a public broadcast; DND interruption filter does
    // not, so we also poll on a timer to keep the banner fresh when the user
    // toggles DND from Quick Settings.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                state.value = computeSilentState(context)
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

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

    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return when (am.ringerMode) {
        AudioManager.RINGER_MODE_SILENT ->
            SilentModeState(isActive = true, displayText = "Silent mode")
        AudioManager.RINGER_MODE_VIBRATE ->
            SilentModeState(isActive = true, displayText = "Vibrate mode")
        else -> SilentModeState()
    }
}
