package com.starception.submission.sensor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import android.util.Log;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ACTIVITY DETECTION SERVICE: Multi-sensor activity recognition using accelerometer, gyroscope, and GPS
 * 
 * This service combines multiple sensors to accurately detect user activity states:
 * - Accelerometer: Detects movement and vibration patterns
 * - Gyroscope: Detects rotational movement and orientation changes
 * - GPS: Detects speed and travel patterns
 * 
 * ACTIVITIES DETECTED:
 * - STATIONARY: User is at rest (sitting, standing still)
 * - WALKING: User is walking at normal pace
 * - RUNNING: User is running or jogging
 * - DRIVING: User is in a vehicle
 * - UNKNOWN: Activity cannot be determined
 */
public class ActivityDetectionService implements SensorEventListener, LocationListener {
    
    private static final String TAG = "ActivityDetection";
    
    // Sensor sampling frequencies
    private static final int SENSOR_DELAY_US = SensorManager.SENSOR_DELAY_UI; // ~60Hz
    private static final long LOCATION_UPDATE_INTERVAL = 1000; // 1 second
    private static final float LOCATION_UPDATE_DISTANCE = 1.0f; // 1 meter
    
    // Activity detection thresholds - Improved to distinguish walking from running and phone pickup
    private static final double WALKING_VARIANCE_MIN = 0.8; // Minimum variance for walking
    private static final double WALKING_VARIANCE_MAX = 2.5; // Maximum variance for walking
    private static final double WALKING_GYRO_MIN = 0.4; // Minimum gyro for walking (arm swing)
    private static final double WALKING_GYRO_MAX = 1.8; // Maximum gyro for walking
    private static final double WALKING_SPEED_MIN = 0.3; // m/s (1 km/h) - minimum speed to distinguish from phone pickup
    private static final double WALKING_SPEED_MAX = 2.5; // m/s (~9 km/h, typical walking speed is 1.4 m/s)

    private static final double RUNNING_VARIANCE_MIN = 2.0; // Minimum variance for running (higher impact)
    private static final double RUNNING_GYRO_MIN = 1.5; // Minimum gyro for running (more rotational movement)
    private static final double RUNNING_SPEED_MIN = 2.0; // m/s (~7 km/h, typical running starts at 6 km/h)

    private static final double DRIVING_SPEED_THRESHOLD = 5.0; // m/s (18 km/h)
    private static final double STATIONARY_VARIANCE_THRESHOLD = 0.15; // Slightly higher for better stability
    private static final double STATIONARY_ACCEL_THRESHOLD = 0.3; // Lower threshold for stationary

    // Phone usage detection thresholds
    private static final double ON_PHONE_VARIANCE_THRESHOLD = 0.4; // Moderate variance from phone usage
    private static final double ON_PHONE_GYRO_THRESHOLD = 0.3; // Moderate rotation from phone handling
    
    // Phone holding detection - when phone is held but still
    private static final double GRAVITY_ACCEL = 9.8; // Standard gravity
    private static final double HELD_PHONE_ACCEL_MIN = 8.5; // Minimum accel when phone is held at angle (more restrictive)
    private static final double HELD_PHONE_ACCEL_MAX = 11.0; // Maximum accel when phone is held
    private static final double FLAT_SURFACE_TOLERANCE = 1.0; // Tolerance for flat surface detection
    
    // Better detection for held phone vs flat surface
    private static final double HELD_VS_FLAT_GRAVITY_THRESHOLD = 0.5; // Difference from perfect gravity when held
    private static final double MICRO_MOVEMENT_THRESHOLD = 0.08; // Very small movements indicate holding (hand tremor)
    private static final double PERFECT_FLAT_TOLERANCE = 0.15; // Very strict tolerance for truly flat surface (must be almost exactly 9.8)
    
    // Phone orientation detection thresholds
    private static final double PORTRAIT_ACCEL_Z_MIN = 8.0; // Z-axis dominates when phone is vertical
    private static final double LANDSCAPE_ACCEL_X_MIN = 8.0; // X-axis dominates when phone is horizontal
    private static final double FLAT_ACCEL_Y_MIN = 8.0; // Y-axis dominates when phone is flat
    private static final double ORIENTATION_THRESHOLD = 0.6; // Ratio threshold for clear orientation
    
