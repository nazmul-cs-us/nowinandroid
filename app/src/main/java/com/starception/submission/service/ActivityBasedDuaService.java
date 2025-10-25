package com.starception.submission.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
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
    private static final String CHANNEL_ID = "activity_detection_channel";
    private static final int NOTIFICATION_ID = 3001;
    
    private DuaManager duaManager;
    private boolean isServiceRunning = false;
    private NotificationManager notificationManager;
    
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
            // Start as foreground service to prevent Android from killing it
            Notification notification = createNotification("Monitoring activity...");
            startForeground(NOTIFICATION_ID, notification);
            Log.i(TAG, "✅ Started as foreground service");
            
            duaManager.start();
            isServiceRunning = true;
            Log.i(TAG, "Activity detection and dua playing started");
            
            // Log current activity if available
            ActivityDetectionService.ActivityType currentActivity = duaManager.getCurrentActivity();
            Log.i(TAG, "Current detected activity: " + currentActivity);
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting activity detection", e);
            stopForeground(true);
            stopSelf();
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
        
        stopForeground(true);
        isServiceRunning = false;
        super.onDestroy();
    }
    
    /**
     * Create notification channel for Android O and above
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Activity Detection",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitors your activity for travel dua");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "Notification channel created");
        }
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
