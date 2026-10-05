/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.core.model.deenly

/** Product areas that can contribute an executable Now Nudge candidate. */
enum class DeenlyFeatureArea {
    PRAYER,
    QIBLA,
    QURAN,
    HADITH,
    DUA_DHIKR,
    LEARNING,
    SALAH_TRAINING,
    DISCOVERY,
    SAVED_CONTENT,
    SEARCH,
    DRIVING,
    DOWNLOADS,
    SETTINGS,
    INTERESTS,
    PROFILE,
}

/** The product event that proves a recommended action delivered value. */
enum class DeenlyCompletionSignal {
    ACTION_CONFIRMED,
    DESTINATION_OPENED,
    CONTENT_CONSUMED,
    PLAYBACK_STARTED,
    PLAYBACK_COMPLETED,
    LESSON_COMPLETED,
    DOWNLOAD_COMPLETED,
    SETTING_CHANGED,
    SESSION_STARTED,
}

/**
 * Stable metadata for one executable app action.
 *
 * IDs are persisted in interaction events and training datasets, so they must never be renamed.
 * A feature can add new IDs without changing the ranking model's output shape: the model scores
 * candidates rather than predicting one class from a closed enum.
 */
data class DeenlyActionDefinition(
    val id: String,
    val featureArea: DeenlyFeatureArea,
    val completionSignal: DeenlyCompletionSignal,
    val defaultCooldownMinutes: Int,
    val startsAudio: Boolean = false,
    val requiresNetwork: Boolean = false,
)

/** Stable action identifiers shared by Android, iOS, analytics, and offline training. */
object DeenlyActionIds {
    const val PRAYER_MARK_COMPLETED = "prayer.mark_completed"
    const val PRAYER_OPEN_TIMES = "prayer.open_times"
    const val PRAYER_GO_TO_MOSQUE = "prayer.go_to_mosque"
    const val PRAYER_CONFIGURE_ALERTS = "prayer.configure_alerts"
    const val QIBLA_OPEN = "qibla.open"

    const val QURAN_OPEN_LIBRARY = "quran.open_library"
    const val QURAN_RESUME_READING = "quran.resume_reading"
    const val QURAN_OPEN_SURAH = "quran.open_surah"
    const val QURAN_OPEN_AYAH = "quran.open_ayah"
    const val QURAN_PLAY_RECITATION = "quran.play_recitation"

    const val HADITH_OPEN_BUKHARI_BOOK = "hadith.open_sahih_bukhari_book"
    const val HADITH_OPEN_TIRMIDHI_BOOK = "hadith.open_shamayel_at_tirmidhi_book"
    const val HADITH_PLAY_BUKHARI_BOOK = "hadith.play_sahih_bukhari_book"
    const val HADITH_PLAY_TIRMIDHI_BOOK = "hadith.play_shamayel_at_tirmidhi_book"
    const val HADITH_FEELING_BLESSED_BUKHARI = "hadith.feeling_blessed.sahih_bukhari"
    const val HADITH_FEELING_BLESSED_TIRMIDHI = "hadith.feeling_blessed.shamayel_at_tirmidhi"

    const val DUA_OPEN_QURANIC = "dua.open_quranic"
    const val DUA_OPEN_FORTRESS = "dua.open_fortress"
    const val DUA_OPEN_MORNING_EVENING = "dua.open_morning_evening"
    const val DUA_PLAY_TRAVEL = "dua.play_travel"

    const val LEARNING_RESUME_COURSE = "learning.resume_course"
    const val LEARNING_CONTINUE_LESSON = "learning.continue_lesson"
    const val LEARNING_START_QUIZ = "learning.start_quiz"
    const val LEARNING_OPEN_KNOWLEDGE = "learning.open_knowledge"

    const val SALAH_START_TRAINING = "salah.start_training"
    const val SALAH_REVIEW_SESSION = "salah.review_session"
    const val SALAH_OPEN_SIMULATION = "salah.open_simulation"

    const val DISCOVERY_OPEN_FOR_YOU = "discovery.open_for_you"
    const val DISCOVERY_OPEN_TOPIC = "discovery.open_topic"
    const val DISCOVERY_OPEN_ARTICLE = "discovery.open_article"
    const val SAVED_OPEN = "saved.open"

    const val SEARCH_OPEN = "search.open"
    const val SEARCH_START_VOICE = "search.start_voice"
    const val DRIVING_OPEN_MODE = "driving.open_mode"
    const val DOWNLOAD_REQUIRED_CONTENT = "downloads.required_content"
    const val DOWNLOAD_RETRY = "downloads.retry"
    const val SETTINGS_OPEN = "settings.open"
    const val INTERESTS_OPEN = "interests.open"
    const val PROFILE_OPEN = "profile.open"

    const val CONTEXTUAL_OPEN = "contextual.open"
}

