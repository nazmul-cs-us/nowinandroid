package com.starception.submission.automotive

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Android Auto Car App Service for Prayer Times
 * 
 * Provides Islamic prayer times in a car-optimized interface
 * Category: IOT (Internet of Things) - for utility and informational apps
 */
class PrayerTimesCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // For production, consider using a more restrictive validator
        // that only allows known hosts for security
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return PrayerTimesCarSession()
    }
}