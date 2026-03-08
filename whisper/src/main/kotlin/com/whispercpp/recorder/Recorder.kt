package com.whispercpp.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import com.whispercpp.media.encodeWaveFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class Recorder(private val audioManager: AudioManager? = null) {
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private var recorder: AudioRecordThread? = null

    suspend fun startRecording(outputFile: File, onError: (Exception) -> Unit) = withContext(scope.coroutineContext) {
        val isBluetoothMode = audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION
        recorder = AudioRecordThread(outputFile, onError, null, audioManager, isBluetoothMode)
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
        val isBluetoothMode = audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION
        recorder = AudioRecordThread(outputFile, onError, onAmplitude, audioManager, isBluetoothMode)
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
    private val onAmplitude: ((Float) -> Unit)?,
    private val audioManager: AudioManager? = null,
    private val isBluetoothMode: Boolean = false
) :
    Thread("AudioRecorder") {
    private var quit = AtomicBoolean(false)

    companion object {
        private const val TAG = "AudioRecordThread"
        private const val SAMPLE_RATE = 16000
        private const val MAX_RECORDING_MS = 30_000L
        private const val MAX_SAMPLES = (SAMPLE_RATE * (MAX_RECORDING_MS / 1000L)).toInt()

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

            // Create AudioRecord with preferred device selection
            val audioRecord = createAudioRecordWithPreferredDevice(bufferSize)

            try {
                // Log which device is being used for recording
                logRecordingDevice(audioRecord)

                // Attach audio enhancement effects for noisy driving environments
                val audioSessionId = audioRecord.audioSessionId
                attachAudioEffects(audioSessionId)

                audioRecord.startRecording()
                resetFilter()

                var allData = ShortArray(SAMPLE_RATE * 10) // Start with 10s capacity.
                var dataSize = 0

                fun appendSample(sample: Short): Boolean {
                    if (dataSize >= MAX_SAMPLES) {
                        return false
                    }
                    if (dataSize >= allData.size) {
                        val nextSize = (allData.size * 2).coerceAtMost(MAX_SAMPLES)
                        allData = allData.copyOf(nextSize)
                    }
                    allData[dataSize++] = sample
                    return true
                }

                // Add 500ms of pre-roll silence at the start
                // This prevents Whisper from cutting off the first word ("yes")
                // which was being lost due to AudioRecord startup delay
                val preRollSamples = SAMPLE_RATE / 2  // 500ms at 16kHz = 8000 samples
                Log.i(TAG, "🎙️ Adding $preRollSamples samples of pre-roll silence")
                repeat(preRollSamples) {
                    if (!appendSample(0)) {
                        Log.w(TAG, "🎙️ Reached max recording size during pre-roll; stopping early")
                        quit.set(true)
                        return@repeat
                    }
                }

                var totalSamplesRead = 0
                var chunkCount = 0
                val recordingStartMs = System.currentTimeMillis()

                Log.i(TAG, "🎙️ RECORDING STARTED - entering main recording loop")

                while (!quit.get()) {
                    val elapsedMs = System.currentTimeMillis() - recordingStartMs
                    if (elapsedMs >= MAX_RECORDING_MS) {
                        Log.w(TAG, "🎙️ Max recording duration ${MAX_RECORDING_MS}ms reached, forcing stop")
                        break
                    }

                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        totalSamplesRead += read
                        chunkCount++

                        // DISABLED: High-pass filter was too aggressive, store raw audio
                        // for (i in 0 until read) {
                        //     allData.add(applyHighPassFilter(buffer[i]))
                        // }
                        // Store raw audio without filtering for voice capture testing
                        for (i in 0 until read) {
                            if (!appendSample(buffer[i])) {
                                Log.w(TAG, "🎙️ Max recording buffer reached at $dataSize samples, forcing stop")
                                quit.set(true)
                                break
                            }
                        }

                        // Calculate amplitude for logging and callback
                        val amplitude = calculateRmsAmplitude(buffer, read)
                        val rms = amplitude * 32767  // Convert back to raw RMS
                        val dbLevel = if (rms > 0) 20 * kotlin.math.log10(rms.toDouble()) else -100.0

                        // Log audio level - first 3 chunks, then every 4th chunk for diagnosis
                        if (chunkCount <= 3 || chunkCount % 4 == 0) {
                            Log.i(TAG, "🎙️ AUDIO LEVEL [chunk $chunkCount]: amplitude=${String.format("%.4f", amplitude)}, dB=${dbLevel.toInt()}, samples=$totalSamplesRead, read=$read")
                        }

                        // Emit amplitude to callback
                        onAmplitude?.invoke(amplitude)
                    } else {
                        throw java.lang.RuntimeException("audioRecord.read returned $read")
                    }
                }

                audioRecord.stop()
                val finalData = allData.copyOf(dataSize)
                Log.i(TAG, "🎙️ RECORDING STOPPED - total samples: ${finalData.size}, chunks: $chunkCount, duration: ${finalData.size / 16000.0}s")
                encodeWaveFile(outputFile, finalData)
                Log.i(TAG, "🎙️ WAV FILE SAVED: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
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
     * DISABLED FOR TESTING: NoiseSuppressor was aggressively suppressing user's voice
     */
    private fun attachAudioEffects(audioSessionId: Int) {
        // DISABLED: NoiseSuppressor was too aggressive, suppressing user's voice
        // if (NoiseSuppressor.isAvailable()) {
        //     try {
        //         noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
        //             enabled = true
        //             Log.i(TAG, "✅ NoiseSuppressor enabled")
        //         }
        //     } catch (e: Exception) {
        //         Log.w(TAG, "⚠️ Failed to create NoiseSuppressor: ${e.message}")
        //     }
        // }
        Log.i(TAG, "⚠️ NoiseSuppressor DISABLED for voice capture testing")

        // DISABLED: AcousticEchoCanceler may also be interfering
        // if (AcousticEchoCanceler.isAvailable()) {
        //     try {
        //         acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
        //             enabled = true
        //             Log.i(TAG, "✅ AcousticEchoCanceler enabled")
        //         }
        //     } catch (e: Exception) {
        //         Log.w(TAG, "⚠️ Failed to create AcousticEchoCanceler: ${e.message}")
        //     }
        // }
        Log.i(TAG, "⚠️ AcousticEchoCanceler DISABLED for voice capture testing")

        // DISABLED: AGC might be interfering with audio capture on some devices
        // Keeping all audio effects disabled until we confirm mic is working
        // if (AutomaticGainControl.isAvailable()) {
        //     try {
        //         automaticGainControl = AutomaticGainControl.create(audioSessionId)?.apply {
        //             enabled = true
        //             Log.i(TAG, "✅ AutomaticGainControl enabled")
        //         }
        //     } catch (e: Exception) {
        //         Log.w(TAG, "⚠️ Failed to create AutomaticGainControl: ${e.message}")
        //     }
        // }
        Log.i(TAG, "⚠️ AutomaticGainControl DISABLED for voice capture testing")
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

    /**
     * Create AudioRecord with preferred device selection.
     * On Android 6.0+ (API 23+), explicitly selects the phone's built-in microphone
     * to avoid using car Bluetooth microphone which is far away and noisy.
     */
    @SuppressLint("MissingPermission")
    private fun createAudioRecordWithPreferredDevice(bufferSize: Int): AudioRecord {
        // ALWAYS use MIC audio source for now
        // VOICE_RECOGNITION was returning silent audio (amplitude=0.0000) on some devices
        // MIC provides raw microphone input without system-level preprocessing
        Log.i(TAG, "🎤 Using MIC audio source (VOICE_RECOGNITION was returning silence)")
        val audioSource = MediaRecorder.AudioSource.MIC

        // On Android 6.0+ (API 23+), use AudioRecord.Builder for better control
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val builder = AudioRecord.Builder()
                .setAudioSource(audioSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)

            val record = builder.build()

            // Set preferred device to built-in microphone
            val builtInMic = findBuiltInMicrophone()
            if (builtInMic != null) {
                val success = record.setPreferredDevice(builtInMic)
                Log.i(TAG, "🎤 setPreferredDevice(${builtInMic.productName}): $success")
            }

            return record
        } else {
            // Fallback for older devices
            return AudioRecord(
                audioSource,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }
    }

    /**
     * Find the phone's built-in microphone device.
     */
    private fun findBuiltInMicrophone(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || audioManager == null) {
            return null
        }

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        Log.d(TAG, "🎤 Available input devices: ${devices.size}")

        for (device in devices) {
            val typeName = getDeviceTypeName(device.type)
            Log.d(TAG, "  - ${device.productName} (type=$typeName, id=${device.id})")

            // Prefer TYPE_BUILTIN_MIC (phone's main microphone)
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                Log.i(TAG, "🎤 Found built-in microphone: ${device.productName}")
                return device
            }
        }

        // If no built-in mic found, return first non-Bluetooth device
        for (device in devices) {
            if (device.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO &&
                device.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                Log.i(TAG, "🎤 Using fallback device: ${device.productName}")
                return device
            }
        }

        Log.w(TAG, "🎤 No suitable microphone found!")
        return null
    }

    /**
     * Log which audio device is actually being used for recording.
     */
    private fun logRecordingDevice(audioRecord: AudioRecord) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val routedDevice = audioRecord.routedDevice
            if (routedDevice != null) {
                Log.i(TAG, "🎤 RECORDING DEVICE: ${routedDevice.productName} (type=${getDeviceTypeName(routedDevice.type)})")
            } else {
                Log.w(TAG, "🎤 RECORDING DEVICE: Unknown (routedDevice is null)")
            }
        }
    }

    /**
     * Get human-readable name for audio device type.
     */
    private fun getDeviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        else -> "UNKNOWN($type)"
    }

    fun stopRecording() {
        quit.set(true)
    }
}
