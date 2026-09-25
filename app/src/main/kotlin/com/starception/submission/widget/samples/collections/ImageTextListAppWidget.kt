/*
 * Copyright 2026 The Android Open Source Project
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

package com.starception.submission.widget.samples.collections

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.starception.submission.R
import com.starception.submission.widget.StarceptionWidgetTheme
import com.starception.submission.widget.loadWidgetThemeSource
import com.starception.submission.widget.samples.collections.data.FakeImageTextListDataRepository
import com.starception.submission.widget.samples.collections.data.FakeImageTextListDataRepository.Companion.getImageTextListDataRepo
import com.starception.submission.widget.samples.collections.layout.ImageTextListItemData
import com.starception.submission.widget.samples.collections.layout.ImageTextListLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.starception.submission.core.ui.R as CoreUiR

/**
 * A sample [GlanceAppWidget] demonstrating the [ImageTextListLayout].
 *
 * Has ability to toggle between placeholder text and real-like text.
 */
class ImageTextListAppWidget : GlanceAppWidget() {
    // Unlike the "Single" size mode, using "Exact" allows us to have better control over rendering in
    // different sizes. And, unlike the "Responsive" mode, it doesn't cause several views for each
    // supported size to be held in the widget host's memory.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = getImageTextListDataRepo(id)

        val initialItems = withContext(Dispatchers.Default) {
            repo.load(context)
        }
        val themeSource = loadWidgetThemeSource(context)

        provideContent {
            StarceptionWidgetTheme(themeSource) {
                val items by repo.data().collectAsState(initial = initialItems)
                val coroutineScope = rememberCoroutineScope()

                key(LocalSize.current) {
                    WidgetContent(
                        items = items,
                        refreshAction = {
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    repo.refresh(context)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    @Composable
    fun WidgetContent(
        items: List<ImageTextListItemData>,
        refreshAction: () -> Unit,
    ) {
        val context = LocalContext.current

        ImageTextListLayout(
            items = items,
            title = context.getString(
                R.string.sample_text_and_image_list_app_widget_name,
            ),
            titleIconRes = CoreUiR.drawable.flaticon_magic_book_9061096,
            titleBarActionIconRes = R.drawable.sample_refresh_icon,
            titleBarActionIconContentDescription = context.getString(
                R.string.sample_refresh_icon_button_label,
            ),
            titleBarAction = refreshAction,
        )
    }
}

class ImageTextListAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ImageTextListAppWidget()

    @SuppressLint("RestrictedApi")
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach {
            FakeImageTextListDataRepository.cleanUp(AppWidgetId(appWidgetId = it))
        }
        super.onDeleted(context, appWidgetIds)
    }
}
