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
import androidx.glance.color.ColorProvider as DayNightColorProvider
import com.starception.submission.MainActivity
import com.starception.submission.R

/** Matches the ported layouts' own widgetPadding so the two sit consistently. */
private val WIDGET_PADDING = 16.dp

/** A quiet inset surface that keeps the schedule distinct without introducing a new hue. */
private val PrayerScheduleSurface = DayNightColorProvider(
    Color(0xFFF8F9FF),
    Color(0xFF323A4B),
)

private val PrayerScheduleDivider = DayNightColorProvider(
    Color(0xFFD2D7E3),
    Color(0xFF596274),
)

/** Prayer-specific accents mirror the reference artwork while remaining legible at night. */
private val FajrAccent = DayNightColorProvider(Color(0xFFE9AD22), Color(0xFFFFD166))
private val DhuhrAccent = DayNightColorProvider(Color(0xFFF1B72F), Color(0xFFFFD166))
private val MaghribAccent = DayNightColorProvider(Color(0xFFD86F2F), Color(0xFFFFA66F))
private val IshaAccent = DayNightColorProvider(Color(0xFF6042B8), Color(0xFFC6B8FF))

// The title bar's height is set by the tallest thing in it — the 48dp refresh target —
// plus 4dp of padding above and below. The hero below sizes its type against the height
// it is given, so this has to come off that budget, and it has to be *right*: the row was
// briefly built with 8dp of padding, making it 64dp against this 56dp constant, and the
// 8dp difference was enough to squeeze the last row until the TextView clipped its own
// glyphs mid-stroke. Change one and change the other.
private val TITLE_BAR_HEIGHT = 56.dp

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
 * Its fixed furniture is the start padding (16dp), the pin (22dp), the gap after it (8dp),
 * the refresh target (48dp) and the end padding (4dp) — 98dp before a single character of
 * the place name. Below roughly 200dp the name has less room than the chrome around it,
 * and the bare surface makes better use of the card.
 *
 * Measured against LocalSize, which on One UI is the grid cell rather than the drawn size:
 * that launcher scales the whole rendering to about 83%, so this is ~167dp on screen.
 */
private val TITLE_BAR_MIN_WIDTH = 200.dp

/** The single-line layout's own inset — tighter than [WIDGET_PADDING], which it cannot afford. */
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
 * Measured against the content box, so both thresholds carry the 10dp that
 * [SURFACE_BOTTOM_PADDING] gave back to it — they were tuned when the titled surface
 * still reserved a full [WIDGET_PADDING] underneath. Without that, a 2-row card came out
 * at 154dp against the old 150dp gate and began drawing a five-prayer schedule into a box
 * with room for one, which is a two-row card showing "Today's Prayers" and Fajr alone.
 */
// Below this height there is not enough room for five useful schedule rows. Keep the
// prayer hero instead of selecting a cramped/partial timetable; once the schedule does
// fit, its compact renderer preserves the same cards, icons and columns as the full size.
private val EXPANDED_CONTENT_MIN_HEIGHT = 260.dp
private val FULL_SCHEDULE_MIN_HEIGHT = 300.dp

/**
 * Bottom inset under the titled surface's content.
 *
 * Deliberately less than [WIDGET_PADDING]: the schedule ends in a line of text, whose own
 * ascent/descent already stands it off the edge, so the full margin left the last prayer
 * floating a whole row's height above the card's bottom — measured 60px of dead band
 * under Isha against a 73px row pitch. The sides keep the full padding, where there is no
 * such optical inset.
 */