    // Pocket detection - phone vertical with rhythmic walking motion
    private static final double POCKET_GYRO_MAX = 0.8; // Less rotation than active use
    private static final double POCKET_VARIANCE_MIN = 0.5; // Rhythmic walking pattern
    
    // Data collection window (seconds)
    private static final int DATA_WINDOW_SIZE = 3; // 3 seconds of data
    private static final int ANALYSIS_INTERVAL = 2000; // Analyze every 2 seconds
    
    // Sensors
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private LocationManager locationManager;
    
    // Data collection
    private ConcurrentLinkedQueue<AccelerometerData> accelData;
    private ConcurrentLinkedQueue<GyroscopeData> gyroData;
    private ConcurrentLinkedQueue<LocationData> locationData;
    
    // Current phone orientation
    private PhoneOrientation currentOrientation = PhoneOrientation.UNKNOWN;
    
    // Current state
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private ActivityType currentActivity = ActivityType.UNKNOWN;
    private long lastActivityChange = 0;
    
    // Activity stability tracking - prevent false detections from brief movements
    private ActivityType pendingActivity = ActivityType.UNKNOWN;
    private long pendingActivityStartTime = 0;
    private static final long ACTIVITY_CONFIRMATION_TIME = 1500; // 1.5 seconds minimum
    
    // Hysteresis: Make activities "sticky" to prevent rapid flipping
    private static final int STABLE_DETECTION_COUNT = 3; // Need 3 consecutive detections (6 seconds)
    private int consecutiveDetectionCount = 0;
    private ActivityType lastDetectedActivity = ActivityType.UNKNOWN;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    // Callbacks
    private ActivityChangeCallback callback;
    private Context context;
    
    // Data structures for sensor data
    private static class AccelerometerData {
        float x, y, z;
        long timestamp;
        
        AccelerometerData(float x, float y, float z, long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
        }
        
        double getMagnitude() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }
    
    private static class GyroscopeData {
        float x, y, z;
        long timestamp;
        
        GyroscopeData(float x, float y, float z, long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
        }
        
        double getMagnitude() {
            return Math.sqrt(x * x + y * y + z * z);
        }
    }
    
    private static class LocationData {
        double latitude;
        double longitude;
        float speed; // m/s
        long timestamp;
        
        LocationData(double lat, double lng, float speed, long timestamp) {
            this.latitude = lat;
            this.longitude = lng;
            this.speed = speed;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Activity types that can be detected
     */
    public enum ActivityType {
        STATIONARY,  // User is at rest
        ON_PHONE,    // User is actively using the phone
        WALKING,     // User is walking
        RUNNING,     // User is running
        DRIVING,     // User is in a vehicle
        UNKNOWN      // Activity cannot be determined
    }
    
    /**
     * Phone orientation/position detection for improved accuracy
     */
    public enum PhoneOrientation {
        PORTRAIT,      // Phone held vertically (typical phone use)
        LANDSCAPE,     // Phone held horizontally (watching videos)
        FLAT_UP,       // Phone lying flat, screen up (on table)
        FLAT_DOWN,     // Phone lying flat, screen down
        IN_POCKET,     // Phone in pocket (vertical, walking motion)
        UNKNOWN        // Cannot determine orientation
    }
    
    /**
     * Callback interface for activity changes
     */
    public interface ActivityChangeCallback {
        void onActivityChanged(ActivityType newActivity, ActivityType previousActivity);
    }
    
    public ActivityDetectionService(Context context) {
        this.context = context.getApplicationContext();
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        
        // Initialize sensors
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        
        // Initialize data collections
        this.accelData = new ConcurrentLinkedQueue<>();
        this.gyroData = new ConcurrentLinkedQueue<>();
        this.locationData = new ConcurrentLinkedQueue<>();
        
        Log.i(TAG, "ActivityDetectionService initialized");
    }
    
    /**
     * Check if all required permissions are granted
     */
    public boolean hasRequiredPermissions() {
        boolean hasLocationPermission = ContextCompat.checkSelfPermission(context, 
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, 
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        
        boolean hasActivityRecognition = ContextCompat.checkSelfPermission(context, 
                Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
        
        return hasLocationPermission && hasActivityRecognition;
    }
    
    /**
     * Start activity detection
     */
    public void startDetection(ActivityChangeCallback callback) {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Required permissions not granted for activity detection");
            return;
        }
        
        if (isRunning.get()) {
            Log.w(TAG, "Activity detection already running");
            return;
        }
        
        this.callback = callback;
        isRunning.set(true);
        
        // Initialize timing variables
        lastActivityChange = System.currentTimeMillis();
        
        // Register sensor listeners
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SENSOR_DELAY_US);
            Log.i(TAG, "Accelerometer registered");
        }
        
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SENSOR_DELAY_US);
            Log.i(TAG, "Gyroscope registered");
        }
        
