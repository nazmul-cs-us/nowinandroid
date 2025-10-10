package com.starception.submission.automotive

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * Car Session for Prayer Times Android Auto app
 * 
 * Manages the session lifecycle and provides screens for prayer times
 */
class PrayerTimesCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return PrayerTimesMainScreen(carContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Refresh the current screen when new intent is received
        carContext.getCarService(androidx.car.app.ScreenManager::class.java)
            .getTop()?.invalidate()
    }
}