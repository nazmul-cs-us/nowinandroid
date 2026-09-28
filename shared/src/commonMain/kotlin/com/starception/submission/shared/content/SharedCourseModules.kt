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

package com.starception.submission.shared.content

/** One lesson inside a course module — Android's Lesson model. */
data class SharedLesson(
    val id: String,
    val title: String,
    val subtitle: String,
    val duration: String,
    val surahNumber: Int = 0,
    val hadithNumber: Int = 0,
)

/** A group of lessons — Android's CourseModule. */
data class SharedCourseModule(
    val id: String,
    val title: String,
    val lessons: List<SharedLesson>,
)

/** The 114 surah names, matching Android's courseSurahNames. */
private val courseSurahNames = listOf(
    "Al-Fatihah", "Al-Baqarah", "Aal-E-Imran", "An-Nisa", "Al-Ma'idah",
    "Al-An'am", "Al-A'raf", "Al-Anfal", "At-Tawbah", "Yunus",
    "Hud", "Yusuf", "Ar-Ra'd", "Ibrahim", "Al-Hijr",
    "An-Nahl", "Al-Isra", "Al-Kahf", "Maryam", "Ta-Ha",
    "Al-Anbiya", "Al-Hajj", "Al-Mu'minun", "An-Nur", "Al-Furqan",
    "Ash-Shu'ara", "An-Naml", "Al-Qasas", "Al-Ankabut", "Ar-Rum",
    "Luqman", "As-Sajdah", "Al-Ahzab", "Saba", "Fatir",
    "Ya-Sin", "As-Saffat", "Sad", "Az-Zumar", "Ghafir",
    "Fussilat", "Ash-Shura", "Az-Zukhruf", "Ad-Dukhan", "Al-Jathiyah",
    "Al-Ahqaf", "Muhammad", "Al-Fath", "Al-Hujurat", "Qaf",
    "Adh-Dhariyat", "At-Tur", "An-Najm", "Al-Qamar", "Ar-Rahman",
    "Al-Waqi'ah", "Al-Hadid", "Al-Mujadila", "Al-Hashr", "Al-Mumtahanah",
    "As-Saff", "Al-Jumu'ah", "Al-Munafiqun", "At-Taghabun", "At-Talaq",
    "At-Tahrim", "Al-Mulk", "Al-Qalam", "Al-Haqqah", "Al-Ma'arij",
    "Nuh", "Al-Jinn", "Al-Muzzammil", "Al-Muddaththir", "Al-Qiyamah",
    "Al-Insan", "Al-Mursalat", "An-Naba", "An-Nazi'at", "Abasa",
    "At-Takwir", "Al-Infitar", "Al-Mutaffifin", "Al-Inshiqaq", "Al-Buruj",
    "At-Tariq", "Al-A'la", "Al-Ghashiyah", "Al-Fajr", "Al-Balad",
    "Ash-Shams", "Al-Layl", "Ad-Duha", "Ash-Sharh", "At-Tin",
    "Al-Alaq", "Al-Qadr", "Al-Bayyinah", "Az-Zalzalah", "Al-Adiyat",
    "Al-Qari'ah", "At-Takathur", "Al-Asr", "Al-Humazah", "Al-Fil",
    "Quraysh", "Al-Ma'un", "Al-Kawthar", "Al-Kafirun", "An-Nasr",
    "Al-Masad", "Al-Ikhlas", "Al-Falaq", "An-Nas",
)

/** Generates modules for a course — Android's generateModulesForCourse. */
fun modulesForCourse(courseId: String): List<SharedCourseModule> = when (courseId) {
    "memorize_3_ayahs" -> courseSurahNames.chunked(10).mapIndexed { moduleIndex, surahs ->
        SharedCourseModule(
            id = "module_$moduleIndex",
            title = "Surahs ${moduleIndex * 10 + 1} - ${moduleIndex * 10 + surahs.size}",
            lessons = surahs.mapIndexed { index, surahName ->
                val surahNumber = moduleIndex * 10 + index + 1
                SharedLesson(
                    id = "surah_$surahNumber",
                    title = "Surah $surahName",
                    subtitle = "3 ayahs",
                    duration = "5 min",
                    surahNumber = surahNumber,
                )
            },
        )
    }
    "daily_bukhari" -> {
        val hadithsPerSession = 30
        (0 until 13).map { sessionIndex ->
            val start = sessionIndex * hadithsPerSession + 1
            val end = minOf((sessionIndex + 1) * hadithsPerSession, 365)
            SharedCourseModule(
                id = "session_$sessionIndex",
                title = "Session ${sessionIndex + 1}",
                lessons = (start..end).map { hadithNumber ->
                    SharedLesson(
                        id = "hadith_$hadithNumber",
                        title = "Hadith #$hadithNumber",
                        subtitle = "Sahih Al-Bukhari",
                        duration = "3 min",
                        hadithNumber = hadithNumber,
                    )
                },
            )
        }
    }
    "juz_amma" -> {
        val juzSurahs = (78..114).toList()
        juzSurahs.chunked(10).mapIndexed { moduleIndex, surahs ->
            SharedCourseModule(
                id = "module_$moduleIndex",
                title = "Surahs ${surahs.first()} - ${surahs.last()}",
                lessons = surahs.mapIndexed { index, surahNumber ->
                    SharedLesson(
                        id = "surah_$surahNumber",
                        title = "Surah ${courseSurahNames[surahNumber - 1]}",
                        subtitle = "3 ayahs",
                        duration = "5 min",
                        surahNumber = surahNumber,
                    )
                },
            )
        }
    }
    "quran_reading" -> {
        // 30 Juz modules, pages 1-604
        val juzPages = listOf(
            1, 22, 42, 62, 82, 102, 121, 142, 162, 182,
            201, 222, 242, 262, 282, 302, 322, 342, 362, 382,
            402, 422, 442, 462, 482, 502, 522, 542, 562, 582,
        )
        juzPages.mapIndexed { juzIndex, startPage ->
            val endPage = juzPages.getOrNull(juzIndex + 1)?.minus(1) ?: 604
            SharedCourseModule(
                id = "juz_$juzIndex",
                title = "Juz ${juzIndex + 1}",
                lessons = (startPage..endPage).map { pageNumber ->
                    SharedLesson(
                        id = "page_$pageNumber",
                        title = "Page $pageNumber",
                        subtitle = "Read and reflect",
                        duration = "5 min",
                    )
                },
            )
        }
    }
    else -> emptyList()
}
