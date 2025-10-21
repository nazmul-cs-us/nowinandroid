package com.starception.submission.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.starception.submission.dua.DuaManager;
import com.starception.submission.sensor.ActivityDetectionService;

/**
 * ACTIVITY-BASED DUA SERVICE: Background service for continuous activity detection and dua playing
 * 
 * This service runs in the background to continuously monitor user activity and play
 * appropriate duas when activity changes. It integrates with the existing prayer app
 * architecture and provides seamless dua recommendations.
 */
public class ActivityBasedDuaService extends Service {
    
    private static final String TAG = "ActivityBasedDuaService";
    private static final String ACTION_START_DETECTION = "com.starception.submission.START_ACTIVITY_DETECTION";
    private static final String ACTION_STOP_DETECTION = "com.starception.submission.STOP_ACTIVITY_DETECTION";
    
    private DuaManager duaManager;
    private boolean isServiceRunning = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "ActivityBasedDuaService created");
        
        duaManager = new DuaManager(this);
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
     * Start activity detection and dua playing
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
            duaManager.start();
            isServiceRunning = true;
            Log.i(TAG, "Activity detection and dua playing started");
            
            // Log current activity if available
            ActivityDetectionService.ActivityType currentActivity = duaManager.getCurrentActivity();
            Log.i(TAG, "Current detected activity: " + currentActivity);
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting activity detection", e);
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
        
        isServiceRunning = false;
        super.onDestroy();
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
