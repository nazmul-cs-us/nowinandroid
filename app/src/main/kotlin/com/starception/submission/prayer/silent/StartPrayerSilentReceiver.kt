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
