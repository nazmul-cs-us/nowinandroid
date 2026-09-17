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
import android.graphics.Canvas
import android.graphics.PorterDuffColorFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.starception.submission.R
import com.starception.submission.core.designsystem.R as DesignR
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

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

    class Input(
        val sky: WidgetSky,
        val prayers: List<WidgetPrayer>,
        val daylightLabel: String,
        val nightLabel: String,
        val cornerRadiusDp: Float,
    )

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

        private val titleBlock = (if (compact) 26f else 30f) * px
        private val labelBlock = (if (compact) 48f else 56f) * px
        private val groundTop = h - labelBlock
        private val horizonY = titleBlock + (groundTop - titleBlock) * 0.72f
        private val apexY = titleBlock + 10f * px
        private val nadirY = groundTop - 6f * px

        private val bold = font(DesignR.font.ubuntu_sans_bold)
        private val medium = font(DesignR.font.ubuntu_sans_medium)

        // Where the sun is now decides the palette for the whole card.
        private val nowAltitude = altitude(sky.now)
        private val palette = paletteFor(nowAltitude)

        fun draw() {
            drawSky()
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

        private fun altitude(minute: Int): Double {
            val declination = 23.44 * sin(2.0 * PI * (284 + sky.dayOfYear) / 365.0)
            val hourAngle = 15.0 * (minute - sky.dhuhr) / 60.0
            val lat = Math.toRadians(sky.latitude)
            val dec = Math.toRadians(declination)
            val sinAlt = sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(Math.toRadians(hourAngle))
            return Math.toDegrees(asin(sinAlt.coerceIn(-1.0, 1.0)))
        }

        private val apexAltitude = altitude(sky.dhuhr).coerceAtLeast(10.0)

        private fun yFor(altitude: Double): Float = if (altitude >= 0) {
            horizonY - ((altitude / apexAltitude).coerceIn(0.0, 1.0) * (horizonY - apexY)).toFloat()
        } else {
            horizonY + ((-altitude / 20.0).coerceIn(0.0, 1.0) * (nadirY - horizonY)).toFloat()
        }

        private fun columnX(index: Int): Float = w * (index + 0.5f) / columns

        class Node(val x: Float, val y: Float, val minute: Int)

        private fun pathPoints(): List<Node> {
            val minutes = listOf(sky.fajr, sky.dhuhr, sky.asr, sky.maghrib, sky.isha)
            val nodes = minutes.mapIndexed { index, minute ->
                Node(columnX(index), yFor(if (index == 3) 0.0 else altitude(minute)), minute)
            }.toMutableList()
            val riseFraction = ((sky.sunrise - sky.fajr).toFloat() / (sky.dhuhr - sky.fajr).coerceAtLeast(1))
                .coerceIn(0.05f, 0.95f)
            nodes.add(1, Node(nodes[0].x + (nodes[1].x - nodes[0].x) * riseFraction, horizonY, sky.sunrise))
            return nodes
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
                0f, 0f, w, groundTop,
                Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, groundTop,
                        intArrayOf(palette.top, palette.mid, palette.horizon, palette.horizon),
                        floatArrayOf(0f, 0.55f, 0.9f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                },
            )
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
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; alpha = if (palette.dark) 245 else 185 }
            val hills = Path().apply {
                moveTo(0f, horizonY + 6f * px)
                cubicTo(w * 0.12f, horizonY - 5f * px, w * 0.22f, horizonY + 4f * px, w * 0.34f, horizonY - 1f * px)
                cubicTo(w * 0.42f, horizonY - 4f * px, w * 0.5f, horizonY + 3f * px, w * 0.6f, horizonY + 1f * px)
                cubicTo(w * 0.7f, horizonY - 3f * px, w * 0.86f, horizonY + 4f * px, w, horizonY - 2f * px)
                lineTo(w, groundTop)
                lineTo(0f, groundTop)
                close()
            }
            val mask = skyline ?: run { canvas.drawPath(hills, paint); return }
            // Skyline: centred, spanning most of the width, baseline on the horizon, its
            // height a share of the sky so the great dome never crowds the sun's apex.
            // Height up to 82% of the sky, width following, centred on the card.
            val targetW = w * 0.8f
            val targetH = min(targetW * mask.height / mask.width, (horizonY - titleBlock) * 0.82f)
            val drawW = targetH * mask.width / mask.height
            val left = (w - drawW) / 2f
            val top = horizonY + 2f * px - targetH
            val dst = RectF(left, top, left + drawW, horizonY + 2f * px)
            if (palette.dark) {
                val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = PorterDuffColorFilter(0xFF8FA3D9.toInt(), PorterDuff.Mode.SRC_IN); alpha = 120
                }
                canvas.drawBitmap(mask, null, RectF(dst.left, dst.top - 1.6f * px, dst.right, dst.bottom - 1.6f * px), rim)
            }
            canvas.drawPath(hills, paint)
            canvas.drawBitmap(mask, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(ink, PorterDuff.Mode.SRC_IN); alpha = paint.alpha
            })
        }

        /**
         * Masjid an-Nabawi in silhouette, from the app's own traced vector, rasterised once
         * at a width that stays crisp after the launcher scales the card.
         */
        private val skyline: Bitmap? by lazy {
            runCatching {
                val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.ic_masjid_nabawi_silhouette, null)
                    ?: return@runCatching null
                val targetW = 1400
                val targetH = (targetW * drawable.intrinsicHeight.toFloat() / drawable.intrinsicWidth).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, targetW, targetH)
                drawable.draw(Canvas(bitmap))
                trimTransparent(bitmap)
            }.getOrNull()
        }

        /** Crops fully transparent margins so the silhouette's baseline meets the horizon. */
        private fun trimTransparent(bitmap: Bitmap): Bitmap {
            val w = bitmap.width; val h = bitmap.height
            val row = IntArray(w)
            var top = 0; var bottom = h - 1; var left = 0; var right = w - 1
            fun rowHasInk(y: Int): Boolean { bitmap.getPixels(row, 0, w, 0, y, w, 1); return row.any { (it ushr 24) > 16 } }
            while (top < bottom && !rowHasInk(top)) top++
            while (bottom > top && !rowHasInk(bottom)) bottom--
            val col = IntArray(h)
            fun colHasInk(x: Int): Boolean { bitmap.getPixels(col, 0, 1, x, 0, 1, h); return col.any { (it ushr 24) > 16 } }
            while (left < right && !colHasInk(left)) left++
            while (right > left && !colHasInk(right)) right--
            return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
        }

        private fun drawPath(nodes: List<Node>) {
            val path = smoothPath(nodes)
            val thread = if (palette.dark) 0xFFCFD6F2.toInt() else 0xFFFFE9A8.toInt()
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 6f * px; color = thread; alpha = 40
            })
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.4f * px; strokeCap = Paint.Cap.ROUND; color = thread; alpha = 210
            })
        }

        private fun smoothPath(nodes: List<Node>): Path {
            val pts = buildList {
                add(Node(nodes.first().x - w * 0.14f, nodes.first().y + 4f * px, nodes.first().minute))
                addAll(nodes)
                add(Node(nodes.last().x + w * 0.14f, nodes.last().y + 4f * px, nodes.last().minute))
            }
            val path = Path()
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val p0 = pts[max(i - 1, 0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[min(i + 2, pts.size - 1)]
                path.cubicTo(
                    p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                    p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                    p2.x, p2.y,
                )
            }
            return path
        }

        private fun positionNow(nodes: List<Node>): Pair<Float, Float>? {
            val now = sky.now
            if (now < nodes.first().minute || now > nodes.last().minute) return null
            val i = nodes.indices.first { idx -> idx == nodes.size - 2 || now < nodes[idx + 1].minute }
            val p1 = nodes[i]; val p2 = nodes[i + 1]; val p0 = nodes[max(i - 1, 0)]; val p3 = nodes[min(i + 2, nodes.size - 1)]
            val t = ((now - p1.minute).toFloat() / (p2.minute - p1.minute).coerceAtLeast(1)).coerceIn(0f, 1f)
            val c1x = p1.x + (p2.x - p0.x) / 6f; val c1y = p1.y + (p2.y - p0.y) / 6f
            val c2x = p2.x - (p3.x - p1.x) / 6f; val c2y = p2.y - (p3.y - p1.y) / 6f
            val u = 1 - t
            return (u * u * u * p1.x + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * p2.x) to
                (u * u * u * p1.y + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * p2.y)
        }

        /** The warm haze the low sun throws across the sky, painted before the skyline. */
        private fun drawLuminaryGlow(nodes: List<Node>) {
            val (x, y) = positionNow(nodes) ?: return
            val isDay = sky.now in sky.sunrise..sky.maghrib
            if (!isDay) return
            val reach = w * 0.45f
            canvas.drawCircle(x, y, reach, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(x, y, reach, intArrayOf(0x8CFFE1A6.toInt(), 0x33FFC77A, 0x00FFC77A), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            })
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
            val (x, y) = positionNow(nodes) ?: moonAtNight() ?: return
            val r = (if (compact) 7f else 9f) * px
            if (sky.now in sky.sunrise..sky.maghrib) {
                canvas.drawCircle(x, y, r * 2.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(x, y, r * 2.4f, 0xAAFFF1C2.toInt(), 0x00FFF1C2, Shader.TileMode.CLAMP)
                })
                canvas.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.3f, intArrayOf(0xFFFFFBEA.toInt(), 0xFFFFE28A.toInt(), 0xFFF7B24A.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                })
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
            canvas.drawCircle(x, y, r * 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(x, y, r * 3f, 0x55E8ECFF, 0x00E8ECFF, Shader.TileMode.CLAMP)
            })
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
                canvas.drawCircle(x + offset, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                })
            }
            canvas.restoreToCount(layer)
        }

        /** Each prayer is a point of light on the path; the next one burns brighter. */
        private fun drawNodes(nodes: List<Node>) {
            input.prayers.forEachIndexed { column, prayer ->
                val node = nodeForColumn(nodes, column)
                val core = if (palette.dark) 0xFFFFFFFF.toInt() else 0xFFFFF4D0.toInt()
                val glow = if (prayer.isNext) 0xFFFFD86A.toInt() else if (palette.dark) 0xFFCFD6F2.toInt() else 0xFFFFE9A8.toInt()
                val r = (if (prayer.isNext) 4.6f else 3.2f) * px
                canvas.drawCircle(node.x, node.y, r * 3.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(node.x, node.y, r * 3.2f, (glow and 0x00FFFFFF) or 0x99000000.toInt(), glow and 0x00FFFFFF, Shader.TileMode.CLAMP)
                })
                canvas.drawCircle(node.x, node.y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = core })
                // A hairline down to the label so the eye connects point and name.
                canvas.drawLine(node.x, node.y + r + 3f * px, node.x, groundTop - 2f * px, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    strokeWidth = 1f * px; color = core; alpha = if (prayer.isNext) 110 else 55
                })
            }
        }

        private fun nodeForColumn(nodes: List<Node>, column: Int): Node =
            nodes[(if (column == 0) 0 else column + 1).coerceIn(0, nodes.size - 1)]

        private fun drawGround() {
            canvas.drawRect(0f, groundTop, w, h, Paint().apply {
                shader = LinearGradient(0f, groundTop, 0f, h, 0xFF0E2926.toInt(), 0xFF081A18.toInt(), Shader.TileMode.CLAMP)
            })
            canvas.drawLine(0f, groundTop, w, groundTop, Paint().apply { color = 0x33FFFFFF; strokeWidth = 1f * px })
        }

        private fun drawTitle() {
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = bold; textSize = (if (compact) 13f else 15f) * px; color = palette.ink
            }
            val barX = 12f * px
            canvas.drawRoundRect(RectF(barX, 9f * px, barX + 3.5f * px, 9f * px + title.textSize * 1.15f), 2f * px, 2f * px, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.ink })
            canvas.drawText("Today's Prayers", barX + 9f * px, 9f * px + title.textSize * 0.98f, title)
            // Day and night lengths, quietly at the right.
            val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = medium; textSize = (if (compact) 8f else 9f) * px; color = palette.ink; alpha = 200; textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("${input.daylightLabel}   ·   ${input.nightLabel}", w - 12f * px, 9f * px + title.textSize * 0.9f, caption)
        }

        private fun drawLabels() {
            val ink = 0xFFF4F1E6.toInt()
            val muted = 0xFFB9C7C0.toInt()
            val nameSize = (if (compact) 10.5f else 11.5f) * px
            val timeSize = (if (compact) 9.5f else 10.5f) * px
            val name = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = nameSize; textAlign = Paint.Align.CENTER }
            val time = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = medium; textSize = timeSize; textAlign = Paint.Align.CENTER }
            val nameY = groundTop + nameSize + 5f * px
            val timeY = nameY + timeSize + 2f * px
            val statusCy = timeY + 13f * px
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
                    canvas.drawCircle(cx, cy, 7.5f * px, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2EFFFFFF })
                    canvas.drawCircle(cx, cy, 7.5f * px, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = 1f * px; color = 0x40FFFFFF
                    })
                    val check = Path().apply {
                        moveTo(cx - 3.2f * px, cy); lineTo(cx - 0.9f * px, cy + 2.3f * px); lineTo(cx + 3.4f * px, cy - 2.5f * px)
                    }
                    canvas.drawPath(check, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = 1.6f * px; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = 0xFFF4F1E6.toInt()
                    })
                }
                else -> canvas.drawCircle(cx, cy, 6f * px, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 1.8f * px; color = 0xFFFFD86A.toInt(); alpha = 210
                })
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
                textSize = (if (compact) 9f else 10f) * px
                textAlign = Paint.Align.CENTER
                color = if (gold) 0xFF0E2926.toInt() else 0xFFF4F1E6.toInt()
            }
            val tw = paint.measureText(text)
            val ph = (if (compact) 17f else 19f) * px
            val padX = 10f * px
            val rect = RectF(cx - tw / 2 - padX, cy - ph / 2, cx + tw / 2 + padX, cy + ph / 2)
            if (gold) {
                canvas.drawRoundRect(
                    RectF(rect.left - 3f * px, rect.top - 3f * px, rect.right + 3f * px, rect.bottom + 3f * px),
                    ph, ph,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFD86A },
                )
                canvas.drawRoundRect(rect, ph / 2, ph / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, rect.top, 0f, rect.bottom, 0xFFFFE28A.toInt(), 0xFFF5C244.toInt(), Shader.TileMode.CLAMP)
                })
            } else {
                canvas.drawRoundRect(rect, ph / 2, ph / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x24FFFFFF })
                canvas.drawRoundRect(rect, ph / 2, ph / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 1f * px; color = 0x3DFFFFFF
                })
            }
            canvas.drawText(text, cx, cy + paint.textSize * 0.35f, paint)
        }

        private fun font(res: Int): Typeface =
            runCatching { ResourcesCompat.getFont(context, res) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }
}
