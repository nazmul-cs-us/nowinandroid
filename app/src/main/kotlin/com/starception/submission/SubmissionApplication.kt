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

package com.starception.submission

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy.Builder
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.starception.submission.sync.initializers.Sync
import com.starception.submission.util.ProfileVerifierLogger
import com.starception.submission.util.AnrPreventionConfig
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import android.content.Intent
import android.os.Build
import android.util.Log
import com.starception.submission.services.PrayerNotificationService
import com.starception.submission.ui.search.InMemorySearchService
import com.starception.submission.util.PrayerNotificationManager
import com.starception.submission.prayer.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [Application] class for Submission
 */
@HiltAndroidApp
class SubmissionApplication : Application(), ImageLoaderFactory {
    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var profileVerifierLogger: ProfileVerifierLogger

    @Inject
    lateinit var inMemorySearchService: InMemorySearchService

    @Inject
    lateinit var settingsSyncManager: com.starception.submission.usersettings.sync.SettingsSyncManager

    @Inject
    lateinit var contentCoordinator: com.starception.submission.download.ContentCoordinator

    @Inject
    lateinit var audioDownloadHelper: com.starception.submission.download.AudioDownloadHelper

    override fun onCreate() {
        Log.d("SubmissionApplication", "Application onCreate started")
        super.onCreate()

        // Initialize FileLogger for prayer/adhan debugging
        FileLogger.init(this)
        FileLogger.i("SubmissionApplication", "Application onCreate started - FileLogger initialized")

        // Initialize Prayer Tracker for tracking completed prayers
        com.starception.submission.util.PrayerTracker.initialize(this)
        Log.d("SubmissionApplication", "Prayer Tracker initialized")

        // Verify ANR prevention configuration
        AnrPreventionConfig.isOptimizedForAnrPrevention()
        
        // Clean up any existing service instances to prevent conflicts
        cleanupExistingServices()
        
        setStrictModePolicy()

        // Use background thread for heavy initialization to prevent ANR
        Thread {
            try {
                // ENABLE Sync initialization to populate topics data for interests pages
                if (AnrPreventionConfig.ENABLE_BACKGROUND_SYNC) {
                    Sync.initialize(context = this)
                    Log.d("SubmissionApplication", "Sync initialized to populate topics data")
                } else {
                    Log.w("SubmissionApplication", "Sync disabled - interests pages will be empty")
                }
                profileVerifierLogger()
                
                // DISABLE prayer notification manager initialization to prevent ANR
                if (AnrPreventionConfig.ENABLE_AUTO_SERVICE_START) {
                    PrayerNotificationManager.initialize(this)
                }
                
                // Start cloud settings-sync (push on change, pull on login) when signed in.
                settingsSyncManager.start()

                // Rebuild derived content (news.db) when a content category finishes downloading.
                contentCoordinator.start()

                // Wire the news-card chapter play button (core/ui) to play-local-else-download-
                // then-cache fortress audio. core/ui can't see the download system, so we supply
                // the resolver here; a null return falls back to streaming the URL.
                com.starception.submission.core.ui.ChapterAudioController.localAudioResolver = { url ->
                    audioDownloadHelper.resolveFortressAudioUrlToLocalPath(url)
                }

                // Bridge ChapterAudioController <-> GlobalMediaViewModel so Fortress chapter
                // playback surfaces the shared media mini-bar with a progress sweep (like Surah/
                // Hadith). core/ui can't reference the app-module media layer, so wire it here.
                com.starception.submission.core.ui.ChapterAudioController.onPlaybackStateChanged =
                    { playing, title ->
                        com.starception.submission.media.GlobalMediaViewModel
                            .onFortressPlaybackChanged?.invoke(playing, title)
                    }
                com.starception.submission.core.ui.ChapterAudioController.onProgressChanged =
                    { pos, dur ->
                        com.starception.submission.media.GlobalMediaViewModel
                            .onFortressProgressChanged?.invoke(pos, dur)
                    }
                com.starception.submission.media.GlobalMediaViewModel.onFortressPlayPauseRequested =
                    { com.starception.submission.core.ui.ChapterAudioController.togglePlayPause() }
                com.starception.submission.media.GlobalMediaViewModel.onFortressSeekRequested =
                    { pos -> com.starception.submission.core.ui.ChapterAudioController.seekTo(pos) }

                Log.d("SubmissionApplication", "Background initialization completed")
            } catch (e: Exception) {
                Log.e("SubmissionApplication", "Error during background initialization", e)
            }
        }.apply {
            // Set thread priority to prevent blocking main thread
            priority = AnrPreventionConfig.getBackgroundThreadPriority()
            name = "AppInitThread"
        }.start()
        
        // Pre-warm the in-memory search indices (surahs + quranic duas + verses
        // + fortress chapters) on a background coroutine. Without this, the very
        // first keystroke pays ~160ms to build all four indices, so SQL hits
        // (fortress / ayahs) show before the in-memory hits — the gap is visible
        // to the user.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                inMemorySearchService.preload()
            } catch (e: Exception) {
                Log.w("SubmissionApplication", "Search index preload failed", e)
            }
        }

        Log.d("SubmissionApplication", "Application onCreate completed")
        
        // DISABLE automatic service startup from Application to prevent service timeout ANR
        // Service will only be started from MainActivity.onResume() after user interaction
        Log.d("SubmissionApplication", "Application initialized, service will start only from MainActivity")
    }
    
    /**
     * Clean up any existing service instances to prevent conflicts when app reopens
     */
    private fun cleanupExistingServices() {
        try {
            // Stop any existing prayer notification service to prevent conflicts
            val intent = Intent(this, PrayerNotificationService::class.java)
            stopService(intent)
            Log.d("SubmissionApplication", "Cleaned up existing service instances")
        } catch (e: Exception) {
            Log.e("SubmissionApplication", "Error cleaning up existing services", e)
        }
    }

    override fun newImageLoader(): ImageLoader = imageLoader.get()

    /**
     * Return true if the application is debuggable.
     */
    private fun isDebuggable(): Boolean {
        return 0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
    }

    /**
     * Set a thread policy that detects all potential problems on the main thread, such as network
     * and disk access.
     *
     * If a problem is found, the offending call will be logged and the application will be killed.
     */
    private fun setStrictModePolicy() {
        if (isDebuggable()) {
            StrictMode.setThreadPolicy(
                Builder().detectAll().penaltyLog().build(),
            )
        }
    }
}
