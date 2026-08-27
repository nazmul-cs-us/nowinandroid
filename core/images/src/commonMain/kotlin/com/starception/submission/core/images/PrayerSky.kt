/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission.core.images

import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.prayer_sky_fajr_clear
import com.starception.submission.core.images.resources.prayer_sky_fajr_partly_cloudy
import com.starception.submission.core.images.resources.prayer_sky_fajr_overcast
import com.starception.submission.core.images.resources.prayer_sky_fajr_fog
import com.starception.submission.core.images.resources.prayer_sky_fajr_rain
import com.starception.submission.core.images.resources.prayer_sky_fajr_snow
import com.starception.submission.core.images.resources.prayer_sky_fajr_thunderstorm
import com.starception.submission.core.images.resources.prayer_sky_sunrise_clear
import com.starception.submission.core.images.resources.prayer_sky_sunrise_partly_cloudy
import com.starception.submission.core.images.resources.prayer_sky_sunrise_overcast
import com.starception.submission.core.images.resources.prayer_sky_sunrise_fog
import com.starception.submission.core.images.resources.prayer_sky_sunrise_rain
import com.starception.submission.core.images.resources.prayer_sky_sunrise_snow
import com.starception.submission.core.images.resources.prayer_sky_sunrise_thunderstorm
import com.starception.submission.core.images.resources.prayer_sky_dhuhr_clear
import com.starception.submission.core.images.resources.prayer_sky_dhuhr_partly_cloudy
import com.starception.submission.core.images.resources.prayer_sky_dhuhr_overcast
import com.starception.submission.core.images.resources.prayer_sky_dhuhr_fog
import com.starception.submission.core.images.resources.prayer_sky_dhuhr_rain
import com.starception.submission.core.images.resources.prayer_sky_dhuhr_snow
import com.starception.submission.core.images.resources.prayer_sky_dhuhr_thunderstorm
import com.starception.submission.core.images.resources.prayer_sky_asr_clear
import com.starception.submission.core.images.resources.prayer_sky_asr_partly_cloudy
import com.starception.submission.core.images.resources.prayer_sky_asr_overcast
import com.starception.submission.core.images.resources.prayer_sky_asr_fog
import com.starception.submission.core.images.resources.prayer_sky_asr_rain
import com.starception.submission.core.images.resources.prayer_sky_asr_snow
import com.starception.submission.core.images.resources.prayer_sky_asr_thunderstorm
import com.starception.submission.core.images.resources.prayer_sky_maghrib_clear
import com.starception.submission.core.images.resources.prayer_sky_maghrib_partly_cloudy
import com.starception.submission.core.images.resources.prayer_sky_maghrib_overcast
import com.starception.submission.core.images.resources.prayer_sky_maghrib_fog
import com.starception.submission.core.images.resources.prayer_sky_maghrib_rain
import com.starception.submission.core.images.resources.prayer_sky_maghrib_snow
import com.starception.submission.core.images.resources.prayer_sky_maghrib_thunderstorm
import com.starception.submission.core.images.resources.prayer_sky_isha_clear
import com.starception.submission.core.images.resources.prayer_sky_isha_partly_cloudy
import com.starception.submission.core.images.resources.prayer_sky_isha_overcast
import com.starception.submission.core.images.resources.prayer_sky_isha_fog
import com.starception.submission.core.images.resources.prayer_sky_isha_rain
import com.starception.submission.core.images.resources.prayer_sky_isha_snow
import com.starception.submission.core.images.resources.prayer_sky_isha_thunderstorm
import org.jetbrains.compose.resources.DrawableResource

/** The six points of the day the sky artwork is painted for. */
enum class PrayerSkyPhase {
    Fajr,
    Sunrise,
    Dhuhr,
    Asr,
    Maghrib,
    Isha,
}

/** Sky conditions the artwork covers. */
enum class PrayerSkyWeather {
    Clear,
    PartlyCloudy,
    Overcast,
    Fog,
    Rain,
    Snow,
    Thunderstorm,
}

/**
 * Which sky phase to paint, from minutes since midnight.
 *
 * Takes plain minute-of-day integers rather than a date type so it needs no
 * datetime dependency and both platforms can call it directly — Android already
 * had these values as minutes.
 *
 * The phases do not line up exactly with the prayers: the sky is already Fajr's
 * 45 minutes before Fajr, stays sunrise-lit for 75 minutes after sunrise, and
 * turns toward Maghrib 45 minutes early. That lead-in is what makes the artwork
 * track the sky rather than the schedule.
 */
