package com.starception.submission.automotive

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import dagger.hilt.android.AndroidEntryPoint

/**
 * Android Auto Car App Service for Prayer Times
 * 
 * This service provides prayer time information and controls
 * optimized for in-car display and interaction.
 */
@AndroidEntryPoint
class PrayerTimesCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return PrayerTimesCarSession()
    }
}