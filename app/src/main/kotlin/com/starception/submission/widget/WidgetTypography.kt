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
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.starception.submission.core.designsystem.R as DesignR

/**
 * Type sizing for the prayer widget.
 *
 * Glance renders to RemoteViews, which has no text auto-sizing and no intrinsic
 * measurement, so a layout that must survive every launcher footprint has to work out its
 * own type. The two things this file exists to get right:
 *
 *  - **Real measurement, not character counts.** Estimating a string's width as
 *    `length * emFactor` is wrong by up to 30% depending on which letters it contains —
 *    "Best Time to Pray Dhuhr" and "39 minutes since Dhuhr" are one character apart and
 *    render about the same width at *different* sizes. Guessing too wide makes the layout
 *    reserve a second line it does not need and shrink every size on the card to pay for
 *    it. [Metrics] measures the actual glyphs instead.
 *
 *  - **A ramp, not arithmetic.** Deriving each size as a fraction of the last produces
 *    values like 19.91sp over 14.93sp: sizes no designer would choose, and the reason the
 *    card read as arbitrary rather than composed. [STEPS] is a fixed ladder of whole-point
 *    sizes whose four lines were picked to sit together, and the layout picks a rung.
 */
internal object WidgetTypography {

    /**
     * One rung of the ramp: the four text roles in the hero, sized to sit together.
     *
     * Whole points throughout, and the gaps between roles narrow as the rung gets smaller
     * — at the top a strong heading can carry a much lighter supporting line, but at 14sp
     * there is no room for contrast that does not cost legibility.
     */
    data class Step(
        val title: Float,
        val elapsed: Float,
        val label: Float,
        val next: Float,
    )

    /** Smallest first; the layout takes the largest rung that fits. */
    val STEPS = listOf(
        Step(title = 14f, elapsed = 12f, label = 11f, next = 13f),
        Step(title = 16f, elapsed = 13f, label = 11f, next = 14f),
        Step(title = 18f, elapsed = 14f, label = 12f, next = 16f),
        Step(title = 20f, elapsed = 15f, label = 12f, next = 17f),
        Step(title = 22f, elapsed = 16f, label = 13f, next = 18f),
        Step(title = 24f, elapsed = 17f, label = 13f, next = 20f),
        Step(title = 28f, elapsed = 19f, label = 14f, next = 22f),
    )

    /**
     * Measured width of text, in dp, per 1sp of type size.
     *
     * Width scales linearly with text size, so one measurement at a reference size gives
     * the width at any size — `widthDp = perSp * sizeSp`.
     *
     * Measured with the process default typeface, which is what RemoteViews resolves to in
     * the launcher as well; both sides read the same system font, including a vendor one
     * like One UI's.
     */
    fun widthPerSp(context: Context, text: String, bold: Boolean): Float {
        if (text.isEmpty()) return 0f
        val metrics = context.resources.displayMetrics
        val widthPx = synchronized(paint) {
            paint.typeface = typeface(context, bold)
            paint.textSize = REFERENCE_PX
            paint.measureText(text)
        }
        val widthDp = widthPx / metrics.density
        val referenceSp = REFERENCE_PX / metrics.scaledDensity
        return widthDp / referenceSp
    }

    /**
     * Largest size, in sp, at which [text] fits [lines] lines of [maxWidthDp].
     *
     * The multi-line allowance is deliberately short of `lines * maxWidthDp`: text breaks
     * at word boundaries, so a wrapped line never fills its full width, and treating it as
     * if it did is what puts the last word on a line of its own.
     */
    fun fittingSize(
        context: Context,
        text: String,
        maxWidthDp: Float,
        lines: Int = 1,
        bold: Boolean = false,
    ): Float {
        val perSp = widthPerSp(context, text, bold)
        if (perSp <= 0f) return Float.MAX_VALUE
        val usable = maxWidthDp * lines * if (lines > 1) WRAP_EFFICIENCY else 1f
        return usable / perSp
    }

    /** How many lines [text] needs at [sizeSp] within [maxWidthDp]. */
    fun lineCount(context: Context, text: String, maxWidthDp: Float, sizeSp: Float): Int {
        val needed = widthPerSp(context, text, bold = false) * sizeSp
        return if (needed <= maxWidthDp) 1 else 2
    }

    /**
     * Height of one line box, in dp, per 1sp of type size — the real figure from the
     * font's own metrics rather than the ~1.3x rule of thumb.
     *
     * The estimate is what left a card with type two rungs smaller than it could carry and
     * a band of dead space where the difference went: overstating each line box by even a
     * few percent compounds across four lines into a whole rung.
     */
    fun lineHeightPerSp(context: Context): Float {
        val metrics = context.resources.displayMetrics
        val heightPx = synchronized(paint) {
            paint.typeface = typeface(context, bold = false)
            paint.textSize = REFERENCE_PX
            val fm = paint.fontMetrics
            // top/bottom, not ascent/descent: TextView defaults to includeFontPadding=true
            // and lays each line out in the larger of the two boxes. Measuring the smaller
            // one under-counts every line and picks a rung whose last line then clips off
            // the bottom of the card.
            fm.bottom - fm.top
        }
        val heightDp = heightPx / metrics.density
        val referenceSp = REFERENCE_PX / metrics.scaledDensity
        return heightDp / referenceSp
    }

    /**
     * The face the widget actually draws in, so the measurements match the rendering.
     *
     * This has to be Ubuntu Sans and not [Typeface.DEFAULT]: the widget's text is drawn by
     * RemoteViews TextViews carrying the bundled family, and measuring one font while
     * rendering another puts every fitting decision out by the difference between them.
     *
     * Falls back to the system face if the resource cannot be loaded, which keeps the
     * sizing approximately right rather than dividing by a zero-width measurement.
     */
    private fun typeface(context: Context, bold: Boolean): Typeface {
        val cached = if (bold) boldFace else regularFace
        if (cached != null) return cached
        val resource = if (bold) DesignR.font.ubuntu_sans_bold else DesignR.font.ubuntu_sans_regular
        val loaded = runCatching { ResourcesCompat.getFont(context, resource) }.getOrNull()
            ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        if (bold) boldFace = loaded else regularFace = loaded
        return loaded
    }

    private var regularFace: Typeface? = null
    private var boldFace: Typeface? = null

    private const val REFERENCE_PX = 100f

    /** Word wrapping leaves a ragged edge; a wrapped line holds ~92% of its width. */
    private const val WRAP_EFFICIENCY = 0.92f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
}
