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
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // Sensor handler thread for background operation
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    // Current posture label (set by UI)
    @Volatile
    var currentPosture: SalahPosture = SalahPosture.NOT_PRAYING

    // Session tracking
    var sessionId: String = ""
        private set
    private var isRecording = false

    // Accumulation buffers for current window
    private val accelXBuffer = mutableListOf<Float>()
    private val accelYBuffer = mutableListOf<Float>()
    private val accelZBuffer = mutableListOf<Float>()
    private val gyroXBuffer = mutableListOf<Float>()
    private val gyroYBuffer = mutableListOf<Float>()
    private val gyroZBuffer = mutableListOf<Float>()

    // Output file writer
    private var writer: BufferedWriter? = null
    private var outputFile: File? = null

    // Stats
    private var totalSamplesWritten = 0
    private val postureCounts = mutableMapOf<SalahPosture, Int>()

    // Callbacks
    var onSampleRecorded: ((SalahDataSample) -> Unit)? = null
    var onStatsUpdated: ((Map<SalahPosture, Int>, Int) -> Unit)? = null

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
        if (!isRecording || currentPosture == SalahPosture.NOT_PRAYING) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                synchronized(accelXBuffer) {
                    accelXBuffer.add(event.values[0])
                    accelYBuffer.add(event.values[1])
                    accelZBuffer.add(event.values[2])
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                synchronized(gyroXBuffer) {
                    gyroXBuffer.add(event.values[0])
                    gyroYBuffer.add(event.values[1])
                    gyroZBuffer.add(event.values[2])
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
     */
    private fun flushWindow() {
        val ax: FloatArray
        val ay: FloatArray
        val az: FloatArray
        val gx: FloatArray
        val gy: FloatArray
        val gz: FloatArray
        val posture: SalahPosture

        synchronized(accelXBuffer) {
            if (accelXBuffer.size < WINDOW_SIZE) return
            ax = accelXBuffer.take(WINDOW_SIZE).toFloatArray()
            ay = accelYBuffer.take(WINDOW_SIZE).toFloatArray()
            az = accelZBuffer.take(WINDOW_SIZE).toFloatArray()
            // Remove consumed samples (keep any overflow for next window)
            repeat(WINDOW_SIZE) {
                accelXBuffer.removeFirstOrNull()
                accelYBuffer.removeFirstOrNull()
                accelZBuffer.removeFirstOrNull()
            }
        }

        synchronized(gyroXBuffer) {
            if (gyroXBuffer.size < WINDOW_SIZE) return
            gx = gyroXBuffer.take(WINDOW_SIZE).toFloatArray()
            gy = gyroYBuffer.take(WINDOW_SIZE).toFloatArray()
            gz = gyroZBuffer.take(WINDOW_SIZE).toFloatArray()
            repeat(WINDOW_SIZE) {
                gyroXBuffer.removeFirstOrNull()
                gyroYBuffer.removeFirstOrNull()
                gyroZBuffer.removeFirstOrNull()
            }
        }

        posture = currentPosture
        if (posture == SalahPosture.NOT_PRAYING) return

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
            timestamp = System.currentTimeMillis(),
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
