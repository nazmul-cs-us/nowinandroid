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
        LiveUpdateNotificationHelper.postPrayerNotification(this, prayerName)
        val notification: Notification = NotificationCompat.Builder(this, "prayer_live_update_channel")
            .setContentTitle(getString(R.string.live_notification_title))
            .setContentText(getString(R.string.live_notification_content, prayerName))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        startForeground(1001, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
