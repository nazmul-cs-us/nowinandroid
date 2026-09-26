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

package com.starception.submission.shared.travel

/**
 * Plays the bundled Travel Dua clip. The iOS actual uses AVAudioPlayer with
 * the playback session so the dua continues in the background like the
 * Android foreground-service chain.
 */
expect class TravelDuaPlayer() {
    /** Starts the dua; [onComplete] fires when it finishes or fails to start. */
    fun play(onComplete: () -> Unit)

    fun stop()
}
