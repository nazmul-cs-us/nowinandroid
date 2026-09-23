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
import androidx.compose.runtime.remember
import androidx.glance.LocalGlanceId
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.core.content.res.ResourcesCompat
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
import android.view.ViewGroup
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Path
import android.graphics.Outline
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import android.widget.FrameLayout
import android.view.View
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.compose
import androidx.glance.ExperimentalGlanceApi
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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColorProvider
import com.starception.submission.MainActivity
import com.starception.submission.R
import com.starception.submission.core.designsystem.icon.topicIconResFor

/** The reference keeps its inner cards close to the outer shell: roughly 2% per side. */
private val WIDGET_PADDING = 8.dp
private val ReferenceWidgetBackground = ColorProvider(Color(0xFFFFFCF8))
private val ReferenceForestArgb = 0xFF0B4D43.toInt()
private val ReferenceForest = ColorProvider(Color(ReferenceForestArgb))
private val ReferenceForestOn = ColorProvider(Color(0xFFFFFFFF))
private val ReferenceHeroInk = Color(0xFFF4F1E6)
private val ReferenceHeroText = ColorProvider(ReferenceHeroInk)
private val ReferenceInk = ColorProvider(Color(0xFF18352E))
private val ReferenceMuted = ColorProvider(Color(0xFF4F665D))
private val ReferenceQuote = ColorProvider(Color(0xFF24463D))
private val ReferenceQuoteMark = ColorProvider(Color(0xFFA9A08D))
private val ReferenceHeaderPill = ColorProvider(Color(0xFFF0F2F5))
// Sampled from the supplied header design.
private val ReferenceTopHeaderPill = ColorProvider(Color(0xFFEAEAEF))
private val ReferenceTopHeaderInk = ColorProvider(Color(0xFF110F3B))
private val ReferenceTopHeaderMuted = ColorProvider(Color(0xFF61607A))
// Dark-plate variants: the location name and date sit directly on the widget plate, which
// turns dark when the widget's colour mode is Dark (or follows a dark system). The fixed
// indigo inks above vanish on that surface, so the on-plate copy switches to light inks.
private val ReferenceTopHeaderInkOnDark = ColorProvider(Color(0xFFF3F2FB))
private val ReferenceTopHeaderMutedOnDark = ColorProvider(Color(0xFFC3C2D6))
private val ReferencePinSurface = ColorProvider(Color(0xFFD9E1DC))
private val ReferenceHeaderPinInk = ColorProvider(Color(0xFF14352B))
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

/**
 * Ceiling on the devotional card's type ramp, as a multiple of the reference size.
 *
 * A flipper page can be better than twice [REFERENCE_DESIGN_DEVOTIONAL_HEIGHT]; every line
 * on the card is fitted to its own column besides, so this only keeps a very tall grant
 * from running the type past what the card's proportions can carry.
 */
private const val DEVOTIONAL_MAX_TYPE_SCALE = 2.0f

/** The header chips' size relative to the title, from the reference's 9.5/13.5 and 9/13.5. */
private const val DEVOTIONAL_CHIP_TYPE_RATIO = 9.5f / 13.5f
private const val DEVOTIONAL_ACTION_TYPE_RATIO = 9f / 13.5f

/**
 * Everything in the devotional header that is not text, in dp: the 4dp rule and its 7dp
 * gap, the 8dp before the first chip, both chips' 9dp horizontal padding, and the 8dp the
 * two chips must keep between them.
 */
private const val DEVOTIONAL_HEADER_FURNITURE = 4f + 7f + 8f + 18f + 8f + 18f

/** Rough line height of the widget's Latin type, for budgeting rows against a card. */
private const val TEXT_LINE_HEIGHT_EM = 1.35f

/**
 * Line height of the Arabic faces at the 0.72 spacing WidgetArabicText draws them with.
 *
 * The Quran faces declare an ascent and descent about twice their letter height, so even
 * reduced they sit well apart; this is what a line of them costs in dp per sp.
 */
private const val ARABIC_LINE_HEIGHT_EM = 1.44f

/**
 * How near the room's size a single Arabic line must reach to be preferred over wrapping.
 */
private const val ARABIC_SINGLE_LINE_SHARE = 0.85f

/**
 * The Arabic's ramp, per unit of the devotional's type scale.
 *
 * Set well above the Latin ramp on purpose. These faces carry their letters small inside a
 * tall em — the ascent and descent leave room for stacked marks — so Arabic set at the
 * same nominal size as the English beside it reads about a third smaller. The room is what
 * limits this in practice; the ramp only has to be high enough not to limit it first.
 */
private const val ARABIC_RAMP_SP = 24f

/** Top and bottom padding of the devotional's text column, plus the gaps it keeps. */
private val DEVOTIONAL_COLUMN_INSETS = 28.dp

/** The lantern column's spaced small caps, one phrase a line as in the reference. */
private val DHIKR_LINES = listOf("SMALL", "DHIKR", "A BRIGHTER", "TOMORROW")
private const val DHIKR_TRACKING_EM = 0.16f
private const val HERO_ARTWORK_MIN_ALPHA = 0.86f
/**
 * Tallest the hero may grow relative to its width. The 3:1 artwork's sky and foliage
 * margins absorb the difference (see alphaAdjustedImageProvider); past this the margins
 * would have to stretch so far that the composition stops looking like a photograph.
 */
private const val HERO_MIN_ASPECT = 1.95f

/**
 * Aspect at which the hero swaps between its wide (3:1) and tall (3:2) plate.
 *
 * The geometric mean of the two, so each is picked over the range where it is the closer
 * fit and neither is ever stretched by more than about 40%.
 */
private const val HERO_ARTWORK_SWITCH_ASPECT = 2.12f
private const val PANEL_ARTWORK_MIN_ALPHA = 0.64f

// The reference header's pin disc, weather capsule and refresh disc share one height:
// 90px of a 1148px-wide design, i.e. 32dp at this widget's width. The capsule is 3x that.
private val REFERENCE_HEADER_DISC = 34.dp

// Measured against the reference at equal width, the whole header band — plate top to
// hero top — is about 48dp: roughly 8dp above the discs and 7.5dp below them. Scaffold
// adds about 3dp of its own clearance above, so the row itself carries 9dp + 32dp + 7dp.
// Keep the height budget synchronized so the hero begins immediately below it.
private val HEADER_TOUCH_TARGET_TOP_PADDING = 8.dp
private val HEADER_TOUCH_TARGET_BOTTOM_PADDING = 6.dp
private val TITLE_BAR_HEIGHT = 61.dp
/** The weather capsule stands a little taller than the discs it sits between. */
private val REFERENCE_HEADER_CAPSULE_HEIGHT = 40.dp

// The content surface must clear the plate's bottom edge by the same amount it clears the
// left and right edges (WIDGET_PADDING), or the card reads as bottom-heavy — the full-bleed
// artwork cards (sky timeline, flipper) have no inset of their own to make up the shortfall.
private val TITLED_WIDGET_BOTTOM_PADDING = WIDGET_PADDING

/**
 * Slack left on any width a type size is fitted against.
 *
 * Paint.measureText and TextView's own layout do not agree to the last dp — default letter
 * spacing, hinting, and the room TextView keeps for an ellipsis all cost a little — so a
 * size fitted to exactly its column comes back ellipsised anyway. The devotional's
 * attribution was the case that showed it: fitted at 12.99sp into 181.6dp, and cut to
 * "Fortress of the Muslim · Pra...". Three percent covers the disagreement and is not
 * visible in the type.
 */
private const val TEXT_FIT_SLACK = 0.97f

/** Lines the phase title may wrap to; the ramp will not pick a size needing more. */
private const val TITLE_MAX_LINES = 2

