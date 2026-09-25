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

package com.starception.submission.di

import android.content.Context
import android.util.Log
import com.starception.submission.core.contentdatabase.NewsDatabase
import com.starception.submission.download.AssetRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton

/**
 * Provides NewsDatabase with CDN asset support via AssetRepository.
 * This module lives in the app module (not core/contentdatabase) so it
 * can access AssetRepository for CDN-downloaded database files.
 *
 * On first launch (empty database), automatically regenerates news.db
 * from bundled source databases (quran.db, fortress_of_the_muslim_v2.db, etc.).
 */
@Module
@InstallIn(SingletonComponent::class)
object NewsDatabaseModule {

    private const val CDN_KEY = "databases/news.db"
    private const val TAG = "NewsDatabaseModule"

    @Provides
    @Singleton
    fun provideNewsDatabase(
        @ApplicationContext context: Context,
        assetRepository: AssetRepository,
    ): NewsDatabase {
        val dbFile = assetRepository.getDatabaseFile(CDN_KEY)
        val db = NewsDatabase.getInstance(context, dbFile)

        // Auto-regenerate news.db from bundled sources if database is empty
        // This handles fresh installs where news.db hasn't been generated yet
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val count = db.newsDao().getNewsResourceCount()
                if (count == 0) {
                    Log.i(TAG, "News database is empty, auto-regenerating from sources...")
                    val result = NewsDatabase.regenerateFromSources(context)
                    if (result.success) {
                        Log.i(TAG, "Auto-regeneration complete: ${result.totalNewsResources} resources, ${result.topicMappings} mappings")
                    } else {
                        Log.e(TAG, "Auto-regeneration failed: ${result.error}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking/regenerating news database", e)
            }
        }

        return db
    }
}
