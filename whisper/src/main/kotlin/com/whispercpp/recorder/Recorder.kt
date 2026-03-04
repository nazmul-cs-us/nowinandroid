package com.whispercpp.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.whispercpp.media.encodeWaveFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class Recorder {
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private var recorder: AudioRecordThread? = null

    suspend fun startRecording(outputFile: File, onError: (Exception) -> Unit) = withContext(scope.coroutineContext) {
        recorder = AudioRecordThread(outputFile, onError, null)
        recorder?.start()
    }

    /**
     * Start recording with amplitude callback for real-time visualization
     * @param outputFile File to save the recording
     * @param onError Error callback
     * @param onAmplitude Callback with normalized amplitude (0.0 to 1.0) called ~60 times per second
     */
    suspend fun startRecordingWithAmplitude(
        outputFile: File,
        onError: (Exception) -> Unit,
        onAmplitude: (Float) -> Unit
    ) = withContext(scope.coroutineContext) {
        recorder = AudioRecordThread(outputFile, onError, onAmplitude)
        recorder?.start()
    }

    suspend fun stopRecording() = withContext(scope.coroutineContext) {
        recorder?.stopRecording()
        @Suppress("BlockingMethodInNonBlockingContext")
        recorder?.join()
        recorder = null
    }
}

private class AudioRecordThread(
    private val outputFile: File,
    private val onError: (Exception) -> Unit,
    private val onAmplitude: ((Float) -> Unit)?
) :
    Thread("AudioRecorder") {
    private var quit = AtomicBoolean(false)

    companion object {
        private const val TAG = "AudioRecordThread"
        private const val SAMPLE_RATE = 16000

        // High-pass filter state (for road noise removal)
        private var previousSample = 0f
        private var filteredSample = 0f

        /**
         * Apply high-pass filter to remove low-frequency road/engine noise.
         * Cutoff ~200Hz to preserve speech (300Hz-3400Hz) while removing road rumble.
         */
        private fun applyHighPassFilter(sample: Short): Short {
            val alpha = 0.926f  // Cutoff ~200Hz at 16kHz sample rate
            val inputFloat = sample.toFloat() / Short.MAX_VALUE
            filteredSample = alpha * (filteredSample + inputFloat - previousSample)
            previousSample = inputFloat
            return (filteredSample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        private fun resetFilter() {
            previousSample = 0f
            filteredSample = 0f
        }
    }

    // Audio enhancement effects
    private var noiseSuppressor: NoiseSuppressor? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null

    @SuppressLint("MissingPermission")
    override fun run() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 4
            val buffer = ShortArray(bufferSize / 2)

            // Use VOICE_RECOGNITION source - optimized for speech with built-in processing
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,  // Better than MIC for noisy environments
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            try {
                // Attach audio enhancement effects for noisy driving environments
                val audioSessionId = audioRecord.audioSessionId
                attachAudioEffects(audioSessionId)

                audioRecord.startRecording()
                resetFilter()

                val allData = mutableListOf<Short>()

                while (!quit.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // Apply high-pass filter to remove road noise before storing
                        for (i in 0 until read) {
                            allData.add(applyHighPassFilter(buffer[i]))
                        }

                        // Calculate and emit amplitude if callback is provided
                        onAmplitude?.let { callback ->
                            val amplitude = calculateRmsAmplitude(buffer, read)
                            callback(amplitude)
                        }
                    } else {
                        throw java.lang.RuntimeException("audioRecord.read returned $read")
                    }
                }

                audioRecord.stop()
                encodeWaveFile(outputFile, allData.toShortArray())
            } finally {
                releaseAudioEffects()
                audioRecord.release()
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Attach audio enhancement effects for noisy environments.
     */
    private fun attachAudioEffects(audioSessionId: Int) {
        // Noise Suppressor
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "✅ NoiseSuppressor enabled")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to create NoiseSuppressor: ${e.message}")
            }
        }

        // Acoustic Echo Canceler
        if (AcousticEchoCanceler.isAvailable()) {
            try {
                acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "✅ AcousticEchoCanceler enabled")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to create AcousticEchoCanceler: ${e.message}")
            }
        }

        // Automatic Gain Control
        if (AutomaticGainControl.isAvailable()) {
            try {
                automaticGainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "✅ AutomaticGainControl enabled")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to create AutomaticGainControl: ${e.message}")
            }
        }
    }

    /**
     * Release audio enhancement effects.
     */
    private fun releaseAudioEffects() {
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        try { acousticEchoCanceler?.release() } catch (_: Exception) {}
        try { automaticGainControl?.release() } catch (_: Exception) {}
        noiseSuppressor = null
        acousticEchoCanceler = null
        automaticGainControl = null
    }

    /**
     * Calculate normalized RMS amplitude (0.0 to 1.0)
     */
    private fun calculateRmsAmplitude(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        val rms = sqrt(sum / length)
        // Normalize to 0-1 range (Short.MAX_VALUE = 32767)
        return (rms / 32767.0).toFloat().coerceIn(0f, 1f)
    }

    fun stopRecording() {
        quit.set(true)
    }
}