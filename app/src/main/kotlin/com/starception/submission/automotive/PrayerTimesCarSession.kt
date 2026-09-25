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

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session

/**
 * Car Session for Prayer Times Android Auto app
 *
 * Manages the session lifecycle and provides screens for prayer times.
 * This session initializes the AutomotivePrayerDataProvider which connects
 * to the app's existing prayer calculation infrastructure.
 */
class PrayerTimesCarSession : Session() {

    companion object {
        private const val TAG = "PrayerTimesCarSession"
    }

    // Lazy initialization of data provider to ensure carContext is available
    private val dataProvider: AutomotivePrayerDataProvider by lazy {
        Log.i(TAG, "🚗 Initializing prayer data provider")
        AutomotivePrayerDataProvider(carContext)
    }

    override fun onCreateScreen(intent: Intent): Screen {
        Log.i(TAG, "🚗 Creating main prayer times screen")
        return PrayerTimesMainScreen(carContext, dataProvider)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(TAG, "🚗 Received new intent, refreshing current screen")

        // Refresh the current screen when new intent is received
        carContext.getCarService(ScreenManager::class.java)
            .top?.invalidate()
    }
}
