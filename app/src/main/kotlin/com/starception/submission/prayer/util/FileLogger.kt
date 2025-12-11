/*
 * Copyright 2024 The Android Open Source Project
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

package com.starception.submission.prayer.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * File-based logging system for debugging prayer/adhan notification issues.
 *
 * Features:
 * - Writes logs to device internal storage
 * - Organizes logs by day in separate directories (e.g., "2024-12-11_Wednesday")
 * - Auto-cleans logs older than 3 days
 * - Thread-safe logging with background executor
 *
 * Log directory structure:
 * /data/data/com.starception.submission.demo.debug/files/prayer_logs/
 *   ├── 2024-12-09_Monday/
 *   │   └── prayer_log.txt
 *   ├── 2024-12-10_Tuesday/
 *   │   └── prayer_log.txt
 *   └── 2024-12-11_Wednesday/
 *       └── prayer_log.txt
 *
 * Usage:
 *   FileLogger.init(context)
 *   FileLogger.log("TAG", "Message here")
 *   FileLogger.logError("TAG", "Error message", exception)
 */
object FileLogger {

    private const val TAG = "FileLogger"
    private const val LOG_ROOT_DIR = "prayer_logs"
    private const val LOG_FILE_NAME = "prayer_log.txt"
    private const val MAX_LOG_DAYS = 3

    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_EEEE", Locale.US)
    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fullTimestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var applicationContext: Context? = null
    private var isInitialized = false

    /**
     * Initialize the FileLogger with application context.
     * Call this once in Application.onCreate() or before first use.
     */
    fun init(context: Context) {
        applicationContext = context.applicationContext
        isInitialized = true

        // Clean up old logs on initialization
        executor.execute {
            try {
                cleanupOldLogs()
                logInternal("INFO", TAG, "FileLogger initialized. Log directory: ${getLogRootDir()?.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize FileLogger", e)
            }
        }
    }

    /**
     * Log a debug message.
     */
    fun d(tag: String, message: String) {
        log("DEBUG", tag, message)
        Log.d(tag, message)
    }

    /**
     * Log an info message.
     */
    fun i(tag: String, message: String) {
        log("INFO", tag, message)
        Log.i(tag, message)
    }

    /**
     * Log a warning message.
     */
    fun w(tag: String, message: String) {
        log("WARN", tag, message)
        Log.w(tag, message)
    }

    /**
     * Log an error message.
     */
    fun e(tag: String, message: String) {
        log("ERROR", tag, message)
        Log.e(tag, message)
    }

