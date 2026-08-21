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
package com.starception.submission.widget.samples.text.layout

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.background
import com.starception.submission.widget.WidgetText
import com.starception.submission.widget.WidgetTextAlign
import com.starception.submission.widget.arabicFontResourceFor
import com.starception.submission.widget.WidgetFontWeight
import androidx.core.content.res.ResourcesCompat
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.wrapContentWidth
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.TextAlign
import androidx.glance.ColorFilter
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.action.Action
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.starception.submission.R
import com.starception.submission.widget.samples.text.layout.WidgetTextDimensions.captionFontSizeAndMaxLines
import com.starception.submission.widget.samples.text.layout.WidgetTextDimensions.contentSize
import com.starception.submission.widget.samples.text.layout.WidgetTextDimensions.widgetPadding
import com.starception.submission.widget.samples.text.layout.WidgetTextDimensions.primaryTextFontSizeAndMaxLines
import com.starception.submission.widget.samples.utils.ActionUtils.actionStartDemoActivity
import com.starception.submission.widget.samples.utils.FontUtils.calculateFontSizeAndMaxLines

/**
 * A layout focused on presenting text only content.
 *
 * A longer text with a short caption are displayed in a [Scaffold] under an app-specific title bar.
 *
 * This serves as an implementation suggestion, but should be customized to fit your product's
 * needs.
 *
 * @param title the text to be displayed as title of the widget, e.g. name of your widget or app.
 * @param titleIconRes a tintable icon that represents your app or brand, that can be displayed
 * with the provided [title]. In this sample, we use icon from a drawable resource, but you should
 * use an appropriate icon source for your use case.
 * @param titleBarActionIconRes resource id of a tintable icon that can be displayed as
 * an icon button within the title bar area of the widget. For example, a search icon.
 * @param titleBarActionIconContentDescription description of the [titleBarActionIconRes] button
 * to be used by the accessibility services.
 * @param titleBarAction action to be performed on click of the [titleBarActionIconRes] button.
 * @param data the text and caption to be displayed in the widget.
 * @param action action to be performed on click of the main content.
 */
@Composable
fun LongTextLayout(
  title: String,
  @DrawableRes titleIconRes: Int,
  @DrawableRes titleBarActionIconRes: Int? = null,
  titleBarActionIconContentDescription: String? = null,
  titleBarAction: Action? = null,
  data: LongTextLayoutData,
  action: Action? = null,
) {
  val showTitleBar = LongTextLayoutSize.fromLocalSize() != LongTextLayoutSize.XSmall
  val scaffoldTopPadding = if (showTitleBar) {
    0.dp
  } else {
    widgetPadding
  }

  Scaffold(
    backgroundColor = GlanceTheme.colors.widgetBackground,
    horizontalPadding = widgetPadding,
    modifier = GlanceModifier
      .padding(
        bottom = widgetPadding,
        top = scaffoldTopPadding
      ),
    titleBar = {
      if (showTitleBar) {
        TitleBarContent(
          titleIconRes,
          title,
          titleBarAction,
          titleBarActionIconRes,
          titleBarActionIconContentDescription
        )
      }
    },
  ) {
    TextStack(
      data = data,
      verticalAlignment = if (showTitleBar) {
        Alignment.Bottom
      } else Alignment.CenterVertically,
      action = action,
    )
  }
}

