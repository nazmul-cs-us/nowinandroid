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
import android.util.TypedValue
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
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
import androidx.glance.action.clickable
import android.widget.RemoteViews
import androidx.glance.appwidget.AndroidRemoteViews
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
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
import com.starception.submission.MainActivity
import com.starception.submission.R

/** Matches the ported layouts' own widgetPadding so the two sit consistently. */
private val WIDGET_PADDING = 16.dp

// Glance's TitleBar lays out a 48dp icon box inside 4dp of vertical padding and sets no
// height of its own, so it occupies 56dp whatever the title says. The hero below sizes
// its type against the height it is given, so this has to come off that budget: without
// it a titled card computed its type from the full card height, overflowed the title
// bar's share, and clipped its last line.
private val TITLE_BAR_HEIGHT = 56.dp

/** Floor for the hero's internal gaps, before any leftover height is shared out. */
private val GROUP_GAP_MIN = 10.dp

/** The single-line layout's own inset — tighter than [WIDGET_PADDING], which it cannot afford. */
private val TINY_PADDING = 10.dp


/** [ExpressiveProgressBar]'s drawn height, which the height budget has to account for. */
private const val PROGRESS_BAR_HEIGHT = 6f

/** The meteocon plus its gap, as the single-line layout has to budget for it. */
private const val ICON_ROOM = 28f

// The least *content* height — card height already less the surrounding surface's insets
// — that the hero is worth drawing in: its six lines at the smallest type it accepts
// (18sp), plus the progress bar and the two minimum gaps. Below this the single-line
// layout is used instead, because the hero would only clip.
private val HERO_MIN_HEIGHT = 100.dp

