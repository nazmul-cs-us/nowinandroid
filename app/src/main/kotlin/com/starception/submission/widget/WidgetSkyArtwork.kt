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
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.starception.submission.R
import com.starception.submission.prayer.sky.julianDay
import com.starception.submission.prayer.sky.sunAltitudeAt
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import com.starception.submission.core.designsystem.R as DesignR

/**
 * Today's Prayers as a living sky.
 *
 * The card is a painting of the sky as it is right now over the widget's location: its
 * colours blend continuously from the sun's true altitude — noon blue, golden hour, rose
 * and violet twilight, indigo night with stars — over a mosque skyline on the horizon. The
 * sun glows where it actually stands, or after sunset the moon shows its phase for the Hijri
 * date. The five prayers are luminous points on the sun's thin golden path, each at the
 * altitude the sun has at that moment, with their names, times and status on the ground
 * beneath. Prayers keep five equal columns so the labels stay aligned; only their heights
 * are astronomical.
 *
 * Everything, including the title, is one bitmap: the card aligns to the pixel and needs
 * nothing special when a flipper page rasterises it.
 */
internal object WidgetSkyArtwork {

    /**
     * The skyline, decoded once per size rather than once per render.
     *
     * This used to be a `by lazy` on the Renderer — and a Renderer is built fresh every
     * render, so every minute the widget rasterised a vector at 1400px into ARGB_8888 and
     * then scanned it row by row and column by column with `getPixels`, on the main
     * thread. Holding one scaled copy per width bucket is both faster and *less* memory
     * than allocating and discarding four megabytes a minute.
     *
     * Same pattern as WidgetMeteocons and WidgetWeatherArtwork, which cache the same way.
     */
    private val skylineCache = HashMap<Int, Bitmap>()

    private fun skylineArt(context: Context, targetWidthPx: Int): Bitmap? =
        synchronized(skylineCache) {
            // Bucketed so a resize drag does not decode at every intermediate width.
            val bucket = ((targetWidthPx.coerceAtLeast(1) + 127) / 128) * 128
            skylineCache[bucket]?.let { return it }
            val decoded = runCatching {
                BitmapFactory.decodeResource(context.resources, R.drawable.prayer_widget_skyline_v1)
            }.getOrNull() ?: return null
            val height = (bucket * decoded.height.toFloat() / decoded.width)
                .toInt().coerceAtLeast(1)
            val scaled = runCatching {
                Bitmap.createScaledBitmap(decoded, bucket, height, true)
            }.getOrNull() ?: decoded
            if (scaled !== decoded) decoded.recycle()
            skylineCache[bucket] = scaled
            scaled
        }

    class Input(
        val sky: WidgetSky,
        val prayers: List<WidgetPrayer>,
        val daylightLabel: String,
        val nightLabel: String,
        val cornerRadiusDp: Float,
    )

    /** Height the card was drawn at; its type ramps from here. */
    private const val REFERENCE_TIMELINE_HEIGHT_DP = 142f

    /** Ceiling on that ramp — a flipper page is close to twice the reference. */
    private const val TIMELINE_MAX_TYPE_SCALE = 1.8f

    /** Share of a column its label may fill, leaving a gutter to the next one. */
    private const val TIMELINE_COLUMN_FILL = 0.92f

    /**
     * Points sampled along the sun's path.
     *
     * Forty-eight is about twenty pixels a segment at this card's width, on a curve with
     * no inflections to miss. Going finer costs trigonometry and changes no pixel.
     */
    private const val SUN_TRACK_SAMPLES = 48

