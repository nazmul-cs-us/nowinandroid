/*
 * Copyright 2021 The Android Open Source Project
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

package com.starception.submission.shared.settings

import com.starception.submission.config.TravelDuaSettings
import com.starception.submission.shared.storage.KeyValueStore
import com.starception.submission.shared.storage.platformKeyValueStore

enum class VoiceRecognitionMode {
    KEYWORDS,
    TRANSCRIPTION,
}

/** Small, platform-independent preferences used by the iOS audio settings. */
class UserAudioSettings(private val store: KeyValueStore = platformKeyValueStore()) {

    fun travelDua(): TravelDuaSettings = TravelDuaSettings(
        enabled = boolean(KEY_TRAVEL_ENABLED, true),
        cooldownMinutes = integer(KEY_TRAVEL_COOLDOWN, 5).coerceIn(1, 30),
        playbackDelaySeconds = integer(KEY_TRAVEL_DELAY, 60).coerceIn(10, 180),
        gapToleranceMinutes = integer(KEY_TRAVEL_GAP, 5).coerceIn(1, 15),
        drivingSpeedThresholdKmh = integer(KEY_TRAVEL_SPEED, 10).coerceIn(10, 40),
    )

    fun saveTravelDua(settings: TravelDuaSettings) {
        store.putString(KEY_TRAVEL_ENABLED, settings.enabled.toString())
        store.putString(KEY_TRAVEL_COOLDOWN, settings.cooldownMinutes.coerceIn(1, 30).toString())
        store.putString(KEY_TRAVEL_DELAY, settings.playbackDelaySeconds.coerceIn(10, 180).toString())
        store.putString(KEY_TRAVEL_GAP, settings.gapToleranceMinutes.coerceIn(1, 15).toString())
        store.putString(KEY_TRAVEL_SPEED, settings.drivingSpeedThresholdKmh.coerceIn(10, 40).toString())
    }

    fun recognitionMode(): VoiceRecognitionMode = store.getString(KEY_RECOGNITION_MODE)
        ?.let { value -> VoiceRecognitionMode.entries.firstOrNull { it.name == value } }
        ?: VoiceRecognitionMode.KEYWORDS

    fun saveRecognitionMode(mode: VoiceRecognitionMode) {
        store.putString(KEY_RECOGNITION_MODE, mode.name)
    }

    fun narrationVoiceIdentifier(): String? =
        store.getString(KEY_NARRATION_VOICE)?.takeIf { it.isNotBlank() }

    fun saveNarrationVoiceIdentifier(identifier: String) {
        store.putString(KEY_NARRATION_VOICE, identifier)
    }

    fun narrationSpeakerId(): Int = integer(KEY_NARRATION_SPEAKER, 0).coerceAtLeast(0)

    fun saveNarrationSpeakerId(speakerId: Int) {
        store.putString(KEY_NARRATION_SPEAKER, speakerId.coerceAtLeast(0).toString())
    }

    private fun integer(key: String, default: Int): Int =
        store.getString(key)?.toIntOrNull() ?: default

    private fun boolean(key: String, default: Boolean): Boolean =
        store.getString(key)?.toBooleanStrictOrNull() ?: default

    private companion object {
        const val KEY_TRAVEL_ENABLED = "ios_travel_dua_enabled"
        const val KEY_TRAVEL_COOLDOWN = "ios_travel_dua_cooldown_minutes"
        const val KEY_TRAVEL_DELAY = "ios_travel_dua_playback_delay_seconds"
        const val KEY_TRAVEL_GAP = "ios_travel_dua_gap_tolerance_minutes"
        const val KEY_TRAVEL_SPEED = "ios_travel_dua_speed_threshold_kmh"
        const val KEY_RECOGNITION_MODE = "ios_voice_recognition_mode"
        const val KEY_NARRATION_VOICE = "ios_narration_voice_identifier"
        const val KEY_NARRATION_SPEAKER = "ios_narration_speaker_id"
    }
}