@Composable
private fun TitleBarContent(
  titleIconRes: Int,
  title: String,
  titleBarAction: Action?,
  titleBarActionIconRes: Int?,
  titleBarActionIconContentDescription: String?,
) {
  // Glance's TitleBar is not used here, for the same reason the prayer widget stopped
  // using it: its title is a Glance Text, which cannot carry the bundled Ubuntu Sans. The
  // two widgets sat on the same home screen with their headers in different typefaces.
  // This is the arrangement TitleBar draws — start icon, title, trailing action — rebuilt
  // so the title is a WidgetText.
  Row(
    modifier = GlanceModifier
      .fillMaxWidth()
      .padding(start = widgetPadding, end = 4.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Fully qualified: this file declares its own private Image() further down.
    androidx.glance.Image(
      provider = ImageProvider(titleIconRes),
      contentDescription = null,
      colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
      modifier = GlanceModifier.size(22.dp),
    )
    Spacer(modifier = GlanceModifier.width(8.dp))
    WidgetText(
      text = title.takeIf { showTitle() } ?: "",
      size = 15.sp,
      color = GlanceTheme.colors.onSurface,
      weight = WidgetFontWeight.Medium,
      modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
    )
    if (titleBarAction != null && titleBarActionIconRes != null) {
      CircleIconButton(
        imageProvider = ImageProvider(titleBarActionIconRes),
        contentDescription = titleBarActionIconContentDescription,
        contentColor = GlanceTheme.colors.secondary,
        backgroundColor = null, // transparent
        onClick = titleBarAction
      )
    }
  }
}

@Composable
private fun TextStack(
  data: LongTextLayoutData,
  verticalAlignment: Alignment.Vertical,
  action: Action?,
) {
  // Sized from the card, not from the text.
  //
  // The sample sized the body by shrinking it until the whole string fitted, which made
  // the type a function of content length: a short dua rendered large and a long hadith
  // small, so the same widget changed its typography every time it refreshed. That is
  // defensible when the text must fit a fixed box, and pointless now that it scrolls.
  val width = LocalSize.current.width
  // The body leads the card. It was one step smaller than the header above it, which put
  // the least important text on the card in the largest type — the prayer widget beside it
  // opens with its content at ~20sp, and these two should read as the same family.
  val bodySize = when {
    width >= 300.dp -> 17.sp
    width >= 220.dp -> 16.sp
    else -> 15.sp
  }
  // Set against the app's own Arabic, not against the Latin body beside it. The reader
  // has the Surah/Dua screens rendering at arabic_font_size (~34sp by default), and at
  // 1.15x the body this came out around 22sp — the same face, but visibly smaller and
  // tighter than the app, which is what made the widget's Arabic look like a different
  // font. A widget card cannot carry 34sp, so this takes the ratio rather than the value.
  val arabicSize = (bodySize.value * 1.45f).sp

  // Always carry the original after the English translation. The previous fit estimate
  // could suppress Arabic even when the launcher had ample room for it; the scrollable
  // content already handles genuinely long reminders without dropping either language.
  val arabic = data.arabic

  // LazyColumn, not Column, because the content is no longer a sample string of known
  // length: a Bukhari hadith or a Fortress dua can run to a paragraph, and a Column simply
  // truncated it — the reader got an ellipsis and no way to see the rest without opening
  // the app. A LazyColumn is the one scrollable container RemoteViews accepts, so it is
  // what lets the card show all of it.
  //
  // maxLines goes with it. It existed to stop overflow in a fixed-height Column; keeping
  // it here would cap the text at the same place while giving it somewhere to scroll to,
  // which is the worst of both.
  Column(modifier = GlanceModifier.fillMaxSize()) {
    // The list takes the space the footer does not, so the reference stays pinned to the
    // bottom of the card while the text scrolls behind it. Putting the footer inside the
    // list would scroll it out of sight, which for a citation is the one place it must
    // not be.
    LazyColumn(modifier = GlanceModifier.defaultWeight()) {
    item {
      Column(modifier = GlanceModifier.maybeClickable(action)) {
        // WidgetText, not Glance's Text, so this card is set in Ubuntu Sans like the
        // prayer widget beside it. Glance cannot carry a bundled font — see
        // widget_text_regular.xml — so the two cards were in different typefaces on the
        // same home screen, which reads as an unfinished app rather than a design.
        // The same treatment the prayer card gives "Next Prayer": accent colour, bold. In
        // muted secondary at Medium it read as a caption on the text rather than a label
        // for it, and at a glance the card did not say whether it was showing a hadith or
        // a dua — which is the first thing it should answer.
        Row(
          modifier = GlanceModifier
            .wrapContentWidth()
            .height(40.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(50.dp)
            .padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          WidgetText(
            text = data.caption.uppercase(),
            size = 10.sp,
            color = GlanceTheme.colors.onPrimaryContainer,
            weight = WidgetFontWeight.Medium,
            modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
          )
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        WidgetText(
          text = data.text,
          size = bodySize,
          color = GlanceTheme.colors.onSurface,
          weight = WidgetFontWeight.Regular,
          // The whole point of the scrolling container: let it run.
          maxLines = 100,
        )
        if (arabic != null) {
          Spacer(modifier = GlanceModifier.height(12.dp))
          Column(
            modifier = GlanceModifier
              .fillMaxWidth()
              .background(GlanceTheme.colors.secondaryContainer)
              .cornerRadius(16.dp)
              .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            ArabicWidgetBitmapText(
              text = arabic,
              size = arabicSize,
              width = contentSize.width - 24.dp,
              color = GlanceTheme.colors.onSecondaryContainer,
            )
          }
        }
      }
    }
    }

    if (data.sourceName != null || data.sourceDetail != null) {
      // Clearance, because the list's bottom edge cuts through whatever line happens to be
      // there. Without it a half-sliced line of the hadith sat directly on the citation and
      // read as a rendering fault rather than as text continuing below the fold.
      Spacer(modifier = GlanceModifier.height(10.dp))
      Spacer(
        modifier = GlanceModifier
          .fillMaxWidth()
          .height(1.dp)
          .background(GlanceTheme.colors.surfaceVariant),
      )
      Spacer(modifier = GlanceModifier.height(8.dp))
      Row(
        modifier = GlanceModifier.fillMaxWidth().maybeClickable(action),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        WidgetText(
          text = data.sourceName.orEmpty(),
          size = 11.sp,
          color = GlanceTheme.colors.outline,
          weight = WidgetFontWeight.Medium,
          modifier = GlanceModifier.wrapContentWidth().wrapContentHeight(),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (data.sourceDetail != null) {
          WidgetText(
            text = data.sourceDetail,
            size = 11.sp,
            color = GlanceTheme.colors.outline,
            weight = WidgetFontWeight.Medium,
            // The chapter title a dua cites can be a full sentence; it gives way to the
            // book name rather than pushing it off the card.
            modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
            align = WidgetTextAlign.End,
          )
        }
      }
    }
  }
}

/**
 * Rasterises Arabic inside the app process so the selected Mushaf face survives the
 * RemoteViews boundary. Samsung's launcher silently replaces bundled fonts referenced by
 * a remote TextView with its system Arabic face; an ImageView preserves the shaped glyphs.
 */
@Composable
private fun ArabicWidgetBitmapText(
  text: String,
  size: TextUnit,
  width: Dp,
  color: ColorProvider,
) {
  val context = LocalContext.current
  val metrics = context.resources.displayMetrics
  val widthPx = (width.value * metrics.density).toInt().coerceAtLeast(1)
  val textSizePx = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_SP,
    size.value,
    metrics,
  )
  val currentTextColor = color.getColor(context).toArgb()
  val lightTextColor = color.getColor(context.withNightMode(night = false)).toArgb()
  val darkTextColor = color.getColor(context.withNightMode(night = true)).toArgb()
  val fontRes = arabicFontResourceFor(context)
  val bitmap = remember(text, widthPx, textSizePx, fontRes) {
    renderArabicBitmap(
      text = text,
      widthPx = widthPx,
      textSizePx = textSizePx,
      typeface = ResourcesCompat.getFont(context, fontRes),
      densityDpi = metrics.densityDpi,
    )
  }
  val height = (bitmap.height / metrics.density).dp
  val remoteViews = remember(bitmap, currentTextColor, lightTextColor, darkTextColor) {
    RemoteViews(context.packageName, R.layout.widget_arabic_bitmap).apply {
      setImageViewBitmap(R.id.widget_arabic_image, bitmap)
      setContentDescription(R.id.widget_arabic_image, text)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setColorInt(
          R.id.widget_arabic_image,
          "setColorFilter",
          lightTextColor,
          darkTextColor,
        )
      } else {
        setInt(R.id.widget_arabic_image, "setColorFilter", currentTextColor)
      }
    }
  }

  AndroidRemoteViews(
    remoteViews = remoteViews,
    modifier = GlanceModifier.fillMaxWidth().height(height),
  )
}

private fun renderArabicBitmap(
  text: String,
  widthPx: Int,
  textSizePx: Float,
  typeface: android.graphics.Typeface?,
  densityDpi: Int,
): Bitmap {
  val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
    this.textSize = textSizePx
    // Keep the glyph bitmap theme-neutral; RemoteViews applies the day/night tint.
    color = Color.WHITE
    this.typeface = typeface
  }
  val layout = StaticLayout.Builder
    .obtain(text, 0, text.length, paint, widthPx)
    .setAlignment(Layout.Alignment.ALIGN_CENTER)
    .setIncludePad(true)
    // Indo-Pak reports an unusually tall ascent/descent box around its marks. Even the
    // nominal 1.0 baseline interval therefore leaves a conspicuous empty band between
    // lines; tighten that metrics box while retaining clearance for the diacritics.
    .setLineSpacing(0f, 0.78f)
    .setTextDirection(TextDirectionHeuristics.RTL)
    .build()
  return Bitmap.createBitmap(widthPx, layout.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    .also { bitmap ->
      bitmap.density = densityDpi
      layout.draw(Canvas(bitmap))
    }
}

private fun Context.withNightMode(night: Boolean): Context {
  val configuration = Configuration(resources.configuration)
  configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
  return createConfigurationContext(configuration)
}

private enum class LongTextLayoutSize {
  XSmall,
  Normal;

  companion object {
    @Composable
    fun fromLocalSize(): LongTextLayoutSize {
      return if (LocalSize.current.height <= 180.dp) {
        XSmall
      } else {
        Normal
      }
    }
  }
}

@Composable
private fun showTitle(): Boolean {
  return LocalSize.current.width >= 260.dp
}

private fun GlanceModifier.maybeClickable(action: Action?): GlanceModifier {
  return if (action != null) {
    this.clickable(action)
  } else {
    this
  }
}

/**
 * Information to be displayed in a [com.starception.submission.widget.samples.text.layout.LongTextLayout].
 *
 * @param key a unique identifier for the data being displayed as primary content e.g. article ID,
 * in an "article of the day" widget. This may not be necessary for your use case; in this sample,
 * we use this key to differentiate between placeholder and real data when demonstrating clicks.
 * @param text a required text representing primary information being conveyed to the user via the
 * widget; suitable for text of about 65 characters.
 * @param caption shorter text accompanying the primary [text] - that can fit in one line; e.g.
 * author's name in an "article of the day" widget.
 */
data class LongTextLayoutData(
  val key: String,
  val text: String,
  val caption: String,
  /** Arabic original, rendered under the text when the card has height to spare. */
  val arabic: String? = null,
  /** Book the text came from, shown in the footer's left corner. */
  val sourceName: String? = null,
  /** Where in that book, shown in the footer's right corner. */
  val sourceDetail: String? = null,
)

internal object WidgetTextDimensions {
  val widgetPadding = 16.dp
  private val titleBarHeight: Dp
    @Composable get() = if (LongTextLayoutSize.fromLocalSize() == LongTextLayoutSize.XSmall) {
      0.dp
    } else {
      56.dp
    }

  /** Height and width in dp available to main content (excluding title bar, padding, spacing). */
  val contentSize: DpSize
    @Composable get() {
      val size = LocalSize.current

      return DpSize(
        width = size.width - (2 * widgetPadding),
        height = size.height - widgetPadding - titleBarHeight
      )
    }

  // Upper and lower bounds for the caption.
  internal val minCaptionFontSize = 12.sp // low - GM3 Label Medium
  internal val maxCaptionFontSize = 14.sp // high - GM3 Label Large

  // Upper bound for primary text.
  internal val maxPrimaryTextFontSize = 28.sp // GM3 Headline Medium

  // For a font size 16 of primary text, we want caption to be of size 14.
  internal const val captionToPrimaryTextRatio = 0.875f

  @Composable
  fun primaryTextFontSizeAndMaxLines(text: String): Pair<TextUnit, Int> {
    val size = LocalSize.current
    // Primary text and caption share 70:30 height within the area available for texts.
    val availableHeightForPrimaryText = Dp(0.70f * contentSize.height.value)
    // In this layout, texts take up entire horizontal space except the paddings on the sides.
    val availableWidthForPrimaryText = size.width - (widgetPadding * 2)

    return calculateFontSizeAndMaxLines(
      context = LocalContext.current,
      text = text,
      availableWidth = availableWidthForPrimaryText,
      availableHeight = availableHeightForPrimaryText,
      minFontSize = (minCaptionFontSize.value / captionToPrimaryTextRatio).sp,
      maxFontSize = maxPrimaryTextFontSize
    )
  }

  fun captionFontSizeAndMaxLines(primaryFontSize: TextUnit): Pair<TextUnit, Int> {
    val estimatedFontSize = primaryFontSize.value * captionToPrimaryTextRatio
    val captionMaxLines = 1 // Caption is always 1 line.
    return estimatedFontSize.coerceAtMost(maxCaptionFontSize.value).sp to captionMaxLines
  }
}

/**
 * Previews of the long text layout with shorter caption and longer main text
 *
 * Previewing them at standard & min-max sizes allows us to adjust font sizes if needed. Use the
 * Preview annotation to view the widget at specific width / height.
 */


/**
 * Previews of the long text layout with longer caption and shorter main text
 *
 * Previewing them at standard & min-max sizes allows us to adjust font sizes if needed. Use the
 * Preview annotation to view the widget at specific width / height.
 */

/**
 * Previews of the long text layout with medium sized caption and main text
 *
 * Previewing them at standard & min-max sizes allows us to adjust font sizes if needed. Use the
 * Preview annotation to view the widget at specific width / height.
 */

@Composable
private fun LongTextLayoutPreview(text: String, caption: String) {
  val context = LocalContext.current

  LongTextLayout(
    title = context.getString(R.string.sample_long_text_app_widget_name),
    titleIconRes = R.drawable.sample_text_icon,
    titleBarActionIconRes = R.drawable.sample_refresh_icon,
    titleBarActionIconContentDescription = context.getString(
      R.string.sample_refresh_icon_button_label
    ),
    titleBarAction = null,
    data = LongTextLayoutData(
      key = "1",
      text = text,
      caption = caption,
    ),
    action = actionStartDemoActivity("1"),
  )
}
