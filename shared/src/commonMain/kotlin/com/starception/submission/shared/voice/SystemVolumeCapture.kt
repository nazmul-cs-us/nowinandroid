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

package com.starception.submission.shared.voice

/**
 * The iOS counterpart of Android's system volume bar: starting a session
 * plays near-silent audio so the hardware volume buttons surface the SYSTEM
 * volume HUD; adjustments stream back as percentages and are captured as
 * the prayer's adhan volume. [stop] ends the session.
 */
expect class SystemVolumeCapture() {
    /** Activates the audio session; returns false when audio is unavailable. */
    fun start(onVolumeChanged: (percent: Int) -> Unit): Boolean

    fun stop()
}
