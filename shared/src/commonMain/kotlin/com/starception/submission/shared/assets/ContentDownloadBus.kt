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

package com.starception.submission.shared.assets

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A content download in flight, for the app-level pull-to-sync strip. */
data class ContentDownloadStatus(
    val label: String,
    /** 0..1; 0 with an active download means "indeterminate". */
    val progress: Float,
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
)

/**
 * The shared counterpart of the Android app's download-progress flow
 * (MainActivityViewModel.contentDownloadLabel / rawDownloadProgress):
 * content downloads publish here so the pull-to-sync strip can carry
 * the banner on every screen, not just the storage manager.
 */
object ContentDownloadBus {
    private val _state = MutableStateFlow<ContentDownloadStatus?>(null)
    val state: StateFlow<ContentDownloadStatus?> = _state

    fun publish(status: ContentDownloadStatus?) {
        _state.value = status
    }
}
