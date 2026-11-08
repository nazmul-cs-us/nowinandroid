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

package com.starception.submission.core.images

import androidx.compose.runtime.Composable
import platform.Foundation.NSUserDefaults

private val artworkSettings by lazy {
    val defaults = NSUserDefaults.standardUserDefaults
    HomeArtworkSettings(defaults.stringForKey("home_artwork_schedule")) { value ->
        defaults.setObject(value, forKey = "home_artwork_schedule")
    }
}

@Composable
actual fun rememberHomeArtworkSettings(): HomeArtworkSettings = artworkSettings
