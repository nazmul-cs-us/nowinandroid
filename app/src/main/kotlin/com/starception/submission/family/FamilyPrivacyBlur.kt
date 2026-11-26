package com.starception.submission.family

import kotlin.math.abs
import kotlin.math.roundToInt

internal data class PrivacyRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun clamped(width: Int, height: Int) = PrivacyRegion(left.coerceIn(0, width), top.coerceIn(0, height),
        right.coerceIn(0, width), bottom.coerceIn(0, height))

    fun stabilizedFrom(previous: PrivacyRegion): PrivacyRegion {
        val width = right - left
        val height = bottom - top
        val horizontalLimit = width * .025f
        val verticalLimit = height * .025f
        val shiftX = (((previous.left + previous.right) - (left + right)) * .25f)
            .coerceIn(-horizontalLimit, horizontalLimit).roundToInt()
        val shiftY = (((previous.top + previous.bottom) - (top + bottom)) * .25f)
            .coerceIn(-verticalLimit, verticalLimit).roundToInt()
        // Expand the shifted mask to preserve coverage around the current face.
        return PrivacyRegion(
            left + shiftX - abs(shiftX), top + shiftY - abs(shiftY),
            right + shiftX + abs(shiftX), bottom + shiftY + abs(shiftY),
        )
    }
}

