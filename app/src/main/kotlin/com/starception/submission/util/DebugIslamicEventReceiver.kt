/*
 * Debug-only BroadcastReceiver to force-show an Islamic-event banner in
 * PullToSyncContainer. Lets us verify the banner visually without waiting for
 * a real Hijri event day.
 *
 * Usage (one of the supported keys: arafah | ashura | laylat_qadr | eid_fitr
 * | eid_adha | tashreeq | hijri_new_year):
 *   adb shell am broadcast -a com.starception.submission.DEBUG_SIMULATE_ISLAMIC_EVENT \
 *     --es key arafah \
 *     -n com.starception.submission.demo.debug/com.starception.submission.util.DebugIslamicEventReceiver
 *
 * Clear the override (back to real-date behaviour):
 *   adb shell am broadcast -a com.starception.submission.DEBUG_SIMULATE_ISLAMIC_EVENT --ez clear true \
 *     -n com.starception.submission.demo.debug/com.starception.submission.util.DebugIslamicEventReceiver
 */
package com.starception.submission.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.starception.submission.feature.prayertimes.wobble.IslamicEventState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide singleton holding a debug-injected Islamic event override.
 * IslamicEventStateProvider merges this with its calculated state so the rest
 * of the UI doesn't need to know about the debug path.
 */
object DebugIslamicEventBus {
    val state = MutableStateFlow<IslamicEventState?>(null)
}

class DebugIslamicEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DebugIslamicEvent"
        const val ACTION = "com.starception.submission.DEBUG_SIMULATE_ISLAMIC_EVENT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        if (intent.getBooleanExtra("clear", false)) {
            Log.i(TAG, "🧪 CLEAR simulated Islamic event")
            DebugIslamicEventBus.state.value = null
            return
        }

        val key = intent.getStringExtra("key") ?: "arafah"
        val state = catalog[key] ?: run {
            Log.w(TAG, "🧪 Unknown key '$key'; supported: ${catalog.keys.joinToString()}")
            return
        }
        Log.i(TAG, "🧪 SIMULATE Islamic event: $state")
        DebugIslamicEventBus.state.value = state
    }

    private val catalog: Map<String, IslamicEventState> = mapOf(
        "arafah" to IslamicEventState(true, "arafah", "Day of Arafah", "Arafah"),
        "ashura" to IslamicEventState(true, "ashura", "Day of Ashura", "Ashura"),
        "laylat_qadr" to IslamicEventState(true, "laylat_qadr", "Tonight may be Laylat al-Qadr", "Qadr"),
        "eid_fitr" to IslamicEventState(true, "eid_fitr", "Eid Mubarak — Eid al-Fitr", "Eid"),
        "eid_adha" to IslamicEventState(true, "eid_adha", "Eid Mubarak — Eid al-Adha", "Eid"),
        "tashreeq" to IslamicEventState(true, "tashreeq", "Days of Tashreeq", "Takbir"),
        "hijri_new_year" to IslamicEventState(true, "hijri_new_year", "Hijri New Year", "Muharram"),
    )
}
