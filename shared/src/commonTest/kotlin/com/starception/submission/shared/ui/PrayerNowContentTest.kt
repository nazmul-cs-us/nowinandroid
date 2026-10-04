/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

package com.starception.submission.shared.ui

import com.starception.submission.core.images.PrayerSkyPhase
import com.starception.submission.core.images.PrayerSkyWeather
import com.starception.submission.prayer.model.PrayerNotificationPreferences
import com.starception.submission.shared.SharedPrayerDay
import com.starception.submission.shared.SharedPrayerSlot
import kotlin.test.Test
import kotlin.test.assertEquals

class PrayerNowContentTest {
    @Test
    fun tracksYesterdayIshaBeforeFajrLikeAndroid() {
        val day = prayerDay(nowMinute = 145, currentPrayer = null, nextPrayer = "Fajr")

        assertEquals("Make Time for Isha", day.heroHeadline(PrayerNotificationPreferences()))
        assertEquals("6h 21m since Isha", day.heroSubtitle("Union Square"))
    }

    @Test
    fun usesAndroidElapsedPhrasing() {
        val day = prayerDay(nowMinute = 13 * 60, currentPrayer = "Dhuhr", nextPrayer = "Asr")

        assertEquals("1 minute since Dhuhr", day.heroSubtitle("Union Square"))
    }

    private fun prayerDay(
        nowMinute: Int,
        currentPrayer: String?,
        nextPrayer: String?,
    ) = SharedPrayerDay(
        slots = listOf(
            SharedPrayerSlot("Fajr", hour = 5, minute = 53),
            SharedPrayerSlot("Dhuhr", hour = 12, minute = 59, isCurrent = currentPrayer == "Dhuhr"),
            SharedPrayerSlot("Asr", hour = 17, minute = 8),
            SharedPrayerSlot("Maghrib", hour = 18, minute = 52),
            SharedPrayerSlot("Isha", hour = 20, minute = 4),
        ),
        currentPrayer = currentPrayer,
        nextPrayer = nextPrayer,
        countdown = "2h 28m",
        skyPhase = PrayerSkyPhase.Isha,
        skyWeather = PrayerSkyWeather.Clear,
        nowMinute = nowMinute,
    )
}
