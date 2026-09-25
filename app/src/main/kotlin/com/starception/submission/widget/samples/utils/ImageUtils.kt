/*
 * Copyright 2026 The Android Open Source Project
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

package com.starception.submission.widget.samples.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ImageUtils {

    /**
     * Loads the image resource as bitmap by scaling for the requested size.
     * https://developer.android.com/topic/performance/graphics/load-bitmap#load-bitmap
     */
    fun decodeSampledBitmapFromResource(
        res: Resources,
        resId: Int,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Bitmap {
        fun calculateInSampleSize(originalSize: Size, requestedSize: Size): Int {
            val (height: Int, width: Int) = originalSize.height to originalSize.width
            var inSampleSize = 1
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (height / inSampleSize > requestedSize.height && width / inSampleSize > requestedSize.width) {
                inSampleSize *= 2
            }
            return inSampleSize
        }

        // First decode with inJustDecodeBounds=true to check dimensions
        return BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeResource(res, resId, this)
            val originalWidth: Int = outWidth
            val originalHeight: Int = outHeight

            inSampleSize =
                calculateInSampleSize(
                    originalSize = Size(originalWidth, originalHeight),
                    requestedSize = Size(requestedWidth, requestedHeight),
                )

            // Decode bitmap with inSampleSize set
            inJustDecodeBounds = false

            BitmapFactory.decodeResource(res, resId, this)
        }
    }

    /**
     * Returns maximum bytes allowed per the appwidget limits for bitmaps in the remote views.
     */
    fun Context.getMaxWidgetMemoryAllowedSizeInBytes(): Int {
        val size = resources.displayMetrics.run { Size(widthPixels, heightPixels) }
        // Cap memory usage at 1.5 times the size of the display
        // 1.5 * 4 bytes/pixel * w * h ==> 6 * w * h
        // See https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/appwidget/java/com/android/server/appwidget/AppWidgetServiceImpl.java;l=274-281;drc=389cb6f54a5a5bb8dea540f57a3a8ac3c3c1c758
        return 6 * size.width * size.height
    }

    /**
     * Returns maximum possible size for each image in the widget when provided as bitmap.
     */
    fun getMaxPossibleImageSize(aspectRatio: Double, memoryLimitBytes: Int, maxImages: Int): Size {
        // for each orientation (landscape, portrait, +2 for fold).
        val limit = (memoryLimitBytes / 4) / maxImages
        val maxSizeAllowedPerPixel: Int = limit / BYTES_PER_PIXEL

        val side = sqrt(maxSizeAllowedPerPixel.toDouble()).toInt()
        val width = if (aspectRatio > 1) side else max(1, (side * aspectRatio).roundToInt())
        val height = if (aspectRatio > 1) max(1, (side / aspectRatio).roundToInt()) else side
        return Size(width, height)
    }

    private const val BYTES_PER_PIXEL = 4
}
