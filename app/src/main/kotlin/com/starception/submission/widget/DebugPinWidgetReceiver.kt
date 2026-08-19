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

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Asks the launcher to pin a prayer widget, so one can be placed from adb.
 *
 * A widget's composition only ever runs for a *placed* widget, which makes anything that
 * happens at render time — the animated Meteocon above all — impossible to verify
 * without physically dragging one out of the picker. There is no adb command to place a
 * widget, but [AppWidgetManager.requestPinAppWidget] is the supported way for an app to
 * ask, and the launcher handles it with a confirmation the user accepts once.
 *
 * Trigger with:
 *   adb shell am broadcast -a com.starception.submission.DEBUG_PIN_WIDGET \
 *     -n com.starception.submission.demo.debug/com.starception.submission.widget.DebugPinWidgetReceiver \
 *     --es size large
 *
 * `size` accepts tiny, small, wide, large or full; it defaults to large, the size that
 * shows the animated weather icon.
 */
class DebugPinWidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = AppWidgetManager.getInstance(context)

        if (!manager.isRequestPinAppWidgetSupported) {
            Log.w(TAG, "Launcher does not support pinning widgets")
            return
        }

        val receiver = when (intent.getStringExtra("size")?.lowercase()) {
            "tiny" -> PrayerTimesTinyWidgetReceiver::class.java
            "small" -> PrayerTimesSmallWidgetReceiver::class.java
            "wide" -> PrayerTimesWidgetReceiver::class.java
            "full" -> PrayerTimesFullWidgetReceiver::class.java
            else -> PrayerTimesLargeWidgetReceiver::class.java
        }

        val requested = manager.requestPinAppWidget(
            ComponentName(context, receiver),
            null,
            null,
        )
        Log.i(TAG, "Pin request for ${receiver.simpleName} accepted by launcher: $requested")
    }

    private companion object {
        const val TAG = "PrayerWidget"
    }
}
