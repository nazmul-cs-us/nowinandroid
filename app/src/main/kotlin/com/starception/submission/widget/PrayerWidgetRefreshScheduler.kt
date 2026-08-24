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
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
 *  - **A minute-aligned alarm**, re-armed each time it fires, keeping the static
 *    RemoteViews countdown in step with the wall clock while the screen is on. The app
 *    already has exact-alarm access for its prayer notifications, so the widget can use
 *    the same capability instead of [AlarmManager.setWindow], whose sub-ten-minute
 *    windows are expanded by Android 12+ and cannot provide a one-minute cadence.
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
     * The countdown is rendered as static RemoteViews text, so it has to be redrawn once
     * per minute to remain accurate. The alarm is non-wakeup: it will not wake a sleeping
     * device just to redraw a home screen nobody can see. ACTION_USER_PRESENT handles the
     * first redraw after sleep.
     */
    private const val INTERVAL_MILLIS = 60 * 1000L

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.w(TAG, "No AlarmManager; widget will refresh only when the launcher asks")
            return
        }
        val triggerAtMillis = nextMinuteBoundary(System.currentTimeMillis())
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setExact(
                    AlarmManager.RTC,
                    triggerAtMillis,
                    pendingIntent(appContext),
                )
            } else {
                // Exact-alarm access can be revoked. Keep a best-effort refresh instead
                // of allowing the widget to freeze until the launcher's 30-minute tick.
                alarmManager.set(
                    AlarmManager.RTC,
                    triggerAtMillis,
                    pendingIntent(appContext),
                )
                Log.w(TAG, "Exact alarm access unavailable; widget refresh is best-effort")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not schedule widget refresh", e)
        }
    }

    /** Re-arm only while at least one prayer widget is actually placed. */
    fun scheduleIfWidgetPlaced(context: Context) {
        if (hasPlacedWidget(context)) {
            schedule(context)
        } else {
            cancel(context)
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

    private fun nextMinuteBoundary(nowMillis: Long): Long =
        ((nowMillis / INTERVAL_MILLIS) + 1L) * INTERVAL_MILLIS

    private fun hasPlacedWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context.applicationContext)
        return listOf(
            PrayerTimesTinyWidgetReceiver::class.java,
            PrayerTimesSmallWidgetReceiver::class.java,
            PrayerTimesWidgetReceiver::class.java,
            PrayerTimesLargeWidgetReceiver::class.java,
            PrayerTimesFullWidgetReceiver::class.java,
        ).any { receiver ->
            manager.getAppWidgetIds(ComponentName(context, receiver)).isNotEmpty()
        }
    }
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
                PrayerWidgetRefreshScheduler.scheduleIfWidgetPlaced(appContext)
                pendingResult.finish()
            }
        }
    }
}
