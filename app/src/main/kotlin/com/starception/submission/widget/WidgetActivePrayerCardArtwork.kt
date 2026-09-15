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
            Canvas(output).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }
}
