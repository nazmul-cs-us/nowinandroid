package com.starception.submission.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
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
    private var isLiveMode: Boolean = false

    // Live mode auto-stop timer
    private var liveAutoStopHandler: Handler? = null
    private var liveAutoStopRunnable: Runnable? = null

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

    /**
     * Start recording sensor data for a new session.
     */
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        sessionId = UUID.randomUUID().toString().take(8)
        totalSamplesWritten = 0
        postureCounts.clear()

        // Create output directory
        val dataDir = getDataDirectory()
        dataDir.mkdirs()

        // Create output file
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        outputFile = File(dataDir, "salah_data_${timestamp}_$sessionId.jsonl")
        writer = BufferedWriter(FileWriter(outputFile, true))

        // Initialize sensor timestamp reference for converting to wall clock
        sensorBootTimeNs = android.os.SystemClock.elapsedRealtimeNanos()
        wallClockAtBoot = System.currentTimeMillis()

        Log.i(TAG, "🕌 Starting salah data collection")
        Log.i(TAG, "   Session: $sessionId")
        Log.i(TAG, "   Output: ${outputFile?.absolutePath}")

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
     * Stop recording and finalize the output file.
     * @param trimLastMs If > 0, discard samples from the last N milliseconds
     *                   (to remove noise from pulling phone out of pocket).
     */
    fun stopRecording(trimLastMs: Long = 0) {
        if (!isRecording) return

        isRecording = false
        sensorManager.unregisterListener(this)

        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null

        // Flush remaining buffer
        flushWindow()

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
        if (!isRecording) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                synchronized(accelXBuffer) {
                    accelXBuffer.add(event.values[0])
                    accelYBuffer.add(event.values[1])
                    accelZBuffer.add(event.values[2])
                    accelTimestamps.add(event.timestamp) // nanoseconds since boot
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                synchronized(gyroXBuffer) {
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

        posture = currentPosture
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
                write(sample.toJson().toString())
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
        isLiveMode = true
        currentPosture = SalahPosture.QIYAM // Default posture for live mode

        // Create output directory
        val dataDir = getDataDirectory()
        dataDir.mkdirs()

        // Create output file with "live" prefix
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        outputFile = File(dataDir, "salah_live_${timestamp}_$sessionId.jsonl")
        writer = BufferedWriter(FileWriter(outputFile, true))

        // Initialize sensor timestamp reference
        sensorBootTimeNs = android.os.SystemClock.elapsedRealtimeNanos()
        wallClockAtBoot = System.currentTimeMillis()

        Log.i(TAG, "🕌 Starting LIVE salah prayer recording")
        Log.i(TAG, "   Session: $sessionId")
        Log.i(TAG, "   Output: ${outputFile?.absolutePath}")
        Log.i(TAG, "   Auto-stop: 30 minutes")

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
            stopLivePrayerRecording()
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
        isLiveMode = false
        sensorManager.unregisterListener(this)

        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null

        // Flush remaining buffer
        flushWindow()

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

    /**
     * Get current session stats.
     */
    fun getSessionStats(): Pair<Map<SalahPosture, Int>, Int> {
        return Pair(postureCounts.toMap(), totalSamplesWritten)
    }

    /**
     * Scan all JSONL files and return aggregate posture counts across all collected data.
     * Returns a map of posture name to count, plus the total count.
     */
    fun getGlobalPostureCounts(): Pair<Map<String, Int>, Int> {
        val counts = mutableMapOf<String, Int>()
        var total = 0

        for (file in listDataFiles()) {
            try {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach
                        try {
                            val posture = JSONObject(line).optString("posture", "")
                            if (posture.isNotEmpty()) {
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
