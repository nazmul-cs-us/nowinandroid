package com.starception.dua.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.starception.dua.R

/**
 * Helper class to test and verify notification functionality
 * Useful for debugging notification issues
 */
object NotificationTestHelper {
    
    private const val TAG = "NotificationTestHelper"
    private const val TEST_CHANNEL_ID = "test_notifications"
    private const val TEST_NOTIFICATION_ID = 9999
    
    /**
     * Test basic notification functionality
     */
    fun testBasicNotification(context: Context) {
        try {
            createTestNotificationChannel(context)
            
            val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
                .setContentTitle("Test Notification")
                .setContentText("Notifications are working!")
                .setSmallIcon(R.drawable.ic_prayer_hands)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(TEST_NOTIFICATION_ID, notification)
            
            Log.d(TAG, "Test notification sent successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending test notification", e)
        }
    }
    
    /**
     * Create test notification channel
     */
    private fun createTestNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (notificationManager.getNotificationChannel(TEST_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    TEST_CHANNEL_ID,
                    "Test Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Channel for testing notification functionality"
                    enableVibration(true)
                    setShowBadge(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Test notification channel created")
            }
        }
    }
    
    /**
     * Check if notifications are enabled for the app
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.areNotificationsEnabled()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification status", e)
            false
        }
    }
    
    /**
     * Log notification status for debugging
     */
    fun logNotificationStatus(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val enabled = notificationManager.areNotificationsEnabled()
            
            Log.d(TAG, "Notification Status:")
            Log.d(TAG, "- App notifications enabled: $enabled")
            Log.d(TAG, "- Android version: ${Build.VERSION.SDK_INT}")
            Log.d(TAG, "- Notification channels: ${notificationManager.notificationChannels.size}")
            
            // Log existing channels
            notificationManager.notificationChannels.forEach { channel ->
                Log.d(TAG, "- Channel: ${channel.name} (${channel.id}) - Importance: ${channel.importance}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging notification status", e)
        }
    }
}
