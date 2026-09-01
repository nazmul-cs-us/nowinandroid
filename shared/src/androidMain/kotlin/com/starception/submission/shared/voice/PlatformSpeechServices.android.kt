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

actual class PlatformSpeechRecognizer actual constructor() {
    actual fun start(
        mode: VoiceRecognitionMode,
        onEvent: (SpeechRecognitionEvent) -> Unit,
    ) {
        onEvent(SpeechRecognitionEvent.Error("Speech recognition is supplied by the Android app"))
    }

    actual fun stop() = Unit
}

actual class PlatformSpeechSynthesizer actual constructor() {
    actual fun voices(): List<NarrationVoice> = emptyList()

    actual fun speak(
        text: String,
        voiceIdentifier: String?,
        language: String,
        onComplete: (String?) -> Unit,
    ): Boolean {
        onComplete("Speech synthesis is supplied by the Android app")
        return false
    }

    actual fun stop() = Unit
}
