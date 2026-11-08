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

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private var artworkSettings: Pair<SharedPreferences, HomeArtworkSettings>? = null

@Composable
actual fun rememberHomeArtworkSettings(): HomeArtworkSettings {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val prefs = context.getSharedPreferences("home_artwork", 0)
        artworkSettings?.takeIf { it.first === prefs }?.second ?: run {
            HomeArtworkSettings(prefs.getString("schedule", null)) { value ->
                prefs.edit().putString("schedule", value).apply()
            }.also { artworkSettings = prefs to it }
        }
    }
}
