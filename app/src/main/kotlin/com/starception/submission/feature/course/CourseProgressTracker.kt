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
import org.json.JSONObject

/**
 * Course completion info returned when checking if content is completed in a course
 */
data class CourseCompletionInfo(
    val courseId: String,
    val courseName: String,
    val lessonTitle: String,
)

/**
 * Pending lesson info for lessons that have been viewed but not yet confirmed as completed
 */
data class PendingLessonInfo(
    val lessonId: String,
    val lessonTitle: String,
    val timestamp: Long,
)

/**
 * Tracks course progress and completed lessons.
 * Stores which specific surahs/hadiths have been completed in each course.
 */
object CourseProgressTracker {

    private const val PREFS_NAME = "course_progress"
    private const val COMPLETED_LESSONS_PREFIX = "completed_lessons_"
    private const val PENDING_COMPLETION_PREFIX = "pending_completion_"

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

    // ==================== Pending Completion Methods ====================

    /**
     * Set a lesson as pending completion (user has viewed but not confirmed)
     * @param context Android context
     * @param courseId The course ID
     * @param lessonId The lesson ID
     * @param lessonTitle Human-readable lesson title
     */
    fun setPendingCompletion(
        context: Context,
        courseId: String,
        lessonId: String,
        lessonTitle: String
    ) {
        val prefs = getPrefs(context)
        val key = "$PENDING_COMPLETION_PREFIX$courseId"
        val json = JSONObject().apply {
            put("lessonId", lessonId)
            put("lessonTitle", lessonTitle)
            put("timestamp", System.currentTimeMillis())
        }
        prefs.edit().putString(key, json.toString()).apply()
    }

    // Pending completions expire after 1 hour (in milliseconds)
    private const val PENDING_EXPIRATION_MS = 60 * 60 * 1000L

