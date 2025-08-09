package com.starception.dua.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.starception.dua.R

object PrayerNotificationManager {
    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context
    
    private const val TAG = "PrayerNotificationMgr"
    private const val CHANNEL_ID = "prayer_live_update_channel"
    private const val CHANNEL_NAME = "Prayer Notifications"
    private const val NOTIFICATION_ID = 1001
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        Log.d(TAG, "PrayerNotificationManager initialized")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Prayer time notifications with live updates support"
                enableVibration(false) // Reduce interruption for live updates
                setShowBadge(true)
                enableLights(true)
                
                // Future Android 16 Live Update optimizations
                if (Build.VERSION.SDK_INT >= 35) { // Android 16
                    try {
                        setAllowBubbles(true)
                    } catch (e: Exception) {
                        Log.d(TAG, "Live Update APIs not yet available")
                    }
                }
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }
    
    /**
     * Check if device supports Live Update notifications (Android 16+)
     * Future-ready detection for when AndroidX APIs are available
     */
    fun supportsLiveUpdates(): Boolean {
        val supported = Build.VERSION.SDK_INT >= 35 // Android 16
        Log.d(TAG, "Live Updates supported: $supported (API ${Build.VERSION.SDK_INT})")
        return supported
    }
    
    /**
     * Post prayer notification using Live Updates if supported, otherwise regular notification
     */
    fun postPrayerNotification(prayerName: String, progress: Int = 0, isOngoing: Boolean = true) {
        val notification = if (supportsLiveUpdates()) {
            buildLiveUpdateReadyNotification(prayerName, progress, isOngoing)
        } else {
            buildRegularNotification(prayerName, progress, isOngoing)
        }
        
        notificationManager.notify(NOTIFICATION_ID, notification.build())
        Log.d(TAG, "Posted notification: $prayerName (progress: $progress%)")
    }
    
    /**
     * Build notification ready for Android 16 Live Updates
     * Uses current APIs with Live Update optimizations
     */
    private fun buildLiveUpdateReadyNotification(
        prayerName: String, 
        progress: Int, 
        isOngoing: Boolean
    ): NotificationCompat.Builder {
        val title = try {
            appContext.getString(R.string.live_notification_title)
        } catch (e: Exception) {
            "Prayer Time Tracker"
        }
        
        val content = try {
            appContext.getString(R.string.live_notification_content, prayerName)
        } catch (e: Exception) {
            "Current prayer: $prayerName"
        }
        
        val progressText = if (progress > 0) " - Progress: $progress%" else ""
        
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$content$progressText")
            .setSmallIcon(R.drawable.ic_prayer_notification)
            .setOngoing(isOngoing)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(0) // Silent for live updates
            .setSilent(true)
            .setLocalOnly(true)
            .setShowWhen(true)
            .setUsesChronometer(false)
        
        // Show progress bar for Live Update simulation
        if (progress > 0 && progress <= 100) {
            builder.setProgress(100, progress, false)
        }
        
        // Future: When AndroidX supports Android 16 APIs, add:
        // builder.setRequestPromotedOngoing(true)
        // builder.setStyle(createProgressStyle(progress))
        
        Log.d(TAG, "Built Live Update ready notification")
        return builder
    }
    
    /**
     * Build regular notification for pre-Android 16 devices
     */
    private fun buildRegularNotification(prayerName: String, progress: Int, isOngoing: Boolean): NotificationCompat.Builder {
        val title = try {
            appContext.getString(R.string.live_notification_title)
        } catch (e: Exception) {
            "Prayer Time Tracker"
        }
        
        val content = try {
            appContext.getString(R.string.live_notification_content, prayerName)
        } catch (e: Exception) {
            "Current prayer: $prayerName"
        }
        
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_prayer_notification)
            .setOngoing(isOngoing)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        // Show simple progress for regular notifications
        if (progress > 0 && progress <= 100) {
            builder.setProgress(100, progress, false)
        }
        
        Log.d(TAG, "Built regular notification")
        return builder
    }
    
    /**
     * Update prayer progress - main entry point for live updates
     */
    fun updatePrayerProgress(prayerName: String, progress: Int) {
        postPrayerNotification(prayerName, progress, true)
    }
    
    /**
     * Cancel prayer notification
     */
    fun cancelPrayerNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        Log.d(TAG, "Cancelled prayer notification")
    }
    
    /**
     * Check if notification has promotable characteristics (for debugging)
     * Future-ready for when AndroidX supports hasPromotableCharacteristics()
     */
    fun hasPromotableCharacteristics(): Boolean {
        val hasPromotable = supportsLiveUpdates() // Simplified check for now
        Log.d(TAG, "Has promotable characteristics: $hasPromotable")
        return hasPromotable
    }
    
    /**
     * Get notification manager for advanced usage
     */
    fun getNotificationManager(): NotificationManager = notificationManager
}