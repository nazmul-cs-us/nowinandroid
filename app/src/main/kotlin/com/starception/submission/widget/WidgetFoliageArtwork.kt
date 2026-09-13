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
import kotlin.math.PI
import kotlin.math.sin

/** Time-aware foliage palette used by the reference-style tall widget. */
internal enum class WidgetDayPhase {
    DAWN,
    DAY,
    AFTERNOON,
    SUNSET,
    NIGHT,
}

internal enum class WidgetFoliagePlacement {
    BOTH,
    RIGHT,
    HERO_RIGHT,
}

/**
 * Produces the transparent frames used by the widget's gently swaying corner leaves.
 *
 * App widgets have no Compose animation clock after RemoteViews crosses into the launcher.
 * The frames therefore contain the tiny changes in branch angle and a ViewFlipper advances
 * them. They are cached per palette so repeated widget updates do not redraw the bitmaps.
 */
internal object WidgetFoliageArtwork {

    private const val FRAME_COUNT = 8
    // Rasterise at half the logical drawing size. The curves stay clean when the launcher
    // scales them, while eight ARGB frames remain comfortably below RemoteViews' bitmap
    // memory ceiling even when two decorated cards are present.
    private const val BITMAP_WIDTH = 320
    private const val BITMAP_HEIGHT = 128
    private const val LOGICAL_WIDTH = 640
    private const val LOGICAL_HEIGHT = 256

    private val frameCache = mutableMapOf<Pair<WidgetDayPhase, WidgetFoliagePlacement>, List<Bitmap>>()

    @Synchronized
    fun frames(
        phase: WidgetDayPhase,
        placement: WidgetFoliagePlacement,
    ): List<Bitmap> = frameCache.getOrPut(phase to placement) {
        List(FRAME_COUNT) { frame -> drawFrame(phase, placement, frame) }
    }

    private fun drawFrame(
        phase: WidgetDayPhase,
        placement: WidgetFoliagePlacement,
        frame: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(
            BITMAP_WIDTH.toFloat() / LOGICAL_WIDTH,
            BITMAP_HEIGHT.toFloat() / LOGICAL_HEIGHT,
        )
        val palette = paletteFor(phase)
        val cycle = frame.toDouble() / FRAME_COUNT.toDouble() * PI * 2.0
        val mainSway = (sin(cycle) * 2.25).toFloat()
        val secondarySway = (sin(cycle + PI / 2.0) * 1.35).toFloat()

        if (placement == WidgetFoliagePlacement.BOTH) {
            drawPlant(
                canvas = canvas,
                rootX = -8f,
                rootY = LOGICAL_HEIGHT + 5f,
                mirrored = false,
                sway = mainSway,
                secondarySway = secondarySway,
                palette = palette,
            )
        }
        if (placement == WidgetFoliagePlacement.HERO_RIGHT) {
            drawHeroPlant(
                canvas = canvas,
                sway = -secondarySway,
                secondarySway = -mainSway,
                palette = palette,
            )
        } else {
            drawPlant(
                canvas = canvas,
                rootX = LOGICAL_WIDTH + 24f,
                rootY = LOGICAL_HEIGHT + 4f,
                mirrored = true,
                sway = -secondarySway,
                secondarySway = -mainSway,
                palette = palette,
            )
        }
        return bitmap
    }

