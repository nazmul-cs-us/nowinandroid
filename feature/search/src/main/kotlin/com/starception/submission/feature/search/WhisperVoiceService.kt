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

package com.starception.submission.feature.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.whispercpp.media.decodeWaveFile
import com.whispercpp.recorder.Recorder
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Whisper Voice Service using whisper.cpp (native C++ implementation)
 *
 * This provides offline, on-device speech-to-text for search queries.
 * Uses the whisper.cpp library with GGML models for fast, accurate transcription.
 *
 * Benefits over cloud-based recognition:
 * - Works completely offline
 * - Privacy-preserving (audio never leaves device)
 * - No network latency
 * - Works in airplane mode
 * - Native C++ performance
 */
class WhisperVoiceService(
    private val context: Context
) {
    companion object {
        private const val TAG = "WhisperVoiceService"
        private const val MODEL_ASSET_PATH = "models/ggml-tiny.en.bin"
        private const val RECORDING_FILE = "whisper_recording.wav"

        // End-pointing: recording stops automatically once the user has spoken
        // and then stayed quiet for END_SILENCE_MS. Amplitude is the recorder's
        // normalized RMS (0..1). Rooms and mics vary too much for a fixed cutoff
        // (measured ambient on a Pixel 9 Pro: 0.02 with spikes to 0.06; speech
        // 0.07-0.14), so:
        // - speech is detected on the raw level (fast attack — smoothing made
        //   short words invisible) but needs SPEECH_DEBOUNCE_TICKS consecutive
        //   ticks, so a single 100ms ambient spike can't reset the silence timer,
        // - the threshold rides a tracked noise floor (drops to the quietest
        //   recent level instantly, creeps back up slowly).
        // Calibrated against real VOICE_RECOGNITION-source recordings from this
        // device (raw, unprocessed — ~10x quieter than the adaptive MIC source):
        // speech registers 0.01-0.03 RMS, true silence 0.002-0.008. Detection is
        // primarily relative to the tracked noise floor; the absolute minimums
        // exist only to survive a near-zero floor.
        private const val SPEECH_OVER_NOISE_FACTOR = 2.5f
        private const val MIN_SPEECH_THRESHOLD = 0.008f
        private const val SPEECH_DEBOUNCE_TICKS = 2
        private const val NOISE_FLOOR_RISE_PER_TICK = 0.0001f
        private const val END_SILENCE_MS = 1_800L
        private const val NO_SPEECH_TIMEOUT_MS = 6_000L
        private const val MAX_RECORDING_DURATION_MS = 12_000L

        // Below this peak level a capture is treated as genuinely empty and
        // skips transcription; anything louder gets transcribed even if the
        // debounced detector never fired, so quiet speech is never thrown away.
        private const val SKIP_TRANSCRIPTION_PEAK = 0.006f

        // Silero VAD (speech-based end-pointing; RMS is only the fallback when
        // the VAD model fails to load).
        private const val VAD_MODEL_ASSET_PATH = "models/silero_vad.onnx"
        private const val VAD_WINDOW_SIZE = 512
        private const val VAD_SPEECH_PROBABILITY = 0.5f
        // The raw VOICE_RECOGNITION source is quiet (speech 0.01-0.03); a fixed
        // pre-gain puts it in the range Silero was trained on.
        private const val VAD_PRE_GAIN = 8f
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var initJob: Job? = null
    private var recordJob: Job? = null
    private var autoStopJob: Job? = null

    // Silero VAD: recognizes speech rather than loudness, so end-pointing works
    // even when quiet speech overlaps ambient noise in RMS terms (measured on
    // this device: quiet words 0.006-0.01 vs ambient 0.003-0.008 — an energy
    // threshold fundamentally cannot separate those).
    private var vad: com.k2fsa.sherpa.onnx.Vad? = null
    private val vadWindow = FloatArray(VAD_WINDOW_SIZE)
    private var vadWindowFill = 0

    @Volatile
    private var lastVadSpeechAtMs = 0L

    @Volatile
    private var vadSpeechEverDetected = false

    private var whisperContext: WhisperContext? = null
    private var recorder: Recorder? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isInitializing = MutableStateFlow(false)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    // Normalized microphone amplitude (0..1) while recording, ~60 updates/sec.
    // Drives the live voice-wave visualization in the search bar.
    private val _voiceLevel = MutableStateFlow(0f)
    val voiceLevel: StateFlow<Float> = _voiceLevel.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var currentCallback: ((VoiceSearchService.VoiceSearchResult) -> Unit)? = null

    /**
     * Initialize the Whisper model
     * This loads the GGML model from assets.
     * Should be called before starting transcription.
     */
    fun initialize(onComplete: ((Boolean) -> Unit)? = null) {
        if (_isInitialized.value) {
            onComplete?.invoke(true)
            return
        }

        if (_isInitializing.value) {
            return
        }

        initJob = scope.launch {
            _isInitializing.value = true
            _statusMessage.value = "Loading Whisper model..."

            try {
                val success = withContext(Dispatchers.IO) {
                    initializeWhisper()
                }

                _isInitialized.value = success
                _statusMessage.value = if (success) "Whisper ready" else "Failed to load model"
                onComplete?.invoke(success)

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Whisper", e)
                _error.value = "Failed to initialize: ${e.message}"
                _statusMessage.value = "Initialization failed"
                onComplete?.invoke(false)
            } finally {
                _isInitializing.value = false
            }
        }
    }

    private fun initializeWhisper(): Boolean {
        try {
            Log.i(TAG, "System info: ${WhisperContext.getSystemInfo()}")

            // Load model from bundled assets first, then the CDN download location
            // (Settings > Content & Storage / asset download manager puts it there).
            whisperContext = try {
                Log.i(TAG, "Initializing whisper.cpp from asset: $MODEL_ASSET_PATH")
                WhisperContext.createContextFromAsset(context.assets, MODEL_ASSET_PATH)
            } catch (e: Exception) {
                val cdnFile = cdnModelFile()
                if (cdnFile.exists() && cdnFile.length() > 100_000) {
                    Log.i(TAG, "Loading model from CDN download: ${cdnFile.absolutePath}")
                    WhisperContext.createContextFromFile(cdnFile.absolutePath)
                } else {
                    Log.e(TAG, "Whisper model not found in assets or CDN downloads")
                    throw e
                }
            }

            // Initialize recorder
            recorder = Recorder()

            // Silero VAD for speech-based end-pointing; on failure the monitor
            // falls back to RMS thresholds.
            if (vad == null) {
                vad = try {
                    val vadConfig = com.k2fsa.sherpa.onnx.VadModelConfig(
                        sileroVadModelConfig = com.k2fsa.sherpa.onnx.SileroVadModelConfig(
                            model = VAD_MODEL_ASSET_PATH,
                            threshold = VAD_SPEECH_PROBABILITY,
                            minSilenceDuration = 0.25f,
                            minSpeechDuration = 0.1f,
                            windowSize = VAD_WINDOW_SIZE,
                        ),
                        sampleRate = 16000,
                        numThreads = 1,
                        provider = "cpu",
                    )
                    com.k2fsa.sherpa.onnx.Vad(context.assets, vadConfig).also {
                        Log.i(TAG, "Silero VAD initialized")
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Silero VAD unavailable, using RMS end-pointing fallback", e)
                    null
                }
            }

            Log.i(TAG, "whisper.cpp initialized successfully")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeWhisper", e)
            return false
        }
    }

    private fun cdnModelFile(): File =
        File(File(context.filesDir, "cdn_assets"), MODEL_ASSET_PATH)

    /**
     * True when the Whisper model file exists (bundled in the APK or downloaded
     * from the CDN), without loading it. Lets callers distinguish "model missing,
     * offer the download" from "model present but not yet initialized".
     */
    fun isModelAvailable(): Boolean {
        val bundled = try {
            context.assets.open(MODEL_ASSET_PATH).use { true }
        } catch (_: Exception) {
            false
        }
        return bundled || cdnModelFile().let { it.exists() && it.length() > 100_000 }
    }

    private fun getRecordingFile(): File {
        return File(context.filesDir, RECORDING_FILE)
    }

    /**
     * Check if microphone permission is granted
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if Whisper is available and initialized
     */
    fun isAvailable(): Boolean {
        return _isInitialized.value
    }

    /**
     * Start listening for voice input using Whisper
     * Records audio and transcribes using the local whisper.cpp model.
     */
    fun startListening(onResult: (VoiceSearchService.VoiceSearchResult) -> Unit) {
        if (!hasPermission()) {
            Log.e(TAG, "Microphone permission not granted")
            onResult(VoiceSearchService.VoiceSearchResult.Error("Microphone permission required"))
            return
        }

        if (!_isInitialized.value) {
            Log.w(TAG, "Whisper not initialized, initializing now...")
            initialize { success ->
                if (success) {
                    startListeningInternal(onResult)
                } else {
                    onResult(VoiceSearchService.VoiceSearchResult.Error("Failed to initialize Whisper"))
                }
            }
            return
        }

        startListeningInternal(onResult)
    }

    private fun startListeningInternal(onResult: (VoiceSearchService.VoiceSearchResult) -> Unit) {
        if (_isListening.value || _isTranscribing.value) {
            Log.w(TAG, "Already listening or transcribing")
            return
        }

        currentCallback = onResult
        _error.value = null
        _recognizedText.value = null
        _isListening.value = true
        _statusMessage.value = "Listening..."

        // Fresh VAD state for this capture
        lastVadSpeechAtMs = 0L
        vadSpeechEverDetected = false
        vadWindowFill = 0
        try {
            vad?.reset()
        } catch (e: Exception) {
            Log.w(TAG, "VAD reset failed", e)
        }

        // Start recording using coroutine-based recorder
        recordJob = scope.launch {
            try {
                val recordingFile = getRecordingFile()
                Log.i(TAG, "Starting recording to: ${recordingFile.absolutePath}")

                recorder?.startRecordingWithAmplitude(
                    recordingFile,
                    onError = { error ->
                        Log.e(TAG, "Recording error", error)
                        scope.launch(Dispatchers.Main) {
                            _isListening.value = false
                            _voiceLevel.value = 0f
                            _error.value = "Recording error: ${error.message}"
                            currentCallback?.invoke(
                                VoiceSearchService.VoiceSearchResult.Error("Recording failed: ${error.message}")
                            )
                            currentCallback = null
                        }
                    },
                    onAmplitude = { amplitude ->
                        _voiceLevel.value = amplitude
                    },
                    onSamples = ::feedVad,
                )

                Log.i(TAG, "Recording started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                _isListening.value = false
                _error.value = "Failed to start recording: ${e.message}"
                currentCallback?.invoke(
                    VoiceSearchService.VoiceSearchResult.Error("Failed to start recording: ${e.message}")
                )
                currentCallback = null
            }
        }

        // End-pointing: watch the live amplitude and stop automatically once the
        // user has spoken and then gone quiet — without this the recording never
        // ends and no transcription ever runs.
        autoStopJob?.cancel()
        autoStopJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            val vadActive = vad != null
            var lastSpeechAt = 0L
            var noiseFloor = Float.MAX_VALUE
            var ticksAboveThreshold = 0
            var peakLevel = 0f
            while (_isListening.value) {
                kotlinx.coroutines.delay(100)
                val now = System.currentTimeMillis()
                val level = _voiceLevel.value
                if (level > peakLevel) peakLevel = level
                if (vadActive) {
                    // Primary: Silero VAD — recognizes speech itself, so quiet
                    // words that overlap ambient in loudness still count.
                    lastSpeechAt = lastVadSpeechAtMs
                } else {
                    // Fallback: adaptive RMS threshold. Track the ambient floor:
                    // follow drops immediately, rise slowly so brief speech
                    // doesn't get absorbed into the floor.
                    noiseFloor =
                        if (level < noiseFloor) level else noiseFloor + NOISE_FLOOR_RISE_PER_TICK
                    val speechThreshold =
                        maxOf(noiseFloor * SPEECH_OVER_NOISE_FACTOR, MIN_SPEECH_THRESHOLD)
                    if (level >= speechThreshold) {
                        ticksAboveThreshold++
                        if (ticksAboveThreshold >= SPEECH_DEBOUNCE_TICKS) lastSpeechAt = now
                    } else {
                        ticksAboveThreshold = 0
                    }
                }
                val elapsed = now - startedAt
                val shouldStop = when {
                    lastSpeechAt != 0L && now - lastSpeechAt >= END_SILENCE_MS -> true
                    lastSpeechAt == 0L && elapsed >= NO_SPEECH_TIMEOUT_MS -> true
                    elapsed >= MAX_RECORDING_DURATION_MS -> true
                    else -> false
                }
                if (shouldStop) {
                    Log.i(
                        TAG,
                        "Auto-stopping recording (vad=$vadActive, spoke=${lastSpeechAt != 0L}, " +
                            "elapsed=${elapsed}ms, peak=$peakLevel)",
                    )
                    if (lastSpeechAt == 0L && peakLevel < SKIP_TRANSCRIPTION_PEAK) {
                        // Genuinely nothing there — skip transcription; running
                        // Whisper over pure silence wastes seconds (~1.1x realtime).
                        // Borderline captures (quiet speech) still get transcribed.
                        cancelListening()
                    } else {
                        stopListening()
                    }
                    break
                }
            }
        }

        Log.i(TAG, "Started listening for voice input (whisper.cpp)")
    }

    /**
     * Feeds raw recorder samples to the Silero VAD in 512-sample windows.
     * Runs on the recording thread; one window costs well under a millisecond.
     */
    private fun feedVad(samples: FloatArray) {
        val v = vad ?: return
        var i = 0
        while (i < samples.size) {
            val take = minOf(VAD_WINDOW_SIZE - vadWindowFill, samples.size - i)
            for (j in 0 until take) {
                vadWindow[vadWindowFill + j] = (samples[i + j] * VAD_PRE_GAIN).coerceIn(-1f, 1f)
            }
            vadWindowFill += take
            i += take
            if (vadWindowFill == VAD_WINDOW_SIZE) {
                vadWindowFill = 0
                try {
                    if (v.compute(vadWindow) >= VAD_SPEECH_PROBABILITY) {
                        lastVadSpeechAtMs = System.currentTimeMillis()
                        vadSpeechEverDetected = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "VAD compute failed", e)
                }
            }
        }
    }

    /**
     * Stop recording without transcribing (no speech was detected).
     * Reports [VoiceSearchService.VoiceSearchResult.Cancelled] to the caller.
     */
    private fun cancelListening() {
        autoStopJob?.cancel()
        autoStopJob = null
        if (!_isListening.value) return
        scope.launch {
            try {
                recorder?.stopRecording()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recorder on cancel", e)
            }
            _isListening.value = false
            _voiceLevel.value = 0f
            _statusMessage.value = "No speech detected"
            val callback = currentCallback
            currentCallback = null
            callback?.invoke(VoiceSearchService.VoiceSearchResult.Cancelled)
        }
    }

    /**
     * Stop listening and start transcription
     */
    fun stopListening() {
        autoStopJob?.cancel()
        autoStopJob = null
        if (_isListening.value) {
            _statusMessage.value = "Processing..."

            scope.launch {
                try {
                    // Stop recording
                    recorder?.stopRecording()
                    _isListening.value = false
                    _voiceLevel.value = 0f

                    // Start transcription
                    startTranscription()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recorder", e)
                    _isListening.value = false
                    _error.value = "Error stopping recording: ${e.message}"
                    currentCallback?.invoke(
                        VoiceSearchService.VoiceSearchResult.Error("Error: ${e.message}")
                    )
                    currentCallback = null
                }
            }
        }
    }

    private suspend fun startTranscription() {
        _isTranscribing.value = true
        _statusMessage.value = "Transcribing..."

        try {
            val recordingFile = getRecordingFile()

            if (!recordingFile.exists() || recordingFile.length() == 0L) {
                Log.e(TAG, "Recording file missing or empty")
                handleTranscriptionResult(null)
                return
            }

            Log.i(TAG, "Transcribing file: ${recordingFile.absolutePath} (${recordingFile.length()} bytes)")

            // Decode WAV to float array
            val decoded = withContext(Dispatchers.IO) {
                decodeWaveFile(recordingFile)
            }

            // Transcription costs ~1.1x realtime on-device, so every second of
            // leading/trailing silence is a second the user waits — trim it.
            // Then level the volume: this device has no hardware AGC, so words
            // spoken from a distance arrive ~5x quieter than close speech and
            // Whisper misses them without software gain.
            val audioData = applySoftwareAgc(trimSilence(decoded))
            Log.i(
                TAG,
                "Audio decoded: ${decoded.size} samples (${decoded.size / 16000.0}s), " +
                    "trimmed to ${audioData.size} (${audioData.size / 16000.0}s)",
            )

            // Transcribe using whisper.cpp
            val result = whisperContext?.transcribeData(audioData, printTimestamp = false)

            handleTranscriptionResult(result)

        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            _isTranscribing.value = false
            _error.value = "Transcription error: ${e.message}"
            currentCallback?.invoke(
                VoiceSearchService.VoiceSearchResult.Error("Transcription failed: ${e.message}")
            )
            currentCallback = null
        }
    }

    /**
     * Drop leading/trailing silence so Whisper only processes speech. Windows
     * are 100ms RMS; the cut threshold adapts to the clip's own quietest
     * windows, and a 250ms margin is kept on both sides so speech onsets and
     * tails survive. Returns the input unchanged when nothing crosses the
     * threshold (lets Whisper make the final call).
     */
    private fun trimSilence(audio: FloatArray, sampleRate: Int = 16000): FloatArray {
        val win = sampleRate / 10
        val windowCount = audio.size / win
        if (windowCount < 5) return audio
        val rms = FloatArray(windowCount) { w ->
            var sum = 0.0
            for (i in w * win until (w + 1) * win) sum += audio[i] * audio[i]
            kotlin.math.sqrt(sum / win).toFloat()
        }
        val noiseFloor = rms.sorted()[windowCount / 5]
        val threshold = maxOf(noiseFloor * SPEECH_OVER_NOISE_FACTOR, MIN_SPEECH_THRESHOLD)
        val first = rms.indexOfFirst { it >= threshold }
        val last = rms.indexOfLast { it >= threshold }
        if (first == -1) return audio
        val margin = sampleRate / 4
        val start = (first * win - margin).coerceAtLeast(0)
        val end = ((last + 1) * win + margin).coerceAtMost(audio.size)
        return audio.copyOfRange(start, end)
    }

    /**
     * Software AGC: per-100ms-window gain toward a target speech level, smoothed
     * across windows to avoid pumping. Only boosts (never attenuates), capped at
     * 12x so true silence isn't blown up into noise. Needed because this device
     * reports AutomaticGainControl as unavailable.
     */
    private fun applySoftwareAgc(audio: FloatArray, sampleRate: Int = 16000): FloatArray {
        val win = sampleRate / 10
        if (audio.size < win) return audio
        val target = 0.1f
        var gain = 1f
        val out = FloatArray(audio.size)
        var i = 0
        while (i < audio.size) {
            val end = minOf(i + win, audio.size)
            var sum = 0.0
            for (j in i until end) sum += audio[j] * audio[j]
            val rms = kotlin.math.sqrt(sum / (end - i)).toFloat()
            val desired = if (rms > 1e-4f) (target / rms).coerceIn(1f, 12f) else gain
            gain += 0.3f * (desired - gain)
            for (j in i until end) out[j] = (audio[j] * gain).coerceIn(-1f, 1f)
            i = end
        }
        return out
    }

    private fun handleTranscriptionResult(result: String?) {
        // Clean up result - remove leading/trailing whitespace and common artifacts
        val text = result?.trim()
            ?.replace(Regex("^\\[.*?\\]\\s*"), "") // Remove timestamp prefixes like [00:00.000 --> 00:03.000]
            ?.replace(Regex("\\s+"), " ") // Normalize whitespace
            ?.trim()
            ?: ""

        val callback = currentCallback
        currentCallback = null

        // Invoke callback on main thread for UI updates
        scope.launch(Dispatchers.Main) {
            _isTranscribing.value = false
            _recognizedText.value = text

            if (text.isNotBlank()) {
                Log.i(TAG, "Transcription result: $text")
                _statusMessage.value = "Done"
                callback?.invoke(VoiceSearchService.VoiceSearchResult.Success(text))
            } else {
                Log.w(TAG, "Empty transcription result")
                _statusMessage.value = "No speech detected"
                callback?.invoke(VoiceSearchService.VoiceSearchResult.Cancelled)
            }
        }
    }

    /**
     * Cancel listening and transcription
     */
    fun cancel() {
        scope.launch {
            _isListening.value = false
            _isTranscribing.value = false

            try {
                recorder?.stopRecording()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recorder during cancel", e)
            }

            currentCallback?.invoke(VoiceSearchService.VoiceSearchResult.Cancelled)
            currentCallback = null
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        scope.launch {
            cancel()
            initJob?.cancel()
            recordJob?.cancel()
            autoStopJob?.cancel()

            try {
                vad?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing VAD", e)
            }
            vad = null

            try {
                whisperContext?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing whisper context", e)
            }

            whisperContext = null
            recorder = null
            _isInitialized.value = false
        }
    }

    /**
     * Test method: Records 3 seconds and transcribes.
     * Use to verify whisper.cpp is working properly.
     */
    fun testMicrophonePlayback() {
        Log.i(TAG, "Test not implemented for whisper.cpp recorder")
        _statusMessage.value = "Test not available"
    }
}
