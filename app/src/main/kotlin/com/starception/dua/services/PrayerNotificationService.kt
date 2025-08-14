package com.starception.dua.services

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.starception.dua.MainActivity
import com.starception.dua.util.PrayerNotificationManager
import com.starception.dua.util.AnrPreventionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * Simplified Prayer Notification Service
 * Focuses on ANR prevention and basic notification functionality
 */
class PrayerNotificationService : Service() {
    
    companion object {
        private const val TAG = "PrayerNotificationService"
        private const val FOREGROUND_ID = 1001
        private val UPDATE_INTERVAL_MS = AnrPreventionConfig.SERVICE_UPDATE_INTERVAL_MS
        
        @Volatile
        private var isServiceRunning = false
        
        @Volatile
        private var isInitializing = false
        
        // Add app instance tracking to prevent conflicts
        @Volatile
        private var currentAppInstance = 0L
        
        // Generate unique app instance ID
        fun getNextAppInstance(): Long = System.currentTimeMillis()
        
        // Check if service is already running in another process
        fun isServiceRunningInAnotherProcess(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningServices = am.getRunningServices(Integer.MAX_VALUE)
            return runningServices.any { 
                it.service.className == PrayerNotificationService::class.java.name &&
                it.pid != android.os.Process.myPid()
            }
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Prayer notification service created")
        
        // Check if service is already running in another process to prevent conflicts
        if (isServiceRunningInAnotherProcess(this)) {
            Log.w(TAG, "Service already running in another process, stopping this instance to prevent conflicts")
            stopSelf()
            return
        }
        
        // Prevent duplicate initialization that causes service timeout/ANR
        if (isInitializing) {
            Log.w(TAG, "Service already initializing, stopping duplicate")
            stopSelf()
            return
        }
        
        if (isServiceRunning) {
            Log.w(TAG, "Service already running, stopping duplicate")
            stopSelf()
            return
        }
        
        isInitializing = true
        
        try {
            Log.d(TAG, "Creating modern async-initialized service")
            
            // CRITICAL FIX: Start foreground immediately to prevent ANR
            startForegroundImmediately()
            
            // Initialize everything asynchronously to prevent blocking
            serviceScope.launch {
                try {
                    // Start prayer time updates in background  
                    startRealPrayerTimeUpdates()
                    
                    isServiceRunning = true
                    isInitializing = false
                    Log.d(TAG, "Modern service started successfully asynchronously")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during async service initialization", e)
                    isInitializing = false
                    isServiceRunning = false
                    stopSelf()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during service creation", e)
            isInitializing = false
            isServiceRunning = false
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Prayer notification service onStartCommand - startId: $startId")
        
        // If service is already running, just return - no need to restart
        if (isServiceRunning) {
            Log.d(TAG, "Service already running, ignoring duplicate start")
            return START_NOT_STICKY
        }
        
        // CRITICAL: If service wasn't started in onCreate, start it now to prevent ANR
        if (!isServiceRunning) {
            try {
                // Add timeout to prevent hanging
                val timeoutJob = serviceScope.launch {
                    delay(5000) // 5 second timeout
                    if (!isServiceRunning) {
                        Log.e(TAG, "Service startup timeout, stopping to prevent ANR")
                        stopSelf()
                    }
                }
                
                startForegroundImmediately()
                startRealPrayerTimeUpdates()
                isServiceRunning = true
                
                // Cancel timeout since service started successfully
                timeoutJob.cancel()
                
                Log.d(TAG, "Service started from onStartCommand")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service from onStartCommand", e)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        Log.d(TAG, "Service starting for the first time")
        return START_NOT_STICKY
    }

    /**
     * Start foreground service immediately to prevent ANR
     * CRITICAL: Services must call startForeground() within 5 seconds
     * Uses minimal notification creation without expensive initialization
     */
    private fun startForegroundImmediately() {
        try {
            // Create minimal notification channel if needed (fastest approach)
            createMinimalNotificationChannel()
            
            // Create basic notification synchronously (no heavy initialization)
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
            
            val basicNotification = androidx.core.app.NotificationCompat.Builder(this, "prayer_live_update_channel")
                .setContentTitle("Prayer Time Tracker")
                .setContentText("Starting prayer time updates...")
                .setSmallIcon(com.starception.dua.R.drawable.ic_prayer_hands)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
                .build()
            
            // CRITICAL: Call startForeground() immediately to prevent ANR
            startForeground(FOREGROUND_ID, basicNotification)
            
            Log.d(TAG, "Foreground service started immediately to prevent ANR")
            
            // Now initialize background updates
            initializeBackgroundUpdates()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service immediately", e)
            throw e  // Re-throw to trigger cleanup in onCreate
        }
    }
    
    /**
     * Create minimal notification channel for immediate foreground service startup
     * Avoids heavy PrayerNotificationManager initialization that can cause ANR
     */
    private fun createMinimalNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            // Check if channel already exists to avoid recreation
            if (notificationManager.getNotificationChannel("prayer_live_update_channel") == null) {
                val channel = android.app.NotificationChannel(
                    "prayer_live_update_channel",
                    "Prayer Notifications",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Prayer time notifications and live updates"
                    enableVibration(true) // Enable vibration for prayer notifications
                    setShowBadge(true)
                    enableLights(true) // Enable notification light
                    setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null) // Enable sound
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Created enhanced notification channel with sound and vibration")
            }
        }
    }
    
    /**
     * Initialize background prayer notification updates after foreground service is started
     * Simplified to avoid complex dependencies
     */
    private fun initializeBackgroundUpdates() {
        serviceScope.launch {
            try {
                // Initialize PrayerNotificationManager in background to avoid blocking main thread
                if (!PrayerNotificationManager.isInitialized()) {
                    PrayerNotificationManager.initialize(applicationContext)
                    Log.d(TAG, "PrayerNotificationManager initialized in background")
                }
                
                // Log notification capabilities (in background)
                if (PrayerNotificationManager.supportsLiveUpdates()) {
                    Log.d(TAG, "Using Live Update notifications (Android 16+)")
                    Log.d(TAG, "Has promotable characteristics: ${PrayerNotificationManager.hasPromotableCharacteristics()}")
                } else {
                    Log.d(TAG, "Using regular notifications (pre-Android 16)")
                }
                
                // Post initial loading notification (in background)
                PrayerNotificationManager.postPrayerNotification("Prayer Time Tracker Active", 0, true)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in background initialization", e)
            }
        }
    }
    
    /**
     * Start simplified prayer time updates
     * Focuses on basic notification functionality without complex dependencies
     */
    private fun startRealPrayerTimeUpdates() {
        serviceScope.launch {
            var updateCount = 0
            val maxUpdates = 60 // Run for about 1 hour (60 minutes)
            val startTime = System.currentTimeMillis()
            val maxServiceTime = AnrPreventionConfig.MAX_SERVICE_RUNTIME_MS
            
            try {
                while (isServiceRunning && coroutineContext[Job]?.isActive == true && updateCount < maxUpdates) {
                    try {
                        // Check if service has been running too long
                        if (System.currentTimeMillis() - startTime > maxServiceTime) {
                            Log.d(TAG, "Service exceeded max time limit (${maxServiceTime / 1000}s), stopping automatically")
                            break
                        }
                        
                        updateBasicPrayerNotification(updateCount)
                        updateCount++
                        if (AnrPreventionConfig.LOG_SERVICE_LIFECYCLE) {
                            Log.d(TAG, "Prayer update #$updateCount/$maxUpdates")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating prayer notification", e)
                        // Fallback notification on error
                        if (PrayerNotificationManager.isInitialized()) {
                            PrayerNotificationManager.postPrayerNotification("Prayer tracker active", 0, true)
                        }
                    }
                    
                    // Wait before next update (every minute) - on background thread
                    delay(UPDATE_INTERVAL_MS)
                }
                
                // Auto-stop service after max updates or time limit
                val runtimeSeconds = (System.currentTimeMillis() - startTime) / 1000
                Log.d(TAG, "Service stopping automatically - updates: $updateCount, runtime: ${runtimeSeconds}s")
                isServiceRunning = false
                stopForeground(true)
                stopSelf()
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in prayer update loop", e)
            }
            Log.d(TAG, "Prayer time updates stopped")
        }
    }
    
    /**
     * Update basic prayer notification without complex dependencies
     */
    private suspend fun updateBasicPrayerNotification(updateCount: Int) {
        try {
            // Ensure PrayerNotificationManager is initialized before use
            if (!PrayerNotificationManager.isInitialized()) {
                PrayerNotificationManager.initialize(applicationContext)
            }
            
            // Simple notification update
            val message = "Prayer tracker running - Update #$updateCount"
            PrayerNotificationManager.postPrayerNotification(message, updateCount, true)
            
            Log.d(TAG, "Updated basic prayer notification: $message")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in basic updatePrayerNotification", e)
            PrayerNotificationManager.postPrayerNotification("Prayer tracker active", 0, true)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Prayer notification service destroy started")
        
        try {
            // Reset flags FIRST to stop all loops immediately
            isServiceRunning = false
            isInitializing = false
            
            // Cancel all coroutines immediately
            serviceScope.cancel()
            
            // Stop foreground service and remove notification
            try {
                stopForeground(true)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping foreground", e)
            }
            
            // Clean up notifications
            try {
                if (PrayerNotificationManager.isInitialized()) {
                    PrayerNotificationManager.cancelPrayerNotification()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up notifications", e)
            }
            
            Log.d(TAG, "Modern service cleanup completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
        } finally {
            super.onDestroy()
            Log.d(TAG, "Prayer notification service destroyed")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
