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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.content.res.Configuration
import android.content.Context
import android.util.TypedValue
import androidx.annotation.FontRes
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent
import androidx.glance.action.clickable
import android.widget.RemoteViews
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColorProvider
import com.starception.submission.MainActivity
import com.starception.submission.R

/** The reference keeps its inner cards close to the outer shell: roughly 2% per side. */
private val WIDGET_PADDING = 8.dp
private val ReferenceWidgetBackground = ColorProvider(Color(0xFFFFFCF8))
private val ReferenceForest = ColorProvider(Color(0xFF0B4D43))
private val ReferenceForestOn = ColorProvider(Color(0xFFFFFFFF))
private val ReferenceHeroText = ColorProvider(Color(0xFFF4F1E6))
private val ReferenceInk = ColorProvider(Color(0xFF18352E))
private val ReferenceMuted = ColorProvider(Color(0xFF4F665D))
private val ReferenceQuote = ColorProvider(Color(0xFF24463D))
private val ReferenceQuoteMark = ColorProvider(Color(0xFFA9A08D))
private val ReferenceHeaderPill = ColorProvider(Color(0xFFF0F2F5))
private val ReferenceTopHeaderPill = ColorProvider(Color(0xFFF0F1F7))
private val ReferenceTopHeaderInk = ColorProvider(Color(0xFF0A174E))
private val ReferenceTopHeaderMuted = ColorProvider(Color(0xFF535979))
private val ReferencePinSurface = ColorProvider(Color(0xFFE4EFEA))
private val ReferenceSun = ColorProvider(Color(0xFFF5B83D))
private val ReferenceWarm = ColorProvider(Color(0xFFF08B61))
private val ReferenceTwilight = ColorProvider(Color(0xFF7379B9))
private val ReferenceNight = ColorProvider(Color(0xFF263F87))
private val ReferenceUpcoming = ColorProvider(Color(0xFF3C82D4))
private val ReferenceActiveChip = ColorProvider(Color(0xFF063C34))

/** Width-matched reference composition: 132 hero + 142 timeline + 115 devotional + gaps. */
private val REFERENCE_DESIGN_CONTENT_HEIGHT = 401.dp
private val REFERENCE_DESIGN_HERO_HEIGHT = 132.dp
private val REFERENCE_DESIGN_TIMELINE_HEIGHT = 142.dp
private val REFERENCE_DESIGN_DEVOTIONAL_HEIGHT = 115.dp

// The 35dp pin plus the measured top/bottom breathing room yields a 49dp visible row;
// Scaffold contributes the remaining title-bar clearance. Keep the 58dp height budget
// synchronized so the hero begins immediately below it.
private val HEADER_TOUCH_TARGET_TOP_PADDING = 8.dp
private val HEADER_TOUCH_TARGET_BOTTOM_PADDING = 6.dp
private val TITLE_BAR_HEIGHT = 58.dp

// Titled layouts finish with an inset card (the next-prayer or timetable surface). Its
// final row contributes its own visual clearance, so a full 16dp outer bottom inset would
// double the visible space below the last item. Four dp keeps the perceived content edge
// aligned with the header's 16dp visible top edge while retaining separation at the shell.
private val TITLED_WIDGET_BOTTOM_PADDING = 4.dp

/** Lines the phase title may wrap to; the ramp will not pick a size needing more. */
private const val TITLE_MAX_LINES = 2

/** Measurement constants retained by the narrow fallback renderer. */
private const val TINY_GROUP_GAP = 16f
private const val ICON_ROOM = 28f

/** Floor for the hero's internal gaps, before any leftover height is shared out. */
private val GROUP_GAP_MIN = 10.dp

/**
 * Narrowest drawable width the title bar is worth showing at.
 *
 * Its fixed furniture is the start inset, pin, gap, refresh target and end inset — about
 * 100dp before a single character of
 * the place name. Below roughly 200dp the name has less room than the chrome around it,
 * and the bare surface makes better use of the card.
 *
 * Measured against LocalSize, which on One UI is the grid cell rather than the drawn size:
 * that launcher scales the whole rendering to about 83%, so this is ~167dp on screen.
 */
private val TITLE_BAR_MIN_WIDTH = 200.dp

/** The single-line layout's own inset — tighter than [WIDGET_PADDING], which it cannot afford. */
private val SINGLE_ROW_WIDGET_PADDING = 8.dp
private val SINGLE_ROW_WIDGET_MAX_HEIGHT = 72.dp

/** [ExpressiveProgressBar]'s drawn height, which the height budget has to account for. */
private const val PROGRESS_BAR_HEIGHT = 6f

// The least *content* height — card height already less the surrounding surface's insets
// — that the hero is worth drawing in: its four lines at the lowest rung of the ramp
// (14/12/11/13sp), plus the progress bar and the two minimum gaps. Below this the
// horizontally reflowed one-row hero is used instead, because the vertical hero would clip.
private val HERO_MIN_HEIGHT = 100.dp

/**
 * Height at which useful supporting content replaces expanding blank hero space.
 *
 * Measured against the content box after the widget's uniform outer inset is removed.
 */
// Below this height there is not enough room for five useful schedule rows. Keep the
// prayer hero instead of selecting a cramped/partial timetable; once the schedule does
// fit, its compact renderer preserves the same cards, icons and columns as the full size.
private val EXPANDED_CONTENT_MIN_HEIGHT = 260.dp
private val FULL_SCHEDULE_MIN_HEIGHT = 300.dp

/**
 * Minimum drawable area for the complete hierarchy from the supplied reference.
 *
 * The reference gives the current-prayer timeline, next-prayer card and all five rows
 * their own sections. Below this height those sections would have to clip, so the
 * existing compact reflows remain in use instead.
 */
private val REFERENCE_LAYOUT_MIN_HEIGHT = 390.dp
private val REFERENCE_LAYOUT_MIN_WIDTH = 280.dp
private val REFERENCE_MEDIUM_MIN_HEIGHT = 260.dp
private val REFERENCE_COMPACT_MIN_HEIGHT = 90.dp
private val REFERENCE_COMPACT_MIN_WIDTH = 200.dp

/** Lift under the hero's closing countdown, which sits larger and lower than list type. */
private val HERO_BOTTOM_CLEARANCE = 2.dp

/**
 * Reads the launcher's current widget bounds without relying on Glance's cached LocalSize.
 *
 * Android's option keys describe the portrait footprint as min-width/max-height and the
 * landscape footprint as max-width/min-height. A zero dimension means the host did not
 * publish useful bounds, in which case the caller falls back to LocalSize.
 */
private fun hostReportedWidgetSize(context: Context, glanceId: GlanceId): DpSize? {
    val appWidgetId = (glanceId as? AppWidgetId)?.appWidgetId ?: return null
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    val landscape = context.resources.configuration.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    val width = options.getInt(
        if (landscape) {
            AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
        } else {
            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
        },
    )
    val height = options.getInt(
        if (landscape) {
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
        } else {
            AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
        },
    )
    return if (width > 0 && height > 0) DpSize(width.dp, height.dp) else null
}

/**
 * Home-screen prayer times widget.
 *
 * Glance renders to RemoteViews, so none of the app's screen composables can be reused
 * here — every layout below is written against the Glance layout primitives, which have
 * no `Modifier.weight` on the cross axis, no intrinsic measurement, and no text
 * auto-sizing. With no auto-sizing to lean on, the layout derives its own type scale
 * from the granted footprint (see [PrayerHeroContent]); every x1 size carries the same
 * information in a compact horizontal or stacked reflow.
 *
 * Every subclass renders the identical layouts and stays freely resizable; they exist
 * only so the widget picker lists one entry per size, each landing at its own footprint.
 * The size a subclass drops at is declared in its appwidget-provider XML
 * (targetCellWidth/Height), not here — Glance always chooses the layout from the space
 * it is actually given.
 */
abstract class BasePrayerTimesWidget : GlanceAppWidget() {

    // Exact, not Responsive, and for the same reason the ported widgets use it: with
    // Responsive, Glance composes once per declared bucket and the host picks the
    // nearest, so a 125dp card and a 179dp card both render the identical SMALL
    // composition and nothing adapts in between. Exact composes for the size actually
    // granted, which makes LocalSize the real footprint and lets the layout scale
    // continuously with the launcher's grid. It also keeps one RemoteViews in the host's
    // memory rather than one per bucket.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Loaded outside provideContent so the data read is not tied to recomposition.
        val state = loadPrayerWidgetState(context)
        val themeSource = loadWidgetThemeSource(context)