/** Complete version-one catalog of user-facing actions that Now Nudge can rank. */
object DeenlyActionCatalog {
    val all: List<DeenlyActionDefinition> = listOf(
        action(DeenlyActionIds.PRAYER_MARK_COMPLETED, DeenlyFeatureArea.PRAYER, DeenlyCompletionSignal.ACTION_CONFIRMED, 180),
        action(DeenlyActionIds.PRAYER_OPEN_TIMES, DeenlyFeatureArea.PRAYER, DeenlyCompletionSignal.DESTINATION_OPENED, 240),
        action(DeenlyActionIds.PRAYER_GO_TO_MOSQUE, DeenlyFeatureArea.PRAYER, DeenlyCompletionSignal.DESTINATION_OPENED, 720),
        action(DeenlyActionIds.PRAYER_CONFIGURE_ALERTS, DeenlyFeatureArea.PRAYER, DeenlyCompletionSignal.SETTING_CHANGED, 4_320),
        action(DeenlyActionIds.QIBLA_OPEN, DeenlyFeatureArea.QIBLA, DeenlyCompletionSignal.DESTINATION_OPENED, 180),
        action(DeenlyActionIds.QURAN_OPEN_LIBRARY, DeenlyFeatureArea.QURAN, DeenlyCompletionSignal.DESTINATION_OPENED, 720),
        action(DeenlyActionIds.QURAN_RESUME_READING, DeenlyFeatureArea.QURAN, DeenlyCompletionSignal.CONTENT_CONSUMED, 240),
        action(DeenlyActionIds.QURAN_OPEN_SURAH, DeenlyFeatureArea.QURAN, DeenlyCompletionSignal.CONTENT_CONSUMED, 360),
        action(DeenlyActionIds.QURAN_OPEN_AYAH, DeenlyFeatureArea.QURAN, DeenlyCompletionSignal.CONTENT_CONSUMED, 360),
        action(DeenlyActionIds.QURAN_PLAY_RECITATION, DeenlyFeatureArea.QURAN, DeenlyCompletionSignal.PLAYBACK_STARTED, 360, startsAudio = true, requiresNetwork = true),
        action(DeenlyActionIds.HADITH_OPEN_BUKHARI_BOOK, DeenlyFeatureArea.HADITH, DeenlyCompletionSignal.CONTENT_CONSUMED, 720),
        action(DeenlyActionIds.HADITH_OPEN_TIRMIDHI_BOOK, DeenlyFeatureArea.HADITH, DeenlyCompletionSignal.CONTENT_CONSUMED, 720),
        action(DeenlyActionIds.HADITH_PLAY_BUKHARI_BOOK, DeenlyFeatureArea.HADITH, DeenlyCompletionSignal.PLAYBACK_STARTED, 720, startsAudio = true),
        action(DeenlyActionIds.HADITH_PLAY_TIRMIDHI_BOOK, DeenlyFeatureArea.HADITH, DeenlyCompletionSignal.PLAYBACK_STARTED, 720, startsAudio = true),
        action(DeenlyActionIds.HADITH_FEELING_BLESSED_BUKHARI, DeenlyFeatureArea.HADITH, DeenlyCompletionSignal.PLAYBACK_COMPLETED, 720, startsAudio = true),
        action(DeenlyActionIds.HADITH_FEELING_BLESSED_TIRMIDHI, DeenlyFeatureArea.HADITH, DeenlyCompletionSignal.PLAYBACK_COMPLETED, 720, startsAudio = true),
        action(DeenlyActionIds.DUA_OPEN_QURANIC, DeenlyFeatureArea.DUA_DHIKR, DeenlyCompletionSignal.CONTENT_CONSUMED, 360),
        action(DeenlyActionIds.DUA_OPEN_FORTRESS, DeenlyFeatureArea.DUA_DHIKR, DeenlyCompletionSignal.CONTENT_CONSUMED, 360),
        action(DeenlyActionIds.DUA_OPEN_MORNING_EVENING, DeenlyFeatureArea.DUA_DHIKR, DeenlyCompletionSignal.CONTENT_CONSUMED, 720),
        action(DeenlyActionIds.DUA_PLAY_TRAVEL, DeenlyFeatureArea.DUA_DHIKR, DeenlyCompletionSignal.PLAYBACK_STARTED, 360, startsAudio = true),
        action(DeenlyActionIds.LEARNING_RESUME_COURSE, DeenlyFeatureArea.LEARNING, DeenlyCompletionSignal.CONTENT_CONSUMED, 720),
        action(DeenlyActionIds.LEARNING_CONTINUE_LESSON, DeenlyFeatureArea.LEARNING, DeenlyCompletionSignal.LESSON_COMPLETED, 360),
        action(DeenlyActionIds.LEARNING_START_QUIZ, DeenlyFeatureArea.LEARNING, DeenlyCompletionSignal.ACTION_CONFIRMED, 240),
        action(DeenlyActionIds.LEARNING_OPEN_KNOWLEDGE, DeenlyFeatureArea.LEARNING, DeenlyCompletionSignal.CONTENT_CONSUMED, 240),
        action(DeenlyActionIds.SALAH_START_TRAINING, DeenlyFeatureArea.SALAH_TRAINING, DeenlyCompletionSignal.SESSION_STARTED, 1_440),
        action(DeenlyActionIds.SALAH_REVIEW_SESSION, DeenlyFeatureArea.SALAH_TRAINING, DeenlyCompletionSignal.CONTENT_CONSUMED, 720),
        action(DeenlyActionIds.SALAH_OPEN_SIMULATION, DeenlyFeatureArea.SALAH_TRAINING, DeenlyCompletionSignal.DESTINATION_OPENED, 1_440),
        action(DeenlyActionIds.DISCOVERY_OPEN_FOR_YOU, DeenlyFeatureArea.DISCOVERY, DeenlyCompletionSignal.CONTENT_CONSUMED, 720),
        action(DeenlyActionIds.DISCOVERY_OPEN_TOPIC, DeenlyFeatureArea.DISCOVERY, DeenlyCompletionSignal.CONTENT_CONSUMED, 720),
        action(DeenlyActionIds.DISCOVERY_OPEN_ARTICLE, DeenlyFeatureArea.DISCOVERY, DeenlyCompletionSignal.CONTENT_CONSUMED, 720),
        action(DeenlyActionIds.SAVED_OPEN, DeenlyFeatureArea.SAVED_CONTENT, DeenlyCompletionSignal.CONTENT_CONSUMED, 720),
        action(DeenlyActionIds.SEARCH_OPEN, DeenlyFeatureArea.SEARCH, DeenlyCompletionSignal.DESTINATION_OPENED, 720),
        action(DeenlyActionIds.SEARCH_START_VOICE, DeenlyFeatureArea.SEARCH, DeenlyCompletionSignal.ACTION_CONFIRMED, 720),
        action(DeenlyActionIds.DRIVING_OPEN_MODE, DeenlyFeatureArea.DRIVING, DeenlyCompletionSignal.SESSION_STARTED, 360, startsAudio = true),
        action(DeenlyActionIds.DOWNLOAD_REQUIRED_CONTENT, DeenlyFeatureArea.DOWNLOADS, DeenlyCompletionSignal.DOWNLOAD_COMPLETED, 1_440, requiresNetwork = true),
        action(DeenlyActionIds.DOWNLOAD_RETRY, DeenlyFeatureArea.DOWNLOADS, DeenlyCompletionSignal.DOWNLOAD_COMPLETED, 360, requiresNetwork = true),
        action(DeenlyActionIds.SETTINGS_OPEN, DeenlyFeatureArea.SETTINGS, DeenlyCompletionSignal.SETTING_CHANGED, 4_320),
        action(DeenlyActionIds.INTERESTS_OPEN, DeenlyFeatureArea.INTERESTS, DeenlyCompletionSignal.SETTING_CHANGED, 2_880),
        action(DeenlyActionIds.PROFILE_OPEN, DeenlyFeatureArea.PROFILE, DeenlyCompletionSignal.SETTING_CHANGED, 4_320),
        action(DeenlyActionIds.CONTEXTUAL_OPEN, DeenlyFeatureArea.DISCOVERY, DeenlyCompletionSignal.CONTENT_CONSUMED, 240),
    )