/**
 * Aspect the foliage frames are rasterised at (see WidgetFoliageArtwork's bitmap sizes).
 *
 * Their ImageViews scale fitXY, so any box of a different shape stretches the leaves; the
 * cards size the strip against this instead of letting it fill.
 */
private const val FOLIAGE_ASPECT = 2.5f

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
// The full stack needs about the design's 401dp, less the 24dp its cards can each give up.
// A 5x4 grant on One UI leaves 387dp after the header; it must land here, not in the flipper.
private val REFERENCE_LAYOUT_MIN_HEIGHT = 376.dp
private val REFERENCE_LAYOUT_MIN_WIDTH = 280.dp
/** Shortest content area whose single page can hold the timeline or the devotional card. */
private val REFERENCE_FLIP_MIN_HEIGHT = 120.dp
private val REFERENCE_COMPACT_MIN_HEIGHT = 90.dp
private val REFERENCE_COMPACT_MIN_WIDTH = 200.dp

/** Lift under the hero's closing countdown, which sits larger and lower than list type. */
private val HERO_BOTTOM_CLEARANCE = 2.dp

/**
 * Start inset every line in the hero's text column is drawn after.
 *
 * The zone fractions are measured from the card's left edge — "the title may reach 53% of
 * the width" — so this has to come out of them. A column that is `width * fraction` wide
 * *and* begins this far in ends one whole inset past the edge it was fitted against, which
 * on a 406dp grant put the title and the elapsed line hard against the arch and clipped
 * their last glyph.
 */
private val HERO_TEXT_START_INSET = 14.dp

/**
 * How much larger the elapsed duration is set than the words after it.
 *
 * The row is drawn as three runs at two sizes, so the line's width is not the width of
 * the string at one size; [ReferencePrayerHero] measures the runs separately against this.
 */
private const val HERO_DURATION_RATIO = 1.35f

/** The hero column's fixed gaps — 4 + 5 + the rule + 2 — which the type cannot squeeze. */
private const val HERO_STACK_SPACERS = 12f

/**
 * Line height of the hero's serif display type, for measuring its stack against the card.
 *
 * Lower than [TEXT_LINE_HEIGHT_EM] because these lines draw with `includeFontPadding=false`
 * and there is nothing wrapping. Estimating high is not free: it makes the squeeze believe
 * the stack is taller than it is, so the type comes out smaller than it needed to be and
 * the slack reappears as a gap in the middle of the card.
 */
private const val HERO_LINE_HEIGHT_EM = 1.22f

/** A card from the combined prayer widget that can also be placed independently. */
enum class PrayerWidgetSection {
    NEXT_PRAYER,
    TODAYS_PRAYERS,
    DEVOTIONAL,
}

/**
 * Reads the launcher's current widget bounds without relying on Glance's cached LocalSize.
 *
 * Android's option keys describe the portrait footprint as min-width/max-height and the
 * landscape footprint as max-width/min-height. A zero dimension means the host did not
 * publish useful bounds, in which case the caller falls back to LocalSize.
 */
/**
 * One UI Home draws a widget scaled by its `hsResizeRatio` (0.83 on a Galaxy S25 Ultra)
 * but leaves sp text at full size, so every label renders ~20% larger than the layout
 * it sits in — labels overflow columns the measurement said they fit, and the header
 * type looks heavier than the lock-screen instance of the same widget. Text sizes are
 * multiplied by this ratio so what the launcher shows is what the layout was sized for.
 * Hosts that do not send the key (the lock screen, other launchers) get 1.
 */
private fun hostTextScale(context: Context, glanceId: GlanceId): Float {
    val appWidgetId = (glanceId as? AppWidgetId)?.appWidgetId ?: return 1f
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    val ratio = options.getFloat("hsResizeRatio", 1f)
    return if (ratio in 0.5f..1f) ratio else 1f
}

internal val LocalHostTextScale = staticCompositionLocalOf { 1f }

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
 * The five size-based subclasses render identical responsive layouts and exist so the
 * widget picker can offer useful starting footprints. The three section subclasses pin
 * one of the combined widget's cards, allowing the next prayer, today's timeline, or the
 * devotional reminder to be placed independently.
 *
 * The size a subclass drops at is declared in its appwidget-provider XML
 * (targetCellWidth/Height), not here — Glance always receives the space actually granted
 * by the launcher.
 */