/** Three separable box passes approximate a strong Gaussian blur, including the chroma planes. */
internal object FamilyPrivacyBlur {
    private const val DOT_CYCLE_NS = 1_800_000_000L
    // Mint #63E69B in limited-range BT.601, as used by the I420 camera path.
    private const val DOT_Y = 173
    private const val DOT_U = 115
    private const val DOT_V = 76
    private const val CHROMA_ALPHA_MAX = 252
    fun apply(
        plane: ByteArray,
        width: Int,
        height: Int,
        region: PrivacyRegion,
        radius: Int,
        oval: Boolean = false,
        dots: Boolean = false,
        timestampNs: Long = 0,
        dotChromaAlpha: ByteArray? = null,
    ) {
        val b = region.clamped(width, height)
        val w = b.right - b.left
        val h = b.bottom - b.top
        if (w <= 0 || h <= 0) return
        val pixels = IntArray(w * h) { i -> plane[(b.top + i / w) * width + b.left + i % w].toInt() and 255 }
        val scratch = IntArray(pixels.size)
        val r = radius.coerceAtLeast(1)
        repeat(3) {
            for (y in 0 until h) {
                var sum = 0
                var end = -1
                var start = 0
                for (x in 0 until w) {
                    val nextEnd = minOf(w - 1, x + r)
                    while (end < nextEnd) { end++; sum += pixels[y * w + end] }
                    val nextStart = maxOf(0, x - r)
                    while (start < nextStart) { sum -= pixels[y * w + start]; start++ }
                    scratch[y * w + x] = sum / (end - start + 1)
                }
            }
            for (x in 0 until w) {
                var sum = 0
                var end = -1
                var start = 0
                for (y in 0 until h) {
                    val nextEnd = minOf(h - 1, y + r)
                    while (end < nextEnd) { end++; sum += scratch[end * w + x] }
                    val nextStart = maxOf(0, y - r)
                    while (start < nextStart) { sum -= scratch[start * w + x]; start++ }
                    pixels[y * w + x] = sum / (end - start + 1)
                }
            }
        }
        if (!oval && !dots) {
            for (y in 0 until h) for (x in 0 until w) {
                plane[(b.top + y) * width + b.left + x] = pixels[y * w + x].toByte()
            }
            return
        }
        val chromaWidth = (width + 1) / 2
        val centerX = (region.left + region.right) / 2f
        val centerY = (region.top + region.bottom) / 2f
        val radiusX = (region.right - region.left) / 2f
        val radiusY = (region.bottom - region.top) / 2f
        val spacing = (minOf(radiusX, radiusY) / 9f).coerceIn(5f, 14f)
        val phase = Math.floorMod(timestampNs, DOT_CYCLE_NS).toFloat() / DOT_CYCLE_NS
        val faceX = FloatArray(w) { x -> (b.left + x + .5f - centerX) / radiusX }
        val faceXSquared = FloatArray(w) { x -> faceX[x] * faceX[x] }
        val dotCenterXSquared = FloatArray(if (dots) w else 0)
        val dotOffsetXSquared = FloatArray(if (dots) w else 0)
        val dotSpark = FloatArray(if (dots) w else 0)
        val dotVerticalWeight = FloatArray(if (dots) w else 0)
        if (dots) {
            for (x in 0 until w) {
                val localX = b.left + x + .5f - region.left
                val dotX = localX % spacing - spacing / 2f
                val dotCenterX = faceX[x] - dotX / radiusX
                val horizontalScale = 1f - .42f * abs(dotCenterX).coerceAtMost(1f)
                dotCenterXSquared[x] = dotCenterX * dotCenterX
                val offset = dotX / horizontalScale
                dotOffsetXSquared[x] = offset * offset
                // Stagger the eased pulse across columns, like a moving voice waveform.
                val columnPhase = (phase + 2.18f - dotCenterX * .55f) % 1f
                val rise = (columnPhase / .28f).coerceIn(0f, 1f)
                val fall = ((.72f - columnPhase) / .44f).coerceIn(0f, 1f)
                val spark = rise * rise * (3f - 2f * rise) * fall * fall * (3f - 2f * fall)
                dotSpark[x] = spark
                val stretch = 1f + .55f * spark
                dotVerticalWeight[x] = 1f / (stretch * stretch)
            }
        }
        for (y in 0 until h) {
            val localY = b.top + y + .5f - region.top
            val dy = (b.top + y + .5f - centerY) / radiusY
            // A broader forehead and cheeks taper into a narrower jaw.
            val jawTaper = 1f - .52f * ((dy - .20f) / .80f).coerceIn(0f, 1f)
            val horizontalWeight = 1f / (jawTaper * jawTaper)
            val ySquared = dy * dy
            val verticalDistance = if (dy < 0f) ySquared else ySquared * ySquared
            val dotY = localY % spacing - spacing / 2f
            val dotCenterY = dy - dotY / radiusY
            val dotCenterYSquared = dotCenterY * dotCenterY
            val verticalScale = 1f - .42f * abs(dotCenterY).coerceAtMost(1f)
            val dotOffsetY = dotY / verticalScale
            val dotOffsetYSquared = dotOffsetY * dotOffsetY
            val rowIndex = (b.top + y) * width + b.left
            for (x in 0 until w) {
                val maskDistance = faceXSquared[x] * horizontalWeight + verticalDistance
                // Keep full blur across the face. Feather only the padded outer edge.
                val coverage = if (oval) ((1f - maskDistance) / .18f).coerceIn(0f, 1f) else 1f
                if (coverage == 0f) continue
                val index = rowIndex + x
                val original = plane[index].toInt() and 255
                var blurred = pixels[y * w + x].toFloat()
                if (dots) {
                    // Sample perspective at the dot center so every dot has a coherent outline.
                    val distanceFromCenter = (dotCenterXSquared[x] + dotCenterYSquared).coerceAtMost(1f)
                    val spark = dotSpark[x]
                    val dotRadius = spacing * (.29f - .11f * distanceFromCenter + .055f * spark)
                    val ellipseDistance = (dotOffsetXSquared[x] + dotOffsetYSquared * dotVerticalWeight[x]) / (dotRadius * dotRadius)
                    val dot = ((1f - ellipseDistance) * 2.6f).coerceIn(0f, 1f)
                    val opacity = (.28f + .18f * spark) * dot
                    blurred += (DOT_Y - blurred) * opacity
                    if (dotChromaAlpha != null && opacity > 0f) {
                        // Each I420 chroma sample covers four luma pixels. Average their opacity.
                        val chromaIndex = (b.top + y) / 2 * chromaWidth + (b.left + x) / 2
                        val previous = dotChromaAlpha[chromaIndex].toInt() and 255
                        val contribution = (opacity * coverage * (CHROMA_ALPHA_MAX / 4)).roundToInt()
                        dotChromaAlpha[chromaIndex] = (previous + contribution).coerceAtMost(CHROMA_ALPHA_MAX).toByte()
                    }
                }
                plane[index] = (original + (blurred - original) * coverage).toInt().toByte()
            }
        }
    }

    fun tintDotChroma(u: ByteArray, v: ByteArray, alpha: ByteArray) {
        for (index in alpha.indices) {
            val weight = alpha[index].toInt() and 255
            if (weight == 0) continue
            val cb = u[index].toInt() and 255
            val cr = v[index].toInt() and 255
            u[index] = (cb + (DOT_U - cb) * weight / CHROMA_ALPHA_MAX).toByte()
            v[index] = (cr + (DOT_V - cr) * weight / CHROMA_ALPHA_MAX).toByte()
        }
    }
}