/**
 * Home-screen prayer times widget.
 *
 * Glance renders to RemoteViews, so none of the app's screen composables can be reused
 * here — every layout below is written against the Glance layout primitives, which have
 * no `Modifier.weight` on the cross axis, no intrinsic measurement, and no text
 * auto-sizing. With no auto-sizing to lean on, the layout derives its own type scale
 * from the granted footprint (see [PrayerHeroContent]); the 2x1 is the one size that
 * cannot carry that layout at all, so it keeps a hand-written single line.
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

        provideContent {
            GlanceTheme(colors = PrayerWidgetColors) {
                when (state) {
                    PrayerWidgetState.Unavailable -> BareSurface { UnavailableContent() }
                    is PrayerWidgetState.Available -> {
                        val size = LocalSize.current
                        // Each surface insets the card by a different amount, so what the
                        // hero actually gets is worked out here — the hero cannot see
                        // which surface wrapped it. Deciding the layout from the same two
                        // figures that are then handed to it is what keeps the choice and
                        // the sizing from disagreeing.
                        val innerWidth = size.width - (WIDGET_PADDING * 2)
                        // The Scaffold pads the sides and the bottom; the title bar
                        // stands in for the top.
                        val titledHeight = size.height - TITLE_BAR_HEIGHT - WIDGET_PADDING
                        val bareHeight = size.height - (WIDGET_PADDING * 2)
                        when {
                            // Any single-row card, whatever its width. The hero stacks six
                            // lines and one row cannot carry them at a readable size, so a
                            // 4x1 gets the same single line a 2x1 does rather than a
                            // squeezed copy of the hero.
                            bareHeight < HERO_MIN_HEIGHT ->
                                BareSurface(padding = TINY_PADDING) {
                                    TinyContent(state, size.width - (TINY_PADDING * 2))
                                }

                            // Everything taller renders the same hero. Only the surface
                            // around it differs: the title bar has to earn its 56dp, so it
                            // needs both the width to show a title and enough height left
                            // afterwards for the hero to still fit. Gating on width alone
                            // handed a 4x1 a title bar over a zero-height content box.
                            size.width >= 230.dp && titledHeight >= HERO_MIN_HEIGHT ->
                                TitledSurface(state) {
                                    PrayerHeroContent(
                                        state = state,
                                        contentSize = DpSize(innerWidth, titledHeight),
                                    )
                                }

                            else -> BareSurface(Alignment.Vertical.Top) {
                                PrayerHeroContent(
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
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        backgroundColor = GlanceTheme.colors.widgetBackground,
        horizontalPadding = WIDGET_PADDING,
        // Scaffold pads the sides only; the ported layouts add the bottom themselves, so
        // without this the last line of content sits against the card's edge. No top
        // padding — the title bar already stands off the top.
        modifier = GlanceModifier
            .padding(bottom = WIDGET_PADDING)
            .clickable(actionStartActivity<MainActivity>()),
        titleBar = {
            // Glance's own TitleBar is not used here, and only for one reason: its title
            // is a Glance Text, which cannot carry the bundled Ubuntu Sans (see
            // widget_text_regular.xml). Leaving it in place put the place name in the
            // system font directly above four lines in the app's own face, which read as
            // a mistake rather than a hierarchy. The arrangement below is the one it
            // draws — start icon, title, trailing action — rebuilt so the title is a
            // WidgetText.
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                // A pin, because the title beside it is the place name. The prayer
                // monochrome icon that was here draws its paths in a ~90-unit space
                // inside a 0.023 scale group, so it resolved to about two units of a
                // 24dp viewport — present, but too small to see.
                Image(
                    provider = ImageProvider(R.drawable.ic_location_pin),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier.size(22.dp),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                WidgetText(
                    text = state.place,
                    size = 15.sp,
                    color = GlanceTheme.colors.onSurface,
                    weight = WidgetFontWeight.Medium,
                    modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
                )
                // Same button the ported layouts put here — transparent background,
                // secondary tint — so the prayer card's header and the sample cards'
                // headers stay indistinguishable.
                CircleIconButton(
                    imageProvider = ImageProvider(R.drawable.sample_refresh_icon),
                    contentDescription = "Refresh prayer times",
                    contentColor = GlanceTheme.colors.secondary,
                    backgroundColor = null,
                    onClick = actionRunCallback<RefreshPrayerWidgetAction>(),
                )
            }
        },
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            content()
        }
    }
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
private fun WidgetText(
    text: String,
    size: TextUnit,
    color: ColorProvider,
    weight: WidgetFontWeight,
    maxLines: Int = 1,
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
        setTextColor(R.id.widget_text, color.getColor(context).toArgb())
        setInt(R.id.widget_text, "setMaxLines", maxLines)
    }
    AndroidRemoteViews(remoteViews = remoteViews, modifier = modifier)
}

/** The three weights the widget draws in, each backed by its own one-TextView layout. */
private enum class WidgetFontWeight(@LayoutRes val layout: Int) {
    Regular(R.layout.widget_text_regular),
    Medium(R.layout.widget_text_medium),
    Bold(R.layout.widget_text_bold),
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
private fun TinyContent(state: PrayerWidgetState.Available, contentWidth: Dp) {
    // One line, and it answers the question the widget is named after. The previous
    // version showed the current prayer's phase headline over its elapsed time and never
    // named the next prayer at all — two stacked lines needing ~37dp inside a 2x1 that
    // has roughly 28dp of usable height, so it also clipped.
    //
    // It now reads "Asr in 3h 58m", the same phrase the hero ends on, rather than adding
    // the absolute clock time. That is both consistent and what makes it fit: a Glance Row
    // does not shrink its children, so anything past the edge is simply cut off — at 150dp
    // wide the old row wanted ~165dp of content in ~130dp and lost the end of the
    // countdown. Dropping the clock time takes ~60dp out of the line.
    val context = LocalContext.current
    val width = contentWidth.value
    val countdown = state.countdown.removePrefix("in ").trim()
    val line = "${state.nextPrayer.name} in $countdown"

    // The meteocon is the first thing to go, not the last: at this size it is a ~7dp
    // smudge whose absence costs nothing, while the 28dp it occupies is a quarter of the
    // line on the narrowest card. It is kept only when the text does not need that room.
    val fitsWithIcon = WidgetTypography.fittingSize(context, line, width - ICON_ROOM) >= 13f
    val textRoom = width - (if (fitsWithIcon) ICON_ROOM else 0f)

    // Measured against the real sentence, bold, since the widest of the three runs sets
    // the line. Capped at the ramp's lower rungs — this is a supporting size by role, and
    // letting it grow to fill a wide 4x1 would out-shout the hero on the card beside it.
    val textSize = WidgetTypography
        .fittingSize(context, line, textRoom, bold = true)
        .coerceIn(11f, 18f)
        .sp

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (fitsWithIcon) {
            AnimatedMeteocon(prayer = state.nextPrayer, size = 20.dp)
            Spacer(modifier = GlanceModifier.width(8.dp))
        }
        // Weighted the same three ways the hero weights its own next-prayer line, so the
        // two sizes read as the same sentence rather than two different summaries.
        Text(
            text = state.nextPrayer.name,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Text(
            text = " in ",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = textSize,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Text(
            text = countdown,
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
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
        val fitsAcross =
            WidgetTypography.fittingSize(context, elapsedText, width) >= candidate.elapsed
        fitsDown && fitsAcross
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
        maxLines = 2,
    )

    WidgetText(
        text = elapsedText,
        size = elapsedSize,
        color = GlanceTheme.colors.onSurfaceVariant,
        weight = WidgetFontWeight.Regular,
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
        AnimatedMeteocon(prayer = state.nextPrayer, size = 20.dp)
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
    val context = LocalContext.current

    // The artwork is now the Mono set — one black silhouette — so it has to be tinted to
    // be anything but a black smudge on a light card. Tinted to the same accent the
    // countdown beside it uses, so the row reads as one thing.
    val tint = GlanceTheme.colors.primary

    if (frames.size < 2) {
        prayer.weatherIcon?.let {
            Image(
                provider = ImageProvider(it),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = GlanceModifier.size(size),
            )
        }
        return
    }

    // setColorFilter over setImageViewBitmap rather than recolouring the bitmaps: the
    // frames are cached per weather code and shared by every widget, so tinting them at
    // render time would bake one theme's colour into the cache. ImageView.setColorFilter
    // is remotable and applies SRC_ATOP, which recolours the glyph and leaves its alpha —
    // exactly what a solid silhouette needs.
    val tintArgb = tint.getColor(context).toArgb()
    val remoteViews = RemoteViews(
        context.packageName,
        R.layout.widget_meteocon_flipper,
    ).apply {
        FRAME_VIEW_IDS.forEachIndexed { index, viewId ->
            // The layout has a fixed number of children; cycle the available frames over
            // them so a short render still fills every slot rather than leaving blanks
            // that would read as a stutter.
            setImageViewBitmap(viewId, frames[index % frames.size])
            setInt(viewId, "setColorFilter", tintArgb)
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


