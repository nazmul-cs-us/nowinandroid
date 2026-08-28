/*
 * Copyright 2022 The Android Open Source Project
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

package com.starception.submission.feature.prayertimes.wobble

/**
 * The prayer-driven silent window, as the sync banner shows it.
 *
 * Only the state crosses. Reading it means asking the platform whether a silent
 * session is running, which on Android is PrayerSilentModeController and a
 * Context; that reader stays in the app module.
 */
data class SilentModeState(
    val isActive: Boolean = false,
    val displayText: String = "",
)
