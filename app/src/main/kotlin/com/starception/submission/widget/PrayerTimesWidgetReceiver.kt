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

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "PrayerTimesWidget"

/**
 * Shared behaviour for every prayer widget size.
 *
 * One receiver per picker entry is a framework requirement — the launcher binds widgets
 * by provider component, so a single receiver could only ever produce a single entry.
 */
abstract class BasePrayerWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // Prayer times are wall-clock values, so a timezone move or a manual clock
        // change silently invalidates everything on screen without any of our own data
        // changing. updatePeriodMillis would not catch that for up to 30 minutes.
        if (intent.action in CLOCK_CHANGE_ACTIONS) {
            PrayerWidgetUpdater.refresh(context)
        }
    }

    private companion object {
        val CLOCK_CHANGE_ACTIONS = setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
        )
    }
}

class PrayerTimesTinyWidgetReceiver : BasePrayerWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerTimesTinyWidget()
}

class PrayerTimesSmallWidgetReceiver : BasePrayerWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerTimesSmallWidget()
}

class PrayerTimesWidgetReceiver : BasePrayerWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerTimesWidget()
}

class PrayerTimesLargeWidgetReceiver : BasePrayerWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerTimesLargeWidget()
}

class PrayerTimesFullWidgetReceiver : BasePrayerWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerTimesFullWidget()
}

/**
 * Pushes fresh content into every placed prayer widget, at every size.
 *
 * Glance scopes [updateAll] to one GlanceAppWidget class, so each size has to be
 * updated separately — missing one would leave that size frozen on stale times. Safe to
 * call when no widget exists; Glance treats an empty widget set as a no-op. Callers
 * should not await it, the redraw is best-effort and must never block their own work.
 */
object PrayerWidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            listOf(
                PrayerTimesTinyWidget(),
                PrayerTimesSmallWidget(),
                PrayerTimesWidget(),
                PrayerTimesLargeWidget(),
                PrayerTimesFullWidget(),
            ).forEach { widget ->
                try {
                    widget.updateAll(appContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Widget refresh failed for ${widget.javaClass.simpleName}", e)
                }
            }
        }
    }
}
