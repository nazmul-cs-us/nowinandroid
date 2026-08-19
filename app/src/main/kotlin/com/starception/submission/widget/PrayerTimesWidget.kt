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
import android.appwidget.AppWidgetManager
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
/** Matches the ported layouts' own widgetPadding so the two sit consistently. */
private val WIDGET_PADDING = 16.dp

internal val TINY = DpSize(110.dp, 40.dp)
internal val SMALL = DpSize(110.dp, 110.dp)
// Without this bucket everything from 110dp to 249dp wide fell back to SMALL, because
// Glance picks the largest declared size that fits and WIDE needs a full 250dp. A 3x3
// (213x179dp on One UI) therefore rendered the 2x2 hero stretched across twice the area
// it was drawn for, which is what made it look sparse and under-sized.
internal val MEDIUM = DpSize(180.dp, 170.dp)
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
        setOf(TINY, SMALL, MEDIUM, WIDE, LARGE, XLARGE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Loaded before provideContent so the data read happens once per update rather
        // than once per size bucket — Responsive mode composes the content for every
        // declared size to build its RemoteViews set.
        val state = loadPrayerWidgetState(context)

        // LocalSize reports the *declared* bucket, not what the launcher granted: a card
        // occupying 125x180dp still reports SMALL's 110x110dp. Sizing type or gating
        // content on LocalSize therefore silently under-fills every card that lands
        // between two buckets. The options bundle carries the real footprint.
        val granted = grantedSize(context, id)

        provideContent {
            GlanceTheme(colors = PrayerWidgetColors) {
                when (state) {
                    PrayerWidgetState.Unavailable -> BareSurface { UnavailableContent() }
                    is PrayerWidgetState.Available -> when (LocalSize.current) {
                        // The two smallest sizes cannot carry the canonical layout: it
                        // opens with a title bar and a scrolling list, which at a 2x1
                        // leaves room for neither. They keep the compact hero, which is
                        // the only thing that fits there.
                        TINY -> BareSurface(padding = 10.dp) { TinyContent(state) }
                        SMALL -> BareSurface(Alignment.Vertical.Top) { SmallContent(state, granted) }
                        // Tall enough for the canonical list, which fills the height
                        // with real rows rather than stretching five short lines.
                        MEDIUM -> PrayerActionListContent(state)
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

/**
 * The footprint the launcher actually gave this widget, or null if it has not reported
 * one yet. Falls back to LocalSize at the call site in that case.
 */
private fun grantedSize(context: Context, id: GlanceId): DpSize? = try {
    val manager = AppWidgetManager.getInstance(context)
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
    val options = manager.getAppWidgetOptions(appWidgetId)
    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    if (width > 0 && height > 0) DpSize(width.dp, height.dp) else null
} catch (e: Exception) {
    null
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
    // 16dp on every side, matching TextWithImageLayoutDimensions.widgetPadding in the
    // ported layouts — "padding that visually appears between the widget outline and
    // anything inside". These surfaces sit next to those on the same home screen, and
    // the previous 14dp horizontal against 12dp vertical read as an uneven border gap
    // beside them.
    padding: androidx.compose.ui.unit.Dp = WIDGET_PADDING,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(padding),
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
    // One line, and it answers the question the widget is named after. The previous
    // version showed the current prayer's phase headline over its elapsed time and never
    // named the next prayer at all — two stacked lines needing ~37dp inside a 2x1 that
    // has roughly 28dp of usable height, so it also clipped.
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        AnimatedMeteocon(prayer = state.nextPrayer, size = 20.dp)
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = state.nextPrayer.name,
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = state.nextPrayer.time,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = state.countdown,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
    }
}

/** 2x2 — the next prayer as a hero block, no list. */
@Composable
private fun ColumnScope.SmallContent(
    state: PrayerWidgetState.Available,
    granted: DpSize?,
) {
    val insight = state.insight

    // SMALL serves every width from 110dp up to 180dp, so no fixed type scale fits it:
    // 36sp was tuned against a 173dp card and truncated "6:51 PM" to "6:51 P..." on the
    // 125dp card the launcher actually granted. Deriving the size from the width really
    // available ends that guessing.
    //
    // A digit in this face occupies roughly 0.58em and the longest time string is eight
    // characters ("12:23 PM"), so the largest size that still fits is
    // contentWidth / (8 * 0.58), clamped to stay readable at either end.
    val cardSize = granted ?: LocalSize.current
    val contentWidth = cardSize.width - (WIDGET_PADDING * 2)
    val timeSize = (contentWidth.value / (8 * 0.58f)).coerceIn(20f, 34f).sp
    val headlineSize = (timeSize.value * 0.44f).coerceIn(12f, 16f).sp
    val supportingSize = (timeSize.value * 0.38f).coerceIn(10f, 13f).sp

    Text(
        text = insight?.shortTitle ?: "Next prayer",
        style = TextStyle(
            color = GlanceTheme.colors.primary,
            fontSize = headlineSize,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 2,
    )

    if (insight != null) {
        Text(
            text = insight.elapsed,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = supportingSize,
            ),
            maxLines = 1,
        )
    }

    state.windowProgress?.let { progress ->
        Spacer(modifier = GlanceModifier.height(8.dp))
        ExpressiveProgressBar(progress = progress, width = contentWidth)
    }

    Spacer(modifier = GlanceModifier.height(10.dp))

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = state.nextPrayer.name.uppercase(),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = supportingSize,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        AnimatedMeteocon(prayer = state.nextPrayer, size = 20.dp)
    }
    Text(
        text = state.nextPrayer.time,
        style = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = timeSize,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
    Text(
        text = state.countdown,
        style = TextStyle(
            color = GlanceTheme.colors.primary,
            fontSize = supportingSize,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
    )

    // The prayers after the next one. At 125x180dp the width clamp caps the headline at
    // ~20sp, leaving roughly half the height spare — no alignment can fill that, only
    // content can. These rows are dropped when the card is short enough not to need them.
    // Wrapping past the end of the day keeps this at two rows. Taking only what is left
    // today meant that after Maghrib just one prayer remained, and the weighted spacer
    // stranded that single row against the bottom edge. The wrapped entries are
    // tomorrow's, which is what "upcoming" means once the day's prayers are done.
    // Row count is derived from the height that is actually left rather than fixed at
    // two. The hero alone is ~107dp; a 166dp card has ~134dp usable, so two rows (~42dp)
    // overflowed and the second was clipped against the bottom edge.
    val afterNext = state.prayers.dropWhile { !it.isNext }.drop(1)
    val upcomingRows = when {
        cardSize.height >= 210.dp -> 3
        cardSize.height >= 180.dp -> 2
        cardSize.height >= 150.dp -> 1
        else -> 0
    }
    val upcoming = (afterNext + state.prayers).take(upcomingRows)

    if (upcoming.isNotEmpty()) {
        // A weighted spacer here pinned the rows to the bottom edge, so any overflow was
        // clipped rather than visible. A fixed gap lets them sit under the countdown.
        Spacer(modifier = GlanceModifier.height(10.dp))
        upcoming.forEach { prayer ->
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    text = prayer.name,
                    style = TextStyle(
                        color = GlanceTheme.colors.outline,
                        fontSize = supportingSize,
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = prayer.time,
                    style = TextStyle(
                        color = GlanceTheme.colors.outline,
                        fontSize = supportingSize,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Linear progress in the Material 3 Expressive style: rounded caps on both the active
 * segment and the remaining track, separated by a gap.
 *
 * Glance's own LinearProgressIndicator renders a plain RemoteViews ProgressBar — square
 * ends, no gap — and the Compose Material 3 expressive indicator cannot run in a widget
 * at all, since a widget's content is RemoteViews with no Compose runtime behind it.
 * Building it from two rounded boxes is the only way to get the shape here.
 *
 * Widths are computed rather than weighted because Glance's defaultWeight() only splits
 * space equally; there is no fractional weight to express "62% of the row".
 */
@Composable
private fun ExpressiveProgressBar(progress: Float, width: androidx.compose.ui.unit.Dp) {
    val gap = 4.dp
    val usable = (width.value - gap.value).coerceAtLeast(0f)
    val active = (usable * progress.coerceIn(0f, 1f)).dp
    val remaining = (usable - active.value).dp

    Row(modifier = GlanceModifier.fillMaxWidth()) {
        if (active.value > 0f) {
            Box(
                modifier = GlanceModifier
                    .width(active)
                    .height(6.dp)
                    .cornerRadius(3.dp)
                    .background(GlanceTheme.colors.primary),
            ) {}
        }
        if (remaining.value > 0f) {
            Spacer(modifier = GlanceModifier.width(gap))
            Box(
                modifier = GlanceModifier
                    .width(remaining)
                    .height(6.dp)
                    .cornerRadius(3.dp)
                    .background(GlanceTheme.colors.surfaceVariant),
            ) {}
        }
    }
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


