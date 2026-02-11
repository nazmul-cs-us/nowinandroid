/*
 * Copyright 2024 Starception
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

package com.starception.submission.feature.course

import android.content.Context
import android.content.SharedPreferences

/**
 * Course completion info returned when checking if content is completed in a course
 */
data class CourseCompletionInfo(
    val courseId: String,
    val courseName: String,
    val lessonTitle: String,
)

/**
 * Tracks course progress and completed lessons.
 * Stores which specific surahs/hadiths have been completed in each course.
 */
object CourseProgressTracker {

    private const val PREFS_NAME = "course_progress"
    private const val COMPLETED_LESSONS_PREFIX = "completed_lessons_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Mark a lesson as completed in a course
     * @param context Android context
     * @param courseId The course ID (e.g., "juz_amma", "daily_bukhari")
     * @param lessonId The lesson ID (e.g., "surah_78", "hadith_1")
     */
    fun markLessonCompleted(context: Context, courseId: String, lessonId: String) {
        val prefs = getPrefs(context)
        val key = "$COMPLETED_LESSONS_PREFIX$courseId"
        val currentSet = prefs.getStringSet(key, emptySet()) ?: emptySet()
        val newSet = currentSet.toMutableSet().apply { add(lessonId) }
        prefs.edit().putStringSet(key, newSet).apply()
    }

    /**
     * Check if a lesson is completed in a course
     */
    fun isLessonCompleted(context: Context, courseId: String, lessonId: String): Boolean {
        val prefs = getPrefs(context)
        val key = "$COMPLETED_LESSONS_PREFIX$courseId"
        val completedSet = prefs.getStringSet(key, emptySet()) ?: emptySet()
        return lessonId in completedSet
    }

    /**
     * Get all completed lessons for a course
     */
    fun getCompletedLessons(context: Context, courseId: String): Set<String> {
        val prefs = getPrefs(context)
        val key = "$COMPLETED_LESSONS_PREFIX$courseId"
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    /**
     * Check if a surah has been completed in any enrolled course
     * @param context Android context
     * @param surahNumber The surah number (1-114)
     * @return CourseCompletionInfo if completed in a course, null otherwise
     */
    fun getSurahCourseCompletion(context: Context, surahNumber: Int): CourseCompletionInfo? {
        val prefs = getPrefs(context)
        val enrolledCourses = prefs.getStringSet("enrolled_courses", emptySet()) ?: emptySet()

        // Check Juz Amma course (surahs 78-114)
        if ("juz_amma" in enrolledCourses && surahNumber in 78..114) {
            val lessonId = "surah_$surahNumber"
            if (isLessonCompleted(context, "juz_amma", lessonId)) {
                return CourseCompletionInfo(
                    courseId = "juz_amma",
                    courseName = "Juz Amma Memorization",
                    lessonTitle = "Surah ${getSurahName(surahNumber)}",
                )
            }
        }

        // Check Memorize First 3 Ayahs course (all surahs 1-114)
        if ("memorize_3_ayahs" in enrolledCourses && surahNumber in 1..114) {
            val lessonId = "surah_$surahNumber"
            if (isLessonCompleted(context, "memorize_3_ayahs", lessonId)) {
                return CourseCompletionInfo(
                    courseId = "memorize_3_ayahs",
                    courseName = "Memorize First 3 Ayahs",
                    lessonTitle = "Surah ${getSurahName(surahNumber)}",
                )
            }
        }

        // Check Complete Quran Reading course
        if ("quran_reading" in enrolledCourses && surahNumber in 1..114) {
            val lessonId = "surah_$surahNumber"
            if (isLessonCompleted(context, "quran_reading", lessonId)) {
                return CourseCompletionInfo(
                    courseId = "quran_reading",
                    courseName = "Complete Quran Reading",
                    lessonTitle = "Surah ${getSurahName(surahNumber)}",
                )
            }
        }

        return null
    }

    /**
     * Check if a hadith has been completed in any enrolled course
     * @param context Android context
     * @param hadithNumber The hadith number
     * @param databaseFile The database file (e.g., "sahih_bukhari.db")
     * @return CourseCompletionInfo if completed in a course, null otherwise
     */
    fun getHadithCourseCompletion(context: Context, hadithNumber: Int, databaseFile: String): CourseCompletionInfo? {
        val prefs = getPrefs(context)
        val enrolledCourses = prefs.getStringSet("enrolled_courses", emptySet()) ?: emptySet()

        // Check Daily Bukhari course
        if ("daily_bukhari" in enrolledCourses && databaseFile == "sahih_bukhari.db") {
            val lessonId = "hadith_$hadithNumber"
            if (isLessonCompleted(context, "daily_bukhari", lessonId)) {
                // Determine which day this hadith belongs to
                val dayNumber = hadithNumber
                val monthNames = listOf("January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December")
                val daysInMonth = listOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

                var remaining = dayNumber
                var monthIndex = 0
                var dayOfMonth = 1

                for (i in monthNames.indices) {
                    if (remaining <= daysInMonth[i]) {
                        monthIndex = i
                        dayOfMonth = remaining
                        break
                    }
                    remaining -= daysInMonth[i]
                }

                return CourseCompletionInfo(
                    courseId = "daily_bukhari",
                    courseName = "Daily Hadith",
                    lessonTitle = "${monthNames[monthIndex]} Day $dayOfMonth",
                )
            }
        }

        return null
    }

    /**
     * Clear all completed lessons for a course (when unenrolling)
     */
    fun clearCourseProgress(context: Context, courseId: String) {
        val prefs = getPrefs(context)
        val key = "$COMPLETED_LESSONS_PREFIX$courseId"
        prefs.edit().remove(key).apply()
    }

    /**
     * Get surah name by number
     */
    private fun getSurahName(surahNumber: Int): String {
        val surahNames = listOf(
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
            "Al-Masad", "Al-Ikhlas", "Al-Falaq", "An-Nas"
        )
        return surahNames.getOrElse(surahNumber - 1) { "Surah $surahNumber" }
    }
}
