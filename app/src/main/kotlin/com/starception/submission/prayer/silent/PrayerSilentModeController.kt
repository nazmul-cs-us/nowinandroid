package com.starception.submission.prayer.silent

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log

class PrayerSilentModeController(private val context: Context) {

    private val nm: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val am: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Schedules silent mode to switch ON at `now + delayMinutes` (i.e. once the
     * user-configured "Go to Mosque" window for this prayer has elapsed), then
     * stay on for [durationMinutes]. The actual DND flip + restore alarm are
     * set when [StartPrayerSilentReceiver] fires this back into [enableForPrayer].
     * No-op if delay <= 0 — in that case the caller should invoke [enableForPrayer]
     * directly.
     */
    fun scheduleStartAfter(prayerName: String, delayMinutes: Int, durationMinutes: Int) {
        if (delayMinutes <= 0) {
            enableForPrayer(prayerName, durationMinutes)
            return
        }
        if (!nm.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "DND access not granted — cannot schedule silent mode for $prayerName")
            return
        }
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        val intent = Intent(context, StartPrayerSilentReceiver::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_PRAYER_NAME, prayerName)
            putExtra(EXTRA_DURATION_MIN, durationMinutes)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            START_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        Log.i(TAG, "Silent mode for $prayerName scheduled at +${delayMinutes}m (Go to Mosque phase), then ${durationMinutes}m duration")
    }

    fun enableForPrayer(prayerName: String, durationMinutes: Int) {
        if (!nm.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "DND access not granted — cannot enable silent mode for $prayerName")
            return
        }
        val priorFilter = nm.currentInterruptionFilter
        val triggerAt = System.currentTimeMillis() + durationMinutes * 60_000L
        prefs.edit()
            .putInt(KEY_PRIOR_FILTER, priorFilter)
            .putString(KEY_PRAYER_NAME, prayerName)
            .putLong(KEY_END_AT_MS, triggerAt)
            .apply()
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        Log.i(TAG, "Silent mode enabled for $prayerName (priorFilter=$priorFilter, duration=${durationMinutes}m)")

        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, restorePendingIntent())
    }

    fun restore() {
        if (!nm.isNotificationPolicyAccessGranted) return
        val priorFilter = prefs.getInt(KEY_PRIOR_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        nm.setInterruptionFilter(priorFilter)
        prefs.edit()
            .remove(KEY_PRIOR_FILTER)
            .remove(KEY_PRAYER_NAME)
            .remove(KEY_END_AT_MS)
            .apply()
        Log.i(TAG, "Silent mode restored to filter=$priorFilter")
    }

    private fun restorePendingIntent(): PendingIntent {
        val intent = Intent(context, RestorePrayerSilentReceiver::class.java).apply {
            action = ACTION_RESTORE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "PrayerSilentMode"
        private const val PREFS_NAME = "prayer_silent_mode"
        private const val KEY_PRIOR_FILTER = "prior_filter"
        private const val KEY_PRAYER_NAME = "prayer_name"
        private const val KEY_END_AT_MS = "end_at_ms"
        private const val REQUEST_CODE = 47291
        private const val START_REQUEST_CODE = 47292
        const val ACTION_RESTORE = "com.starception.submission.action.RESTORE_PRAYER_SILENT"
        const val ACTION_START = "com.starception.submission.action.START_PRAYER_SILENT"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_DURATION_MIN = "duration_min"

        fun currentSession(context: Context): SilentSession? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val endAtMs = prefs.getLong(KEY_END_AT_MS, 0L)
            if (endAtMs <= System.currentTimeMillis()) return null
            val prayerName = prefs.getString(KEY_PRAYER_NAME, null) ?: return null
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) return null
            return SilentSession(prayerName = prayerName, endAtMs = endAtMs)
        }
    }
}

data class SilentSession(val prayerName: String, val endAtMs: Long) {
    fun minutesLeft(now: Long = System.currentTimeMillis()): Int =
        ((endAtMs - now).coerceAtLeast(0L) / 60_000L).toInt().coerceAtLeast(1)
}
