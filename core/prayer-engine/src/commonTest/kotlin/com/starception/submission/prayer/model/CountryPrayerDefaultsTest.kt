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

package com.starception.submission.prayer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the country lookup against the JSON the Android app reads, so the
 * generated Kotlin cannot drift from it silently.
 */
class CountryPrayerDefaultsTest {

    @Test
    fun coversEveryCountryInTheSourceData() {
        assertEquals(250, COUNTRY_ENTRIES.size)
    }

    @Test
    fun uaeUsesItsOwnMethodAndOffsets() {
        val uae = assertNotNull(prayerDefaultsFor("AE"))
        assertEquals(CalculationMethod.UAE_IACAD, uae.method)
        assertEquals(18.2, uae.fajrAngle)
        assertEquals(18.2, uae.ishaAngle)
        // The +3 the Android tile shows on Dhuhr, Asr and Maghrib.
        assertEquals(3, uae.timeOffsets["asr"])
        assertEquals(-3, uae.timeOffsets["sunrise"])
    }

    @Test
    fun saudiUsesUmmAlQuraWhichHasNoIshaAngle() {
        val sa = assertNotNull(prayerDefaultsFor("SA"))
        assertEquals(CalculationMethod.UMM_AL_QURA, sa.method)
        // Isha is 90 minutes after Maghrib, not an angle. Reporting an angle of
        // 0.0 here would put Isha at sunset.
        assertNull(sa.ishaAngle)
        assertEquals(90, sa.ishaDelay)
    }

    @Test
    fun hanafiCountriesUseTheDoubleShadow() {
        assertEquals(ASR_SHADOW_HANAFI, assertNotNull(prayerDefaultsFor("PK")).asrShadowFactor)
        assertEquals(ASR_SHADOW_STANDARD, assertNotNull(prayerDefaultsFor("AE")).asrShadowFactor)
    }

    @Test
    fun caseInsensitiveAndUnknownCodesAreDistinguishable() {
        assertNotNull(prayerDefaultsFor("gb"))
        // Not a country; the caller needs to tell this apart from a real entry
        // that happens to use Muslim World League.
        assertNull(prayerDefaultsFor("ZZ"))
    }

    @Test
    fun everyCountryResolvesToAUsableMethod() {
        COUNTRY_ENTRIES.keys.forEach { code ->
            val defaults = assertNotNull(prayerDefaultsFor(code), "no defaults for $code")
            assertTrue(defaults.fajrAngle > 0.0, "$code has a zero Fajr angle")
            // Either an angle or a delay must define Isha, or it cannot be computed.
            assertTrue(
                defaults.ishaAngle != null || defaults.ishaDelay != null,
                "$code defines Isha by neither angle nor delay",
            )
        }
    }
}
