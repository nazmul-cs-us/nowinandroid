package com.starception.submission.voice.handsfree

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.starception.submission.download.AssetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** Streaming wake-word detection; waiting audio is never saved or sent over the network. */
internal class DeenlyWakeWordDetector(private val context: Context, private val assets: AssetRepository) {
    private var spotter: KeywordSpotter? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var stopped = false

    suspend fun prepare() = withContext(Dispatchers.IO) {
        fun model(name: String): File = requireNotNull(assets.getDatabaseFile("models/kws/$name")) {
            "Download the Keyword Detection Model in Voice Recognition settings."
        }
        val encoder = model("encoder.int8.onnx")
        val decoder = model("decoder.int8.onnx")
        val joiner = model("joiner.int8.onnx")
        val tokens = model("tokens.txt")
        val keywords = File(context.noBackupFilesDir, "voice-wake/hey-deenly.txt")
        keywords.parentFile?.mkdirs()
        keywords.writeText(DeenlyWakePhrase.keywords(tokens.readText()))
        currentCoroutineContext().ensureActive()
        spotter = KeywordSpotter(assetManager = null, config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(transducer = OnlineTransducerModelConfig(
                encoder = encoder.path, decoder = decoder.path, joiner = joiner.path),
                tokens = tokens.path, numThreads = 1, provider = "cpu", debug = false),
            keywordsFile = keywords.path, keywordsScore = 1.5f, keywordsThreshold = .35f,
            numTrailingBlanks = 2, maxActivePaths = 4))
    }

    /** False means another microphone user took priority; try again once it is idle. */
    @Suppress("MissingPermission")
    suspend fun awaitWakeWord(shouldYield: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        val engine = checkNotNull(spotter)
        val audio = context.getSystemService(AudioManager::class.java)
        if (shouldYield() || audio.activeRecordingConfigurations.isNotEmpty()) return@withContext false
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "The microphone does not support offline wake-word recognition." }
        val record = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(16000)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum * 2, 6400)).build()
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); error("Could not open the microphone.") }
        var stream = try { engine.createStream() } catch (error: Exception) { record.release(); throw error }
        var streamStarted = SystemClock.elapsedRealtime()
        stopped = false
        recorder = record
        try {
            currentCoroutineContext().ensureActive()
            if (shouldYield()) return@withContext false
            record.startRecording()
            val input = ShortArray(1600)
            while (!stopped && !shouldYield()) {
                currentCoroutineContext().ensureActive()
                val count = record.read(input, 0, input.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) {
                    if (stopped) break
                    error("Microphone capture stopped. Re-enable hands-free mode to try again.")
                }
                if (audio.activeRecordingConfigurations.any {
                        it.clientAudioSessionId != record.audioSessionId ||
                            (Build.VERSION.SDK_INT >= 29 && it.isClientSilenced)
                    }) break
                stream.acceptWaveform(FloatArray(count) { input[it] / 32768f }, 16000)
                while (engine.isReady(stream)) {
                    engine.decode(stream)
                    if (engine.getResult(stream).keyword == "hey_deenly") return@withContext true
                }
                // Bound native feature/history buffers during long sessions without reopening the mic.
                if (SystemClock.elapsedRealtime() - streamStarted >= 120_000) {
                    val fresh = engine.createStream()
                    stream.release()
                    stream = fresh
                    streamStarted = SystemClock.elapsedRealtime()
                }
            }
            false
        } finally {
            recorder = null
            runCatching { record.stop() }
            record.release()
            stream.release()
        }
    }

    fun stop() { stopped = true; runCatching { recorder?.stop() } }
    /** Call only after the recording coroutine has finished. */
    fun release() { spotter?.release(); spotter = null }
}
