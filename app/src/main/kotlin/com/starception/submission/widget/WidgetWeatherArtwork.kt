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

package com.starception.submission.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * The header's weather glyphs, drawn to match the supplied reference: a warm radial sun
 * peeking over a soft white-to-blue cloud, with the same cloud carrying rain, snow, a
 * storm bolt or fog for the other conditions, and a crescent for clear nights.
 *
 * Painted rather than bundled because no icon set on hand shared this rendering — the
 * Flaticon PNGs are flat and mix palettes, and the Meteocon frames are line art.
 */
internal object WidgetWeatherArtwork {

    enum class Condition {
        CLEAR_DAY,
        CLEAR_NIGHT,
        PARTLY_CLOUDY_DAY,
        PARTLY_CLOUDY_NIGHT,
        CLOUDY,
        FOG,
        RAIN,
        SNOW,
        STORM,
    }

    /** WMO weather code (as Open-Meteo reports it) to the glyph that represents it. */
    fun condition(weatherCode: Int, isDay: Boolean): Condition = when (weatherCode) {
        0 -> if (isDay) Condition.CLEAR_DAY else Condition.CLEAR_NIGHT
        1, 2 -> if (isDay) Condition.PARTLY_CLOUDY_DAY else Condition.PARTLY_CLOUDY_NIGHT
        3 -> Condition.CLOUDY
        45, 48 -> Condition.FOG
        in 51..67, in 80..82 -> Condition.RAIN
        in 71..77, 85, 86 -> Condition.SNOW
        in 95..99 -> Condition.STORM
        else -> Condition.CLOUDY
    }

    private val cache = HashMap<Condition, Bitmap>()

    @Synchronized
    fun bitmap(condition: Condition, sizePx: Int = SIZE): Bitmap = cache.getOrPut(condition) {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val u = sizePx / 96f
        when (condition) {
            Condition.CLEAR_DAY -> sun(canvas, cx = 48f * u, cy = 48f * u, r = 24f * u, u = u)
            Condition.CLEAR_NIGHT -> moon(canvas, cx = 50f * u, cy = 48f * u, r = 24f * u, u = u)
            Condition.PARTLY_CLOUDY_DAY -> {
                sun(canvas, cx = 38f * u, cy = 36f * u, r = 19f * u, u = u)
                cloud(canvas, left = 26f * u, top = 44f * u, width = 62f * u, u = u)
            }
            Condition.PARTLY_CLOUDY_NIGHT -> {
                moon(canvas, cx = 38f * u, cy = 36f * u, r = 18f * u, u = u)
                cloud(canvas, left = 26f * u, top = 44f * u, width = 62f * u, u = u)
            }
            Condition.CLOUDY -> {
                cloud(canvas, left = 30f * u, top = 24f * u, width = 54f * u, u = u, back = true)
                cloud(canvas, left = 10f * u, top = 42f * u, width = 62f * u, u = u)
            }
            Condition.FOG -> {
                cloud(canvas, left = 16f * u, top = 26f * u, width = 62f * u, u = u)
                fog(canvas, u)
            }
            Condition.RAIN -> {
                cloud(canvas, left = 16f * u, top = 22f * u, width = 64f * u, u = u)
                rain(canvas, u)
            }
            Condition.SNOW -> {
                cloud(canvas, left = 16f * u, top = 22f * u, width = 64f * u, u = u)
                snow(canvas, u)
            }
            Condition.STORM -> {
                cloud(canvas, left = 16f * u, top = 20f * u, width = 64f * u, u = u, stormy = true)
                bolt(canvas, u)
            }
        }
        bitmap
    }

    private const val SIZE = 96

