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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/** Smooth sunrise-to-night track used by the reference prayer timeline footer. */
internal object WidgetDaylightBarArtwork {
    val bitmap: Bitmap by lazy {
        val width = 768
        val height = 24
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    0f,
                    intArrayOf(
                        Color.rgb(250, 222, 126),
                        Color.rgb(246, 176, 91),
                        Color.rgb(231, 112, 105),
                        Color.rgb(131, 128, 191),
                        Color.rgb(49, 68, 145),
                    ),
                    floatArrayOf(0f, 0.24f, 0.50f, 0.73f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            Canvas(output).drawRoundRect(
                RectF(0f, 2f, width.toFloat(), height - 2f),
                10f,
                10f,
                paint,
            )
        }
    }
}