        provideContent {
            StarceptionWidgetTheme(
                source = themeSource,
                // This editorial widget intentionally follows the supplied light artwork
                // when the global widget background type is Basic.
                basicBackgroundColor = Color(0xFFFFFCF8),
            ) {
                when (state) {
                    PrayerWidgetState.Unavailable -> {
                        val size = hostReportedWidgetSize(context, id) ?: LocalSize.current
                        val singleRow = size.height <= SINGLE_ROW_WIDGET_MAX_HEIGHT
                        BareSurface(
                            padding = if (singleRow) {
                                SINGLE_ROW_WIDGET_PADDING
                            } else {
                                WIDGET_PADDING
                            },
                        ) {
                            UnavailableContent(singleRow = singleRow)
                        }
                    }
                    is PrayerWidgetState.Available -> {
                        // One UI 8 currently hands Glance a stale 405x98 LocalSize when a
                        // resized widget refreshes, even though AppWidgetManager still
                        // reports the correct 405x334 footprint. Prefer the provider
                        // options when present so refreshes cannot collapse an x3/x4 card
                        // back to its original picker height.
                        // Resolve this inside the composition: resizing can reuse the
                        // same Glance session, so capturing it above provideContent would
                        // freeze whichever intermediate row-span the drag first crossed.
                        val size = hostReportedWidgetSize(context, id) ?: LocalSize.current
                        // Each surface insets the card by a different amount, so what the
                        // hero actually gets is worked out here — the hero cannot see
                        // which surface wrapped it. Deciding the layout from the same two
                        // figures that are then handed to it is what keeps the choice and
                        // the sizing from disagreeing.
                        val innerWidth = (size.width - (WIDGET_PADDING * 2)).coerceAtLeast(1.dp)
                        // The title bar owns the top inset. Titled content ends in an
                        // inset surface, so its smaller shell inset avoids double-padding
                        // below the final row.
                        val titledHeight =
                            (size.height - TITLE_BAR_HEIGHT - TITLED_WIDGET_BOTTOM_PADDING)
                                .coerceAtLeast(1.dp)
                        val bareHeight =
                            (size.height - (WIDGET_PADDING * 2)).coerceAtLeast(1.dp)
                        when {
                            // A real one-row footprint cannot carry a header plus a body.
                            // Use the launcher's live height rather than its nominal x1
                            // label: display scaling and grid density change the actual dp.
                            size.height <= SINGLE_ROW_WIDGET_MAX_HEIGHT -> {
                                val singleRowWidth = (
                                    size.width - (SINGLE_ROW_WIDGET_PADDING * 2)
                                    ).coerceAtLeast(1.dp)
                                val singleRowHeight = (
                                    size.height - (SINGLE_ROW_WIDGET_PADDING * 2)
                                    ).coerceAtLeast(1.dp)
                                BareSurface(
                                    padding = SINGLE_ROW_WIDGET_PADDING,
                                ) {
                                    if (singleRowWidth >= REFERENCE_COMPACT_MIN_WIDTH) {
                                        ReferencePrayerHero(
                                            state = state,
                                            width = singleRowWidth,
                                            height = singleRowHeight,
                                        )
                                    } else {
                                        NextPrayerStripContent(
                                            state = state,
                                            contentSize = DpSize(singleRowWidth, singleRowHeight),
                                        )
                                    }
                                }
                            }

                            // A shallow multi-row or compact-square footprint still has
                            // enough room for a header and current/next prayer summary.
                            bareHeight < HERO_MIN_HEIGHT -> {
                                if (innerWidth >= REFERENCE_COMPACT_MIN_WIDTH) {
                                    BareSurface(padding = WIDGET_PADDING) {
                                        ReferencePrayerHero(
                                            state = state,
                                            width = innerWidth,
                                            height = bareHeight,
                                        )
                                    }
                                } else {
                                    CompactPrayerSurface(
                                        state = state,
                                        contentSize = DpSize(innerWidth, bareHeight),
                                    )
                                }
                            }

                            // Everything taller renders the same hero. Only the surface
                            // around it differs: the title bar has to earn its 52dp, so it
                            // needs both the width to show a title and enough height left
                            // afterwards for the hero to still fit. Gating on width alone
                            // handed a 4x1 a title bar over a zero-height content box.
                            //
                            // The width threshold is what the bar's own contents need —
                            // padding, pin, a readable place name and the refresh target —
                            // not a cell size. It was 230dp when this compared against the
                            // granted cell; measuring the drawable area instead made that
                            // the same test at a different scale, and a 3-column card
                            // silently lost its header.
                            size.width >= TITLE_BAR_MIN_WIDTH && titledHeight >= HERO_MIN_HEIGHT -> {
                                val useReferenceLayout =
                                    innerWidth >= REFERENCE_COMPACT_MIN_WIDTH &&
                                        titledHeight >= REFERENCE_COMPACT_MIN_HEIGHT
                                TitledSurface(
                                    state = state,
                                    detailedHeader = innerWidth >= REFERENCE_LAYOUT_MIN_WIDTH,
                                ) {
                                    if (useReferenceLayout) {
                                        ReferencePrayerContent(
                                            state = state,
                                            contentSize = DpSize(innerWidth, titledHeight),
                                        )
                                    } else {
                                        AdaptivePrayerContent(
                                            state = state,
                                            contentSize = DpSize(innerWidth, titledHeight),
                                        )
                                    }
                                }
                            }

                            else -> BareSurface(Alignment.Vertical.Top) {
                                if (innerWidth >= REFERENCE_COMPACT_MIN_WIDTH) {
                                    ReferencePrayerContent(
                                        state = state,
                                        contentSize = DpSize(innerWidth, bareHeight),
                                    )
                                } else {
                                    AdaptivePrayerContent(
                                        state = state,
                                        contentSize = DpSize(innerWidth, bareHeight),
                                    )
                                }
                            }
                        }
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
    // The supplied prayer reference uses a much tighter shell inset than the sample
    // widgets: about 2% of its width, which resolves to 8dp at this footprint.
    padding: androidx.compose.ui.unit.Dp = WIDGET_PADDING,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(padding),
        verticalAlignment = verticalAlignment,
    ) {
        content()
    }
}

/** The Daily Reminder header language compressed to fit a single widget row. */
@Composable
private fun CompactPrayerSurface(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    val context = LocalContext.current
    val transparentHeader = !LocalWidgetAppearance.current.showBackground
    val transparentForeground = LocalTransparentWidgetForeground.current
    val headerInk = if (transparentHeader) {
        transparentForeground.primary
    } else {
        GlanceTheme.colors.onSurface
    }
    val headerIcon = if (transparentHeader) {
        transparentForeground.primary
    } else {
        GlanceTheme.colors.primary
    }
    val headerAction = if (transparentHeader) {
        transparentForeground.secondary
    } else {
        GlanceTheme.colors.secondary
    }
    val width = contentSize.width.value
    val wide = width >= 230f
    val headerTitle = state.place
    val titleRoom = (width - 62f).coerceAtLeast(1f)
    val titleSize = WidgetTypography
        .fittingSize(context, headerTitle, titleRoom, bold = true)
        .coerceAtMost(15f)
    val elapsed = state.insight?.elapsed ?: state.nextPrayer.time
    val countdown = state.countdown.removePrefix("in ").trim()
    val currentTitle = state.insight?.title ?: "Prayer now"
    val next = "${state.nextPrayer.name} · $countdown"
    val currentSize = WidgetTypography
        .fittingSize(context, currentTitle, width * 0.55f, bold = true)
        .coerceAtMost(15f)
    val elapsedSize = WidgetTypography
        .fittingSize(context, elapsed, width * 0.55f, bold = true)
        .coerceAtMost(14f)
    val nextSize = WidgetTypography
        .fittingSize(context, next, width * 0.40f, bold = true)
        .coerceAtMost(14.5f)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(WIDGET_PADDING),
    ) {
        if (width < 150f) {
            NarrowSquarePrayerContent(state = state, contentSize = contentSize)
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_flaticon_location_marker),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(headerIcon),
                    modifier = GlanceModifier.size(22.dp),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                WidgetText(
                    text = headerTitle,
                    size = titleSize.sp,
                    color = headerInk,
                    weight = if (transparentHeader) {
                        WidgetFontWeight.ShadowBold
                    } else {
                        WidgetFontWeight.Medium
                    },
                    modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Image(
                    provider = ImageProvider(R.drawable.sample_refresh_icon),
                    contentDescription = "Refresh prayer times",
                    colorFilter = ColorFilter.tint(headerAction),
                    modifier = GlanceModifier
                        .size(24.dp)
                        .clickable(actionRunCallback<RefreshPrayerWidgetAction>()),
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WidgetText(
                        text = currentTitle,
                        size = currentSize.sp,
                        color = GlanceTheme.colors.onSurface,
                        weight = WidgetFontWeight.Medium,
                    )
                    WidgetText(
                        text = elapsed,
                        size = elapsedSize.sp,
                        color = GlanceTheme.colors.primary,
                        weight = WidgetFontWeight.Bold,
                    )
                }
                Spacer(modifier = GlanceModifier.width(10.dp))
                Column(horizontalAlignment = Alignment.Horizontal.End) {
                    WidgetText(
                        text = if (wide) "Next Prayer" else "Next",
                        size = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                        weight = WidgetFontWeight.Bold,
                        align = WidgetTextAlign.End,
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                    WidgetText(
                        text = next,
                        size = nextSize.sp,
                        color = GlanceTheme.colors.onSurface,
                        weight = WidgetFontWeight.Bold,
                        align = WidgetTextAlign.End,
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                }
            }
        }
    }
}

/** Dense but readable 2×2 rendering for narrow launchers and high display scaling. */
@Composable
private fun NarrowSquarePrayerContent(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    val context = LocalContext.current
    val width = contentSize.width.value
    val currentName = state.currentPrayerName()
    val elapsed = state.insight?.elapsed ?: state.nextPrayer.time
    val countdown = state.countdown.removePrefix("in ").trim()
    val next = "${state.nextPrayer.name} · $countdown"
    val elapsedSize = WidgetTypography
        .fittingSize(context, elapsed, width, bold = true)
        .coerceAtMost(11.5f)
    val nextSize = WidgetTypography
        .fittingSize(context, next, width, bold = true)
        .coerceAtMost(10.5f)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_flaticon_location_marker),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(18.dp),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(R.drawable.sample_refresh_icon),
                contentDescription = "Refresh prayer times",
                colorFilter = ColorFilter.tint(GlanceTheme.colors.secondary),
                modifier = GlanceModifier
                    .size(20.dp)
                    .clickable(actionRunCallback<RefreshPrayerWidgetAction>()),
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        WidgetText(
            text = currentName.uppercase(),
            size = 10.sp,
            color = GlanceTheme.colors.primary,
            weight = WidgetFontWeight.Bold,
        )
        WidgetText(
            text = elapsed,
            size = elapsedSize.sp,
            color = GlanceTheme.colors.onSurface,
            weight = WidgetFontWeight.Bold,
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        WidgetText(
            text = next,
            size = nextSize.sp,
            color = GlanceTheme.colors.onSurfaceVariant,
            weight = WidgetFontWeight.Bold,
        )
    }
}


/**
 * Titled surface matching the ported layouts.
 *
 * Uses the same Scaffold and TitleBar components those layouts do rather than a
 * reproduction, so the header — icon colour, title weight, action button, spacing — can
 * only ever match. The title carries the place name, which is what the widget's own
 * heading would otherwise waste a line repeating.
 */
@Composable
private fun TitledSurface(
    state: PrayerWidgetState.Available,
    detailedHeader: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        backgroundColor = TransparentWidgetBackground,
        horizontalPadding = WIDGET_PADDING,
        // Scaffold pads the sides only. The final content is already an inset surface,
        // so use the compensated bottom inset instead of stacking another full 16dp.
        modifier = GlanceModifier
            .padding(bottom = TITLED_WIDGET_BOTTOM_PADDING)
            .clickable(actionStartActivity<MainActivity>()),
        titleBar = {
            val appearance = LocalWidgetAppearance.current
            val transparentHeader = !appearance.showBackground
            val transparentForeground = LocalTransparentWidgetForeground.current
            val headerInk = if (transparentHeader) {
                transparentForeground.primary
            } else {
                GlanceTheme.colors.onSurface
            }
            val headerIcon = if (transparentHeader) {
                transparentForeground.primary
            } else {
                GlanceTheme.colors.primary
            }
            val headerAction = if (transparentHeader) {
                transparentForeground.secondary
            } else {
                GlanceTheme.colors.secondary
            }
            // Glance's own TitleBar is not used here, and only for one reason: its title
            // is a Glance Text, which cannot carry the bundled Ubuntu Sans (see
            // widget_text_regular.xml). Leaving it in place put the place name in the
            // system font directly above four lines in the app's own face, which read as
            // a mistake rather than a hierarchy. The arrangement below is the one it
            // draws — start icon, title, trailing action — rebuilt so the title is a
            // WidgetText.
            Row(
                // Horizontal padding is applied here, not inherited: Scaffold's
                // horizontalPadding reaches the content slot only, and the TitleBar this
                // replaced carried its own inset. Without it the pin sat flush against
                // the card's left edge — measured 0.0dp, against ~15dp for every line of
                // text beneath it.
                //
                // The end inset is smaller because the refresh control is a 48dp touch
                // target around a ~24dp glyph: padding it to the full margin would push
                // the glyph a further 12dp in and leave the header looking lopsided. The
                // difference lets the button's *glyph* line up with the text margin while
                // its target still reaches the edge.
                //
                // The Flaticon marker is centered in its bitmap, so the standard content
                // inset aligns its visible edge with the text below without compensation.
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 8.dp,
                        top = HEADER_TOUCH_TARGET_TOP_PADDING,
                        bottom = HEADER_TOUCH_TARGET_BOTTOM_PADDING,
                    ),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                if (detailedHeader) {
                    ReferenceHeader(state)
                } else {
                    Image(
                        provider = ImageProvider(R.drawable.ic_flaticon_location_marker),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(headerIcon),
                        modifier = GlanceModifier.size(22.dp),
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    WidgetText(
                        text = state.place,
                        size = 15.sp,
                        color = headerInk,
                        weight = if (transparentHeader) {
                            WidgetFontWeight.ShadowBold
                        } else {
                            WidgetFontWeight.Medium
                        },
                        modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
                    )
                }
                // Same button the ported layouts put here — transparent background,
                // secondary tint — so the prayer card's header and the sample cards'
                // headers stay indistinguishable.
                if (detailedHeader) {
                    val dynamicHeader = appearance.backgroundType ==
                        WidgetBackgroundType.DYNAMIC_COLOR
                    val themePaintedHeader = dynamicHeader || transparentHeader
                    Box(
                        modifier = GlanceModifier
                            .size(33.dp)
                            .background(
                                if (themePaintedHeader) {
                                    GlanceTheme.colors.surfaceVariant
                                } else {
                                    ReferenceTopHeaderPill
                                },
                            )
                            .cornerRadius(17.dp)
                            .clickable(actionRunCallback<RefreshPrayerWidgetAction>()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.sample_refresh_icon),
                            contentDescription = "Refresh prayer times",
                            colorFilter = ColorFilter.tint(
                                if (transparentHeader) {
                                    transparentForeground.primary
                                } else if (dynamicHeader) {
                                    GlanceTheme.colors.onSurface
                                } else {
                                    ReferenceTopHeaderInk
                                },
                            ),
                            modifier = GlanceModifier.size(22.dp),
                        )
                    }
                } else {
                    CircleIconButton(
                        imageProvider = ImageProvider(R.drawable.sample_refresh_icon),
                        contentDescription = "Refresh prayer times",
                        contentColor = headerAction,
                        backgroundColor = null,
                        onClick = actionRunCallback<RefreshPrayerWidgetAction>(),
                    )
                }
            }
        },
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            content()
        }
    }
}

/** Location/date and weather groups arranged like the supplied reference. */
@Composable
private fun androidx.glance.layout.RowScope.ReferenceHeader(
    state: PrayerWidgetState.Available,
) {
    val appearance = LocalWidgetAppearance.current
    val transparentHeader = !appearance.showBackground
    val transparentForeground = LocalTransparentWidgetForeground.current
    val dynamicHeader = appearance.backgroundType ==
        WidgetBackgroundType.DYNAMIC_COLOR
    val themePaintedHeader = dynamicHeader || transparentHeader
    val accent = when {
        transparentHeader -> transparentForeground.primary
        dynamicHeader -> GlanceTheme.colors.primary
        else -> ReferenceForest
    }
    val titleColor = when {
        transparentHeader -> transparentForeground.primary
        dynamicHeader -> GlanceTheme.colors.onSurface
        else -> ReferenceTopHeaderInk
    }
    val subtitleColor = if (transparentHeader) {
        transparentForeground.secondary
    } else if (dynamicHeader) {
        GlanceTheme.colors.onSurfaceVariant
    } else {
        ReferenceTopHeaderMuted
    }
    Box(
        modifier = GlanceModifier
            .size(35.dp)
            .background(
                if (themePaintedHeader) {
                    GlanceTheme.colors.primaryContainer
                } else {
                    ReferencePinSurface
                },
            )
            .cornerRadius(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_location_pin),
            contentDescription = null,
            colorFilter = ColorFilter.tint(accent),
            modifier = GlanceModifier.size(21.dp),
        )
    }
    Spacer(modifier = GlanceModifier.width(9.dp))
    Column(modifier = GlanceModifier.defaultWeight()) {
        WidgetText(
            text = state.place,
            size = 14.sp,
            color = titleColor,
            weight = if (transparentHeader) {
                WidgetFontWeight.ShadowBold
            } else {
                WidgetFontWeight.Medium
            },
        )
        WidgetText(
            text = state.dateLabel,
            size = 9.5.sp,
            color = subtitleColor,
            weight = if (transparentHeader) {
                WidgetFontWeight.ShadowMedium
            } else {
                WidgetFontWeight.Regular
            },
        )
    }
    Spacer(modifier = GlanceModifier.width(8.dp))
    // Keep this as one direct child of the outer row. RemoteViews/Glance drops children
    // after the tenth direct item; when these pieces were emitted separately the refresh
    // button became child 11 and disappeared. Its missing 48dp target also made the real
    // title row shorter than TITLE_BAR_HEIGHT, leaving a false extra inset at the bottom.
    Row(
        modifier = GlanceModifier
            .width(118.dp)
            .height(40.dp)
            .then(
                if (themePaintedHeader) {
                    GlanceModifier.background(GlanceTheme.colors.surfaceVariant)
                } else {
                    GlanceModifier.background(
                        imageProvider = ImageProvider(R.drawable.widget_weather_pill_background),
                        contentScale = ContentScale.FillBounds,
                    )
                },
            )
            .cornerRadius(20.dp)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (state.currentWeather?.icon != null) {
            StaticMeteoconBitmap(still = state.currentWeather.icon, size = 29.dp)
        } else {
            Image(
                provider = ImageProvider(R.drawable.flaticon_weather_cloudy),
                contentDescription = "Weather unavailable",
                modifier = GlanceModifier.size(29.dp),
            )
        }
        Spacer(modifier = GlanceModifier.width(4.dp))
        Column(modifier = GlanceModifier.width(65.dp)) {
            WidgetText(
                text = state.currentWeather?.temperature ?: "--°",
                size = 15.sp,
                color = titleColor,
                weight = if (transparentHeader) {
                    WidgetFontWeight.ShadowBold
                } else {
                    WidgetFontWeight.Bold
                },
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
            )
            WidgetText(
                text = state.currentWeather?.summary ?: "Unavailable",
                size = 9.5.sp,
                color = titleColor,
                weight = if (transparentHeader) {
                    WidgetFontWeight.ShadowMedium
                } else {
                    WidgetFontWeight.Medium
                },
                align = WidgetTextAlign.Start,
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
            )
        }
    }
    Spacer(modifier = GlanceModifier.width(6.dp))
}

