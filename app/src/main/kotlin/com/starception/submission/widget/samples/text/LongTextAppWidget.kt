/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.starception.submission.widget.samples.text

import com.starception.submission.widget.PrayerWidgetColors

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.glance.currentState
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.starception.submission.R
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent
import com.starception.submission.MainActivity
import com.starception.submission.widget.DailyReminder
import com.starception.submission.widget.DailyReminderRepository
import com.starception.submission.widget.WidgetNavigationBus
import com.starception.submission.widget.samples.text.layout.LongTextLayout
import com.starception.submission.widget.samples.text.layout.LongTextLayoutData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LongTextAppWidget : GlanceAppWidget() {
  // Unlike the "Single" size mode, using "Exact" allows us to have better control over rendering in
  // different sizes. And, unlike the "Responsive" mode, it doesn't cause several views for each
  // supported size to be held in the widget host's memory.
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    // Loaded once for the first paint, so the card never flashes a placeholder.
    val initialOffset = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[OFFSET_KEY] ?: 0
    val initial = DailyReminderRepository.load(context, initialOffset)

    provideContent {
      // Read *inside* provideContent, and this is the whole reason refresh works.
      //
      // Glance calls provideGlance once per session; provideContent then establishes a
      // long-lived composition, and every later update() recomposes that composition
      // rather than re-entering provideGlance. Content loaded in the outer scope is
      // therefore captured once and can never change — verified by log: the refresh
      // callback wrote state, resolved the right glanceId and called updateAll, and
      // provideGlance was never called again while the card sat unchanged.
      //
      // currentState is observable from inside the composition, so bumping the offset
      // recomposes here, and the effect below reloads against the new value.
      val offset = currentState<Preferences>()[OFFSET_KEY] ?: initialOffset
      var reminder by remember { mutableStateOf(initial) }
      LaunchedEffect(offset) {
        if (offset != initialOffset) {
          reminder = DailyReminderRepository.load(context, offset)
        }
      }

      GlanceTheme(colors = PrayerWidgetColors) {
        LongTextAppWidgetContent(reminder = reminder)
      }
    }
  }

  @Composable
  internal fun LongTextAppWidgetContent(reminder: DailyReminder) {
    val context = LocalContext.current

    LongTextLayout(
      title = context.getString(R.string.sample_long_text_app_widget_name),
      titleIconRes = R.drawable.ic_widget_daily_reminder_flaticon,
      titleBarActionIconRes = R.drawable.sample_refresh_icon,
      titleBarActionIconContentDescription = context.getString(
        R.string.sample_refresh_icon_button_label
      ),
      // Advancing the rotation has to outlive this composition — the widget process is
      // gone moments after it draws — so refresh writes the new offset and asks for a
      // redraw rather than mutating an in-memory repository the way the sample did.
      // An ActionCallback, not a lambda. A Glance lambda action is re-invoked whenever
      // the RemoteViews it belongs to is re-applied, and this one's own redraw re-applies
      // it — so a single tap advanced the rotation several times and skipped past the dua.
      // Measured: four taps left the stored offset at 14.
      titleBarAction = actionRunCallback<AdvanceDailyReminderAction>(),
      data = LongTextLayoutData(
        key = reminder.key,
        text = reminder.text,
        caption = reminder.caption,
        contentTitle = reminder.contentTitle,
        sourceName = reminder.sourceName,
        sourceDetail = reminder.sourceDetail,
        arabic = reminder.arabic,
      ),
      action = reminder.openAction(context),
    )
  }
}

/**
 * Opens the hadith or dua this reminder came from, or just the app when it has no source.
 *
 * The target rides on the intent rather than in a deep-link Uri: a dua carries its own
 * translation, and percent-encoding a paragraph into a path segment works but produces a
 * URI nobody can read. MainActivity hands it to [WidgetNavigationBus].
 */
private fun DailyReminder.openAction(context: Context): Action {
  val target = target ?: return actionStartActivity<MainActivity>()
  val intent = WidgetNavigationBus.put(
    Intent(context, MainActivity::class.java).apply {
      // A widget tap should resume the app where the user left it, not stack a second
      // copy of the task behind whatever they were doing.
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    },
    target,
  )
  return actionStartIntent(intent)
}

/** Advances the reminder to the next item in the rotation, then redraws. */
class AdvanceDailyReminderAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    // Write through Glance's state, then update. The pair is the documented way to make a
    // widget re-compose, and both halves are needed: the write is what marks the widget
    // dirty, and without it update() returns successfully having done nothing.
    updateAppWidgetState(context, glanceId) { prefs ->
      prefs[OFFSET_KEY] = (prefs[OFFSET_KEY] ?: 0) + 1
    }
    // updateAll, not update(glanceId): the targeted call wrote the state and returned
    // without ever re-running provideGlance, verified by log. updateAll is the same call
    // the prayer widget's own refresh uses, which does re-compose. The offset stays
    // per-widget regardless, because it lives in that widget's Glance state.
    LongTextAppWidget().updateAll(context)
  }
}

/** How far the reminder has been advanced past the day's default, in Glance's state. */
private val OFFSET_KEY = intPreferencesKey("daily_reminder_offset")

class LongTextAppWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = LongTextAppWidget()
}
