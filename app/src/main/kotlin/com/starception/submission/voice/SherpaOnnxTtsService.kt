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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Offline TTS service using Sherpa-ONNX with Coqui VITS models.
 * Provides fully offline text-to-speech without network dependency.
 */
@Singleton
class SherpaOnnxTtsService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SherpaOnnxTtsService"

        // Model files in assets/tts/
        private const val MODEL_FILE = "en_US-lessac-medium.onnx"
        private const val TOKENS_FILE = "tokens.txt"
        private const val DATA_DIR = "espeak-ng-data"

        // TTS configuration
        private const val DEFAULT_SPEAKER_ID = 0
        private const val DEFAULT_SPEED = 1.0f
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var isInitialized = false
    private var isInitializing = false

    // Cache directory for extracted model files
    private val modelDir: File by lazy {
        File(context.filesDir, "tts_model").also { it.mkdirs() }
    }

    /**
     * Initialize the TTS engine.
     * Must be called before speaking.
     */
    suspend fun initialize(): Boolean {
        if (isInitialized) return true
        if (isInitializing) return false

        isInitializing = true

        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Initializing Sherpa-ONNX TTS...")

                // Extract model files from assets if needed
                val modelPath = extractAssetFile(MODEL_FILE)
                val tokensPath = extractAssetFile(TOKENS_FILE)
                val dataDir = extractAssetDir(DATA_DIR)

                if (modelPath == null || tokensPath == null || dataDir == null) {
                    Log.e(TAG, "Failed to extract model files")
                    isInitializing = false
                    return@withContext false
                }

                Log.d(TAG, "Model path: $modelPath")
                Log.d(TAG, "Tokens path: $tokensPath")
                Log.d(TAG, "Data dir: $dataDir")

                // Configure VITS model
                val vitsConfig = OfflineTtsVitsModelConfig(
                    model = modelPath,
                    lexicon = "",
                    tokens = tokensPath,
                    dataDir = dataDir,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )

                // Configure TTS model
                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )

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

                Log.i(TAG, "Sherpa-ONNX TTS initialized successfully")
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
                    Log.d(TAG, "Generating speech for: \"$text\"")

                    // Generate audio
                    val audio = tts?.generate(
                        text = text,
                        sid = speakerId,
                        speed = speed
                    )

                    if (audio == null || audio.samples.isEmpty()) {
                        Log.e(TAG, "Failed to generate audio")
                        onComplete?.invoke()
                        if (continuation.isActive) continuation.resume(false)
                        return@launch
                    }

                    Log.d(TAG, "Generated ${audio.samples.size} samples at ${audio.sampleRate} Hz")

                    // Play audio
                    playAudio(audio)

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
}
