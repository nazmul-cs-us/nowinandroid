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

package com.starception.submission.feature.prayertimes.data

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Country-level fallback for when the location cannot be resolved (no location
 * permission, no internet, no cached location).
 *
 * Instead of assuming Dubai, this derives the device's country from the cell
 * network / SIM (no permissions needed) or the system locale, then reads that
 * country's representative coordinates from country_prayer_methods.json — so a
 * user in Bangladesh sees Bangladesh's prayer times and country name, not
 * Dubai's.
 */
object DeviceCountryFallback {

    data class CountryDefault(
        val countryCode: String,
        val countryName: String,
        val latitude: Double,
        val longitude: Double,
    )

    /** Two-letter ISO code of the device's country, or null when undetectable. */
    fun detectCountryCode(context: Context): String? {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        // Network country reflects where the user physically is; SIM is where
        // they bought the SIM. Prefer network, then SIM, then system locale.
        val networkIso = telephony?.networkCountryIso?.trim()?.uppercase()
        if (!networkIso.isNullOrEmpty() && networkIso.length == 2) return networkIso
        val simIso = telephony?.simCountryIso?.trim()?.uppercase()
        if (!simIso.isNullOrEmpty() && simIso.length == 2) return simIso
        val locale = Locale.getDefault().country.uppercase()
        if (locale.length == 2) return locale
        return null
    }

    /**
     * The detected country's representative coordinates from
     * country_prayer_methods.json, or null when the country is unknown or the
     * JSON can't be read.
     */
    fun countryDefault(context: Context): CountryDefault? {
        val countryCode = detectCountryCode(context) ?: return null
        return try {
            val json = context.assets.open("country_prayer_methods.json")
                .bufferedReader().use { it.readText() }
            val root = kotlinx.serialization.json.Json.parseToJsonElement(json)
                as? kotlinx.serialization.json.JsonObject ?: return null
            val countries = root["countries"] as? kotlinx.serialization.json.JsonObject
                ?: return null
            val entry = countries[countryCode] as? kotlinx.serialization.json.JsonObject
                ?: return null
            val name = (entry["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: return null
            val coords = entry["coordinates"] as? kotlinx.serialization.json.JsonObject
                ?: return null
            val latitude = (coords["latitude"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.content?.toDoubleOrNull() ?: return null
            val longitude = (coords["longitude"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.content?.toDoubleOrNull() ?: return null
            CountryDefault(
                countryCode = countryCode,
                countryName = name,
                latitude = latitude,
                longitude = longitude,
            )
        } catch (e: Exception) {
            null
        }
    }

    /** TimeZone offset of the device in hours (e.g. +6.0 for Dhaka). */
    fun deviceTimeZoneOffsetHours(): Double =
        java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000.0

    /**
     * Human-readable label for the fallback, e.g. "Bangladesh (Default)" —
     * "Dubai, UAE (Default)" only when even the country is undetectable.
     */
    fun countryFallbackLabel(context: Context): String =
        countryDefault(context)?.let { "${it.countryName} (Default)" }
            ?: "Dubai, UAE (Default)"

    /**
     * Prayer times for the detected country's representative coordinates,
     * calculated offline with the country's own auto-detected method from
     * country_prayer_methods.json. Falls back to Dubai's static times when the
     * country is undetectable.
     */
    fun countryFallbackPrayerTimes(context: Context): com.starception.submission.prayer.model.DayPrayerTimes {
        val country = countryDefault(context)
        if (country != null) {
            val location = com.starception.submission.prayer.model.Location(
                latitude = country.latitude,
                longitude = country.longitude,
                timeZoneOffset = deviceTimeZoneOffsetHours(),
                city = country.countryName,
                country = country.countryName,
            )
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                PrayerTimeCalculatorEntryPoint::class.java,
            )
            val settings = entryPoint.prayerSettingsRepository()
                .getAutoDetectedSettingsForCountry(country.countryCode)
            if (settings != null) {
                try {
                    val service = entryPoint.prayerTimeCalculatorService()
                    val today = java.time.LocalDate.now()
                    val calculated = service.calculatePrayerTimes(
                        java.time.LocalDate.of(today.year, today.monthValue, today.dayOfMonth),
                        location,
                        settings,
                    )
                    if (calculated != null) return calculated
                } catch (e: Exception) {
                    // Calculation failure falls through to the static default.
                }
            }
        }

        // Undetectable country (or calculation failed): the original Dubai
        // static times remain the last-resort default.
        return com.starception.submission.prayer.model.DayPrayerTimes(
            date = java.time.LocalDateTime.now(),
            fajr = java.time.LocalTime.of(5, 15),
            sunrise = java.time.LocalTime.of(6, 45),
            dhuhr = java.time.LocalTime.of(12, 15),
            asr = java.time.LocalTime.of(15, 45),
            maghrib = java.time.LocalTime.of(18, 30),
            isha = java.time.LocalTime.of(19, 45),
            location = com.starception.submission.prayer.model.Location(
                latitude = 25.2048,
                longitude = 55.2708,
                timeZoneOffset = 4.0,
                city = "Dubai",
                country = "UAE",
            ),
        )
    }
}
