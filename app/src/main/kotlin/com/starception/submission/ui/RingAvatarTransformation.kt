package com.starception.submission.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SweepGradient
import coil.size.Size
import coil.size.pxOrElse
import coil.transform.Transformation

/**
 * Center-crops the source to a circle and draws a multicolor gradient ring around it —
 * the "Google account" avatar look. Ring width and gap are fractions of the bitmap's
 * smaller dimension so the result scales with whatever size Coil requests.
 */
class RingAvatarTransformation(
    private val ringFraction: Float = 0.045f,
    private val gapFraction: Float = 0.05f,
    private val ringColors: IntArray = GOOGLE_RING_COLORS,
    private val ringPositions: FloatArray = GOOGLE_RING_POSITIONS,
    // Final output edge in px. When set, the result is delivered at exactly this size
    // (independent of the decode size), so a consumer that draws at a fixed size — e.g.
    // a MaterialButton icon — gets a 1:1 bitmap instead of softly rescaling. Leave null
    // to size the output from the Coil request (the AsyncImage path).
    private val outputPx: Int? = null,
) : Transformation {

    override val cacheKey: String =
        "ring:$MIN_RENDER_PX:$MAX_RENDER_PX:$outputPx:$ringFraction:$gapFraction:${ringColors.joinToString(",")}:${ringPositions.joinToString(",")}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        // Center-crop to a square so the circle isn't distorted.
        val square = if (input.width != input.height) {
            val side = minOf(input.width, input.height)
            Bitmap.createBitmap(input, (input.width - side) / 2, (input.height - side) / 2, side, side)
        } else {
            input
        }

        // Supersample ~3x the actual display size, then downscale once to that size
        // before returning. This keeps the thin ring crisp (drawn at high res with
        // good anti-aliasing) while handing the consumer a bitmap close to its display
        // size — so MaterialButton/Compose draw ~1:1 instead of softly rescaling a
        // huge or tiny bitmap. Falls back to the source size when no target is known.
        val targetPx = outputPx ?: minOf(size.width.pxOrElse { 0 }, size.height.pxOrElse { 0 })
        val dim = if (targetPx > 0) {
            (targetPx * 3).coerceIn(MIN_RENDER_PX, MAX_RENDER_PX)
        } else {
            maxOf(square.width, MIN_RENDER_PX)
        }
        val output = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val center = dim / 2f
        val ringWidth = dim * ringFraction
        val gap = dim * gapFraction
        val outerRadius = center
        val avatarRadius = outerRadius - ringWidth - gap

        // Inner circular avatar.
        val avatarDiameter = (avatarRadius * 2f).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(square, avatarDiameter, avatarDiameter, true)
        val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        avatarPaint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setTranslate(center - avatarRadius, center - avatarRadius) })
        }
        canvas.drawCircle(center, center, avatarRadius, avatarPaint)

        // Multicolor sweep-gradient ring.
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = ringWidth
        ringPaint.shader = SweepGradient(center, center, ringColors, ringPositions)
        canvas.drawCircle(center, center, outerRadius - ringWidth / 2f, ringPaint)

        // Downscale the supersampled render to the display size for a crisp ~1:1 draw.
        return if (targetPx in 1 until dim) {
            Bitmap.createScaledBitmap(output, targetPx, targetPx, true)
                .also { if (it != output) output.recycle() }
        } else {
            output
        }
    }

    private companion object {
        // Supersample bounds: render between these before downscaling to the display
        // size, so the ring is crisp without producing a needlessly huge bitmap.
        private const val MIN_RENDER_PX = 256
        private const val MAX_RENDER_PX = 1024

        private const val BLUE = 0xFF4285F4.toInt()
        private const val GREEN = 0xFF34A853.toInt()
        private const val YELLOW = 0xFFFBBC05.toInt()
        private const val RED = 0xFFEA4335.toInt()

        // Each Google color holds an equal 25% quadrant with a near-instant (~0.2%)
        // edge between them, so the ring reads as four sharp colors with no blend —
        // matching the Google account ring. Android's SweepGradient runs clockwise
        // from 3 o'clock, so this lands: yellow bottom-right, green bottom-left,
        // blue top-left, red top-right.
        val GOOGLE_RING_COLORS = intArrayOf(
            YELLOW, YELLOW, GREEN, GREEN, BLUE, BLUE, RED, RED, YELLOW,
        )
        val GOOGLE_RING_POSITIONS = floatArrayOf(
            0f, 0.249f, 0.251f, 0.499f, 0.501f, 0.749f, 0.751f, 0.999f, 1f,
        )
    }
}
