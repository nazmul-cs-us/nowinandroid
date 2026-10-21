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
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSURL
import kotlin.math.roundToLong

actual suspend fun resolveQuranAudioUrl(
    surahNumber: Int,
    translationCode: String,
): String? {
    val category = when (translationCode) {
        "bn" -> "quran_audio_bengali"
        "en" -> "quran_audio_english"
        "hi" -> "quran_audio_hindi"
        "ur" -> "quran_audio_urdu"
        else -> "quran_audio_arabic"
    }
    val manifest = iosCloudAssets.loadManifest()
        ?: return quranAudioUrl(surahNumber)
    val entry = manifest.getAssetsByCategory(category).firstOrNull { asset ->
        asset.cdnKey.substringAfterLast('/')
            .takeWhile(Char::isDigit)
            .toIntOrNull() == surahNumber
    } ?: return quranAudioUrl(surahNumber)
    val resolved = iosCloudAssets.resolveAsset(entry.cdnKey, manifest)
        ?: return null
    return NSURL.fileURLWithPath(resolved.absolutePath).absoluteString
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

    init {
        engine.attachNode(player)
    }

    actual fun play(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, withOptions = 0u, error = null)
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
        AVAudioSession.sharedInstance().setActive(false, withOptions = 0u, error = null)
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
