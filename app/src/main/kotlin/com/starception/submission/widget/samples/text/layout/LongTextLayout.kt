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

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
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
import com.starception.submission.widget.WidgetText
import com.starception.submission.widget.WidgetTextAlign
import com.starception.submission.widget.WidgetFontWeight
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
import com.starception.submission.R
import com.starception.submission.widget.samples.text.layout.WidgetTextDimensions.captionFontSizeAndMaxLines
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
    width >= 300.dp -> 19.sp
    width >= 220.dp -> 17.sp
    else -> 15.sp
  }
  val captionSize = 12.sp

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
        WidgetText(
          text = data.caption,
          size = captionSize,
          color = GlanceTheme.colors.secondary,
          weight = WidgetFontWeight.Medium,
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        WidgetText(
          text = data.text,
          size = bodySize,
          color = GlanceTheme.colors.onSurface,
          weight = WidgetFontWeight.Regular,
          // The whole point of the scrolling container: let it run.
          maxLines = 100,
        )
      }
    }
    }

    if (data.sourceName != null || data.sourceDetail != null) {
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