    fun render(context: Context, input: Input, widthDp: Float, heightDp: Float): Bitmap {
        val metrics = context.resources.displayMetrics
        val scale = min(metrics.density, 3f) * min(1f, kotlin.math.sqrt(1_100_000f / (widthDp * heightDp * 9f)))
        val w = (widthDp * scale).toInt().coerceAtLeast(1)
        val h = (heightDp * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = input.cornerRadiusDp * scale
        canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW) })
        Renderer(context, input, canvas, w.toFloat(), h.toFloat(), scale).draw()
        return bitmap
    }

    private class Renderer(
        private val context: Context,
        private val input: Input,
        private val canvas: Canvas,
        private val w: Float,
        private val h: Float,
        private val px: Float,
    ) {
        private val sky = input.sky
        private val columns = input.prayers.size.coerceAtLeast(1)
        private val compact = h / px < 130f

        private val bold = font(DesignR.font.ubuntu_sans_bold)
        private val medium = font(DesignR.font.ubuntu_sans_medium)

        /**
         * The card is handed anything from the reference's 142dp up to a whole flipper
         * page, and everything that carries or frames type follows this — otherwise a
         * taller card is the same small labels adrift in more sky.
         *
         * Height alone is not enough to go on. A taller card gives the five columns no
         * more width, and the title shares its line with the daylight caption: ramping on
         * height only put "Today's Prayers" through the caption, ran the times into each
         * other and left the two pills overlapping. So the ramp is also held to what the
         * widest label in a column, and the title row, can actually carry.
         */
        private val typeScale: Float = run {
            val byHeight =
                ((h / px) / REFERENCE_TIMELINE_HEIGHT_DP).coerceIn(1f, TIMELINE_MAX_TYPE_SCALE)
            val gauge = Paint(Paint.ANTI_ALIAS_FLAG)
            fun widest(texts: List<String>, face: Typeface, sizeDp: Float): Float {
                gauge.typeface = face
                gauge.textSize = sizeDp * px
                return texts.maxOfOrNull { gauge.measureText(it) } ?: 0f
            }
            val column = (w / columns) * TIMELINE_COLUMN_FILL
            val names = widest(input.prayers.map { it.name }, bold, if (compact) 10.5f else 11.5f)
            val times = widest(input.prayers.map { it.time }, medium, if (compact) 9.5f else 10.5f)
            // The pill carries its own 10dp flanks either side of the label.
            val pills = widest(
                input.prayers.mapNotNull { it.relativeLabel },
                medium,
                if (compact) 9f else 10f,
            ).let { if (it > 0f) it + 20f * px else 0f }
            // The title and the daylight caption share one line, inside the side margins.
            val titleRow = widest(listOf("Today's Prayers"), bold, if (compact) 13f else 15f) +
                widest(
                    listOf("${input.daylightLabel}   ·   ${input.nightLabel}"),
                    medium,
                    if (compact) 8f else 9f,
                ) + 33f * px
            val caps = listOf(
                names to column,
                times to column,
                titleRow to (w - 24f * px),
            ).mapNotNull { (needed, room) -> if (needed > 0f) room / needed else null }
            // Never below 1: the reference sizes already fit the shape it was drawn for.
            minOf(byHeight, caps.minOrNull() ?: Float.MAX_VALUE).coerceAtLeast(1f)
        }

        /**
         * The pills ramp on their own, and hardly at all.
         *
         * "1h 14m ago" plus its flanks is already most of a column at the reference size,
         * and the two that show — the last prayer's and the next one's — sit side by side,
         * so they run into each other before anything else on the card does. Holding the
         * names and times down to what the pills can take meant nothing grew; letting the
         * pills keep their own ceiling lets the rest of the row move.
         */
        private val pillScale: Float = run {
            val gauge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = medium
                textSize = (if (compact) 9f else 10f) * px
            }
            val widest = input.prayers.mapNotNull { it.relativeLabel }
                .maxOfOrNull { gauge.measureText(it) } ?: 0f
            if (widest <= 0f) {
                typeScale
            } else {
                minOf(typeScale, (w / columns) * TIMELINE_COLUMN_FILL / (widest + 20f * px))
                    .coerceAtLeast(1f)
            }
        }

        /** One unit of pill-scaled dp. */
        private val pp = px * pillScale

        /** One unit of type-scaled dp, as [px] is one unit of plain dp. */
        private val tp = px * typeScale

        private val titleBlock = (if (compact) 26f else 30f) * tp
        private val labelBlock = (if (compact) 48f else 56f) * tp
        private val groundTop = h - labelBlock

        // The strip of ground between the skyline and the label row is a fixed share of the
        // card in the reference, which on a flipper page turned into a broad empty band.
        // Hold it near the size it has there — scaled with the type, like the row beneath
        // it — and give everything it gives up back to the sky, where the arc and the
        // skyline can use it.
        private val horizonY = run {
            val band = (groundTop - titleBlock)
            val ground = (16f * tp).coerceAtMost(band * 0.28f)
            titleBlock + band - ground
        }
        private val apexY = titleBlock + 10f * px
        private val nadirY = groundTop - 6f * px

        // Where the sun is now decides the palette for the whole card.
        private val nowAltitude = altitude(sky.now.toDouble())
        private val palette = paletteFor(nowAltitude)

        fun draw() {
            drawSky()
            drawTwilightBands()
            drawStars()
            val nodes = pathPoints()
            drawLuminaryGlow(nodes)
            drawPath(nodes)
            // The skyline over the path: the sun sets behind the mosque, not in front of it.
            drawSkyline()
            drawLuminary(nodes)
            drawNodes(nodes)
            drawGround()
            drawTitle()
            drawLabels()
        }

        // ---- astronomy ----------------------------------------------------------

        /** The Julian Day of a local minute, fractional and unbounded at both ends. */
        private fun julianDayAt(minute: Double): Double =
            julianDay(sky.localMidnightEpochSeconds + minute * 60.0)

        /**
         * The sun's apparent altitude at a local minute.
         *
         * This used to be Cooper's declination approximation with the hour angle measured
         * from Dhuhr. Dhuhr is not solar noon — it is transit plus a method-dependent
         * margin, and the user may shift it further still — so the whole arc was skewed by
         * however many minutes that came to. It now comes from the shared engine, which is
         * checked against Meeus's published answers.
         */
        private fun altitude(minute: Double): Double =
            sunAltitudeAt(julianDayAt(minute), sky.latitude, sky.longitude)

        /** The five prayer minutes, in the order of their columns. */
        private val prayerMinutes = intArrayOf(sky.fajr, sky.dhuhr, sky.asr, sky.maghrib, sky.isha)

        private fun columnX(index: Int): Float = w * (index + 0.5f) / columns

        /**
         * Time to x, stretched so each prayer lands on its own column centre.
         *
         * The five columns must stay evenly spaced — the labels beneath depend on it, and
         * the real intervals are hopeless anyway: Maghrib to Isha can be seventy minutes
         * against seven hours from Fajr to Dhuhr, which would collapse two labels into one.
         *
         * So the axis is time, piecewise-linearly warped: monotone, pinned at the five
         * prayers, and linear in between. Every point drawn against it is at its true
         * altitude and in the right order relative to its neighbours; what it is not is
         * uniform. Beyond the ends it keeps the slope of the outermost segment so the curve
         * runs off both edges rather than stopping.
         */
        private fun timeToX(minute: Double): Float {
            val first = prayerMinutes.first()
            val last = prayerMinutes.last()
            if (minute <= first) {
                val slope = (columnX(1) - columnX(0)) / (prayerMinutes[1] - first).coerceAtLeast(1)
                return columnX(0) + (minute - first).toFloat() * slope
            }
            if (minute >= last) {
                val span = (last - prayerMinutes[prayerMinutes.size - 2]).coerceAtLeast(1)
                val slope = (columnX(4) - columnX(3)) / span
                return columnX(4) + (minute - last).toFloat() * slope
            }
            for (i in 0 until prayerMinutes.size - 1) {
                val from = prayerMinutes[i]
                val to = prayerMinutes[i + 1]
                if (minute <= to) {
                    val t = ((minute - from) / (to - from).coerceAtLeast(1).toDouble()).toFloat()
                    return columnX(i) + (columnX(i + 1) - columnX(i)) * t
                }
            }
            return columnX(4)
        }

        /**
         * The sun's path, sampled rather than splined.
         *
         * Forty-eight samples over the drawn width is about twenty pixels a segment on a
         * curve this smooth — indistinguishable from a spline, and unlike a spline every
         * point on it is a real altitude at a real time. Sampled as minute/altitude pairs
         * first because the vertical scale is derived from them before it can be applied.
         */
        private val sunSampleMinutes: DoubleArray = DoubleArray(SUN_TRACK_SAMPLES).also { out ->
            val first = prayerMinutes.first().toDouble()
            val last = prayerMinutes.last().toDouble()
            val margin = (last - first) * 0.06
            val from = first - margin
            val step = (last + margin - from) / (SUN_TRACK_SAMPLES - 1)
            for (i in out.indices) out[i] = from + step * i
        }

        private val sunSampleAltitudes: DoubleArray =
            DoubleArray(SUN_TRACK_SAMPLES) { altitude(sunSampleMinutes[it]) }

        /**
         * Today's true transit altitude, taken from the sampled track.
         *
         * Not `altitude(dhuhr)`: Dhuhr is transit plus a margin, so reading the apex there
         * compressed the whole vertical scale by that much. Floored so a polar winter,
         * where the sun barely clears the horizon, cannot divide the card by nearly zero.
         */
        private val apexAltitude = (sunSampleAltitudes.maxOrNull() ?: 10.0).coerceAtLeast(10.0)

        private fun yFor(altitude: Double): Float = if (altitude >= 0) {
            horizonY - ((altitude / apexAltitude).coerceIn(0.0, 1.0) * (horizonY - apexY)).toFloat()
        } else {
            // Eighteen, not twenty, so the three twilight boundaries land on exact thirds
            // of the band: civil at -6, nautical at -12, astronomical night at the bottom.
            horizonY + ((-altitude / 18.0).coerceIn(0.0, 1.0) * (nadirY - horizonY)).toFloat()
        }

        /** The sampled path in card coordinates. */
        private val sunTrack: List<PointF> = List(SUN_TRACK_SAMPLES) { i ->
            PointF(timeToX(sunSampleMinutes[i]), yFor(sunSampleAltitudes[i]))
        }

        class Node(val x: Float, val y: Float, val minute: Int)

        /**
         * The five prayers, each on the track at its own column.
         *
         * Maghrib used to be forced to altitude zero and sunrise pinned to the horizon
         * line. Both now take the altitude they actually have, which for a sunset defined
         * with refraction is a pixel or two below the horizon — correct, and it reads as
         * the sun having just gone.
         */
        private fun pathPoints(): List<Node> =
            prayerMinutes.mapIndexed { index, minute ->
                Node(columnX(index), yFor(altitude(minute.toDouble())), minute)
            }

        // ---- palette ------------------------------------------------------------

        /** Sky top, sky mid, horizon glow, ink for text over the sky, and star strength. */
        class Palette(val top: Int, val mid: Int, val horizon: Int, val ink: Int, val stars: Float, val dark: Boolean)

        /**
         * Continuous in altitude: the same sky an observer sees, from deep noon blue through
         * the gold and rose of the low sun to violet dusk and indigo night.
         */
        private fun paletteFor(alt: Double): Palette {
            data class Stop(val alt: Double, val top: Int, val mid: Int, val horizon: Int)
            val stops = listOf(
                Stop(-18.0, 0xFF070C24.toInt(), 0xFF141E4A.toInt(), 0xFF2A3A72.toInt()),
                Stop(-10.0, 0xFF101A44.toInt(), 0xFF3A3F7E.toInt(), 0xFF8D5A86.toInt()),
                Stop(-4.0, 0xFF2A3E7C.toInt(), 0xFF8B6AA0.toInt(), 0xFFE58B7A.toInt()),
                Stop(2.0, 0xFF3F6BB0.toInt(), 0xFFD79A6B.toInt(), 0xFFF9CF8E.toInt()),
                Stop(12.0, 0xFF3F7FC4.toInt(), 0xFF9BC2E6.toInt(), 0xFFF3DDB7.toInt()),
                Stop(30.0, 0xFF3273BE.toInt(), 0xFF86B7E6.toInt(), 0xFFD8E9F6.toInt()),
                Stop(90.0, 0xFF2B6AB6.toInt(), 0xFF7FB2E4.toInt(), 0xFFD3E6F5.toInt()),
            )
            val upper = stops.indexOfFirst { it.alt >= alt }.let { if (it <= 0) 1 else it }.coerceAtMost(stops.size - 1)
            val a = stops[upper - 1]
            val b = stops[upper]
            val t = ((alt - a.alt) / (b.alt - a.alt)).toFloat().coerceIn(0f, 1f)
            val stars = ((-alt - 3.0) / 9.0).toFloat().coerceIn(0f, 1f)
            val dark = alt < 3.0
            return Palette(
                top = blend(a.top, b.top, t),
                mid = blend(a.mid, b.mid, t),
                horizon = blend(a.horizon, b.horizon, t),
                ink = if (dark) 0xFFF4F1E6.toInt() else 0xFF14352E.toInt(),
                stars = stars,
                dark = dark,
            )
        }

        private fun blend(from: Int, to: Int, t: Float): Int = Color.argb(
            lerp(Color.alpha(from), Color.alpha(to), t),
            lerp(Color.red(from), Color.red(to), t),
            lerp(Color.green(from), Color.green(to), t),
            lerp(Color.blue(from), Color.blue(to), t),
        )

        private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()

        // ---- drawing ------------------------------------------------------------

        private fun drawSky() {
            canvas.drawRect(
                0f,
                0f,
                w,
                groundTop,
                Paint().apply {
                    shader = LinearGradient(
                        0f,
                        0f,
                        0f,
                        groundTop,
                        intArrayOf(palette.top, palette.mid, palette.horizon, palette.horizon),
                        floatArrayOf(0f, 0.55f, 0.9f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
        }

        /**
         * The three twilight bands, under the horizon where the sun goes at night.
         *
         * These are not decoration and they are not arbitrary: civil, nautical and
         * astronomical twilight are defined at six, twelve and eighteen degrees below the
         * horizon, and the Fajr and Isha angles are chosen from exactly that scale. So the
         * bands show *why* those two prayers fall where they do — the card stops asserting
         * the times and starts explaining them.
         *
         * [yFor] maps zero to minus eighteen degrees onto the band, so the boundaries land
         * on exact thirds of it.
         */
        private fun drawTwilightBands() {
            if (nadirY <= horizonY) return
            val boundaries = floatArrayOf(yFor(-6.0), yFor(-12.0), yFor(-18.0))
            // Deepening with each step, taken from the palette's own twilight stops so the
            // bands belong to whatever the sky is currently doing.
            val inks = intArrayOf(0xFF2A3E7C.toInt(), 0xFF101A44.toInt(), 0xFF070C24.toInt())
            var top = horizonY
            for (i in boundaries.indices) {
                val bottom = boundaries[i]
                canvas.drawRect(
                    0f,
                    top,
                    w,
                    bottom,
                    Paint().apply {
                        color = inks[i]
                        alpha = 38 + i * 16
                    },
                )
                canvas.drawLine(
                    0f,
                    bottom,
                    w,
                    bottom,
                    Paint().apply {
                        color = 0xFFFFFFFF.toInt()
                        alpha = 20
                        strokeWidth = 1f * px
                    },
                )
                top = bottom
            }
            if (compact) return
            val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = medium
                textSize = 7.5f * tp
                color = 0xFFFFFFFF.toInt()
                alpha = 90
                textAlign = Paint.Align.RIGHT
            }
            val labels = arrayOf("civil", "nautical", "astronomical")
            var from = horizonY
            for (i in labels.indices) {
                val to = boundaries[i]
                // Only where the band is tall enough to hold the word without crowding it.
                if (to - from > caption.textSize * 1.6f) {
                    canvas.drawText(labels[i], w - 10f * px, to - caption.textSize * 0.45f, caption)
                }
                from = to
            }
        }

        /** A fixed field of stars, deterministic so the sky does not twinkle between renders. */
        private fun drawStars() {
            if (palette.stars <= 0f) return
            val random = Random(20260916)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(70) {
                val x = random.nextFloat() * w
                val y = random.nextFloat() * (horizonY - 8f * px)
                val r = (0.6f + random.nextFloat() * 1.1f) * px
                val a = (0.35f + random.nextFloat() * 0.65f) * palette.stars
                paint.color = Color.argb((a * 255).toInt(), 244, 241, 230)
                canvas.drawCircle(x, y, r, paint)
            }
        }

        /** Hills and the Masjid an-Nabawi silhouette along the horizon. */
        private fun drawSkyline() {
            // By day the skyline is a dark green cut-out; at night near-black would vanish
            // into the indigo, so it lightens to slate and gets a backlit rim along its top,
            // as a skyline does against the afterglow.
            val ink = if (palette.dark) 0xFF1A2440.toInt() else 0xFF20443E.toInt()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink
                alpha = if (palette.dark) 245 else 185
            }
            val hills = Path().apply {
                moveTo(0f, horizonY + 6f * px)
                cubicTo(w * 0.12f, horizonY - 5f * px, w * 0.22f, horizonY + 4f * px, w * 0.34f, horizonY - 1f * px)
                cubicTo(w * 0.42f, horizonY - 4f * px, w * 0.5f, horizonY + 3f * px, w * 0.6f, horizonY + 1f * px)
                cubicTo(w * 0.7f, horizonY - 3f * px, w * 0.86f, horizonY + 4f * px, w, horizonY - 2f * px)
                lineTo(w, groundTop)
                lineTo(0f, groundTop)
                close()
            }
            val art = skylineArt(context, w.toInt())
                ?: run {
                    canvas.drawPath(hills, paint)
                    return
                }
            // The panorama is drawn 3:1 precisely so it spans the card edge to edge and
            // still clears the sun's apex — the old single-building art was 1.48:1, which
            // the short wide sky band held to about two fifths of the width however it was
            // scaled, leaving the horizon either side of it bare.
            val baseline = horizonY + 2f * px
            val targetH = min(w * art.height / art.width, (horizonY - titleBlock) * 0.94f)
            val drawW = targetH * art.width / art.height
            val left = (w - drawW) / 2f
            val dst = RectF(left, baseline - targetH, left + drawW, baseline)

            canvas.drawPath(hills, paint)

            // Drawn in its own colours at every hour, on purpose.
            //
            // It used to flatten toward a silhouette as the sun went down — which is what
            // happens to a real building at dusk, but on a widget it cost the thing its
            // legibility: by Isha the dome and the palms were a slate shape barely separable
            // from the night sky. A card you cannot read at night is worse than one that is
            // not quite photographic, so the artwork keeps its greens and creams and only
            // the sky behind it changes.
            canvas.drawBitmap(
                art,
                null,
                dst,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }

        /**
         * Where the sun actually is, on the track rather than on a decorative curve.
         *
         * This used to evaluate the Catmull-Rom spline between the two bracketing prayers,
         * so the drawn sun sat wherever the interpolation happened to put it — near enough
         * most of the day, and visibly off around the shoulders of the curve.
         */
        private fun sunPositionNow(): Pair<Float, Float>? {
            val minute = sky.now.toDouble()
            if (minute < sunSampleMinutes.first() || minute > sunSampleMinutes.last()) return null
            return timeToX(minute) to yFor(altitude(minute))
        }

        /**
         * The sun's path, drawn as a day in progress rather than a uniform thread.
         *
         * The part the sun has already travelled is lit; the part still to come is a faint
         * guide. One line at one weight said only "the sun goes this way" — split at the
         * current minute it also says how much of the day has gone, which is the thing
         * someone glancing at a prayer widget actually wants to know.
         */
        private fun drawPath(nodes: List<Node>) {
            fun pathOf(from: Int, to: Int): Path? {
                if (to - from < 1) return null
                return Path().apply {
                    moveTo(sunTrack[from].x, sunTrack[from].y)
                    for (i in from + 1..to) lineTo(sunTrack[i].x, sunTrack[i].y)
                }
            }
            val thread = if (palette.dark) 0xFFCFD6F2.toInt() else 0xFFFFE9A8.toInt()
            // The sample the sun has just passed, so the two halves meet under it.
            val reached = sunSampleMinutes
                .indexOfLast { it <= sky.now.toDouble() }
                .coerceIn(0, sunTrack.size - 1)

            pathOf(reached, sunTrack.size - 1)?.let { ahead ->
                canvas.drawPath(
                    ahead,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 1.2f * px
                        strokeCap = Paint.Cap.ROUND
                        color = thread
                        alpha = 70
                        pathEffect = DashPathEffect(floatArrayOf(3f * px, 4f * px), 0f)
                    },
                )
            }
            pathOf(0, reached)?.let { travelled ->
                canvas.drawPath(
                    travelled,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 7f * px
                        strokeCap = Paint.Cap.ROUND
                        color = thread
                        alpha = 48
                    },
                )
                canvas.drawPath(
                    travelled,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 2.1f * px
                        strokeCap = Paint.Cap.ROUND
                        color = thread
                        alpha = 235
                    },
                )
            }
        }

        private fun drawLuminaryGlow(nodes: List<Node>) {
            val (x, y) = sunPositionNow() ?: return
            val isDay = sky.now in sky.sunrise..sky.maghrib
            if (!isDay) return
            val reach = w * 0.45f
            canvas.drawCircle(
                x,
                y,
                reach,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(x, y, reach, intArrayOf(0x8CFFE1A6.toInt(), 0x33FFC77A, 0x00FFC77A), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
                },
            )
        }

        /**
         * Between Isha and Fajr the sun's path is over, so the moon takes its own arc across
         * the sky: rising at the right after Isha, highest at the middle of the night, setting
         * at the left before Fajr.
         */
        private fun moonAtNight(): Pair<Float, Float>? {
            val dayMinutes = 24 * 60
            val nightLength = (sky.fajr + dayMinutes - sky.isha).coerceAtLeast(1)
            val sinceIsha = when {
                sky.now >= sky.isha -> sky.now - sky.isha
                sky.now < sky.fajr -> sky.now + dayMinutes - sky.isha
                else -> return null
            }
            val f = (sinceIsha.toFloat() / nightLength).coerceIn(0f, 1f)
            val x = w * (0.88f - 0.76f * f)
            val y = apexY + 8f * px + (1f - sin(PI * f).toFloat()) * (horizonY - apexY) * 0.55f
            return x to y
        }

        private fun drawLuminary(nodes: List<Node>) {
            val (x, y) = sunPositionNow() ?: moonAtNight() ?: return
            val r = (if (compact) 7f else 9f) * px
            if (sky.now in sky.sunrise..sky.maghrib) {
                canvas.drawCircle(
                    x,
                    y,
                    r * 2.4f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(x, y, r * 2.4f, 0xAAFFF1C2.toInt(), 0x00FFF1C2, Shader.TileMode.CLAMP)
                    },
                )
                canvas.drawCircle(
                    x,
                    y,
                    r,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.3f, intArrayOf(0xFFFFFBEA.toInt(), 0xFFFFE28A.toInt(), 0xFFF7B24A.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                    },
                )
            } else {
                drawMoon(x, y, r * 0.9f)
            }
        }

        /**
         * The moon at tonight's phase. Lit fraction follows the Hijri day (new at 1, full near
         * 15); the shadow disc is offset away from the lit limb — right for the waxing half,
         * left for the waning half, as seen from the northern hemisphere.
         */
        private fun drawMoon(x: Float, y: Float, r: Float) {
            canvas.drawCircle(
                x,
                y,
                r * 3f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(x, y, r * 3f, 0x55E8ECFF, 0x00E8ECFF, Shader.TileMode.CLAMP)
                },
            )
            val phase = ((sky.hijriDay - 1) / 29.53).toFloat().coerceIn(0f, 1f)
            val waxing = phase <= 0.5f
            val lit = (if (waxing) phase * 2f else (1f - phase) * 2f).coerceIn(0f, 1f)
            // Earthshine so a new moon still reads as a disc.
            canvas.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2EF6F0D8 })
            if (lit < 0.03f) return
            val layer = canvas.saveLayer(x - r * 4f, y - r * 1.5f, x + r * 4f, y + r * 1.5f, null)
            canvas.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF6F0D8.toInt() })
            if (lit < 0.97f) {
                val offset = 2f * r * lit * (if (waxing) -1f else 1f)
                canvas.drawCircle(
                    x + offset,
                    y,
                    r,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    },
                )
            }
            canvas.restoreToCount(layer)
        }

        /** Each prayer is a point of light on the path; the next one burns brighter. */
        private fun drawNodes(nodes: List<Node>) {
            input.prayers.forEachIndexed { column, prayer ->
                val node = nodeForColumn(nodes, column)
                val core = if (palette.dark) 0xFFFFFFFF.toInt() else 0xFFFFF4D0.toInt()
                val glow = if (prayer.isNext) {
                    0xFFFFD86A.toInt()
                } else if (palette.dark) {
                    0xFFCFD6F2.toInt()
                } else {
                    0xFFFFE9A8.toInt()
                }
                val r = (if (prayer.isNext) 4.6f else 3.2f) * px
                canvas.drawCircle(
                    node.x,
                    node.y,
                    r * 3.2f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(node.x, node.y, r * 3.2f, (glow and 0x00FFFFFF) or 0x99000000.toInt(), glow and 0x00FFFFFF, Shader.TileMode.CLAMP)
                    },
                )
                canvas.drawCircle(node.x, node.y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = core })
                // A hairline down to the label so the eye connects point and name.
                canvas.drawLine(
                    node.x,
                    node.y + r + 3f * px,
                    node.x,
                    groundTop - 2f * px,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        strokeWidth = 1f * px
                        color = core
                        alpha = if (prayer.isNext) 110 else 55
                    },
                )
            }
        }

        private fun nodeForColumn(nodes: List<Node>, column: Int): Node =
            nodes[(if (column == 0) 0 else column + 1).coerceIn(0, nodes.size - 1)]

        private fun drawGround() {
            canvas.drawRect(
                0f,
                groundTop,
                w,
                h,
                Paint().apply {
                    shader = LinearGradient(0f, groundTop, 0f, h, 0xFF0E2926.toInt(), 0xFF081A18.toInt(), Shader.TileMode.CLAMP)
                },
            )
            canvas.drawLine(
                0f,
                groundTop,
                w,
                groundTop,
                Paint().apply {
                    color = 0x33FFFFFF
                    strokeWidth = 1f * px
                },
            )
        }

        private fun drawTitle() {
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = bold
                textSize = (if (compact) 13f else 15f) * tp
                color = palette.ink
            }
            val barX = 12f * px
            val titleTop = 9f * tp
            canvas.drawRoundRect(RectF(barX, titleTop, barX + 3.5f * tp, titleTop + title.textSize * 1.15f), 2f * px, 2f * px, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.ink })
            canvas.drawText("Today's Prayers", barX + 9f * tp, titleTop + title.textSize * 0.98f, title)
            // Day and night lengths, quietly at the right.
            val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = medium
                textSize = (if (compact) 8f else 9f) * tp
                color = palette.ink
                alpha = 200
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("${input.daylightLabel}   ·   ${input.nightLabel}", w - 12f * px, 9f * tp + title.textSize * 0.9f, caption)
        }

        private fun drawLabels() {
            val ink = 0xFFF4F1E6.toInt()
            val muted = 0xFFB9C7C0.toInt()
            val nameSize = (if (compact) 10.5f else 11.5f) * tp
            val timeSize = (if (compact) 9.5f else 10.5f) * tp
            val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = bold
                textSize = nameSize
                textAlign = Paint.Align.CENTER
            }
            val time = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = medium
                textSize = timeSize
                textAlign = Paint.Align.CENTER
            }
            val nameY = groundTop + nameSize + 5f * tp
            val timeY = nameY + timeSize + 2f * tp
            val statusCy = timeY + 13f * tp
            input.prayers.forEachIndexed { column, prayer ->
                val cx = columnX(column)
                name.color = if (prayer.isNext) 0xFFFFD86A.toInt() else ink
                time.color = if (prayer.isNext) ink else muted
                canvas.drawText(prayer.name, cx, nameY, name)
                canvas.drawText(prayer.time, cx, timeY, time)
                drawStatus(prayer, cx, statusCy)
            }
        }

        private fun drawStatus(prayer: WidgetPrayer, cx: Float, cy: Float) {
            val label = prayer.relativeLabel
            when {
                prayer.isNext && label != null -> pill(cx, cy, label, gold = true)
                label != null -> pill(cx, cy, label, gold = false)
                prayer.isPast -> {
                    canvas.drawCircle(cx, cy, 7.5f * tp, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2EFFFFFF })
                    canvas.drawCircle(
                        cx,
                        cy,
                        7.5f * tp,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = 1f * px
                            color = 0x40FFFFFF
                        },
                    )
                    val check = Path().apply {
                        moveTo(cx - 3.2f * tp, cy)
                        lineTo(cx - 0.9f * tp, cy + 2.3f * tp)
                        lineTo(cx + 3.4f * tp, cy - 2.5f * tp)
                    }
                    canvas.drawPath(
                        check,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = 1.6f * px
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            color = 0xFFF4F1E6.toInt()
                        },
                    )
                }
                else -> canvas.drawCircle(
                    cx,
                    cy,
                    6f * tp,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 1.8f * px
                        color = 0xFFFFD86A.toInt()
                        alpha = 210
                    },
                )
            }
        }

        /**
         * A capsule with real air around its text. The gold one (next prayer) carries a soft
         * vertical gradient and a glow; the quiet one (a past prayer's "ago") is a translucent
         * sheet with a hairline edge so it holds its shape on both dark ground and starlight.
         */
        private fun pill(cx: Float, cy: Float, text: String, gold: Boolean) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = medium
                textSize = (if (compact) 9f else 10f) * pp
                textAlign = Paint.Align.CENTER
                color = if (gold) 0xFF0E2926.toInt() else 0xFFF4F1E6.toInt()
            }
            val tw = paint.measureText(text)
            val ph = (if (compact) 17f else 19f) * pp
            val padX = 10f * pp
            val rect = RectF(cx - tw / 2 - padX, cy - ph / 2, cx + tw / 2 + padX, cy + ph / 2)
            if (gold) {
                canvas.drawRoundRect(
                    RectF(rect.left - 3f * pp, rect.top - 3f * pp, rect.right + 3f * pp, rect.bottom + 3f * pp),
                    ph,
                    ph,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFD86A },
                )
                canvas.drawRoundRect(
                    rect,
                    ph / 2,
                    ph / 2,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(0f, rect.top, 0f, rect.bottom, 0xFFFFE28A.toInt(), 0xFFF5C244.toInt(), Shader.TileMode.CLAMP)
                    },
                )
            } else {
                canvas.drawRoundRect(rect, ph / 2, ph / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24FFFFFF })
                canvas.drawRoundRect(
                    rect,
                    ph / 2,
                    ph / 2,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 1f * px
                        color = 0x3DFFFFFF
                    },
                )
            }
            canvas.drawText(text, cx, cy + paint.textSize * 0.35f, paint)
        }

        private fun font(res: Int): Typeface =
            runCatching { ResourcesCompat.getFont(context, res) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }
}
