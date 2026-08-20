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

    /**
     * Frames sampled per animated icon, matching the child count of
     * widget_meteocon_flipper.xml. Every frame is a separate bitmap crossing Binder, so
     * this is the single biggest lever on the widget's payload — raising it is what
     * breaks a widget with TransactionTooLargeException, not the number of widgets.
     */
    private const val FRAME_COUNT = 6

    /** 56px x 6 frames is ~75 KB per animated icon; 96px would nearly double that. */
    private const val ANIMATED_ICON_PX = 56

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
     * [FRAME_COUNT] frames spread evenly across the Meteocon's loop, for playback in a
     * ViewFlipper. Empty when the artwork could not be rendered, which callers should
     * treat as "fall back to the still icon".
     */
    @Synchronized
    fun animationFrames(context: Context, weatherCode: Int, isDay: Boolean): List<Bitmap> {
        val resource = meteoconResource(weatherCode, isDay)
        return frameCache.getOrPut(resource) { renderFrames(context, resource) }
    }

    private fun renderFrames(context: Context, @RawRes resource: Int): List<Bitmap> = try {
        val composition = LottieCompositionFactory.fromRawResSync(context, resource).value
        if (composition == null) {
            Log.w(TAG, "Meteocon $resource could not be parsed for animation")
            emptyList()
        } else {
            val drawable = LottieDrawable().apply {
                setComposition(composition)
                setBounds(0, 0, ANIMATED_ICON_PX, ANIMATED_ICON_PX)
            }
            // Every frame is trimmed to the same box (computed from the fullest frame)
            // so the glyph does not jitter as the animation advances.
            List(FRAME_COUNT) { index ->
                // Spread over [FRAME_WINDOW_START, 1f), stopping short of 1f: the last
                // frame of a loop is the same image as the first, and holding it twice
                // makes the animation visibly stutter.
                drawable.progress = FRAME_WINDOW_START +
                    index.toFloat() * (1f - FRAME_WINDOW_START) / FRAME_COUNT
                Bitmap.createBitmap(
                    ANIMATED_ICON_PX,
                    ANIMATED_ICON_PX,
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

    /**
     * Open-Meteo WMO weather code to Meteocon artwork.
     *
     * Mirrors the mapping the prayer screen uses so the widget never disagrees with the
     * app about the same hour's sky.
     *
     * The Mono style, not Fill, and the reason is contrast rather than taste. Fill draws
     * its clouds in near-white with the weather picked out in colour: on a photographic
     * launcher wallpaper that reads, but this icon sits on the widget's own light surface,
     * where a white cloud all but disappeared — a rain icon measured about 9% ink against
     * the card, and only its two blue drops were visible at all. Mono is a single black
     * silhouette, which the caller tints to a theme colour, so it carries the same weight
     * as the type beside it whatever the weather.
     */
    @RawRes
    private fun meteoconResource(weatherCode: Int, isDay: Boolean): Int = when (weatherCode) {
        0 -> if (isDay) R.raw.meteocon_mono_clear_day else R.raw.meteocon_mono_clear_night
        1, 2 -> if (isDay) {
            R.raw.meteocon_mono_partly_cloudy_day
        } else {
            R.raw.meteocon_mono_partly_cloudy_night
        }
        3 -> if (isDay) R.raw.meteocon_mono_overcast_day else R.raw.meteocon_mono_overcast_night
        45, 48 -> if (isDay) R.raw.meteocon_mono_fog_day else R.raw.meteocon_mono_fog_night
        in 51..57 -> R.raw.meteocon_mono_drizzle
        in 61..67, in 80..82 -> R.raw.meteocon_mono_rain
        in 71..77, 85, 86 -> R.raw.meteocon_mono_snow
        in 95..99 -> if (isDay) {
            R.raw.meteocon_mono_thunderstorms_day
        } else {
            R.raw.meteocon_mono_thunderstorms_night
        }
        else -> R.raw.meteocon_mono_cloudy
    }
}
