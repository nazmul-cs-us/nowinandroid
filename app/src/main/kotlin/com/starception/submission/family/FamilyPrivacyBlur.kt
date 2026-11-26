package com.starception.submission.family

internal data class PrivacyRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun clamped(width: Int, height: Int) = PrivacyRegion(left.coerceIn(0, width), top.coerceIn(0, height),
        right.coerceIn(0, width), bottom.coerceIn(0, height))
}

/** Three separable box passes approximate a strong Gaussian blur, including the chroma planes. */
internal object FamilyPrivacyBlur {
    fun apply(
        plane: ByteArray,
        width: Int,
        height: Int,
        region: PrivacyRegion,
        radius: Int,
        oval: Boolean = false,
        dots: Boolean = false,
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
        val centerX = (region.left + region.right) / 2f
        val centerY = (region.top + region.bottom) / 2f
        val radiusX = (region.right - region.left) / 2f
        val radiusY = (region.bottom - region.top) / 2f
        val spacing = (minOf(radiusX, radiusY) / 9f).coerceIn(5f, 14f)
        for (y in 0 until h) {
            val localY = b.top + y + .5f - region.top
            val dy = (b.top + y + .5f - centerY) / radiusY
            val dotY = localY % spacing - spacing / 2f
            val dotRadius = spacing * (.17f + .12f * (1f - dy * dy).coerceIn(0f, 1f))
            for (x in 0 until w) {
                val dx = (b.left + x + .5f - centerX) / radiusX
                val distanceSquared = dx * dx + dy * dy
                // Keep full blur across the face. Feather only the padded outer edge.
                val coverage = if (oval) ((1f - distanceSquared) / .24f).coerceIn(0f, 1f) else 1f
                if (coverage == 0f) continue
                val index = (b.top + y) * width + b.left + x
                val original = plane[index].toInt() and 255
                var blurred = pixels[y * w + x].toFloat()
                if (dots) {
                    val localX = b.left + x + .5f - region.left
                    val dotX = localX % spacing - spacing / 2f
                    val dot = ((dotRadius * dotRadius - dotX * dotX - dotY * dotY) / dotRadius)
                        .coerceIn(0f, 1f)
                    blurred += (235f - blurred).coerceAtLeast(0f) * .36f * dot
                }
                plane[index] = (original + (blurred - original) * coverage).toInt().toByte()
            }
        }
    }
}
