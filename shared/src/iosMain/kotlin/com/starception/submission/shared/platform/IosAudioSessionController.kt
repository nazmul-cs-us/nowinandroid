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

package com.starception.submission.shared.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.setActive
import platform.darwin.DISPATCH_QUEUE_SERIAL
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_sync

/** Serializes the app's synchronous AVAudioSession API away from the UI thread. */
@OptIn(ExperimentalForeignApi::class)
internal object IosAudioSessionController {
    private val queue = dispatch_queue_create(
        "com.starception.submission.audio-session",
        DISPATCH_QUEUE_SERIAL,
    )

    fun activatePlayback(): Boolean {
        var activated = false
        dispatch_sync(queue) {
            val session = AVAudioSession.sharedInstance()
            val configured = session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            activated = configured && session.setActive(true, withOptions = 0u, error = null)
        }
        return activated
    }

    fun activateRecording(): Boolean {
        var activated = false
        dispatch_sync(queue) {
            val session = AVAudioSession.sharedInstance()
            val configured = session.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                mode = AVAudioSessionModeMeasurement,
                options = 0u,
                error = null,
            )
            activated = configured && session.setActive(true, withOptions = 0u, error = null)
        }
        return activated
    }

    /** Deactivation does not gate subsequent UI work, so it can remain fully asynchronous. */
    fun deactivate() {
        dispatch_async(queue) {
            AVAudioSession.sharedInstance().setActive(false, withOptions = 0u, error = null)
        }
    }
}