    /**
     * Get the pending lesson info for a course
     * @return PendingLessonInfo if there's a pending lesson, null otherwise
     *
     * Note: Automatically clears and returns null for pending completions older than 1 hour
     */
    fun getPendingCompletion(context: Context, courseId: String): PendingLessonInfo? {
        val prefs = getPrefs(context)
        val key = "$PENDING_COMPLETION_PREFIX$courseId"
        val jsonString = prefs.getString(key, null) ?: return null

        return try {
            val json = JSONObject(jsonString)
            val timestamp = json.getLong("timestamp")
            val currentTime = System.currentTimeMillis()

            // Check if pending completion has expired (older than 1 hour)
            if (currentTime - timestamp > PENDING_EXPIRATION_MS) {
                // Auto-clear stale pending completion
                android.util.Log.d("CourseProgressTracker", "🗑️ Clearing expired pending completion for $courseId (age: ${(currentTime - timestamp) / 1000 / 60} minutes)")
                clearPendingCompletion(context, courseId)
                return null
            }

            PendingLessonInfo(
                lessonId = json.getString("lessonId"),
                lessonTitle = json.getString("lessonTitle"),
                timestamp = timestamp,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clear the pending completion for a course
     */
    fun clearPendingCompletion(context: Context, courseId: String) {
        val prefs = getPrefs(context)
        val key = "$PENDING_COMPLETION_PREFIX$courseId"
        prefs.edit().remove(key).apply()
    }

    /**
     * Check if there's a pending completion for a course
     */
    fun hasPendingCompletion(context: Context, courseId: String): Boolean {
        return getPendingCompletion(context, courseId) != null
    }

    /**
     * Confirm a pending completion - mark the lesson as completed and clear pending
     */
    fun confirmPendingCompletion(context: Context, courseId: String): Boolean {
        val pending = getPendingCompletion(context, courseId) ?: return false
        markLessonCompleted(context, courseId, pending.lessonId)
        clearPendingCompletion(context, courseId)
        return true
    }

    // ==================== End Pending Completion Methods ====================

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

        // Check Complete Quran Listening course
        if (COURSE_ID_QURAN_LISTENING in enrolledCourses && surahNumber in 1..114) {
            val lessonId = "surah_$surahNumber"
            if (isLessonCompleted(context, COURSE_ID_QURAN_LISTENING, lessonId)) {
                return CourseCompletionInfo(
                    courseId = COURSE_ID_QURAN_LISTENING,
                    courseName = "Complete Quran Listening",
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

    // ==================== Complete Quran Listening Course Methods ====================

    private const val KEY_QURAN_LISTENING_PROGRESS = "quran_listening_progress_json"
    private const val COURSE_ID_QURAN_LISTENING = "complete_quran_listening"

    /**
     * Get current Quran listening progress
     * @return QuranListeningProgress with current state
     */
    fun getQuranListeningProgress(context: Context): QuranListeningProgress {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_QURAN_LISTENING_PROGRESS, null)
        return json?.let { QuranListeningProgress.fromJson(it) } ?: QuranListeningProgress()
    }

    /**
     * Save Quran listening progress
     */
    fun saveQuranListeningProgress(context: Context, progress: QuranListeningProgress) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_QURAN_LISTENING_PROGRESS, progress.toJson())
            .apply()
        android.util.Log.d("CourseProgressTracker", "🕌 Saved Quran progress: Surah ${progress.currentSurahNumber}, position ${progress.currentPositionMs / 1000}s")
    }

    /**
     * Update position within current surah (called periodically during playback)
     * Also updates session listening time
     */
    fun updateQuranPosition(context: Context, positionMs: Int) {
        val progress = getQuranListeningProgress(context)
        val currentTime = System.currentTimeMillis()

        // Calculate time since last update for session tracking
        val timeSinceLastUpdate = if (progress.lastPlayedTimestamp > 0) {
            currentTime - progress.lastPlayedTimestamp
        } else {
            0L
        }

        // Only add to session time if the gap is reasonable (< 10 seconds)
        // This prevents large jumps when resuming after a pause
        val additionalSessionTime = if (timeSinceLastUpdate in 1..10000) {
            timeSinceLastUpdate
        } else {
            0L
        }

        saveQuranListeningProgress(context, progress.copy(
            currentPositionMs = positionMs,
            lastPlayedTimestamp = currentTime,
            sessionListeningTimeMs = progress.sessionListeningTimeMs + additionalSessionTime,
        ))
    }

    /**
     * Mark current surah as complete and advance to next
     * @return The new surah index (0-113) after advancing
     */
    fun completeCurrentSurah(context: Context): Int {
        val progress = getQuranListeningProgress(context)
        val completedSurahIndex = progress.currentSurahIndex

        // Mark lesson as completed in existing system
        val lessonId = "surah_${completedSurahIndex + 1}"
        markLessonCompleted(context, COURSE_ID_QURAN_LISTENING, lessonId)

        // Calculate next surah (wrap around to 0 after 113)
        val nextIndex = if (completedSurahIndex >= 113) 0 else completedSurahIndex + 1

        // Update progress for next surah
        saveQuranListeningProgress(context, progress.copy(
            currentSurahIndex = nextIndex,
            currentPositionMs = 0,
            sessionSurahsCompleted = progress.sessionSurahsCompleted + 1,
            lastPlayedTimestamp = System.currentTimeMillis(),
        ))

        android.util.Log.i("CourseProgressTracker", "🕌 Completed Surah ${completedSurahIndex + 1} (${getSurahName(completedSurahIndex + 1)}), advancing to Surah ${nextIndex + 1}")

        return nextIndex
    }

    /**
     * Start a new listening session
     * Resets session counters while preserving overall progress
     */
    fun startQuranListeningSession(context: Context) {
        val progress = getQuranListeningProgress(context)
        val currentTime = System.currentTimeMillis()

        saveQuranListeningProgress(context, progress.copy(
            sessionStartTime = currentTime,
            sessionListeningTimeMs = 0,
            sessionSurahsCompleted = 0,
            lastPlayedTimestamp = currentTime,
        ))

        android.util.Log.i("CourseProgressTracker", "🕌 Started new Quran listening session at Surah ${progress.currentSurahNumber}")
    }

    /**
     * End listening session and accumulate stats
     */
    fun endQuranListeningSession(context: Context) {
        val progress = getQuranListeningProgress(context)

        // Accumulate session time into total
        val updatedProgress = progress.copy(
            totalListeningTimeMs = progress.totalListeningTimeMs + progress.sessionListeningTimeMs,
            sessionStartTime = 0,
            // Keep session stats for display until next session starts
        )

        saveQuranListeningProgress(context, updatedProgress)

        val sessionMinutes = progress.sessionListeningTimeMs / 60000
        android.util.Log.i("CourseProgressTracker", "🕌 Ended Quran session: ${sessionMinutes}min listened, ${progress.sessionSurahsCompleted} surahs completed")
    }

    /**
     * Get the next uncompleted surah index for the Quran listening course
     * Useful for finding where to resume if user wants to skip completed surahs
     */
    fun getNextUncompletedSurahIndex(context: Context): Int {
        val completedLessons = getCompletedLessons(context, COURSE_ID_QURAN_LISTENING)
        for (i in 0..113) {
            val lessonId = "surah_${i + 1}"
            if (lessonId !in completedLessons) {
                return i
            }
        }
        // All completed, start from beginning
        return 0
    }

    /**
     * Check if user is enrolled in Complete Quran Listening course
     */
    fun isEnrolledInQuranListening(context: Context): Boolean {
        val prefs = getPrefs(context)
        val enrolledCourses = prefs.getStringSet("enrolled_courses", emptySet()) ?: emptySet()
        return COURSE_ID_QURAN_LISTENING in enrolledCourses
    }

    // ==================== End Complete Quran Listening Methods ====================

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
