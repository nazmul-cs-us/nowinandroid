/*
 * Copyright 2024 The Android Open Source Project
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

package com.starception.submission.feature.search

/**
 * Represents a suggested verse for quick access in search.
 * These are popular/important ayahs that users frequently search for.
 */
data class SuggestedVerse(
    val name: String,
    val arabicName: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val description: String,
    val category: String
)

/**
 * Predefined list of suggested verses for user convenience.
 */
object SuggestedVerses {
    val verses = listOf(
        // Most Popular
        SuggestedVerse(
            name = "Ayatul Kursi",
            arabicName = "آية الكرسي",
            surahNumber = 2,
            ayahNumber = 255,
            description = "The Throne Verse - Greatest verse in the Quran",
            category = "Protection"
        ),
        SuggestedVerse(
            name = "Al-Fatiha",
            arabicName = "الفاتحة",
            surahNumber = 1,
            ayahNumber = 1,
            description = "The Opening - Essential prayer surah",
            category = "Prayer"
        ),

        // Last 3 Surahs (Mu'awwidhat)
        SuggestedVerse(
            name = "Al-Ikhlas",
            arabicName = "الإخلاص",
            surahNumber = 112,
            ayahNumber = 1,
            description = "The Sincerity - Equal to 1/3 of Quran",
            category = "Tawheed"
        ),
        SuggestedVerse(
            name = "Al-Falaq",
            arabicName = "الفلق",
            surahNumber = 113,
            ayahNumber = 1,
            description = "The Daybreak - Protection from evil",
            category = "Protection"
        ),
        SuggestedVerse(
            name = "An-Nas",
            arabicName = "الناس",
            surahNumber = 114,
            ayahNumber = 1,
            description = "Mankind - Protection from whispers",
            category = "Protection"
        ),

        // Important Verses
        SuggestedVerse(
            name = "Verse of Light",
            arabicName = "آية النور",
            surahNumber = 24,
            ayahNumber = 35,
            description = "Allah is the Light of the heavens and earth",
            category = "Reflection"
        ),
        SuggestedVerse(
            name = "Last 2 Ayahs of Baqarah",
            arabicName = "خواتيم البقرة",
            surahNumber = 2,
            ayahNumber = 285,
            description = "Protection when recited at night",
            category = "Protection"
        ),
        SuggestedVerse(
            name = "Surah Al-Mulk",
            arabicName = "الملك",
            surahNumber = 67,
            ayahNumber = 1,
            description = "The Sovereignty - Protection in grave",
            category = "Protection"
        ),
        SuggestedVerse(
            name = "Surah Yasin",
            arabicName = "يس",
            surahNumber = 36,
            ayahNumber = 1,
            description = "The Heart of the Quran",
            category = "Blessings"
        ),
        SuggestedVerse(
            name = "Surah Ar-Rahman",
            arabicName = "الرحمن",
            surahNumber = 55,
            ayahNumber = 1,
            description = "The Most Merciful - Beauty of Allah's blessings",
            category = "Gratitude"
        ),

        // Verses for Difficult Times
        SuggestedVerse(
            name = "Verse of Patience",
            arabicName = "آية الصبر",
            surahNumber = 2,
            ayahNumber = 153,
            description = "O believers! Seek comfort in patience and prayer",
            category = "Patience"
        ),
        SuggestedVerse(
            name = "Allah's Promise",
            arabicName = "وعد الله",
            surahNumber = 94,
            ayahNumber = 5,
            description = "Verily, with hardship comes ease",
            category = "Hope"
        ),
        SuggestedVerse(
            name = "Trust in Allah",
            arabicName = "التوكل",
            surahNumber = 65,
            ayahNumber = 3,
            description = "Whoever puts their trust in Allah, He is sufficient",
            category = "Trust"
        ),

        // Forgiveness & Mercy
        SuggestedVerse(
            name = "Verse of Mercy",
            arabicName = "آية الرحمة",
            surahNumber = 39,
            ayahNumber = 53,
            description = "Do not despair of Allah's mercy",
            category = "Mercy"
        ),
        SuggestedVerse(
            name = "Surah Al-Kahf",
            arabicName = "الكهف",
            surahNumber = 18,
            ayahNumber = 1,
            description = "The Cave - Protection from Dajjal (Friday)",
            category = "Friday"
        ),
    )

    /**
     * Filter verses by search query
     */
    fun search(query: String): List<SuggestedVerse> {
        if (query.isBlank()) return verses
        val lowerQuery = query.lowercase()
        return verses.filter { verse ->
            verse.name.lowercase().contains(lowerQuery) ||
            verse.arabicName.contains(query) ||
            verse.description.lowercase().contains(lowerQuery) ||
            verse.category.lowercase().contains(lowerQuery) ||
            "${verse.surahNumber}:${verse.ayahNumber}".contains(query)
        }
    }
}
