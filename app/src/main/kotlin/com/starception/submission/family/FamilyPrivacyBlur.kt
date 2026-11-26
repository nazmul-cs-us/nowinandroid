package com.starception.submission.family

internal data class PrivacyRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun clamped(width: Int, height: Int) = PrivacyRegion(left.coerceIn(0, width), top.coerceIn(0, height),
        right.coerceIn(0, width), bottom.coerceIn(0, height))
}

/** Three separable box passes approximate a strong Gaussian blur, including the chroma planes. */
internal object FamilyPrivacyBlur {
    fun apply(plane: ByteArray, width: Int, height: Int, region: PrivacyRegion, radius: Int) {
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
        for (y in 0 until h) for (x in 0 until w) plane[(b.top + y) * width + b.left + x] = pixels[y * w + x].toByte()
    }
}
