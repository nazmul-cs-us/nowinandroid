package com.starception.dua.services

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.starception.dua.util.PrayerNotificationManager

class PrayerNotificationService : Service() {
    
    companion object {
        private const val TAG = "PrayerNotificationService"
        private const val FOREGROUND_ID = 1001
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var currentPrayer = "Fajr"
    private var prayerProgress = 0
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Prayer notification service created")
        
        // Initialize the notification manager
        PrayerNotificationManager.initialize(this)
        
        // Start as a foreground service with an initial notification
        startForegroundWithPrayer(currentPrayer)
        
        // Start simulating prayer progression for demo purposes
        startPrayerProgressSimulation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Prayer notification service started")
        return START_STICKY
    }

    private fun startForegroundWithPrayer(prayerName: String) {
        try {
            // Create the initial notification
            PrayerNotificationManager.postPrayerNotification(prayerName, 0, true)
            
            // Get the notification for foreground service
            val notification = if (PrayerNotificationManager.supportsLiveUpdates()) {
                Log.d(TAG, "Using Live Update notifications (Android 16+)")
                Log.d(TAG, "Has promotable characteristics: ${PrayerNotificationManager.hasPromotableCharacteristics()}")
            } else {
                Log.d(TAG, "Using regular notifications (pre-Android 16)")
            }
            
            // Start foreground with a basic notification (the Live Update will be posted separately)
            val basicNotification = androidx.core.app.NotificationCompat.Builder(this, "prayer_live_update_channel")
                .setContentTitle("Prayer Time Tracker")
                .setContentText("Tracking prayer times")
                .setSmallIcon(com.starception.dua.R.drawable.ic_prayer_hands)
                .setOngoing(true)
                .build()
            
            startForeground(FOREGROUND_ID, basicNotification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }
    
    /**
     * Simulate prayer time progression for demonstration
     * In a real app, this would be based on actual prayer times
     */
    private fun startPrayerProgressSimulation() {
        val prayers = arrayOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        var currentIndex = 0
        
        handler.post(object : Runnable {
            override fun run() {
                // Update prayer progress
                prayerProgress += 10
                
                if (prayerProgress > 100) {
                    // Move to next prayer
                    prayerProgress = 0
                    currentIndex = (currentIndex + 1) % prayers.size
                    currentPrayer = prayers[currentIndex]
                    Log.d(TAG, "Prayer time changed to: $currentPrayer")
                }
                
                // Update the notification
                PrayerNotificationManager.updatePrayerProgress(currentPrayer, prayerProgress)
                Log.d(TAG, "Updated prayer progress: $currentPrayer - $prayerProgress%")
                
                // Schedule next update (every 30 seconds for demo)
                handler.postDelayed(this, 30000)
            }
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Prayer notification service destroyed")
        handler.removeCallbacksAndMessages(null)
        PrayerNotificationManager.cancelPrayerNotification()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
