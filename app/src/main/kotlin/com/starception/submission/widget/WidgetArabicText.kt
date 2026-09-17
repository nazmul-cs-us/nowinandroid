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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat

/**
 * Arabic drawn in our own process, in the reader's chosen Quran face, delivered as a bitmap.
 *
 * A RemoteViews TextView is inflated by the launcher, and on One UI the `@font` family named
 * in the layout does not survive that crossing — the widget showed the system Naskh whatever
 * the app's setting said. Laying the text out here also removes the guesswork about how wide
 * the host will draw it: the bitmap is exactly the lines it holds.
 */
internal object WidgetArabicText {

    class Rendered(val bitmap: Bitmap, val widthDp: Float, val heightDp: Float, val lines: Int)

    /**
     * Fits [text] into [widthDp] at the largest size in `[minSizeSp, maxSizeSp]` that needs
     * no more than [maxLines] lines, preferring one line whenever it can stay at or above
     * [singleLineFloorSp]. Returns null for blank text.
     */
    fun render(
        context: Context,
        text: String,
        widthDp: Float,
        maxSizeSp: Float,
        minSizeSp: Float,
        maxLines: Int,
        singleLineFloorSp: Float,
        color: Int,
        /**
         * Tallest the finished block may be. The size search honours this as well as the
         * line allowance, so a caller can hand over the room it has and get the largest
         * size that truly fits it — estimating the height from the size outside here means
         * guessing these faces' line spacing, and guessing it low wastes the room.
         */
        maxHeightDp: Float = Float.MAX_VALUE,
        // The Quran faces declare an ascent/descent about twice their letter height, so
        // even their natural pitch reads as a gap; 0.72 of it still clears stacked marks.
        lineSpacing: Float = 0.72f,
    ): Rendered? {
        if (text.isBlank() || widthDp <= 0f) return null
        val metrics = context.resources.displayMetrics
        val widthPx = (widthDp * metrics.density).toInt().coerceAtLeast(1)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = runCatching {
                ResourcesCompat.getFont(context, arabicFontResourceFor(context))
            }.getOrNull() ?: Typeface.DEFAULT
            this.color = color
        }
        fun layoutAt(sizeSp: Float): StaticLayout {
            paint.textSize = sizeSp * metrics.scaledDensity
            return StaticLayout.Builder
                .obtain(text, 0, text.length, paint, widthPx)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                .setLineSpacing(0f, lineSpacing)
                .setIncludePad(false)
                .build()
        }
        val maxHeightPx = if (maxHeightDp == Float.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            (maxHeightDp * metrics.density).toInt()
        }
        var chosen: StaticLayout? = null
        var size = maxSizeSp
        // One line first, while it stays legible; then as many lines as allowed.
        while (size >= singleLineFloorSp) {
            val layout = layoutAt(size)
            if (layout.lineCount <= 1 && layout.height <= maxHeightPx) { chosen = layout; break }
            size -= 0.5f
        }
        if (chosen == null) {
            size = maxSizeSp
            while (size >= minSizeSp) {
                val layout = layoutAt(size)
                if (layout.lineCount <= maxLines && layout.height <= maxHeightPx) {
                    chosen = layout
                    break
                }
                size -= 0.5f
            }
        }
        val layout = chosen ?: layoutAt(minSizeSp)
        val heightPx = layout.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        layout.draw(Canvas(bitmap))
        return Rendered(
            bitmap = bitmap,
            widthDp = widthDp,
            heightDp = heightPx / metrics.density,
            lines = layout.lineCount,
        )
    }
}
