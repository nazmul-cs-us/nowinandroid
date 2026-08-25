package com.starception.submission.prayer.silent

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.util.Log
import com.starception.submission.MainActivity

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
        scheduleWakeup(triggerAt, pi, "start $prayerName")
        Log.i(TAG, "Silent mode for $prayerName scheduled at +${delayMinutes}m (Go to Mosque phase), then ${durationMinutes}m duration")
    }

    fun enableForPrayer(prayerName: String, durationMinutes: Int) {
        if (!nm.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "DND access not granted — cannot enable silent mode for $prayerName")
            return
        }
        val now = System.currentTimeMillis()
        val existingEndAt = prefs.getLong(KEY_END_AT_MS, 0L)
        val existingPrayer = prefs.getString(KEY_PRAYER_NAME, null)
        if (existingEndAt > now && existingPrayer.equals(prayerName, ignoreCase = true)) {
            Log.i(TAG, "Silent mode already active for $prayerName — ignoring duplicate trigger")
            return
        }
        // Preserve the ORIGINAL pre-prayer filter across overlapping/re-entrant windows: if a
        // silent session is already active, reuse its stored prior filter instead of capturing
        // the current one (which is already our own PRIORITY filter). Otherwise restore would
        // put the phone back into DND instead of returning it to the user's real setting.
        val sessionActive = existingEndAt > now
        val priorFilter = if (sessionActive) {
            prefs.getInt(KEY_PRIOR_FILTER, nm.currentInterruptionFilter)
        } else {
            nm.currentInterruptionFilter
        }
        val triggerAt = now + durationMinutes * 60_000L
        prefs.edit()
            .putInt(KEY_PRIOR_FILTER, priorFilter)
            .putString(KEY_PRAYER_NAME, prayerName)
            .putLong(KEY_END_AT_MS, triggerAt)
            .apply()
        if (!activatePrayerRule()) {
            prefs.edit()
                .remove(KEY_PRIOR_FILTER)
                .remove(KEY_PRAYER_NAME)
                .remove(KEY_END_AT_MS)
                .apply()
            return
        }
        Log.i(TAG, "Silent mode enabled for $prayerName (priorFilter=$priorFilter, duration=${durationMinutes}m)")

        scheduleWakeup(triggerAt, restorePendingIntent(), "restore after $prayerName")
    }

    /**
     * Safety net for when the one-shot restore alarm never fires — e.g. the device rebooted
     * during the silent window (AlarmManager alarms do not survive a reboot) or the app was
     * force-stopped by the OS. Should be called on boot and whenever the app is opened.
     *
     * - If the window has already ended but DND is still on, restore the phone immediately.
     * - If the window is still active, re-arm the restore alarm so it still fires on time.
     */
    fun recoverIfNeeded() {
        if (!nm.isNotificationPolicyAccessGranted) return
        val endAt = prefs.getLong(KEY_END_AT_MS, 0L)
        if (endAt <= 0L) return
        val now = System.currentTimeMillis()
        if (now >= endAt) {
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                Log.i(TAG, "Recovery: silent window expired but DND still on — restoring now")
                restore()
            } else {
                // Filter already back to normal; just clear the stale bookkeeping.
                prefs.edit()
                    .remove(KEY_PRIOR_FILTER)
                    .remove(KEY_PRAYER_NAME)
                    .remove(KEY_END_AT_MS)
                    .apply()
            }
        } else {
            Log.i(TAG, "Recovery: re-arming restore alarm for active silent window (ends in ${(endAt - now) / 60_000L}m)")
            scheduleWakeup(endAt, restorePendingIntent(), "recovery restore")
        }
    }

    fun restore() {
        if (!nm.isNotificationPolicyAccessGranted) return
        val priorFilter = prefs.getInt(KEY_PRIOR_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            setPrayerRuleActive(false)
        } else {
            nm.setInterruptionFilter(priorFilter)
        }
        prefs.edit()
            .remove(KEY_PRIOR_FILTER)
            .remove(KEY_PRAYER_NAME)
            .remove(KEY_END_AT_MS)
            .apply()
        Log.i(TAG, "Silent mode restored to filter=$priorFilter")
    }

    /**
     * Android 15+ no longer allows target-35 apps to directly change global DND. A legacy
     * setInterruptionFilter call creates an implicit rule that OEM settings can leave disabled.
     * Own an explicit Prayer Time rule instead, then publish its active state at prayer time.
     */
    private fun activatePrayerRule(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            setPrayerRuleActive(true)
        } else {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            true
        }
    }

    private fun setPrayerRuleActive(active: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        val ruleId = ensurePrayerZenRule() ?: return false
        val state = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
        return runCatching {
            nm.setAutomaticZenRuleState(
                ruleId,
                Condition(prayerConditionId(), "Prayer time", state, Condition.SOURCE_SCHEDULE),
            )
            Log.i(TAG, "Prayer Time DND rule ${if (active) "activated" else "deactivated"}")
            true
        }.getOrElse { error ->
            Log.e(TAG, "Unable to ${if (active) "activate" else "deactivate"} Prayer Time DND rule", error)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun ensurePrayerZenRule(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        val storedRuleId = prefs.getString(KEY_ZEN_RULE_ID, null)
        if (storedRuleId != null) {
            val storedRule = runCatching { nm.getAutomaticZenRule(storedRuleId) }.getOrNull()
            if (storedRule != null) {
                if (!storedRule.isEnabled) {
                    storedRule.isEnabled = true
                    val enabled = runCatching {
                        nm.updateAutomaticZenRule(storedRuleId, storedRule)
                    }.getOrDefault(false)
                    if (!enabled) {
                        Log.w(TAG, "Prayer Time DND rule is disabled in system Modes settings")
                        return null
                    }
                }
                return storedRuleId
            }
            prefs.edit().remove(KEY_ZEN_RULE_ID).apply()
        }

        val configurationActivity = ComponentName(context, MainActivity::class.java)
        val rule = AutomaticZenRule(
            "Prayer Time",
            null,
            configurationActivity,
            prayerConditionId(),
            null,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            true,
        )
        return runCatching { nm.addAutomaticZenRule(rule) }
            .getOrNull()
            ?.also { newRuleId ->
                prefs.edit().putString(KEY_ZEN_RULE_ID, newRuleId).apply()
                Log.i(TAG, "Created Prayer Time automatic DND rule")
            }
    }

    private fun prayerConditionId(): Uri = Uri.Builder()
        .scheme(Condition.SCHEME)
        .authority(context.packageName)
        .appendPath("prayer-time")
        .build()

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

    private fun scheduleWakeup(triggerAt: Long, pendingIntent: PendingIntent, label: String) {
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (error: SecurityException) {
            // Android 12+ may deny exact-alarm access. DND must still restore, so
            // fall back to an inexact idle-capable alarm instead of leaving the
            // phone silent indefinitely.
            Log.w(TAG, "Exact alarm unavailable for $label; using inexact fallback", error)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    companion object {
        private const val TAG = "PrayerSilentMode"
        private const val PREFS_NAME = "prayer_silent_mode"
        private const val KEY_PRIOR_FILTER = "prior_filter"
        private const val KEY_PRAYER_NAME = "prayer_name"
        private const val KEY_END_AT_MS = "end_at_ms"
        private const val KEY_ZEN_RULE_ID = "zen_rule_id"
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
