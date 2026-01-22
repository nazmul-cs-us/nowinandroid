package com.starception.submission.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import com.starception.submission.MainActivity;
import com.starception.submission.R;
import com.starception.submission.dua.DuaManager;
import com.starception.submission.sensor.ActivityDetectionService;

/**
 * ACTIVITY-BASED DUA SERVICE: Foreground service for continuous activity detection and dua playing
 * 
 * This service runs as a foreground service to continuously monitor user activity and play
 * appropriate duas when activity changes. Running as foreground service ensures Android
 * won't kill it when the app is in the background, allowing reliable driving detection.
 */
public class ActivityBasedDuaService extends Service {
    
    private static final String TAG = "ActivityBasedDuaService";
    private static final String ACTION_START_DETECTION = "com.starception.submission.START_ACTIVITY_DETECTION";
    private static final String ACTION_STOP_DETECTION = "com.starception.submission.STOP_ACTIVITY_DETECTION";
    // Use same channel and ID as PrayerNotificationService to share one notification
    private static final String CHANNEL_ID = "prayer_live_update_channel";
    private static final int NOTIFICATION_ID = 1001;  // Same as PrayerNotificationService
    
    private DuaManager duaManager;
    private boolean isServiceRunning = false;
    private NotificationManager notificationManager;

    // HandlerThread for sensor callbacks - CRITICAL for background operation
    // Android 9+ throttles sensors for background apps, but foreground service handlers work
    private HandlerThread sensorHandlerThread;
    private Handler sensorHandler;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "ActivityBasedDuaService created");
        
        duaManager = new DuaManager(this);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.w(TAG, "Intent is null, stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        String action = intent.getAction();
        Log.i(TAG, "Service started with action: " + action);
        
        switch (action != null ? action : "") {
            case ACTION_START_DETECTION:
                startActivityDetection();
                break;
                
            case ACTION_STOP_DETECTION:
                stopActivityDetection();
                break;
                
            default:
                // Default action: start detection
                startActivityDetection();
                break;
        }
        
        // Return START_STICKY to ensure service restarts if killed by system
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        // This is a started service, not a bound service
        return null;
    }
    
    /**
     * Start activity detection and dua playing as foreground service
     *
     * CRITICAL FOR BACKGROUND SENSOR OPERATION:
     * Android 9+ throttles sensor delivery for background apps. By creating a
     * HandlerThread in this foreground service and passing its Handler to the
     * sensor registration, we ensure sensors continue to deliver at full rate
     * even when the app is closed.
     */
    private void startActivityDetection() {
        if (isServiceRunning) {
            Log.d(TAG, "Activity detection already running");
            return;
        }

        if (duaManager.isRunning()) {
            Log.d(TAG, "DuaManager already running");
            isServiceRunning = true;
            return;
        }

        try {
            // Start as foreground service FIRST to prevent Android from killing it
            Notification notification = createNotification("Monitoring activity...");
            startForeground(NOTIFICATION_ID, notification);
            Log.i(TAG, "✅ Started as foreground service");

            // Create HandlerThread for sensor callbacks WITHIN foreground service context
            // This is CRITICAL for background sensor operation
            if (sensorHandlerThread == null) {
                sensorHandlerThread = new HandlerThread("ForegroundSensorThread", Thread.MAX_PRIORITY);
                sensorHandlerThread.start();
                sensorHandler = new Handler(sensorHandlerThread.getLooper());
                Log.i(TAG, "✅ Created foreground service HandlerThread for sensors");
            }

            // Start DuaManager with our foreground service handler
            // This ensures sensors are registered with foreground context
            duaManager.start(sensorHandler);
            isServiceRunning = true;
            Log.i(TAG, "✅ Activity detection started with foreground service handler");

            // Log current activity if available
            ActivityDetectionService.ActivityType currentActivity = duaManager.getCurrentActivity();
            Log.i(TAG, "Current detected activity: " + currentActivity);

        } catch (Exception e) {
            Log.e(TAG, "Error starting activity detection", e);
            cleanupHandlerThread();
            stopForeground(true);
            stopSelf();
        }
    }

    /**
     * Clean up HandlerThread resources
     */
    private void cleanupHandlerThread() {
        if (sensorHandlerThread != null) {
            sensorHandlerThread.quitSafely();
            try {
                sensorHandlerThread.join(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for handler thread to stop");
            }
            sensorHandlerThread = null;
            sensorHandler = null;
            Log.i(TAG, "HandlerThread cleaned up");
        }
    }
    
    /**
     * Stop activity detection and dua playing
     */
    private void stopActivityDetection() {
        if (!isServiceRunning) {
            Log.d(TAG, "Activity detection not running");
            return;
        }

        try {
            duaManager.stop();
            cleanupHandlerThread();
            isServiceRunning = false;
            stopForeground(true);
            Log.i(TAG, "Activity detection and dua playing stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping activity detection", e);
        }

        stopSelf();
    }
    
    @Override
    public void onDestroy() {
        Log.i(TAG, "Service being destroyed");

        if (duaManager != null) {
            duaManager.cleanup();
        }

        // Clean up HandlerThread
        cleanupHandlerThread();

        stopForeground(true);
        isServiceRunning = false;
        super.onDestroy();
    }

    /**
     * Called when the app is removed from recent tasks (user swipes it away)
     * This ensures the service restarts to maintain activity detection
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.w(TAG, "⚠️ App removed from recent tasks - scheduling service restart");

        try {
            // Schedule service restart using AlarmManager
            Intent restartIntent = new Intent(getApplicationContext(), ActivityBasedDuaService.class);
            restartIntent.setAction(ACTION_START_DETECTION);
            restartIntent.setPackage(getPackageName());

            PendingIntent restartPendingIntent = PendingIntent.getService(
                getApplicationContext(),
                3001,  // Unique request code
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );

            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null) {
                // Restart in 1 second
                alarmManager.set(
                    android.app.AlarmManager.ELAPSED_REALTIME,
                    android.os.SystemClock.elapsedRealtime() + 1000,
                    restartPendingIntent
                );
                Log.i(TAG, "✅ Service restart scheduled for 1 second from now");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to schedule service restart: " + e.getMessage());
        }

        super.onTaskRemoved(rootIntent);
    }
    
    /**
     * Create notification channel for Android O and above
     * Note: Using same channel as PrayerNotificationService (prayer_live_update_channel)
     * so we skip creation here - channel is already created by PrayerNotificationService
     */
    private void createNotificationChannel() {
        // Channel is created by PrayerNotificationService - skip duplicate creation
        Log.d(TAG, "Using existing prayer_live_update_channel from PrayerNotificationService");
    }
    
    /**
     * Create foreground service notification
     */
    private Notification createNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Activity Tracking")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_prayer)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build();
    }
    
    /**
     * Create intent to start activity detection
     */
    public static Intent createStartIntent(android.content.Context context) {
        Intent intent = new Intent(context, ActivityBasedDuaService.class);
        intent.setAction(ACTION_START_DETECTION);
        return intent;
    }
    
    /**
     * Create intent to stop activity detection
     */
    public static Intent createStopIntent(android.content.Context context) {
        Intent intent = new Intent(context, ActivityBasedDuaService.class);
        intent.setAction(ACTION_STOP_DETECTION);
        return intent;
    }
    
    /**
     * Check if service is currently running activity detection
     */
    public boolean isRunning() {
        return isServiceRunning && duaManager != null && duaManager.isRunning();
    }
}
