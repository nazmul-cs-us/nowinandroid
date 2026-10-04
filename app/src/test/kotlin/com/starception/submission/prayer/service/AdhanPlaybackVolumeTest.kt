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

package com.starception.submission.prayer.service

import com.starception.submission.prayer.model.PrayerNotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class AdhanPlaybackVolumeTest {

    @Test
    fun allPrayersDefaultToFivePercentVolume() {
        val preferences = PrayerNotificationPreferences()

        listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").forEach { prayer ->
            assertEquals(5, preferences.getAdhanVolumeForPrayer(prayer))
        }
    }

    @Test
    fun playerGainUsesQuietPerceptualCurveAndClampsInput() {
        assertEquals(0f, adhanPlayerGain(-20), 0.000001f)
        assertEquals(0f, adhanPlayerGain(0), 0.000001f)
        assertEquals(0.0025f, adhanPlayerGain(5), 0.000001f)
        assertEquals(0.25f, adhanPlayerGain(50), 0.000001f)
        assertEquals(1f, adhanPlayerGain(100), 0.000001f)
        assertEquals(1f, adhanPlayerGain(140), 0.000001f)
    }

    @Test
    fun eachPrayerUsesItsOwnOverrideAndFallsBackToMaster() {
        val preferences = PrayerNotificationPreferences(
            adhanVolume = 61,
            fajrAdhanVolume = 5,
            dhuhrAdhanVolume = 15,
            asrAdhanVolume = 25,
            maghribAdhanVolume = 35,
            ishaAdhanVolume = 45,
        )

        assertEquals(5, preferences.getAdhanVolumeForPrayer("Fajr"))
        assertEquals(15, preferences.getAdhanVolumeForPrayer("Dhuhr"))
        assertEquals(25, preferences.getAdhanVolumeForPrayer("Asr"))
        assertEquals(35, preferences.getAdhanVolumeForPrayer("Maghrib"))
        assertEquals(45, preferences.getAdhanVolumeForPrayer("Isha"))
        assertEquals(61, preferences.getAdhanVolumeForPrayer("unknown"))
    }
}
