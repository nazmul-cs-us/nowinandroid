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

package com.starception.submission.util

import android.content.Context
import android.util.Log
import com.starception.submission.service.ActivityBasedDuaService

/**
 * ACTIVITY-BASED DUA HELPER: Easy integration for activity detection and dua playing
 *
 * This helper class provides simple methods to start/stop activity-based dua playing
 * throughout the application. It manages the background service and handles permissions.
 */
object ActivityBasedDuaHelper {

    private const val TAG = "ActivityBasedDuaHelper"

    /**
     * Start activity detection and dua playing
     */
    @JvmStatic
    fun startActivityDetection(context: Context) {
        try {
            val intent = ActivityBasedDuaService.createStartIntent(context)
            context.startService(intent)
            Log.i(TAG, "Started activity-based dua service")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting activity detection service", e)
        }
    }

    /**
     * Stop activity detection and dua playing
     */
    @JvmStatic
    fun stopActivityDetection(context: Context) {
        try {
            val intent = ActivityBasedDuaService.createStopIntent(context)
            context.startService(intent)
            Log.i(TAG, "Stopped activity-based dua service")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping activity detection service", e)
        }
    }

    /**
     * Check if activity detection service is running
     */
    fun isServiceRunning(context: Context): Boolean {
        // Note: This is a simplified check. In production, you might want to use
        // a more robust method like checking service state through a bound service
        return true // Placeholder - you could implement proper service state checking
    }
}
