package com.starception.submission.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.util.Log
import com.starception.submission.ml.SalahDataSample
import com.starception.submission.ml.SalahPosture
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.atan2
import kotlin.math.sqrt
import org.json.JSONObject

/**
 * Service that records accelerometer + gyroscope sensor data for salah posture training.
 *
 * Collects raw sensor data at 50Hz and groups into 100ms windows (5 samples each).
 * Each window is labeled with the current posture and exported to JSONL files.
 *
 * Uses the same sensor types as ActivityDetectionService but is independent:
 * - TYPE_ACCELEROMETER for orientation/tilt detection
 * - TYPE_GYROSCOPE for rotational movement
 */
class SalahDataCollectionService(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "SalahDataCollection"
        private const val SENSOR_DELAY_US = 20_000 // 50Hz = 20ms between samples
        private const val WINDOW_SIZE = 5 // 5 samples per 100ms window at 50Hz
        private const val DATA_DIR_NAME = "salah_training_data"
        private const val MAX_PRAYER_DURATION_MS = 30 * 60 * 1000L // 30 minutes

        /** Filename prefix for live-prayer recordings pending review (see [getGlobalPostureCounts]). */
        const val LIVE_FILE_PREFIX = "salah_live_"
        /** Filename prefix for guided recordings with instruction-derived labels. */
        const val GUIDED_FILE_PREFIX = "salah_guided_"
        /** Filename prefix for live recordings after explicit human review. */
        const val REVIEWED_FILE_PREFIX = "salah_reviewed_"

        const val MANUAL_FILE_PREFIX = "salah_data_"
        private const val DATA_SCHEMA_VERSION = 2
    }

    enum class CollectionMode(val jsonValue: String, val filePrefix: String) {
        MANUAL("manual", MANUAL_FILE_PREFIX),
        GUIDED("guided", GUIDED_FILE_PREFIX),
        LIVE("live", LIVE_FILE_PREFIX),
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // Sensor handler thread for background operation
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    // Current posture label (set by UI)
    @Volatile
    var currentPosture: SalahPosture = SalahPosture.QIYAM

    // Session tracking
    var sessionId: String = ""
        private set
    private var isRecording = false
    @Volatile
    private var isSampleCaptureEnabled = false
    private var isLiveMode: Boolean = false
    private var collectionMode: CollectionMode = CollectionMode.MANUAL

    // Live mode auto-stop timer
    private var liveAutoStopHandler: Handler? = null
    private var liveAutoStopRunnable: Runnable? = null

    // Keeps the CPU (and thus non-wakeup sensor delivery) alive while the phone
    // is in the user's pocket with the screen off during a recording session.
    private var wakeLock: PowerManager.WakeLock? = null

    // Accumulation buffers for current window (with timestamps for synchronization)
    private val accelXBuffer = mutableListOf<Float>()
    private val accelYBuffer = mutableListOf<Float>()
    private val accelZBuffer = mutableListOf<Float>()
    private val accelTimestamps = mutableListOf<Long>() // nanoseconds from event.timestamp
    private val gyroXBuffer = mutableListOf<Float>()
    private val gyroYBuffer = mutableListOf<Float>()
    private val gyroZBuffer = mutableListOf<Float>()
    private val gyroTimestamps = mutableListOf<Long>() // nanoseconds from event.timestamp

    // Boot-time reference for converting sensor nanoseconds to wall clock
    private var sensorBootTimeNs = 0L
    private var wallClockAtBoot = 0L

    // Output file writer
    private var writer: BufferedWriter? = null
    private var outputFile: File? = null

    // Stats
    private var totalSamplesWritten = 0
    private val postureCounts = mutableMapOf<SalahPosture, Int>()

    // Callbacks
    var onSampleRecorded: ((SalahDataSample) -> Unit)? = null
    var onStatsUpdated: ((Map<SalahPosture, Int>, Int) -> Unit)? = null
    var onLivePostureDetected: ((SalahPosture, Float) -> Unit)? = null

    // Invoked on the main thread with the recorded file path when live mode
    // auto-stops at MAX_PRAYER_DURATION_MS, so the UI can leave the recording state.
    var onLiveAutoStopped: ((String?) -> Unit)? = null

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "starception:SalahDataCollection"
        ).apply {
            // Safety timeout slightly past the max session length
            acquire(MAX_PRAYER_DURATION_MS + 60_000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock: ${e.message}")
        }
        wakeLock = null
    }

    /**
     * Start recording sensor data for a new session.
     */
    fun startRecording(
        captureImmediately: Boolean = true,
        mode: CollectionMode = CollectionMode.MANUAL,
    ) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        sessionId = UUID.randomUUID().toString().take(8)
        totalSamplesWritten = 0
        postureCounts.clear()
        clearSensorBuffers()
        isSampleCaptureEnabled = captureImmediately
        isLiveMode = false
        collectionMode = mode

        // Create output directory
        val dataDir = getDataDirectory()
        dataDir.mkdirs()

        // Create output file
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        outputFile = File(dataDir, "${mode.filePrefix}${timestamp}_$sessionId.jsonl")
        writer = BufferedWriter(FileWriter(outputFile, true))

        // Initialize sensor timestamp reference for converting to wall clock
        sensorBootTimeNs = android.os.SystemClock.elapsedRealtimeNanos()
        wallClockAtBoot = System.currentTimeMillis()

        Log.i(TAG, "🕌 Starting salah data collection")
        Log.i(TAG, "   Session: $sessionId")
        Log.i(TAG, "   Output: ${outputFile?.absolutePath}")

        acquireWakeLock()

        // Start sensor thread
        sensorThread = HandlerThread("SalahSensorThread").apply { start() }
        sensorHandler = Handler(sensorThread!!.looper)

        // Register sensors
        accelerometer?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY_US, sensorHandler)
            Log.i(TAG, "   Accelerometer registered @ 50Hz")
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY_US, sensorHandler)
            Log.i(TAG, "   Gyroscope registered @ 50Hz")
        }

        isRecording = true
    }

    /**
     * Pause writing sensor windows without ending the current file/session.
     *
     * Guided recording uses this while instructions are spoken and while the user moves
     * through an unmodelled boundary. Clearing partial windows prevents samples on opposite
     * sides of a label change from being combined into one training window.
     */
    fun pauseSampleCapture() {
        isSampleCaptureEnabled = false
        clearSensorBuffers()
    }

    /** Resume the current session with a clean window boundary and an explicit label. */
    fun resumeSampleCapture(posture: SalahPosture) {
        isSampleCaptureEnabled = false
        clearSensorBuffers()
        currentPosture = posture
        isSampleCaptureEnabled = true
    }

    /**
     * Stop recording and finalize the output file.
     * @param trimLastMs If > 0, discard samples from the last N milliseconds
     *                   (to remove noise from pulling phone out of pocket).
     */
    fun stopRecording(trimLastMs: Long = 0) {
        if (!isRecording) return

        isRecording = false
        isSampleCaptureEnabled = false
        sensorManager.unregisterListener(this)
        releaseWakeLock()

        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null

        // Flush remaining buffer
        flushWindow()
        clearSensorBuffers()

        // Close writer
        try {
            writer?.flush()
            writer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing writer: ${e.message}")
        }
        writer = null

        // Trim the last N ms of data from the file
        if (trimLastMs > 0 && outputFile != null) {
            trimFileEnd(outputFile!!, trimLastMs)
        }

        Log.i(TAG, "🕌 Salah data collection stopped")
        Log.i(TAG, "   Total samples: $totalSamplesWritten")
        Log.i(TAG, "   File: ${outputFile?.absolutePath}")
        Log.i(TAG, "   File size: ${outputFile?.length()?.let { it / 1024 }} KB")
        postureCounts.forEach { (posture, count) ->
            Log.i(TAG, "   $posture: $count windows")
        }
    }

    /**
     * Remove all JSONL entries from the end of the file whose timestamp
     * falls within the last [trimMs] milliseconds.
     */
    private fun trimFileEnd(file: File, trimMs: Long) {
        if (!file.exists() || file.length() == 0L) return

        try {
            val lines = file.readLines()
            if (lines.isEmpty()) return

            // Find the cutoff timestamp
            val cutoffTime = System.currentTimeMillis() - trimMs

            // Keep only lines whose timestamp is before the cutoff
            val kept = mutableListOf<String>()
            var trimmed = 0
            for (line in lines) {
                if (line.isBlank()) continue
                try {
                    val json = JSONObject(line)
                    val ts = json.optLong("timestamp", 0)
                    if (ts < cutoffTime) {
                        kept.add(line)
                    } else {
                        trimmed++
                    }
                } catch (e: Exception) {
                    // Keep lines we can't parse
                    kept.add(line)
                }
            }

            if (trimmed > 0) {
                // Rewrite file without trimmed entries
                BufferedWriter(FileWriter(file, false)).use { w ->
                    kept.forEach { line ->
                        w.write(line)
                        w.newLine()
                    }
                }
                totalSamplesWritten -= trimmed
                Log.i(TAG, "   Trimmed $trimmed samples (last ${trimMs}ms)")

                // Recount postures from kept data
                postureCounts.clear()
                kept.forEach { line ->
                    try {
                        val postureName = JSONObject(line).optString("posture", "")
                        val posture = try { SalahPosture.valueOf(postureName) } catch (_: Exception) { null }
                        if (posture != null) {
                            postureCounts[posture] = (postureCounts[posture] ?: 0) + 1
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming file: ${e.message}")
        }
    }

    /**
     * Check if currently recording.
     */
    fun isRecording(): Boolean = isRecording

    override fun onSensorChanged(event: SensorEvent) {
        if (!isRecording || !isSampleCaptureEnabled) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                synchronized(accelXBuffer) {
                    if (!isRecording || !isSampleCaptureEnabled) return
                    accelXBuffer.add(event.values[0])
                    accelYBuffer.add(event.values[1])
                    accelZBuffer.add(event.values[2])
                    accelTimestamps.add(event.timestamp) // nanoseconds since boot
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                synchronized(gyroXBuffer) {
                    if (!isRecording || !isSampleCaptureEnabled) return
                    gyroXBuffer.add(event.values[0])
                    gyroYBuffer.add(event.values[1])
                    gyroZBuffer.add(event.values[2])
                    gyroTimestamps.add(event.timestamp) // nanoseconds since boot
                }
            }
        }

        // Check if we have enough data for a window
        val accelReady: Boolean
        val gyroReady: Boolean
        synchronized(accelXBuffer) { accelReady = accelXBuffer.size >= WINDOW_SIZE }
        synchronized(gyroXBuffer) { gyroReady = gyroXBuffer.size >= WINDOW_SIZE }

        if (accelReady && gyroReady) {
            flushWindow()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun clearSensorBuffers() {
        synchronized(accelXBuffer) {
            synchronized(gyroXBuffer) {
                accelXBuffer.clear()
                accelYBuffer.clear()
                accelZBuffer.clear()
                accelTimestamps.clear()
                gyroXBuffer.clear()
                gyroYBuffer.clear()
                gyroZBuffer.clear()
                gyroTimestamps.clear()
            }
        }
    }

    /**
     * Flush accumulated sensor data into a labeled sample and write to file.
     *
     * Uses timestamp-based pairing: takes the WINDOW_SIZE most recent accel and gyro
     * samples, ensuring they are temporally close (within 10ms of each other).
     * Uses sensor event timestamps (monotonic nanoseconds) converted to wall clock
     * for accurate timing in JSONL output.
     */
    private fun flushWindow() {
        val ax: FloatArray
        val ay: FloatArray
        val az: FloatArray
        val gx: FloatArray
        val gy: FloatArray
        val gz: FloatArray
        val windowTimestampNs: Long
        val posture: SalahPosture

        // Lock both buffers together to get synchronized snapshot
        synchronized(accelXBuffer) {
            synchronized(gyroXBuffer) {
                if (accelXBuffer.size < WINDOW_SIZE || gyroXBuffer.size < WINDOW_SIZE) return

                // Check temporal alignment: last accel and last gyro timestamps should be
                // within 10ms of each other. If not, discard older samples until they align.
                val accelLastTs = accelTimestamps[accelTimestamps.size - 1]
                val gyroLastTs = gyroTimestamps[gyroTimestamps.size - 1]
                val MAX_DRIFT_NS = 10_000_000L // 10ms in nanoseconds

                if (kotlin.math.abs(accelLastTs - gyroLastTs) > MAX_DRIFT_NS) {
                    // Sensors drifted - discard the older buffer's excess samples
                    if (accelLastTs < gyroLastTs) {
                        // Accel is behind - discard oldest accel to catch up
                        val discard = (accelXBuffer.size - WINDOW_SIZE).coerceAtMost(accelXBuffer.size - 1).coerceAtLeast(0)
                        if (discard > 0) {
                            repeat(discard) {
                                accelXBuffer.removeFirstOrNull()
                                accelYBuffer.removeFirstOrNull()
                                accelZBuffer.removeFirstOrNull()
                                accelTimestamps.removeFirstOrNull()
                            }
                        }
                    } else {
                        // Gyro is behind - discard oldest gyro to catch up
                        val discard = (gyroXBuffer.size - WINDOW_SIZE).coerceAtMost(gyroXBuffer.size - 1).coerceAtLeast(0)
                        if (discard > 0) {
                            repeat(discard) {
                                gyroXBuffer.removeFirstOrNull()
                                gyroYBuffer.removeFirstOrNull()
                                gyroZBuffer.removeFirstOrNull()
                                gyroTimestamps.removeFirstOrNull()
                            }
                        }
                    }
                    // Re-check if we still have enough
                    if (accelXBuffer.size < WINDOW_SIZE || gyroXBuffer.size < WINDOW_SIZE) return
                }

                ax = accelXBuffer.take(WINDOW_SIZE).toFloatArray()
                ay = accelYBuffer.take(WINDOW_SIZE).toFloatArray()
                az = accelZBuffer.take(WINDOW_SIZE).toFloatArray()
                gx = gyroXBuffer.take(WINDOW_SIZE).toFloatArray()
                gy = gyroYBuffer.take(WINDOW_SIZE).toFloatArray()
                gz = gyroZBuffer.take(WINDOW_SIZE).toFloatArray()

                // Use the median timestamp of the window for accurate timing
                val accelWindowTs = accelTimestamps.take(WINDOW_SIZE)
                val gyroWindowTs = gyroTimestamps.take(WINDOW_SIZE)
                windowTimestampNs = (accelWindowTs[WINDOW_SIZE / 2] + gyroWindowTs[WINDOW_SIZE / 2]) / 2
                // Capture the label while holding the same locks used by pause/resume.
                // This prevents an already-extracted old window from receiving the next
                // guided segment's label during a boundary race.
                posture = currentPosture

                // Remove consumed samples (keep overflow for next window)
                repeat(WINDOW_SIZE) {
                    accelXBuffer.removeFirstOrNull()
                    accelYBuffer.removeFirstOrNull()
                    accelZBuffer.removeFirstOrNull()
                    accelTimestamps.removeFirstOrNull()
                    gyroXBuffer.removeFirstOrNull()
                    gyroYBuffer.removeFirstOrNull()
                    gyroZBuffer.removeFirstOrNull()
                    gyroTimestamps.removeFirstOrNull()
                }
            }
        }

        // Record sample with current posture label

        // Convert sensor timestamp (nanoseconds since boot) to wall clock milliseconds
        val windowTimestampMs = wallClockAtBoot + (windowTimestampNs - sensorBootTimeNs) / 1_000_000

        // Compute summary features
        val avgAccelY = ay.average().toFloat()
        val avgAccelZ = az.average().toFloat()
        val avgAccelX = ax.average().toFloat()
        val pitch = Math.toDegrees(atan2(avgAccelY.toDouble(), avgAccelZ.toDouble())).toFloat()
        val roll = Math.toDegrees(atan2(avgAccelX.toDouble(), avgAccelZ.toDouble())).toFloat()
        val accelMag = sqrt(
            avgAccelX * avgAccelX + avgAccelY * avgAccelY + avgAccelZ * avgAccelZ
        )
        val avgGyroX = gx.average().toFloat()
        val avgGyroY = gy.average().toFloat()
        val avgGyroZ = gz.average().toFloat()
        val gyroMag = sqrt(
            avgGyroX * avgGyroX + avgGyroY * avgGyroY + avgGyroZ * avgGyroZ
        )

        val sample = SalahDataSample(
            timestamp = windowTimestampMs,
            sessionId = sessionId,
            posture = posture,
            accelX = ax,
            accelY = ay,
            accelZ = az,
            gyroX = gx,
            gyroY = gy,
            gyroZ = gz,
            pitch = pitch,
            roll = roll,
            accelMagnitude = accelMag,
            gyroMagnitude = gyroMag
        )

        // Write to JSONL file
        try {
            writer?.apply {
                val json = sample.toJson().apply {
                    put("schema_version", DATA_SCHEMA_VERSION)
                    put("collection_mode", collectionMode.jsonValue)
                    put(
                        "label_source",
                        when (collectionMode) {
                            CollectionMode.MANUAL -> "manual_user"
                            CollectionMode.GUIDED -> "guided_instruction"
                            CollectionMode.LIVE -> "model_prediction"
                        },
                    )
                    if (collectionMode == CollectionMode.LIVE) {
                        put("human_reviewed", false)
                    }
                }
                write(json.toString())
                newLine()
            }
            totalSamplesWritten++
            postureCounts[posture] = (postureCounts[posture] ?: 0) + 1

            // Notify callbacks
            onSampleRecorded?.invoke(sample)
            if (totalSamplesWritten % 10 == 0) {
                onStatsUpdated?.invoke(postureCounts.toMap(), totalSamplesWritten)
                // Flush periodically
                writer?.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing sample: ${e.message}")
        }
    }

    /**
     * Start live prayer recording mode.
     * Records continuously without requiring a posture label.
     * Sets currentPosture to QIYAM as default (will be relabeled later).
     * Creates file named salah_live_{timestamp}_{sessionId}.jsonl
     * Auto-stops after 30 minutes.
     */
    fun startLivePrayerRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        sessionId = UUID.randomUUID().toString().take(8)
        totalSamplesWritten = 0
        postureCounts.clear()
        clearSensorBuffers()
        isSampleCaptureEnabled = true
        isLiveMode = true
        collectionMode = CollectionMode.LIVE
        currentPosture = SalahPosture.QIYAM // Default posture for live mode

        // Create output directory
        val dataDir = getDataDirectory()
        dataDir.mkdirs()

        // Create output file with "live" prefix
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        outputFile = File(dataDir, "$LIVE_FILE_PREFIX${timestamp}_$sessionId.jsonl")
        writer = BufferedWriter(FileWriter(outputFile, true))

        // Initialize sensor timestamp reference
        sensorBootTimeNs = android.os.SystemClock.elapsedRealtimeNanos()
        wallClockAtBoot = System.currentTimeMillis()

        Log.i(TAG, "🕌 Starting LIVE salah prayer recording")
        Log.i(TAG, "   Session: $sessionId")
        Log.i(TAG, "   Output: ${outputFile?.absolutePath}")
        Log.i(TAG, "   Auto-stop: 30 minutes")

        acquireWakeLock()

        // Start sensor thread
        sensorThread = HandlerThread("SalahSensorThread").apply { start() }
        sensorHandler = Handler(sensorThread!!.looper)

        // Register sensors
        accelerometer?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY_US, sensorHandler)
            Log.i(TAG, "   Accelerometer registered @ 50Hz")
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY_US, sensorHandler)
            Log.i(TAG, "   Gyroscope registered @ 50Hz")
        }

        isRecording = true

        // Start 30-minute auto-stop timer
        liveAutoStopHandler = Handler(android.os.Looper.getMainLooper())
        liveAutoStopRunnable = Runnable {
            Log.i(TAG, "Live recording auto-stop (30 minutes reached)")
            val filePath = stopLivePrayerRecording()
            onLiveAutoStopped?.invoke(filePath)
        }
        liveAutoStopHandler?.postDelayed(liveAutoStopRunnable!!, MAX_PRAYER_DURATION_MS)
    }

    /**
     * Stop live prayer recording with auto-trim of last 3 seconds.
     * Returns the output file path.
     */
    fun stopLivePrayerRecording(): String? {
        if (!isRecording || !isLiveMode) {
            Log.w(TAG, "Not in live recording mode")
            return null
        }

        // Cancel auto-stop timer
        liveAutoStopRunnable?.let { liveAutoStopHandler?.removeCallbacks(it) }
        liveAutoStopHandler = null
        liveAutoStopRunnable = null

        isRecording = false
        isSampleCaptureEnabled = false
        isLiveMode = false
        sensorManager.unregisterListener(this)
        releaseWakeLock()

        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null

        // Flush remaining buffer
        flushWindow()
        clearSensorBuffers()

        // Close writer
        try {
            writer?.flush()
            writer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing writer: ${e.message}")
        }
        writer = null

        // Auto-trim last 3 seconds (to remove noise from pulling phone out)
        val trimMs = 3000L
        if (outputFile != null) {
            trimFileEnd(outputFile!!, trimMs)
        }

        Log.i(TAG, "🕌 Live salah recording stopped")
        Log.i(TAG, "   Total samples: $totalSamplesWritten")
        Log.i(TAG, "   File: ${outputFile?.absolutePath}")
        Log.i(TAG, "   File size: ${outputFile?.length()?.let { it / 1024 }} KB")

        val filePath = outputFile?.absolutePath
        currentPosture = SalahPosture.QIYAM // Reset posture
        return filePath
    }

    /**
     * Get the data directory for training data files.
     */
    fun getDataDirectory(): File {
        return File(context.getExternalFilesDir(null), DATA_DIR_NAME)
    }

    /**
     * List all recorded training data files.
     */
    fun listDataFiles(): List<File> {
        val dir = getDataDirectory()
        return dir.listFiles()
            ?.filter { it.extension == "jsonl" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Get total file size of all training data.
     */
    fun getTotalDataSizeKb(): Long {
        return listDataFiles().sumOf { it.length() } / 1024
    }

    /**
     * Delete all training data files.
     */
    fun deleteAllData() {
        listDataFiles().forEach { it.delete() }
        Log.i(TAG, "All training data deleted")
    }

    /**
     * Delete a specific training data file by name.
     * @return true if the file was deleted
     */
    fun deleteFile(fileName: String): Boolean {
        val file = File(getDataDirectory(), fileName)
        val deleted = file.delete()
        if (deleted) {
            Log.i(TAG, "Deleted file: $fileName")
        } else {
            Log.w(TAG, "Failed to delete file: $fileName")
        }
        return deleted
    }

    /**
     * Get posture counts for a specific data file.
     * Returns a map of posture name to sample count.
     */
    fun getFilePostureCounts(fileName: String): Map<String, Int> {
        val file = File(getDataDirectory(), fileName)
        if (!file.exists()) return emptyMap()
        val counts = mutableMapOf<String, Int>()
        try {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    try {
                        val posture = JSONObject(line).optString("posture", "")
                        if (posture.isNotEmpty()) {
                            counts[posture] = (counts[posture] ?: 0) + 1
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        return counts
    }

    /** Return why a file is excluded from training, or null when provenance is valid. */
    fun getFileTrainingIssue(fileName: String): String? {
        val file = File(getDataDirectory(), fileName)
        if (!file.exists() || file.length() == 0L) return "Empty · excluded"
        if (file.name.startsWith(LIVE_FILE_PREFIX)) return "Live · needs review"

        try {
            file.bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.forEach { line ->
                    val json = JSONObject(line)
                    when (json.optString("collection_mode", "")) {
                        CollectionMode.MANUAL.jsonValue,
                        CollectionMode.GUIDED.jsonValue -> Unit
                        CollectionMode.LIVE.jsonValue -> if (!json.optBoolean("human_reviewed", false)) {
                            return "Live · needs review"
                        }
                        else -> return "Legacy labels · confirm review"
                    }
                }
            }
        } catch (_: Exception) {
            return "Invalid data · excluded"
        }
        return null
    }

    /**
     * Get current session stats.
     */
    fun getSessionStats(): Pair<Map<SalahPosture, Int>, Int> {
        return Pair(postureCounts.toMap(), totalSamplesWritten)
    }

    /**
     * Scan all JSONL files and return aggregate posture counts across all collected data.
     * Returns a map of posture name to count, plus the total count.
     *
     * Counts only rows with explicit provenance. Unreviewed live-prayer recordings and
     * legacy rows without `collection_mode` are excluded so the progress UI mirrors the
     * fail-closed Python training loader.
     */
    fun getGlobalPostureCounts(): Pair<Map<String, Int>, Int> {
        val counts = mutableMapOf<String, Int>()
        var total = 0

        for (file in listDataFiles().filter { !it.name.startsWith(LIVE_FILE_PREFIX) }) {
            try {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach
                        try {
                            val json = JSONObject(line)
                            val posture = json.optString("posture", "")
                            val mode = json.optString("collection_mode", "")
                            val isEligible = mode == CollectionMode.MANUAL.jsonValue ||
                                mode == CollectionMode.GUIDED.jsonValue ||
                                (mode == CollectionMode.LIVE.jsonValue &&
                                    json.optBoolean("human_reviewed", false))
                            if (isEligible && posture.isNotEmpty()) {
                                counts[posture] = (counts[posture] ?: 0) + 1
                                total++
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        return Pair(counts, total)
    }
}
