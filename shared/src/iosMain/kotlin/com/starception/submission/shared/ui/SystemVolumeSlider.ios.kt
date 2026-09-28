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

package com.starception.submission.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.MediaPlayer.MPVolumeView

/** Apple's MPVolumeView — the Control Center system volume slider. */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun SystemVolumeSlider(modifier: Modifier) {
    val volumeView = remember {
        MPVolumeView(frame = platform.CoreGraphics.CGRectMake(0.0, 0.0, 0.0, 30.0))
    }
    UIKitView(
        factory = { volumeView },
        modifier = modifier,
    )
}
