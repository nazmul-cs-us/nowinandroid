package com.starception.dua

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.starception.dua.util.LiveUpdateNotificationHelper

class PrayerNotificationService : Service() {
    override fun onCreate() {
        super.onCreate()
        // Start as a foreground service with an initial notification
        startForegroundWithPrayer("Fajr")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Here you would schedule or listen for prayer time changes and update notification accordingly
        // For demonstration, we'll just keep the service running
        return START_STICKY
    }

    private fun startForegroundWithPrayer(prayerName: String) {
        LiveUpdateNotificationHelper.createNotificationChannel(this)
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Use promoted ongoing notification for Android 14+
            LiveUpdateNotificationHelper.postPrayerNotification(this, prayerName)
            // Notification is posted, but we need to build one for startForeground
            Notification.Builder(this, "prayer_live_update_channel")
                .setContentTitle(getString(R.string.live_notification_title))
                .setContentText(getString(R.string.live_notification_content, prayerName))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setPromotedOngoing(true)
                .build()
        } else {
            // Standard notification for lower versions
            LiveUpdateNotificationHelper.postPrayerNotification(this, prayerName)
            NotificationCompat.Builder(this, "prayer_live_update_channel")
                .setContentTitle(getString(R.string.live_notification_title))
                .setContentText(getString(R.string.live_notification_content, prayerName))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build()
        }
        startForeground(1001, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
