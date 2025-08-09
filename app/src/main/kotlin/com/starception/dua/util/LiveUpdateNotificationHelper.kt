package com.starception.dua.util

import android.content.Context

/**
 * Legacy helper for prayer notifications - now delegates to PrayerNotificationManager
 * This maintains backward compatibility while using the new Live Update implementation
 */
object LiveUpdateNotificationHelper {
    
    fun createNotificationChannel(context: Context) {
        // Initialize the new notification manager
        PrayerNotificationManager.initialize(context)
    }

    fun postPrayerNotification(context: Context, prayerName: String) {
        // Ensure manager is initialized
        PrayerNotificationManager.initialize(context)
        // Use the new Live Update implementation
        PrayerNotificationManager.postPrayerNotification(prayerName, progress = 50, isOngoing = true)
    }

    fun cancelPrayerNotification(context: Context) {
        // Ensure manager is initialized
        PrayerNotificationManager.initialize(context)
        PrayerNotificationManager.cancelPrayerNotification()
    }
}
