package com.whispercpp.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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

    @SuppressLint("MissingPermission")
    override fun run() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 4
            val buffer = ShortArray(bufferSize / 2)

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            try {
                audioRecord.startRecording()

                val allData = mutableListOf<Short>()

                while (!quit.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        for (i in 0 until read) {
                            allData.add(buffer[i])
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
                audioRecord.release()
            }
        } catch (e: Exception) {
            onError(e)
        }
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