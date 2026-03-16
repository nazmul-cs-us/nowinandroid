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
import com.starception.submission.download.AssetRepository
import com.starception.submission.settings.components.TtsModelType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    @ApplicationContext private val context: Context,
    private val assetRepository: AssetRepository,
) {
    companion object {
        private const val TAG = "SherpaOnnxTtsService"
        private const val DEFAULT_SPEAKER_ID = 0
        private const val DEFAULT_SPEED = 1.0f
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Load persisted cache from disk on service creation
        loadCacheFromDisk()
    }

    // Mutex for thread-safe access to TTS engine (prevents native crashes)
    private val ttsMutex = Mutex()

    // Flag to track if generation is in progress (for safe release)
    @Volatile
    private var isGenerating = false

    // Flag to cancel ongoing background pre-generation when voice prompt needs priority
    @Volatile
    private var cancelBackgroundGeneration = false

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var isInitialized = false
    private var isInitializing = false
    private var currentVoice: TtsVoice = TtsVoice.KOKORO_EN

    // Pre-generated audio cache (key: text hash, value: samples + sample rate)
    // Cache is persisted to disk for survival across app restarts
    private data class CachedAudio(val samples: FloatArray, val sampleRate: Int)
    private val audioCache = mutableMapOf<Int, CachedAudio>()

    // Track which texts are currently being generated to avoid duplicates
    private val generatingHashes = mutableSetOf<Int>()

    // Persistent cache directory
    private val cacheDir: File by lazy {
        File(context.cacheDir, "tts_audio_cache").also {
            it.mkdirs()
            Log.d(TAG, "📂 TTS cache directory: ${it.absolutePath}")
        }
    }

    // Maximum number of cached audio files to keep on disk
    private val MAX_CACHE_FILES = 10

    // Cache directory for extracted model files
    private val modelDir: File by lazy {
        File(context.filesDir, "tts_model").also { it.mkdirs() }
    }

    /**
     * Set the voice model to use.
     * If already initialized with a different voice, this will release and re-initialize.
     * Thread-safe: waits for any ongoing generation to complete before releasing.
     */
    fun setVoice(voice: TtsVoice) {
        if (currentVoice != voice) {
            Log.i(TAG, "Switching TTS voice from ${currentVoice.displayName} to ${voice.displayName}")
            currentVoice = voice
            if (isInitialized) {
                // Don't release while generation is in progress - just mark for re-init
                if (isGenerating) {
                    Log.w(TAG, "TTS generation in progress, deferring voice switch")
                    scope.launch {
                        // Wait for generation to complete
                        while (isGenerating) {
                            kotlinx.coroutines.delay(100)
                        }
                        ttsMutex.withLock {
                            releaseInternal()
                        }
                    }
                } else {
                    release()
                }
            }
        }
    }

    // Mutex for initialization to ensure only one initialization at a time
    private val initMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Initialize the TTS engine with the current voice.
     * Must be called before speaking.
     * Thread-safe: multiple callers will wait for initialization to complete.
     */
    suspend fun initialize(): Boolean {
        // Fast path: already initialized
        if (isInitialized) return true

        // Use mutex to ensure only one initialization at a time
        // Other callers will wait until initialization completes
        return initMutex.withLock {
            // Check again after acquiring lock (another thread might have initialized)
            if (isInitialized) return@withLock true

            isInitializing = true

        withContext(Dispatchers.IO) {
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

                // Pre-validate extracted model files before passing to native code.
                // The native OfflineTts() constructor will SIGABRT if given invalid/corrupt
                // model files, and native signals bypass Kotlin try/catch.
                val modelFile = java.io.File(modelPath)
                val tokensFile = java.io.File(tokensPath)
                val MIN_MODEL_SIZE = 100_000L // ONNX models should be at least 100KB
                val MIN_TOKENS_SIZE = 100L    // tokens.txt should be at least 100 bytes

                if (!modelFile.exists() || modelFile.length() < MIN_MODEL_SIZE) {
                    Log.e(TAG, "Model file validation failed: exists=${modelFile.exists()}, size=${modelFile.length()} bytes (min=$MIN_MODEL_SIZE)")
                    // Delete stale/corrupt extracted file so next attempt re-extracts
                    modelFile.delete()
                    isInitializing = false
                    return@withContext false
                }

                if (!tokensFile.exists() || tokensFile.length() < MIN_TOKENS_SIZE) {
                    Log.e(TAG, "Tokens file validation failed: exists=${tokensFile.exists()}, size=${tokensFile.length()} bytes (min=$MIN_TOKENS_SIZE)")
                    tokensFile.delete()
                    isInitializing = false
                    return@withContext false
                }

                // Validate voices file for Kokoro (required, ~4MB)
                if (currentVoice.modelType == TtsModelType.KOKORO && !voicesPath.isNullOrEmpty()) {
                    val voicesFile = java.io.File(voicesPath)
                    if (!voicesFile.exists() || voicesFile.length() < MIN_MODEL_SIZE) {
                        Log.e(TAG, "Voices file validation failed: exists=${voicesFile.exists()}, size=${voicesFile.length()} bytes (min=$MIN_MODEL_SIZE)")
                        voicesFile.delete()
                        isInitializing = false
                        return@withContext false
                    }
                }

                Log.i(TAG, "Model file validation passed: model=${modelFile.length()} bytes, tokens=${tokensFile.length()} bytes")

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
    }

    /**
     * Speak text using offline TTS.
     * Generates all audio first, then plays continuously without gaps.
     * Thread-safe: uses mutex to prevent concurrent TTS access.
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
                // Use mutex to prevent concurrent TTS access (native library is not thread-safe)
                ttsMutex.withLock {
                try {
                    // Double-check TTS is still valid after acquiring lock
                    if (tts == null || !isInitialized) {
                        Log.w(TAG, "TTS became unavailable, reinitializing...")
                        val reinit = initialize()
                        if (!reinit) {
                            onComplete?.invoke()
                            if (continuation.isActive) continuation.resume(false)
                            return@withLock
                        }
                    }

                    // Split text into sentences for generation
                    val sentences = splitIntoSentences(text)
                        .filter { it.isNotBlank() }
                    Log.d(TAG, "Generating ${sentences.size} sentences...")

                    if (sentences.isEmpty()) {
                        onComplete?.invoke()
                        if (continuation.isActive) continuation.resume(true)
                        return@withLock
                    }

                    // Hybrid approach:
                    // - Short text (<=10 sentences): Generate all first for smooth playback
                    // - Long text (>10 sentences): Batch of 5 for faster start with some gaps
                    val sampleRate = tts?.sampleRate() ?: 22050
                    var playedAnySamples = false

                    if (sentences.size <= 10) {
                        // Short text: generate all first for gap-free playback
                        Log.d(TAG, "Short text (${sentences.size} sentences) - generating all first")
                        val allSamples = mutableListOf<Float>()

                        for ((index, sentence) in sentences.withIndex()) {
                            val audio = generateSentence(sentence, speakerId, speed, index + 1, sentences.size)
                            if (audio != null && audio.samples.isNotEmpty()) {
                                allSamples.addAll(audio.samples.toList())
                            }
                        }

                        if (allSamples.isNotEmpty()) {
                            Log.d(TAG, "Playing ${allSamples.size} samples continuously")
                            playAudioSamples(allSamples.toFloatArray(), sampleRate)
                            playedAnySamples = true
                        }
                    } else {
                        // Long text: batch of 5 sentences for faster initial response
                        val BATCH_SIZE = 5
                        val batches = sentences.chunked(BATCH_SIZE)
                        Log.d(TAG, "Long text (${sentences.size} sentences) - ${batches.size} batches of $BATCH_SIZE")

                        for ((batchIndex, batch) in batches.withIndex()) {
                            val batchSamples = mutableListOf<Float>()

                            for ((sentenceIndex, sentence) in batch.withIndex()) {
                                val globalIndex = batchIndex * BATCH_SIZE + sentenceIndex + 1
                                val audio = generateSentence(sentence, speakerId, speed, globalIndex, sentences.size)
                                if (audio != null && audio.samples.isNotEmpty()) {
                                    batchSamples.addAll(audio.samples.toList())
                                }
                            }

                            if (batchSamples.isNotEmpty()) {
                                Log.d(TAG, "Playing batch ${batchIndex + 1}/${batches.size}")
                                playAudioSamples(batchSamples.toFloatArray(), sampleRate)
                                playedAnySamples = true
                            }
                        }
                    }

                    if (!playedAnySamples) {
                        Log.w(TAG, "No audio samples generated")
                        onComplete?.invoke()
                        if (continuation.isActive) continuation.resume(false)
                        return@withLock
                    }

                    Log.d(TAG, "Finished playing all ${sentences.size} sentences")

                    onComplete?.invoke()
                    if (continuation.isActive) continuation.resume(true)

                } catch (e: Exception) {
                    Log.e(TAG, "Error generating/playing speech", e)
                    onComplete?.invoke()
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            }

            continuation.invokeOnCancellation {
                stopSpeaking()
            }
        }
    }

    /**
     * Generate audio for a single sentence.
     * Thread-safe with null checks to prevent native crashes.
     */
    private fun generateSentence(
        sentence: String,
        speakerId: Int,
        speed: Float,
        sentenceNum: Int,
        totalSentences: Int
    ): GeneratedAudio? {
        // Capture TTS reference to prevent race condition
        val ttsEngine = tts
        if (ttsEngine == null) {
            Log.w(TAG, "TTS engine is null, cannot generate audio")
            return null
        }

        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized, cannot generate audio")
            return null
        }

        Log.d(TAG, "Generating sentence $sentenceNum/$totalSentences: \"${sentence.take(50)}...\"")

        return try {
            isGenerating = true
            val audio = ttsEngine.generate(
                text = sentence,
                sid = speakerId,
                speed = speed
            )
            if (audio != null && audio.samples.isNotEmpty()) {
                Log.d(TAG, "Generated ${audio.samples.size} samples at ${audio.sampleRate} Hz")
            }
            if (audio?.samples?.isNotEmpty() == true) audio else null
        } catch (e: Exception) {
            Log.e(TAG, "Native TTS error generating sentence: ${e.message}", e)
            null
        } finally {
            isGenerating = false
        }
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
     * Play generated audio samples from a GeneratedAudio object.
     */
    private fun playAudio(audio: GeneratedAudio) {
        playAudioSamples(audio.samples, audio.sampleRate)
    }

    /**
     * Play audio samples continuously without gaps.
     * Uses a single AudioTrack for smooth playback.
     */
    private fun playAudioSamples(samples: FloatArray, sampleRate: Int) {
        try {
            // Stop any existing playback
            stopAudioTrack()

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
            Thread.sleep(durationMs + 100) // Buffer for audio cleanup

            stopAudioTrack()

            // Minimal delay - mic now works immediately after TTS
            // Removed 200ms delay that was causing user's "yes" to be missed
            Thread.sleep(20)

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

    /**
     * Request cancellation of any ongoing background pre-generation.
     * Use this before speaking high-priority audio (like voice prompts).
     */
    fun cancelBackgroundWork() {
        cancelBackgroundGeneration = true
        Log.i(TAG, "🛑 Cancellation requested for background generation")
    }

    /**
     * Check if background TTS generation is currently in progress.
     * If true, callers should use an alternative TTS (e.g., Android TTS)
     * to avoid waiting for the mutex.
     */
    fun isBackgroundWorkInProgress(): Boolean {
        return isGenerating || generatingHashes.isNotEmpty()
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

    // ==================== Pre-generation API ====================

    /**
     * Pre-generate TTS audio in background. Call this while other audio plays.
     * The generated audio will be cached and played instantly when speak() is called.
     * Multiple calls can run concurrently - each text generates independently.
     */
    fun preGenerateAsync(
        text: String,
        speakerId: Int = DEFAULT_SPEAKER_ID,
        speed: Float = DEFAULT_SPEED
    ) {
        val textHash = text.hashCode()

        // Skip if already cached
        if (audioCache.containsKey(textHash)) {
            Log.d(TAG, "Audio already cached for text hash $textHash")
            return
        }

        // Skip if already being generated
        synchronized(generatingHashes) {
            if (textHash in generatingHashes) {
                Log.d(TAG, "Audio already being generated for text hash $textHash")
                return
            }
            generatingHashes.add(textHash)
        }

        // Launch independent job for this text (don't cancel others)
        scope.launch {
            Log.i(TAG, "🔄 Starting background pre-generation for hash=$textHash...")

            try {
                if (!isInitialized) {
                    val initialized = initialize()
                    if (!initialized) {
                        Log.e(TAG, "Failed to initialize TTS for pre-generation")
                        return@launch
                    }
                }

                // IMPORTANT: Acquire mutex PER-SENTENCE to allow priority speech to interrupt
                // This prevents voice prompts from waiting 20+ seconds for entire pre-generation
                try {
                    val sentences = splitIntoSentences(text).filter { it.isNotBlank() }
                    val allSamples = mutableListOf<Float>()
                    var sampleRate = tts?.sampleRate() ?: 22050

                    Log.d(TAG, "🔄 Pre-generating ${sentences.size} sentences for hash=$textHash...")

                    for ((index, sentence) in sentences.withIndex()) {
                        // Check for cancellation request BEFORE acquiring mutex
                        if (cancelBackgroundGeneration) {
                            Log.i(TAG, "🛑 Background generation cancelled for hash=$textHash at sentence ${index + 1}/${sentences.size}")
                            cancelBackgroundGeneration = false
                            break
                        }

                        // Acquire mutex only for this sentence - allows priority speech to jump in between sentences
                        ttsMutex.withLock {
                            // Double-check cancellation after acquiring mutex
                            if (cancelBackgroundGeneration) {
                                Log.i(TAG, "🛑 Background generation cancelled (in mutex) for hash=$textHash")
                                cancelBackgroundGeneration = false
                                return@withLock
                            }

                            val audio = generateSentence(sentence, speakerId, speed, index + 1, sentences.size)
                            if (audio != null && audio.samples.isNotEmpty()) {
                                allSamples.addAll(audio.samples.toList())
                                sampleRate = audio.sampleRate
                            }
                        }
                    }

                    if (allSamples.isNotEmpty()) {
                        val cachedAudio = CachedAudio(allSamples.toFloatArray(), sampleRate)
                        audioCache[textHash] = cachedAudio
                        Log.i(TAG, "✅ Pre-generation complete: ${allSamples.size} samples cached (hash=$textHash)")
                        Log.d(TAG, "📦 Cache now has ${audioCache.size} entries: ${audioCache.keys}")

                        // Persist to disk for survival across app restarts
                        saveToDisk(textHash, cachedAudio)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during pre-generation for hash=$textHash", e)
                }
            } finally {
                // Remove from generating set when done
                synchronized(generatingHashes) {
                    generatingHashes.remove(textHash)
                }
            }
        }
    }

    /**
     * Check if audio for given text is pre-generated and cached (memory or disk).
     */
    fun isCached(text: String): Boolean {
        val hash = text.hashCode()

        // First check memory cache
        if (audioCache.containsKey(hash)) {
            return true
        }

        // Then check disk cache
        val diskFile = File(cacheDir, "${hash}_*.pcm")
        val files = cacheDir.listFiles { file ->
            file.name.startsWith("${hash}_") && file.extension == "pcm"
        }

        if (files != null && files.isNotEmpty()) {
            // Found on disk, load into memory
            loadSingleFileFromDisk(files.first())
            return audioCache.containsKey(hash)
        }

        if (audioCache.isNotEmpty()) {
            Log.d(TAG, "🔍 Cache lookup: hash=$hash NOT found. Cache has ${audioCache.size} entries: ${audioCache.keys}")
        }
        return false
    }

    /**
     * Load a single cache file from disk into memory.
     */
    private fun loadSingleFileFromDisk(file: File): Boolean {
        return try {
            val parts = file.nameWithoutExtension.split("_")
            if (parts.size != 2) return false

            val hash = parts[0].toIntOrNull() ?: return false
            val sampleRate = parts[1].toIntOrNull() ?: return false

            if (audioCache.containsKey(hash)) return true // Already loaded

            val bytes = file.readBytes()
            val samples = FloatArray(bytes.size / 4)
            java.nio.ByteBuffer.wrap(bytes)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer()
                .get(samples)

            audioCache[hash] = CachedAudio(samples, sampleRate)
            Log.d(TAG, "📂 Loaded from disk: ${file.name} (${samples.size} samples)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load from disk: ${file.name}", e)
            false
        }
    }

    /**
     * Get current cache size (number of pre-generated audio clips).
     */
    fun getCacheSize(): Int = audioCache.size

    /**
     * Get cache info for logging/debugging.
     * Returns a summary of cached audio clips (memory + disk).
     */
    fun getCacheInfo(): String {
        // Memory cache info
        val totalSamples = audioCache.values.sumOf { it.samples.size }
        val totalDurationMs = if (audioCache.isNotEmpty()) {
            val avgSampleRate = audioCache.values.first().sampleRate
            (totalSamples * 1000L) / avgSampleRate
        } else 0L

        // Disk cache info
        val diskFiles = cacheDir.listFiles { file -> file.extension == "pcm" } ?: emptyArray()
        val diskSizeKb = diskFiles.sumOf { it.length() } / 1024

        return "📦 TTS Cache: ${audioCache.size} in memory (~${totalDurationMs / 1000}s), ${diskFiles.size} on disk (${diskSizeKb}KB)"
    }

    /**
     * Get number of cached files on disk.
     */
    fun getDiskCacheCount(): Int {
        return cacheDir.listFiles { file -> file.extension == "pcm" }?.size ?: 0
    }

    /**
     * Play pre-generated audio if cached, otherwise generate and play normally.
     * Checks both memory and disk cache.
     */
    suspend fun speakCachedOrGenerate(
        text: String,
        speakerId: Int = DEFAULT_SPEAKER_ID,
        speed: Float = DEFAULT_SPEED,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        val textHash = text.hashCode()

        // Check memory cache first
        var cached = audioCache[textHash]

        // If not in memory, try loading from disk
        if (cached == null) {
            val files = cacheDir.listFiles { file ->
                file.name.startsWith("${textHash}_") && file.extension == "pcm"
            }
            if (files != null && files.isNotEmpty()) {
                if (loadSingleFileFromDisk(files.first())) {
                    cached = audioCache[textHash]
                    Log.i(TAG, "📂 Loaded from disk cache: hash=$textHash")
                }
            }
        }

        if (cached != null) {
            Log.i(TAG, "🎯 Playing from cache (${cached.samples.size} samples, hash=$textHash)")
            // DON'T remove from memory cache - keep for potential replay
            // But DO delete the disk file to allow new hadith caching
            deleteDiskCache(textHash)
            playAudioSamples(cached.samples, cached.sampleRate)
            audioCache.remove(textHash) // Remove from memory after playing
            onComplete?.invoke()
            return true
        }

        // Not cached, generate and play normally
        Log.d(TAG, "Cache miss (hash=$textHash), generating normally")
        return speak(text, speed, speakerId, onComplete)
    }

    /**
     * Delete a specific cache file from disk.
     */
    private fun deleteDiskCache(textHash: Int) {
        try {
            val files = cacheDir.listFiles { file ->
                file.name.startsWith("${textHash}_") && file.extension == "pcm"
            }
            files?.forEach { file ->
                file.delete()
                Log.d(TAG, "🗑️ Deleted disk cache: ${file.name}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting disk cache for hash=$textHash", e)
        }
    }

    /**
     * Clear all cached audio (memory and disk).
     */
    fun clearCache() {
        audioCache.clear()
        synchronized(generatingHashes) {
            generatingHashes.clear()
        }
        // Also clear disk cache
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "Audio cache cleared (memory and disk)")
    }

    /**
     * Load cached audio files from disk into memory.
     * Call this on service initialization to restore persisted cache.
     */
    fun loadCacheFromDisk() {
        scope.launch {
            try {
                // Small delay to ensure object is fully constructed before accessing lazy properties
                // This prevents NullPointerException when accessing cacheDir from init block
                kotlinx.coroutines.delay(100)

                val files = cacheDir.listFiles { file -> file.extension == "pcm" } ?: return@launch
                var loadedCount = 0

                for (file in files) {
                    try {
                        // Filename format: {hash}_{sampleRate}.pcm
                        val parts = file.nameWithoutExtension.split("_")
                        if (parts.size != 2) continue

                        val hash = parts[0].toIntOrNull() ?: continue
                        val sampleRate = parts[1].toIntOrNull() ?: continue

                        // Skip if already in memory
                        if (audioCache.containsKey(hash)) continue

                        // Load PCM data
                        val bytes = file.readBytes()
                        val samples = FloatArray(bytes.size / 4)
                        java.nio.ByteBuffer.wrap(bytes)
                            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            .asFloatBuffer()
                            .get(samples)

                        audioCache[hash] = CachedAudio(samples, sampleRate)
                        loadedCount++
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load cache file: ${file.name}", e)
                        file.delete() // Clean up corrupted file
                    }
                }

                if (loadedCount > 0) {
                    Log.i(TAG, "📂 Loaded $loadedCount cached audio files from disk")
                    Log.i(TAG, getCacheInfo())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cache from disk", e)
            }
        }
    }

    /**
     * Save cached audio to disk for persistence.
     */
    private fun saveToDisk(textHash: Int, audio: CachedAudio) {
        scope.launch {
            try {
                // Filename format: {hash}_{sampleRate}.pcm
                val file = File(cacheDir, "${textHash}_${audio.sampleRate}.pcm")

                // Convert float array to bytes
                val buffer = java.nio.ByteBuffer.allocate(audio.samples.size * 4)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                buffer.asFloatBuffer().put(audio.samples)

                file.writeBytes(buffer.array())
                Log.d(TAG, "💾 Saved audio to disk: ${file.name} (${audio.samples.size} samples)")

                // Cleanup old files if we have too many
                pruneOldCacheFiles()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving audio to disk", e)
            }
        }
    }

    /**
     * Remove oldest cache files if we exceed the limit.
     */
    private fun pruneOldCacheFiles() {
        try {
            val files = cacheDir.listFiles { file -> file.extension == "pcm" }
                ?.sortedBy { it.lastModified() } ?: return

            if (files.size > MAX_CACHE_FILES) {
                val toDelete = files.take(files.size - MAX_CACHE_FILES)
                toDelete.forEach { file ->
                    val hash = file.nameWithoutExtension.split("_").firstOrNull()?.toIntOrNull()
                    if (hash != null) {
                        audioCache.remove(hash) // Also remove from memory
                    }
                    file.delete()
                    Log.d(TAG, "🗑️ Pruned old cache file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error pruning cache files", e)
        }
    }

    /**
     * Get list of cached hadith hashes (for debugging).
     */
    fun getCachedHashes(): Set<Int> = audioCache.keys.toSet()

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

            val inputStream = assetRepository.openAsset("models/tts/$assetPath")
                ?: context.assets.open("tts/$assetPath")
            inputStream.use { input ->
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
     * Checks CDN downloads first, then falls back to bundled assets.
     * Only creates the .extracted marker if actual files were extracted.
     */
    private fun extractAssetDir(assetDir: String): String? {
        val outputDir = File(modelDir, assetDir)
        val markerFile = File(outputDir, ".extracted")

        // Validate existing marker - if directory only has .extracted and no real files,
        // the marker is stale (created when no source files were available)
        if (markerFile.exists()) {
            val realFiles = outputDir.listFiles { f -> f.name != ".extracted" }
            if (realFiles != null && realFiles.isNotEmpty()) {
                return outputDir.absolutePath
            }
            // Stale marker - directory is empty, delete and re-extract
            Log.w(TAG, "Stale .extracted marker found in empty dir: $assetDir, re-extracting...")
            markerFile.delete()
        }

        return try {
            outputDir.mkdirs()
            var extractedCount = 0

            // Strategy 1: Check CDN download directory for files
            // CDN files are at: cdn_assets/models/tts/{assetDir}/
            val cdnDir = File(File(context.filesDir, "cdn_assets"), "models/tts/$assetDir")
            if (cdnDir.exists() && cdnDir.isDirectory) {
                Log.i(TAG, "Found CDN directory: ${cdnDir.absolutePath}")
                extractedCount += copyDirRecursive(cdnDir, outputDir)
            }

            // Strategy 2: Check bundled assets (may not exist if in .gitignore)
            if (extractedCount == 0) {
                val bundledFiles = try {
                    context.assets.list("tts/$assetDir")
                } catch (e: Exception) {
                    null
                }

                if (bundledFiles != null && bundledFiles.isNotEmpty()) {
                    Log.i(TAG, "Found ${bundledFiles.size} bundled asset files in tts/$assetDir")
                    for (file in bundledFiles) {
                        val assetPath = "$assetDir/$file"
                        val outputFile = File(outputDir, file)

                        val subFiles = context.assets.list("tts/$assetPath")
                        if (subFiles != null && subFiles.isNotEmpty()) {
                            extractAssetDirRecursive("tts/$assetPath", outputFile)
                            extractedCount++
                        } else {
                            val inputStream = assetRepository.openAsset("models/tts/$assetPath")
                                ?: context.assets.open("tts/$assetPath")
                            inputStream.use { input ->
                                FileOutputStream(outputFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            extractedCount++
                        }
                    }
                }
            }

            // Only create marker if files were actually extracted
            if (extractedCount > 0) {
                markerFile.createNewFile()
                Log.i(TAG, "Extracted directory: $assetDir ($extractedCount files) -> ${outputDir.absolutePath}")
                outputDir.absolutePath
            } else {
                Log.e(TAG, "No files found to extract for directory: $assetDir (CDN not downloaded yet?)")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract asset directory: $assetDir", e)
            null
        }
    }

    /**
     * Recursively copy a directory from source to destination.
     * Used for copying CDN-downloaded directory trees to the extraction cache.
     */
    private fun copyDirRecursive(srcDir: File, destDir: File): Int {
        destDir.mkdirs()
        var count = 0
        val files = srcDir.listFiles() ?: return 0
        for (file in files) {
            val destFile = File(destDir, file.name)
            if (file.isDirectory) {
                count += copyDirRecursive(file, destFile)
            } else {
                file.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                count++
            }
        }
        Log.d(TAG, "Copied $count files from ${srcDir.name} to ${destDir.absolutePath}")
        return count
    }

    private fun extractAssetDirRecursive(assetPath: String, outputDir: File) {
        outputDir.mkdirs()

        // Try CDN directory first
        val cdnDir = File(File(context.filesDir, "cdn_assets"), "models/$assetPath")
        if (cdnDir.exists() && cdnDir.isDirectory) {
            copyDirRecursive(cdnDir, outputDir)
            return
        }

        // Fall back to bundled assets
        val files = context.assets.list(assetPath) ?: return

        for (file in files) {
            val fullAssetPath = "$assetPath/$file"
            val outputFile = File(outputDir, file)

            val subFiles = context.assets.list(fullAssetPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                extractAssetDirRecursive(fullAssetPath, outputFile)
            } else {
                // Convert tts/... path to models/tts/... CDN key
                val cdnKey = "models/$fullAssetPath"
                val inputStream = assetRepository.openAsset(cdnKey)
                    ?: context.assets.open(fullAssetPath)
                inputStream.use { input ->
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
     * Thread-safe: waits for any ongoing generation to complete.
     */
    fun release() {
        // If generation is in progress, stop it first
        if (isGenerating) {
            Log.w(TAG, "Releasing TTS while generation in progress - stopping first")
            stopSpeaking()
            // Give a small delay for the generation to stop
            Thread.sleep(100)
        }

        releaseInternal()
    }

    /**
     * Internal release without waiting - called from mutex-protected contexts.
     */
    private fun releaseInternal() {
        try {
            stopAudioTrack()
            tts?.free()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing TTS", e)
        }
        tts = null
        isInitialized = false
        isGenerating = false
        Log.d(TAG, "TTS resources released")
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
