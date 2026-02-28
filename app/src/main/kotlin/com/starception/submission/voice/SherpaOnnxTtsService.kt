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

package com.starception.submission.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.starception.submission.settings.components.TtsModelType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.starception.submission.settings.components.TtsVoice
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Offline TTS service using Sherpa-ONNX with multiple voice options.
 * Supports Piper (American English) and VITS-VCTK (109 British speakers).
 * Provides high-quality, fully offline text-to-speech without network dependency.
 */
@Singleton
class SherpaOnnxTtsService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SherpaOnnxTtsService"
        private const val DEFAULT_SPEAKER_ID = 0
        private const val DEFAULT_SPEED = 1.0f
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var isInitialized = false
    private var isInitializing = false
    private var currentVoice: TtsVoice = TtsVoice.KOKORO_EN

    // Cache directory for extracted model files
    private val modelDir: File by lazy {
        File(context.filesDir, "tts_model").also { it.mkdirs() }
    }

    /**
     * Set the voice model to use.
     * If already initialized with a different voice, this will release and re-initialize.
     */
    fun setVoice(voice: TtsVoice) {
        if (currentVoice != voice) {
            Log.i(TAG, "Switching TTS voice from ${currentVoice.displayName} to ${voice.displayName}")
            currentVoice = voice
            if (isInitialized) {
                release()
            }
        }
    }

    /**
     * Initialize the TTS engine with the current voice.
     * Must be called before speaking.
     */
    suspend fun initialize(): Boolean {
        if (isInitialized) return true
        if (isInitializing) return false

        isInitializing = true

        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Initializing Sherpa-ONNX TTS with ${currentVoice.displayName}...")

                // Extract model files from assets based on selected voice
                val modelPath = extractAssetFile(currentVoice.modelFile)
                val tokensPath = extractAssetFile(currentVoice.tokensFile)

                // Handle data directory (for espeak-ng)
                val dataDir = if (currentVoice.dataDir.isNotEmpty()) {
                    extractAssetDir(currentVoice.dataDir)
                } else ""

                // Handle lexicon file (for VITS)
                val lexiconPath = if (currentVoice.lexiconFile.isNotEmpty()) {
                    extractAssetFile(currentVoice.lexiconFile)
                } else ""

                // Handle voices file (for Kokoro)
                val voicesPath = if (currentVoice.voicesFile.isNotEmpty()) {
                    extractAssetFile(currentVoice.voicesFile)
                } else ""

                if (modelPath == null || tokensPath == null) {
                    Log.e(TAG, "Failed to extract model files")
                    isInitializing = false
                    return@withContext false
                }

                Log.d(TAG, "Model path: $modelPath")
                Log.d(TAG, "Tokens path: $tokensPath")
                Log.d(TAG, "Data dir: ${dataDir ?: "N/A"}")
                Log.d(TAG, "Lexicon path: ${lexiconPath ?: "N/A"}")
                Log.d(TAG, "Voices path: ${voicesPath ?: "N/A"}")

                // Configure TTS model based on type
                val modelConfig = when (currentVoice.modelType) {
                    TtsModelType.KOKORO -> {
                        Log.i(TAG, "Configuring Kokoro model...")
                        val kokoroConfig = OfflineTtsKokoroModelConfig(
                            model = modelPath,
                            voices = voicesPath ?: "",
                            tokens = tokensPath,
                            dataDir = dataDir ?: "",
                            lengthScale = 1.0f
                        )
                        OfflineTtsModelConfig(
                            kokoro = kokoroConfig,
                            numThreads = 2,
                            debug = false,
                            provider = "cpu"
                        )
                    }
                    TtsModelType.VITS -> {
                        Log.i(TAG, "Configuring VITS model...")
                        val vitsConfig = OfflineTtsVitsModelConfig(
                            model = modelPath,
                            lexicon = lexiconPath ?: "",
                            tokens = tokensPath,
                            dataDir = dataDir ?: "",
                            noiseScale = 0.667f,
                            noiseScaleW = 0.8f,
                            lengthScale = 1.0f
                        )
                        OfflineTtsModelConfig(
                            vits = vitsConfig,
                            numThreads = 2,
                            debug = false,
                            provider = "cpu"
                        )
                    }
                }

                // Create TTS config
                val ttsConfig = OfflineTtsConfig(
                    model = modelConfig,
                    maxNumSentences = 1
                )

                // Create TTS instance
                tts = OfflineTts(
                    assetManager = null,
                    config = ttsConfig
                )

                isInitialized = true
                isInitializing = false

                Log.i(TAG, "${currentVoice.displayName} TTS initialized successfully")
                Log.i(TAG, "Sample rate: ${tts?.sampleRate()}, Speakers: ${tts?.numSpeakers()}")

                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize TTS", e)
                isInitializing = false
                false
            }
        }
    }

    /**
     * Speak text using offline TTS.
     * Splits long text into sentences for faster initial playback.
     * @param text The text to speak
     * @param speed Speech speed (0.5 = half speed, 2.0 = double speed)
     * @param speakerId Speaker ID for multi-speaker models (0 for single speaker)
     * @param onComplete Callback when speech is complete
     */
    suspend fun speak(
        text: String,
        speed: Float = DEFAULT_SPEED,
        speakerId: Int = DEFAULT_SPEAKER_ID,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        if (!isInitialized) {
            val initialized = initialize()
            if (!initialized) {
                Log.e(TAG, "TTS not initialized")
                onComplete?.invoke()
                return false
            }
        }

        return suspendCancellableCoroutine { continuation ->
            scope.launch {
                try {
                    // Split text into sentences for faster progressive playback
                    val sentences = splitIntoSentences(text)
                        .filter { it.isNotBlank() }
                    Log.d(TAG, "Speaking ${sentences.size} sentences with parallel generation...")

                    if (sentences.isEmpty()) {
                        onComplete?.invoke()
                        if (continuation.isActive) continuation.resume(true)
                        return@launch
                    }

                    // Generate first sentence immediately
                    var currentAudio = generateSentence(sentences[0], speakerId, speed, 1, sentences.size)

                    for (index in sentences.indices) {
                        if (currentAudio == null) {
                            Log.w(TAG, "Skipping empty audio for sentence ${index + 1}")
                            // Try to generate next sentence if available
                            if (index + 1 < sentences.size) {
                                currentAudio = generateSentence(sentences[index + 1], speakerId, speed, index + 2, sentences.size)
                            }
                            continue
                        }

                        // Start generating NEXT sentence in background while playing current
                        val nextAudioDeferred: Deferred<GeneratedAudio?>? = if (index + 1 < sentences.size) {
                            async {
                                generateSentence(sentences[index + 1], speakerId, speed, index + 2, sentences.size)
                            }
                        } else null

                        // Play current sentence (blocking)
                        Log.d(TAG, "Playing sentence ${index + 1}/${sentences.size}")
                        playAudio(currentAudio)

                        // Get pre-generated next audio (should be ready by now)
                        currentAudio = nextAudioDeferred?.await()
                    }

                    onComplete?.invoke()
                    if (continuation.isActive) continuation.resume(true)

                } catch (e: Exception) {
                    Log.e(TAG, "Error generating/playing speech", e)
                    onComplete?.invoke()
                    if (continuation.isActive) continuation.resume(false)
                }
            }

            continuation.invokeOnCancellation {
                stopSpeaking()
            }
        }
    }

    /**
     * Generate audio for a single sentence.
     */
    private fun generateSentence(
        sentence: String,
        speakerId: Int,
        speed: Float,
        sentenceNum: Int,
        totalSentences: Int
    ): GeneratedAudio? {
        Log.d(TAG, "Generating sentence $sentenceNum/$totalSentences: \"${sentence.take(50)}...\"")
        val audio = tts?.generate(
            text = sentence,
            sid = speakerId,
            speed = speed
        )
        if (audio != null && audio.samples.isNotEmpty()) {
            Log.d(TAG, "Generated ${audio.samples.size} samples at ${audio.sampleRate} Hz")
        }
        return if (audio?.samples?.isNotEmpty() == true) audio else null
    }

    /**
     * Split text into sentences for progressive playback.
     * Keeps sentences reasonably sized for faster generation.
     */
    private fun splitIntoSentences(text: String): List<String> {
        // Split on sentence boundaries (. ! ?)
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // If text is short or has no sentence boundaries, return as-is
        if (sentences.size <= 1 && text.length > 200) {
            // Split long text without punctuation by commas or chunks
            return text.split(Regex(",\\s*|;\\s*"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

        return sentences
    }

    /**
     * Play generated audio samples.
     */
    private fun playAudio(audio: GeneratedAudio) {
        try {
            // Stop any existing playback
            stopAudioTrack()

            val sampleRate = audio.sampleRate
            val samples = audio.samples

            // Convert float samples to 16-bit PCM
            val pcmData = ShortArray(samples.size)
            for (i in samples.indices) {
                // Clamp to [-1, 1] and convert to 16-bit
                val sample = samples[i].coerceIn(-1f, 1f)
                pcmData[i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }

            // Create AudioTrack
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize.coerceAtLeast(pcmData.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack?.write(pcmData, 0, pcmData.size)
            audioTrack?.play()

            // Wait for playback to complete
            val durationMs = (samples.size * 1000L) / sampleRate
            Thread.sleep(durationMs + 100) // Add small buffer

            stopAudioTrack()

        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
            stopAudioTrack()
        }
    }

    /**
     * Stop current speech playback.
     */
    fun stopSpeaking() {
        stopAudioTrack()
    }

    private fun stopAudioTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack", e)
        }
    }

    /**
     * Extract a single file from assets to internal storage.
     */
    private fun extractAssetFile(assetPath: String): String? {
        val outputFile = File(modelDir, assetPath)

        // Check if already extracted
        if (outputFile.exists() && outputFile.length() > 0) {
            return outputFile.absolutePath
        }

        return try {
            outputFile.parentFile?.mkdirs()

            context.assets.open("tts/$assetPath").use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Extracted: $assetPath -> ${outputFile.absolutePath}")
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract asset: $assetPath", e)
            null
        }
    }

    /**
     * Extract a directory from assets to internal storage.
     */
    private fun extractAssetDir(assetDir: String): String? {
        val outputDir = File(modelDir, assetDir)

        // Check if already extracted (look for marker file)
        val markerFile = File(outputDir, ".extracted")
        if (markerFile.exists()) {
            return outputDir.absolutePath
        }

        return try {
            outputDir.mkdirs()

            // List and extract all files in the directory
            val files = context.assets.list("tts/$assetDir") ?: return null

            for (file in files) {
                val assetPath = "$assetDir/$file"
                val outputFile = File(outputDir, file)

                // Check if it's a directory
                val subFiles = context.assets.list("tts/$assetPath")
                if (subFiles != null && subFiles.isNotEmpty()) {
                    // Recursively extract subdirectory
                    extractAssetDirRecursive("tts/$assetPath", outputFile)
                } else {
                    // Extract file
                    context.assets.open("tts/$assetPath").use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            // Create marker file
            markerFile.createNewFile()

            Log.d(TAG, "Extracted directory: $assetDir -> ${outputDir.absolutePath}")
            outputDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract asset directory: $assetDir", e)
            null
        }
    }

    private fun extractAssetDirRecursive(assetPath: String, outputDir: File) {
        outputDir.mkdirs()

        val files = context.assets.list(assetPath) ?: return

        for (file in files) {
            val fullAssetPath = "$assetPath/$file"
            val outputFile = File(outputDir, file)

            val subFiles = context.assets.list(fullAssetPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                extractAssetDirRecursive(fullAssetPath, outputFile)
            } else {
                context.assets.open(fullAssetPath).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    /**
     * Check if TTS is ready to use.
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Get the sample rate of the TTS model.
     */
    fun getSampleRate(): Int = tts?.sampleRate() ?: 22050

    /**
     * Get the number of speakers in the model.
     */
    fun getNumSpeakers(): Int = tts?.numSpeakers() ?: 1

    /**
     * Release all resources.
     */
    fun release() {
        stopSpeaking()
        tts?.free()
        tts = null
        isInitialized = false
    }

    /**
     * Test the TTS engine with a simple phrase.
     * Call this to verify TTS is working.
     */
    fun runTest() {
        Log.i(TAG, "========== TTS TEST STARTED ==========")
        scope.launch {
            try {
                Log.i(TAG, "Step 1: Initializing TTS...")
                val initSuccess = initialize()
                Log.i(TAG, "Step 1 Result: ${if (initSuccess) "SUCCESS" else "FAILED"}")

                if (initSuccess) {
                    Log.i(TAG, "Step 2: Generating speech with VITS-VCTK...")
                    val speakSuccess = speak("Hello! The VITS text to speech is working perfectly.")
                    Log.i(TAG, "Step 2 Result: ${if (speakSuccess) "SUCCESS" else "FAILED"}")
                }

                Log.i(TAG, "========== TTS TEST COMPLETED ==========")
            } catch (e: Exception) {
                Log.e(TAG, "TTS TEST FAILED with exception", e)
            }
        }
    }
}
