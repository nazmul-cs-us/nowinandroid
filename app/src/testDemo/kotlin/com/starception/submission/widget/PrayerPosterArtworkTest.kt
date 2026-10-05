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
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrayerPosterArtworkTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun dayAndNightRenderAtTheReferenceAspect() {
        val sunrise = PrayerPosterArtwork.render(context, state(now = 370), 350f, 480f)
        val day = PrayerPosterArtwork.render(context, state(now = 12 * 60), 350f, 480f)
        val sunset = PrayerPosterArtwork.render(context, state(now = 1090), 350f, 480f)
        val night = PrayerPosterArtwork.render(context, state(now = 22 * 60), 350f, 480f)
        val preDawn = PrayerPosterArtwork.render(context, state(now = 4 * 60), 350f, 480f)

        assertEquals(day.width, night.width)
        assertEquals(day.height, night.height)
        assertTrue(day.width > 0 && day.height > day.width)
        assertNotEquals(day.getPixel(day.width / 2, day.height / 4), night.getPixel(night.width / 2, night.height / 4))
        // The single large environmental sun and moon move with time; individual prayer
        // points remain simple markers rather than additional celestial bodies.
        assertTrue(!sunrise.sameAs(day))
        assertTrue(!day.sameAs(sunset))
        assertTrue(!night.sameAs(preDawn))

        // Kept under build/ so local visual QA has a deterministic artifact without
        // changing source-controlled baselines on every prayer-time adjustment.
        val output = File("build/outputs/prayer-poster-previews").apply { mkdirs() }
        sunrise.writePng(File(output, "sunrise.png"))
        day.writePng(File(output, "day.png"))
        sunset.writePng(File(output, "sunset.png"))
        night.writePng(File(output, "night.png"))
        preDawn.writePng(File(output, "pre-dawn.png"))
        PrayerPosterArtwork.render(
            context = context,
            state = state(now = 12 * 60),
            widthDp = 450f,
            heightDp = 506f,
        ).writePng(File(output, "picker-preview-day.png"))

        val nightLightsOff = PrayerPosterArtwork.render(
            context = context,
            state = state(now = 22 * 60),
            widthDp = 350f,
            heightDp = 480f,
            isNightOverride = true,
            lighting = PrayerPosterArtwork.Lighting(mosqueLightIntensity = 0f),
        )
        assertTrue(!night.sameAs(nightLightsOff))
        nightLightsOff.writePng(File(output, "night-mosque-lights-off.png"))

        val timeline = PrayerPosterArtwork.renderTimelineCard(
            context = context,
            state = state(now = 22 * 60),
            widthDp = 374f,
            heightDp = 138f,
            isNightOverride = false,
        )
        assertTrue(timeline.width > timeline.height)
        timeline.writePng(File(output, "timeline-card-day.png"))
        PrayerPosterArtwork.renderTimelineCard(
            context = context,
            state = state(now = 12 * 60),
            widthDp = 374f,
            heightDp = 138f,
            isNightOverride = true,
        ).writePng(File(output, "timeline-card-night.png"))
    }

    @Test
    fun onlyPortraitFootprintsUseThePoster() {
        assertTrue(PrayerPosterArtwork.isSuitable(350f, 480f))
        assertTrue(!PrayerPosterArtwork.isSuitable(350f, 180f))
        assertTrue(!PrayerPosterArtwork.isSuitable(220f, 400f))
    }

    private fun state(now: Int): PrayerWidgetState.Available {
        val prayerTimes = listOf(
            WidgetPrayer("Fajr", "4:48 AM", isNext = false, isPast = now > 288),
            WidgetPrayer("Dhuhr", "12:15 PM", isNext = false, isPast = now > 735),
            WidgetPrayer("Asr", "3:43 PM", isNext = false, isPast = now > 943),
            WidgetPrayer("Maghrib", "6:23 PM", isNext = true, isPast = now > 1103),
            WidgetPrayer("Isha", "7:37 PM", isNext = false, isPast = now > 1177),
        )
        return PrayerWidgetState.Available(
            place = "Nadd Al Hamar",
            dateLabel = "Fri, 18 Sep 2026  •  7 Rabi' al-Thani 1448",
            currentWeather = WidgetWeather(icon = null, temperature = "31°", summary = "Clear sky"),
            nextPrayer = prayerTimes[3],
            countdown = "in 2h 5m",
            solarEvent = WidgetSolarEvent("Sunset", "6:23 PM", isSunset = true),
            prayers = prayerTimes,
            insight = null,
            windowProgress = 0.5f,
            reminder = DailyReminder("poster-test", "", "", target = null),
            ayah = WidgetAyah(
                text = "And to Allah belongs the east and the west. Wherever you turn, there is the Face of Allah.",
                citation = "Al-Baqarah 2:115",
                target = WidgetNavigationTarget.Surah(surahNumber = 2, ayahNumber = 115),
            ),
            dayPhase = if (now in 356 until 1103) WidgetDayPhase.DAY else WidgetDayPhase.NIGHT,
            daylightLabel = "Daylight 12h 27m",
            nightLabel = "Night 11h 33m",
            sky = WidgetSky(
                fajr = 288,
                sunrise = 356,
                dhuhr = 735,
                asr = 943,
                maghrib = 1103,
                isha = 1177,
                now = now,
                latitude = 25.17,
                longitude = 55.37,
                localMidnightEpochSeconds = 1_789_680_000,
                hijriDay = 7,
            ),
            prayerTimelineProgress = 0.65f,
        )
    }

    private fun Bitmap.writePng(file: File) {
        FileOutputStream(file).use { output ->
            assertTrue(compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
