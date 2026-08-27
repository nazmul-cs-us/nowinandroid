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

package com.starception.submission.prayer.model

/** Standard (Shafi'i, Maliki, Hanbali) casts a shadow of 1×; Hanafi uses 2×. */
const val ASR_SHADOW_STANDARD = 1
const val ASR_SHADOW_HANAFI = 2

/**
 * How prayer times should be calculated in a given country.
 *
 * This is what makes the times right somewhere other than where the developer
 * happened to be: the angles for Fajr and Isha vary by convention, and using one
 * country's method everywhere produces times that look plausible and are wrong.
 */
data class CountryPrayerDefaults(
    val countryName: String,
    val method: CalculationMethod,
    val asrShadowFactor: Int,
    /** Per-prayer minute adjustments the country's authority publishes. */
    val timeOffsets: Map<String, Int> = emptyMap(),
) {
    val fajrAngle: Double get() = method.fajrAngle

    /**
     * Isha's angle, or `null` where the method defines Isha as a fixed delay
     * after Maghrib instead — Umm al-Qura's 90 minutes being the usual case.
     */
    val ishaAngle: Double? get() = method.ishaAngle?.takeIf { it > 0.0 }

    /** Minutes after Maghrib, for the methods that work that way. */
    val ishaDelay: Int? get() = method.ishaDelay?.takeIf { it > 0 }
}

/**
 * The defaults for an ISO 3166-1 alpha-2 country code, or `null` if unknown.
 *
 * Returning `null` rather than silently substituting a default keeps "we don't
 * recognise this country" distinguishable from "this country uses Muslim World
 * League", which several genuinely do.
 */
fun prayerDefaultsFor(countryCode: String): CountryPrayerDefaults? {
    val entry = COUNTRY_ENTRIES[countryCode.uppercase()] ?: return null
    return CountryPrayerDefaults(
        countryName = entry.countryName,
        method = calculationMethodNamed(entry.methodName),
        asrShadowFactor = asrShadowFor(entry.madhhabName),
        timeOffsets = entry.timeOffsets,
    )
}

/**
 * Maps the JSON's method name onto a [CalculationMethod].
 *
 * Ported from PrayerSettingsRepository so both platforms resolve a country the
 * same way. Note how many names fall through: the data names 25 methods and only
 * nine have a distinct implementation, so `Algeria`, `Turkey`, `Russia`,
 * `Morocco` and the rest resolve to Muslim World League. That is the existing
 * Android behaviour, reproduced deliberately rather than improved here — changing
 * which method a country uses would change prayer times for its users, and that
 * is a decision about religious practice, not a refactor.
 */
internal fun calculationMethodNamed(name: String): CalculationMethod = when (name) {
    "Muslim_World_League" -> CalculationMethod.MUSLIM_WORLD_LEAGUE
    "Umm_al_Qura_University_Makkah" -> CalculationMethod.UMM_AL_QURA
    "UAE_IACAD" -> CalculationMethod.UAE_IACAD
    "Egyptian_General_Authority_of_Survey" -> CalculationMethod.EGYPTIAN_AUTHORITY
    "University_of_Islamic_Sciences_Karachi",
    "University_of_Karachi",
    -> CalculationMethod.UNIVERSITY_OF_ISLAMIC_SCIENCES

    "Islamic_Society_of_North_America" -> CalculationMethod.ISNA
    "Institute_of_Geophysics_University_of_Tehran" ->
        CalculationMethod.INSTITUTE_OF_GEOPHYSICS_TEHRAN

    "Shia_Ithna_Ashari_Leva_Research_Institute_Qum" -> CalculationMethod.SHIA_ITHNA_ASHARI
    "Majlis_Ugama_Islam_Singapura_Singapore" -> CalculationMethod.MUIS
    "Europe", "South_Africa" -> CalculationMethod.MUSLIM_WORLD_LEAGUE
    else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
}

/**
 * Maps a madhhab name to its Asr shadow factor.
 *
 * Only Hanafi differs; the other four schools all take the shadow at 1×.
 */
internal fun asrShadowFor(madhhab: String): Int =
    if (madhhab.lowercase() == "hanafi") ASR_SHADOW_HANAFI else ASR_SHADOW_STANDARD
