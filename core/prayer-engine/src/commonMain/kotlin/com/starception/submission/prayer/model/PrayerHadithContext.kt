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
    val prayerKey: String,
) {
    fun heading(displayPrayerName: String): String = when {
        isJumuahPrayer -> "HADITH FOR JUMU'AH · FRIDAY"
        isFriday -> "FRIDAY · HADITH FOR ${displayPrayerName.uppercase()}"
        else -> "HADITH FOR ${displayPrayerName.uppercase()}"
    }

    /** A passing mention of the next prayer must not make an unrelated narration eligible. */
    fun relevanceScore(text: String): Int {
        val normalized = text.lowercase().filterNot {
            it.code in 0x064B..0x065F || it.code == 0x0670 || it.code == 0x0640
        }
        if (isFriday && listOf("friday", "jumu", "jumma", "جمعة").none { it in normalized }) return 0
        val firstFocus = queries.map { normalized.indexOf(it.lowercase()) }
            .filter { it >= 0 }.minOrNull() ?: return 0
        val competingTopics = when (prayerKey) {
            "fajr" -> listOf("night prayer", "middle of the night", "tahajjud", "taraweeh", "tarawih",
                "dhuhr prayer", "asr prayer", "maghrib prayer", "isha prayer")
            "dhuhr" -> listOf("fajr prayer", "asr prayer", "maghrib prayer", "isha prayer")
            "asr" -> listOf("fajr prayer", "dhuhr prayer", "maghrib prayer", "isha prayer")
            "maghrib" -> listOf("fajr prayer", "dhuhr prayer", "asr prayer", "isha prayer")
            "isha" -> listOf("fajr prayer", "dhuhr prayer", "asr prayer", "maghrib prayer")
            else -> emptyList()
        }
        val competingBeforeFocus = competingTopics.any { term ->
            normalized.indexOf(term).let { it >= 0 && it < firstFocus }
        }
        val focusScore = if (firstFocus < 320) 5 else 2
        return (focusScore - if (competingBeforeFocus) 4 else 0).coerceAtLeast(0)
    }

    fun accepts(text: String): Boolean = relevanceScore(text) > 0
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
    val prayerKey = when (prayer) {
        "zuhr", "zuhur", "jumuah", "jumua", "jumah", "jummah" -> "dhuhr"
        else -> prayer
    }
    return PrayerHadithContext(queries, friday, friday && midday, prayerKey)
}
