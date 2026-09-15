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
import android.graphics.Path
import android.graphics.Shader

/** Layered forest surface behind the active prayer, matching the supplied reference. */
internal object WidgetActivePrayerCardArtwork {
    val bitmap: Bitmap by lazy {
        val width = 256
        val height = 512
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    height.toFloat(),
                    intArrayOf(
                        Color.rgb(214, 233, 224),
                        Color.rgb(79, 143, 118),
                        Color.rgb(39, 113, 91),
                        Color.rgb(10, 72, 61),
                    ),
                    floatArrayOf(0f, 0.34f, 0.72f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            val card = Path().apply {
                // The reference grows the selected prayer out of the landscape as a
                // rounded hill, not as a rectangle placed on top of it.
                moveTo(0f, height * 0.22f)
                cubicTo(
                    10f,
                    height * 0.11f,
                    width * 0.22f,
                    height * 0.015f,
                    width * 0.50f,
                    0f,
                )
                cubicTo(
                    width * 0.78f,
                    height * 0.015f,
                    width - 10f,
                    height * 0.11f,
                    width.toFloat(),
                    height * 0.22f,
                )
                lineTo(width.toFloat(), height * 0.88f)
                cubicTo(
                    width.toFloat(),
                    height * 0.965f,
                    width * 0.90f,
                    height.toFloat(),
                    width * 0.78f,
                    height.toFloat(),
                )
                lineTo(width * 0.22f, height.toFloat())
                cubicTo(
                    width * 0.10f,
                    height.toFloat(),
                    0f,
                    height * 0.965f,
                    0f,
                    height * 0.88f,
                )
                close()
            }
            Canvas(output).drawPath(card, paint)
        }
    }
}
