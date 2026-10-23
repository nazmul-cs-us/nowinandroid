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

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** Selection context shared by the Android and iOS Salah readers. */
data class PrayerHadithContext(
    val queries: List<String>,
    val isFriday: Boolean,
    val isJumuahPrayer: Boolean,
) {
    fun heading(displayPrayerName: String): String = when {
        isJumuahPrayer -> "HADITH FOR JUMU'AH · FRIDAY"
        isFriday -> "FRIDAY · HADITH FOR ${displayPrayerName.uppercase()}"
        else -> "HADITH FOR ${displayPrayerName.uppercase()}"
    }

    // Search may match commentary; require Friday relevance in the displayed narration too.
    fun accepts(text: String): Boolean = !isFriday || text.lowercase().filterNot { it.code in 0x064B..0x065F || it.code == 0x0670 || it.code == 0x0640 }.let {
        "friday" in it || "jumu" in it || "jumma" in it || "جمعة" in it
    }
}

fun prayerHadithContext(prayerName: String?, date: LocalDate): PrayerHadithContext {
    val prayer = prayerName.orEmpty().trim().lowercase().replace("'", "").replace("’", "")
    val explicitlyJumuah = prayer in setOf("jumuah", "jumua", "jumah", "jummah")
    val friday = date.dayOfWeek == DayOfWeek.FRIDAY || explicitlyJumuah
    val midday = prayer in setOf("dhuhr", "zuhr", "zuhur") || explicitlyJumuah
    val queries = if (friday) {
        // Never mix ordinary prayer results into this pool: sorting by length
        // would otherwise let a short generic narration displace Friday's topic.
        if (midday) listOf("Friday prayer", "Jumu", "Friday sermon", "Friday", "الجمعة")
        else listOf("Friday", "Jumu", "Friday prayer", "الجمعة")
    } else when (prayer) {
        "fajr" -> listOf("Fajr prayer", "morning prayer", "صلاة الفجر", "صلاة الصبح")
        "dhuhr", "zuhr", "zuhur" -> listOf("Dhuhr prayer", "midday prayer", "صلاة الظهر")
        "asr" -> listOf("Asr prayer", "afternoon prayer", "صلاة العصر")
        "maghrib" -> listOf("Maghrib prayer", "sunset prayer", "صلاة المغرب")
        "isha" -> listOf("Isha prayer", "night prayer", "صلاة العشاء")
        else -> listOf("prayer", "congregational prayer", "الصلاة")
    }
    return PrayerHadithContext(queries, friday, friday && midday)
}