        // Register location listener
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_UPDATE_INTERVAL,
                LOCATION_UPDATE_DISTANCE,
                this
            );
            Log.i(TAG, "GPS location updates registered");
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
        }
        
        // Start periodic analysis
        startPeriodicAnalysis();
        
        Log.i(TAG, "Activity detection started");
    }
    
    /**
     * Stop activity detection
     */
    public void stopDetection() {
        if (!isRunning.get()) {
            return;
        }
        
        isRunning.set(false);
        
        // Unregister sensor listeners
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);
        
        // Clear data
        accelData.clear();
        gyroData.clear();
        locationData.clear();
        
        Log.i(TAG, "Activity detection stopped");
    }
    
    /**
     * Start periodic analysis of collected data
     */
    private void startPeriodicAnalysis() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRunning.get()) {
                    analyzeCurrentActivity();
                    startPeriodicAnalysis(); // Schedule next analysis
                }
            }
        }, ANALYSIS_INTERVAL);
    }
    
    /**
     * Analyze collected sensor data to determine current activity
     */
    private void analyzeCurrentActivity() {
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - (DATA_WINDOW_SIZE * 1000);
        
        // Filter recent accelerometer data
        double avgAccel = calculateAverageAcceleration(windowStart);
        double accelVariance = calculateAccelerationVariance(windowStart);
        
        // Filter recent gyroscope data
        double avgGyro = calculateAverageGyroscope(windowStart);
        
        // Get recent location data
        double maxSpeed = getMaxSpeedInWindow(windowStart);
        
        // Detect phone orientation for better activity classification
        PhoneOrientation orientation = detectPhoneOrientation(windowStart);
        currentOrientation = orientation;
        
        // Determine activity based on thresholds AND orientation
        ActivityType detectedActivity = determineActivity(avgAccel, accelVariance, avgGyro, maxSpeed, orientation);
        
        // HYSTERESIS: Make activities sticky to prevent rapid flipping
        // Only change if we get 3 consecutive different detections
        if (detectedActivity == lastDetectedActivity) {
            consecutiveDetectionCount++;
        } else {
            consecutiveDetectionCount = 1;
            lastDetectedActivity = detectedActivity;
        }
        
        // Don't change activity unless we have stable detections OR immediate transitions
        boolean isStableDetection = (consecutiveDetectionCount >= STABLE_DETECTION_COUNT);
        boolean isImmediateTransition = (detectedActivity == ActivityType.DRIVING || 
                                         (currentActivity == ActivityType.STATIONARY && detectedActivity != ActivityType.WALKING));
        
        // IMPROVED: Require confirmation for WALKING to prevent false positives
        boolean needsConfirmation = (detectedActivity == ActivityType.WALKING && maxSpeed < 0.1);
        
        if (needsConfirmation && !isStableDetection) {
            // Walking needs both time confirmation AND stable detections
            if (detectedActivity != pendingActivity) {
                pendingActivity = detectedActivity;
                pendingActivityStartTime = currentTime;
                Log.d(TAG, "Pending activity: " + detectedActivity + " (count: " + consecutiveDetectionCount + 
                      "/" + STABLE_DETECTION_COUNT + ")");
                return;
            } else {
                long timePending = currentTime - pendingActivityStartTime;
                if (timePending < ACTIVITY_CONFIRMATION_TIME) {
                    Log.d(TAG, "Confirming: " + detectedActivity + " (time: " + timePending + "ms, count: " + 
                          consecutiveDetectionCount + "/" + STABLE_DETECTION_COUNT + ")");
                    return;
                }
            }
        } else if (!isStableDetection && !isImmediateTransition) {
            // Not stable yet, wait for more consistent detections
            Log.d(TAG, "Waiting for stable detection: " + detectedActivity + " (count: " + 
                  consecutiveDetectionCount + "/" + STABLE_DETECTION_COUNT + ")");
            return;
        } else {
            pendingActivity = ActivityType.UNKNOWN;
            pendingActivityStartTime = 0;
        }
        
        // Update activity if changed
        if (detectedActivity != currentActivity) {
            ActivityType previousActivity = currentActivity;
            currentActivity = detectedActivity;
            lastActivityChange = currentTime;
            pendingActivity = ActivityType.UNKNOWN;
            consecutiveDetectionCount = 0; // Reset for next transition
            
            Log.i(TAG, "Activity changed: " + previousActivity + " -> " + currentActivity + 
                  " (Accel: " + String.format("%.2f", avgAccel) + 
                  ", Gyro: " + String.format("%.2f", avgGyro) + 
                  ", Speed: " + String.format("%.2f", maxSpeed) + " m/s" +
                  ", Orientation: " + orientation + ")");
            
            if (callback != null) {
                callback.onActivityChanged(detectedActivity, previousActivity);
            }
        }
    }
    
    /**
     * Calculate average acceleration magnitude in the time window
     */
    private double calculateAverageAcceleration(long windowStart) {
        double total = 0;
        int count = 0;
        
        for (AccelerometerData data : accelData) {
            if (data.timestamp >= windowStart) {
                total += data.getMagnitude();
                count++;
            }
        }
        
        return count > 0 ? total / count : 0;
    }
    
    /**
     * Calculate acceleration variance to detect movement patterns
     */
    private double calculateAccelerationVariance(long windowStart) {
        double avg = calculateAverageAcceleration(windowStart);
        double variance = 0;
        int count = 0;
        
        for (AccelerometerData data : accelData) {
            if (data.timestamp >= windowStart) {
                double diff = data.getMagnitude() - avg;
                variance += diff * diff;
                count++;
            }
        }
        
        return count > 0 ? variance / count : 0;
    }
    
    /**
     * Calculate average gyroscope magnitude in the time window
     */
    private double calculateAverageGyroscope(long windowStart) {
        double total = 0;
        int count = 0;
        
        for (GyroscopeData data : gyroData) {
            if (data.timestamp >= windowStart) {
                total += data.getMagnitude();
                count++;
            }
        }
        
        return count > 0 ? total / count : 0;
    }
    
    /**
     * Get maximum speed in the time window
     */
    private double getMaxSpeedInWindow(long windowStart) {
        double maxSpeed = 0;
        
        for (LocationData data : locationData) {
            if (data.timestamp >= windowStart && data.speed > maxSpeed) {
                maxSpeed = data.speed;
            }
        }
        
        return maxSpeed;
    }
    
    /**
     * Determine activity type based on sensor data AND phone orientation
     *
     * Priority order (based on real sensor data analysis):
     * 1. DRIVING - High speed (most reliable)
     * 2. RUNNING - High variance + high gyro + higher speed
     * 3. WALKING - Moderate variance + moderate gyro + low speed + orientation check
     * 4. STATIONARY - Gyro ≤ 0.003 (sensor noise) + variance ≤ 0.005 + flat surface
     * 5. ON_PHONE - Gyro > 0.003 (hand tremors) OR held at angle with movement + portrait/landscape orientation
     * 6. ON_PHONE (fallback) - Any other movement patterns
     */
    private ActivityType determineActivity(double avgAccel, double accelVariance, double avgGyro, double maxSpeed, PhoneOrientation orientation) {
        Log.d(TAG, String.format("Activity Analysis - Accel: %.2f, Variance: %.2f, Gyro: %.2f, Speed: %.2f m/s, Orientation: %s",
                avgAccel, accelVariance, avgGyro, maxSpeed, orientation));

        // 1. High speed indicates DRIVING (most reliable indicator)
        if (maxSpeed > DRIVING_SPEED_THRESHOLD) {
            Log.d(TAG, "Detected: DRIVING (speed: " + String.format("%.2f", maxSpeed) + " m/s / " + String.format("%.1f", maxSpeed * 3.6) + " km/h)");
            return ActivityType.DRIVING;
        }

        // 2. RUNNING detection - High impact, high rotation, faster speed
        // Running has higher variance (more impact) and higher gyro (more body rotation)
        // Must meet ALL criteria: high variance AND high gyro (OR high speed if GPS available)
        boolean hasRunningVariance = accelVariance >= RUNNING_VARIANCE_MIN;
        boolean hasRunningGyro = avgGyro >= RUNNING_GYRO_MIN;
        boolean hasRunningSpeed = maxSpeed >= RUNNING_SPEED_MIN && maxSpeed < DRIVING_SPEED_THRESHOLD;

        // Running if: (high variance AND high gyro) OR (high speed with significant movement)
        if ((hasRunningVariance && hasRunningGyro) || (hasRunningSpeed && accelVariance >= RUNNING_VARIANCE_MIN)) {
            Log.d(TAG, "Detected: RUNNING (variance: " + String.format("%.2f", accelVariance) +
                       ", gyro: " + String.format("%.2f", avgGyro) +
                       ", speed: " + String.format("%.2f", maxSpeed) + " m/s)");
            return ActivityType.RUNNING;
        }

        // 3. WALKING detection - Moderate impact, moderate rotation, low speed + orientation check
        // Walking has rhythmic but moderate variance and gyro from natural gait
        // Works with or without GPS - uses variance pattern as primary indicator
        // IMPROVED: Use phone orientation to distinguish walking from phone use
        boolean hasWalkingVariance = accelVariance >= WALKING_VARIANCE_MIN && accelVariance < WALKING_VARIANCE_MAX;
        boolean hasWalkingGyro = avgGyro >= WALKING_GYRO_MIN && avgGyro < WALKING_GYRO_MAX;
        boolean hasWalkingSpeed = maxSpeed >= WALKING_SPEED_MIN && maxSpeed < WALKING_SPEED_MAX;
        boolean hasGPSData = maxSpeed > 0.1; // GPS is providing data
        
        // Check if phone orientation suggests walking (in pocket or in hand while moving)
        // Phone in pocket: typically portrait orientation with less gyro rotation
        // Phone being used while walking: portrait/landscape with more gyro
        boolean orientationSuggestsWalking = (orientation == PhoneOrientation.PORTRAIT || 
                                              orientation == PhoneOrientation.IN_POCKET ||
                                              orientation == PhoneOrientation.UNKNOWN); // Unknown could be pocket

        // Walking detection with or without GPS:
        // WITH GPS: Use speed + variance (most reliable) - REQUIRED both variance AND gyro for confidence
        // WITHOUT GPS: Use variance + gyro pattern (fallback for indoors)
        if (hasWalkingVariance && orientationSuggestsWalking) {
            if (hasGPSData) {
                // GPS available - require BOTH speed AND gyro for more confidence
                // This prevents "on phone while standing" from being detected as walking
                if (hasWalkingSpeed && hasWalkingGyro) {
                    Log.d(TAG, "Detected: WALKING (GPS) (variance: " + String.format("%.2f", accelVariance) +
                               ", gyro: " + String.format("%.2f", avgGyro) +
                               ", speed: " + String.format("%.2f", maxSpeed) + " m/s" +
                               ", orientation: " + orientation + ")");
                    return ActivityType.WALKING;
                }
            } else {
                // No GPS - use sensor pattern (variance + gyro)
                // Only if gyro is in walking range to avoid false positives
                if (hasWalkingGyro) {
                    Log.d(TAG, "Detected: WALKING (sensors) (variance: " + String.format("%.2f", accelVariance) +
                               ", gyro: " + String.format("%.2f", avgGyro) +
                               ", speed: N/A" +
                               ", orientation: " + orientation + ")");
                    return ActivityType.WALKING;
                }
            }
        }

        // 4. STATIONARY (check BEFORE on_phone) - Phone on flat surface, completely still
        // ONLY stationary if phone is on a flat surface with ZERO movement
        // Based on real sensor data: table = gyro ~0.0005-0.002, variance ~0.0000-0.003
        // ROBUST: If sensors show PERFECT stillness, assume STATIONARY regardless of orientation
        // (Some devices have different coordinate systems, so we prioritize sensor stillness)
        boolean meetsStationarySensorCriteria = (accelVariance <= 0.005 && 
                                                  avgGyro <= 0.003 && 
                                                  Math.abs(avgAccel - GRAVITY_ACCEL) <= PERFECT_FLAT_TOLERANCE);
        
        // IMPROVED: If phone is PERFECTLY still, it's on a surface (handles camera bumps!)
        // Camera bumps cause phone to tilt, so we can't rely on orientation alone
        // Instead, use VERY strict stillness criteria: variance < 0.002, gyro < 0.002
        boolean isPerfectlyStill = (accelVariance <= 0.0015 && avgGyro <= 0.0015);
        
        if (isPerfectlyStill && meetsStationarySensorCriteria) {
            Log.d(TAG, "Detected: STATIONARY (perfectly still on surface - accel: " + String.format("%.2f", avgAccel) +
                       ", variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) +
                       ", orientation: " + orientation + ")");
            return ActivityType.STATIONARY;
        }
        
        // If sensors show some movement (even tiny), it's likely being held
        if (meetsStationarySensorCriteria && !isPerfectlyStill) {
            Log.d(TAG, "Detected: ON_PHONE (slight movement detected - orientation: " + orientation +
                       ", variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }

        // 5. ON_PHONE - Phone is being held or used
        // IMPROVED: Use orientation to confirm phone is being actively used
        // Portrait/Landscape/FlatUp orientation strongly suggests phone is being held and used
        // FLAT_UP added: When reading while holding phone flat (common use case)
        boolean orientationSuggestsPhoneUse = (orientation == PhoneOrientation.PORTRAIT || 
                                               orientation == PhoneOrientation.LANDSCAPE ||
                                               orientation == PhoneOrientation.FLAT_UP);
        
        // Holding a phone (even steady) creates micro-movements greater than sensor noise
        // Check if phone is being held at an angle (not flat) AND has movement
        if (avgAccel >= HELD_PHONE_ACCEL_MIN && avgAccel <= HELD_PHONE_ACCEL_MAX &&
            (avgGyro > 0.003 || accelVariance > 0.005)) {
            // If orientation confirms phone use, definitely ON_PHONE
            if (orientationSuggestsPhoneUse) {
                Log.d(TAG, "Detected: ON_PHONE (held + " + orientation + " - accel: " + String.format("%.2f", avgAccel) +
                           ", variance: " + String.format("%.4f", accelVariance) +
                           ", gyro: " + String.format("%.4f", avgGyro) + ")");
                return ActivityType.ON_PHONE;
            }
            // Otherwise might be walking with phone, continue checking
        }

        // If there's gyro movement above sensor noise AND portrait/landscape orientation
        // Hand tremors typically create gyro > 0.003 (above table noise of ~0.002)
        if (avgGyro > 0.003 && orientationSuggestsPhoneUse) {
            Log.d(TAG, "Detected: ON_PHONE (" + orientation + " with micro-movements - variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }

        // Default to ON_PHONE for any other low movement WITH phone-use orientation
        // This catches edge cases like reading while phone is held still
        if ((accelVariance <= STATIONARY_VARIANCE_THRESHOLD || avgGyro <= 0.15) && orientationSuggestsPhoneUse) {
            Log.d(TAG, "Detected: ON_PHONE (" + orientation + " holding or low movement - variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }

        // Any other movement should be ON_PHONE (phone handling, gestures, etc.)
        if (avgGyro > 0.15 || accelVariance > STATIONARY_VARIANCE_THRESHOLD) {
            Log.d(TAG, "Detected: ON_PHONE (device handling - variance: " + String.format("%.2f", accelVariance) +
                       ", gyro: " + String.format("%.2f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }

        // ROBUST FALLBACK: If we reach here, phone is likely being held/used
        // It's not walking, running, driving, or truly stationary
        // Better to show ON_PHONE than UNKNOWN (more useful feedback)
        if (avgGyro > 0 || accelVariance > 0) {
            Log.d(TAG, "Detected: ON_PHONE (fallback - has movement but unclear pattern - variance: " + 
                       String.format("%.2f", accelVariance) + ", gyro: " + String.format("%.2f", avgGyro) + 
                       ", orientation: " + orientation + ")");
            return ActivityType.ON_PHONE;
        }
        
        // Final fallback - should almost never reach here
        Log.d(TAG, "Detected: UNKNOWN (no movement detected - variance: " + String.format("%.2f", accelVariance) +
                   ", gyro: " + String.format("%.2f", avgGyro) + ")");
        return ActivityType.UNKNOWN;
    }
    
    /**
     * Clean old data to prevent memory buildup
     */
    private void cleanOldData() {
        long cutoff = System.currentTimeMillis() - (DATA_WINDOW_SIZE * 2000); // Double the window
        
        accelData.removeIf(data -> data.timestamp < cutoff);
        gyroData.removeIf(data -> data.timestamp < cutoff);
        locationData.removeIf(data -> data.timestamp < cutoff);
    }
    
    // SensorEventListener implementation
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isRunning.get()) return;
        
        long timestamp = System.currentTimeMillis();
        
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                accelData.offer(new AccelerometerData(
                    event.values[0], event.values[1], event.values[2], timestamp));
                break;
                
            case Sensor.TYPE_GYROSCOPE:
                gyroData.offer(new GyroscopeData(
                    event.values[0], event.values[1], event.values[2], timestamp));
                break;
        }
        
        // Clean old data periodically
        if (accelData.size() > 300) { // ~5 seconds at 60Hz
            cleanOldData();
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Sensor accuracy changed: " + sensor.getName() + " = " + accuracy);
    }
    
    // LocationListener implementation
    @Override
    public void onLocationChanged(Location location) {
        if (!isRunning.get()) return;
        
        long timestamp = System.currentTimeMillis();
        locationData.offer(new LocationData(
            location.getLatitude(),
            location.getLongitude(),
            location.getSpeed(),
            timestamp
        ));
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "Location provider status changed: " + provider + " = " + status);
    }
    
    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Location provider enabled: " + provider);
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Location provider disabled: " + provider);
    }
    
    /**
     * Detect phone orientation based on accelerometer data
     * Uses the dominant axis to determine how the phone is positioned
     */
    private PhoneOrientation detectPhoneOrientation(long windowStart) {
        if (accelData.isEmpty()) {
            return PhoneOrientation.UNKNOWN;
        }
        
        // Calculate average acceleration on each axis (keep signs!)
        double avgX = 0, avgY = 0, avgZ = 0;
        int count = 0;
        
        for (AccelerometerData data : accelData) {
            if (data.timestamp >= windowStart) {
                avgX += data.x;  // Keep sign for direction
                avgY += data.y;  // Keep sign for direction
                avgZ += data.z;  // Keep sign for direction
                count++;
            }
        }
        
        if (count == 0) {
            return PhoneOrientation.UNKNOWN;
        }
        
        avgX /= count;
        avgY /= count;
        avgZ /= count;
        
        // Use absolute values for comparison
        double absX = Math.abs(avgX);
        double absY = Math.abs(avgY);
        double absZ = Math.abs(avgZ);
        
        // DEBUG: Log actual axis values to diagnose orientation issues
        Log.d(TAG, String.format("Orientation Axes - X:%.2f, Y:%.2f, Z:%.2f (abs: X:%.2f, Y:%.2f, Z:%.2f)", 
              avgX, avgY, avgZ, absX, absY, absZ));
        
        // Determine dominant axis (check FLAT first - most reliable)
        // Y-axis dominant: Phone flat (on table)
        // Z-axis dominant: Phone vertical (portrait)
        // X-axis dominant: Phone horizontal (landscape)
        
        // FLAT detection - Y-axis dominant (RELAXED threshold for better detection)
        if (absY > 7.0 && absY > absX * 1.2 && absY > absZ * 1.2) {
            Log.d(TAG, "Orientation: FLAT (Y-axis dominant: " + String.format("%.2f", absY) + ")");
            return avgY > 0 ? PhoneOrientation.FLAT_UP : PhoneOrientation.FLAT_DOWN;
        } 
        // PORTRAIT detection - Z-axis dominant
        else if (absZ > 8.0 && absZ > absX * 1.3 && absZ > absY * 1.3) {
            return PhoneOrientation.PORTRAIT;
        } 
        // LANDSCAPE detection - X-axis dominant  
        else if (absX > 8.0 && absX > absY * 1.3 && absX > absZ * 1.3) {
            return PhoneOrientation.LANDSCAPE;
        }
        
        return PhoneOrientation.UNKNOWN;
    }
    
    /**
     * Get current detected activity
     */
    public ActivityType getCurrentActivity() {
        return currentActivity;
    }
    
    /**
     * Get current phone orientation
     */
    public PhoneOrientation getCurrentOrientation() {
        return currentOrientation;
    }
    
    /**
     * Get time since last activity change
     */
    public long getTimeSinceLastChange() {
        return System.currentTimeMillis() - lastActivityChange;
    }
    
    /**
     * Check if service is currently running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
}