    /** Larger, fewer eucalyptus leaves matching the hero's botanical quote panel. */
    private fun drawHeroPlant(
        canvas: Canvas,
        sway: Float,
        secondarySway: Float,
        palette: FoliagePalette,
    ) {
        val rootX = 592f
        val rootY = LOGICAL_HEIGHT + 5f
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.stem
            alpha = 145
            style = Paint.Style.STROKE
            strokeWidth = 2.7f
            strokeCap = Paint.Cap.ROUND
        }
        val bases = listOf(
            574f to 235f,
            580f to 220f,
            588f to 207f,
            597f to 198f,
            605f to 222f,
        )
        bases.forEachIndexed { index, (baseX, baseY) ->
            canvas.drawPath(
                Path().apply {
                    moveTo(rootX, rootY)
                    cubicTo(
                        rootX + (index - 2) * 3f,
                        244f,
                        baseX + (2 - index) * 2f,
                        baseY + 8f,
                        baseX,
                        baseY,
                    )
                },
                stemPaint,
            )
        }
        val leaves = listOf(
            LeafSpec(574f, 235f, 104f, 25f, -155f, palette.highlight),
            LeafSpec(580f, 220f, 100f, 25f, -134f, palette.primary),
            LeafSpec(588f, 207f, 95f, 25f, -110f, palette.deep),
            LeafSpec(597f, 198f, 92f, 25f, -91f, palette.secondary),
            LeafSpec(605f, 222f, 80f, 23f, -63f, palette.primary),
        )
        leaves.forEachIndexed { index, leaf ->
            drawLeaf(
                canvas = canvas,
                spec = leaf.copy(
                    angle = leaf.angle + sway * 0.24f +
                        secondarySway * (0.10f + index * 0.025f),
                ),
                vein = palette.vein,
                alpha = 202,
            )
        }
    }

    private fun drawPlant(
        canvas: Canvas,
        rootX: Float,
        rootY: Float,
        mirrored: Boolean,
        sway: Float,
        secondarySway: Float,
        palette: FoliagePalette,
    ) {
        canvas.save()
        canvas.translate(rootX, rootY)
        if (mirrored) canvas.scale(-1f, 1f)
        canvas.rotate(sway)
        // Keep the leaves at the card edges, where they decorate rather than compete
        // with prayer names and devotional copy.
        canvas.scale(0.82f, 0.82f)

        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.stem
            alpha = 158
            style = Paint.Style.STROKE
            strokeWidth = 3.2f
            strokeCap = Paint.Cap.ROUND
        }
        val stem = Path().apply {
            moveTo(2f, 2f)
            cubicTo(24f, -35f, 35f, -91f, 66f, -157f)
        }
        canvas.drawPath(stem, stemPaint)

        val branches = listOf(
            LeafSpec(14f, -25f, 34f, 13f, -50f, palette.deep),
            LeafSpec(21f, -42f, 40f, 14f, 20f, palette.primary),
            LeafSpec(28f, -63f, 45f, 15f, -43f, palette.primary),
            LeafSpec(37f, -82f, 43f, 14f, 25f, palette.secondary),
            LeafSpec(45f, -105f, 48f, 15f, -39f, palette.deep),
            LeafSpec(54f, -128f, 43f, 14f, 28f, palette.primary),
            LeafSpec(64f, -153f, 50f, 16f, -24f, palette.highlight),
        )
        branches.forEachIndexed { index, leaf ->
            drawLeaf(
                canvas = canvas,
                spec = leaf.copy(angle = leaf.angle + secondarySway * (0.18f + index * 0.035f)),
                vein = palette.vein,
            )
        }
        canvas.restore()
    }

    private fun drawLeaf(
        canvas: Canvas,
        spec: LeafSpec,
        vein: Int,
        alpha: Int = 172,
    ) {
        canvas.save()
        canvas.translate(spec.x, spec.y)
        canvas.rotate(spec.angle)

        val leafPath = Path().apply {
            moveTo(0f, 0f)
            cubicTo(
                spec.length * 0.28f,
                -spec.width * 1.08f,
                spec.length * 0.75f,
                -spec.width * 0.78f,
                spec.length,
                0f,
            )
            cubicTo(
                spec.length * 0.76f,
                spec.width * 0.66f,
                spec.length * 0.34f,
                spec.width * 0.92f,
                0f,
                0f,
            )
            close()
        }
        canvas.drawPath(
            leafPath,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    -spec.width,
                    spec.length,
                    spec.width * 0.5f,
                    intArrayOf(
                        blend(spec.color, Color.WHITE, 0.18f),
                        spec.color,
                        blend(spec.color, Color.BLACK, 0.20f),
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP,
                )
                this.alpha = alpha
                style = Paint.Style.FILL
            },
        )
        canvas.drawLine(
            3f,
            0f,
            spec.length * 0.86f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = vein
                alpha = 82
                strokeWidth = 1.1f
                strokeCap = Paint.Cap.ROUND
            },
        )
        canvas.restore()
    }

    private fun blend(from: Int, to: Int, amount: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * amount).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * amount).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount).toInt(),
    )

    private fun paletteFor(phase: WidgetDayPhase): FoliagePalette = when (phase) {
        WidgetDayPhase.DAWN -> FoliagePalette(
            stem = Color.rgb(42, 91, 78),
            deep = Color.rgb(20, 86, 70),
            primary = Color.rgb(58, 119, 92),
            secondary = Color.rgb(109, 150, 117),
            highlight = Color.rgb(190, 156, 87),
            vein = Color.rgb(230, 220, 181),
        )
        WidgetDayPhase.DAY -> FoliagePalette(
            stem = Color.rgb(21, 84, 65),
            deep = Color.rgb(7, 74, 59),
            primary = Color.rgb(25, 107, 76),
            secondary = Color.rgb(76, 137, 98),
            highlight = Color.rgb(126, 164, 118),
            vein = Color.rgb(217, 231, 208),
        )
        WidgetDayPhase.AFTERNOON -> FoliagePalette(
            stem = Color.rgb(50, 86, 58),
            deep = Color.rgb(23, 76, 57),
            primary = Color.rgb(70, 112, 70),
            secondary = Color.rgb(128, 139, 77),
            highlight = Color.rgb(192, 155, 77),
            vein = Color.rgb(238, 218, 168),
        )
        WidgetDayPhase.SUNSET -> FoliagePalette(
            stem = Color.rgb(30, 73, 63),
            deep = Color.rgb(8, 67, 58),
            primary = Color.rgb(37, 102, 77),
            secondary = Color.rgb(121, 92, 72),
            highlight = Color.rgb(188, 115, 69),
            vein = Color.rgb(244, 204, 158),
        )
        WidgetDayPhase.NIGHT -> FoliagePalette(
            stem = Color.rgb(21, 55, 64),
            deep = Color.rgb(8, 49, 57),
            primary = Color.rgb(22, 72, 77),
            secondary = Color.rgb(44, 88, 101),
            highlight = Color.rgb(93, 117, 132),
            vein = Color.rgb(188, 205, 206),
        )
    }

    private data class LeafSpec(
        val x: Float,
        val y: Float,
        val length: Float,
        val width: Float,
        val angle: Float,
        val color: Int,
    )

    private data class FoliagePalette(
        val stem: Int,
        val deep: Int,
        val primary: Int,
        val secondary: Int,
        val highlight: Int,
        val vein: Int,
    )
}
