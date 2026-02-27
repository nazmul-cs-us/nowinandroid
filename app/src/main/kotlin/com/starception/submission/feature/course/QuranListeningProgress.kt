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

import org.json.JSONObject

/**
 * Persistent progress data for Complete Quran Listening course.
 * Tracks the user's position in the Quran and session statistics.
 *
 * @property currentSurahIndex The current surah index (0-113)
 * @property currentPositionMs Position within current surah in milliseconds
 * @property sessionStartTime Timestamp when current listening session started (0 if no active session)
 * @property sessionListeningTimeMs Accumulated listening time in current session
 * @property sessionSurahsCompleted Number of surahs completed in current session
 * @property totalListeningTimeMs All-time total listening time across all sessions
 * @property lastPlayedTimestamp Timestamp when audio was last played
 */
data class QuranListeningProgress(
    val currentSurahIndex: Int = 0,
    val currentPositionMs: Int = 0,
    val sessionStartTime: Long = 0,
    val sessionListeningTimeMs: Long = 0,
    val sessionSurahsCompleted: Int = 0,
    val totalListeningTimeMs: Long = 0,
    val lastPlayedTimestamp: Long = 0,
) {
    companion object {
        /**
         * Parse QuranListeningProgress from JSON string
         * @param json JSON string representation
         * @return QuranListeningProgress object or null if parsing fails
         */
        fun fromJson(json: String): QuranListeningProgress? {
            return try {
                val obj = JSONObject(json)
                QuranListeningProgress(
                    currentSurahIndex = obj.optInt("currentSurahIndex", 0),
                    currentPositionMs = obj.optInt("currentPositionMs", 0),
                    sessionStartTime = obj.optLong("sessionStartTime", 0),
                    sessionListeningTimeMs = obj.optLong("sessionListeningTimeMs", 0),
                    sessionSurahsCompleted = obj.optInt("sessionSurahsCompleted", 0),
                    totalListeningTimeMs = obj.optLong("totalListeningTimeMs", 0),
                    lastPlayedTimestamp = obj.optLong("lastPlayedTimestamp", 0),
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Convert to JSON string for SharedPreferences storage
     */
    fun toJson(): String {
        return JSONObject().apply {
            put("currentSurahIndex", currentSurahIndex)
            put("currentPositionMs", currentPositionMs)
            put("sessionStartTime", sessionStartTime)
            put("sessionListeningTimeMs", sessionListeningTimeMs)
            put("sessionSurahsCompleted", sessionSurahsCompleted)
            put("totalListeningTimeMs", totalListeningTimeMs)
            put("lastPlayedTimestamp", lastPlayedTimestamp)
        }.toString()
    }

    /**
     * Check if there's an active listening session
     */
    val isSessionActive: Boolean
        get() = sessionStartTime > 0

    /**
     * Get the current surah number (1-based for display)
     */
    val currentSurahNumber: Int
        get() = currentSurahIndex + 1

    /**
     * Get progress percentage through the entire Quran (0-100)
     */
    val overallProgressPercent: Int
        get() = ((currentSurahIndex.toFloat() / 114f) * 100).toInt()
}
