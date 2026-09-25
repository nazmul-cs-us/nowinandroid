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

package com.starception.submission.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import com.starception.submission.R
import com.starception.submission.widget.samples.collections.ActionListAppWidget
import com.starception.submission.widget.samples.collections.ActionListAppWidgetAppWidgetReceiver
import com.starception.submission.widget.samples.collections.CheckListAppWidget
import com.starception.submission.widget.samples.collections.CheckListAppWidgetReceiver
import com.starception.submission.widget.samples.collections.ImageGridAppWidget
import com.starception.submission.widget.samples.collections.ImageGridAppWidgetReceiver
import com.starception.submission.widget.samples.collections.ImageTextListAppWidget
import com.starception.submission.widget.samples.collections.ImageTextListAppWidgetReceiver
import com.starception.submission.widget.samples.text.LongTextAppWidget
import com.starception.submission.widget.samples.text.LongTextAppWidgetReceiver
import com.starception.submission.widget.samples.text.TextWithImageAppWidget
import com.starception.submission.widget.samples.text.TextWithImageAppWidgetReceiver
import com.starception.submission.widget.samples.toolbars.ExpressiveToolbarAppWidget
import com.starception.submission.widget.samples.toolbars.ExpressiveToolbarAppWidgetReceiver
import com.starception.submission.widget.samples.toolbars.SearchToolBarAppWidget
import com.starception.submission.widget.samples.toolbars.SearchToolBarAppWidgetReceiver
import com.starception.submission.widget.samples.toolbars.ToolBarAppWidget
import com.starception.submission.widget.samples.toolbars.ToolBarAppWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One widget provider paired with the exact Glance widget and its launcher drop footprint. */
data class WidgetPreviewSpec(
    val key: String,
    val receiverClassName: String,
    val widget: GlanceAppWidget,
    val size: DpSize,
    val source: WidgetPreviewSource,
    @DrawableRes val previewImage: Int,
    @LayoutRes val previewLayout: Int = 0,
)

/**
 * Chooses the most faithful representation that can also be rendered outside a launcher host.
 *
 * Collection RemoteViews need a real AppWidgetHost to attach their adapters; applying them to an
 * AndroidView only produces an empty list. Prayer cards also contain asynchronously rasterised
 * artwork. Their packaged images are captures of the actual widgets, while ordinary widgets can
 * safely be composed live and collection widgets use their complete host-compatible layouts.
 */
enum class WidgetPreviewSource { LIVE_GLANCE, PACKAGED_IMAGE, HOST_LAYOUT }

/**
 * The single source of truth for in-app, launcher, and generated static previews.
 *
 * Samsung's 5-column grid grants roughly 81dp per column and 111dp per row after launcher
 * gutters. These are the same footprints used by the widget's debug resize/preview tooling.
 */
