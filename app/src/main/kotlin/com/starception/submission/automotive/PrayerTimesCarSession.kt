package com.starception.submission.automotive

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * Simple Car Session for Prayer Times Android Auto app
 * 
 * No dependency injection - basic session management
 */
class PrayerTimesCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return PrayerTimesMainScreen(carContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle new intent if needed
    }
}