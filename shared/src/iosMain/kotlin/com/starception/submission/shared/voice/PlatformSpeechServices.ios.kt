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
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMeasurement
import kotlinx.cinterop.ObjCSignatureOverride
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLocale
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
actual class PlatformSpeechRecognizer actual constructor() {
    private val recognizer = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = "en-US"))
    private var audioEngine: AVAudioEngine? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var callback: ((SpeechRecognitionEvent) -> Unit)? = null
    private var mode = VoiceRecognitionMode.KEYWORDS

    actual fun start(
        mode: VoiceRecognitionMode,
        onEvent: (SpeechRecognitionEvent) -> Unit,
    ) {
        stop()
        this.mode = mode
        callback = onEvent
        SFSpeechRecognizer.requestAuthorization { status ->
            dispatch_async(dispatch_get_main_queue()) {
                if (status != SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
                    fail("Speech recognition permission was not granted")
                    return@dispatch_async
                }
                AVAudioSession.sharedInstance().requestRecordPermission { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (granted) beginRecording() else fail("Microphone permission was not granted")
                    }
                }
            }
        }
    }

    private fun beginRecording() {
        val speechRecognizer = recognizer
        if (!speechRecognizer.available) {
            fail("Speech recognition is unavailable")
            return
        }

        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeMeasurement,
            options = 0u,
            error = null,
        )
        session.setActive(true, withOptions = 0u, error = null)

        val engine = AVAudioEngine()
        val recognitionRequest = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
            if (speechRecognizer.supportsOnDeviceRecognition) requiresOnDeviceRecognition = true
            if (mode == VoiceRecognitionMode.KEYWORDS) contextualStrings = listOf("yes", "no")
        }
        val input = engine.inputNode
        val format = input.outputFormatForBus(0u)
        input.installTapOnBus(0u, bufferSize = 1_024u, format = format) { buffer, _ ->
            if (buffer != null) recognitionRequest.appendAudioPCMBuffer(buffer)
        }

        request = recognitionRequest
        audioEngine = engine
        task = speechRecognizer.recognitionTaskWithRequest(recognitionRequest) { result, error ->
            dispatch_async(dispatch_get_main_queue()) {
                if (request !== recognitionRequest) return@dispatch_async
                val text = result?.bestTranscription?.formattedString.orEmpty().trim()
                val resolved = resolvedRecognitionText(mode, text, result?.final == true)
                when {
                    resolved != null -> finish(resolved)
                    text.isNotEmpty() -> callback?.invoke(SpeechRecognitionEvent.Partial(text))
                    error != null -> fail(error.localizedDescription)
                }
            }
        }
        engine.prepare()
        if (!engine.startAndReturnError(null)) {
            fail("The microphone could not be started")
            return
        }
        callback?.invoke(SpeechRecognitionEvent.Listening)
    }

    private fun finish(text: String) {
        val target = callback
        callback = null
        stopInternals()
        target?.invoke(SpeechRecognitionEvent.Result(text))
    }

    private fun fail(message: String) {
        val target = callback
        callback = null
        stopInternals()
        target?.invoke(SpeechRecognitionEvent.Error(message))
    }

    actual fun stop() {
        callback = null
        stopInternals()
    }

    private fun stopInternals() {
        audioEngine?.stop()
        audioEngine?.inputNode?.removeTapOnBus(0u)
        request?.endAudio()
        task?.cancel()
        task = null
        request = null
        audioEngine = null
        AVAudioSession.sharedInstance().setActive(false, withOptions = 0u, error = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class PlatformSpeechSynthesizer actual constructor() {
    private val synthesizer = AVSpeechSynthesizer()
    private var completion: ((String?) -> Unit)? = null
    private var activeUtterance: AVSpeechUtterance? = null
    private val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        @ObjCSignatureOverride
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didFinishSpeechUtterance: AVSpeechUtterance,
        ) {
            if (didFinishSpeechUtterance === activeUtterance) complete(null)
        }

        @ObjCSignatureOverride
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didCancelSpeechUtterance: AVSpeechUtterance,
        ) {
            if (didCancelSpeechUtterance === activeUtterance) complete(null)
        }
    }

    init {
        synthesizer.delegate = delegate
    }

    actual fun voices(): List<NarrationVoice> = listOf("en-US", "en-GB")
        .mapNotNull { language -> AVSpeechSynthesisVoice.voiceWithLanguage(language) }
        .distinctBy { it.identifier }
        .map { NarrationVoice(it.identifier, it.name, it.language) }

    actual fun speak(
        text: String,
        voiceIdentifier: String?,
        language: String,
        onComplete: (String?) -> Unit,
    ): Boolean {
        stop()
        val voice = voiceIdentifier?.let(AVSpeechSynthesisVoice::voiceWithIdentifier)
            ?: AVSpeechSynthesisVoice.voiceWithLanguage(language)
            ?: return false
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, withOptions = 0u, error = null)
        completion = onComplete
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text).apply {
            this.voice = voice
            rate = 0.48f
        }
        activeUtterance = utterance
        synthesizer.speakUtterance(utterance)
        return true
    }

    actual fun stop() {
        completion = null
        activeUtterance = null
        if (synthesizer.speaking) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        AVAudioSession.sharedInstance().setActive(false, withOptions = 0u, error = null)
    }

    private fun complete(error: String?) {
        val target = completion
        completion = null
        activeUtterance = null
        AVAudioSession.sharedInstance().setActive(false, withOptions = 0u, error = null)
        target?.invoke(error)
    }
}
