package com.starception.submission.automotive

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.lifecycleScope
import com.starception.submission.automotive.screens.PrayerTimesMainScreen
import com.starception.submission.automotive.screens.PrayerTimesGridScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Car Session for Prayer Times Android Auto app
 * 
 * Manages the session lifecycle and navigation between screens
 * in the Android Auto environment.
 */
@AndroidEntryPoint
class PrayerTimesCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        // Start with the grid view for a more visual experience
        return PrayerTimesGridScreen(carContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle any new intents if needed
        screenManager.push(PrayerTimesGridScreen(carContext))
    }
}