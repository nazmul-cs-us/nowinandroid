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

package com.starception.submission.shared.quran

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The shared Quran screens are hosted by the iOS app; the Android app uses
 * its own SurahDetailScreen (with the full CDN artwork pipeline), so the
 * Android target of this expect renders a neutral placeholder.
 */
@Composable
internal actual fun SurahArtworkHeader(
    surahNumber: Int,
    contentDescription: String,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize())
}