/**
 * A line of widget text in the app's own typeface.
 *
 * Glance's own [Text] cannot carry a bundled font — see widget_text_regular.xml — so the
 * line is a RemoteViews TextView inflated from this package's resources instead. Text,
 * size and colour are set here; weight selects the layout, because TextView.setTypeface is
 * not remotable.
 *
 * [WidgetTypography] measures against the same faces, so the sizes it chooses are the
 * sizes these lines actually render at.
 */
@Composable
internal fun WidgetText(
    text: String,
    size: TextUnit,
    color: ColorProvider,
    weight: WidgetFontWeight,
    maxLines: Int = 1,
    /** Horizontal alignment, applied as gravity — TextView.setGravity is remotable. */
    align: WidgetTextAlign = WidgetTextAlign.Start,
    // Width is claimed by default: the TextView inside is match_parent, so it needs a
    // bounded box to wrap against. Callers laying text out in a Row pass wrapContentWidth
    // instead, so the runs sit next to each other rather than each taking the whole line.
    //
    // Height must be wrapped explicitly. An AndroidRemoteViews left unconstrained inside a
    // fillMaxSize Column took the entire height, and every line after the first was pushed
    // off the card — the title rendered alone on an otherwise empty surface.
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
) {
    val context = LocalContext.current
    val remoteViews = RemoteViews(context.packageName, weight.layout).apply {
        setTextViewText(R.id.widget_text, text)
        setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, size.value)
        // Both colours, not one. getColor() resolves a ColorProvider against the context
        // it is given, so setTextColor bakes a fixed ARGB into the RemoteViews at
        // composition time — and the launcher, which does re-resolve Glance's own
        // backgrounds when the system theme flips, has no way to re-resolve this. The
        // result was a prayer card whose background followed a switch to light mode while
        // its text stayed the light-on-light of dark mode, effectively invisible.
        //
        // setColorInt hands the launcher both values and lets it pick per its own
        // configuration, so the text follows the theme without the widget recomposing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setColorInt(
                R.id.widget_text,
                "setTextColor",
                color.getColor(context.withNightMode(night = false)).toArgb(),
                color.getColor(context.withNightMode(night = true)).toArgb(),
            )
        } else {
            // Below S there is no two-colour setter. A widget on those versions redraws on
            // the next update anyway, which is the behaviour this used to have everywhere.
            setTextColor(R.id.widget_text, color.getColor(context).toArgb())
        }
        setInt(R.id.widget_text, "setMaxLines", maxLines)
        setInt(R.id.widget_text, "setGravity", align.gravity)
    }
    AndroidRemoteViews(remoteViews = remoteViews, modifier = modifier)
}

/**
 * The Arabic face the reader has chosen in the app.
 *
 * The widget used to hardcode one, which is why its Arabic could look nothing like the
 * same text inside the app: these are not interchangeable styles but different Mushaf
 * traditions, and the app lets the reader pick between them. Read from the same
 * `quran_prefs`/`arabic_font` the Surah and Dua screens write, with the same default, so
 * changing it in the app changes it here.
 *
 * Mirrors getArabicFontResId() in SurahDetailScreen — keep the two in sync.
 */
internal fun arabicFontFor(context: Context): WidgetFontWeight {
    return when (selectedArabicFont(context)) {
        "noor_e_hidayat" -> WidgetFontWeight.ArabicNoor
        "thabit" -> WidgetFontWeight.ArabicThabit
        "uthmani_script" -> WidgetFontWeight.ArabicUthmani
        "indopak_script" -> WidgetFontWeight.ArabicIndoPak
        "amiri" -> WidgetFontWeight.ArabicAmiri
        "scheherazade" -> WidgetFontWeight.ArabicScheherazade
        else -> WidgetFontWeight.ArabicPdms
    }
}

/** Bundled face for drawing Arabic before it crosses the RemoteViews process boundary. */
@FontRes
internal fun arabicFontResourceFor(context: Context): Int = when (selectedArabicFont(context)) {
    "noor_e_hidayat" -> R.font.noor_hidayat_quran
    "thabit" -> R.font.thabit_quran
    "uthmani_script" -> R.font.amiri_quran
    "indopak_script" -> R.font.indopak_quran
    "amiri" -> R.font.amiri_regular
    "scheherazade" -> R.font.scheherazade_regular
    else -> R.font.pdms_saleem_quran
}

private fun selectedArabicFont(context: Context): String? = context
    .getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
    .getString("arabic_font", "pdms_saleem")

/**
 * The same context with night mode forced one way, for resolving a colour in both.
 *
 * Only the UI_MODE_NIGHT bits are touched; everything else — density, locale, size — is
 * inherited, so a colour resolved through this differs from the caller's only in the one
 * dimension being asked about.
 */
private fun Context.withNightMode(night: Boolean): Context {
    val configuration = Configuration(resources.configuration)
    configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    return createConfigurationContext(configuration)
}

/** Horizontal alignment for [WidgetText], as a Gravity value. */
internal enum class WidgetTextAlign(val gravity: Int) {
    Start(android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL),
    Center(android.view.Gravity.CENTER),
    End(android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL),
}

/** The three weights the widget draws in, each backed by its own one-TextView layout. */
internal enum class WidgetFontWeight(@LayoutRes val layout: Int) {
    Regular(R.layout.widget_text_regular),
    /** Regular Ubuntu Sans without narrow-column inter-word stretching. */
    RegularRagged(R.layout.widget_text_regular_ragged),
    Medium(R.layout.widget_text_medium),
    Bold(R.layout.widget_text_bold),
    /** White-on-wallpaper header faces with a dark halo for mixed light/dark imagery. */
    ShadowRegular(R.layout.widget_text_shadow_regular),
    ShadowMedium(R.layout.widget_text_shadow_medium),
    ShadowBold(R.layout.widget_text_shadow_bold),
    SerifRegular(R.layout.widget_text_serif_regular),
    SerifBold(R.layout.widget_text_serif_bold),
    SerifItalic(R.layout.widget_text_serif_italic),

    // The Arabic faces the app offers. Ubuntu Sans has no Arabic coverage at all, and
    // which of these to use is the reader's choice, not ours — see [arabicFontFor].
    ArabicPdms(R.layout.widget_text_arabic_pdms),
    ArabicNoor(R.layout.widget_text_arabic_noor),
    ArabicThabit(R.layout.widget_text_arabic_thabit),
    ArabicUthmani(R.layout.widget_text_arabic_uthmani),
    ArabicIndoPak(R.layout.widget_text_arabic_indopak),
    ArabicAmiri(R.layout.widget_text_arabic_amiri),
    ArabicScheherazade(R.layout.widget_text_arabic_scheherazade),
}

/**
 * Title-bar refresh.
 *
 * Recomputes rather than fetches: prayer times are derived from cached location and
 * settings, so this re-runs the same load a scheduled update does. It goes through
 * [PrayerWidgetUpdater] rather than updating this widget alone because the sizes are
 * separate GlanceAppWidget classes — refreshing only the tapped one would leave every
 * other placed size on the stale reading it was already showing.
 */
class RefreshPrayerWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        PrayerWidgetUpdater.refresh(context)
    }
}

@Composable
private fun UnavailableContent(singleRow: Boolean = false) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        // WidgetText, not Glance's Text, for the same reason every other line uses it:
        // this is the first thing a user sees before granting location, and rendering it
        // in the system font while the rest of the widget is Ubuntu Sans would make the
        // empty state look like a different app's.
        WidgetText(
            text = if (singleRow) "Open app" else "Prayer times",
            size = (if (singleRow) 12f else 15f).sp,
            color = GlanceTheme.colors.onSurface,
            weight = WidgetFontWeight.Medium,
            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
        )
        if (!singleRow) {
            WidgetText(
                text = "Open the app to set your location",
                size = 12.sp,
                color = GlanceTheme.colors.onSurfaceVariant,
                weight = WidgetFontWeight.Regular,
                maxLines = 2,
                modifier = GlanceModifier.wrapContentHeight(),
            )
        }
    }
}

// ---------------------------------------------------------------------------------
// Size layouts
// ---------------------------------------------------------------------------------

/**
 * The single-row layouts — anything too short for the hero stack.
 *
 * One row, but not one fixed row. A 2x1 has space for a countdown and nothing else; a
 * wide 4x1 has enough horizontal room to carry the same information hierarchy as the
 * 4x2 hero. So the strip has two shapes:
 *
 *  - **The prayer-window hero**, on wide strips — current-prayer guidance and elapsed
 *    status on the left, the next prayer and countdown on the right, with the same
 *    prayer-window progress used by 4x2 below them.
 *  - **The next prayer**, otherwise — phase title, countdown, and the clock time when
 *    there is room for it.
 *
 * Everything is admitted only after being measured, so the same composable serves every
 * width rather than one width's layout being stretched across the others.
 */
@Composable
private fun TinyContent(state: PrayerWidgetState.Available, contentSize: DpSize) {
    PrayerWindowStrip(state = state, contentSize = contentSize)
}

/**
 * The taller hero's story preserved as one compact visual flow for every x1 width.
 *
 * Keeping current and next prayer in one vertical sequence makes the progress bar read
 * as the bridge between them. The next-prayer row gets a quiet container of its own so
 * it is easy to find without competing with the guidance headline.
 */
@Composable
private fun PrayerWindowStrip(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    val context = LocalContext.current
    val width = contentSize.width.value
    val titleText = state.insight?.title ?: "Prayer now"
    val elapsedText = state.insight?.elapsed ?: state.nextPrayer.time
    val countdown = state.countdown.removePrefix("in ").trim()
    val nextText = if (width >= 230f) {
        "${state.nextPrayer.name} in $countdown"
    } else {
        "${state.nextPrayer.name} · $countdown"
    }
    val nextLabel = if (width >= 230f) "Next Prayer" else "Next"
    val isWide = width >= 230f

    val titleSize = WidgetTypography
        .fittingSize(context, titleText, width, bold = true)
        .coerceIn(if (isWide) 14f else 11f, if (isWide) 17f else 14f)
    val elapsedSize = WidgetTypography
        .fittingSize(context, elapsedText, width)
        .coerceIn(if (isWide) 11.5f else 9f, if (isWide) 13f else 11f)
    val nextSize = WidgetTypography
        .fittingSize(context, nextText, width * 0.62f, bold = true)
        .coerceIn(if (isWide) 12.5f else 10f, if (isWide) 16f else 13f)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        WidgetText(
            text = titleText,
            size = titleSize.sp,
            color = GlanceTheme.colors.onSurface,
            weight = WidgetFontWeight.Medium,
        )
        WidgetText(
            text = elapsedText,
            size = elapsedSize.sp,
            color = GlanceTheme.colors.onSurfaceVariant,
            weight = WidgetFontWeight.Regular,
        )

        Spacer(modifier = GlanceModifier.defaultWeight())

        state.windowProgress?.let { progress ->
            ExpressiveProgressBar(progress = progress, width = contentSize.width)
            Spacer(modifier = GlanceModifier.height(6.dp))
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.primary)
                .cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            WidgetText(
                text = nextLabel,
                size = (if (isWide) 11.5f else 10f).sp,
                color = GlanceTheme.colors.onPrimary,
                weight = WidgetFontWeight.Bold,
                modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            WidgetText(
                text = nextText,
                size = nextSize.sp,
                color = GlanceTheme.colors.onPrimary,
                weight = WidgetFontWeight.Bold,
                align = WidgetTextAlign.End,
                modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            )
        }
    }
}

