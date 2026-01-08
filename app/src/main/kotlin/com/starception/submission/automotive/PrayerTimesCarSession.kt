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