abstract class BasePrayerTimesWidget protected constructor(
    private val standaloneSection: PrayerWidgetSection? = null,
) : GlanceAppWidget() {

    // Exact, not Responsive, and for the same reason the ported widgets use it: with
    // Responsive, Glance composes once per declared bucket and the host picks the
    // nearest, so a 125dp card and a 179dp card both render the identical SMALL
    // composition and nothing adapts in between. Exact composes for the size actually
    // granted, which makes LocalSize the real footprint and lets the layout scale
    // continuously with the launcher's grid. It also keeps one RemoteViews in the host's
    // memory rather than one per bucket.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Loaded outside provideContent so the data read is not tied to recomposition, and
        // reused across the rapid re-runs a resize causes (see loadPrayerWidgetStateCached).
        val state = loadPrayerWidgetStateCached(context)
        val themeSource = loadWidgetThemeSource(context)

        provideContent {
            StarceptionWidgetTheme(
                source = themeSource,
            ) {
                CompositionLocalProvider(
                    LocalHostTextScale provides hostTextScale(context, id),
                    LocalWidgetThemeSource provides themeSource,
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
                        // The reference header is a fixed strip — 32dp discs, 12sp name —
                        // whatever height the widget has; only the cards below it scale.
                        // Scaling it with the grant was tried and made it read as heavier
                        // than the design at every tall footprint.
                        val headerScale = 1f
                        val titledHeight =
                            (size.height - TITLE_BAR_HEIGHT - TITLED_WIDGET_BOTTOM_PADDING)
                                .coerceAtLeast(1.dp)
                        val bareHeight =
                            (size.height - (WIDGET_PADDING * 2)).coerceAtLeast(1.dp)
                        if (standaloneSection != null) {
                            BareSurface(
                                verticalAlignment = Alignment.Vertical.Top,
                                padding = WIDGET_PADDING,
                            ) {
                                StandalonePrayerSection(
                                    section = standaloneSection,
                                    state = state,
                                    contentSize = DpSize(innerWidth, bareHeight),
                                )
                            }
                        } else when {
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
                                    headerScale = headerScale,
                                    headerWidth = innerWidth,
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

/** Picker entry for the illustrated current/next-prayer card on its own. */
class PrayerNextWidget : BasePrayerTimesWidget(PrayerWidgetSection.NEXT_PRAYER)

/** Picker entry for today's five-prayer sun-path timeline on its own. */
class PrayerTimelineWidget : BasePrayerTimesWidget(PrayerWidgetSection.TODAYS_PRAYERS)

/** Picker entry for the source-backed dua and hadith card on its own. */
class PrayerDevotionalWidget : BasePrayerTimesWidget(PrayerWidgetSection.DEVOTIONAL)

// ---------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------

/**
 * Rounded widget background with no chrome, for sizes too small for a title bar.
 *
 * Compact layouts do not use Glance Scaffold, so this surface owns their single
 * widget-background marker and paints the plate colour on it.
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
            .background(LocalWidgetHostBackground.current)
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
    val appearance = LocalWidgetAppearance.current
    val transparentHeader = appearance.needsWallpaperContrast()
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
            .background(LocalWidgetHostBackground.current)
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
    headerScale: Float = 1f,
    headerWidth: Dp = REFERENCE_LAYOUT_MIN_WIDTH,
    content: @Composable ColumnScope.() -> Unit,
) {
    val disc = REFERENCE_HEADER_DISC * headerScale
    Scaffold(
        backgroundColor = LocalWidgetHostBackground.current,
        horizontalPadding = WIDGET_PADDING,
        // Scaffold pads the sides only. The final content is already an inset surface,
        // so use the compensated bottom inset instead of stacking another full 16dp.
        modifier = GlanceModifier
            .padding(bottom = TITLED_WIDGET_BOTTOM_PADDING)
            .clickable(actionStartActivity<MainActivity>()),
        titleBar = {
            val appearance = LocalWidgetAppearance.current
            // The reference header is built from opaque pills — pin disc, weather
            // capsule, refresh disc — so it does not need the plate behind it to be
            // thick. Only a removed plate (no background at all) sends it to the
            // white-on-wallpaper treatment; a thin plate keeps the reference look.
            val transparentHeader = if (detailedHeader) {
                !appearance.showBackground
            } else {
                appearance.needsWallpaperContrast()
            }
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
                //
                // The reference header instead lines its pin disc and refresh disc up flush
                // with the card edges below, so it takes the content inset on both sides.
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(
                        start = if (detailedHeader) WIDGET_PADDING else 12.dp,
                        end = 8.dp,
                        top = HEADER_TOUCH_TARGET_TOP_PADDING * headerScale,
                        bottom = HEADER_TOUCH_TARGET_BOTTOM_PADDING * headerScale,
                    ),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                if (detailedHeader) {
                    ReferenceHeader(state, headerScale, headerWidth)
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
                    // The reference header is a fixed palette — green pin, indigo ink,
                    // lavender capsule — like the illustrated cards beneath it. Dynamic
                    // Color recolours the plate, not this strip; only a removed plate
                    // changes it.
                    val dynamicHeader = false
                    val themePaintedHeader = transparentHeader
                    Box(
                        modifier = GlanceModifier
                            .size(disc)
                            .background(
                                if (themePaintedHeader) {
                                    GlanceTheme.colors.surfaceVariant
                                } else {
                                    ReferenceTopHeaderPill
                                },
                            )
                            .cornerRadius(disc / 2)
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
                            modifier = GlanceModifier.size(18.dp * headerScale),
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
    scale: Float = 1f,
    width: Dp = REFERENCE_LAYOUT_MIN_WIDTH,
) {
    val context = LocalContext.current
    val disc = REFERENCE_HEADER_DISC * scale
    // The capsule keeps the reference's 3:1 shape but widens less than it grows, or on a
    // tall grant it takes the date line's room. The date is then fitted to what is left.
    val capsuleHeight = REFERENCE_HEADER_CAPSULE_HEIGHT * scale
    val capsuleWidth = REFERENCE_HEADER_DISC * 3 * scale
    val summaryText = state.currentWeather?.summary ?: "Unavailable"
    val summarySize = (10.5f * scale).coerceAtMost(
        WidgetTypography.fittingSize(
            context = context,
            text = summaryText,
            maxWidthDp = (capsuleWidth - 21.dp * scale - 29.dp * scale).value,
        ),
    ).sp
    val textColumnWidth = (
        width - disc - 11.dp * scale - 8.dp - capsuleWidth - 7.dp * scale - disc
        ).coerceAtLeast(80.dp)
    val dateSize = (11f * scale).coerceAtMost(
        WidgetTypography.fittingSize(
            context = context,
            text = state.dateLabel,
            maxWidthDp = textColumnWidth.value - 4f,
        ),
    ).sp
    val appearance = LocalWidgetAppearance.current
    // Mirrors TitledSurface: the reference pills stand on their own with their own fixed
    // palette (Dynamic Color recolours the plate, not them), so only a removed plate
    // switches this header to the wallpaper treatment.
    val transparentHeader = !appearance.showBackground
    val transparentForeground = LocalTransparentWidgetForeground.current
    val dynamicHeader = false
    val themePaintedHeader = transparentHeader
    val accent = when {
        transparentHeader -> transparentForeground.primary
        dynamicHeader -> GlanceTheme.colors.primary
        else -> ReferenceHeaderPinInk
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
    // The location name and date sit directly on the plate (not inside a pill), so they must
    // follow the plate's day/night colour. The weather capsule and refresh disc keep their
    // own light backgrounds, so their ink stays fixed regardless of mode.
    val darkPlate = !transparentHeader && LocalWidgetDarkTheme.current
    val placeTitleColor = if (darkPlate) ReferenceTopHeaderInkOnDark else titleColor
    val placeSubtitleColor = if (darkPlate) ReferenceTopHeaderMutedOnDark else subtitleColor
    Box(
        modifier = GlanceModifier
            .size(disc)
            .background(
                if (themePaintedHeader) {
                    GlanceTheme.colors.primaryContainer
                } else {
                    ReferencePinSurface
                },
            )
            .cornerRadius(disc / 2),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_location_pin),
            contentDescription = null,
            colorFilter = ColorFilter.tint(accent),
            modifier = GlanceModifier.size(19.dp * scale),
        )
    }
    Spacer(modifier = GlanceModifier.width(11.dp * scale))
    // Sizes measured against the full reference at equal plate width: the name is a
    // heavy 12sp line, the date 8.5sp, and together they span most of the disc height.
    Column(modifier = GlanceModifier.defaultWeight()) {
        WidgetText(
            text = state.place,
            size = (15f * scale).sp,
            color = placeTitleColor,
            weight = WidgetFontWeight.Bold,
        )
        Spacer(modifier = GlanceModifier.height(2.dp * scale))
        WidgetText(
            text = state.dateLabel,
            size = dateSize,
            color = placeSubtitleColor,
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
    // Reference capsule: three times its height, icon two-thirds of the height, medium-weight
    // temperature over a small summary, both left-aligned beside the icon.
    Row(
        modifier = GlanceModifier
            .width(capsuleWidth)
            .height(capsuleHeight)
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
            .cornerRadius(capsuleHeight / 2)
            .padding(start = 11.dp * scale, end = 10.dp * scale),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (state.currentWeather?.icon != null) {
            StaticMeteoconBitmap(still = state.currentWeather.icon, size = 21.dp * scale)
        } else {
            Image(
                provider = ImageProvider(R.drawable.flaticon_weather_cloudy),
                contentDescription = "Weather unavailable",
                modifier = GlanceModifier.size(21.dp * scale),
            )
        }
        Spacer(modifier = GlanceModifier.width(8.dp * scale))
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetText(
                text = state.currentWeather?.temperature ?: "--°",
                size = (16f * scale).sp,
                color = titleColor,
                weight = if (transparentHeader) {
                    WidgetFontWeight.ShadowBold
                } else {
                    WidgetFontWeight.Regular
                },
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
            )
            WidgetText(
                text = summaryText,
                size = summarySize,
                color = titleColor,
                weight = if (transparentHeader) {
                    WidgetFontWeight.ShadowMedium
                } else {
                    WidgetFontWeight.Regular
                },
                align = WidgetTextAlign.Start,
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
            )
        }
    }
    Spacer(modifier = GlanceModifier.width(7.dp * scale))
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
    /** Tracking in ems, for spaced small-caps captions; TextView.setLetterSpacing is remotable. */
    letterSpacing: Float? = null,
) {
    val hostTextScale = LocalHostTextScale.current
    val context = LocalContext.current
    val remoteViews = RemoteViews(context.packageName, weight.layout).apply {
        setTextViewText(R.id.widget_text, text)
        letterSpacing?.let { setFloat(R.id.widget_text, "setLetterSpacing", it) }
        setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_SP, size.value * hostTextScale)
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

        contentSize.height >= REFERENCE_FLIP_MIN_HEIGHT -> {
            ReferenceFlippingPrayerContent(state = state, contentSize = contentSize)
        }

        else -> {
            ReferenceShortPrayerContent(state = state, contentSize = contentSize)
        }
    }
}

/** Renders one of the combined widget's three cards at the standalone widget's full size. */
@Composable
private fun StandalonePrayerSection(
    section: PrayerWidgetSection,
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    when (section) {
        PrayerWidgetSection.NEXT_PRAYER -> ReferencePrayerHero(
            state = state,
            width = contentSize.width,
            height = contentSize.height,
        )

        PrayerWidgetSection.TODAYS_PRAYERS -> ReferencePrayerTimeline(
            state = state,
            width = contentSize.width,
            height = contentSize.height,
        )

        PrayerWidgetSection.DEVOTIONAL -> ReferenceDevotionalPanel(
            state = state,
            width = contentSize.width,
            height = contentSize.height,
        )
    }
}

/**
 * Reduced footprints show the same three cards one at a time.
 *
 * The launcher's ViewFlipper (widget_section_flipper.xml) receives the hero, Today's
 * Prayers and Dua & Hadith as sibling pages and cycles them every few seconds, so
 * shrinking the widget hides nothing — it just paces it. The pages are built in
 * [buildSectionFlipper] before this composition starts; see there for why.
 */
@Composable
private fun ReferenceFlippingPrayerContent(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    val context = LocalContext.current
    val id = LocalGlanceId.current
    val themeSource = LocalWidgetThemeSource.current
    // Built here, keyed on the size, not once in provideGlance: a resize only recomposes,
    // so pages built up front stayed at the old size and were squashed into the new one.
    // Until the pages for this size exist the hero stands in, then they replace it.
    val flipper by produceState<RemoteViews?>(initialValue = null, contentSize, state) {
        value = buildSectionFlipper(context, id, state, themeSource, contentSize)
    }
    val pages = flipper
    if (pages != null) {
        AndroidRemoteViews(remoteViews = pages, modifier = GlanceModifier.fillMaxSize())
    } else {
        ReferenceShortPrayerContent(state = state, contentSize = contentSize)
    }
}

/** The theme source of this render, for compositions that spawn sub-compositions. */
private val LocalWidgetThemeSource = staticCompositionLocalOf<WidgetThemeSource> {
    error("Widget theme source was not provided")
}

/**
 * Composes each section, rasterises it here, and stacks the bitmaps in the flipper.
 *
 * Glance cannot host the three cards as pages itself: every Glance composition draws view
 * ids from one pool, and the launcher's findViewById descends into nested views, so the
 * ids of one card's tree collide with another's and the launcher rejects the widget
 * ("FrameLayout doesn't have method setImageResource") — whether the pages are Glance
 * children of a container or separately composed RemoteViews nested with addView.
 * Bitmaps carry no ids. Each page is composed with [compose], inflated in this process
 * with RemoteViews.apply, laid out at the page size and drawn; the launcher only flips
 * three images. Text is composed at scale 1 because it is our own TextViews drawing it,
 * not the launcher's; the launcher then scales the finished bitmap like everything else.
 *
 */
@OptIn(ExperimentalGlanceApi::class)
private suspend fun buildSectionFlipper(
    context: Context,
    id: GlanceId,
    state: PrayerWidgetState.Available,
    themeSource: WidgetThemeSource,
    contentSize: DpSize,
): RemoteViews? {
    val flipper = RemoteViews(context.packageName, R.layout.widget_section_flipper)
    // Composed concurrently: serially the three pages took ~1.5s, most of which the
    // launcher spent showing the previous rendering stretched to the new cells.
    // Each page composes under its own synthetic id: compose() registers a session per id,
    // and concurrent sessions for one id abort each other ("Another session for N has
    // started"). The pages carry no id-bound actions, so any unique id will do.
    val realId = (id as? AppWidgetId)?.appWidgetId ?: return null
    val shownPages = PINNED_SECTION_PAGE?.let(::listOf) ?: SectionPage.entries
    val pages = coroutineScope {
        shownPages.map { page ->
            async {
                SectionPageWidget(themeSource, state, contentSize, textScale = 1f, page)
                    .compose(
                        context = context,
                        id = AppWidgetId(-(realId * 10 + page.ordinal + 1)),
                        size = contentSize,
                    )
            }
        }.awaitAll()
    }
    val bitmaps = pages.map { views ->
        withContext(Dispatchers.Main) { rasterise(context, views, contentSize) } ?: return null
    }
    // Pinned, every page carries the same image: the carousel still runs but has nothing to
    // change to. Stopping it would mean ViewFlipper.stopFlipping across RemoteViews, which
    // is a method the launcher may or may not accept; this needs nothing of the host.
    SECTION_PAGE_VIEW_IDS.forEachIndexed { index, viewId ->
        flipper.setImageViewBitmap(viewId, bitmaps[index % bitmaps.size])
    }
    return flipper
}

private val SECTION_PAGE_VIEW_IDS = intArrayOf(
    R.id.section_page_0,
    R.id.section_page_1,
    R.id.section_page_2,
)

/**
 * Inflates [views] in this process and draws it at [size]. Rendered at the device density,
 * in RGB_565 with a transparent-safe fallback to ARGB when the page needs alpha (the
 * plate shows through around the hero), and at most ~1.1MP so three pages stay inside the
 * host's bitmap budget alongside the rest of the widget.
 */
internal fun rasterise(context: Context, views: RemoteViews, size: DpSize): Bitmap? =
    runCatching {
        val density = context.resources.displayMetrics.density
        val scale = minOf(1f, kotlin.math.sqrt(1_100_000f / (size.width.value * size.height.value * density * density)))
        val widthPx = (size.width.value * density * scale).toInt().coerceAtLeast(1)
        val heightPx = (size.height.value * density * scale).toInt().coerceAtLeast(1)
        val parent = FrameLayout(context)
        val view = views.apply(context, parent)
        view.measure(
            View.MeasureSpec.makeMeasureSpec((size.width.value * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((size.height.value * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        drawWithOutlineClips(view, canvas)
        bitmap
    }.getOrNull()

/**
 * Draws [view] and its subtree onto a software canvas, honouring clipToOutline.
 *
 * Glance rounds corners by giving views an outline and setting clipToOutline; the
 * framework only applies that clip on a hardware canvas inside an attached window, so a
 * plain View.draw() into a bitmap renders every disc and rounded card as a square. This
 * walks the tree itself and clips each view to its outline before drawing it.
 */
private fun drawWithOutlineClips(view: View, canvas: Canvas) {
    if (view.visibility != View.VISIBLE) return
    canvas.save()
    canvas.translate(view.left.toFloat(), view.top.toFloat())
    // What ViewGroup.drawChild does for clipChildren: a centre-cropped ImageView relies on
    // this to stay inside its column.
    canvas.clipRect(0, 0, view.width, view.height)
    if (view.alpha < 1f) {
        canvas.saveLayerAlpha(0f, 0f, view.width.toFloat(), view.height.toFloat(), (view.alpha * 255).toInt())
    }
    if (view.clipToOutline) {
        val outline = Outline()
        view.outlineProvider?.getOutline(view, outline)
        val rect = Rect()
        if (outline.getRect(rect)) {
            val path = Path().apply {
                addRoundRect(RectF(rect), outline.radius, outline.radius, Path.Direction.CW)
            }
            canvas.clipPath(path)
        }
    }
    if (view is ViewGroup) {
        view.background?.let { background ->
            background.setBounds(0, 0, view.width, view.height)
            background.draw(canvas)
        }
        for (index in 0 until view.childCount) {
            drawWithOutlineClips(view.getChildAt(index), canvas)
        }
    } else {
        view.draw(canvas)
    }
    canvas.restore()
}

private enum class SectionPage { HERO, TIMELINE, DEVOTIONAL }

/**
 * Pins the flipper to one section while that section is being worked on.
 *
 * Only that page is composed, and it is put on all three of the flipper's children, so the
 * card stops changing under you between screenshots. Must be null in anything shipped —
 * the widget is meant to rotate through all three.
 */
private val PINNED_SECTION_PAGE: SectionPage? = null

/** One flipper page: a single card composed at the full reduced content size. */
private class SectionPageWidget(
    private val themeSource: WidgetThemeSource,
    private val state: PrayerWidgetState.Available,
    private val contentSize: DpSize,
    private val textScale: Float,
    private val page: SectionPage,
) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            StarceptionWidgetTheme(source = themeSource) {
                CompositionLocalProvider(LocalHostTextScale provides textScale) {
                    when (page) {
                        // The hero takes the whole page, as the other two sections do. It
                        // used to be capped at HERO_MIN_ASPECT and centred, which left a
                        // band of bare plate above and below it and made the flipper
                        // visibly shrink whenever this page came round. It can fill now
                        // that the hero has a 3:2 plate to reach for as well as the 3:1
                        // one — filling on the 3:1 alone is what wrecked the artwork.
                        SectionPage.HERO -> ReferencePrayerHero(
                            state = state,
                            width = contentSize.width,
                            height = contentSize.height.coerceAtLeast(112.dp),
                        )
                        SectionPage.TIMELINE -> ReferencePrayerTimeline(
                            state = state,
                            width = contentSize.width,
                            height = contentSize.height,
                        )
                        SectionPage.DEVOTIONAL -> ReferenceDevotionalPanel(
                            state = state,
                            width = contentSize.width,
                            height = contentSize.height,
                        )
                    }
                }
            }
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
    // The three cards keep the reference's proportions — 132 : 142 : 115, the same
    // 34% / 36% / 30% split the design uses — at every height the launcher grants, so a
    // taller widget is the same composition scaled, not one card swollen with the surplus.
    // The only exception is the hero, whose 3:1 artwork cannot follow the width past
    // HERO_MIN_ASPECT; whatever it declines is shared by the other two in their own ratio.
    val available = contentSize.height - (sectionGap * 2f)
    val designTotal = REFERENCE_DESIGN_HERO_HEIGHT +
        REFERENCE_DESIGN_TIMELINE_HEIGHT +
        REFERENCE_DESIGN_DEVOTIONAL_HEIGHT
    val heroHeight = (available * (REFERENCE_DESIGN_HERO_HEIGHT / designTotal))
        .coerceAtMost(contentSize.width / HERO_MIN_ASPECT)
        .coerceAtLeast(128.dp)
    val belowHero = (available - heroHeight).coerceAtLeast(0.dp)
    val scheduleShare = REFERENCE_DESIGN_TIMELINE_HEIGHT /
        (REFERENCE_DESIGN_TIMELINE_HEIGHT + REFERENCE_DESIGN_DEVOTIONAL_HEIGHT)
    val scheduleHeight = (belowHero * scheduleShare).coerceAtLeast(138.dp)
    val devotionalHeight = (belowHero - scheduleHeight)
        .coerceAtLeast(REFERENCE_DESIGN_DEVOTIONAL_HEIGHT - 6.dp)

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
            width = contentSize.width,
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
    // Launchers grant this card anything from its designed 132dp up to roughly double.
    // Everything below is sized against the design height and grows with the grant, so a
    // taller card reads as a bigger card rather than the same card with more green in it.
    val heightScale = (height / REFERENCE_DESIGN_HERO_HEIGHT).coerceIn(1f, 1.6f)
    // Give the elapsed prayer window enough width for longer labels such as
    // "12h 45m since Maghrib" without shrinking or clipping. The cap follows the scale so
    // a larger type ramp has a proportionally wider column to sit in.
    val leftWidth = (width * 0.43f - HERO_TEXT_START_INSET)
        .coerceIn(128.dp, 174.dp * heightScale)
    // The arch curves away toward the top of the card, so the title and "since" line have
    // more green to sit on (about 53% of the width) than the prayer name does mid-card.
    // Fitting them to the name's column is what kept them small.
    val topWidth = (width * 0.53f - HERO_TEXT_START_INSET).coerceAtLeast(leftWidth)
    // The quote/photo region consumes over half the hero; keep the editorial type ramp
    // compact until the left text column can hold “14m since Dhuhr” without ellipsis.
    val compact = width < 380.dp
    val context = LocalContext.current
    val appearance = LocalWidgetAppearance.current
    // The artwork's arch ends about 84% of the way across; what remains, less the end
    // inset, is the strip the quote is centred in. The quote must fit *that*, not the
    // column it is drawn in: on a 363dp lock-screen grant the strip is ~46dp, and text
    // sized for the home-screen widget spills left over the arch. Fit the widest line to
    // the strip and drop the quote altogether once it would have to go below legibility.
    // Right-aligned against the card edge, so only the widest line ("remembrance.")
    // reaches toward the arch and the shorter lines stay clear of it.
    val quoteZoneWidth = (width * 0.185f - 8.dp).coerceAtLeast(0.dp)
    // The strip's width is set by the quote's longest line, so a tall hero uses its height
    // instead: broken into short lines the limit becomes "establish", not "remembrance.",
    // and the type can grow into the room below. Short heroes keep the reference's three.
    val quoteLines = if (height >= 170.dp) {
        listOf("And", "establish", "prayer", "for My", "remem-", "brance.")
    } else {
        listOf("And establish", "prayer for My", "remembrance.")
    }
    val quoteFitSize = quoteLines.minOf { line ->
        WidgetTypography.fittingSize(context, line, maxWidthDp = quoteZoneWidth.value - 6f)
    }
    val showQuote = quoteFitSize >= 7f
    val quoteSize = quoteFitSize.coerceAtMost(13f * heightScale).sp
    // The attribution is set two steps smaller, and must fit the strip in its own right.
    val quoteAttributionSize = (quoteFitSize - 2f)
        .coerceAtMost(
            WidgetTypography.fittingSize(context, "— Taha 20:14", maxWidthDp = quoteZoneWidth.value - 6f),
        )
        .coerceIn(7f, 10f * heightScale)
        .sp
    // The plate exists at two shapes and the card picks whichever it is closer to. One
    // bitmap cannot serve both: the stacked layout gives the hero a wide band near 3:1
    // while a flipper page is nearer 3:2, and FillBounds would stretch whichever is wrong
    // by about two to one. Inventing the difference at runtime was tried first and there
    // is no band that survives it — a thick one smears the clouds into streaks, a two-row
    // sliver turns them into hard vertical stripes.
    val wideArtwork = width.value / height.value >= HERO_ARTWORK_SWITCH_ASPECT
    val heroArtwork = alphaAdjustedImageProvider(
        context = context,
        drawableRes = if (wideArtwork) {
            R.drawable.prayer_widget_reference_hero_v4
        } else {
            R.drawable.prayer_widget_reference_hero_v5
        },
        alpha = appearance.effectiveArtworkAlpha(HERO_ARTWORK_MIN_ALPHA),
        targetAspect = width.value / height.value,
        // Whatever height is still missing comes out of the margins either side of the
        // mosque. On the wide plate the arch is cut flat at the bottom edge and its curve
        // fills the top, so everything down to ~90% is kept and only the foliage strip
        // grows; the tall plate carries real sky above the minarets, which can give a
        // little as well.
        preservedBand = if (wideArtwork) 0f..0.90f else 0.20f..0.95f,
    )
    val phaseTitle = state.insight?.title ?: "Prayer now"
    val phaseTitleSizeRaw = WidgetTypography
        .fittingSize(
            context = context,
            text = phaseTitle,
            maxWidthDp = topWidth.value - 4f,
            bold = true,
        )
        // The sans title introduces the serif "since" line beneath it; both are sized to
        // fill the room above the prayer name. Fit is the ceiling, never the floor:
        // "Make Time for Maghrib" must stay whole.
        .coerceAtMost((if (compact) 15f else 19f) * heightScale)
        .coerceAtLeast(9f)
    // Reference type ramp, measured against the hero's height, with two deliberate changes:
    // the reference sets "2h 9m since Dhuhr" as a faint caption, but the elapsed time is
    // information people glance at, so it stays a readable line — bold duration, about a
    // third of the prayer name; and the reference's closing italic reflection is dropped,
    // its height given to the name and countdown instead.
    // The elapsed time is what people glance for, so the duration is set a third larger
    // than the words after it. Measuring the whole string at the smaller size against a
    // flat 88% of the column was the fit this used to take, and it under-measures exactly
    // when the duration is a large share of the line — "6h 37m since Fajr" needs more than
    // the 12% that left, so the row overran the column and the launcher clipped its last
    // glyph mid-stroke. Width per sp is linear, so summing the runs at the ratio each is
    // drawn at gives the real width of the row and the largest size that fits it.
    val elapsedWidthPerSp = (
        elapsedDuration?.let { duration ->
            HERO_DURATION_RATIO * WidgetTypography.widthPerSp(
                context = context,
                text = duration,
                bold = true,
                serif = true,
            ) + WidgetTypography.widthPerSp(
                context = context,
                text = " since ",
                bold = false,
                serif = true,
            )
        } ?: 0f
        ) + WidgetTypography.widthPerSp(
        context = context,
        text = elapsedPrayer,
        bold = false,
        serif = true,
    )
    val elapsedFittingSize = if (elapsedWidthPerSp > 0f) {
        (topWidth.value - 4f) * TEXT_FIT_SLACK / elapsedWidthPerSp
    } else {
        Float.MAX_VALUE
    }
    val captionSizeRaw = ((if (compact) 18f else 24f) * heightScale)
        .coerceAtMost(elapsedFittingSize)
        .sp
    val durationSizeRaw = (captionSizeRaw.value * HERO_DURATION_RATIO).sp
    val nameSizeRaw = ((if (compact) 36f else 46f) * heightScale).coerceAtMost(
        WidgetTypography.fittingSize(
            context = context,
            text = state.nextPrayer.name,
            maxWidthDp = leftWidth.value - 4f,
            bold = true,
            serif = true,
        ),
    ).sp
    // The closing line is two runs at two sizes, like the elapsed one above it, so it is
    // fitted the same way. It used to take the ramp straight from the height, which held
    // only while the card was capped short: on a full page heightScale sits at its 1.6
    // ceiling and "in 3h 22m" ran past the column and came back ellipsized as "in 3h ...".
    val countdownText = state.countdown.removePrefix("in ")
    val countdownRatio = if (compact) 21f / 16f else 27f / 20f
    val countdownWidthPerSp = WidgetTypography.widthPerSp(
        context = context,
        text = "in ",
        bold = false,
        serif = true,
    ) + countdownRatio * WidgetTypography.widthPerSp(
        context = context,
        text = countdownText,
        bold = true,
        serif = true,
    )
    val countdownWordSizeRaw = ((if (compact) 16f else 20f) * heightScale)
        .coerceAtMost(
            if (countdownWidthPerSp > 0f) {
                (leftWidth.value - 4f) * TEXT_FIT_SLACK / countdownWidthPerSp
            } else {
                Float.MAX_VALUE
            },
        )
        .sp
    val countdownSizeRaw = (countdownWordSizeRaw.value * countdownRatio).sp

    // Every line above is fitted to the width of its column; nothing ever checked the
    // card's height. The stacked layout hands the hero about 127dp, where these four ask
    // for roughly 142 — so the closing countdown was clipped straight off the bottom.
    // Measure the stack and squeeze it as one, which keeps the type's proportions to each
    // other and only gives up size when the card genuinely cannot hold them.
    val heroStackHeight = (
        phaseTitleSizeRaw +
            maxOf(durationSizeRaw.value, captionSizeRaw.value) +
            nameSizeRaw.value +
            countdownSizeRaw.value
        ) * HERO_LINE_HEIGHT_EM + HERO_STACK_SPACERS
    val heroSqueeze = ((height - 24.dp).value / heroStackHeight).coerceIn(0.55f, 1f)
    val phaseTitleSize = phaseTitleSizeRaw * heroSqueeze
    val captionSize = (captionSizeRaw.value * heroSqueeze).sp
    val durationSize = (durationSizeRaw.value * heroSqueeze).sp
    val nameSize = (nameSizeRaw.value * heroSqueeze).sp
    val countdownWordSize = (countdownWordSizeRaw.value * heroSqueeze).sp
    val countdownSize = (countdownSizeRaw.value * heroSqueeze).sp
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(
                imageProvider = heroArtwork,
                contentScale = ContentScale.FillBounds,
            )
            .cornerRadius(24.dp),
        // The foliage is the only child given anything but the whole box, and it wants the
        // bottom of it; the Row below fills, so the alignment does not reach it.
        contentAlignment = Alignment.BottomStart,
    ) {
        // Drawn at a fixed 2.5:1 and scaled fitXY, so filling the box stretched the leaves
        // by however much the card departed from that — about 1.7x once the hero took a
        // whole flipper page. Give it its own aspect against the card's width instead and
        // let it sit on the bottom edge, which is where it grows from.
        AnimatedFoliage(
            phase = state.dayPhase,
            placement = WidgetFoliagePlacement.HERO_RIGHT,
            modifier = GlanceModifier
                .fillMaxWidth()
                .height((width / FOLIAGE_ASPECT).coerceAtMost(height)),
        )
        // One left inset for every line — caption, rule, name and countdown share an edge
        // in the reference — with the same clearance top and bottom.
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Vertical.Top,
        ) {
            Column(
                modifier = GlanceModifier
                    .width(topWidth)
                    .height(height - 24.dp),
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
                        .wrapContentHeight(),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    verticalAlignment = Alignment.Vertical.Bottom,
                ) {
                    elapsedDuration?.let { duration ->
                        WidgetText(
                            text = duration,
                            size = durationSize,
                            color = ReferenceHeroText,
                            weight = WidgetFontWeight.SerifBold,
                            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                        )
                        WidgetText(
                            text = " since ",
                            size = captionSize,
                            color = ReferenceHeroText,
                            weight = WidgetFontWeight.SerifRegular,
                            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                        )
                    }
                    WidgetText(
                        text = elapsedPrayer,
                        size = captionSize,
                        color = ReferenceHeroText,
                        weight = WidgetFontWeight.SerifRegular,
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                }
                Spacer(modifier = GlanceModifier.height(5.dp))
                // The app's own fading divider, in the hero ink, spanning the caption.
                FadingHorizontalSeparator(
                    color = ReferenceHeroInk,
                    modifier = GlanceModifier.width(topWidth - 24.dp),
                )
                // The caption group hugs the top and the name/countdown block sits on the
                // bottom, as in the reference; whatever height is left opens between them.
                Spacer(modifier = GlanceModifier.defaultWeight())
                WidgetText(
                    text = state.nextPrayer.name,
                    size = nameSize,
                    color = ReferenceHeroText,
                    weight = WidgetFontWeight.SerifBold,
                    modifier = GlanceModifier
                        .width(leftWidth)
                        .wrapContentHeight(),
                )
                Row(
                    modifier = GlanceModifier.width(leftWidth),
                    verticalAlignment = Alignment.Vertical.Bottom,
                ) {
                    WidgetText(
                        text = "in ",
                        size = countdownWordSize,
                        color = LocalWidgetHeroAccent.current,
                        weight = WidgetFontWeight.SerifRegular,
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                    WidgetText(
                        text = countdownText,
                        size = countdownSize,
                        color = LocalWidgetHeroAccent.current,
                        weight = WidgetFontWeight.SerifBold,
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                }
                Spacer(modifier = GlanceModifier.height(2.dp))
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            // The quote lives in the pale strip between the arch and the card edge. The
            // arch bulges rightward near the top, so the block starts a little lower where
            // the strip is widest, and is centred in it: the fixed-width box is the strip,
            // the wrapped column inside it is the text, so the gap to the arch and the gap
            // to the edge stay equal whatever the font measures.
            if (showQuote) {
                Box(
                    modifier = GlanceModifier
                        .width(quoteZoneWidth)
                        .fillMaxHeight()
                        .padding(top = 6.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Column(
                        modifier = GlanceModifier.wrapContentWidth(),
                        horizontalAlignment = Alignment.Horizontal.End,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.widget_quote_mark),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(ReferenceQuoteMark),
                            modifier = GlanceModifier.size(11.dp),
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        WidgetText(
                            text = quoteLines.joinToString("\n"),
                            size = quoteSize,
                            color = ReferenceQuote,
                            weight = WidgetFontWeight.Medium,
                            maxLines = quoteLines.size,
                            align = WidgetTextAlign.End,
                            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        WidgetText(
                            text = "— Taha 20:14",
                            size = quoteAttributionSize,
                            color = ReferenceQuote,
                            weight = WidgetFontWeight.Regular,
                            align = WidgetTextAlign.End,
                            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                        )
                    }
                }
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
    val context = LocalContext.current
    val appearance = LocalWidgetAppearance.current
    val heroArtwork = alphaAdjustedImageProvider(
        context = context,
        drawableRes = R.drawable.prayer_widget_reference_hero_v4,
        alpha = appearance.effectiveArtworkAlpha(HERO_ARTWORK_MIN_ALPHA),
    )
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
                imageProvider = heroArtwork,
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

/**
 * Today's Prayers as a living sky — see [WidgetSkyArtwork].
 *
 * The whole card is the rendered painting (sky, skyline, sun path, prayers, title and
 * labels); only the card's clip and click target are Glance.
 */
@Composable
private fun ReferencePrayerTimeline(
    state: PrayerWidgetState.Available,
    width: Dp,
    height: Dp,
) {
    val context = LocalContext.current
    val sky = remember(state.sky, state.prayers, width, height) {
        WidgetSkyArtwork.render(
            context = context,
            input = WidgetSkyArtwork.Input(
                sky = state.sky,
                prayers = state.prayers,
                daylightLabel = state.daylightLabel,
                nightLabel = state.nightLabel,
                cornerRadiusDp = 22f,
            ),
            widthDp = width.value,
            heightDp = height.value,
        )
    }
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(
                imageProvider = ImageProvider(sky),
                contentScale = ContentScale.FillBounds,
            )
            .cornerRadius(22.dp)
            .semantics { contentDescription = "Today's prayers on the sun's path" },
    ) {}
}

/** A source-backed dua or hadith panel, linked to its full in-app reading. */
@Composable
private fun ReferenceDevotionalPanel(
    state: PrayerWidgetState.Available,
    width: Dp,
    height: Dp,
) {
    val context = LocalContext.current
    val appearance = LocalWidgetAppearance.current
    val panelArtwork = alphaAdjustedImageProvider(
        context = context,
        drawableRes = R.drawable.prayer_widget_devotional_panel,
        alpha = appearance.effectiveArtworkAlpha(PANEL_ARTWORK_MIN_ALPHA),
    )
    val reminder = state.reminder
    val expanded = height >= 140.dp
    // The card's type follows its height so a taller grant reads as a larger card, and
    // the translation is allowed the lines that height affords instead of ellipsizing
    // with empty space below it.
    // The card is given the whole flipper page now, which is better than twice the height
    // the reference drew it at, so the ramp has to reach that far — clamped at 1.5 the type
    // stopped growing less than halfway up and the card filled with blank space instead.
    val heightScale = (height / REFERENCE_DESIGN_DEVOTIONAL_HEIGHT)
        .coerceIn(1f, DEVOTIONAL_MAX_TYPE_SCALE)
    val bodyLines = when {
        height >= 220.dp -> 4
        height >= 170.dp -> 3
        else -> 2
    }
    val artworkWidth = (height * 0.68f).coerceIn(84.dp, 96.dp * heightScale.coerceAtMost(1.15f))
    // Every line is sized to *fit* the text column at its line allowance — larger type
    // than fits only trades empty space for an ellipsis. The Arabic prefers a single line,
    // as in the reference, and takes a second only when one line would drop below 12sp.
    val textColumnWidth = (width - artworkWidth - 12.dp - 12.dp - 30.dp).coerceAtLeast(60.dp)
    // Spaced small caps: the tracking adds 0.16em per character on top of the glyphs, which
    // no plain width measurement sees, so it is added back here. Without it the column grew
    // with the ramp and "A BRIGHTER" came back as "A BRIGHTE...".
    val dhikrPerSp = DHIKR_LINES.maxOf { line ->
        WidgetTypography.widthPerSp(context, line, bold = false, serif = true) +
            DHIKR_TRACKING_EM * line.length
    }
    val dhikrSize = (7.5f * heightScale)
        .coerceAtMost(
            if (dhikrPerSp > 0f) {
                (artworkWidth.value - 22f) * TEXT_FIT_SLACK / dhikrPerSp
            } else {
                Float.MAX_VALUE
            },
        )
        .sp
    val prayerContext = "After ${state.currentPrayerName()}"
    // The header is a single row of fixed furniture — rule, gaps and the two chips' own
    // padding — plus three runs of text, and nothing was ever measured against the width it
    // had. Once the type grew the row simply ran past the card and the launcher cut the
    // last chip to "See mor". Size the title to what is left after the furniture and derive
    // both chips from it, so the row keeps the reference's proportions and always fits.
    val headerWidth = (width - artworkWidth - 24.dp).coerceAtLeast(60.dp)
    val headerTextPerSp = WidgetTypography.widthPerSp(context, "Dua & Hadith", bold = true) +
        DEVOTIONAL_CHIP_TYPE_RATIO *
        WidgetTypography.widthPerSp(context, prayerContext, bold = false, medium = true) +
        DEVOTIONAL_ACTION_TYPE_RATIO *
        WidgetTypography.widthPerSp(context, "See more  ›", bold = false, medium = true)
    val headerFittingSize = if (headerTextPerSp > 0f) {
        (headerWidth.value - DEVOTIONAL_HEADER_FURNITURE) * TEXT_FIT_SLACK / headerTextPerSp
    } else {
        Float.MAX_VALUE
    }
    val captionSize = ((if (expanded) 13.5f else 13f) * heightScale)
        .coerceAtMost(headerFittingSize)
        .sp
    val chipSize = (captionSize.value * DEVOTIONAL_CHIP_TYPE_RATIO).sp
    val actionSize = (captionSize.value * DEVOTIONAL_ACTION_TYPE_RATIO).sp
    // The chips' padding and corner were fixed dp while the type they wrap more than
    // doubled, so they stopped reading as the reference's stadium pills and became chunky
    // rounded boxes. Derive all three from the type: the corner is half the finished
    // height, which is a stadium at any size, and the flanks stay wider than the caps.
    val chipVerticalPadding = (chipSize.value * 0.42f).dp
    val chipHorizontalPadding = (chipSize.value * 0.95f).dp
    val chipCornerRadius =
        ((chipSize.value * TEXT_LINE_HEIGHT_EM) / 2f + chipVerticalPadding.value).dp
    // The attribution shares its row with the topic icon and the bookmark disc's clearance.
    val sourceText = listOfNotNull(reminder.sourceName, reminder.sourceDetail)
        .joinToString(" · ")
    val sourceIconSize = (14f * heightScale).dp
    val sourcePerSp = WidgetTypography.widthPerSp(context, sourceText, bold = false, medium = true)
    val sourceSize = ((if (expanded) 10f else 9.5f) * heightScale)
        .coerceAtMost(
            if (sourcePerSp > 0f) {
                (headerWidth.value - sourceIconSize.value - 46f) * TEXT_FIT_SLACK / sourcePerSp
            } else {
                Float.MAX_VALUE
            },
        )
        .sp
    // The same colourful topic icons the Interests page uses for these two subjects.
    val reminderIcon = when (reminder.caption) {
        "Hadith" -> topicIconResFor("hadith")
        else -> topicIconResFor("dua")
    } ?: R.drawable.ic_prayer
    val bodySize = ((if (expanded) 10f else 9.5f) * heightScale).coerceAtMost(
        WidgetTypography.fittingSize(
            context = context,
            text = "“${reminder.text}”",
            maxWidthDp = textColumnWidth.value * 0.96f,
            lines = bodyLines,
            medium = true,
        ),
    ).sp
    // The Arabic gets whatever height the other three rows leave, so it can be sized to
    // the room it actually has rather than to a fixed ceiling. Their heights are known
    // here because every one of them has already been fitted above.
    val arabicRoom = (
        height -
            (captionSize.value * TEXT_LINE_HEIGHT_EM + 10f).dp -
            (bodySize.value * TEXT_LINE_HEIGHT_EM * bodyLines).dp -
            maxOf(sourceIconSize, (sourceSize.value * TEXT_LINE_HEIGHT_EM).dp) -
            DEVOTIONAL_COLUMN_INSETS
        ).coerceAtLeast(18.dp)
    val arabicLines = if (height >= 220.dp) 3 else 2
    // Deliberately generous: the room, handed to render as maxHeightDp, is what actually
    // binds. Capping the size here as well meant estimating these faces' line spacing, and
    // the estimate came in low — the block was sized for three lines, drew two, and left a
    // third of the room it had been given empty.
    val arabicMax = ARABIC_RAMP_SP * heightScale
    // The single line the reference sets is preferred only while it can stay near the size
    // the card affords. Held to a flat 12.5sp floor it always won — a long dua fits one
    // line at about 13sp, so the Arabic stayed that size on a card twice the height while
    // everything around it grew. Measured against the room instead, it gives way to two or
    // three lines at a readable size once one line would be small for the space.
    val arabicSingleLineFloor = maxOf(12.5f, arabicMax * ARABIC_SINGLE_LINE_SHARE)
    // Drawn in-process in the reader's chosen face — see WidgetArabicText — so it always
    // fits and the font setting is honoured.
    val arabicRendered = reminder.arabic?.let { arabic ->
        WidgetArabicText.render(
            context = context,
            text = arabic,
            widthDp = textColumnWidth.value,
            maxSizeSp = arabicMax,
            minSizeSp = 11f,
            maxLines = arabicLines,
            singleLineFloorSp = arabicSingleLineFloor,
            color = ReferenceForestArgb,
            maxHeightDp = arabicRoom.value,
        )
    }
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(
                imageProvider = panelArtwork,
                contentScale = ContentScale.FillBounds,
            )
            .cornerRadius(22.dp)
            .clickable(reminder.openAction(context)),
        // As in the hero: the leaves are the one child that is not given the whole box.
        contentAlignment = Alignment.BottomStart,
    ) {
        AnimatedFoliage(
            phase = state.dayPhase,
            placement = WidgetFoliagePlacement.DEVOTIONAL_RIGHT,
            modifier = GlanceModifier
                .fillMaxWidth()
                .height((width / FOLIAGE_ASPECT).coerceAtMost(height)),
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
                // Flush with the card: the card's own clip rounds the left corners and the
                // right edge stays square, as in the reference.
                Image(
                    provider = ImageProvider(R.drawable.prayer_widget_devotional_lantern_v2),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize(),
                )
                // The reference sets this as spaced serif small caps, one word a line.
                Column(modifier = GlanceModifier.padding(start = 10.dp, top = 12.dp)) {
                    WidgetText(
                        text = DHIKR_LINES.joinToString("\n"),
                        size = dhikrSize,
                        color = ReferenceForestOn,
                        weight = WidgetFontWeight.SerifRegular,
                        maxLines = 4,
                        letterSpacing = DHIKR_TRACKING_EM,
                        modifier = GlanceModifier
                            .width(artworkWidth - 12.dp)
                            .wrapContentHeight(),
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Box(
                        modifier = GlanceModifier
                            .width(26.dp)
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
                    .padding(top = 8.dp, bottom = 8.dp),
            ) {
                // Title row, as the reference: green bar, "Dua & Hadith", the prayer-window
                // chip in the header's lavender, and "See more" at the far end.
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
                    Spacer(modifier = GlanceModifier.width(7.dp))
                    WidgetText(
                        text = "Dua & Hadith",
                        size = captionSize,
                        color = ReferenceInk,
                        weight = WidgetFontWeight.Bold,
                        modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .wrapContentWidth()
                            .background(ReferenceHeaderPill)
                            .cornerRadius(chipCornerRadius)
                            .padding(
                                horizontal = chipHorizontalPadding,
                                vertical = chipVerticalPadding,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        WidgetText(
                            text = prayerContext,
                            size = chipSize,
                            color = ReferenceTopHeaderInk,
                            weight = WidgetFontWeight.Medium,
                            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                        )
                    }
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Box(
                        modifier = GlanceModifier
                            .wrapContentWidth()
                            .background(ReferenceHeaderPill)
                            .cornerRadius(chipCornerRadius)
                            .padding(
                                horizontal = chipHorizontalPadding,
                                vertical = chipVerticalPadding,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        WidgetText(
                            text = "See more  ›",
                            size = actionSize,
                            color = ReferenceTopHeaderInk,
                            weight = WidgetFontWeight.Medium,
                            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                arabicRendered?.let { rendered ->
                    Image(
                        provider = ImageProvider(rendered.bitmap),
                        contentDescription = reminder.arabic,
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier
                            .width(rendered.widthDp.dp)
                            .height(rendered.heightDp.dp),
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                }
                WidgetText(
                    text = "“${reminder.text}”",
                    size = bodySize,
                    color = ReferenceTopHeaderInk,
                    weight = WidgetFontWeight.Medium,
                    maxLines = bodyLines,
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
                        contentDescription = reminder.caption,
                        modifier = GlanceModifier.size(sourceIconSize),
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    WidgetText(
                        text = sourceText,
                        size = sourceSize,
                        color = ReferenceTopHeaderInk,
                        weight = WidgetFontWeight.Medium,
                        modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
                    )
                    Spacer(modifier = GlanceModifier.width(40.dp))
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
                    .background(ReferenceHeaderPill)
                    .cornerRadius(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_bookmark),
                    contentDescription = "Save devotional",
                    colorFilter = ColorFilter.tint(ReferenceTopHeaderInk),
                    modifier = GlanceModifier.size(16.dp),
                )
            }
        }
    }
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
    R.id.foliage_frame_8,
    R.id.foliage_frame_9,
    R.id.foliage_frame_10,
    R.id.foliage_frame_11,
)
