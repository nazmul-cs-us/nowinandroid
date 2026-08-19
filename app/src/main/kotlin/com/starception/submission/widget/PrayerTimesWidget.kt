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
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import android.widget.RemoteViews
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.starception.submission.MainActivity
import com.starception.submission.R

// Launcher cells resolve to (70 * n - 30) dp, so these are the real 2x1, 2x2, 4x2, 4x3
// and 5x4 footprints. Glance picks the largest bucket that still fits, which means an
// in-between drop (a 3x2, say) lands on the 2x2 layout and simply has room to spare
// rather than overflowing.
internal val TINY = DpSize(110.dp, 40.dp)
internal val SMALL = DpSize(110.dp, 110.dp)
internal val WIDE = DpSize(250.dp, 110.dp)
internal val LARGE = DpSize(250.dp, 180.dp)
internal val XLARGE = DpSize(320.dp, 250.dp)

/**
 * Home-screen prayer times widget.
 *
 * Glance renders to RemoteViews, so none of the app's screen composables can be reused
 * here — every layout below is written against the Glance layout primitives, which have
 * no `Modifier.weight` on the cross axis, no intrinsic measurement, and no text
 * auto-sizing. That is why each size bucket gets a hand-written layout rather than one
 * elastic layout: shrinking a single design across a 2x1 strip and a 5x4 panel would
 * either clip the prayer list or leave the small sizes unreadable.
 *
 * Every subclass renders the identical layouts and stays freely resizable; they exist
 * only so the widget picker lists one entry per size, each landing at its own footprint.
 * The size a subclass drops at is declared in its appwidget-provider XML
 * (targetCellWidth/Height), not here — Glance always chooses the layout from the space
 * it is actually given.
 */
abstract class BasePrayerTimesWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(TINY, SMALL, WIDE, LARGE, XLARGE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Loaded before provideContent so the data read happens once per update rather
        // than once per size bucket — Responsive mode composes the content for every
        // declared size to build its RemoteViews set.
        val state = loadPrayerWidgetState(context)

        provideContent {
            GlanceTheme(colors = PrayerWidgetColors) {
                when (state) {
                    PrayerWidgetState.Unavailable -> BareSurface { UnavailableContent() }
                    is PrayerWidgetState.Available -> when (LocalSize.current) {
                        // The two smallest sizes cannot carry the canonical layout: it
                        // opens with a title bar and a scrolling list, which at a 2x1
                        // leaves room for neither. They keep the compact hero, which is
                        // the only thing that fits there.
                        TINY -> BareSurface { TinyContent(state) }
                        SMALL -> BareSurface(Alignment.Vertical.Top) { SmallContent(state) }
                        // Everything with room renders through the layout ported from
                        // platform-samples, so it is identical to the sample widgets by
                        // construction rather than by imitation.
                        else -> PrayerActionListContent(state)
                    }
                }
            }
        }
    }
}

/** Picker entry "Next Prayer" — drops at 2x1. */
class PrayerTimesTinyWidget : BasePrayerTimesWidget()

/** Picker entry "Next Prayer Card" — drops at 2x2. */
class PrayerTimesSmallWidget : BasePrayerTimesWidget()

/** Picker entry "Prayer Times" — drops at 4x2. */
class PrayerTimesWidget : BasePrayerTimesWidget()

/** Picker entry "Prayer Times & Location" — drops at 4x3. */
class PrayerTimesLargeWidget : BasePrayerTimesWidget()

/** Picker entry "Prayer Times (Full Day)" — drops at 5x4. */
class PrayerTimesFullWidget : BasePrayerTimesWidget()

// ---------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------

/**
 * Rounded widget background with no chrome, for sizes too small for a title bar.
 *
 * [appWidgetBackground] is what lets the launcher clip the widget to the system corner
 * radius; without it the background paints square corners inside rounded ones.
 */
@Composable
private fun BareSurface(
    verticalAlignment: Alignment.Vertical = Alignment.Vertical.CenterVertically,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = verticalAlignment,
    ) {
        content()
    }
}


@Composable
private fun UnavailableContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = "Prayer times",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
        Text(
            text = "Open the app to set your location",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
    }
}

// ---------------------------------------------------------------------------------
// Size layouts
// ---------------------------------------------------------------------------------

