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

package com.starception.submission.shared.travel

import com.starception.submission.config.TravelDuaSettings
import com.starception.submission.shared.audio.quranAudioUrl
import com.starception.submission.shared.hadith.SharedHadithRepository
import com.starception.submission.shared.hadith.createSharedHadithRepository
import com.starception.submission.shared.voice.PlatformSpeechSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Driving mode audio chain, mirroring the Android DrivingAudioService:
 * Travel Dua -> daily Bukhari hadith (TTS) -> a Quran surah (streaming).
 *
 * Speed evidence feeds [updateSpeed]; once the shared TravelDuaTriggerPolicy
 * decides the user has been driving long enough, the chain plays once per
 * trip. A manual [start] is also available.
 */
class DrivingModeCoordinator(
    private val duaPlayer: TravelDuaPlayer = TravelDuaPlayer(),
    private val hadithRepository: SharedHadithRepository = createSharedHadithRepository(),
    private val synthesizer: PlatformSpeechSynthesizer = PlatformSpeechSynthesizer(),
    private val onSurahRequested: (url: String) -> Unit = {},
) {
    /** Chain stage surfaced to the UI. */
    enum class Stage { IDLE, DUA, HADITH, QURAN }

    var onStageChanged: ((Stage) -> Unit)? = null

    private var stage: Stage = Stage.IDLE
        set(value) {
            field = value
            onStageChanged?.invoke(value)
        }

    private val policy = TravelDuaTriggerPolicy()
    private var scope: CoroutineScope? = null
    private var hadithJob: Job? = null

    /** Feed GPS speed (m/s); returns true when the policy triggers the chain. */
    fun updateSpeed(speedMetersPerSecond: Double, settings: TravelDuaSettings): Boolean {
        val shouldPlay = policy.update(speedMetersPerSecond, currentTimeMillis(), settings)
        if (shouldPlay) start()
        return shouldPlay
    }

    /** Starts the full chain manually (or from the trigger policy). */
    fun start() {
        if (stage != Stage.IDLE) return
        stage = Stage.DUA
        duaPlayer.play {
            playDailyHadithThenSurah()
        }
    }

    private fun playDailyHadithThenSurah() {
        val workScope = scope ?: CoroutineScope(Dispatchers.Default).also { scope = it }
        stage = Stage.HADITH
        hadithJob = workScope.launch {
            val hadith = runCatching { hadithRepository.getHadith(DAILY_HADITH_ID) }.getOrNull()
            if (hadith != null && hadith.english.isNotBlank()) {
                val done = kotlinx.coroutines.CompletableDeferred<Unit>()
                val ok = synthesizer.speak(
                    text = hadith.english,
                    onComplete = { done.complete(Unit) },
                )
                if (ok) done.await()
            }
            if (stage != Stage.IDLE) {
                stage = Stage.QURAN
                onSurahRequested(quranAudioUrl(TRAVEL_SURA))
            }
            stage = Stage.IDLE
        }
    }

    fun stop() {
        hadithJob?.cancel()
        hadithJob = null
        duaPlayer.stop()
        synthesizer.stop()
        stage = Stage.IDLE
    }

    companion object {
        /** The same fixed narrations the Android service opens its chain with. */
        private const val DAILY_HADITH_ID = 1
        private const val TRAVEL_SURA = 1

        private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
    }
}
