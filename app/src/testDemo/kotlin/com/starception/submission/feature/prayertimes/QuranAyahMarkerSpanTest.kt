package com.starception.submission.feature.prayertimes

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuranAyahMarkerSpanTest {
    @Test fun circleAndDigitsStayInsideTheMeasuredSlotAtEveryReadingSize() {
        for (size in listOf(18f, 24f, 36f, 48f)) {
            for (digits in listOf("١", "١٢", "١١٠")) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size
                    color = Color.WHITE
                }
                val span = QuranAyahMarkerSpan(digits, Typeface.DEFAULT, Color.YELLOW, Color.GRAY, .65f)
                val width = span.getSize(paint, "\uFFFC", 0, 1, null)
                val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                try {
                    // Native layout supplies x after justification; the span must
                    // draw only inside the width that it told that same layout.
                    val x = 30f
                    span.draw(Canvas(bitmap), "\uFFFC", 0, 1, x, 0, 80, 160, paint)
                    var paintedPixels = 0
                    for (row in 0 until bitmap.height) {
                        for (column in 0 until bitmap.width) {
                            if (Color.alpha(bitmap.getPixel(column, row)) != 0) {
                                paintedPixels++
                                assertTrue("$digits at $size escaped its slot", column >= x && column < x + width)
                            }
                        }
                    }
                    assertTrue(paintedPixels > 0)
                    assertEquals(Color.WHITE, paint.color)
                    assertEquals(size, paint.textSize, 0f)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }
}
