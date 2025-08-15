package com.starception.dua.util

import com.starception.dua.MainActivity
import com.starception.dua.R
import android.app.Notification
import android.content.res.Configuration
import android.graphics.Color
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat

object PrayerNotificationManager {
    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context
    private var initialized: Boolean = false
    
    private const val TAG = "PrayerNotificationMgr"
    private const val CHANNEL_ID = "prayer_live_update_channel"
    private const val CHANNEL_NAME = "Prayer Notifications"
    private const val NOTIFICATION_ID = 1001
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        initialized = true
        Log.d(TAG, "PrayerNotificationManager initialized")
    }
    
    /**
     * Check if the notification manager has been initialized
     */
    fun isInitialized(): Boolean = initialized
    
    /**
     * Create PendingIntent to launch the main app
     * Uses proper flags to ensure clean app startup from notification
     */
    private fun createAppLaunchIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            // Use minimal flags to avoid conflicts with app lifecycle
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Don't add CATEGORY_LAUNCHER for notification intents - this can cause conflicts
            // Don't set ACTION_MAIN for notification intents
        }
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use app's theme colors
            val isDarkTheme = (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val themeColor = if (isDarkTheme) {
                Color.parseColor("#FFA9FE") // Purple80 - Dark theme
            } else {
                Color.parseColor("#8B418F") // Purple40 - Light theme
            }
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Prayer Times",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live updates for prayer times and guidance"
                // Remove custom light color for better lock screen compatibility
                // lightColor = appContext.getColor(R.color.purple_500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(false) // Disable custom lights for better compatibility
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Modern notification channel created with theme colors and Live Update support")
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
     * Post detailed prayer notification with custom content
     */
    fun postDetailedPrayerNotification(
        title: String,
        content: String,
        detailedMessage: String,
        progress: Int = 0,
        isOngoing: Boolean = true
    ) {
        Log.d(TAG, "Posting detailed prayer notification: $title")
        
        // Check if we can post promoted notifications (Android 16+ Live Updates)
        val canPostPromoted = if (Build.VERSION.SDK_INT >= 35) {
            try {
                val notificationManager = appContext.getSystemService(NotificationManager::class.java)
                notificationManager.canPostPromotedNotifications()
            } catch (e: Exception) {
                Log.d(TAG, "Cannot check promoted notification capability: ${e.message}")
                false
            }
        } else {
            false
        }
        
        Log.d(TAG, "Can post promoted notifications: $canPostPromoted")
        
        if (Build.VERSION.SDK_INT >= 35 && canPostPromoted) {
            // Use Android 16 Live Update notification
            try {
                val notification = buildLiveUpdateNotification(
                    title = title,
                    content = content,
                    detailedMessage = detailedMessage,
                    progress = progress,
                    isOngoing = isOngoing
                )
                
                notificationManager.notify(NOTIFICATION_ID, notification.build())
                Log.d(TAG, "Posted Android 16 Live Update notification: $title")
                
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create Android 16 Live Update notification, falling back: ${e.message}")
                postCompatNotification(title, content, detailedMessage, progress, isOngoing)
            }
        } else {
            // Fallback to standard notification
            postCompatNotification(title, content, detailedMessage, progress, isOngoing)
        }
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
        
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_prayer_hands)
            .setContentIntent(createAppLaunchIntent())
            .setOngoing(isOngoing)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(0) // Silent for live updates
            .setSilent(true)
            .setLocalOnly(true)
            .setShowWhen(true)
            .setUsesChronometer(false)
        
        // Add Live Update specific features for Android 16+
        if (Build.VERSION.SDK_INT >= 35) { // Android 16
            try {
                builder.setRequestPromotedOngoing(true)
                if (progress > 0) {
                    builder.setStyle(buildPrayerProgressStyle(progress))
                }
                
                // Add large icon for better Live Update appearance
                builder.setLargeIcon(
                    IconCompat.createWithResource(
                        appContext, R.drawable.ic_prayer_hands
                    ).toIcon(appContext)
                )
                
                Log.d(TAG, "Applied Live Update features (API ${Build.VERSION.SDK_INT})")
            } catch (e: Exception) {
                Log.d(TAG, "Live Update APIs not available: ${e.message}")
                // Fallback to regular progress bar
                if (progress > 0 && progress <= 100) {
                    builder.setProgress(100, progress, false)
                }
            }
        } else {
            // Show progress bar for pre-Android 16
            if (progress > 0 && progress <= 100) {
                builder.setProgress(100, progress, false)
            }
            
            // Add large icon for better appearance
            try {
                builder.setLargeIcon(
                    IconCompat.createWithResource(
                        appContext, R.drawable.ic_prayer_hands
                    ).toIcon(appContext)
                )
            } catch (e: Exception) {
                Log.d(TAG, "Could not set large icon: ${e.message}")
            }
        }
        
        Log.d(TAG, "Built Live Update ready notification")
        return builder
    }
    
    /**
     * Build prayer progress style for Live Updates
     * Creates 3 segments: Mosque (0-20min), Best Time (20min-halfway), Make Time (halfway-end)
     */
    @RequiresApi(35) // Android 16
    private fun buildPrayerProgressStyle(progress: Int): NotificationCompat.ProgressStyle {
        // Colors for different prayer phases
        val mosqueColor = Color.valueOf(0.2f, 0.8f, 0.4f, 1f).toArgb()      // Green for mosque phase
        val bestTimeColor = Color.valueOf(0.9f, 0.7f, 0.2f, 1f).toArgb()    // Gold for best time phase
        val makeTimeColor = Color.valueOf(0.8f, 0.3f, 0.3f, 1f).toArgb()    // Red for make time phase
        
        return try {
            NotificationCompat.ProgressStyle()
                .setProgressPoints(
                    listOf(
                        NotificationCompat.ProgressStyle.Point(20).setColor(mosqueColor),      // Mosque reached (20 min)
                        NotificationCompat.ProgressStyle.Point(50).setColor(bestTimeColor),    // Middle of prayer time
                        NotificationCompat.ProgressStyle.Point(100).setColor(makeTimeColor)    // End of prayer time
                    )
                )
                .setProgressSegments(
                    listOf(
                        NotificationCompat.ProgressStyle.Segment(20).setColor(mosqueColor),    // 0-20 min: Go to mosque
                        NotificationCompat.ProgressStyle.Segment(30).setColor(bestTimeColor),  // 20-50 min: Best time for prayer
                        NotificationCompat.ProgressStyle.Segment(50).setColor(makeTimeColor)   // 50-100 min: Make time for prayer
                    )
                )
                .setProgressTrackerIcon(
                    IconCompat.createWithResource(
                        appContext, if (progress >= 100) R.drawable.ic_prayer_check else R.drawable.ic_prayer_progress
                    )
                )
                .setProgress(progress)
        } catch (e: Exception) {
            Log.w(TAG, "Error building progress style: ${e.message}")
            // Fallback to basic progress style
            NotificationCompat.ProgressStyle().setProgress(progress)
        }
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
            .setSmallIcon(R.drawable.ic_prayer_hands)
            .setContentIntent(createAppLaunchIntent())
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
     * Post compatibility notification for older Android versions
     */
    private fun postCompatNotification(
        title: String,
        content: String,
        detailedMessage: String,
        progress: Int,
        isOngoing: Boolean
    ) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID).apply {
            setContentTitle(title)
            setContentText(content)
            setStyle(NotificationCompat.BigTextStyle().bigText(detailedMessage))
            setSmallIcon(R.drawable.ic_prayer_hands)
            setContentIntent(createAppLaunchIntent())
            setOngoing(isOngoing)
            setCategory(NotificationCompat.CATEGORY_STATUS)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setLocalOnly(false) // Allow lock screen display
        }
        
        // Add progress if specified
        if (progress > 0 && progress <= 100) {
            notification.setProgress(100, progress, false)
        }
        
        notificationManager.notify(NOTIFICATION_ID, notification.build())
        Log.d(TAG, "Posted compat notification: $title")
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
     * Force refresh notification to trigger Live Updates
     * This ensures Android 16 Live Update features are properly activated
     */
    fun forceRefreshNotification() {
        if (supportsLiveUpdates()) {
            try {
                // Use Live Update notification for Android 16
                val isDarkTheme = (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val themeColor = if (isDarkTheme) {
                    Color.parseColor("#FFA9FE") // Purple80 - Dark theme
                } else {
                    Color.parseColor("#8B418F") // Purple40 - Light theme
                }
                
                val notification = buildLiveUpdateNotification(
                    "Prayer Time Tracker",
                    "Live Updates Active",
                    "Prayer time tracking with real-time updates",
                    0,
                    true
                ).build()
                
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.d(TAG, "Forced Live Update notification refresh")
            } catch (e: Exception) {
                Log.w(TAG, "Error forcing Live Update notification refresh: ${e.message}")
                // Fallback to simple notification
                val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setContentTitle("Prayer Time Tracker")
                    .setContentText("Live Updates Active")
                    .setSmallIcon(R.drawable.ic_prayer_hands)
                    .setOngoing(true)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setColor(Color.parseColor("#8B418F"))
                    .setColorized(true)
                
                notificationManager.notify(NOTIFICATION_ID, builder.build())
                Log.d(TAG, "Forced compat notification refresh")
            }
        }
    }
    
    /**
     * Get notification manager for advanced usage
     */
    fun getNotificationManager(): NotificationManager = notificationManager

    /**
     * Check and log detailed Live Update status for debugging
     */
    fun checkLiveUpdateStatus(): String {
        val apiLevel = Build.VERSION.SDK_INT
        val supportsLiveUpdates = supportsLiveUpdates()
        val channelCreated = try {
            notificationManager.getNotificationChannel(CHANNEL_ID) != null
        } catch (e: Exception) {
            false
        }
        
        val status = buildString {
            appendLine("📱 Live Update Status Check:")
            appendLine("   API Level: $apiLevel (Android ${getAndroidVersionName(apiLevel)})")
            appendLine("   Live Updates Supported: $supportsLiveUpdates")
            appendLine("   Notification Channel Created: $channelCreated")
            appendLine("   App Context Available: ${::appContext.isInitialized}")
            
            if (supportsLiveUpdates) {
                appendLine("   ✅ Android 16+ Live Update features available")
                appendLine("   🎯 ProgressStyle will be applied")
                appendLine("   🔄 Promoted ongoing notifications enabled")
            } else {
                appendLine("   ❌ Live Updates not supported on this device")
                appendLine("   📱 Requires Android 16 (API 35+)")
            }
        }
        
        Log.i(TAG, status)
        return status
    }
    
    /**
     * Get Android version name from API level
     */
    private fun getAndroidVersionName(apiLevel: Int): String {
        return when (apiLevel) {
            35 -> "16 (Upside Down Cake)"
            34 -> "14 (Upside Down Cake)"
            33 -> "13 (Tiramisu)"
            32 -> "12L (Snow Cone)"
            31 -> "12 (Snow Cone)"
            30 -> "11 (Red Velvet Cake)"
            else -> "Unknown"
        }
    }

    /**
     * Build Live Update ProgressStyle with segments and points
     * Following the official Android 16 sample pattern
     */
    @RequiresApi(35) // Android 16
    private fun buildLiveUpdateProgressStyle(progress: Int): NotificationCompat.ProgressStyle {
        // Modern Material Design 3 color palette for prayer phases
        val mosqueColor = Color.parseColor("#4CAF50")      // Green for mosque phase
        val bestTimeColor = Color.parseColor("#FF9800")    // Orange for best time phase  
        val makeTimeColor = Color.parseColor("#F44336")    // Red for make time phase
        
        return try {
            // Note: The advanced ProgressStyle features (segments, points) are not yet available
            // in NotificationCompat.ProgressStyle. This will be enhanced when AndroidX fully
            // supports Android 16 Progress-Centric Notifications.
            // For now, we use the basic progress functionality.
            NotificationCompat.ProgressStyle().setProgress(progress)
        } catch (e: Exception) {
            Log.w(TAG, "Error building Live Update ProgressStyle: ${e.message}")
            // Fallback to basic ProgressStyle
            NotificationCompat.ProgressStyle().setProgress(progress)
        }
    }
    
    /**
     * Build Live Update notification using NotificationCompat.ProgressStyle
     * Following the official Android 16 Live Update sample pattern
     */
    @RequiresApi(35) // Android 16
    private fun buildLiveUpdateNotification(
        title: String,
        content: String,
        detailedMessage: String,
        progress: Int,
        isOngoing: Boolean
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(appContext, CHANNEL_ID).apply {
            setContentTitle(title)
            setContentText(content)
            setStyle(NotificationCompat.BigTextStyle().bigText(detailedMessage))
            setSmallIcon(R.drawable.ic_prayer_hands)
            setContentIntent(createAppLaunchIntent())
            setOngoing(isOngoing)
            setCategory(NotificationCompat.CATEGORY_STATUS)
            setVisibility(Notification.VISIBILITY_PUBLIC)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            // Remove custom colorization for better lock screen compatibility
            // setColor(themeColor)
            // setColorized(true)
            
            // Android 16 Live Update specific features (following official sample)
            setRequestPromotedOngoing(true)
            
            // Set the ProgressStyle for Live Updates
            setStyle(buildLiveUpdateProgressStyle(progress))
            
            // Add large icon for better Live Update appearance
            setLargeIcon(IconCompat.createWithResource(appContext, R.drawable.ic_prayer_hands).toIcon(appContext))
            
            // Additional settings for better lock screen display
            setShowWhen(true)
            setUsesChronometer(false)
            setAutoCancel(false)
        }
    }
}