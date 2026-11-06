package com.starception.submission.feature.prayertimes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.starception.submission.R
import com.starception.submission.feature.surah.getArabicFontResId
import kotlin.math.ceil

internal const val QURAN_AYAH_MARKER_TAG = "quran-ayah-marker"

/** Measurement and drawing share a ReplacementSpan, including Android justification. */
internal class QuranAyahMarkerSpan(
    private val digits: String,
    private val typeface: Typeface,
    private val accent: Int,
    private val fill: Int,
    private val strokeWidth: Float,
) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int = ceil(paint.textSize * 1.4f).toInt()

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val markerPaint = Paint(paint).apply { clearShadowLayer() }
        val width = getSize(paint, text, start, end, null)
        val centerX = x + width / 2f
        val centerY = y + (paint.ascent() + paint.descent()) / 2f
        val radius = paint.textSize * .46f
        markerPaint.color = fill
        markerPaint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, radius, markerPaint)
        markerPaint.color = accent
        markerPaint.style = Paint.Style.STROKE
        markerPaint.strokeWidth = strokeWidth
        canvas.drawCircle(centerX, centerY, radius, markerPaint)
        markerPaint.style = Paint.Style.FILL
        markerPaint.typeface = typeface
        markerPaint.textSize = paint.textSize * if (digits.length >= 3) .40f else .48f
        markerPaint.textAlign = Paint.Align.CENTER
        val digitBounds = Rect()
        markerPaint.getTextBounds(digits, 0, digits.length, digitBounds)
        canvas.drawText(
            digits,
            centerX,
            centerY - digitBounds.exactCenterY(),
            markerPaint,
        )
    }
}

@Composable
internal fun QuranPassageText(
    text: AnnotatedString,
    style: TextStyle,
    selectedArabicFont: String,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val typeface = remember(selectedArabicFont) {
        ResourcesCompat.getFont(context, getArabicFontResId(selectedArabicFont))
    }
    val markerTypeface = remember { ResourcesCompat.getFont(context, R.font.amiri_quran)!! }
    val fontSizePx = with(density) { style.fontSize.toPx() }
    val lineHeightPx = with(density) { style.lineHeight.toPx() }
    val strokeWidth = .65f * density.density
    val spokenText = remember(text) {
        StringBuilder(text.text).apply {
            text.getStringAnnotations(QURAN_AYAH_MARKER_TAG, 0, text.length)
                .sortedByDescending { it.start }
                .forEach { replace(it.start, it.end, " Ayah ${it.item} ") }
        }.toString()
    }
    val spannable = remember(text, markerTypeface, strokeWidth) {
        SpannableString(text.text).apply {
            text.spanStyles.forEach { range ->
                if (range.item.color != androidx.compose.ui.graphics.Color.Unspecified) {
                    setSpan(
                        ForegroundColorSpan(range.item.color.toArgb()),
                        range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
            text.getStringAnnotations(QURAN_AYAH_MARKER_TAG, 0, text.length).forEach { range ->
                setSpan(
                    QuranAyahMarkerSpan(
                        range.item, markerTypeface,
                        android.graphics.Color.rgb(216, 171, 89),
                        android.graphics.Color.argb(122, 107, 89, 67),
                        strokeWidth,
                    ),
                    range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                setPadding(0, 0, 0, 0)
                includeFontPadding = false
                textDirection = View.TEXT_DIRECTION_RTL
                setTextColor(android.graphics.Color.WHITE)
                setTextIsSelectable(false)
            }
        },
        update = { view ->
            view.typeface = typeface
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizePx)
            view.setLineSpacing(lineHeightPx - view.paint.fontSpacing, 1f)
            view.gravity = when (textAlign) {
                TextAlign.Center -> Gravity.CENTER_HORIZONTAL
                TextAlign.End, TextAlign.Left -> Gravity.LEFT
                else -> Gravity.RIGHT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.justificationMode = if (textAlign == TextAlign.Justify) {
                    Layout.JUSTIFICATION_MODE_INTER_WORD
                } else {
                    Layout.JUSTIFICATION_MODE_NONE
                }
            }
            style.shadow?.let { shadow ->
                view.setShadowLayer(shadow.blurRadius, shadow.offset.x, shadow.offset.y, shadow.color.toArgb())
            }
            if (view.text !== spannable) view.text = spannable
            view.contentDescription = spokenText
        },
    )
}
