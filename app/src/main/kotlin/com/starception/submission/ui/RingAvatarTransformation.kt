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

        // Supersample at a power-of-two multiple of the display size, then downscale
        // by repeated halving (a clean box filter) before returning. Halving avoids
        // the soft, undersampled look of a 3x->1x bilinear shrink, so the thin ring
        // stays crisp while the consumer (MaterialButton/Compose) draws ~1:1 instead
        // of rescaling. Falls back to the source size when no target is known.
        val targetPx = outputPx ?: minOf(size.width.pxOrElse { 0 }, size.height.pxOrElse { 0 })
        val baseTarget = if (targetPx > 0) targetPx else maxOf(square.width, MIN_RENDER_PX)
        val dim = if (targetPx > 0) {
            (baseTarget * SUPERSAMPLE).coerceAtMost(MAX_RENDER_PX)
        } else {
            baseTarget
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
            downscaleByHalving(output, targetPx).also { if (it != output) output.recycle() }
        } else {
            output
        }
    }

    // Shrink to [target] by repeatedly halving (each halving averages a clean 2x2
    // block) before the final exact resize, which keeps edges sharp where a single
    // large-ratio bilinear shrink would smear them.
    private fun downscaleByHalving(src: Bitmap, target: Int): Bitmap {
        var current = src
        while (current.width / 2 > target) {
            val half = current.width / 2
            val next = Bitmap.createScaledBitmap(current, half, half, true)
            if (current != src) current.recycle()
            current = next
        }
        if (current.width == target) return current
        return Bitmap.createScaledBitmap(current, target, target, true)
            .also { if (current != src && current != it) current.recycle() }
    }

    private companion object {
        // Render at this power-of-two multiple of the display size, then downscale by
        // halving, so the ring is crisp without producing a needlessly huge bitmap.
        private const val SUPERSAMPLE = 4
        private const val MIN_RENDER_PX = 256
        private const val MAX_RENDER_PX = 1024

        private const val BLUE = 0xFF4285F4.toInt()
        private const val GREEN = 0xFF34A853.toInt()
        private const val YELLOW = 0xFFFBBC05.toInt()
        private const val RED = 0xFFEA4335.toInt()

        // Four solid Google colors as hard-edged arcs of unequal size — matching the
        // Google One profile ring. Each boundary duplicates the stop position so the
        // color flips instantly (no blend). Android's SweepGradient runs clockwise
        // from 3 o'clock, so these land: red top (~90°), blue right (~120°),
        // green bottom (~90°), yellow left (~60°).
        val GOOGLE_RING_COLORS = intArrayOf(
            BLUE, BLUE, GREEN, GREEN, YELLOW, YELLOW, RED, RED, BLUE, BLUE,
        )
        val GOOGLE_RING_POSITIONS = floatArrayOf(
            0f, 0.1667f, 0.1677f, 0.4167f, 0.4177f, 0.5833f, 0.5843f, 0.8333f, 0.8343f, 1f,
        )
    }
}
