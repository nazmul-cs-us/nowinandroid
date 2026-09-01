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

package com.starception.submission.shared.voice

import com.starception.submission.shared.settings.VoiceRecognitionMode

data class NarrationVoice(
    val identifier: String,
    val name: String,
    val language: String,
    val totalSpeakers: Int = 1,
)

sealed interface SpeechRecognitionEvent {
    data object Listening : SpeechRecognitionEvent
    data class Partial(val text: String) : SpeechRecognitionEvent
    data class Result(val text: String) : SpeechRecognitionEvent
    data class Error(val message: String) : SpeechRecognitionEvent
}

internal fun resolvedRecognitionText(
    mode: VoiceRecognitionMode,
    transcript: String,
    isFinal: Boolean,
): String? {
    val text = transcript.trim()
    if (text.isEmpty()) return null
    if (mode == VoiceRecognitionMode.TRANSCRIPTION) return text.takeIf { isFinal }
    return text.split(' ')
        .asReversed()
        .map { word -> word.trim { !it.isLetter() } }
        .firstOrNull { word -> word.equals("yes", true) || word.equals("no", true) }
}

expect class PlatformSpeechRecognizer() {
    fun start(mode: VoiceRecognitionMode, onEvent: (SpeechRecognitionEvent) -> Unit)
    fun stop()
}

expect class PlatformSpeechSynthesizer() {
    fun voices(): List<NarrationVoice>
    fun speak(
        text: String,
        voiceIdentifier: String? = null,
        language: String = "en-US",
        onComplete: (String?) -> Unit = {},
    ): Boolean
    fun stop()
}
