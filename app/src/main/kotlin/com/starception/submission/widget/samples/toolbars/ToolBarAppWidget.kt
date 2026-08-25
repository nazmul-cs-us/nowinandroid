/*
 * Copyright 2023 The Android Open Source Project
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

package com.starception.submission.widget.samples.toolbars

import com.starception.submission.widget.PrayerWidgetColors

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.starception.submission.R
import com.starception.submission.core.ui.R as CoreUiR
import com.starception.submission.widget.samples.toolbars.layout.ToolBarButton
import com.starception.submission.widget.samples.toolbars.layout.ToolBarLayout
import com.starception.submission.widget.samples.utils.ActionUtils.actionStartDemoActivity

/**
 * A widget to demonstrate the [ToolBarLayout].
 */
class ToolBarAppWidget : GlanceAppWidget() {
    // Unlike the "Single" size mode, using "Exact" allows us to have better control over rendering in
    // different sizes. And, unlike the "Responsive" mode, it doesn't cause several views for each
    // supported size to be held in the widget host's memory.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = PrayerWidgetColors) {
                WidgetContent()
            }
        }
    }

    @Composable
    fun WidgetContent() {
        ToolBarLayout(
            appName = "App name",
            appIconRes = R.drawable.flaticon_mosque_uicon,
            headerButton = ToolBarButton(
                iconRes = R.drawable.sample_mark_prayed_icon,
                contentDescription = "Mark prayed",
                onClick = actionStartDemoActivity("add button")
            ),
            buttons = listOf(
                ToolBarButton(
                    iconRes = R.drawable.sample_qibla_icon,
                    contentDescription = "Qibla",
                    onClick = actionStartDemoActivity("mic button")
                ),
                ToolBarButton(
                    iconRes = CoreUiR.drawable.flaticon_magic_book_9061096,
                    contentDescription = "Quran",
                    onClick = actionStartDemoActivity("share button")
                ),
                ToolBarButton(
                    iconRes = R.drawable.sample_dua_icon,
                    contentDescription = "Duas",
                    onClick = actionStartDemoActivity("video button")
                ),
                ToolBarButton(
                    iconRes = R.drawable.sample_tasbih_icon,
                    contentDescription = "Tasbih",
                    onClick = actionStartDemoActivity("camera button")
                )
            )
        )
    }
}

/**
 * Receiver registered in the manifest for the [ToolBarAppWidget].
 */
class ToolBarAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ToolBarAppWidget()
}
