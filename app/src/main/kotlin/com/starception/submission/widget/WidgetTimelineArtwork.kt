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
import android.graphics.Paint
import android.graphics.Path

/** Soft journey line and colored milestones behind the five prayer illustrations. */
internal object WidgetTimelineArtwork {

    val bitmap: Bitmap by lazy {
        Bitmap.createBitmap(640, 224, Bitmap.Config.ARGB_8888).also(::draw)
    }

    private fun draw(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val line = Path().apply {
            moveTo(34f, 118f)
            cubicTo(70f, 42f, 99f, 42f, 126f, 120f)
            cubicTo(164f, 174f, 207f, 39f, 250f, 120f)
            cubicTo(292f, 175f, 344f, 34f, 386f, 116f)
            cubicTo(430f, 174f, 480f, 43f, 518f, 120f)
            cubicTo(551f, 164f, 581f, 73f, 608f, 108f)
        }
        canvas.drawPath(
            line,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 190
                style = Paint.Style.STROKE
                strokeWidth = 7f
                strokeCap = Paint.Cap.ROUND
            },
        )
        canvas.drawPath(
            line,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(174, 204, 194)
                alpha = 238
                style = Paint.Style.STROKE
                strokeWidth = 3.8f
                strokeCap = Paint.Cap.ROUND
            },
        )

        val milestones = listOf(
            Triple(126f, 120f, Color.rgb(207, 165, 45)),
            Triple(250f, 120f, Color.rgb(225, 126, 84)),
            Triple(386f, 116f, Color.rgb(13, 92, 75)),
            Triple(518f, 120f, Color.rgb(41, 112, 178)),
        )
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 215
        }
        milestones.forEach { (x, y, color) ->
            canvas.drawCircle(x, y, 7f, haloPaint)
            canvas.drawCircle(
                x,
                y,
                4.5f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
            )
        }
    }
}
