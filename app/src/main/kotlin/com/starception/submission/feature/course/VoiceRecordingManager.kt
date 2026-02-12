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

package com.starception.submission.feature.course

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Manager class for handling voice recording functionality in course lessons.
 * Provides methods to record, stop, cancel, and playback audio recordings.
 */
class VoiceRecordingManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingPath: String? = null
    private var isRecording = false
    private var isPlaying = false

    companion object {
        private const val TAG = "VoiceRecordingManager"
        private const val RECORDINGS_DIR = "recordings"
    }

    /**
     * Get the directory for storing recordings
     */
    private fun getRecordingsDir(): File {
        val dir = File(context.filesDir, RECORDINGS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Get the file path for a specific lesson recording
     */
    fun getRecordingPath(courseId: String, lessonId: String): String {
        val courseDir = File(getRecordingsDir(), courseId)
        if (!courseDir.exists()) {
            courseDir.mkdirs()
        }
        return File(courseDir, "${lessonId}.m4a").absolutePath
    }

    /**
     * Check if a recording exists for a lesson
     */
    fun hasRecording(courseId: String, lessonId: String): Boolean {
        val path = getRecordingPath(courseId, lessonId)
        return File(path).exists()
    }

    /**
     * Get the duration of an existing recording in milliseconds
     */
    fun getRecordingDuration(courseId: String, lessonId: String): Long {
        val path = getRecordingPath(courseId, lessonId)
        val file = File(path)
        if (!file.exists()) return 0

        return try {
            val player = MediaPlayer()
            player.setDataSource(path)
            player.prepare()
            val duration = player.duration.toLong()
            player.release()
            duration
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recording duration", e)
            0
        }
    }

    /**
     * Start recording audio for a lesson
     * @return true if recording started successfully
     */
    fun startRecording(courseId: String, lessonId: String): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }

        val outputPath = getRecordingPath(courseId, lessonId)
        currentRecordingPath = outputPath

        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputPath)
                prepare()
                start()
            }

            isRecording = true
            Log.d(TAG, "Recording started: $outputPath")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording", e)
            releaseRecorder()
            return false
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for recording", e)
            releaseRecorder()
            return false
        }
    }

    /**
     * Stop recording and save the audio file
     * @return the path to the saved recording, or null if failed
     */
    fun stopRecording(): String? {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording")
            return null
        }

        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val path = currentRecordingPath
            currentRecordingPath = null
            Log.d(TAG, "Recording stopped and saved: $path")
            path
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            releaseRecorder()
            null
        }
    }

    /**
     * Cancel the current recording and delete any partial file
     */
    fun cancelRecording() {
        if (!isRecording) return

        releaseRecorder()

        // Delete partial recording
        currentRecordingPath?.let { path ->
            File(path).delete()
        }
        currentRecordingPath = null
        Log.d(TAG, "Recording cancelled")
    }

    /**
     * Start playing a recorded lesson
     * @param onCompletion callback when playback completes
     * @return true if playback started successfully
     */
    fun startPlayback(
        courseId: String,
        lessonId: String,
        onCompletion: () -> Unit = {}
    ): Boolean {
        if (isPlaying) {
            stopPlayback()
        }

        val path = getRecordingPath(courseId, lessonId)
        if (!File(path).exists()) {
            Log.w(TAG, "No recording found at: $path")
            return false
        }

        return try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    this@VoiceRecordingManager.isPlaying = false
                    onCompletion()
                }
                start()
            }
            this.isPlaying = true
            Log.d(TAG, "Playback started: $path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback", e)
            releasePlayer()
            false
        }
    }

    /**
     * Stop the current playback
     */
    fun stopPlayback() {
        if (!isPlaying) return

        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
        releasePlayer()
        Log.d(TAG, "Playback stopped")
    }

    /**
     * Delete a recording
     */
    fun deleteRecording(courseId: String, lessonId: String): Boolean {
        val path = getRecordingPath(courseId, lessonId)
        val file = File(path)
        return if (file.exists()) {
            file.delete().also {
                Log.d(TAG, "Recording deleted: $path")
            }
        } else {
            false
        }
    }

    /**
     * Get the current playback position in milliseconds
     */
    fun getPlaybackPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Check if currently recording
     */
    fun isCurrentlyRecording(): Boolean = isRecording

    /**
     * Check if currently playing
     */
    fun isCurrentlyPlaying(): Boolean = isPlaying

    /**
     * Release all resources
     */
    fun release() {
        releaseRecorder()
        releasePlayer()
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing recorder", e)
        }
        mediaRecorder = null
        isRecording = false
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing player", e)
        }
        mediaPlayer = null
        isPlaying = false
    }
}