fun prayerSkyPhase(
    nowMinute: Int,
    fajrMinute: Int = 300,
    sunriseMinute: Int = 390,
    asrMinute: Int = 930,
    maghribMinute: Int = 1_080,
    ishaMinute: Int = 1_200,
): PrayerSkyPhase {
    val fajrApproach = (fajrMinute - 45).coerceAtLeast(0)
    val sunriseEnd = (sunriseMinute + 75).coerceAtMost(1_439)
    val maghribApproach = (maghribMinute - 45).coerceAtLeast(asrMinute)

    return when {
        nowMinute < fajrApproach -> PrayerSkyPhase.Isha
        nowMinute < sunriseMinute -> PrayerSkyPhase.Fajr
        nowMinute < sunriseEnd -> PrayerSkyPhase.Sunrise
        nowMinute < asrMinute -> PrayerSkyPhase.Dhuhr
        nowMinute < maghribApproach -> PrayerSkyPhase.Asr
        nowMinute < ishaMinute -> PrayerSkyPhase.Maghrib
        else -> PrayerSkyPhase.Isha
    }
}

/**
 * Groups Open-Meteo WMO codes into the seven sky families.
 *
 * Shared because the grouping decides which artwork shows: if the platforms
 * disagreed here they would render different skies for the same forecast.
 */
fun prayerSkyWeather(weatherCode: Int?): PrayerSkyWeather = when (weatherCode) {
    1, 2 -> PrayerSkyWeather.PartlyCloudy
    3 -> PrayerSkyWeather.Overcast
    45, 48 -> PrayerSkyWeather.Fog
    in 51..67, in 80..82 -> PrayerSkyWeather.Rain
    in 71..77, 85, 86 -> PrayerSkyWeather.Snow
    in 95..99 -> PrayerSkyWeather.Thunderstorm
    else -> PrayerSkyWeather.Clear
}

/**
 * The sky artwork for a given moment and weather.
 *
 * Every combination of the two enums has artwork, so this is total and needs no
 * fallback branch — the compiler enforces that as the `when` is exhaustive.
 * Shared so Android and iOS show the same sky for the same conditions.
 */