    private fun sun(canvas: Canvas, cx: Float, cy: Float, r: Float, u: Float) {
        val rays = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5B83D.toInt()
            strokeWidth = 3.2f * u
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0 + 22.5)
            val inner = r + 4.5f * u
            val outer = r + 10.5f * u
            canvas.drawLine(
                cx + (cos(angle) * inner).toFloat(),
                cy + (sin(angle) * inner).toFloat(),
                cx + (cos(angle) * outer).toFloat(),
                cy + (sin(angle) * outer).toFloat(),
                rays,
            )
        }
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - r * 0.25f,
                cy - r * 0.25f,
                r * 1.15f,
                intArrayOf(0xFFFFD86A.toInt(), 0xFFF7B24A.toInt(), 0xFFEF9A2F.toInt()),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, r, disc)
    }

    private fun moon(canvas: Canvas, cx: Float, cy: Float, r: Float, u: Float) {
        val layer = canvas.saveLayer(null, null)
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - r * 0.3f,
                cy - r * 0.3f,
                r * 1.2f,
                intArrayOf(0xFFF9EBB5.toInt(), 0xFFEFD37F.toInt(), 0xFFE2BC5A.toInt()),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, r, disc)
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        canvas.drawCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.82f, cut)
        canvas.restoreToCount(layer)
    }

    /**
     * A cloud is three lobes on a rounded base. [width] sets the footprint; height follows.
     * [back] paints the paler, bluer cloud that sits behind another; [stormy] darkens it.
     */
    private fun cloud(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        u: Float,
        back: Boolean = false,
        stormy: Boolean = false,
    ) {
        val height = width * 0.62f
        val bottom = top + height
        val path = Path().apply {
            addCircle(left + width * 0.30f, top + height * 0.48f, height * 0.36f, Path.Direction.CW)
            addCircle(left + width * 0.55f, top + height * 0.36f, height * 0.44f, Path.Direction.CW)
            addCircle(left + width * 0.78f, top + height * 0.56f, height * 0.30f, Path.Direction.CW)
            addRoundRect(
                RectF(left + width * 0.08f, top + height * 0.52f, left + width * 0.98f, bottom),
                height * 0.24f,
                height * 0.24f,
                Path.Direction.CW,
            )
        }
        val colors = when {
            stormy -> intArrayOf(0xFFB9C2D4.toInt(), 0xFF8E9BB5.toInt(), 0xFF6F7D99.toInt())
            back -> intArrayOf(0xFFE3ECF8.toInt(), 0xFFC6D8F0.toInt(), 0xFFB2C8E6.toInt())
            else -> intArrayOf(0xFFFFFFFF.toInt(), 0xFFE6EFFA.toInt(), 0xFFBED5F0.toInt())
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                top,
                0f,
                bottom,
                colors,
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        // A whisper of shadow under the cloud lifts it off whatever it is drawn over.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1F4A6A9A }
        canvas.save()
        canvas.translate(0f, 1.6f * u)
        canvas.drawPath(path, shadow)
        canvas.restore()
        canvas.drawPath(path, fill)
    }

    private fun rain(canvas: Canvas, u: Float) {
        val drop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6FA7E0.toInt()
            strokeWidth = 4f * u
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 3) {
            val x = 34f * u + i * 14f * u
            canvas.drawLine(x, 66f * u, x - 4f * u, 80f * u, drop)
        }
    }

    private fun snow(canvas: Canvas, u: Float) {
        val flake = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCFE0F5.toInt() }
        for (i in 0 until 3) {
            canvas.drawCircle(34f * u + i * 14f * u, 72f * u + (i % 2) * 5f * u, 3.6f * u, flake)
        }
    }

    private fun fog(canvas: Canvas, u: Float) {
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC3D3EA.toInt()
            strokeWidth = 4f * u
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(22f * u, 70f * u, 74f * u, 70f * u, band)
        canvas.drawLine(30f * u, 80f * u, 66f * u, 80f * u, band)
    }

    private fun bolt(canvas: Canvas, u: Float) {
        val path = Path().apply {
            moveTo(52f * u, 52f * u)
            lineTo(42f * u, 70f * u)
            lineTo(50f * u, 70f * u)
            lineTo(45f * u, 86f * u)
            lineTo(58f * u, 66f * u)
            lineTo(50f * u, 66f * u)
            lineTo(56f * u, 52f * u)
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                52f * u,
                0f,
                86f * u,
                intArrayOf(0xFFFFD86A.toInt(), 0xFFF5A623.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(path, fill)
    }
}
