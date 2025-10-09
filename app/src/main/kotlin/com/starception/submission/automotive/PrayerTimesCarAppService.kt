package com.starception.submission.automotive

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Simple Android Auto Car App Service for Prayer Times
 * 
 * No dependency injection - simple implementation for car display
 */
class PrayerTimesCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return PrayerTimesCarSession()
    }
}