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
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import java.util.concurrent.ConcurrentHashMap

/**
 * Preserves the organic silhouette painted into each prayer-phase illustration.
 *
 * Four of the source PNGs predate transparent export and contain a warm-white canvas.
 * Glance previously hid that canvas with a circular corner radius, but doing so also cut
 * away the distinctive arch/pebble outline used by the reference. We remove only the
 * near-white canvas connected to an image edge and cache the resulting bitmap instead.
 * Edge-connected removal is important: bright moons, suns and clouds inside the artwork
 * remain untouched.
 */
internal object WidgetPrayerPhaseArtwork {
    private val cache = ConcurrentHashMap<Int, Bitmap>()

    fun bitmap(context: Context, @DrawableRes resourceId: Int): Bitmap =
        cache.getOrPut(resourceId) {
            val source = requireNotNull(BitmapFactory.decodeResource(context.resources, resourceId)) {
                "Unable to decode prayer phase artwork $resourceId"
            }
            removeConnectedCanvas(source)
        }

    private fun removeConnectedCanvas(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val canvasPixels = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var head = 0
        var tail = 0

        fun enqueue(index: Int) {
            if (!canvasPixels[index] && isWarmWhiteCanvas(pixels[index])) {
                canvasPixels[index] = true
                queue[tail++] = index
            }
        }

        for (x in 0 until width) {
            enqueue(x)
            enqueue((height - 1) * width + x)
        }
        for (y in 0 until height) {
            enqueue(y * width)
            enqueue(y * width + width - 1)
        }

        while (head < tail) {
            val index = queue[head++]
            val x = index % width
            val y = index / width
            if (x > 0) enqueue(index - 1)
            if (x + 1 < width) enqueue(index + 1)
            if (y > 0) enqueue(index - width)
            if (y + 1 < height) enqueue(index + width)
        }

        pixels.indices.forEach { index ->
            if (canvasPixels[index]) pixels[index] = pixels[index] and 0x00FFFFFF
        }

        val cleaned = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output ->
            output.setPixels(pixels, 0, width, 0, 0, width, height)
        }

        // The source exports use different amounts of surrounding canvas. Cropping to
        // the painted alpha bounds makes a 42dp Dhuhr illustration occupy the same visual
        // area as a 42dp Fajr illustration instead of appearing one-third smaller.
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        pixels.forEachIndexed { index, pixel ->
            if ((pixel ushr 24 and 0xFF) > 8) {
                val x = index % width
                val y = index / width
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
        if (maxX < minX || maxY < minY) return cleaned

        val padding = (minOf(width, height) * 0.015f).toInt().coerceAtLeast(2)
        val cropLeft = (minX - padding).coerceAtLeast(0)
        val cropTop = (minY - padding).coerceAtLeast(0)
        val cropRight = (maxX + padding + 1).coerceAtMost(width)
        val cropBottom = (maxY + padding + 1).coerceAtMost(height)
        return Bitmap.createBitmap(
            cleaned,
            cropLeft,
            cropTop,
            cropRight - cropLeft,
            cropBottom - cropTop,
        )
    }

    private fun isWarmWhiteCanvas(pixel: Int): Boolean {
        val alpha = pixel ushr 24 and 0xFF
        if (alpha == 0) return true
        val red = pixel ushr 16 and 0xFF
        val green = pixel ushr 8 and 0xFF
        val blue = pixel and 0xFF
        val darkest = minOf(red, green, blue)
        val lightest = maxOf(red, green, blue)
        return darkest >= 238 && lightest - darkest <= 22
    }
}