fun prayerSkyResource(
    phase: PrayerSkyPhase,
    weather: PrayerSkyWeather,
): DrawableResource = when (phase to weather) {
        PrayerSkyPhase.Fajr to PrayerSkyWeather.Clear -> Res.drawable.prayer_sky_fajr_clear
        PrayerSkyPhase.Fajr to PrayerSkyWeather.PartlyCloudy -> Res.drawable.prayer_sky_fajr_partly_cloudy
        PrayerSkyPhase.Fajr to PrayerSkyWeather.Overcast -> Res.drawable.prayer_sky_fajr_overcast
        PrayerSkyPhase.Fajr to PrayerSkyWeather.Fog -> Res.drawable.prayer_sky_fajr_fog
        PrayerSkyPhase.Fajr to PrayerSkyWeather.Rain -> Res.drawable.prayer_sky_fajr_rain
        PrayerSkyPhase.Fajr to PrayerSkyWeather.Snow -> Res.drawable.prayer_sky_fajr_snow
        PrayerSkyPhase.Fajr to PrayerSkyWeather.Thunderstorm -> Res.drawable.prayer_sky_fajr_thunderstorm
        PrayerSkyPhase.Sunrise to PrayerSkyWeather.Clear -> Res.drawable.prayer_sky_sunrise_clear
        PrayerSkyPhase.Sunrise to PrayerSkyWeather.PartlyCloudy -> Res.drawable.prayer_sky_sunrise_partly_cloudy
        PrayerSkyPhase.Sunrise to PrayerSkyWeather.Overcast -> Res.drawable.prayer_sky_sunrise_overcast
        PrayerSkyPhase.Sunrise to PrayerSkyWeather.Fog -> Res.drawable.prayer_sky_sunrise_fog
        PrayerSkyPhase.Sunrise to PrayerSkyWeather.Rain -> Res.drawable.prayer_sky_sunrise_rain
        PrayerSkyPhase.Sunrise to PrayerSkyWeather.Snow -> Res.drawable.prayer_sky_sunrise_snow
        PrayerSkyPhase.Sunrise to PrayerSkyWeather.Thunderstorm -> Res.drawable.prayer_sky_sunrise_thunderstorm
        PrayerSkyPhase.Dhuhr to PrayerSkyWeather.Clear -> Res.drawable.prayer_sky_dhuhr_clear
        PrayerSkyPhase.Dhuhr to PrayerSkyWeather.PartlyCloudy -> Res.drawable.prayer_sky_dhuhr_partly_cloudy
        PrayerSkyPhase.Dhuhr to PrayerSkyWeather.Overcast -> Res.drawable.prayer_sky_dhuhr_overcast
        PrayerSkyPhase.Dhuhr to PrayerSkyWeather.Fog -> Res.drawable.prayer_sky_dhuhr_fog
        PrayerSkyPhase.Dhuhr to PrayerSkyWeather.Rain -> Res.drawable.prayer_sky_dhuhr_rain
        PrayerSkyPhase.Dhuhr to PrayerSkyWeather.Snow -> Res.drawable.prayer_sky_dhuhr_snow
        PrayerSkyPhase.Dhuhr to PrayerSkyWeather.Thunderstorm -> Res.drawable.prayer_sky_dhuhr_thunderstorm
        PrayerSkyPhase.Asr to PrayerSkyWeather.Clear -> Res.drawable.prayer_sky_asr_clear
        PrayerSkyPhase.Asr to PrayerSkyWeather.PartlyCloudy -> Res.drawable.prayer_sky_asr_partly_cloudy
        PrayerSkyPhase.Asr to PrayerSkyWeather.Overcast -> Res.drawable.prayer_sky_asr_overcast
        PrayerSkyPhase.Asr to PrayerSkyWeather.Fog -> Res.drawable.prayer_sky_asr_fog
        PrayerSkyPhase.Asr to PrayerSkyWeather.Rain -> Res.drawable.prayer_sky_asr_rain
        PrayerSkyPhase.Asr to PrayerSkyWeather.Snow -> Res.drawable.prayer_sky_asr_snow
        PrayerSkyPhase.Asr to PrayerSkyWeather.Thunderstorm -> Res.drawable.prayer_sky_asr_thunderstorm
        PrayerSkyPhase.Maghrib to PrayerSkyWeather.Clear -> Res.drawable.prayer_sky_maghrib_clear
        PrayerSkyPhase.Maghrib to PrayerSkyWeather.PartlyCloudy -> Res.drawable.prayer_sky_maghrib_partly_cloudy
        PrayerSkyPhase.Maghrib to PrayerSkyWeather.Overcast -> Res.drawable.prayer_sky_maghrib_overcast
        PrayerSkyPhase.Maghrib to PrayerSkyWeather.Fog -> Res.drawable.prayer_sky_maghrib_fog
        PrayerSkyPhase.Maghrib to PrayerSkyWeather.Rain -> Res.drawable.prayer_sky_maghrib_rain
        PrayerSkyPhase.Maghrib to PrayerSkyWeather.Snow -> Res.drawable.prayer_sky_maghrib_snow
        PrayerSkyPhase.Maghrib to PrayerSkyWeather.Thunderstorm -> Res.drawable.prayer_sky_maghrib_thunderstorm
        PrayerSkyPhase.Isha to PrayerSkyWeather.Clear -> Res.drawable.prayer_sky_isha_clear
        PrayerSkyPhase.Isha to PrayerSkyWeather.PartlyCloudy -> Res.drawable.prayer_sky_isha_partly_cloudy
        PrayerSkyPhase.Isha to PrayerSkyWeather.Overcast -> Res.drawable.prayer_sky_isha_overcast
        PrayerSkyPhase.Isha to PrayerSkyWeather.Fog -> Res.drawable.prayer_sky_isha_fog
        PrayerSkyPhase.Isha to PrayerSkyWeather.Rain -> Res.drawable.prayer_sky_isha_rain
        PrayerSkyPhase.Isha to PrayerSkyWeather.Snow -> Res.drawable.prayer_sky_isha_snow
        PrayerSkyPhase.Isha to PrayerSkyWeather.Thunderstorm -> Res.drawable.prayer_sky_isha_thunderstorm
        else -> Res.drawable.prayer_sky_dhuhr_clear
}

/**
 * A short human label for a WMO code, e.g. "Clear sky".
 *
 * Lives beside the artwork mapping because both describe the same forecast: if
 * the label and the sky disagreed, one of them would be lying to the user.
 */
fun weatherConditionLabel(weatherCode: Int?): String = when (weatherCode) {
    null -> ""
    0 -> "Clear sky"
    1 -> "Mainly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67, in 80..82 -> "Rain"
    in 71..77, 85, 86 -> "Snow"
    in 95..99 -> "Thunderstorm"
    else -> "Clear sky"
}
