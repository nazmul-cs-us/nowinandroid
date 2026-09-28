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

/**
 * A course the user can enroll in — the shared counterpart of
 * Android's Course data class. The content (surahs, hadiths) opens
 * through the existing detail screens.
 */
data class SharedCourse(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val totalLessons: Int,
    val estimatedDays: Int,
    val difficulty: String,
    val category: String,
)

/** Android's getAvailableCourses() — the four courses. */
val SharedCourses = listOf(
    SharedCourse(
        id = "memorize_3_ayahs",
        title = "Memorize First 3 Ayahs",
        subtitle = "All 114 Surahs",
        description = "Start your memorization journey by learning the opening verses of every Surah.",
        totalLessons = 114,
        estimatedDays = 114,
        difficulty = "Beginner",
        category = "Memorization",
    ),
    SharedCourse(
        id = "daily_bukhari",
        title = "Daily Hadith",
        subtitle = "Sahih Al-Bukhari",
        description = "One hadith a day builds a lifelong habit of prophetic wisdom.",
        totalLessons = 365,
        estimatedDays = 365,
        difficulty = "Beginner",
        category = "Hadith",
    ),
    SharedCourse(
        id = "juz_amma",
        title = "Juz Amma Memorization",
        subtitle = "Last 37 Surahs",
        description = "Master the surahs most recited in daily prayers.",
        totalLessons = 37,
        estimatedDays = 60,
        difficulty = "Intermediate",
        category = "Memorization",
    ),
    SharedCourse(
        id = "quran_reading",
        title = "Complete Quran Reading",
        subtitle = "All 604 Pages",
        description = "Read through the entire Quran with understanding.",
        totalLessons = 604,
        estimatedDays = 365,
        difficulty = "Advanced",
        category = "Quran",
    ),
)
