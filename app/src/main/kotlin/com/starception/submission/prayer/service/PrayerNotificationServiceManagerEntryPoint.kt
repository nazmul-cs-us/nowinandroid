package com.starception.submission.prayer.service

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry Point for accessing PrayerNotificationServiceManager from Composable functions
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrayerNotificationServiceManagerEntryPoint {
    fun prayerNotificationServiceManager(): PrayerNotificationServiceManager
}