val widgetPreviewSpecs: List<WidgetPreviewSpec> by lazy {
    listOf(
        WidgetPreviewSpec(
            "prayer_times",
            PrayerTimesWidgetReceiver::class.java.name,
            PrayerTimesWidget(),
            DpSize(325.dp, 222.dp),
            WidgetPreviewSource.PACKAGED_IMAGE,
            R.drawable.prayer_widget_preview_medium_image,
        ),
        WidgetPreviewSpec(
            "prayer_next",
            PrayerNextWidgetReceiver::class.java.name,
            PrayerNextWidget(),
            DpSize(325.dp, 222.dp),
            WidgetPreviewSource.PACKAGED_IMAGE,
            R.drawable.prayer_widget_preview_next_image,
        ),
        WidgetPreviewSpec(
            "prayer_timeline",
            PrayerTimelineWidgetReceiver::class.java.name,
            PrayerTimelineWidget(),
            DpSize(325.dp, 222.dp),
            WidgetPreviewSource.PACKAGED_IMAGE,
            R.drawable.prayer_widget_preview_timeline_image,
        ),
        WidgetPreviewSpec(
            "prayer_devotional",
            PrayerDevotionalWidgetReceiver::class.java.name,
            PrayerDevotionalWidget(),
            DpSize(325.dp, 222.dp),
            WidgetPreviewSource.PACKAGED_IMAGE,
            R.drawable.prayer_widget_preview_devotional_image,
        ),
        WidgetPreviewSpec(
            "prayer_tiny",
            PrayerTimesTinyWidgetReceiver::class.java.name,
            PrayerTimesTinyWidget(),
            DpSize(162.dp, 111.dp),
            WidgetPreviewSource.PACKAGED_IMAGE,
            R.drawable.prayer_widget_preview_tiny_image,
        ),
        WidgetPreviewSpec(
            "prayer_small",
            PrayerTimesSmallWidgetReceiver::class.java.name,
            PrayerTimesSmallWidget(),
            DpSize(162.dp, 222.dp),
            WidgetPreviewSource.PACKAGED_IMAGE,
            R.drawable.prayer_widget_preview_small_image,
        ),
        WidgetPreviewSpec(
            "prayer_large",
            PrayerTimesLargeWidgetReceiver::class.java.name,
            PrayerTimesLargeWidget(),
            DpSize(325.dp, 334.dp),
            WidgetPreviewSource.PACKAGED_IMAGE,
            R.drawable.prayer_widget_preview_large_image,
        ),
        WidgetPreviewSpec(
            "prayer_full",
            PrayerTimesFullWidgetReceiver::class.java.name,
            PrayerTimesFullWidget(),
            DpSize(406.dp, 445.dp),
            WidgetPreviewSource.PACKAGED_IMAGE,
            R.drawable.prayer_widget_preview_full_image,
        ),
        WidgetPreviewSpec(
            "quick_actions",
            ExpressiveToolbarAppWidgetReceiver::class.java.name,
            ExpressiveToolbarAppWidget(),
            DpSize(162.dp, 222.dp),
            WidgetPreviewSource.PACKAGED_IMAGE,
            R.drawable.sample_expressive_toolbar_preview,
        ),
        WidgetPreviewSpec(
            "prayer_toolbar",
            ToolBarAppWidgetReceiver::class.java.name,
            ToolBarAppWidget(),
            DpSize(325.dp, 111.dp),
            WidgetPreviewSource.LIVE_GLANCE,
            R.drawable.sample_toolbar_preview,
        ),
        WidgetPreviewSpec(
            "search_quran",
            SearchToolBarAppWidgetReceiver::class.java.name,
            SearchToolBarAppWidget(),
            DpSize(162.dp, 222.dp),
            WidgetPreviewSource.LIVE_GLANCE,
            R.drawable.sample_search_toolbar_preview,
        ),
        WidgetPreviewSpec(
            "daily_reminder",
            LongTextAppWidgetReceiver::class.java.name,
            LongTextAppWidget(),
            DpSize(162.dp, 222.dp),
            WidgetPreviewSource.HOST_LAYOUT,
            R.drawable.sample_long_text_preview,
            R.layout.daily_reminder_widget_preview,
        ),
        WidgetPreviewSpec(
            "prayer_insight",
            TextWithImageAppWidgetReceiver::class.java.name,
            TextWithImageAppWidget(),
            DpSize(325.dp, 222.dp),
            WidgetPreviewSource.LIVE_GLANCE,
            R.drawable.sample_text_image_preview,
        ),
        WidgetPreviewSpec(
            "quran_surahs",
            ImageTextListAppWidgetReceiver::class.java.name,
            ImageTextListAppWidget(),
            DpSize(325.dp, 222.dp),
            WidgetPreviewSource.HOST_LAYOUT,
            R.drawable.sample_image_text_list_preview,
            R.layout.widget_preview_quran_surahs,
        ),
        WidgetPreviewSpec(
            "prayer_checklist",
            CheckListAppWidgetReceiver::class.java.name,
            CheckListAppWidget(),
            DpSize(162.dp, 222.dp),
            WidgetPreviewSource.HOST_LAYOUT,
            R.drawable.sample_check_list_preview,
            R.layout.widget_preview_prayer_checklist,
        ),
        WidgetPreviewSpec(
            "prayer_settings",
            ActionListAppWidgetAppWidgetReceiver::class.java.name,
            ActionListAppWidget(),
            DpSize(325.dp, 222.dp),
            WidgetPreviewSource.HOST_LAYOUT,
            R.drawable.sample_action_list_preview,
            R.layout.widget_preview_prayer_settings,
        ),
        WidgetPreviewSpec(
            "quran_grid",
            ImageGridAppWidgetReceiver::class.java.name,
            ImageGridAppWidget(),
            DpSize(325.dp, 334.dp),
            WidgetPreviewSource.HOST_LAYOUT,
            R.drawable.sample_image_grid_preview,
            R.layout.widget_preview_quran_grid,
        ),
    )
}

fun widgetPreviewSpec(receiverClassName: String): WidgetPreviewSpec? =
    widgetPreviewSpecs.firstOrNull { it.receiverClassName == receiverClassName }

private fun WidgetPreviewSpec.syntheticId(slot: Int): AppWidgetId =
    AppWidgetId(-30_000 - (slot * 20_000) - (receiverClassName.hashCode() and 0x3fff))

@OptIn(ExperimentalGlanceApi::class)
suspend fun WidgetPreviewSpec.composePreview(
    context: Context,
    slot: Int = 0,
): RemoteViews =
    withContext(Dispatchers.Default) {
        widget.compose(
            context = context.applicationContext,
            id = syntheticId(slot),
            size = size,
        )
    }

/** Builds a complete preview that is safe both in-app and inside the launcher picker. */
suspend fun WidgetPreviewSpec.previewRemoteViews(
    context: Context,
    slot: Int = 0,
): RemoteViews = when (source) {
    WidgetPreviewSource.PACKAGED_IMAGE -> imagePreviewRemoteViews(context)
    WidgetPreviewSource.HOST_LAYOUT -> RemoteViews(context.packageName, previewLayout)
    WidgetPreviewSource.LIVE_GLANCE -> runCatching {
        composePreview(context, slot)
    }.getOrElse {
        Log.w("WidgetPreviewCatalog", "Live preview failed for $key; using image", it)
        imagePreviewRemoteViews(context)
    }
}

private fun WidgetPreviewSpec.imagePreviewRemoteViews(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_preview_image).apply {
        setImageViewResource(R.id.widget_preview_image, previewImage)
    }

/** Publishes actual Glance RemoteViews to Android 15+ launchers instead of approximation XML. */
object WidgetPreviewRegistrar {
    private const val TAG = "WidgetPreviewRegistrar"

    @Volatile private var registeredForProcess = false

    suspend fun register(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        if (registeredForProcess) return
        registeredForProcess = true
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        widgetPreviewSpecs.forEach { spec ->
            runCatching {
                val views = spec.previewRemoteViews(appContext, slot = 2)
                val accepted = manager.setWidgetPreview(
                    ComponentName(appContext.packageName, spec.receiverClassName),
                    APP_WIDGET_PROVIDER_CATEGORY_HOME_SCREEN,
                    views,
                )
                Log.d(TAG, "${spec.key}: launcher preview accepted=$accepted")
            }.onFailure { error ->
                Log.e(TAG, "Unable to publish ${spec.key} preview", error)
            }
        }
    }
}

// Kept as a named constant so the platform category used by every preview is unambiguous.
private const val APP_WIDGET_PROVIDER_CATEGORY_HOME_SCREEN =
    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
