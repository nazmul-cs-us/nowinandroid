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
import android.graphics.Rect
import android.util.Log
import androidx.annotation.RawRes
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.starception.submission.R

/**
 * Turns the app's animated Meteocons into still bitmaps a widget can show.
 *
 * A widget's content is RemoteViews, drawn by the launcher's process from a serialised
 * description — there is no Lottie there and no frame loop to drive it, so the animated
 * icon used on the prayer screen cannot be reused directly. Rasterising one frame is the
 * only way to keep the same artwork and the same weather-code mapping in both places
 * rather than maintaining a second, divergent set of static icons.
 *
 * The frame is taken partway into the loop instead of at 0, because Meteocons open on a
 * near-empty canvas (clouds drift in, rain has not fallen yet) and frame 0 reads as a
 * blank or half-drawn icon.
 */
internal object WidgetMeteocons {

    private const val TAG = "PrayerWidget"

    /** Far enough in for every element to have entered, before any loop-out begins. */
    private const val REPRESENTATIVE_FRAME = 0.45f

    // Bitmaps travel to the launcher over Binder, which caps a RemoteViews payload at
    // about 1 MB. At 96px ARGB_8888 each icon is ~37 KB, so a five-prayer strip built
    // for all five responsive size buckets stays comfortably inside the budget.
    private const val ICON_PX = 96

    /** Four updates per second, with a short crossfade in the widget host. */
    private const val WEATHER_FRAME_COUNT = 18
    // Sunrise/sunset has eight identical rays, so its visible artwork repeats every
    // one-eighth of the six-second Lottie loop. Twelve samples across that 45-degree arc
    // produce 3.75-degree steps at ~16fps without storing eight duplicate rotations.
    private const val SOLAR_FRAME_COUNT = 12

    /**
     * Weather glyphs are displayed at 17–24dp, so 44px retains their detail while keeping
     * all five animated prayer rows below Binder's RemoteViews bitmap budget. The larger
     * header solar icon gets a denser 56px raster.
     */
    private const val WEATHER_ANIMATED_ICON_PX = 44
    private const val SOLAR_ANIMATED_ICON_PX = 56

    /**
     * Where the sampled loop starts, for the same reason [REPRESENTATIVE_FRAME] is not 0:
     * a Meteocon opens on a near-empty canvas. Sampling from 0 put that blank frame into
     * the flipper, so the icon vanished for 450ms out of every 2.7s — a blink, not an
     * animation. Starting past the entrance costs the drift-in, which at 20dp was never
     * legible anyway, and keeps all six frames on artwork that is actually drawn.
     */
    private const val FRAME_WINDOW_START = 0.25f

    private val cache = HashMap<Int, Bitmap?>()
    private val frameCache = HashMap<Int, List<Bitmap>>()

    /**
     * Bitmap for [weatherCode], or null if the artwork could not be loaded — callers
     * should treat null as "show no icon" rather than substituting a placeholder.
     */
    @Synchronized
    fun forWeather(context: Context, weatherCode: Int, isDay: Boolean): Bitmap? {
        val resource = meteoconResource(weatherCode, isDay)
        return cache.getOrPut(resource) { render(context, resource) }
    }

    /**
     * Frames spread evenly across the Meteocon's visible loop, for playback in a
     * ViewFlipper. Empty when the artwork could not be rendered, which callers should
     * treat as "fall back to the still icon".
     */
    @Synchronized
    fun animationFrames(context: Context, weatherCode: Int, isDay: Boolean): List<Bitmap> {
        val resource = meteoconResource(weatherCode, isDay)
        return frameCache.getOrPut(resource) {
            renderFrames(
                context = context,
                resource = resource,
                frameCount = WEATHER_FRAME_COUNT,
                iconPx = WEATHER_ANIMATED_ICON_PX,
            )
        }
    }

    /** Dedicated multicolour Fill artwork for the next sunrise or sunset. */
    @Synchronized
    fun forSolarEvent(context: Context, isSunset: Boolean): Bitmap? {
        val resource = solarEventResource(isSunset)
        return cache.getOrPut(resource) { render(context, resource) }
    }

    /** Frames for the dedicated Fill sunrise/sunset artwork used in the widget header. */
    @Synchronized
    fun solarAnimationFrames(context: Context, isSunset: Boolean): List<Bitmap> {
        val resource = solarEventResource(isSunset)
        // Unlike condition icons, sunrise/sunset has no entrance to skip: its only
        // motion is one full rotation of eight symmetric rays. Sampling the generic
        // 25%-to-100% window produced 45-degree steps, which are identical for an
        // eight-ray sun and therefore appeared completely static.
        return frameCache.getOrPut(resource) {
            renderFrames(
                context = context,
                resource = resource,
                frameWindowStart = 0f,
                frameWindowEnd = 1f / 8f,
                frameCount = SOLAR_FRAME_COUNT,
                iconPx = SOLAR_ANIMATED_ICON_PX,
            )
        }
    }

