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
import android.os.Bundle
import android.util.Log

/**
 * Rewrites the size options of every placed prayer widget so a given size bucket can be
 * rendered on demand.
 *
 * Glance chooses its layout from the size the host reports, so verifying a 2x1 layout
 * otherwise means physically dragging a 2x1 out of the picker — there is no adb command
 * to place or resize a widget. Resizing the widget already on screen reaches the same
 * code path without adding anything to the launcher.
 *
 * This lives in src/debug so it cannot ship, unlike the earlier pin receiver which sat
 * in src/main and was exported to every app on the device.
 *
 *   adb shell am broadcast -a com.starception.submission.DEBUG_RESIZE_WIDGET \
 *     -n com.starception.submission.demo.debug/com.starception.submission.widget.DebugResizeWidgetReceiver \
 *     --ei w 110 --ei h 40
 */
class DebugResizeWidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val width = intent.getIntExtra("w", 110)
        val height = intent.getIntExtra("h", 40)
        val manager = AppWidgetManager.getInstance(context)

        val providers = listOf(
            PrayerTimesTinyWidgetReceiver::class.java,
            PrayerTimesSmallWidgetReceiver::class.java,
            PrayerTimesWidgetReceiver::class.java,
            PrayerTimesLargeWidgetReceiver::class.java,
            PrayerTimesFullWidgetReceiver::class.java,
        )

        var resized = 0
        providers.forEach { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
                manager.updateAppWidgetOptions(
                    id,
                    Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
                    },
                )
                resized++
            }
        }
        Log.i("PrayerWidget", "Resized $resized widget(s) to ${width}x${height}dp")
    }
}
