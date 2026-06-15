/*
 * Copyright 2024 Starception
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

package com.starception.submission.voice

import com.starception.submission.download.AssetDownloadManager
import com.starception.submission.download.AudioDownloadHelper
import com.starception.submission.download.ContentCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry Point for accessing SherpaOnnxTtsService, AssetDownloadManager,
 * and AudioDownloadHelper from Composable functions that don't have
 * direct access to Hilt injection.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SherpaOnnxTtsEntryPoint {
    fun sherpaOnnxTtsService(): SherpaOnnxTtsService
    fun assetDownloadManager(): AssetDownloadManager
    fun audioDownloadHelper(): AudioDownloadHelper
    fun contentCoordinator(): ContentCoordinator
}