    private val byId = all.associateBy(DeenlyActionDefinition::id)

    init {
        check(byId.size == all.size) { "Now Nudge action IDs must be unique" }
    }

    fun definition(actionId: String): DeenlyActionDefinition? = byId[actionId]
}

private fun action(
    id: String,
    area: DeenlyFeatureArea,
    completion: DeenlyCompletionSignal,
    cooldownMinutes: Int,
    startsAudio: Boolean = false,
    requiresNetwork: Boolean = false,
) = DeenlyActionDefinition(
    id = id,
    featureArea = area,
    completionSignal = completion,
    defaultCooldownMinutes = cooldownMinutes,
    startsAudio = startsAudio,
    requiresNetwork = requiresNetwork,
)

fun DeenlyNudgeAction.defaultActionId(): String = when (this) {
    DeenlyNudgeAction.MARK_PRAYED -> DeenlyActionIds.PRAYER_MARK_COMPLETED
    DeenlyNudgeAction.OPEN_QIBLA -> DeenlyActionIds.QIBLA_OPEN
    DeenlyNudgeAction.PLAY_TRAVEL_DUA -> DeenlyActionIds.DUA_PLAY_TRAVEL
    DeenlyNudgeAction.PLAY_QUIZ -> DeenlyActionIds.LEARNING_START_QUIZ
    DeenlyNudgeAction.OPEN_CONTEXTUAL_RECOMMENDATION -> DeenlyActionIds.CONTEXTUAL_OPEN
}
