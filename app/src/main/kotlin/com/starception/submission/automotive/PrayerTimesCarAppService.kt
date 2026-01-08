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
