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
            List(FRAME_COUNT) { index ->
                // Stop short of 1f: the last frame of a loop is the same image as the
                // first, and holding it twice makes the animation visibly stutter.
                drawable.progress = index.toFloat() / FRAME_COUNT
                Bitmap.createBitmap(
                    ANIMATED_ICON_PX,
                    ANIMATED_ICON_PX,
                    Bitmap.Config.ARGB_8888,
                ).also { drawable.draw(Canvas(it)) }
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
            Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).also { bitmap ->
                drawable.draw(Canvas(bitmap))
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Meteocon $resource could not be rendered", e)
        null
    }

    /**
     * Open-Meteo WMO weather code to Meteocon artwork.
     *
     * Mirrors the mapping the prayer screen uses so the widget never disagrees with the
     * app about the same hour's sky. The Fill style is used throughout: the widget has
     * no palette tinting of its own, and Fill is the variant that stays legible on both
     * a light and a dark launcher wallpaper.
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
