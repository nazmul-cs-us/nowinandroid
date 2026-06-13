package com.starception.submission.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SweepGradient
import coil.size.Size
import coil.transform.Transformation

/**
 * Center-crops the source to a circle and draws a multicolor gradient ring around it —
 * the "Google account" avatar look. Ring width and gap are fractions of the bitmap's
 * smaller dimension so the result scales with whatever size Coil requests.
 */
class RingAvatarTransformation(
    private val ringFraction: Float = 0.075f,
    private val gapFraction: Float = 0.05f,
    private val ringColors: IntArray = GOOGLE_RING_COLORS,
    private val ringPositions: FloatArray = GOOGLE_RING_POSITIONS,
) : Transformation {

    override val cacheKey: String =
        "ring:$ringFraction:$gapFraction:${ringColors.joinToString(",")}:${ringPositions.joinToString(",")}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        // Center-crop to a square so the circle isn't distorted.
        val square = if (input.width != input.height) {
            val side = minOf(input.width, input.height)
            Bitmap.createBitmap(input, (input.width - side) / 2, (input.height - side) / 2, side, side)
        } else {
            input
        }

        val dim = square.width
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

        return output
    }

    private companion object {
        private const val BLUE = 0xFF4285F4.toInt()
        private const val GREEN = 0xFF34A853.toInt()
        private const val YELLOW = 0xFFFBBC05.toInt()
        private const val RED = 0xFFEA4335.toInt()

        // Each Google color holds a solid ~19% arc with quick ~6% blends between them, so
        // the ring reads as four distinct colors (not a rainbow). Android's SweepGradient
        // runs clockwise from 3 o'clock, so this lands: yellow bottom-right, green
        // bottom-left, blue top-left, red top-right — the Google account ring layout.
        val GOOGLE_RING_COLORS = intArrayOf(
            YELLOW, YELLOW, GREEN, GREEN, BLUE, BLUE, RED, RED, YELLOW,
        )
        val GOOGLE_RING_POSITIONS = floatArrayOf(
            0f, 0.22f, 0.28f, 0.47f, 0.53f, 0.72f, 0.78f, 0.97f, 1f,
        )
    }
}