/** The same prayer-window story stacked for narrower 2x1 and 3x1 footprints. */
@Composable
private fun NarrowPrayerHeroStrip(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    val context = LocalContext.current
    val width = contentSize.width.value
    val titleText = state.insight?.title ?: "Prayer now"
    val elapsedText = state.insight?.elapsed ?: state.nextPrayer.time
    val countdown = state.countdown.removePrefix("in ").trim()
    val nextText = "${state.nextPrayer.name} in $countdown"
    val titleSize = WidgetTypography
        .fittingSize(context, titleText, width, bold = true)
        .coerceIn(11f, 14f)
    val elapsedSize = WidgetTypography
        .fittingSize(context, elapsedText, width)
        .coerceIn(9f, 11f)
    val nextSize = WidgetTypography
        .fittingSize(context, nextText, width * 0.68f, bold = true)
        .coerceIn(11f, 14f)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        WidgetText(
            text = titleText,
            size = titleSize.sp,
            color = GlanceTheme.colors.onSurface,
            weight = WidgetFontWeight.Medium,
        )
        WidgetText(
            text = elapsedText,
            size = elapsedSize.sp,
            color = GlanceTheme.colors.onSurfaceVariant,
            weight = WidgetFontWeight.Regular,
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            WidgetText(
                text = "Next Prayer",
                size = 10.sp,
                color = GlanceTheme.colors.primary,
                weight = WidgetFontWeight.Bold,
                modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            WidgetText(
                text = nextText,
                size = nextSize.sp,
                color = GlanceTheme.colors.onSurface,
                weight = WidgetFontWeight.Bold,
                align = WidgetTextAlign.End,
                modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            )
        }
        state.windowProgress?.let { progress ->
            Spacer(modifier = GlanceModifier.height(5.dp))
            ExpressiveProgressBar(progress = progress, width = contentSize.width)
        }
    }
}

/** The 4x2 prayer hero reflowed horizontally for a wide one-row widget. */
@Composable
private fun WidePrayerHeroStrip(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    val context = LocalContext.current
    val width = contentSize.width.value
    val titleText = state.insight?.title ?: "Prayer now"
    val elapsedText = state.insight?.elapsed ?: state.nextPrayer.time
    val countdown = state.countdown.removePrefix("in ").trim()
    val nextText = "${state.nextPrayer.name} in $countdown"

    // Give the current-prayer story the wider half. The trailing block stays large and
    // emphatic, matching the 4x2 label/name/countdown hierarchy instead of looking like
    // one more timetable column.
    val leftWidth = width * 0.58f
    val rightWidth = width * 0.36f
    val titleSize = WidgetTypography
        .fittingSize(context, titleText, leftWidth, bold = true)
        .coerceIn(12f, 16f)
    val elapsedSize = WidgetTypography
        .fittingSize(context, elapsedText, leftWidth)
        .coerceIn(10f, 12f)
    val nextSize = WidgetTypography
        .fittingSize(context, nextText, rightWidth, bold = true)
        .coerceIn(12f, 16f)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                WidgetText(
                    text = titleText,
                    size = titleSize.sp,
                    color = GlanceTheme.colors.onSurface,
                    weight = WidgetFontWeight.Medium,
                )
                WidgetText(
                    text = elapsedText,
                    size = elapsedSize.sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                    weight = WidgetFontWeight.Regular,
                )
            }
            Spacer(modifier = GlanceModifier.width(12.dp))
            Column(
                modifier = GlanceModifier.wrapContentWidth(),
                horizontalAlignment = Alignment.Horizontal.End,
            ) {
                WidgetText(
                    text = "Next Prayer",
                    size = 11.sp,
                    color = GlanceTheme.colors.primary,
                    weight = WidgetFontWeight.Bold,
                    align = WidgetTextAlign.End,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
                WidgetText(
                    text = nextText,
                    size = nextSize.sp,
                    color = GlanceTheme.colors.onSurface,
                    weight = WidgetFontWeight.Bold,
                    align = WidgetTextAlign.End,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
            }
        }

        state.windowProgress?.let { progress ->
            Spacer(modifier = GlanceModifier.height(5.dp))
            ExpressiveProgressBar(progress = progress, width = contentSize.width)
        }
    }
}

