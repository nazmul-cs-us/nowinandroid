/*
 * Copyright 2026 The Android Open Source Project
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

package com.starception.submission.shared.audio

import com.starception.submission.shared.assets.iosCloudAssets
import com.starception.submission.shared.platform.IosAudioSessionController
import kotlinx.coroutines.sync.withLock
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPlayerNode
import platform.Foundation.NSURL
import kotlin.math.roundToLong

private val audioPreparationMutex = kotlinx.coroutines.sync.Mutex()

@OptIn(ExperimentalForeignApi::class)
actual suspend fun resolveQuranAudioUrl(
    surahNumber: Int,
    translationCode: String,
): String? {
    val manifest = iosCloudAssets.loadManifest() ?: return null
    val entry = selectQuranAudioAsset(surahNumber, translationCode, manifest.assets.values)
        ?: return null
    val resolved = iosCloudAssets.resolveAsset(entry.cdnKey, manifest) ?: return null
    val localPath = if (entry.cdnKey.endsWith(".ogg", ignoreCase = true)) {
        // Apple does not read the CDN's Ogg Vorbis files through AVAudioFile.
        // Cache an AAC rendition keyed by source checksum; never silently play another language.
        audioPreparationMutex.withLock {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val output = resolved.absolutePath + "." + entry.sha256.take(16) + ".ios.m4a"
                val files = platform.Foundation.NSFileManager.defaultManager
                if (!files.fileExistsAtPath(output)) {
                    val temporary = output + ".partial.m4a"
                    if (deenly.vorbis.deenly_prepare_vorbis_m4a(resolved.absolutePath, temporary) != 1) {
                        return@withContext null
                    }
                    if (!files.moveItemAtPath(temporary, output, null)) {
                        files.removeItemAtPath(temporary, null)
                        return@withContext null
                    }
                }
                output
            }
        } ?: return null
    } else {
        resolved.absolutePath
    }
    return NSURL.fileURLWithPath(localPath).absoluteString
}

@OptIn(ExperimentalForeignApi::class)
actual class QuranAudioPlayer actual constructor() {
    private val engine = AVAudioEngine()
    private val player = AVAudioPlayerNode()
    private var audioFile: AVAudioFile? = null
    private var currentUrl: String? = null
    private var duration = 0.0
    private var segmentStartSeconds = 0.0
    private var completed = false
    private var scheduleGeneration = 0
    private var audioSessionActive = false

    init {
        engine.attachNode(player)
    }

    actual fun play(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        if (!audioSessionActive) {
            if (!IosAudioSessionController.activatePlayback()) return false
            audioSessionActive = true
        }
        if (url != currentUrl) {
            stopEngine()
            val file = runCatching { AVAudioFile(forReading = nsUrl, error = null) }.getOrNull()
                ?.takeIf { it.isOpen }
                ?: return false
            val sampleRate = file.processingFormat.sampleRate
            if (sampleRate <= 0.0 || file.length <= 0L) return false

            audioFile = file
            currentUrl = url
            duration = file.length / sampleRate
            segmentStartSeconds = 0.0
            completed = false
            engine.connect(player, to = engine.mainMixerNode, format = file.processingFormat)
            scheduleFrom(0.0)
        }
        if (completed) scheduleFrom(0.0)
        if (!engine.running) {
            engine.prepare()
            if (!engine.startAndReturnError(null)) return false
        }
        player.play()
        return player.playing
    }

    actual fun pause() {
        segmentStartSeconds = positionSeconds().toDouble()
        player.pause()
    }

    actual fun stop() {
        stopEngine()
        audioFile = null
        currentUrl = null
        duration = 0.0
        segmentStartSeconds = 0.0
        completed = false
        if (audioSessionActive) {
            audioSessionActive = false
            IosAudioSessionController.deactivate()
        }
    }

    actual fun positionSeconds(): Float {
        if (completed) return duration.toFloat()
        val nodeTime = player.lastRenderTime ?: return segmentStartSeconds.toFloat()
        val playerTime = player.playerTimeForNodeTime(nodeTime) ?: return segmentStartSeconds.toFloat()
        val elapsed = if (playerTime.sampleRate > 0.0) {
            playerTime.sampleTime / playerTime.sampleRate
        } else {
            0.0
        }
        return (segmentStartSeconds + elapsed).coerceIn(0.0, duration).toFloat()
    }

    actual fun durationSeconds(): Float {
        return duration.toFloat()
    }

    actual fun isPlaying(): Boolean {
        return player.playing
    }

    actual fun seekTo(seconds: Float) {
        val wasPlaying = player.playing
        scheduleFrom(seconds.toDouble())
        if (wasPlaying) player.play()
    }

    private fun scheduleFrom(seconds: Double) {
        val file = audioFile ?: return
        scheduleGeneration++
        val generation = scheduleGeneration
        player.stop()
        completed = false
        segmentStartSeconds = seconds.coerceIn(0.0, duration)
        val sampleRate = file.processingFormat.sampleRate
        val startFrame = (segmentStartSeconds * sampleRate).roundToLong().coerceIn(0L, file.length)
        val remainingFrames = (file.length - startFrame).coerceAtLeast(0L)
        if (remainingFrames == 0L) {
            completed = true
            return
        }
        player.scheduleSegment(
            file = file,
            startingFrame = startFrame,
            frameCount = remainingFrames.coerceAtMost(UInt.MAX_VALUE.toLong()).toUInt(),
            atTime = null,
        ) {
            if (generation == scheduleGeneration) completed = true
        }
    }

    private fun stopEngine() {
        scheduleGeneration++
        player.stop()
        engine.stop()
        engine.reset()
        engine.disconnectNodeOutput(player)
    }
}