    private fun renderFrames(
        context: Context,
        @RawRes resource: Int,
        frameCount: Int,
        iconPx: Int,
        frameWindowStart: Float = FRAME_WINDOW_START,
        frameWindowEnd: Float = 1f,
    ): List<Bitmap> = try {
        val composition = LottieCompositionFactory.fromRawResSync(context, resource).value
        if (composition == null) {
            Log.w(TAG, "Meteocon $resource could not be parsed for animation")
            emptyList()
        } else {
            val drawable = LottieDrawable().apply {
                setComposition(composition)
                setBounds(0, 0, iconPx, iconPx)
            }
            // Every frame is trimmed to the same box (computed from the fullest frame)
            // so the glyph does not jitter as the animation advances.
            List(frameCount) { index ->
                // Spread over [FRAME_WINDOW_START, 1f), stopping short of 1f: the last
                // frame of a loop is the same image as the first, and holding it twice
                // makes the animation visibly stutter.
                drawable.progress = frameWindowStart +
                    index.toFloat() * (frameWindowEnd - frameWindowStart) / frameCount
                Bitmap.createBitmap(
                    iconPx,
                    iconPx,
                    Bitmap.Config.ARGB_8888,
                ).also { drawable.draw(Canvas(it)) }
            }.let { frames ->
                val box = frames.fold(null as Rect?) { acc, f ->
                    f.opaqueBounds()?.let { b -> acc?.apply { union(b) } ?: Rect(b) } ?: acc
                } ?: return@let frames
                frames.map { Bitmap.createBitmap(it, box.left, box.top, box.width(), box.height()) }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Meteocon $resource could not be animated", e)
        emptyList()
    }

    private fun render(context: Context, @RawRes resource: Int): Bitmap? = try {
        // The Sync variant is deliberate: this runs inside the widget's suspending data
        // load, already off the main thread, and the async callback form would let
        // provideContent compose before the artwork arrived.
        val composition = LottieCompositionFactory
            .fromRawResSync(context, resource)
            .value

        if (composition == null) {
            Log.w(TAG, "Meteocon $resource could not be parsed")
            null
        } else {
            val drawable = LottieDrawable().apply {
                setComposition(composition)
                progress = REPRESENTATIVE_FRAME
                setBounds(0, 0, ICON_PX, ICON_PX)
            }
            Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
                .also { drawable.draw(Canvas(it)) }
                .trimTransparentBorder()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Meteocon $resource could not be rendered", e)
        null
    }

    /**
     * Bounding box of everything that is not fully transparent, or null if nothing is.
     *
     * Meteocons are authored on a square canvas with generous margins, so the rendered
     * bitmap is mostly empty space. Left untrimmed, an Image sized to 26dp draws a glyph
     * roughly half that, and the text beside it starts wherever the empty margin ends.
     */
    private fun Bitmap.opaqueBounds(): Rect? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        val row = IntArray(width)
        for (y in 0 until height) {
            getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                if (row[x] ushr 24 > 8) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < left || bottom < top) null else Rect(left, top, right + 1, bottom + 1)
    }

    private fun Bitmap.trimTransparentBorder(): Bitmap {
        val box = opaqueBounds() ?: return this
        return Bitmap.createBitmap(this, box.left, box.top, box.width(), box.height())
    }

    @RawRes
    private fun solarEventResource(isSunset: Boolean): Int = if (isSunset) {
        R.raw.meteocon_fill_sunset
    } else {
        R.raw.meteocon_fill_sunrise
    }

    /**
     * Open-Meteo WMO weather code to Meteocon artwork.
     *
     * Mirrors the mapping the prayer screen uses so the widget never disagrees with the
     * app about the same hour's sky.
     *
     * Uses Meteocons' official Fill collection throughout. These resources are the exact
     * versioned Lottie files from the same collection as the sunrise/sunset header icons,
     * so their built-in gradients and colours must be preserved by callers.
     */
    @RawRes
    private fun meteoconResource(weatherCode: Int, isDay: Boolean): Int = when (weatherCode) {
        0 -> if (isDay) R.raw.meteocon_fill_clear_day else R.raw.meteocon_fill_clear_night
        1, 2 -> if (isDay) {
            R.raw.meteocon_fill_partly_cloudy_day
        } else {
            R.raw.meteocon_fill_partly_cloudy_night
        }
        3 -> if (isDay) R.raw.meteocon_fill_overcast_day else R.raw.meteocon_fill_overcast_night
        45, 48 -> if (isDay) R.raw.meteocon_fill_fog_day else R.raw.meteocon_fill_fog_night
        in 51..57 -> R.raw.meteocon_fill_drizzle
        in 61..67, in 80..82 -> R.raw.meteocon_fill_rain
        in 71..77, 85, 86 -> R.raw.meteocon_fill_snow
        in 95..99 -> if (isDay) {
            R.raw.meteocon_fill_thunderstorms_day
        } else {
            R.raw.meteocon_fill_thunderstorms_night
        }
        else -> R.raw.meteocon_fill_cloudy
    }
}
