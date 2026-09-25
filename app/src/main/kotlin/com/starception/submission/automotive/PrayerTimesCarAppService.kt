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

package com.starception.submission.automotive

import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Android Auto Car App Service for Prayer Times
 *
 * Provides Islamic prayer times in a car-optimized interface.
 * Category: IOT (Internet of Things) - for utility and informational apps
 *
 * This service creates a session that provides:
 * - Real-time Islamic prayer times based on user's location
 * - Qibla direction compass for finding the direction to Makkah
 * - Prayer time countdown to next prayer
 *
 * The service uses the app's existing prayer calculation infrastructure
 * through AutomotivePrayerDataProvider for accurate, location-based times.
 */
class PrayerTimesCarAppService : CarAppService() {

    companion object {
        private const val TAG = "PrayerTimesCarApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🚗 Android Auto: Prayer Times service created")
    }

    override fun createHostValidator(): HostValidator {
        // For production, consider using a more restrictive validator
        // that only allows known hosts for security
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        Log.i(TAG, "🚗 Android Auto: Creating new prayer times session")
        return PrayerTimesCarSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "🚗 Android Auto: Prayer Times service destroyed")
    }
}
