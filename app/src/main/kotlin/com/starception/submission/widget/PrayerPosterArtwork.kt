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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.format.DateFormat
import androidx.core.content.res.ResourcesCompat
import com.starception.submission.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import com.starception.submission.core.designsystem.R as DesignR

/**
 * The portrait "Closer to Allah" treatment for the standalone Today's Prayers widget.
 *
 * Glance cannot draw a path, use custom fonts reliably, or blur the scene behind a glass
 * panel. Rendering the complete poster into one bitmap keeps the reference's typography,
 * prayer arc and translucent furniture pixel-aligned at every launcher size. Live values
 * are painted on top of clean day/night scene assets; no user data is baked into them.
 */
internal object PrayerPosterArtwork {

    private const val DESIGN_WIDTH = 710f
    private const val HEADER_HEIGHT_FRACTION = 0.101f
    private const val MAX_BITMAP_PIXELS = 2_000_000f
    private const val SCENE_CROP_BIAS_Y = 0.70f

    // Normalized source-space boundaries split the distant mosque into independently
    // illuminated architectural groups. Boundaries sit between minarets/domes so an
    // unlit group reads as switched-off rooms rather than a hard slice through a lamp.
    private val MOSQUE_LIGHT_GROUP_X = floatArrayOf(0.34f, 0.47f, 0.58f, 0.70f, 0.81f, 0.91f)
    private const val MOSQUE_LIGHT_TOP = 0.47f
    private const val MOSQUE_LIGHT_BOTTOM = 0.84f

    private val artworkCache = HashMap<Int, Bitmap>()

    /** Independent mosque illumination for the night artwork. */
    data class Lighting(
        val mosqueLightIntensity: Float = 1f,
    )

    fun isSuitable(widthDp: Float, heightDp: Float): Boolean =
        widthDp >= 250f && heightDp / widthDp >= 1.08f