/** The next prayer alone, for strips too narrow to carry the whole day. */
@Composable
private fun NextPrayerStripContent(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    val context = LocalContext.current
    val width = contentSize.width.value
    val height = contentSize.height.value
    val lineHeight = WidgetTypography.lineHeightPerSp(context)

    val countdown = state.countdown.removePrefix("in ").trim()
    val name = state.nextPrayer.name
    val mainLine = "$name in $countdown"
    val clock = state.nextPrayer.time
    val phase = state.insight?.title

    // The meteocon is the first thing to go, not the last: at this size it is a small glyph
    // whose absence costs nothing, while the 28dp it occupies is a quarter of the line on
    // the narrowest card. It is kept only when the text does not need that room.
    val fitsWithIcon = WidgetTypography.fittingSize(context, mainLine, width - ICON_ROOM) >= 13f
    val iconRoom = if (fitsWithIcon) ICON_ROOM else 0f

    val mainSize = WidgetTypography
        .fittingSize(context, mainLine, width - iconRoom, bold = true)
        .coerceIn(11f, 18f)

    // What the row has left once the sentence and the icon have taken their share. The
    // clock time earns a place only if it fits in that remainder with a real gap before it,
    // rather than crowding the countdown it sits beside.
    val mainWidth = WidgetTypography.widthPerSp(context, mainLine, bold = true) * mainSize
    val clockSize = (mainSize * 0.92f).coerceAtLeast(11f)
    val clockWidth = WidgetTypography.widthPerSp(context, clock, bold = true) * clockSize
    val showClock = width - iconRoom - mainWidth - TINY_GROUP_GAP >= clockWidth

    val phaseSize = (mainSize * 0.78f).coerceIn(10f, 14f)
    val twoLines = phase != null &&
        height >= lineHeight * (mainSize + phaseSize) &&
        WidgetTypography.fittingSize(context, phase, width - iconRoom) >= phaseSize

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (fitsWithIcon) {
            StaticMeteocon(prayer = state.nextPrayer, size = 20.dp)
            Spacer(modifier = GlanceModifier.width(8.dp))
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            if (twoLines && phase != null) {
                WidgetText(
                    text = phase,
                    size = phaseSize.sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                    weight = WidgetFontWeight.Regular,
                )
            }
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                WidgetText(
                    text = name,
                    size = mainSize.sp,
                    color = GlanceTheme.colors.onSurface,
                    weight = WidgetFontWeight.Bold,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
                WidgetText(
                    text = " in ",
                    size = mainSize.sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                    weight = WidgetFontWeight.Regular,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
                WidgetText(
                    text = countdown,
                    size = mainSize.sp,
                    color = GlanceTheme.colors.primary,
                    weight = WidgetFontWeight.Bold,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
            }
        }
        if (showClock) {
            WidgetText(
                text = clock,
                size = clockSize.sp,
                color = GlanceTheme.colors.onSurface,
                weight = WidgetFontWeight.Bold,
                modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            )
        }
    }
}

/** Every prayer of the day in one calm, evenly divided group. */
@Composable
private fun DayStripContent(
    prayers: List<WidgetPrayer>,
    nameSize: Float,
    timeSize: Float,
    showIcons: Boolean = false,
    currentPrayerName: String? = null,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(14.dp)
            .padding(horizontal = 3.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        prayers.forEachIndexed { index, prayer ->
            if (index > 0) {
                FadingVerticalSeparator(height = if (showIcons) 48.dp else 30.dp)
            }
            // Current and next already have dedicated sections immediately above this
            // strip. Repeating two different active treatments here made the five-item
            // schedule compete with those sections, so both use the same typography.
            // Only prayers completed before the current prayer are gently muted.
            val isCurrent = prayer.name.equals(currentPrayerName, ignoreCase = true)
            val contentColor = if (prayer.isPast && !isCurrent) {
                GlanceTheme.colors.outline
            } else {
                GlanceTheme.colors.onSurface
            }
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(horizontal = 1.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                if (showIcons && prayer.weatherIcon != null) {
                    Box(
                        modifier = GlanceModifier
                            .size(30.dp)
                            .background(GlanceTheme.colors.surfaceVariant)
                            .cornerRadius(15.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        StaticMeteocon(prayer = prayer, size = 23.dp)
                    }
                    Spacer(modifier = GlanceModifier.height(2.dp))
                }
                WidgetText(
                    text = prayer.name,
                    size = nameSize.sp,
                    color = contentColor,
                    weight = WidgetFontWeight.Medium,
                    align = WidgetTextAlign.Center,
                )
                WidgetText(
                    text = prayer.time,
                    size = timeSize.sp,
                    color = contentColor,
                    weight = WidgetFontWeight.Medium,
                    align = WidgetTextAlign.Center,
                )
            }
        }
    }
}

/** Adds useful detail as height grows instead of stretching one hero into empty space. */
@Composable
private fun ColumnScope.AdaptivePrayerContent(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    when {
        contentSize.height >= FULL_SCHEDULE_MIN_HEIGHT ->
            FullPrayerContent(state = state, contentSize = contentSize)

        contentSize.height >= EXPANDED_CONTENT_MIN_HEIGHT ->
            ExpandedPrayerContent(state = state, contentSize = contentSize)

        else -> ShortPrayerContent(state = state, contentSize = contentSize)
    }
}

/**
 * Wide, tall layout matching the supplied reference image.
 *
 * The visual treatment still comes from the app's widget theme; this function owns only
 * the reference's information hierarchy and section placement.
 */
@Composable
private fun ReferencePrayerContent(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    when {
        contentSize.height >= REFERENCE_LAYOUT_MIN_HEIGHT -> {
            ReferenceFullPrayerContent(state = state, contentSize = contentSize)
        }

        contentSize.height >= REFERENCE_MEDIUM_MIN_HEIGHT -> {
            ReferenceMediumPrayerContent(state = state, contentSize = contentSize)
        }

        else -> {
            ReferenceShortPrayerContent(state = state, contentSize = contentSize)
        }
    }
}

/** Full-height reference hierarchy with the complete vertical prayer schedule. */
@Composable
private fun ReferenceFullPrayerContent(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    val sectionGap = 6.dp
    // The artwork's reference proportions are width-driven. One UI permits this widget to
    // grow much taller than those proportions (currently 406 x 570dp); assigning all of
    // that surplus to the final weighted child made the devotional card almost twice its
    // intended height. Share resize deltas across the three sections instead. The weights
    // favour the prayer timeline, whose larger illustrations and status row make useful
    // use of vertical space, while the devotional card stays close to its authored ratio.
    val heightDelta = contentSize.height - REFERENCE_DESIGN_CONTENT_HEIGHT
    val heroHeight = (
        REFERENCE_DESIGN_HERO_HEIGHT + (heightDelta * 0.27f)
        ).coerceAtLeast(128.dp)
    val scheduleHeight = (
        REFERENCE_DESIGN_TIMELINE_HEIGHT + (heightDelta * 0.40f)
        ).coerceAtLeast(138.dp)
    val devotionalHeight = (
        contentSize.height - heroHeight - scheduleHeight - (sectionGap * 2f)
        ).coerceAtLeast(REFERENCE_DESIGN_DEVOTIONAL_HEIGHT - 6.dp)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        ReferencePrayerHero(
            state = state,
            width = contentSize.width,
            height = heroHeight,
        )
        Spacer(modifier = GlanceModifier.height(sectionGap))
        ReferencePrayerTimeline(
            state = state,
            width = contentSize.width,
            height = scheduleHeight,
        )
        Spacer(modifier = GlanceModifier.height(sectionGap))
        ReferenceDevotionalPanel(
            state = state,
            height = devotionalHeight,
        )
    }
}

/** Current/next-prayer card with the live mosque illustration as its visual anchor. */
@Composable
private fun ReferencePrayerHero(
    state: PrayerWidgetState.Available,
    width: Dp,
    height: Dp,
) {
    if (height < 112.dp) {
        ReferenceShallowPrayerHero(state = state, width = width, height = height)
        return
    }
    val currentName = state.currentPrayerName()
    val elapsed = state.insight?.elapsed.orEmpty()
    val elapsedDuration = elapsed.substringBefore(" since ")
        .ifBlank { null }
        ?.compactWidgetDuration()
    val elapsedPrayer = elapsed.substringAfter(" since ", missingDelimiterValue = currentName)
    val longElapsedPhrase = (elapsedDuration?.length ?: 0) > 6
    // Give the elapsed prayer window enough width for longer labels such as
    // "12h 45m since Maghrib" without shrinking or clipping.
    val leftWidth = (width * 0.43f).coerceIn(128.dp, 174.dp)
    val rightWidth = (width * 0.16f).coerceIn(54.dp, 64.dp)
    // The quote/photo region consumes over half the hero; keep the editorial type ramp
    // compact until the left text column can hold “14m since Dhuhr” without ellipsis.
    val compact = width < 380.dp
    val context = LocalContext.current
    val phaseTitle = state.insight?.title ?: "Prayer now"
    val phaseTitleSize = WidgetTypography
        .fittingSize(
            context = context,
            text = phaseTitle,
            maxWidthDp = leftWidth.value - 4f,
            bold = true,
        )
        .coerceIn(if (compact) 13.5f else 14.5f, if (compact) 15f else 16f)
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(
                imageProvider = ImageProvider(R.drawable.prayer_widget_reference_hero_v4),
                contentScale = ContentScale.FillBounds,
            )
            .cornerRadius(24.dp),
    ) {
        AnimatedFoliage(
            phase = state.dayPhase,
            placement = WidgetFoliagePlacement.HERO_RIGHT,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 14.dp, top = 6.dp, end = 7.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Vertical.Top,
        ) {
            Column(
                modifier = GlanceModifier
                    .width(leftWidth)
                    .height(height - 12.dp),
                verticalAlignment = Alignment.Vertical.Top,
            ) {
                WidgetText(
                    text = phaseTitle,
                    size = phaseTitleSize.sp,
                    color = ReferenceHeroText,
                    weight = WidgetFontWeight.Medium,
                    maxLines = 1,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(start = 4.dp),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.Vertical.Bottom,
                ) {
                    elapsedDuration?.let { duration ->
                        WidgetText(
                            text = duration,
                            size = when {
                                compact -> 18.sp
                                longElapsedPhrase -> 18.sp
                                else -> 22.5.sp
                            },
                            color = ReferenceHeroText,
                            weight = WidgetFontWeight.SerifBold,
                            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                        )
                        WidgetText(
                            text = " since ",
                            size = if (compact || longElapsedPhrase) 10.5.sp else 12.5.sp,
                            color = ReferenceHeroText,
                            weight = WidgetFontWeight.SerifRegular,
                            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                        )
                    }
                    WidgetText(
                        text = elapsedPrayer,
                        size = when {
                            compact -> 16.5.sp
                            longElapsedPhrase -> 17.5.sp
                            else -> 20.5.sp
                        },
                        color = ReferenceHeroText,
                        weight = WidgetFontWeight.SerifBold,
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                Row(
                    modifier = GlanceModifier.width(68.dp).height(4.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .height(1.dp)
                            .background(ReferenceHeroText)
                            .cornerRadius(1.dp),
                    ) {}
                    Spacer(modifier = GlanceModifier.width(5.dp))
                    Box(
                        modifier = GlanceModifier
                            .size(3.dp)
                            .background(ReferenceHeroText)
                            .cornerRadius(2.dp),
                    ) {}
                }
                // Share surplus height across the whole story. Assigning it to only this
                // spacer created an artificial hole between the elapsed and next-prayer
                // groups on tall launchers.
                Spacer(modifier = GlanceModifier.defaultWeight())
                WidgetText(
                    text = state.nextPrayer.name,
                    size = if (compact) 31.sp else 34.sp,
                    color = ReferenceHeroText,
                    weight = WidgetFontWeight.SerifBold,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(start = 3.dp),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(start = 3.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    WidgetText(
                        text = "in ",
                        size = when {
                            height >= 150.dp && !compact -> 22.sp
                            compact -> 18.sp
                            else -> 20.sp
                        },
                        color = LocalWidgetHeroAccent.current,
                        weight = WidgetFontWeight.SerifRegular,
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                    WidgetText(
                        text = state.countdown.removePrefix("in "),
                        size = when {
                            height >= 150.dp && !compact -> 28.sp
                            compact -> 22.sp
                            else -> 25.sp
                        },
                        color = LocalWidgetHeroAccent.current,
                        weight = WidgetFontWeight.SerifBold,
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                }
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            Column(
                modifier = GlanceModifier
                    .width(rightWidth),
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_quote_mark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ReferenceQuoteMark),
                    modifier = GlanceModifier.size(16.dp),
                )
                WidgetText(
                    text = "And establish\nprayer for My\nremembrance.",
                    size = if (compact) 8.sp else 8.5.sp,
                    color = ReferenceQuote,
                    weight = WidgetFontWeight.Medium,
                    maxLines = 3,
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                WidgetText(
                    text = "— Taha 20:14",
                    size = if (compact) 7.5.sp else 8.sp,
                    color = ReferenceQuote,
                    weight = WidgetFontWeight.Regular,
                )
            }
        }
    }
}

/** Two-line crop of the same illustrated hero for a vertically resized wide widget. */
@Composable
private fun ReferenceShallowPrayerHero(
    state: PrayerWidgetState.Available,
    width: Dp,
    height: Dp,
) {
    val currentName = state.currentPrayerName()
    val elapsed = state.insight?.elapsed.orEmpty()
    val elapsedDuration = elapsed.substringBefore(" since ")
        .ifBlank { state.nextPrayer.time }
        .compactWidgetDuration()
    val elapsedPrayer = elapsed.substringAfter(" since ", missingDelimiterValue = currentName)
    val veryShallow = height < 72.dp
    val leftWidth = (width * 0.52f).coerceIn(126.dp, 202.dp)

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(
                imageProvider = ImageProvider(R.drawable.prayer_widget_reference_hero_v4),
                contentScale = ContentScale.FillBounds,
            )
            .cornerRadius(24.dp),
    ) {
        AnimatedFoliage(
            phase = state.dayPhase,
            placement = WidgetFoliagePlacement.HERO_RIGHT,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Column(
            modifier = GlanceModifier
                .width(leftWidth)
                .fillMaxHeight()
                .padding(start = 13.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
                verticalAlignment = Alignment.Vertical.Bottom,
            ) {
                WidgetText(
                    text = elapsedDuration,
                    size = if (veryShallow) 14.sp else 17.sp,
                    color = ReferenceHeroText,
                    weight = WidgetFontWeight.SerifBold,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
                WidgetText(
                    text = " since ",
                    size = if (veryShallow) 8.sp else 9.sp,
                    color = ReferenceHeroText,
                    weight = WidgetFontWeight.SerifRegular,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
                WidgetText(
                    text = elapsedPrayer,
                    size = if (veryShallow) 12.sp else 14.sp,
                    color = ReferenceHeroText,
                    weight = WidgetFontWeight.SerifBold,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
            }
            Spacer(modifier = GlanceModifier.height(if (veryShallow) 1.dp else 2.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
                verticalAlignment = Alignment.Vertical.Bottom,
            ) {
                WidgetText(
                    text = state.nextPrayer.name,
                    size = if (veryShallow) 20.sp else 24.sp,
                    color = ReferenceHeroText,
                    weight = WidgetFontWeight.SerifBold,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
                Spacer(modifier = GlanceModifier.width(5.dp))
                WidgetText(
                    text = state.countdown,
                    size = if (veryShallow) 13.sp else 16.sp,
                    color = LocalWidgetHeroAccent.current,
                    weight = WidgetFontWeight.SerifBold,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
            }
        }
    }
}

/** Five-prayer overview with the active prayer given a distinct, expanded surface. */
@Composable
private fun ReferencePrayerTimeline(
    state: PrayerWidgetState.Available,
    width: Dp,
    height: Dp,
) {
    val context = LocalContext.current
    val expanded = height >= 160.dp
    val condensed = height < REFERENCE_DESIGN_TIMELINE_HEIGHT
    val footerHeight = if (expanded) 30.dp else 16.dp
    val prayerRowHeight = (height - footerHeight - 35.dp).coerceAtLeast(68.dp)
    val timelineArtworkHeight = (
        49.dp + ((height - REFERENCE_DESIGN_TIMELINE_HEIGHT) * 0.50f)
        ).coerceIn(47.dp, 72.dp)
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(
                imageProvider = ImageProvider(R.drawable.prayer_widget_reference_panel_v2),
                contentScale = ContentScale.FillBounds,
            )
            .cornerRadius(22.dp),
    ) {
        // Keep the journey line within the illustration band. Stretching this artwork
        // over the whole panel made its lower curves cross prayer names and times.
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Spacer(modifier = GlanceModifier.height(25.dp))
            Image(
                provider = ImageProvider(WidgetTimelineArtwork.bitmap),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxWidth().height(timelineArtworkHeight),
            )
        }
        AnimatedFoliage(
            phase = state.dayPhase,
            placement = WidgetFoliagePlacement.TIMELINE_LEFT,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 10.dp, top = 5.dp, end = 10.dp, bottom = 3.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(4.dp)
                        .height(18.dp)
                        .background(ReferenceForest)
                        .cornerRadius(3.dp),
                ) {}
                Spacer(modifier = GlanceModifier.width(6.dp))
                WidgetText(
                    text = "Today's Prayers",
                    size = 15.sp,
                    color = ReferenceInk,
                    weight = WidgetFontWeight.Bold,
                    modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
                )
                Row(
                    modifier = GlanceModifier
                        .wrapContentWidth()
                        .background(ReferenceHeaderPill)
                        .cornerRadius(14.dp)
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.calendar_month_24),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ReferenceTopHeaderInk),
                        modifier = GlanceModifier.size(11.dp),
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    WidgetText(
                        text = "View Calendar",
                        size = 9.5.sp,
                        color = ReferenceTopHeaderInk,
                        weight = WidgetFontWeight.Medium,
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    WidgetText(
                        text = "›",
                        size = 11.sp,
                        color = ReferenceTopHeaderInk,
                        weight = WidgetFontWeight.Bold,
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(1.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(prayerRowHeight),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                state.prayers.forEach { prayer ->
                    val active = prayer.isNext
                    val cellModifier = GlanceModifier
                        .defaultWeight()
                        .height(prayerRowHeight - 1.dp)
                        .then(
                            if (active) {
                                GlanceModifier
                                    .background(
                                        imageProvider = ImageProvider(
                                            WidgetActivePrayerCardArtwork.bitmap,
                                        ),
                                        contentScale = ContentScale.FillBounds,
                                    )
                                    .cornerRadius(20.dp)
                                    .padding(vertical = 3.dp)
                            } else {
                                GlanceModifier.padding(vertical = 3.dp)
                            },
                        )
                    Column(
                        modifier = cellModifier,
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    ) {
                        Image(
                            provider = ImageProvider(
                                WidgetPrayerPhaseArtwork.bitmap(context, prayer.phaseArtwork()),
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = GlanceModifier
                                .size(
                                    when {
                                        expanded && active -> 48.dp
                                        expanded -> 42.dp
                                        condensed && active -> 32.dp
                                        condensed -> 28.dp
                                        active -> 42.dp
                                        else -> 37.dp
                                    },
                                ),
                        )
                        Spacer(modifier = GlanceModifier.height(1.dp))
                        WidgetText(
                            text = prayer.name,
                            size = when {
                                expanded && active -> 12.5.sp
                                expanded -> 11.5.sp
                                condensed && active -> 10.5.sp
                                condensed -> 9.5.sp
                                active -> 11.5.sp
                                else -> 10.5.sp
                            },
                            color = if (active) ReferenceForestOn else ReferenceInk,
                            weight = WidgetFontWeight.Bold,
                            align = WidgetTextAlign.Center,
                            modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
                        )
                        WidgetText(
                            text = prayer.time,
                            size = when {
                                expanded && active -> 11.sp
                                expanded -> 10.25.sp
                                condensed && active -> 9.5.sp
                                condensed -> 9.sp
                                active -> 10.sp
                                else -> 9.5.sp
                            },
                            color = if (active) ReferenceForestOn else ReferenceMuted,
                            weight = WidgetFontWeight.Medium,
                            align = WidgetTextAlign.Center,
                            modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        ReferencePrayerStatus(
                            prayer = prayer,
                            expanded = expanded,
                            condensed = condensed,
                        )
                    }
                }
            }
            Spacer(modifier = GlanceModifier.height(1.dp))
            ReferenceDaylightBar(
                state = state,
                width = width - 20.dp,
                height = footerHeight,
                expanded = expanded,
            )
        }
    }
}

/** Completed, relative-time and upcoming treatments from the supplied reference. */
@Composable
private fun ReferencePrayerStatus(
    prayer: WidgetPrayer,
    expanded: Boolean,
    condensed: Boolean,
) {
    when {
        prayer.isNext -> {
            Box(
                modifier = GlanceModifier
                    .wrapContentWidth()
                    .background(ReferenceActiveChip)
                    .cornerRadius(11.dp)
                    .padding(
                        horizontal = when {
                            expanded -> 7.dp
                            condensed -> 5.dp
                            else -> 6.dp
                        },
                        vertical = when {
                            expanded -> 3.dp
                            condensed -> 1.dp
                            else -> 2.dp
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                WidgetText(
                    text = prayer.relativeLabel ?: "Next",
                    size = when {
                        expanded -> 9.sp
                        condensed -> 7.75.sp
                        else -> 8.25.sp
                    },
                    color = ReferenceForestOn,
                    weight = WidgetFontWeight.Medium,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
            }
        }

        prayer.relativeLabel != null -> {
            Box(
                modifier = GlanceModifier
                    .wrapContentWidth()
                    .background(ReferencePinSurface)
                    .cornerRadius(11.dp)
                    .padding(
                        horizontal = when {
                            expanded -> 7.dp
                            condensed -> 5.dp
                            else -> 6.dp
                        },
                        vertical = when {
                            expanded -> 3.dp
                            condensed -> 1.dp
                            else -> 2.dp
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                WidgetText(
                    text = prayer.relativeLabel,
                    size = when {
                        expanded -> 9.sp
                        condensed -> 7.75.sp
                        else -> 8.25.sp
                    },
                    color = ReferenceInk,
                    weight = WidgetFontWeight.Medium,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
            }
        }

        prayer.isPast -> {
            Box(
                modifier = GlanceModifier
                    .size(
                        when {
                            expanded -> 20.dp
                            condensed -> 14.dp
                            else -> 18.dp
                        },
                    )
                    .background(ReferencePinSurface)
                    .cornerRadius(if (condensed) 7.dp else if (expanded) 10.dp else 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_verified),
                    contentDescription = "Prayer completed",
                    colorFilter = ColorFilter.tint(ReferenceForest),
                    modifier = GlanceModifier.size(
                        when {
                            expanded -> 13.dp
                            condensed -> 10.dp
                            else -> 12.dp
                        },
                    ),
                )
            }
        }

        else -> {
            // Two nested circles create the reference's blue outlined upcoming marker
            // without depending on a launcher-specific border implementation.
            Box(
                modifier = GlanceModifier
                    .size(
                        when {
                            expanded -> 18.dp
                            condensed -> 14.dp
                            else -> 16.dp
                        },
                    )
                    .background(ReferenceUpcoming)
                    .cornerRadius(if (condensed) 7.dp else if (expanded) 9.dp else 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(
                            when {
                                expanded -> 12.dp
                                condensed -> 9.dp
                                else -> 11.dp
                            },
                        )
                        .background(ReferenceWidgetBackground)
                        .cornerRadius(6.dp),
                ) {}
            }
        }
    }
}

@Composable
private fun ReferenceDaylightBar(
    state: PrayerWidgetState.Available,
    width: Dp,
    height: Dp,
    expanded: Boolean,
) {
    // The five prayer cells divide the panel into equal fifths, so the first and last
    // centres sit at 10% and 90%. The gradient must span those exact centres: putting the
    // daylight/night labels in the same row previously shortened it to the middle three
    // prayers and placed those labels underneath Fajr and Isha.
    val endpointInset = width * 0.10f
    val trackWidth = (width - endpointInset * 2f).coerceAtLeast(72.dp)
    val markerWidth = if (expanded) 6.dp else 5.dp
    val prayerProgress = state.prayerTimelineProgress.coerceIn(0f, 1f)
    val markerOffset = (trackWidth - markerWidth) * prayerProgress
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(ReferenceWidgetBackground)
            .cornerRadius(10.dp),
    ) {
        if (expanded) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(14.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.flaticon_weather_clear),
                    contentDescription = null,
                    modifier = GlanceModifier.size(13.dp),
                )
                Spacer(modifier = GlanceModifier.width(2.dp))
                WidgetText(
                    text = state.daylightLabel,
                    size = 8.sp,
                    color = ReferenceInk,
                    weight = WidgetFontWeight.Medium,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Image(
                    provider = ImageProvider(R.drawable.widget_night_crescent),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(ReferenceNight),
                    modifier = GlanceModifier.size(13.dp),
                )
                Spacer(modifier = GlanceModifier.width(2.dp))
                WidgetText(
                    text = state.nightLabel,
                    size = 8.sp,
                    color = ReferenceInk,
                    weight = WidgetFontWeight.Medium,
                    align = WidgetTextAlign.End,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
            }
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Spacer(modifier = GlanceModifier.width(endpointInset))
            Box(
                modifier = GlanceModifier.width(trackWidth).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(WidgetDaylightBarArtwork.bitmap),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(if (expanded) 8.dp else 7.dp),
                )
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Spacer(modifier = GlanceModifier.width(markerOffset))
                    Box(
                        modifier = GlanceModifier
                            .width(markerWidth)
                            .height(if (expanded) 14.dp else 12.dp)
                            .background(ReferenceForest)
                            .cornerRadius(3.dp),
                    ) {}
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
            }
            Spacer(modifier = GlanceModifier.width(endpointInset))
        }
    }
}

/** A source-backed dua or hadith panel, linked to its full in-app reading. */
@Composable
private fun ReferenceDevotionalPanel(
    state: PrayerWidgetState.Available,
    height: Dp,
) {
    val context = LocalContext.current
    val reminder = state.reminder
    val expanded = height >= 140.dp
    val artworkWidth = (height * 0.68f).coerceIn(84.dp, 96.dp)
    val prayerContext = "After ${state.currentPrayerName()}"
    val reminderIcon = when (reminder.caption) {
        "Dua" -> R.drawable.sample_dua_icon
        "Hadith" -> R.drawable.sample_quran_icon
        else -> R.drawable.ic_prayer
    }
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(
                imageProvider = ImageProvider(R.drawable.prayer_widget_devotional_panel),
                contentScale = ContentScale.FillBounds,
            )
            .cornerRadius(22.dp)
            .clickable(reminder.openAction(context)),
    ) {
        AnimatedFoliage(
            phase = state.dayPhase,
            placement = WidgetFoliagePlacement.DEVOTIONAL_RIGHT,
            modifier = GlanceModifier.fillMaxSize(),
        )
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(end = 12.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .width(artworkWidth)
                    .fillMaxHeight(),
            ) {
                Image(
                    provider = ImageProvider(R.drawable.prayer_widget_devotional_lantern_v2),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(22.dp),
                )
                Column(modifier = GlanceModifier.padding(start = 8.dp, top = 9.dp)) {
                    WidgetText(
                        text = "SMALL\nDHIKR\nA BRIGHTER\nTOMORROW",
                        size = 7.sp,
                        color = ReferenceForestOn,
                        weight = WidgetFontWeight.Medium,
                        maxLines = 4,
                        modifier = GlanceModifier.width(58.dp).wrapContentHeight(),
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Box(
                        modifier = GlanceModifier
                            .width(22.dp)
                            .height(1.dp)
                            .background(ReferenceForestOn),
                    ) {}
                }
            }
            Spacer(modifier = GlanceModifier.width(12.dp))
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .padding(vertical = 6.dp),
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Box(
                        modifier = GlanceModifier
                            .width(4.dp)
                            .height(17.dp)
                            .background(ReferenceForest)
                            .cornerRadius(3.dp),
                    ) {}
                    Spacer(modifier = GlanceModifier.width(5.dp))
                    Image(
                        provider = ImageProvider(reminderIcon),
                        contentDescription = reminder.caption,
                        colorFilter = ColorFilter.tint(ReferenceForest),
                        modifier = GlanceModifier.size(15.dp),
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    WidgetText(
                        text = reminder.caption,
                        size = if (expanded) 13.5.sp else 13.sp,
                        color = ReferenceInk,
                        weight = WidgetFontWeight.Bold,
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                    Spacer(modifier = GlanceModifier.width(7.dp))
                    Box(
                        modifier = GlanceModifier
                            .wrapContentWidth()
                            .background(ReferencePinSurface)
                            .cornerRadius(10.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        WidgetText(
                            text = prayerContext,
                            size = 8.sp,
                            color = ReferenceForest,
                            weight = WidgetFontWeight.Medium,
                            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                        )
                    }
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Box(
                        modifier = GlanceModifier
                            .wrapContentWidth()
                            .background(ReferenceHeaderPill)
                            .cornerRadius(14.dp)
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        WidgetText(
                            text = "See more  ›",
                            size = 9.sp,
                            color = ReferenceTopHeaderInk,
                            weight = WidgetFontWeight.Medium,
                            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                reminder.arabic?.let { arabic ->
                    WidgetText(
                        text = arabic,
                        size = if (expanded) 15.sp else 14.sp,
                        color = ReferenceForest,
                        weight = arabicFontFor(context),
                        // The transliteration row was removed specifically to make room
                        // for the prayer itself. Let the selected Quran face wrap instead
                        // of ellipsising the Arabic after a single line.
                        maxLines = if (expanded) 2 else 1,
                        align = WidgetTextAlign.End,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(end = 30.dp),
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
                WidgetText(
                    text = "“${reminder.text}”",
                    size = if (expanded) 10.5.sp else 9.75.sp,
                    color = ReferenceTopHeaderInk,
                    weight = WidgetFontWeight.Medium,
                    maxLines = 2,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(end = 30.dp),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Image(
                        provider = ImageProvider(reminderIcon),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(ReferenceForest),
                        modifier = GlanceModifier.size(13.dp),
                    )
                    Spacer(modifier = GlanceModifier.width(5.dp))
                    WidgetText(
                        text = listOfNotNull(reminder.sourceName, reminder.sourceDetail)
                            .joinToString(" · "),
                        size = if (expanded) 9.sp else 8.5.sp,
                        color = ReferenceTopHeaderInk,
                        weight = WidgetFontWeight.Medium,
                        modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
                    )
                    Spacer(modifier = GlanceModifier.width(28.dp))
                }
            }
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(end = 9.dp, bottom = 8.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(32.dp)
                    .background(ReferenceWidgetBackground)
                    .cornerRadius(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_bookmark),
                    contentDescription = "Save devotional",
                    colorFilter = ColorFilter.tint(ReferenceForest),
                    modifier = GlanceModifier.size(16.dp),
                )
            }
        }
    }
}

private fun WidgetPrayer.phaseArtwork(): Int = when (name) {
    "Fajr" -> R.drawable.prayer_widget_phase_fajr
    "Dhuhr" -> R.drawable.prayer_widget_phase_dhuhr
    "Asr" -> R.drawable.prayer_widget_phase_asr
    "Maghrib" -> R.drawable.prayer_widget_phase_maghrib
    else -> R.drawable.prayer_widget_phase_isha
}

private fun DailyReminder.openAction(context: Context) = target?.let { target ->
    actionStartIntent(
        WidgetNavigationBus.put(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            target,
        ),
    )
} ?: actionStartActivity<MainActivity>()

/** Mid-height reference hierarchy: the same hero and prayer journey, without the dua. */
@Composable
private fun ReferenceMediumPrayerContent(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    val sectionGap = 6.dp
    // At the smallest medium footprint this resolves to roughly 130dp per section.
    // Taller variants grow both pieces together, retaining the supplied composition
    // instead of replacing it with the legacy mosque-circle/banner widget.
    val heroHeight = (contentSize.height * 0.50f).coerceIn(128.dp, 185.dp)
    val panelHeight = (contentSize.height - heroHeight - sectionGap).coerceAtLeast(126.dp)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        ReferencePrayerHero(
            state = state,
            width = contentSize.width,
            height = heroHeight,
        )
        Spacer(modifier = GlanceModifier.height(sectionGap))
        ReferencePrayerTimeline(
            state = state,
            width = contentSize.width,
            height = panelHeight,
        )
    }
}

/** Short-height reference hierarchy: crop to the same illustrated prayer hero. */
@Composable
private fun ReferenceShortPrayerContent(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    ReferencePrayerHero(
        state = state,
        width = contentSize.width,
        height = contentSize.height,
    )
}

/** All five prayers reflowed into a responsive horizontal panel for medium heights. */
@Composable
private fun ReferencePrayerDayStripPanel(
    state: PrayerWidgetState.Available,
    width: Dp,
    height: Dp,
) {
    val showHeading = height >= 76.dp
    val narrow = width < 260.dp
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(16.dp)
            .padding(
                start = if (narrow) 4.dp else 8.dp,
                top = 10.dp,
                end = if (narrow) 4.dp else 8.dp,
                bottom = 6.dp,
            ),
    ) {
        if (showHeading) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                WidgetText(
                    text = "Today's Prayers",
                    size = (if (narrow) 11f else 12.5f).sp,
                    color = GlanceTheme.colors.onSurface,
                    weight = WidgetFontWeight.Medium,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                val statusText = state.insight?.let {
                    "${state.currentPrayerName().uppercase()} · NOW"
                } ?: "NEXT · ${state.nextPrayer.name.uppercase()}"
                WidgetText(
                    text = statusText,
                    size = (if (narrow) 8f else 9f).sp,
                    color = GlanceTheme.colors.primary,
                    weight = WidgetFontWeight.Bold,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
            }
            Spacer(modifier = GlanceModifier.height(4.dp))
            FadingHorizontalSeparator()
            Spacer(modifier = GlanceModifier.height(3.dp))
        }
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            DayStripContent(
                prayers = state.prayers,
                nameSize = if (narrow) 9f else 10.5f,
                timeSize = if (narrow) 8.5f else 9.5f,
                showIcons = height >= 92.dp,
                currentPrayerName = state.currentPrayerName(),
            )
        }
    }
}

/** Current prayer, elapsed duration and prayer-window timeline from the reference hero. */
@Composable
private fun ReferenceCurrentPrayerHero(
    state: PrayerWidgetState.Available,
    width: Dp,
    height: Dp,
    compact: Boolean,
) {
    val context = LocalContext.current
    val currentName = state.currentPrayerName()
    val currentPrayer = state.prayers.firstOrNull {
        it.name.equals(currentName, ignoreCase = true)
    } ?: state.nextPrayer
    val elapsed = state.insight?.elapsed.orEmpty()
    val duration = elapsed.substringBefore(" since ")
        .takeIf { it.isNotBlank() && it != elapsed }
        ?: elapsed.takeIf { it.isNotBlank() }
        ?: state.countdown.removePrefix("in ").trim()
    val label = currentPrayer.name.uppercase().toCharArray().joinToString(" ")
    val iconRoom = if (compact) 58f else 72f
    val textWidth = (width.value - iconRoom).coerceAtLeast(1f)
    val durationSize = WidgetTypography
        .fittingSize(context, duration, textWidth * 0.56f, bold = true)
        .coerceIn(if (compact) 20f else 24f, if (compact) 25f else 30f)
    val endpointSize = if (compact) 9.5f else 10.5f

    Row(
        modifier = GlanceModifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(if (compact) 50.dp else 64.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(if (compact) 25.dp else 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.flaticon_mosque_widget_4358830),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(if (compact) 30.dp else 39.dp),
            )
        }
        Spacer(modifier = GlanceModifier.width(if (compact) 8.dp else 10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetText(
                text = label,
                size = (if (compact) 10.5f else 12f).sp,
                color = GlanceTheme.colors.primary,
                weight = WidgetFontWeight.Bold,
            )
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.Bottom,
            ) {
                WidgetText(
                    text = duration,
                    size = durationSize.sp,
                    color = GlanceTheme.colors.onSurface,
                    weight = WidgetFontWeight.Bold,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                WidgetText(
                    text = "since ${currentPrayer.name}",
                    size = (if (compact) 10.5f else 12f).sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                    weight = WidgetFontWeight.Medium,
                    // WidgetText is backed by a TextView, so bottom-aligning its box with
                    // the much larger duration box does not align their font baselines.
                    // Lift the smaller run by the measured font-metric difference.
                    modifier = GlanceModifier
                        .defaultWeight()
                        .wrapContentHeight()
                        .padding(bottom = if (compact) 3.dp else 4.dp),
                )
            }
            Spacer(modifier = GlanceModifier.height(if (compact) 4.dp else 6.dp))
            state.windowProgress?.let { progress ->
                ExpressiveProgressBar(progress = progress, width = textWidth.dp)
                Spacer(modifier = GlanceModifier.height(if (compact) 3.dp else 4.dp))
            }
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                WidgetText(
                    text = "${currentPrayer.time} (${currentPrayer.name})",
                    size = endpointSize.sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                    weight = WidgetFontWeight.Medium,
                    modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
                )
                WidgetText(
                    text = "${state.nextPrayer.time} (${state.nextPrayer.name})",
                    size = endpointSize.sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                    weight = WidgetFontWeight.Medium,
                    align = WidgetTextAlign.End,
                    modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
                )
            }
        }
    }
}

/** The timetable card is separate from the next-prayer card, as in the reference. */
@Composable
private fun ReferencePrayerSchedulePanel(
    state: PrayerWidgetState.Available,
    rowHeight: Dp,
    compact: Boolean,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(if (compact) 16.dp else 20.dp)
            .padding(
                start = if (compact) 8.dp else 10.dp,
                top = if (compact) 6.dp else 8.dp,
                end = if (compact) 8.dp else 10.dp,
                // The widget surface already supplies the uniform outer bottom inset.
                // Adding another inset here made the space after Isha look larger than
                // the visible top inset, even though the outer card itself was even.
                bottom = 0.dp,
            ),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(if (compact) 20.dp else 24.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            WidgetText(
                text = "Today's Prayers",
                size = (if (compact) 12.5f else 14f).sp,
                color = GlanceTheme.colors.onSurface,
                weight = WidgetFontWeight.Medium,
                modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
            )
            WidgetText(
                text = state.place,
                size = (if (compact) 9.5f else 10.5f).sp,
                color = GlanceTheme.colors.onSurfaceVariant,
                weight = WidgetFontWeight.Regular,
                modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            )
            Spacer(modifier = GlanceModifier.width(5.dp))
            StaticMeteocon(
                prayer = state.nextPrayer,
                size = if (compact) 17.dp else 19.dp,
            )
            state.nextPrayer.temperature?.let { temperature ->
                Spacer(modifier = GlanceModifier.width(5.dp))
                WidgetText(
                    text = temperature,
                    size = (if (compact) 10f else 11f).sp,
                    color = GlanceTheme.colors.onSurface,
                    weight = WidgetFontWeight.Medium,
                    modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                )
            }
            Spacer(modifier = GlanceModifier.width(5.dp))
            WidgetText(
                text = "›",
                size = (if (compact) 15f else 17f).sp,
                color = GlanceTheme.colors.onSurfaceVariant,
                weight = WidgetFontWeight.Medium,
                modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            )
        }
        Spacer(modifier = GlanceModifier.height(4.dp))
        FadingHorizontalSeparator()
        PrayerScheduleList(
            prayers = state.prayers,
            textSize = if (compact) 13f else 14.5f,
            rowHeight = rowHeight,
            showWeatherIcons = true,
            showDividers = true,
            compact = compact,
            highlightedPrayerName = state.currentPrayerName(),
        )
    }
}

/** Best current-prayer label available to the reference hero and selected schedule row. */
private fun PrayerWidgetState.Available.currentPrayerName(): String = insight?.caption
    ?.takeIf { caption -> prayers.any { it.name.equals(caption, ignoreCase = true) } }
    ?: prayers.lastOrNull { it.isPast }?.name
    ?: nextPrayer.name

/** Keeps the editorial hero's elapsed line compact and consistent with “2h 30m”. */
private fun String.compactWidgetDuration(): String = this
    .replace(" hours", "h")
    .replace(" hour", "h")
    .replace(" minutes", "m")
    .replace(" minute", "m")

/**
 * The short card keeps the full widget's visual identity while omitting the timetable,
 * which cannot remain readable at this height. Two-row cards have enough room for the
 * full hero and banner sizes; genuinely tight cards step both down together.
 */
@Composable
private fun ShortPrayerContent(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    val roomy = contentSize.height >= 145.dp
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // At the very bottom of this tier the location is already present in the title
        // bar and the date row would steal space from the actual prayer information.
        if (contentSize.height >= 115.dp) {
            PrayerContextRow(state)
            Spacer(modifier = GlanceModifier.height(if (roomy) 6.dp else 4.dp))
        }
        FullPrayerHero(state = state, width = contentSize.width, compact = !roomy)
        Spacer(modifier = GlanceModifier.defaultWeight())
        NextPrayerBanner(state = state, compact = contentSize.height < 175.dp)
    }
}

/** x3: context, prayer-window hero, and the complete day at a glance. */
@Composable
private fun ExpandedPrayerContent(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    // This is the compact form of the full design, not a separate visual treatment. The
    // fixed budget covers context + hero + schedule chrome; the five rows evenly share
    // everything left so resizing never drops the last prayer.
    // The panel and titled surface already carry their own lower insets. Give all other
    // available height to the rows so a compact resize does not leave a second band below
    // Isha. Heights too small to support these rows use ShortPrayerContent instead.
    val scheduleRowHeight = ((contentSize.height.value - 165f) /
        state.prayers.size.coerceAtLeast(1))
        .coerceAtLeast(21f)
        .dp

    Column(modifier = GlanceModifier.fillMaxSize()) {
        PrayerContextRow(state)
        Spacer(modifier = GlanceModifier.height(6.dp))
        FullPrayerHero(state = state, width = contentSize.width, compact = true)
        Spacer(modifier = GlanceModifier.height(6.dp))
        // Any rounding difference in the launcher's reported height is absorbed above
        // the panel, keeping its lower edge locked to the shared surface inset.
        Spacer(modifier = GlanceModifier.defaultWeight())
        PrayerSchedulePanel(
            state = state,
            rowHeight = scheduleRowHeight,
            compact = true,
        )
    }
}

/** x4: use the extra height for a readable full schedule rather than larger gaps. */
@Composable
private fun FullPrayerContent(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    // The schedule panel already provides its own lower padding and the titled surface
    // adds the safe widget-edge inset. Spend the remaining height on the five rows rather
    // than creating a second visible band below the panel.
    val scheduleRowHeight = ((contentSize.height.value - 221f) /
        state.prayers.size.coerceAtLeast(1))
        // Samsung exposes taller row spans than Pixel for the same nominal widget size.
        // Let the five rows share all remaining space instead of leaving a band below
        // Isha; 30dp is the minimum at the smallest size that enters this layout.
        .coerceAtLeast(30f)
        .dp

    Column(modifier = GlanceModifier.fillMaxSize()) {
        PrayerContextRow(state)
        Spacer(modifier = GlanceModifier.height(10.dp))
        FullPrayerHero(state = state, width = contentSize.width)
        Spacer(modifier = GlanceModifier.height(10.dp))
        // Keep the same lower edge as the compact and short layouts even when Samsung
        // rounds a row span to a slightly different dp height.
        Spacer(modifier = GlanceModifier.defaultWeight())
        PrayerSchedulePanel(
            state = state,
            rowHeight = scheduleRowHeight,
        )
    }
}

/**
 * The reference widget uses one strong illustration to anchor the live prayer window.
 * Keep it theme-tinted and use the user-selected Flaticon mosque-and-crescent artwork.
 */
@Composable
private fun FullPrayerHero(
    state: PrayerWidgetState.Available,
    width: Dp,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val title = state.insight?.title ?: "Prayer now"
    val elapsed = state.insight?.elapsed ?: state.nextPrayer.time
    val imageRoom = if (compact) 58f else 76f
    val titleSize = WidgetTypography
        .fittingSize(context, title, (width.value - imageRoom).coerceAtLeast(1f), bold = true)
        .coerceAtMost(if (compact) 17f else 19f)

    Row(
        modifier = GlanceModifier.fillMaxWidth().height(if (compact) 52.dp else 70.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(if (compact) 46.dp else 64.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(if (compact) 23.dp else 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.flaticon_mosque_widget_4358830),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(if (compact) 27.dp else 38.dp),
            )
        }
        Spacer(modifier = GlanceModifier.width(if (compact) 10.dp else 12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetText(
                text = title,
                size = titleSize.sp,
                color = GlanceTheme.colors.onSurface,
                weight = WidgetFontWeight.Medium,
            )
            WidgetText(
                text = elapsed,
                size = (if (compact) 14.5f else 17f).sp,
                color = GlanceTheme.colors.primary,
                weight = WidgetFontWeight.Bold,
            )
            state.windowProgress?.let { progress ->
                Spacer(modifier = GlanceModifier.height(if (compact) 3.dp else 5.dp))
                ExpressiveProgressBar(
                    progress = progress,
                    width = width - imageRoom.dp,
                )
            }
        }
    }
}

/** Inset schedule card with a clear next-prayer handoff and scannable weather rows. */
@Composable
private fun PrayerSchedulePanel(
    state: PrayerWidgetState.Available,
    rowHeight: Dp,
    compact: Boolean = false,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(if (compact) 16.dp else 20.dp)
            .padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 6.dp else 10.dp,
            ),
    ) {
        NextPrayerBanner(state = state, compact = compact)
        Spacer(modifier = GlanceModifier.height(if (compact) 8.dp else 14.dp))
        WidgetText(
            text = "Today's Prayers",
            size = (if (compact) 12.5f else 14f).sp,
            color = GlanceTheme.colors.onSurface,
            weight = WidgetFontWeight.Medium,
        )
        Spacer(modifier = GlanceModifier.height(if (compact) 2.dp else 6.dp))
        PrayerScheduleList(
            prayers = state.prayers,
            textSize = if (compact) 14f else 16f,
            rowHeight = rowHeight,
            showWeatherIcons = true,
            showDividers = false,
            compact = compact,
        )
    }
}

/** Shared expressive next-prayer treatment for full, resized, and short heights. */
@Composable
private fun NextPrayerBanner(
    state: PrayerWidgetState.Available,
    compact: Boolean,
    showWeatherIcon: Boolean = false,
    height: Dp = if (compact) 52.dp else 58.dp,
) {
    val countdown = state.countdown.removePrefix("in ").trim()
    val countdownLabel = if (countdown.equals("now", ignoreCase = true)) "Now" else countdown
    val countdownText = if (countdownLabel == "Now") "Now" else "in $countdownLabel"

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(if (compact) 14.dp else 16.dp)
            .padding(horizontal = if (compact) 10.dp else 12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (showWeatherIcon) {
            Box(
                modifier = GlanceModifier
                    .size(if (compact) 32.dp else 38.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(if (compact) 16.dp else 19.dp),
                contentAlignment = Alignment.Center,
            ) {
                StaticMeteocon(
                    prayer = state.nextPrayer,
                    size = if (compact) 20.dp else 24.dp,
                )
            }
            Spacer(modifier = GlanceModifier.width(if (compact) 8.dp else 10.dp))
        }
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .height(if (compact) 40.dp else 46.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            WidgetText(
                // Use the same tracked, all-caps treatment as the current-prayer label
                // in the hero (for example, "D H U H R").
                text = "N E X T  P R A Y E R",
                size = (if (compact) 9.5f else 10.5f).sp,
                color = GlanceTheme.colors.primary,
                weight = WidgetFontWeight.Bold,
            )
            WidgetText(
                text = state.nextPrayer.name,
                size = (if (compact) 17f else 20f).sp,
                color = GlanceTheme.colors.onSurface,
                weight = WidgetFontWeight.Bold,
            )
        }
        Column(
            modifier = GlanceModifier.height(if (compact) 40.dp else 46.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.End,
        ) {
            WidgetText(
                text = countdownText,
                size = (if (compact) 15f else 18f).sp,
                color = GlanceTheme.colors.primary,
                weight = WidgetFontWeight.Bold,
                align = WidgetTextAlign.End,
                modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            )
            WidgetText(
                text = state.nextPrayer.time,
                size = (if (compact) 10.5f else 12f).sp,
                color = GlanceTheme.colors.onSurfaceVariant,
                weight = WidgetFontWeight.Medium,
                align = WidgetTextAlign.End,
                modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            )
        }
        Spacer(modifier = GlanceModifier.width(if (compact) 8.dp else 10.dp))
        Box(
            modifier = GlanceModifier
                .size(if (compact) 28.dp else 30.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(if (compact) 14.dp else 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            WidgetText(
                text = "›",
                size = (if (compact) 18f else 20f).sp,
                color = GlanceTheme.colors.onPrimaryContainer,
                weight = WidgetFontWeight.Medium,
                modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            )
        }
    }
}

@Composable
private fun PrayerContextRow(state: PrayerWidgetState.Available) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        WidgetText(
            text = state.dateLabel,
            size = 13.sp,
            color = GlanceTheme.colors.onSurfaceVariant,
            weight = WidgetFontWeight.Medium,
            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        WidgetText(
            text = "${state.solarEvent.label}  ${state.solarEvent.time}",
            size = 11.5.sp,
            color = GlanceTheme.colors.onSurfaceVariant,
            weight = WidgetFontWeight.Medium,
            align = WidgetTextAlign.End,
            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
        )
    }
}

/** A fixed-height version of the x2 hero suitable above supporting content. */
@Composable
private fun CompactHeroSummary(
    state: PrayerWidgetState.Available,
    width: Dp,
    textScale: Float,
) {
    val context = LocalContext.current
    val title = state.insight?.title ?: "Next prayer"
    val elapsed = state.insight?.elapsed ?: state.nextPrayer.time
    val countdown = state.countdown.removePrefix("in ").trim()
    val next = if (countdown == "now") {
        "${state.nextPrayer.name} now"
    } else {
        "${state.nextPrayer.name} in $countdown"
    }
    val titleSize = WidgetTypography
        .fittingSize(context, title, width.value, bold = true)
        .coerceAtMost(18f * textScale)
    val nextSize = WidgetTypography
        .fittingSize(context, next, width.value * 0.66f, bold = true)
        .coerceAtMost(18f * textScale)

    WidgetText(
        text = title,
        size = titleSize.sp,
        color = GlanceTheme.colors.onSurface,
        weight = WidgetFontWeight.Medium,
    )
    WidgetText(
        text = elapsed,
        size = (16f * textScale).sp,
        color = GlanceTheme.colors.primary,
        weight = WidgetFontWeight.Bold,
    )
    state.windowProgress?.let { progress ->
        Spacer(modifier = GlanceModifier.height(8.dp))
        ExpressiveProgressBar(progress = progress, width = width)
    }
    Spacer(modifier = GlanceModifier.height(9.dp))
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        WidgetText(
            text = "Next Prayer",
            size = (14.5f * textScale).sp,
            color = GlanceTheme.colors.primary,
            weight = WidgetFontWeight.Bold,
            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        WidgetText(
            text = next,
            size = nextSize.sp,
            color = GlanceTheme.colors.primary,
            weight = WidgetFontWeight.Bold,
            align = WidgetTextAlign.End,
            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
        )
    }
}

@Composable
private fun PrayerScheduleList(
    prayers: List<WidgetPrayer>,
    textSize: Float,
    rowHeight: Dp,
    showTemperature: Boolean = true,
    showDividers: Boolean = true,
    showWeatherIcons: Boolean = false,
    compact: Boolean = false,
    highlightedPrayerName: String? = null,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        prayers.forEachIndexed { index, prayer ->
            val highlighted = prayer.name.equals(highlightedPrayerName, ignoreCase = true)
            val rowModifier = if (highlighted) {
                GlanceModifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(if (compact) 10.dp else 12.dp)
                    .padding(horizontal = if (compact) 6.dp else 8.dp)
            } else {
                GlanceModifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .padding(horizontal = if (compact) 6.dp else 8.dp)
            }
            // Each row is wrapped rather than placed directly so the nudge below the last
            // divider costs no extra child: Glance truncates a Column after ten of them,
            // and five rows with four dividers already sits at nine.
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Row(
                    modifier = rowModifier,
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    if (showWeatherIcons) {
                        StaticMeteocon(
                            prayer = prayer,
                            size = if (compact) 20.dp else 24.dp,
                        )
                        Spacer(modifier = GlanceModifier.width(if (compact) 8.dp else 10.dp))
                    }
                    WidgetText(
                        text = prayer.name,
                        size = textSize.sp,
                        color = when {
                            highlighted -> GlanceTheme.colors.onPrimaryContainer
                            prayer.isPast -> GlanceTheme.colors.outline
                            else -> GlanceTheme.colors.onSurface
                        },
                        weight = if (highlighted || prayer.isNext) {
                            WidgetFontWeight.Bold
                        } else {
                            WidgetFontWeight.Medium
                        },
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    if (showTemperature) {
                        WidgetText(
                            text = prayer.temperature.orEmpty(),
                            size = (textSize * 0.78f).sp,
                            color = GlanceTheme.colors.onSurfaceVariant,
                            weight = WidgetFontWeight.Regular,
                            align = WidgetTextAlign.End,
                            modifier = GlanceModifier
                                .width(if (compact) 38.dp else 42.dp)
                                .wrapContentHeight(),
                        )
                    }
                    WidgetText(
                        text = prayer.time,
                        size = textSize.sp,
                        color = if (highlighted) {
                            GlanceTheme.colors.onPrimaryContainer
                        } else {
                            GlanceTheme.colors.onSurface
                        },
                        weight = if (highlighted || prayer.isNext) {
                            WidgetFontWeight.Bold
                        } else {
                            WidgetFontWeight.Medium
                        },
                        align = WidgetTextAlign.End,
                        modifier = GlanceModifier
                            .width(if (compact) 70.dp else 78.dp)
                            .wrapContentHeight(),
                    )
                }
            }
            if (showDividers && index != prayers.lastIndex) {
                FadingHorizontalSeparator()
            }
        }
    }
}

/**
 * The next prayer as a hero block, sized to whatever room it is handed.
 *
 * [contentSize] is the space left after the surrounding surface's own insets, not the
 * card footprint — see the call site.
 */
@Composable
private fun ColumnScope.PrayerHeroContent(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    val insight = state.insight

    // Hierarchy copied from the "Prayer now" tile actually on the home screen — the phase
    // title leads at full size, the elapsed reading follows smaller and dimmer, then the
    // next prayer under its own label. See the tile at SwipeableBigTiles.kt:2540 onward.
    // (There is a second, unused PrayerTextContent further down that file which inverts
    // these two; matching it instead is what made this widget read upside-down against
    // the tile it is meant to mirror.)
    //
    val context = LocalContext.current
    val contentWidth = contentSize.width
    val contentHeight = contentSize.height
    val width = contentWidth.value

    val titleText = insight?.title ?: "Next prayer"
    val elapsedText = insight?.elapsed ?: state.nextPrayer.time

    // The largest rung of the ramp whose four lines all fit, measured rather than
    // estimated — both across the card and down it.
    //
    // Height is checked against the rung's own line count: the title is allowed to wrap to
    // two lines, so a rung that would wrap has to pay for the extra line box before it can
    // be accepted. That is the whole reason this is a search rather than a formula — the
    // number of lines depends on the size, and the size depends on the number of lines.
    // Walking the ladder downwards resolves it without either assumption being made up
    // front, which is what previously left wide cards reserving a second line they never
    // used and shrinking every size on the card to pay for it.
    val lineHeight = WidgetTypography.lineHeightPerSp(context)
    val step = WidgetTypography.STEPS.lastOrNull { candidate ->
        val titleLines = WidgetTypography.lineCount(context, titleText, width, candidate.title)
        // Line box height comes from the font's own metrics; the bar and the two group
        // gaps are fixed, and GROUP_GAP_MIN is the floor for each gap.
        val needed = lineHeight * (
            candidate.title * titleLines +
                candidate.elapsed +
                candidate.label +
                candidate.next
            ) + PROGRESS_BAR_HEIGHT + (GROUP_GAP_MIN.value * 2)
        val fitsDown = needed <= contentHeight.value
        // A wrapped title is fine; a wrapped supporting line is not, since those are
        // single-line by design and would be truncated rather than flowed.
        val fitsAcross = WidgetTypography.fittingSize(
            context = context,
            text = elapsedText,
            maxWidthDp = width,
            bold = true,
        ) >= candidate.elapsed
        // The title has to fit inside the two lines it is allowed, not merely need more
        // than one. On a narrow card a long phrase like "Best Time to Pray Maghrib" runs
        // to three lines well before it runs out of height, and since the view is capped
        // at two it would be quietly ellipsized rather than wrapped — the height budget
        // would have been "satisfied" by a title the user cannot finish reading.
        val titleFits =
            WidgetTypography.fittingSize(context, titleText, width, lines = TITLE_MAX_LINES) >=
                candidate.title
        fitsDown && fitsAcross && titleFits
    } ?: WidgetTypography.STEPS.first()

    val titleSize = step.title.sp
    val elapsedSize = step.elapsed.sp
    val labelSize = step.label.sp
    val nextSize = step.next.sp

    // Full title, prayer name included ("Make Time for Fajr"). It was trimmed to "Make
    // Time" back when it had to survive a 110dp bucket; the layout now sizes its own type,
    // so the phrase no longer has to be shortened to fit.
    WidgetText(
        text = titleText,
        size = titleSize,
        color = GlanceTheme.colors.onSurface,
        weight = WidgetFontWeight.Medium,
        maxLines = TITLE_MAX_LINES,
    )

    WidgetText(
        text = elapsedText,
        size = elapsedSize,
        color = GlanceTheme.colors.primary,
        weight = WidgetFontWeight.Bold,
    )

    // The two gaps are a fixed minimum plus a weighted spacer, so the height the type
    // estimate above did not claim is spread between the groups by the layout itself.
    //
    // Computing the gaps from that estimate instead cannot work: the estimate is what
    // caps heroSize, so on any card where height is the binding axis the leftover it
    // reports is identically zero, while the real content — shorter than the deliberately
    // generous estimate — leaves a band of dead space above the bottom edge. A weight
    // absorbs whatever is actually left without having to predict it.
    // A fixed spacer and a weighted one rather than both modifiers on a single spacer:
    // Glance's translator resolves a weight to layout_weight with a zero base dimension,
    // so a height on the same modifier chain would not survive as a floor.
    state.windowProgress?.let { progress ->
        Spacer(modifier = GlanceModifier.height(GROUP_GAP_MIN))
        Spacer(modifier = GlanceModifier.defaultWeight())
        ExpressiveProgressBar(progress = progress, width = contentWidth)
    }

    Spacer(modifier = GlanceModifier.height(GROUP_GAP_MIN))
    Spacer(modifier = GlanceModifier.defaultWeight())

    WidgetText(
        // Title case, accented — the tile's own label, not a shouted version of it.
        text = "Next Prayer",
        size = labelSize,
        color = GlanceTheme.colors.primary,
        weight = WidgetFontWeight.Bold,
    )

    // The generator words this as "Next • Dhuhr in 2h 15m"; the bullet and its lead-in are
    // what the label above already says, so only the tail is kept — the same slice the
    // tile takes. Falls back to the widget's own fields when no insight is being tracked,
    // which is the one case the generator returns null.
    val nextDetail = insight?.nextPrayerInfo
        ?.substringAfter('•')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "${state.nextPrayer.name} in ${state.countdown.removePrefix("in ")}"
    val nextName = nextDetail.substringBefore(" in ").trim()
    val nextCountdown = nextDetail.substringAfter(" in ", missingDelimiterValue = "").trim()

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        // Three runs rather than one string, because the tile weights them differently —
        // the prayer name and its countdown both carry, the "in" between them does not.
        // Each run is its own view, so what the tile does with spans inside a single
        // AnnotatedString is three siblings here.
        WidgetText(
            text = nextName,
            size = nextSize,
            color = GlanceTheme.colors.onSurface,
            weight = WidgetFontWeight.Bold,
            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
        )
        if (nextCountdown.isNotEmpty()) {
            WidgetText(
                text = " in ",
                size = nextSize,
                color = GlanceTheme.colors.onSurfaceVariant,
                weight = WidgetFontWeight.Regular,
                modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            )
            WidgetText(
                text = nextCountdown,
                size = nextSize,
                color = GlanceTheme.colors.primary,
                weight = WidgetFontWeight.Bold,
                modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        StaticMeteocon(prayer = state.nextPrayer, size = 20.dp)
    }

    // The hero ends on its largest line, so retain a little optical clearance inside the
    // uniformly padded surface. Placed after the row so the weighted gaps above surrender
    // the space, rather than the content growing past the card.
    Spacer(modifier = GlanceModifier.height(HERO_BOTTOM_CLEARANCE))
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
 * Transparent time-colored foliage advanced by the launcher itself.
 *
 * Its eight frames describe one very small sway cycle. A long crossfade in the XML keeps
 * the motion soft, and the palette is regenerated only when the widget's solar phase
 * changes, avoiding a continuous app process or battery cost.
 */
@Composable
private fun AnimatedFoliage(
    phase: WidgetDayPhase,
    modifier: GlanceModifier,
    placement: WidgetFoliagePlacement = WidgetFoliagePlacement.BOTH,
) {
    val context = LocalContext.current
    val frames = WidgetFoliageArtwork.frames(phase, placement)
    val remoteViews = RemoteViews(context.packageName, R.layout.widget_foliage_flipper).apply {
        FOLIAGE_FRAME_VIEW_IDS.forEachIndexed { index, viewId ->
            setImageViewBitmap(viewId, frames[index % frames.size])
        }
    }
    AndroidRemoteViews(remoteViews = remoteViews, modifier = modifier)
}


/**
 * The next prayer's static Meteocon.
 *
 * Weather changes still arrive with normal widget refreshes, but the icon itself remains
 * motionless so it does not compete with the hero or the intentionally subtle foliage.
 */
@Composable
private fun StaticMeteocon(
    prayer: WidgetPrayer,
    size: androidx.compose.ui.unit.Dp,
) {
    StaticMeteoconBitmap(
        still = prayer.weatherIcon,
        size = size,
    )
}

/** Meteocons Fill solar artwork is already coloured, so it must never receive a tint. */
@Composable
private fun StaticSolarMeteocon(
    event: WidgetSolarEvent,
    size: androidx.compose.ui.unit.Dp,
) {
    StaticMeteoconBitmap(
        still = event.icon,
        size = size,
    )
}

@Composable
private fun StaticMeteoconBitmap(
    still: Bitmap?,
    size: androidx.compose.ui.unit.Dp,
) {
    still?.let {
        Image(
            provider = ImageProvider(it),
            contentDescription = null,
            modifier = GlanceModifier.size(size),
        )
    }
}

private val FOLIAGE_FRAME_VIEW_IDS = intArrayOf(
    R.id.foliage_frame_0,
    R.id.foliage_frame_1,
    R.id.foliage_frame_2,
    R.id.foliage_frame_3,
    R.id.foliage_frame_4,
    R.id.foliage_frame_5,
    R.id.foliage_frame_6,
    R.id.foliage_frame_7,
)