private val SURFACE_BOTTOM_PADDING = 6.dp

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

        provideContent {
            GlanceTheme(colors = PrayerWidgetColors) {
                when (state) {
                    PrayerWidgetState.Unavailable -> BareSurface { UnavailableContent() }
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
                        val innerWidth = size.width - (WIDGET_PADDING * 2)
                        // The Scaffold pads the sides and the bottom; the title bar
                        // stands in for the top.
                        val titledHeight = size.height - TITLE_BAR_HEIGHT - SURFACE_BOTTOM_PADDING
                        val bareHeight = size.height - (WIDGET_PADDING * 2)
                        when {
                            // Any single-row card, whatever its width. It carries the same
                            // current/next/progress information plus the same icon/title/
                            // refresh header language as Daily Reminder.
                            bareHeight < HERO_MIN_HEIGHT -> {
                                CompactPrayerSurface(
                                    state = state,
                                    contentSize = DpSize(innerWidth, bareHeight),
                                )
                            }

                            // Everything taller renders the same hero. Only the surface
                            // around it differs: the title bar has to earn its 56dp, so it
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
                            size.width >= TITLE_BAR_MIN_WIDTH && titledHeight >= HERO_MIN_HEIGHT ->
                                TitledSurface(state) {
                                    AdaptivePrayerContent(
                                        state = state,
                                        contentSize = DpSize(innerWidth, titledHeight),
                                    )
                                }

                            else -> BareSurface(Alignment.Vertical.Top) {
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

/** The Daily Reminder header language compressed to fit a single widget row. */
@Composable
private fun CompactPrayerSurface(
    state: PrayerWidgetState.Available,
    contentSize: DpSize,
) {
    val context = LocalContext.current
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
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(
                start = WIDGET_PADDING,
                top = WIDGET_PADDING,
                end = WIDGET_PADDING,
                bottom = SURFACE_BOTTOM_PADDING,
            ),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_flaticon_location_marker),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(22.dp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            WidgetText(
                text = headerTitle,
                size = titleSize.sp,
                color = GlanceTheme.colors.onSurface,
                weight = WidgetFontWeight.Medium,
                modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(R.drawable.sample_refresh_icon),
                contentDescription = "Refresh prayer times",
                colorFilter = ColorFilter.tint(GlanceTheme.colors.secondary),
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
            .padding(bottom = SURFACE_BOTTOM_PADDING)
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
                        start = WIDGET_PADDING,
                        end = 4.dp,
                        top = 4.dp,
                        bottom = 4.dp,
                    ),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_flaticon_location_marker),
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
    Medium(R.layout.widget_text_medium),
    Bold(R.layout.widget_text_bold),

    // The Arabic faces the app offers. Ubuntu Sans has no Arabic coverage at all, and
    // which of these to use is the reader's choice, not ours — see [arabicFontFor].
    ArabicPdms(R.layout.widget_text_arabic_pdms),
    ArabicNoor(R.layout.widget_text_arabic_noor),
    ArabicThabit(R.layout.widget_text_arabic_thabit),
    ArabicUthmani(R.layout.widget_text_arabic_uthmani),
    ArabicIndoPak(R.layout.widget_text_arabic_indopak),
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
        // WidgetText, not Glance's Text, for the same reason every other line uses it:
        // this is the first thing a user sees before granting location, and rendering it
        // in the system font while the rest of the widget is Ubuntu Sans would make the
        // empty state look like a different app's.
        WidgetText(
            text = "Prayer times",
            size = 15.sp,
            color = GlanceTheme.colors.onSurface,
            weight = WidgetFontWeight.Medium,
            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
        )
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
            AnimatedMeteocon(prayer = state.nextPrayer, size = 20.dp)
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

/** Every prayer of the day, name over time, with the next one emphasized. */
@Composable
private fun DayStripContent(
    prayers: List<WidgetPrayer>,
    nameSize: Float,
    timeSize: Float,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        prayers.forEach { prayer ->
            val nameColor = when {
                prayer.isNext -> GlanceTheme.colors.primary
                prayer.isPast -> GlanceTheme.colors.outline
                else -> GlanceTheme.colors.onSurfaceVariant
            }
            val timeColor = when {
                prayer.isNext -> GlanceTheme.colors.primary
                prayer.isPast -> GlanceTheme.colors.outline
                else -> GlanceTheme.colors.onSurface
            }
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                WidgetText(
                    text = prayer.name,
                    size = nameSize.sp,
                    color = nameColor,
                    weight = if (prayer.isNext) WidgetFontWeight.Bold else WidgetFontWeight.Regular,
                    align = WidgetTextAlign.Center,
                )
                WidgetText(
                    text = prayer.time,
                    size = timeSize.sp,
                    color = timeColor,
                    weight = if (prayer.isNext) WidgetFontWeight.Bold else WidgetFontWeight.Medium,
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
            .background(PrayerScheduleSurface)
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
            usePrayerIconAccents = true,
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
) {
    val countdown = state.countdown.removePrefix("in ").trim()
    val countdownLabel = if (countdown.equals("now", ignoreCase = true)) "Now" else countdown
    val countdownText = if (countdownLabel == "Now") "Now" else "in $countdownLabel"

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(if (compact) 52.dp else 58.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(if (compact) 14.dp else 16.dp)
            .padding(horizontal = if (compact) 10.dp else 12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .height(if (compact) 40.dp else 46.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            WidgetText(
                text = "Coming up",
                size = (if (compact) 13f else 14f).sp,
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
            text = "Sunrise  ${state.sunrise}",
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
    usePrayerIconAccents: Boolean = false,
    compact: Boolean = false,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        prayers.forEachIndexed { index, prayer ->
            // Each row is wrapped rather than placed directly so the nudge below the last
            // divider costs no extra child: Glance truncates a Column after ten of them,
            // and five rows with four dividers already sits at nine.
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .padding(horizontal = if (compact) 6.dp else 8.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    if (showWeatherIcons) {
                        AnimatedMeteocon(
                            prayer = prayer,
                            size = if (compact) 20.dp else 24.dp,
                            tint = if (usePrayerIconAccents) prayerAccent(prayer) else null,
                        )
                        Spacer(modifier = GlanceModifier.width(if (compact) 8.dp else 10.dp))
                    }
                    WidgetText(
                        text = prayer.name,
                        size = textSize.sp,
                        color = when {
                            prayer.isPast -> GlanceTheme.colors.outline
                            else -> GlanceTheme.colors.onSurface
                        },
                        weight = if (prayer.isNext) {
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
                        color = GlanceTheme.colors.onSurface,
                        weight = if (prayer.isNext) {
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
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(PrayerScheduleDivider),
                ) {}
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
        AnimatedMeteocon(prayer = state.nextPrayer, size = 20.dp)
    }

    // The hero ends on its largest line, which needs more clearance beneath it than the
    // schedule's small type does. [SURFACE_BOTTOM_PADDING] is tuned for that schedule,
    // and at 6dp it left this countdown all but touching the card's lower edge. Placed
    // after the row so the weighted gaps above surrender the space, rather than the
    // content growing past the card.
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
private fun AnimatedMeteocon(
    prayer: WidgetPrayer,
    size: androidx.compose.ui.unit.Dp,
    tint: ColorProvider? = null,
) {
    val frames = prayer.weatherFrames
    val context = LocalContext.current

    // The artwork is now the Mono set — one black silhouette — so it has to be tinted to
    // be anything but a black smudge on a light card. Tinted to the same accent the
    // countdown beside it uses, so the row reads as one thing.
    val resolvedTint = tint ?: GlanceTheme.colors.primary

    if (frames.size < 2) {
        prayer.weatherIcon?.let {
            Image(
                provider = ImageProvider(it),
                contentDescription = null,
                colorFilter = ColorFilter.tint(resolvedTint),
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
    val tintArgb = resolvedTint.getColor(context).toArgb()
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

@Composable
private fun prayerAccent(prayer: WidgetPrayer): ColorProvider = when (prayer.name.lowercase()) {
    "fajr" -> FajrAccent
    "dhuhr" -> DhuhrAccent
    "maghrib" -> MaghribAccent
    "isha" -> IshaAccent
    else -> GlanceTheme.colors.primary
}

private val FRAME_VIEW_IDS = intArrayOf(
    R.id.meteocon_frame_0,
    R.id.meteocon_frame_1,
    R.id.meteocon_frame_2,
    R.id.meteocon_frame_3,
    R.id.meteocon_frame_4,
    R.id.meteocon_frame_5,
)