    fun render(
        context: Context,
        state: PrayerWidgetState.Available,
        widthDp: Float,
        heightDp: Float,
        includeHeader: Boolean = true,
        isNightOverride: Boolean? = null,
        lighting: Lighting = Lighting(),
    ): Bitmap {
        val metrics = context.resources.displayMetrics
        val requestedScale = min(metrics.density, 3f)
        val pixelGuard = sqrt(MAX_BITMAP_PIXELS / (widthDp * heightDp)).coerceAtMost(1f)
        val scale = requestedScale * pixelGuard
        val width = (widthDp * scale).toInt().coerceAtLeast(1)
        val height = (heightDp * scale).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val radius = width * 0.055f
            canvas.clipPath(
                Path().apply {
                    addRoundRect(
                        RectF(0f, 0f, width.toFloat(), height.toFloat()),
                        radius,
                        radius,
                        Path.Direction.CW,
                    )
                },
            )
            if (includeHeader) {
                Renderer(
                    context,
                    state,
                    canvas,
                    bitmap,
                    width.toFloat(),
                    height.toFloat(),
                    isNightOverride,
                    lighting,
                    0f,
                ).draw(
                    includeHeader = true,
                )
            } else {
                // The standalone widget now uses the exact same Glance header as the hero
                // widget. Paint the remainder as a crop of the original poster coordinate
                // system so every body landmark keeps its reference position after that
                // shared header has consumed the top strip.
                val virtualHeight = height / (1f - HEADER_HEIGHT_FRACTION)
                canvas.save()
                canvas.translate(0f, -virtualHeight * HEADER_HEIGHT_FRACTION)
                Renderer(
                    context,
                    state,
                    canvas,
                    bitmap,
                    width.toFloat(),
                    virtualHeight,
                    isNightOverride,
                    lighting,
                    virtualHeight * HEADER_HEIGHT_FRACTION,
                ).draw(
                    includeHeader = false,
                )
                canvas.restore()
            }
        }
    }

    /** Scene-only treatment for in-app cards that provide their own text and controls. */
    fun renderScene(
        context: Context,
        state: PrayerWidgetState.Available,
        widthDp: Float,
        heightDp: Float,
        isNightOverride: Boolean? = null,
        lighting: Lighting = Lighting(),
    ): Bitmap {
        val metrics = context.resources.displayMetrics
        val requestedScale = min(metrics.density, 3f)
        val pixelGuard = sqrt(MAX_BITMAP_PIXELS / (widthDp * heightDp)).coerceAtMost(1f)
        val scale = requestedScale * pixelGuard
        val width = (widthDp * scale).toInt().coerceAtLeast(1)
        val height = (heightDp * scale).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val radius = width * 0.055f
            canvas.clipPath(
                Path().apply {
                    addRoundRect(
                        RectF(0f, 0f, width.toFloat(), height.toFloat()),
                        radius,
                        radius,
                        Path.Direction.CW,
                    )
                },
            )
            Renderer(
                context,
                state,
                canvas,
                bitmap,
                width.toFloat(),
                height.toFloat(),
                isNightOverride,
                lighting,
                0f,
            ).drawSceneOnly()
        }
    }

    /** Reference treatment adapted to the wide middle card in the combined prayer widget. */
    fun renderTimelineCard(
        context: Context,
        state: PrayerWidgetState.Available,
        widthDp: Float,
        heightDp: Float,
        isNightOverride: Boolean? = null,
        lighting: Lighting = Lighting(),
    ): Bitmap {
        val metrics = context.resources.displayMetrics
        val requestedScale = min(metrics.density, 3f)
        val pixelGuard = sqrt(MAX_BITMAP_PIXELS / (widthDp * heightDp)).coerceAtMost(1f)
        val scale = requestedScale * pixelGuard
        val width = (widthDp * scale).toInt().coerceAtLeast(1)
        val height = (heightDp * scale).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val radius = 22f * scale
            canvas.clipPath(
                Path().apply {
                    addRoundRect(
                        RectF(0f, 0f, width.toFloat(), height.toFloat()),
                        radius,
                        radius,
                        Path.Direction.CW,
                    )
                },
            )
            Renderer(
                context,
                state,
                canvas,
                bitmap,
                width.toFloat(),
                height.toFloat(),
                isNightOverride,
                lighting,
                0f,
            )
                .drawTimelineCard()
        }
    }

    private fun artwork(context: Context, resource: Int): Bitmap? = synchronized(artworkCache) {
        artworkCache[resource] ?: BitmapFactory.decodeResource(context.resources, resource)
            ?.also { artworkCache[resource] = it }
    }

    private class Renderer(
        private val context: Context,
        private val state: PrayerWidgetState.Available,
        private val canvas: Canvas,
        private val targetBitmap: Bitmap,
        private val width: Float,
        private val height: Float,
        isNightOverride: Boolean?,
        lighting: Lighting,
        private val bitmapSampleOffsetY: Float,
    ) {
        private val unit = width / DESIGN_WIDTH
        private val isNight = isNightOverride
            ?: (state.sky.now < state.sky.sunrise || state.sky.now >= state.sky.maghrib)
        private val mosqueLightIntensity = lighting.mosqueLightIntensity.coerceIn(0f, 1f)

        private val sans = font(DesignR.font.ubuntu_sans_regular, Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL))
        private val sansMedium = font(DesignR.font.ubuntu_sans_medium, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
        private val sansBold = font(DesignR.font.ubuntu_sans_bold, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
        private val serif = font(DesignR.font.amiri_regular, Typeface.create(Typeface.SERIF, Typeface.NORMAL))
        private val serifItalic = Typeface.create(serif, Typeface.ITALIC)
        // Amiri is beautiful for Arabic but its tall English glyphs looked vertically
        // stretched at launcher scale. The platform serif has more natural Latin
        // proportions and remains available when RemoteViews renders off-screen.
        private val editorialSerif = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

        private val ink = if (isNight) Color.rgb(246, 246, 242) else Color.rgb(9, 31, 43)
        private val mutedInk = if (isNight) Color.rgb(214, 222, 228) else Color.rgb(36, 58, 64)
        private val accent = if (isNight) Color.rgb(190, 225, 196) else Color.rgb(62, 112, 91)

        fun draw(includeHeader: Boolean) {
            drawScene()
            drawReadabilityVeils()
            drawMovingCelestial(RectF(0f, 0f, width, height))
            if (includeHeader) drawHeader()
            drawEditorialTitle()
            drawNextPrayerPanel()
            drawOuterHairline()
        }

        fun drawSceneOnly() {
            drawScene()
            drawMovingCelestial(
                target = RectF(0f, 0f, width, height),
                clipBehindMountain = true,
            )
            drawOuterHairline()
        }

        fun drawTimelineCard() {
            val ultraCompact = height / unit < 390f
            drawTimelineScene()
            drawCompactReadabilityVeils()
            drawMovingCelestial(RectF(0f, 0f, width, height))
            drawCompactEditorialTitle(ultraCompact = ultraCompact)
            drawMinimalNextPrayerInfo(ultraCompact = ultraCompact)
            drawOuterHairline()
        }

        private fun drawTimelineScene() {
            val source = artwork(context, sceneBaseResource()) ?: return
            val targetAspect = width / height
            val cropHeight = (source.width / targetAspect).toInt().coerceAtMost(source.height)
            // A shorter canvas needs more of the lower source region so the mosque rises
            // into the clear strip between the prayer arc and the glass panel.
            val centerY = (source.height * 0.64f).toInt()
            val top = (centerY - cropHeight / 2).coerceIn(0, source.height - cropHeight)
            val sourceRect = Rect(0, top, source.width, top + cropHeight)
            val targetRect = RectF(0f, 0f, width, height)
            canvas.drawBitmap(
                source,
                sourceRect,
                targetRect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            drawLightingLayer(sourceRect, targetRect)
        }

        private fun drawCompactReadabilityVeils() {
            canvas.drawRect(
                0f,
                0f,
                width,
                height * 0.53f,
                Paint().apply {
                    shader = LinearGradient(
                        0f,
                        0f,
                        0f,
                        height * 0.53f,
                        if (isNight) 0xA8081729.toInt() else 0xA8FFF9EF.toInt(),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP,
                    )
                },
            )
        }

        /** The portrait editorial block, reduced—not replaced—for a wide short canvas. */
        private fun drawCompactEditorialTitle(ultraCompact: Boolean) {
            if (ultraCompact) {
                drawTrackedText(
                    text = "A HIGHER TOMORROW",
                    x = 112f * unit,
                    baseline = 18f * unit,
                    paint = textPaint(5.8f, ink, sansMedium),
                    tracking = 2.45f * unit,
                )
                canvas.drawText(
                    "Closer to",
                    112f * unit,
                    49f * unit,
                    textPaint(22f, ink, editorialSerif),
                )
                canvas.drawText(
                    "Allah",
                    112f * unit,
                    79f * unit,
                    textPaint(27f, accent, editorialSerif),
                )
                return
            }
            drawTrackedText(
                text = "A HIGHER TOMORROW",
                x = 120f * unit,
                baseline = 27f * unit,
                paint = textPaint(7.3f, ink, sansMedium),
                tracking = 3.25f * unit,
            )
            canvas.drawText(
                "Closer to",
                120f * unit,
                66f * unit,
                textPaint(31f, ink, editorialSerif),
            )
            canvas.drawText(
                "Allah",
                120f * unit,
                105f * unit,
                textPaint(37f, accent, editorialSerif),
            )
            drawTrackedText(
                text = "SAME CREATOR  •  SAME SKY",
                x = 120f * unit,
                baseline = 128f * unit,
                paint = textPaint(5.8f, ink, sansMedium),
                tracking = 2.1f * unit,
            )
            drawTrackedText(
                text = "SAME JOURNEY  •  ONE UMMAH",
                x = 120f * unit,
                baseline = 141f * unit,
                paint = textPaint(5.8f, ink, sansMedium),
                tracking = 2.1f * unit,
            )

            val aside = textPaint(
                10f,
                if (isNight) Color.rgb(230, 226, 225) else Color.rgb(88, 78, 65),
                serifItalic,
            ).apply { textAlign = Paint.Align.CENTER }
            val asideX = width * 0.865f
            canvas.drawText("Different", asideX, 37f * unit, aside)
            canvas.drawText("Places", asideX, 52f * unit, aside)
            canvas.drawText("Same Sky", asideX, 67f * unit, aside)
            canvas.drawLine(
                asideX - 8f * unit,
                80f * unit,
                asideX + 8f * unit,
                80f * unit,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = aside.color
                    strokeWidth = unit
                },
            )
        }

        /** Same six live prayer nodes as the portrait poster, reflowed above its glass. */
        private fun drawCompactPrayerArc(ultraCompact: Boolean) {
            val prayerByName = state.prayers.associateBy { it.name.lowercase(Locale.ENGLISH) }
            val nodeFractions = if (ultraCompact) {
                listOf(0.30f, 0.42f, 0.54f, 0.66f, 0.78f, 0.90f)
            } else {
                listOf(0.145f, 0.275f, 0.485f, 0.635f, 0.755f, 0.875f)
            }
            val nodes = listOf(
                ArcNode("Fajr", prayerByName["fajr"]?.time.orEmpty(), nodeFractions[0]),
                ArcNode("Sunrise", formatMinute(state.sky.sunrise), nodeFractions[1]),
                ArcNode("Dhuhr", prayerByName["dhuhr"]?.time.orEmpty(), nodeFractions[2]),
                ArcNode("Asr", prayerByName["asr"]?.time.orEmpty(), nodeFractions[3]),
                ArcNode("Maghrib", prayerByName["maghrib"]?.time.orEmpty(), nodeFractions[4]),
                ArcNode("Isha", prayerByName["isha"]?.time.orEmpty(), nodeFractions[5]),
            )
            val startX = width * if (ultraCompact) 0.28f else 0.12f
            val endX = width * if (ultraCompact) 0.92f else 0.90f
            val panelTop = height - (if (ultraCompact) 142f else 187f) * unit
            val endY = if (ultraCompact) {
                (panelTop - 9f * unit).coerceAtLeast(105f * unit)
            } else {
                (panelTop - 22f * unit).coerceAtLeast(178f * unit)
            }
            val controlX = (startX + endX) / 2f
            val controlY = if (ultraCompact) {
                (endY - 42f * unit).coerceAtLeast(69f * unit)
            } else {
                (endY - 105f * unit).coerceAtLeast(88f * unit)
            }
            nodes.forEach { node ->
                val t = ((width * node.xFraction - startX) / (endX - startX)).coerceIn(0f, 1f)
                val oneMinus = 1f - t
                val x = oneMinus * oneMinus * startX +
                    2f * oneMinus * t * controlX + t * t * endX
                val y = oneMinus * oneMinus * endY +
                    2f * oneMinus * t * controlY + t * t * endY
                if (ultraCompact) {
                    canvas.drawText(
                        node.name,
                        x,
                        y - 24f * unit,
                        textPaint(7.7f, ink, sansMedium).apply { textAlign = Paint.Align.CENTER },
                    )
                } else {
                    canvas.drawText(
                        node.name,
                        x,
                        y - 39f * unit,
                        textPaint(10.5f, ink, sansMedium).apply { textAlign = Paint.Align.CENTER },
                    )
                    canvas.drawText(
                        node.time,
                        x,
                        y - 24f * unit,
                        textPaint(9.5f, ink, sansMedium).apply { textAlign = Paint.Align.CENTER },
                    )
                }
            }
        }

        private fun drawWidePrayerArc() {
            val prayerByName = state.prayers.associateBy { it.name.lowercase(Locale.ENGLISH) }
            val nodes = listOf(
                ArcNode("Fajr", prayerByName["fajr"]?.time.orEmpty(), 0.08f),
                ArcNode("Sunrise", formatMinute(state.sky.sunrise), 0.245f),
                ArcNode("Dhuhr", prayerByName["dhuhr"]?.time.orEmpty(), 0.41f),
                ArcNode("Asr", prayerByName["asr"]?.time.orEmpty(), 0.58f),
                ArcNode("Maghrib", prayerByName["maghrib"]?.time.orEmpty(), 0.755f),
                ArcNode("Isha", prayerByName["isha"]?.time.orEmpty(), 0.92f),
            )
            val startX = width * 0.07f
            val endX = width * 0.93f
            val endY = height * 0.60f
            val controlX = width * 0.50f
            val controlY = height * 0.27f
            nodes.forEach { node ->
                val t = ((width * node.xFraction - startX) / (endX - startX)).coerceIn(0f, 1f)
                val oneMinus = 1f - t
                val x = oneMinus * oneMinus * startX +
                    2f * oneMinus * t * controlX + t * t * endX
                val y = oneMinus * oneMinus * endY +
                    2f * oneMinus * t * controlY + t * t * endY
                val nameBaseline = y - 25f * unit
                val timeBaseline = y - 8f * unit
                canvas.drawText(
                    node.name,
                    x,
                    nameBaseline,
                    textPaint(16f, Color.WHITE, sansBold).apply { textAlign = Paint.Align.CENTER },
                )
                canvas.drawText(
                    node.time,
                    x,
                    timeBaseline,
                    textPaint(13.5f, 0xFFE2E8EC.toInt(), sansMedium).apply {
                        textAlign = Paint.Align.CENTER
                    },
                )
            }
        }

        private fun drawScene() {
            val source = artwork(context, sceneBaseResource()) ?: return
            // Keep the skyline's focal architecture clear of the foreground glass card.
            // A centred crop left only the minaret tips visible; biasing the source window
            // downward moves the mosque upward in the rendered poster without changing the
            // reference card geometry or prayer-arc coordinates.
            val src = sceneCrop(source, SCENE_CROP_BIAS_Y)
            val target = RectF(0f, 0f, width, height)
            canvas.drawBitmap(
                source,
                src,
                target,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            drawLightingLayer(
                source = src,
                target = target,
            )
        }

        private fun sceneCrop(source: Bitmap, verticalBias: Float): Rect {
            val sourceAspect = source.width.toFloat() / source.height
            val targetAspect = width / height
            return if (sourceAspect > targetAspect) {
                val cropWidth = (source.height * targetAspect).toInt()
                val left = (source.width - cropWidth) / 2
                Rect(left, 0, left + cropWidth, source.height)
            } else {
                val cropHeight = (source.width / targetAspect).toInt()
                val availableTravel = (source.height - cropHeight).coerceAtLeast(0)
                val top = (availableTravel * verticalBias.coerceIn(0f, 1f)).toInt()
                Rect(0, top, source.width, (top + cropHeight).coerceAtMost(source.height))
            }
        }

        private fun sceneBaseResource(): Int = if (isNight) {
            R.drawable.prayer_widget_poster_night_moonless_hq_v4
        } else {
            R.drawable.prayer_widget_poster_day_no_glare_hq_v4
        }

        private fun drawLightingLayer(source: Rect, target: RectF) {
            if (isNight && mosqueLightIntensity > 0f) {
                artwork(context, R.drawable.prayer_widget_night_mosque_light_overlay_hq_v3)?.let { layer ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        alpha = (mosqueLightIntensity * 255f).toInt().coerceIn(0, 255)
                    }
                    val groupCount = MOSQUE_LIGHT_GROUP_X.lastIndex
                    // Change only twice an hour. Two separated groups stay off while the
                    // other entrances and minarets remain warmly illuminated.
                    val pattern = Math.floorMod(state.sky.now / 30, groupCount)
                    val secondUnlitGroup = (pattern + 2) % groupCount

                    repeat(groupCount) { group ->
                        if (group != pattern && group != secondUnlitGroup) {
                            val clip = sourceNormalizedRectToTarget(
                                source = source,
                                target = target,
                                sourceWidth = layer.width,
                                sourceHeight = layer.height,
                                left = MOSQUE_LIGHT_GROUP_X[group],
                                top = MOSQUE_LIGHT_TOP,
                                right = MOSQUE_LIGHT_GROUP_X[group + 1],
                                bottom = MOSQUE_LIGHT_BOTTOM,
                            )
                            if (!clip.isEmpty) {
                                canvas.save()
                                canvas.clipRect(clip)
                                canvas.drawBitmap(layer, source, target, paint)
                                canvas.restore()
                            }
                        }
                    }
                }
            }
        }

        private fun sourceNormalizedRectToTarget(
            source: Rect,
            target: RectF,
            sourceWidth: Int,
            sourceHeight: Int,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
        ): RectF {
            fun mapX(normalized: Float): Float = target.left +
                ((normalized * sourceWidth - source.left) / source.width().toFloat()) * target.width()

            fun mapY(normalized: Float): Float = target.top +
                ((normalized * sourceHeight - source.top) / source.height().toFloat()) * target.height()

            return RectF(
                mapX(left).coerceIn(target.left, target.right),
                mapY(top).coerceIn(target.top, target.bottom),
                mapX(right).coerceIn(target.left, target.right),
                mapY(bottom).coerceIn(target.top, target.bottom),
            )
        }

        private fun drawMovingCelestial(
            target: RectF,
            clipBehindMountain: Boolean = false,
        ) {
            val mountainClipSaveCount = if (clipBehindMountain) {
                mountainSkyClip(target)?.let { skyClip ->
                    canvas.save().also { canvas.clipPath(skyClip) }
                }
            } else {
                null
            }
            val progress = if (isNight) {
                val nightEnd = state.sky.sunrise + 24 * 60
                val current = if (state.sky.now < state.sky.sunrise) {
                    state.sky.now + 24 * 60
                } else {
                    state.sky.now
                }
                ((current - state.sky.maghrib).toFloat() /
                    (nightEnd - state.sky.maghrib).coerceAtLeast(1)).coerceIn(0f, 1f)
            } else {
                ((state.sky.now - state.sky.sunrise).toFloat() /
                    (state.sky.maghrib - state.sky.sunrise).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
            val altitude = sin(progress * PI).toFloat().coerceAtLeast(0f)
            val x = target.left + target.width() * (0.17f + 0.66f * progress)
            val horizonY = target.top + target.height() * 0.60f
            // Cap the arc's lift by the artwork width. A purely height-relative zenith
            // sent the sun and moon far into the upper sky when a user made the widget
            // taller, even though the mountain ridge did not move by the same amount.
            // This keeps the body just above the ridge across wide and tall crops while
            // retaining the full sunrise-to-sunset horizontal cycle.
            val heightZenithY = target.top + target.height() * if (isNight) 0.17f else 0.15f
            val ridgeAnchoredZenithY = horizonY - target.width() * if (isNight) 0.26f else 0.28f
            val zenithY = maxOf(heightZenithY, ridgeAnchoredZenithY)
            val radius = min(target.width(), target.height()) * if (isNight) 0.066f else 0.058f
            val bodyRadius = radius * 0.90f
            val arcY = horizonY - (horizonY - zenithY) * altitude
            // The scene uses a different vertical crop as the card changes aspect ratio.
            // The body rides the mapped mountain ridge: at its horizontal position the
            // ridge height is sampled and the body sits perched on the peak edge —
            // most of the disc above, the rest clipped behind the mountain — in both
            // compact and expanded cards, at every hour. The arc only supplies the
            // horizontal sunrise-to-sunset cycle and a fallback when the ridge is
            // unavailable.
            val y = if (clipBehindMountain) {
                mountainRidgeY(target, x)?.let { ridgeY ->
                    ridgeY - bodyRadius * 0.55f
                } ?: minOf(arcY, target.top + target.height() * 0.60f)
            } else {
                arcY
            }
            val glowRadius = radius * if (isNight) 2.35f else 3.0f
            val glowColor = if (isNight) 0xFFD8E7FF.toInt() else 0xFFFFD65A.toInt()
            canvas.drawCircle(
                x,
                y,
                glowRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        x,
                        y,
                        glowRadius,
                        intArrayOf(
                            Color.argb(if (isNight) 105 else 170, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                            Color.argb(if (isNight) 42 else 72, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                            Color.TRANSPARENT,
                        ),
                        floatArrayOf(0f, 0.42f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            drawCircularCelestialTexture(
                resource = if (isNight) {
                    R.drawable.prayer_widget_moon_real_nasa_v1
                } else {
                    R.drawable.prayer_widget_sun_real_3d_v1
                },
                cx = x,
                cy = y,
                bodyRadius = bodyRadius,
                textureRadius = bodyRadius,
            )
            canvas.drawCircle(
                x,
                y,
                bodyRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = if (isNight) {
                        RadialGradient(
                            x - bodyRadius * 0.24f,
                            y - bodyRadius * 0.20f,
                            bodyRadius * 1.38f,
                            intArrayOf(Color.TRANSPARENT, 0x18354A66, 0x7A071321),
                            floatArrayOf(0f, 0.56f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                    } else {
                        RadialGradient(
                            x - bodyRadius * 0.18f,
                            y - bodyRadius * 0.16f,
                            bodyRadius * 1.18f,
                            // The texture already carries a luminous surface, so keep this
                            // highlight restrained rather than washing out its plasma detail.
                            intArrayOf(0x30FFF9D2, 0x10FFE589, Color.TRANSPARENT),
                            floatArrayOf(0f, 0.52f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                    }
                },
            )
            canvas.drawCircle(
                x,
                y,
                bodyRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = (if (isNight) 1.15f else 1.35f) * unit
                    color = if (isNight) 0x70EAF2FF else 0x72FFE18A
                },
            )
            mountainClipSaveCount?.let(canvas::restoreToCount)
        }

        private fun mountainSkyClip(target: RectF): Path? {
            val source = artwork(context, sceneBaseResource()) ?: return null
            val crop = sceneCrop(source, SCENE_CROP_BIAS_Y)
            // Source-normalized ridge samples follow the mountain silhouette in each
            // moonless/sunless scene. Clipping to the sky leaves the original mountain
            // pixels untouched, so the celestial body appears naturally behind the ridge.
            val ridge = mountainRidgePoints()
            fun mapX(normalized: Float): Float = target.left +
                ((normalized * source.width - crop.left) / crop.width().toFloat()) * target.width()
            fun mapY(normalized: Float): Float = target.top +
                ((normalized * source.height - crop.top) / crop.height().toFloat()) * target.height()

            return Path().apply {
                moveTo(target.left, target.top)
                lineTo(target.right, target.top)
                for (index in ridge.lastIndex - 1 downTo 0 step 2) {
                    lineTo(
                        mapX(ridge[index]).coerceIn(target.left, target.right),
                        mapY(ridge[index + 1]).coerceIn(target.top, target.bottom),
                    )
                }
                close()
            }
        }

        private fun mountainRidgeY(target: RectF, targetX: Float): Float? {
            val source = artwork(context, sceneBaseResource()) ?: return null
            val crop = sceneCrop(source, SCENE_CROP_BIAS_Y)
            val sourceX = crop.left +
                ((targetX - target.left) / target.width()).coerceIn(0f, 1f) * crop.width()
            val normalizedX = sourceX / source.width
            val ridge = mountainRidgePoints()
            for (index in 0 until ridge.lastIndex - 2 step 2) {
                val startX = ridge[index]
                val endX = ridge[index + 2]
                if (normalizedX in startX..endX) {
                    val segmentProgress = ((normalizedX - startX) / (endX - startX)).coerceIn(0f, 1f)
                    val normalizedY = ridge[index + 1] +
                        (ridge[index + 3] - ridge[index + 1]) * segmentProgress
                    return target.top +
                        ((normalizedY * source.height - crop.top) / crop.height()) * target.height()
                }
            }
            return null
        }

        private fun mountainRidgePoints(): FloatArray = if (isNight) {
            floatArrayOf(
                0.00f, 0.55f,
                0.10f, 0.55f,
                0.18f, 0.53f,
                0.28f, 0.56f,
                0.36f, 0.52f,
                0.46f, 0.55f,
                0.57f, 0.49f,
                0.64f, 0.47f,
                0.73f, 0.52f,
                0.84f, 0.55f,
                0.93f, 0.51f,
                1.00f, 0.48f,
            )
        } else {
            floatArrayOf(
                0.00f, 0.58f,
                0.10f, 0.57f,
                0.20f, 0.58f,
                0.30f, 0.56f,
                0.41f, 0.52f,
                0.50f, 0.57f,
                0.60f, 0.55f,
                0.70f, 0.52f,
                0.78f, 0.55f,
                0.86f, 0.51f,
                0.94f, 0.53f,
                1.00f, 0.49f,
            )
        }

        private fun drawCircularCelestialTexture(
            resource: Int,
            cx: Float,
            cy: Float,
            bodyRadius: Float,
            textureRadius: Float,
        ) {
            val celestial = artwork(context, resource) ?: return
            canvas.save()
            canvas.clipPath(
                Path().apply {
                    addCircle(cx, cy, bodyRadius, Path.Direction.CW)
                },
            )
            canvas.drawBitmap(
                celestial,
                null,
                RectF(
                    cx - textureRadius,
                    cy - textureRadius,
                    cx + textureRadius,
                    cy + textureRadius,
                ),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            canvas.restore()
        }

        /** Quiet zones retain the reference artwork while keeping small live type legible. */
        private fun drawReadabilityVeils() {
            val topColor = if (isNight) 0xB3081729.toInt() else 0xB8FFF9EF.toInt()
            canvas.drawRect(
                0f,
                height * 0.10f,
                width,
                height * 0.42f,
                Paint().apply {
                    shader = LinearGradient(
                        0f,
                        height * 0.10f,
                        0f,
                        height * 0.43f,
                        topColor,
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            canvas.drawRect(
                0f,
                height * 0.34f,
                width,
                height * 0.58f,
                Paint().apply {
                    shader = LinearGradient(
                        0f,
                        height * 0.34f,
                        0f,
                        height * 0.58f,
                        if (isNight) 0x44051324 else 0x32FFF9EA,
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP,
                    )
                },
            )
        }

        private fun drawHeader() {
            val bottom = height * 0.101f
            val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isNight) 0xC30A1C30.toInt() else 0xE8FBF8F1.toInt()
            }
            canvas.drawRoundRect(
                RectF(0f, -28f * unit, width, bottom),
                38f * unit,
                38f * unit,
                panel,
            )
            if (isNight) {
                canvas.drawRoundRect(
                    RectF(1f * unit, 1f * unit, width - unit, bottom),
                    38f * unit,
                    38f * unit,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 0.8f * unit
                        color = 0x6D9DB5CA
                    },
                )
            }

            val cy = bottom * 0.51f
            val discRadius = 34f * unit
            drawLocationDisc(57f * unit, cy, discRadius)

            val placePaint = textPaint(20f, ink, sansBold)
            fitText(placePaint, state.place, 315f * unit, 14f * unit)
            canvas.drawText(state.place, 112f * unit, cy - 4f * unit, placePaint)
            val datePaint = textPaint(13.5f, mutedInk, sans)
            fitText(datePaint, state.dateLabel, 340f * unit, 10f * unit)
            canvas.drawText(state.dateLabel, 112f * unit, cy + 23f * unit, datePaint)

            drawWeatherCapsule(cy)
            drawRefreshDisc(664f * unit, cy, discRadius)
        }

        private fun drawLocationDisc(cx: Float, cy: Float, radius: Float) {
            canvas.drawCircle(
                cx,
                cy,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        cx - radius * 0.25f,
                        cy - radius * 0.3f,
                        radius * 1.2f,
                        if (isNight) {
                            intArrayOf(0xFF2A815F.toInt(), 0xFF0A3B32.toInt(), 0xAA0B2131.toInt())
                        } else {
                            intArrayOf(0xFFF6FAF1.toInt(), 0xFFD7E7D4.toInt(), 0xFFEAF0E8.toInt())
                        },
                        null,
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            canvas.drawCircle(
                cx,
                cy,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 0.9f * unit
                    color = if (isNight) 0x8198B7AD.toInt() else 0x66FFFFFF
                },
            )
            val pin = Path().apply {
                moveTo(cx, cy + 17f * unit)
                cubicTo(cx - 8f * unit, cy + 7f * unit, cx - 15f * unit, cy, cx - 15f * unit, cy - 8f * unit)
                cubicTo(cx - 15f * unit, cy - 19f * unit, cx - 8f * unit, cy - 25f * unit, cx, cy - 25f * unit)
                cubicTo(cx + 8f * unit, cy - 25f * unit, cx + 15f * unit, cy - 19f * unit, cx + 15f * unit, cy - 8f * unit)
                cubicTo(cx + 15f * unit, cy, cx + 8f * unit, cy + 7f * unit, cx, cy + 17f * unit)
                close()
            }
            canvas.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B4938.toInt() })
            canvas.drawCircle(cx, cy - 9f * unit, 5f * unit, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        }

        private fun drawWeatherCapsule(cy: Float) {
            val left = 455f * unit
            val right = 613f * unit
            val top = cy - 34f * unit
            val bottom = cy + 34f * unit
            canvas.drawRoundRect(
                RectF(left, top, right, bottom),
                34f * unit,
                34f * unit,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isNight) 0x8B132D47.toInt() else 0xBDFFFFFF.toInt()
                },
            )
            state.currentWeather?.icon?.let { icon ->
                canvas.drawBitmap(
                    icon,
                    null,
                    RectF(left + 12f * unit, cy - 22f * unit, left + 56f * unit, cy + 22f * unit),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                )
            }
            val weather = state.currentWeather
            val temperature = weather?.temperature ?: "—"
            val summary = weather?.summary ?: if (isNight) "Night sky" else "Daylight"
            val weatherInk = if (isNight) Color.WHITE else Color.rgb(9, 26, 42)
            canvas.drawText(temperature, left + 68f * unit, cy - 2f * unit, textPaint(20f, weatherInk, sansBold))
            val summaryPaint = textPaint(11.5f, weatherInk, sans)
            fitText(summaryPaint, summary, 76f * unit, 8f * unit)
            canvas.drawText(summary, left + 68f * unit, cy + 20f * unit, summaryPaint)
        }

        private fun drawRefreshDisc(cx: Float, cy: Float, radius: Float) {
            canvas.drawCircle(
                cx,
                cy,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isNight) 0xA51A3854.toInt() else 0xCDEBF0F1.toInt()
                },
            )
            val paint = textPaint(38f, if (isNight) Color.WHITE else Color.rgb(8, 30, 45), sansMedium).apply {
                textAlign = Paint.Align.CENTER
            }
            val baseline = cy - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
            canvas.drawText("↻", cx, baseline, paint)
        }

        private fun drawEditorialTitle() {
            // Anchor the editorial block below the shared header on the same width-based
            // grid as the reference. Height fractions squeezed it upward on launcher
            // footprints and pushed the text into the foliage.
            val contentTop = height * HEADER_HEIGHT_FRACTION
            drawTrackedText(
                text = "A HIGHER TOMORROW",
                x = 155f * unit,
                baseline = contentTop + 37f * unit,
                paint = textPaint(10.5f, ink, sansMedium),
                tracking = 4.5f * unit,
            )
            val closerPaint = textPaint(48f, ink, editorialSerif)
            val allahPaint = textPaint(53f, accent, editorialSerif)
            canvas.drawText("Closer to", 155f * unit, contentTop + 93f * unit, closerPaint)
            canvas.drawText("Allah", 155f * unit, contentTop + 147f * unit, allahPaint)
            drawTrackedText(
                text = "SAME CREATOR  •  SAME SKY",
                x = 155f * unit,
                baseline = contentTop + 174f * unit,
                paint = textPaint(8f, ink, sansMedium),
                tracking = 3.1f * unit,
            )
            drawTrackedText(
                text = "SAME JOURNEY  •  ONE UMMAH",
                x = 155f * unit,
                baseline = contentTop + 191f * unit,
                paint = textPaint(8f, ink, sansMedium),
                tracking = 3.1f * unit,
            )

            val aside = textPaint(15f, if (isNight) Color.rgb(230, 226, 225) else Color.rgb(88, 78, 65), serifItalic).apply {
                textAlign = Paint.Align.CENTER
            }
            val asideX = width * 0.855f
            canvas.drawText("Different", asideX, contentTop + 61f * unit, aside)
            canvas.drawText("Places", asideX, contentTop + 82f * unit, aside)
            canvas.drawText("Same Sky", asideX, contentTop + 103f * unit, aside)
            canvas.drawLine(
                asideX - 10f * unit,
                contentTop + 121f * unit,
                asideX + 10f * unit,
                contentTop + 121f * unit,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = aside.color; strokeWidth = unit },
            )
        }

        private data class ArcNode(
            val name: String,
            val time: String,
            val xFraction: Float,
        )

        private fun drawPrayerArc() {
            val prayerByName = state.prayers.associateBy { it.name.lowercase(Locale.ENGLISH) }
            val nodes = listOf(
                ArcNode("Fajr", prayerByName["fajr"]?.time.orEmpty(), 0.145f),
                ArcNode("Sunrise", formatMinute(state.sky.sunrise), 0.275f),
                ArcNode("Dhuhr", prayerByName["dhuhr"]?.time.orEmpty(), 0.485f),
                ArcNode("Asr", prayerByName["asr"]?.time.orEmpty(), 0.635f),
                ArcNode("Maghrib", prayerByName["maghrib"]?.time.orEmpty(), 0.755f),
                ArcNode("Isha", prayerByName["isha"]?.time.orEmpty(), 0.875f),
            )
            val startX = width * 0.12f
            val endX = width * 0.90f
            val endY = height * 0.535f
            val controlX = (startX + endX) / 2f
            val controlY = height * 0.235f
            nodes.forEach { node ->
                val t = ((width * node.xFraction - startX) / (endX - startX)).coerceIn(0f, 1f)
                val oneMinus = 1f - t
                val x = oneMinus * oneMinus * startX + 2f * oneMinus * t * controlX + t * t * endX
                val y = oneMinus * oneMinus * endY + 2f * oneMinus * t * controlY + t * t * endY
                val labelPaint = textPaint(12.5f, ink, sansMedium).apply { textAlign = Paint.Align.CENTER }
                val timePaint = textPaint(11.5f, ink, sansMedium).apply { textAlign = Paint.Align.CENTER }
                canvas.drawText(node.name, x, y - 34f * unit, labelPaint)
                canvas.drawText(node.time, x, y - 18f * unit, timePaint)
            }
        }

        private fun drawNextPrayerPanel(
            compact: Boolean = false,
            ultraCompact: Boolean = false,
        ) {
            // Use width-derived measurements for this section. Launcher widget heights vary
            // independently from their widths; height fractions compressed the reference
            // card and made the quotation collide with its citation on shorter launchers.
            val frontHeight = (when {
                ultraCompact -> 104f
                compact -> 136f
                else -> 154f
            }) * unit
            val bottom = height - (when {
                ultraCompact -> 7f
                compact -> 8f
                else -> 9f
            }) * unit
            val top = bottom - frontHeight
            val labelY = when { ultraCompact -> 22f; compact -> 30f; else -> 34f }
            val prayerY = when { ultraCompact -> 48f; compact -> 59f; else -> 69f }
            val countdownY = when { ultraCompact -> 82f; compact -> 101f; else -> 128f }
            val dividerTop = when { ultraCompact -> 16f; compact -> 24f; else -> 28f }
            val dividerBottom = when { ultraCompact -> 87f; compact -> 112f; else -> 138f }
            val leafY = when { ultraCompact -> 52f; compact -> 67f; else -> 78f }
            val quoteFirstY = when { ultraCompact -> 22f; compact -> 29f; else -> 31f }
            val quoteSpacing = when { ultraCompact -> 14f; compact -> 17.5f; else -> 20f }
            val citationY = when { ultraCompact -> 86f; compact -> 112f; else -> 133f }
            val moreY = top + (when { ultraCompact -> 50f; compact -> 66f; else -> 76f }) * unit
            val handleTop = top + (when { ultraCompact -> 95f; compact -> 126f; else -> 143f }) * unit
            // Both layers must frost the original scene. Sampling the already-painted rear
            // card for the front layer compounded the tint into an opaque cream rectangle.
            val glassSource = targetBitmap.copy(Bitmap.Config.ARGB_8888, false)
            val horizontalInset = (when {
                ultraCompact -> 5f
                compact -> 7f
                else -> 9f
            }) * unit
            val backingRect = RectF(horizontalInset, top, width - horizontalInset, height - unit)
            drawFrostedGlass(
                sourceBitmap = glassSource,
                rect = backingRect,
                radius = (when { ultraCompact -> 25f; compact -> 31f; else -> 36f }) * unit,
                topTint = if (isNight) 0xB80A1C30.toInt() else 0xB8FBF8F0.toInt(),
                bottomTint = if (isNight) 0xDD081729.toInt() else 0xD0F8F3E8.toInt(),
                border = if (isNight) 0xA69CB2C4.toInt() else 0xE6FFFFFF.toInt(),
                shadow = if (isNight) 0x62000000 else 0x220B2630,
            )
            // Inset the card enough for its rounded sidewalls and layered shadow to remain
            // visible after the overall widget bitmap is clipped by the launcher.
            val rect = RectF(horizontalInset, top, width - horizontalInset, bottom)
            drawFrostedGlass(
                sourceBitmap = glassSource,
                rect = rect,
                radius = (when { ultraCompact -> 22f; compact -> 27f; else -> 31f }) * unit,
                topTint = if (isNight) 0xB80B2138.toInt() else 0xC3FCF9F2.toInt(),
                bottomTint = if (isNight) 0xD6091B2F.toInt() else 0xD2F5EEE3.toInt(),
                border = if (isNight) 0xB39CB2C4.toInt() else 0xF0FFFFFF.toInt(),
                shadow = if (isNight) 0x76000000 else 0x260B2630,
            )
            glassSource.recycle()

            val left = 43f * unit
            canvas.drawText(
                "Next Prayer",
                left,
                top + labelY * unit,
                textPaint(when { ultraCompact -> 12.5f; compact -> 15.5f; else -> 17f }, mutedInk, sansMedium),
            )
            val prayerPaint = textPaint(when { ultraCompact -> 26f; compact -> 33f; else -> 39f }, ink, sansMedium)
            fitText(prayerPaint, state.nextPrayer.name, 175f * unit, 20f * unit)
            canvas.drawText(state.nextPrayer.name, left, top + prayerY * unit, prayerPaint)
            val countdown = state.countdown.let { if (it.startsWith("in ")) it else "in $it" }
            val countdownPaint = textPaint(when { ultraCompact -> 25f; compact -> 34f; else -> 39f }, accent, sansMedium)
            fitText(countdownPaint, countdown, 190f * unit, 18f * unit)
            canvas.drawText(countdown, left, top + countdownY * unit, countdownPaint)

            val dividerX = width * 0.337f
            canvas.drawLine(
                dividerX,
                top + dividerTop * unit,
                dividerX,
                top + dividerBottom * unit,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isNight) 0x5E9EB3C3 else 0x66858B83
                    strokeWidth = unit
                },
            )
            drawLeaf(width * 0.420f, top + leafY * unit)

            val quoteX = width * 0.483f
            val quotePaint = textPaint(
                when { ultraCompact -> 10.5f; compact -> 13.5f; else -> 16f },
                ink,
                sansMedium,
            ).apply {
                if (isNight) setShadowLayer(1.2f * unit, 0f, unit, 0x78000000)
            }
            val quoteLines = wrapText(
                text = "“${state.ayah.text}”",
                paint = quotePaint,
                maxWidth = width * 0.34f,
                maxLines = 4,
            )
            quoteLines.forEachIndexed { index, line ->
                canvas.drawText(
                    line,
                    quoteX,
                    top + (quoteFirstY + index * quoteSpacing) * unit,
                    quotePaint,
                )
            }
            canvas.drawText(
                state.ayah.citation,
                quoteX,
                top + citationY * unit,
                textPaint(
                    when { ultraCompact -> 9.5f; compact -> 11.5f; else -> 13f },
                    if (isNight) 0xFFE3EBF0.toInt() else mutedInk,
                    sansMedium,
                ),
            )

            val moreX = width * 0.900f
            canvas.drawCircle(
                moreX,
                moreY,
                (when { ultraCompact -> 19f; compact -> 23f; else -> 27f }) * unit,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isNight) 0xA41A3753.toInt() else 0xB7BFD6BF.toInt()
                },
            )
            repeat(3) { index ->
                canvas.drawCircle(
                    moreX + (index - 1) * 8f * unit,
                    moreY,
                    2.2f * unit,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink },
                )
            }
            canvas.drawRoundRect(
                RectF(
                    width * 0.410f,
                    handleTop,
                    width * 0.537f,
                    handleTop + 4f * unit,
                ),
                3f * unit,
                3f * unit,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (isNight) Color.WHITE else 0xFF607077.toInt() },
            )
        }

        /**
         * Reduced-height widgets prioritize the artwork. Keep only the live next-prayer
         * information over a quiet edge-to-edge fade: no card, quotation, ornament,
         * divider, action button, or footer furniture.
         */
        private fun drawMinimalNextPrayerInfo(ultraCompact: Boolean) {
            // Give the two-line summary the bottom band instead of pinning both lines
            // against its lower edge. This keeps the label and countdown readable at
            // launcher scale while the fade remains transparent above the reflection.
            val fadeHeight = (if (ultraCompact) 138f else 172f) * unit
            val fadeTop = height - fadeHeight
            canvas.drawRect(
                0f,
                fadeTop,
                width,
                height,
                Paint().apply {
                    shader = LinearGradient(
                        0f,
                        fadeTop,
                        0f,
                        height,
                        Color.TRANSPARENT,
                        if (isNight) 0xF2081729.toInt() else 0xF2FFF9EF.toInt(),
                        Shader.TileMode.CLAMP,
                    )
                },
            )

            // Keep the live block in the familiar lower-left column; the stronger fade
            // gives it contrast without relocating it toward the centre of the artwork.
            val left = (if (ultraCompact) 30f else 38f) * unit
            val labelBaseline = height - (if (ultraCompact) 72f else 91f) * unit
            val valueBaseline = height - (if (ultraCompact) 24f else 31f) * unit
            val labelPaint = textPaint(
                if (ultraCompact) 12.5f else 15f,
                if (isNight) 0xFFE4ECF1.toInt() else 0xFF27434A.toInt(),
                sansBold,
            ).apply {
                setShadowLayer(1.5f * unit, 0f, unit, if (isNight) 0xB8000000.toInt() else 0x66FFFFFF)
            }
            drawTrackedText(
                text = "NEXT PRAYER",
                x = left,
                baseline = labelBaseline,
                paint = labelPaint,
                tracking = (if (ultraCompact) 2f else 2.3f) * unit,
            )

            val countdown = state.countdown.let { if (it.startsWith("in ")) it else "in $it" }
            val value = "${state.nextPrayer.name}  •  $countdown"
            val valuePaint = textPaint(
                if (ultraCompact) 29f else 35f,
                if (isNight) Color.WHITE else 0xFF0A2630.toInt(),
                sansBold,
            ).apply {
                setShadowLayer(2f * unit, 0f, 1.5f * unit, if (isNight) 0xD0000000.toInt() else 0x77FFFFFF)
            }
            fitText(
                paint = valuePaint,
                text = value,
                maxWidth = width - left - 28f * unit,
                minSize = 21f * unit,
            )
            canvas.drawText(value, left, valueBaseline, valuePaint)
        }

        /**
         * Repaints a low-resolution copy of the scene beneath the surface, then layers
         * translucent tint, shadow and a bright edge over it. The downsample/upscale pass
         * supplies the actual diffusion that a flat alpha fill cannot reproduce in a
         * RemoteViews bitmap.
         */
        private fun drawFrostedGlass(
            sourceBitmap: Bitmap,
            rect: RectF,
            radius: Float,
            topTint: Int,
            bottomTint: Int,
            border: Int,
            shadow: Int,
        ) {
            val sourceLeft = rect.left.toInt().coerceIn(0, sourceBitmap.width - 1)
            val sourceTop = (rect.top - bitmapSampleOffsetY).toInt()
                .coerceIn(0, sourceBitmap.height - 1)
            val sourceRight = rect.right.toInt().coerceIn(sourceLeft + 1, sourceBitmap.width)
            val sourceBottom = (rect.bottom - bitmapSampleOffsetY).toInt()
                .coerceIn(sourceTop + 1, sourceBitmap.height)
            val source = Rect(sourceLeft, sourceTop, sourceRight, sourceBottom)
            val diffusion = 4
            val blurred = Bitmap.createBitmap(
                (source.width() / diffusion).coerceAtLeast(1),
                (source.height() / diffusion).coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            Canvas(blurred).drawBitmap(
                sourceBitmap,
                source,
                Rect(0, 0, blurred.width, blurred.height),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            boxBlur(blurred, radius = 4)

            canvas.drawRoundRect(
                rect,
                radius,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x09000000
                    setShadowLayer(13f * unit, 0f, 5f * unit, shadow)
                },
            )

            val glassPath = Path().apply {
                addRoundRect(rect, radius, radius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(glassPath)
            canvas.drawBitmap(
                blurred,
                null,
                rect,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            canvas.drawRect(
                rect,
                Paint().apply {
                    shader = LinearGradient(
                        0f,
                        rect.top,
                        0f,
                        rect.bottom,
                        topTint,
                        bottomTint,
                        Shader.TileMode.CLAMP,
                    )
                },
            )
            canvas.restore()
            blurred.recycle()

            canvas.drawRoundRect(
                rect,
                radius,
                radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.05f * unit
                    color = border
                },
            )
            val highlight = RectF(
                rect.left + 1.6f * unit,
                rect.top + 1.6f * unit,
                rect.right - 1.6f * unit,
                rect.bottom - 1.6f * unit,
            )
            canvas.drawRoundRect(
                highlight,
                (radius - 1.6f * unit).coerceAtLeast(0f),
                (radius - 1.6f * unit).coerceAtLeast(0f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 0.55f * unit
                    color = if (isNight) 0x559CB2C4 else 0x8AFFFFFF.toInt()
                },
            )
        }

        private fun boxBlur(bitmap: Bitmap, radius: Int) {
            if (radius <= 0 || bitmap.width <= 1 || bitmap.height <= 1) return
            val width = bitmap.width
            val height = bitmap.height
            val source = IntArray(width * height)
            val horizontal = IntArray(source.size)
            val output = IntArray(source.size)
            bitmap.getPixels(source, 0, width, 0, 0, width, height)

            fun averageInto(input: IntArray, result: IntArray, horizontalPass: Boolean) {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        var alpha = 0L
                        var red = 0L
                        var green = 0L
                        var blue = 0L
                        var count = 0
                        val start = if (horizontalPass) {
                            (x - radius).coerceAtLeast(0)
                        } else {
                            (y - radius).coerceAtLeast(0)
                        }
                        val end = if (horizontalPass) {
                            (x + radius).coerceAtMost(width - 1)
                        } else {
                            (y + radius).coerceAtMost(height - 1)
                        }
                        for (position in start..end) {
                            val pixel = if (horizontalPass) {
                                input[y * width + position]
                            } else {
                                input[position * width + x]
                            }
                            alpha += Color.alpha(pixel)
                            red += Color.red(pixel)
                            green += Color.green(pixel)
                            blue += Color.blue(pixel)
                            count++
                        }
                        result[y * width + x] = Color.argb(
                            (alpha / count).toInt(),
                            (red / count).toInt(),
                            (green / count).toInt(),
                            (blue / count).toInt(),
                        )
                    }
                }
            }

            averageInto(source, horizontal, horizontalPass = true)
            averageInto(horizontal, output, horizontalPass = false)
            bitmap.setPixels(output, 0, width, 0, 0, width, height)
        }

        private fun drawLeaf(x: Float, y: Float) {
            val leafColor = if (isNight) 0xFFBBDAC1.toInt() else 0xFF0B4743.toInt()
            val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = leafColor
                style = Paint.Style.STROKE
                strokeWidth = 2.2f * unit
                strokeCap = Paint.Cap.ROUND
            }
            val stem = Path().apply {
                moveTo(x - 14f * unit, y + 36f * unit)
                cubicTo(
                    x - 8f * unit,
                    y + 16f * unit,
                    x + 10f * unit,
                    y - 17f * unit,
                    x + 22f * unit,
                    y - 38f * unit,
                )
            }
            canvas.drawPath(stem, stemPaint)

            fun leaf(
                cx: Float,
                cy: Float,
                rotation: Float,
                length: Float,
                breadth: Float,
            ) {
                canvas.save()
                canvas.rotate(rotation, cx, cy)
                val leafPath = Path().apply {
                    moveTo(cx, cy)
                    cubicTo(
                        cx + length * 0.18f * unit,
                        cy - breadth * 0.90f * unit,
                        cx + length * 0.70f * unit,
                        cy - breadth * 1.06f * unit,
                        cx + length * unit,
                        cy,
                    )
                    cubicTo(
                        cx + length * 0.70f * unit,
                        cy + breadth * 0.60f * unit,
                        cx + length * 0.24f * unit,
                        cy + breadth * 0.48f * unit,
                        cx,
                        cy,
                    )
                    close()
                }
                canvas.drawPath(
                    leafPath,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = leafColor
                        style = Paint.Style.FILL
                    },
                )
                canvas.restore()
            }

            leaf(x - 8f * unit, y + 17f * unit, -33f, 24f, 6.4f)
            leaf(x - 4f * unit, y + 7f * unit, -108f, 22f, 6.3f)
            leaf(x + 3f * unit, y - 4f * unit, -31f, 25f, 6.7f)
            leaf(x + 10f * unit, y - 16f * unit, -99f, 23f, 6.4f)
            leaf(x + 16f * unit, y - 27f * unit, -45f, 27f, 6.8f)
        }

        private fun drawOuterHairline() {
            if (!isNight) return
            canvas.drawRoundRect(
                RectF(1.2f * unit, 1.2f * unit, width - 1.2f * unit, height - 1.2f * unit),
                width * 0.055f,
                width * 0.055f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 0.8f * unit
                    color = 0x6B9DB3C4
                },
            )
        }

        private fun formatMinute(minute: Int): String {
            val time = LocalTime.of((minute / 60).mod(24), minute.mod(60))
            val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
            return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        }

        private fun textPaint(size: Float, color: Int, face: Typeface): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = face
                textSize = size * unit
                this.color = color
            }

        private fun fitText(paint: Paint, text: String, maxWidth: Float, minSize: Float) {
            while (paint.textSize > minSize && paint.measureText(text) > maxWidth) {
                paint.textSize -= 0.5f * unit
            }
        }

        private fun wrapText(
            text: String,
            paint: Paint,
            maxWidth: Float,
            maxLines: Int,
        ): List<String> {
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return emptyList()
            val lines = mutableListOf<String>()
            var current = ""
            words.forEachIndexed { index, word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                    current = candidate
                } else {
                    lines += current
                    current = word
                    if (lines.size == maxLines - 1) {
                        val remainder = buildList {
                            add(current)
                            addAll(words.drop(index + 1))
                        }.joinToString(" ")
                        lines += ellipsize(remainder, paint, maxWidth)
                        return lines
                    }
                }
            }
            if (current.isNotEmpty() && lines.size < maxLines) lines += ellipsize(current, paint, maxWidth)
            return lines
        }

        private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            var shortened = text
            while (shortened.isNotEmpty() && paint.measureText("${shortened.trimEnd()}…") > maxWidth) {
                shortened = shortened.dropLast(1)
            }
            return "${shortened.trimEnd()}…"
        }

        private fun trackedWidth(text: String, paint: Paint, tracking: Float): Float =
            text.sumOf { paint.measureText(it.toString()).toDouble() }.toFloat() +
                tracking * (text.length - 1).coerceAtLeast(0)

        private fun drawTrackedText(text: String, x: Float, baseline: Float, paint: Paint, tracking: Float) {
            var cursor = x
            text.forEach { char ->
                val glyph = char.toString()
                canvas.drawText(glyph, cursor, baseline, paint)
                cursor += paint.measureText(glyph) + tracking
            }
        }

        private fun font(resource: Int, fallback: Typeface): Typeface =
            runCatching { ResourcesCompat.getFont(context, resource) }.getOrNull() ?: fallback
    }
}