    /**
     * Log an error message with exception.
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        val stackTrace = Log.getStackTraceString(throwable)
        log("ERROR", tag, "$message\n$stackTrace")
        Log.e(tag, message, throwable)
    }

    /**
     * Log a message with custom level.
     */
    fun log(level: String, tag: String, message: String) {
        if (!isInitialized) {
            Log.w(TAG, "FileLogger not initialized. Call init(context) first. Message: [$tag] $message")
            return
        }

        executor.execute {
            try {
                logInternal(level, tag, message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log", e)
            }
        }
    }

    /**
     * Log a prayer-specific event with structured format.
     */
    fun logPrayerEvent(
        event: String,
        prayerName: String,
        scheduledTime: String? = null,
        actualTime: String? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        val sb = StringBuilder()
        sb.append("PRAYER_EVENT: $event")
        sb.append(" | Prayer: $prayerName")
        scheduledTime?.let { sb.append(" | Scheduled: $it") }
        actualTime?.let { sb.append(" | Actual: $it") }
        if (details.isNotEmpty()) {
            sb.append(" | Details: ${details.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
        }

        i("PrayerEvent", sb.toString())
    }

    /**
     * Log adhan notification scheduling.
     */
    fun logAdhanSchedule(
        prayerName: String,
        scheduledTimeMillis: Long,
        currentTimeMillis: Long = System.currentTimeMillis(),
        alarmType: String = "EXACT",
        details: Map<String, Any?> = emptyMap()
    ) {
        val scheduledTime = fullTimestampFormat.format(Date(scheduledTimeMillis))
        val currentTime = fullTimestampFormat.format(Date(currentTimeMillis))
        val delayMs = scheduledTimeMillis - currentTimeMillis
        val delayMinutes = delayMs / 60000.0

        val allDetails = details.toMutableMap().apply {
            put("delayMs", delayMs)
            put("delayMinutes", String.format("%.2f", delayMinutes))
            put("alarmType", alarmType)
            put("currentTime", currentTime)
        }

        logPrayerEvent(
            event = "ADHAN_SCHEDULED",
            prayerName = prayerName,
            scheduledTime = scheduledTime,
            details = allDetails
        )
    }

    /**
     * Log adhan notification firing.
     */
    fun logAdhanFired(
        prayerName: String,
        expectedTimeMillis: Long? = null,
        actualTimeMillis: Long = System.currentTimeMillis(),
        details: Map<String, Any?> = emptyMap()
    ) {
        val actualTime = fullTimestampFormat.format(Date(actualTimeMillis))
        val allDetails = details.toMutableMap()

        expectedTimeMillis?.let {
            val expectedTime = fullTimestampFormat.format(Date(it))
            val diffMs = actualTimeMillis - it
            val diffSeconds = diffMs / 1000.0
            allDetails["expectedTime"] = expectedTime
            allDetails["diffMs"] = diffMs
            allDetails["diffSeconds"] = String.format("%.2f", diffSeconds)
            allDetails["status"] = if (kotlin.math.abs(diffMs) < 60000) "ON_TIME" else if (diffMs > 0) "LATE" else "EARLY"
        }

        logPrayerEvent(
            event = "ADHAN_FIRED",
            prayerName = prayerName,
            actualTime = actualTime,
            details = allDetails
        )
    }

    /**
     * Get the root log directory.
     */
    fun getLogRootDir(): File? {
        return applicationContext?.let { ctx ->
            File(ctx.filesDir, LOG_ROOT_DIR).also { it.mkdirs() }
        }
    }

    /**
     * Get today's log directory.
     */
    fun getTodayLogDir(): File? {
        return getLogRootDir()?.let { root ->
            val todayDirName = dateFormat.format(Date())
            File(root, todayDirName).also { it.mkdirs() }
        }
    }

    /**
     * Get all log directories (for debugging).
     */
    fun getAllLogDirs(): List<File> {
        return getLogRootDir()?.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: emptyList()
    }

    /**
     * Get log file path for a specific day.
     */
    fun getLogFilePath(dayDir: File): File {
        return File(dayDir, LOG_FILE_NAME)
    }

    /**
     * Read logs from a specific day.
     */
    fun readLogs(dayDir: File): String? {
        val logFile = getLogFilePath(dayDir)
        return if (logFile.exists()) logFile.readText() else null
    }

    /**
     * Read today's logs.
     */
    fun readTodayLogs(): String? {
        return getTodayLogDir()?.let { readLogs(it) }
    }

    /**
     * Internal log writing method.
     */
    private fun logInternal(level: String, tag: String, message: String) {
        val logDir = getTodayLogDir() ?: run {
            Log.e(TAG, "Cannot get log directory")
            return
        }

        val logFile = getLogFilePath(logDir)
        val timestamp = timestampFormat.format(Date())
        val logLine = "[$timestamp] [$level] [$tag] $message\n"

        try {
            FileWriter(logFile, true).use { writer ->
                writer.write(logLine)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file: ${logFile.absolutePath}", e)
        }
    }

    /**
     * Clean up log directories older than MAX_LOG_DAYS.
     */
    private fun cleanupOldLogs() {
        val rootDir = getLogRootDir() ?: return
        val dirs = rootDir.listFiles()?.filter { it.isDirectory } ?: return

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -MAX_LOG_DAYS)
        val cutoffDate = calendar.time
        val cutoffDirName = dateFormat.format(cutoffDate)

        var deletedCount = 0
        for (dir in dirs) {
            // Compare directory names (format: yyyy-MM-dd_DayName)
            // If dir name is less than cutoff, it's older and should be deleted
            if (dir.name < cutoffDirName) {
                try {
                    dir.deleteRecursively()
                    deletedCount++
                    Log.i(TAG, "Deleted old log directory: ${dir.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete log directory: ${dir.name}", e)
                }
            }
        }

        if (deletedCount > 0) {
            Log.i(TAG, "Cleaned up $deletedCount old log directories (older than $cutoffDirName)")
        }
    }

    /**
     * Force cleanup of old logs (can be called manually).
     */
    fun forceCleanup() {
        executor.execute {
            cleanupOldLogs()
        }
    }

    /**
     * Get a summary of available logs.
     */
    fun getLogSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Prayer Logs Summary ===")
        sb.appendLine("Log Root: ${getLogRootDir()?.absolutePath}")
        sb.appendLine("Max Days Kept: $MAX_LOG_DAYS")
        sb.appendLine()

        getAllLogDirs().forEach { dir ->
            val logFile = getLogFilePath(dir)
            val size = if (logFile.exists()) "${logFile.length() / 1024} KB" else "No log file"
            val lineCount = if (logFile.exists()) logFile.readLines().size else 0
            sb.appendLine("${dir.name}: $size, $lineCount lines")
        }

        return sb.toString()
    }
}
