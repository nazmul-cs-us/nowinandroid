package com.starception.submission.feature.prayertimes.wobble

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.starception.submission.prayer.silent.PrayerSilentModeController
import kotlinx.coroutines.delay

data class SilentModeState(
    val isActive: Boolean = false,
    val prayerName: String = "",
    val minutesLeft: Int = 0,
) {
    val displayText: String
        get() = "Silent for ${prayerName.replaceFirstChar { it.uppercase() }} · ${minutesLeft}m left"
}

@Composable
fun rememberSilentModeState(): State<SilentModeState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(SilentModeState()) }
    LaunchedEffect(Unit) {
        while (true) {
            val session = PrayerSilentModeController.currentSession(context)
            state.value = if (session != null) {
                SilentModeState(
                    isActive = true,
                    prayerName = session.prayerName,
                    minutesLeft = session.minutesLeft(),
                )
            } else {
                SilentModeState()
            }
            delay(30_000L)
        }
    }
    return state
}
