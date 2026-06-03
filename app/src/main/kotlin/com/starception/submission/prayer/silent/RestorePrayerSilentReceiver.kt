package com.starception.submission.prayer.silent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestorePrayerSilentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerSilentModeController.ACTION_RESTORE) return
        PrayerSilentModeController(context.applicationContext).restore()
    }
}