/** 2x1 — one line. Only the next prayer fits, so nothing else is shown. */
@Composable
private fun TinyContent(state: PrayerWidgetState.Available) {
    val insight = state.insight

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        AnimatedMeteocon(prayer = state.nextPrayer, size = 26.dp)
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                // The tile's phase headline is the whole point of this widget: "Go to
                // Mosque" and "Make Time for" say something a bare prayer name does not.
                text = insight?.title ?: "${state.nextPrayer.name} ${state.nextPrayer.time}",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            if (insight != null) {
                Text(
                    text = insight.elapsed,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** 2x2 — the next prayer as a hero block, no list. */
@Composable
private fun ColumnScope.SmallContent(state: PrayerWidgetState.Available) {
    val insight = state.insight

    // Type is sized to fill the card rather than sit in it. Earlier passes moved a block
    // of empty space around — centred left gaps top and bottom, top-anchored left 45% at
    // the bottom, a weighted spacer left 51% in the middle — because four short lines at
    // 10-12sp cannot fill a 2x2 wherever they are placed. The fix is the type, not the
    // alignment: at these sizes the content occupies the card and stays legible at arm's
    // length, which is the distance a home-screen widget is actually read from.
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        AnimatedMeteocon(prayer = state.nextPrayer, size = 26.dp)
        Spacer(modifier = GlanceModifier.width(7.dp))
        Text(
            text = insight?.title ?: "Next prayer",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 2,
        )
    }

    if (insight != null) {
        Text(
            text = insight.elapsed,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
    }

    Spacer(modifier = GlanceModifier.defaultWeight())

    // The timeline the "Prayer now" tile draws: how far the clock has travelled from the
    // last prayer to the next. Lifting only the tile's text left the card with a hole in
    // the middle that no amount of alignment could close — four short lines cannot fill a
    // 2x2. This is the element that gives the tile its body, and it is real information
    // rather than padding.
    state.windowProgress?.let { progress ->
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth().height(4.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
    }

    // The next prayer carries its own name, so the large time is unambiguous — an
    // earlier version put "Make Time for Dhuhr" above a large Asr time with nothing
    // saying which prayer the number belonged to.
    Text(
        text = state.nextPrayer.name.uppercase(),
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
    )
    Text(
        text = state.nextPrayer.time,
        style = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
    Text(
        text = state.countdown,
        style = TextStyle(
            color = GlanceTheme.colors.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
    )
}


// ---------------------------------------------------------------------------------
// Components
// ---------------------------------------------------------------------------------


/**
 * The next prayer's Meteocon, animated.
 *
 * Glance has no animation of its own — its output is RemoteViews, which the launcher
 * inflates in its own process with no frame loop to drive. ViewFlipper is the one
 * animating widget on the RemoteViews allowlist: given autoStart and a flipInterval it
 * advances its children by itself, so handing it rasterised Lottie frames is what makes
 * a Meteocon actually move on the home screen.
 *
 * Falls back to the still bitmap whenever frames are missing, so a failed render costs
 * the motion rather than the icon.
 */
@Composable
private fun AnimatedMeteocon(prayer: WidgetPrayer, size: androidx.compose.ui.unit.Dp) {
    val frames = prayer.weatherFrames

    if (frames.size < 2) {
        prayer.weatherIcon?.let {
            Image(
                provider = ImageProvider(it),
                contentDescription = null,
                modifier = GlanceModifier.size(size),
            )
        }
        return
    }

    val remoteViews = RemoteViews(
        LocalContext.current.packageName,
        R.layout.widget_meteocon_flipper,
    ).apply {
        FRAME_VIEW_IDS.forEachIndexed { index, viewId ->
            // The layout has a fixed number of children; cycle the available frames over
            // them so a short render still fills every slot rather than leaving blanks
            // that would read as a stutter.
            setImageViewBitmap(viewId, frames[index % frames.size])
        }
    }

    AndroidRemoteViews(
        remoteViews = remoteViews,
        modifier = GlanceModifier.size(size),
    )
}

private val FRAME_VIEW_IDS = intArrayOf(
    R.id.meteocon_frame_0,
    R.id.meteocon_frame_1,
    R.id.meteocon_frame_2,
    R.id.meteocon_frame_3,
    R.id.meteocon_frame_4,
    R.id.meteocon_frame_5,
)


