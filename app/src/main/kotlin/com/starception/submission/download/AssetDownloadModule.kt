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

package com.starception.submission.download

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AssetDownloadModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // Fail fast when the network is dead so the manifest fallback chain runs quickly.
        // readTimeout is per-read (not per-request), so streaming downloads are unaffected
        // as long as bytes keep flowing within the window.
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideAssetDownloadManager(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): AssetDownloadManager = AssetDownloadManager(context, okHttpClient)

    @Provides
    @Singleton
    fun provideAssetRepository(
        @ApplicationContext context: Context,
        downloadManager: AssetDownloadManager,
    ): AssetRepository = AssetRepository(context, downloadManager)
}
