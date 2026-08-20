/*
 * Copyright 2026 Starception
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

package com.starception.submission.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "PrayerWidgetRefresh"

/**
 * Keeps the widget's reading honest between the launcher's own updates.
 *
 * The widget shows two figures that move every minute — "16 minutes since Dhuhr" and
 * "Asr in 3h 12m" — but a widget's own `updatePeriodMillis` is floored at 30 minutes by
 * the framework, so left to the launcher the card drifts by up to half an hour. It was
 * observed reading "15 minutes since Dhuhr" when the truth was 21.
 *
 * So the widget drives its own cadence instead. Two triggers, because they cover
 * different failure modes:
 *
 *  - **A repeating alarm**, re-armed each time it fires, bounding drift while the screen
 *    is on. [setWindow] rather than an exact alarm deliberately: exact alarms need
 *    SCHEDULE_EXACT_ALARM on Android 12+ and are a poor trade for a display refresh, and
 *    the window lets the system batch this with other work.
 *
 *  - **Unlock**, because the alarm is the part the system is entitled to defer. Doze
 *    holds alarms while the screen is off, which is harmless — nobody is reading the
 *    widget then — but it means the first thing the user sees after unlocking could be a
 *    stale card. Refreshing on ACTION_USER_PRESENT closes exactly that gap.
 */
internal object PrayerWidgetRefreshScheduler {

    /**
     * How often the card re-reads while in use.
     *
     * Five minutes is the compromise between the two things that go wrong at the ends: a
     * minute would be honest to the displayed figure but is far more wake-ups than a
     * home-screen readout justifies, and anything much longer starts showing a countdown
     * the user can see is wrong.
     */
    private const val INTERVAL_MILLIS = 5 * 60 * 1000L

    /** Slack the system may use to batch this with other pending work. */
    private const val WINDOW_MILLIS = 60 * 1000L

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.w(TAG, "No AlarmManager; widget will refresh only when the launcher asks")
            return
        }
        try {
            alarmManager.setWindow(
                AlarmManager.RTC,
                System.currentTimeMillis() + INTERVAL_MILLIS,
                WINDOW_MILLIS,
                pendingIntent(appContext),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not schedule widget refresh", e)
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        try {
            alarmManager?.cancel(pendingIntent(appContext))
        } catch (e: Exception) {
            Log.e(TAG, "Could not cancel widget refresh", e)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, PrayerWidgetRefreshReceiver::class.java).setAction(ACTION_REFRESH),
        // Mutability matters here only in that it must be declared; nothing fills in
        // extras, so immutable is correct and is required from Android 12 anyway.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val REQUEST_CODE = 0x5A1A
    internal const val ACTION_REFRESH = "com.starception.submission.widget.ACTION_REFRESH_WIDGET"
}

/**
 * Redraws the widgets, then re-arms the next refresh.
 *
 * Re-arming here rather than using a repeating alarm keeps the cadence in one place and
 * survives the system dropping a repeat: as long as one fires, the next is booked.
 */
class PrayerWidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        // goAsync, because updateAll suspends and a receiver's process can be torn down
        // the moment onReceive returns — without the token the redraw races that teardown.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                PrayerWidgetUpdater.refreshNow(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Scheduled widget refresh failed", e)
            } finally {
                // Re-armed even on failure: a refresh that threw is the case where
                // stopping would leave the card frozen for good.
                PrayerWidgetRefreshScheduler.schedule(appContext)
                pendingResult.finish()
            }
        }
    }
}
