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
import com.starception.submission.util.PrayerNotificationManager

/**
 * [Application] class for Submission
 */
@HiltAndroidApp
class SubmissionApplication : Application(), ImageLoaderFactory {
    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var profileVerifierLogger: ProfileVerifierLogger

    override fun onCreate() {
        Log.d("SubmissionApplication", "Application onCreate started")
        super.onCreate()

        // Verify ANR prevention configuration
        AnrPreventionConfig.isOptimizedForAnrPrevention()
        
        // Clean up any existing service instances to prevent conflicts
        cleanupExistingServices()
        
        setStrictModePolicy()

        // Use background thread for heavy initialization to prevent ANR
        Thread {
            try {
                // DISABLE Sync initialization to prevent WorkManager ANR after app closure
                if (AnrPreventionConfig.ENABLE_BACKGROUND_SYNC) {
                    Sync.initialize(context = this)
                }
                profileVerifierLogger()
                
                // DISABLE prayer notification manager initialization to prevent ANR
                if (AnrPreventionConfig.ENABLE_AUTO_SERVICE_START) {
                    PrayerNotificationManager.initialize(this)
                }
                
                Log.d("SubmissionApplication", "Background initialization completed")
            } catch (e: Exception) {
                Log.e("SubmissionApplication", "Error during background initialization", e)
            }
        }.apply {
            // Set thread priority to prevent blocking main thread
            priority = AnrPreventionConfig.getBackgroundThreadPriority()
            name = "AppInitThread"
        }.start()
        
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
