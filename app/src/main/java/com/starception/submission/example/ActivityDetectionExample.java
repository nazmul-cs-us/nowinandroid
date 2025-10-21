package com.starception.submission.example;

import android.content.Context;
import android.util.Log;

import com.starception.submission.sensor.ActivityDetectionService;
import com.starception.submission.dua.DuaManager;
import com.starception.submission.util.ActivityBasedDuaHelper;

/**
 * EXAMPLE: How to use the Activity-Based Dua System
 * 
 * This class demonstrates different ways to integrate activity detection
 * and dua playing into your application.
 */
public class ActivityDetectionExample {
    
    private static final String TAG = "ActivityDetectionExample";
    
    private Context context;
    private DuaManager duaManager;
    private ActivityDetectionService activityDetectionService;
    
    public ActivityDetectionExample(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * EXAMPLE 1: Simple Service Integration
     * 
     * The easiest way to start activity-based dua playing.
     * This will automatically detect activities and play appropriate duas.
     */
    public void startSimpleActivityDetection() {
        Log.i(TAG, "Starting simple activity-based dua system");
        
        // Start the background service
        ActivityBasedDuaHelper.startActivityDetection(context);
        
        Log.i(TAG, "Activity detection service started. The system will now automatically:");
        Log.i(TAG, "- Detect user activities (walking, driving, etc.)");
        Log.i(TAG, "- Play appropriate duas when activity changes");
        Log.i(TAG, "- Log dua information for reference");
    }
    
    /**
     * EXAMPLE 2: Advanced Custom Integration
     * 
     * For more control over activity detection and dua management.
     */
    public void startAdvancedActivityDetection() {
        Log.i(TAG, "Starting advanced activity detection with custom callbacks");
        
        // Create activity detection service
        activityDetectionService = new ActivityDetectionService(context);
        
        // Check permissions first
        if (!activityDetectionService.hasRequiredPermissions()) {
            Log.w(TAG, "Required permissions not granted. Please request:");
            Log.w(TAG, "- ACCESS_FINE_LOCation or ACCESS_COARSE_LOCATION");
            Log.w(TAG, "- ACTIVITY_RECOGNITION (Android 10+)");
            return;
        }
        
        // Start with custom callback
        activityDetectionService.startDetection(new ActivityDetectionService.ActivityChangeCallback() {
            @Override
            public void onActivityChanged(ActivityDetectionService.ActivityType newActivity, 
                                        ActivityDetectionService.ActivityType previousActivity) {
                
                Log.i(TAG, "Activity changed: " + previousActivity + " -> " + newActivity);
                
                // Custom handling for specific activities
                switch (newActivity) {
                    case DRIVING:
                        Log.i(TAG, "Playing travel dua for safe driving");
                        break;
                    case ON_PHONE:
                        Log.i(TAG, "Playing protection dua for mindful phone usage");
                        break;
                    case WALKING:
                        Log.i(TAG, "Playing walking dua for safe travel");
                        break;
                    case RUNNING:
                        Log.i(TAG, "Playing strength dua for physical activity");
                        break;
                    case STATIONARY:
                        Log.i(TAG, "Playing contemplation dua for stillness");
                        break;
                    default:
                        Log.i(TAG, "Playing general dua for unknown activity");
                        break;
                }
            }
        });
        
        Log.i(TAG, "Advanced activity detection started with custom callback");
    }
    
    /**
     * EXAMPLE 3: Dua Manager Integration
     * 
     * For custom dua management and audio control.
     */
    public void startDuaManagerOnly() {
        Log.i(TAG, "Starting DuaManager for custom dua handling");
        
        // Create and configure dua manager
        duaManager = new DuaManager(context);
        
        // Start activity detection through dua manager
        duaManager.start();
        
        Log.i(TAG, "DuaManager started. You can also manually play duas:");
        Log.i(TAG, "duaManager.playDuaManually(ActivityType.WALKING)");
    }
    
    /**
     * Stop all activity detection
     */
    public void stopActivityDetection() {
        Log.i(TAG, "Stopping activity detection");
        
        // Stop the service
        ActivityBasedDuaHelper.stopActivityDetection(context);
        
        // Stop individual components
        if (activityDetectionService != null) {
            activityDetectionService.stopDetection();
        }
        
        if (duaManager != null) {
            duaManager.cleanup();
        }
        
        Log.i(TAG, "Activity detection stopped");
    }
    
    /**
     * Get current detected activity
     */
    public ActivityDetectionService.ActivityType getCurrentActivity() {
        if (activityDetectionService != null && activityDetectionService.isRunning()) {
            return activityDetectionService.getCurrentActivity();
        }
        
        if (duaManager != null) {
            return duaManager.getCurrentActivity();
        }
        
        return ActivityDetectionService.ActivityType.UNKNOWN;
    }
    
    /**
     * Check if system is running
     */
    public boolean isRunning() {
        if (activityDetectionService != null) {
            return activityDetectionService.isRunning();
        }
        
        if (duaManager != null) {
            return duaManager.isRunning();
        }
        
        return false;
    }
    
    /**
     * Example of how to integrate this into an Activity
     */
    public static void integrateIntoActivity(Context context) {
        ActivityDetectionExample example = new ActivityDetectionExample(context);
        
        // Start simple detection (recommended for most use cases)
        example.startSimpleActivityDetection();
        
        // Or use advanced integration for custom handling
        // example.startAdvancedActivityDetection();
        
        // Check status
        Log.i(TAG, "System running: " + example.isRunning());
        Log.i(TAG, "Current activity: " + example.getCurrentActivity());
    }
}

/*
INTEGRATION NOTES:

1. PERMISSIONS REQUIRED:
   Add these to your AndroidManifest.xml (already added):
   <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
   <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
   <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />

2. USAGE IN ACTIVITIES:
   
   // In your Activity's onCreate() or onResume():
   ActivityDetectionExample.integrateIntoActivity(this);
   
   // To stop when done:
   ActivityBasedDuaHelper.stopActivityDetection(this);

3. CUSTOMIZATION:
   
   To add your own audio files:
   - Place MP3/WAV files in res/raw/
   - Update DuaManager.initializeDuaMap() with your audio resources
   
   To modify detection thresholds:
   - Edit constants in ActivityDetectionService.java
   - Adjust WALKING_THRESHOLD, RUNNING_THRESHOLD, etc.
   
   To add new activities:
   - Add enum values to ActivityType
   - Update detection algorithm in determineActivity()
   - Add corresponding duas in DuaManager

4. TESTING:
   
   Check logs for activity detection:
   adb logcat | grep -E "(ActivityDetection|DuaManager)"
   
   Monitor permission status:
   Check Android settings for location and activity recognition permissions
*/
