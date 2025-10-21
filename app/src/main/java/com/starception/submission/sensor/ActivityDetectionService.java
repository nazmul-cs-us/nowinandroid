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
    
    // Current state
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private ActivityType currentActivity = ActivityType.UNKNOWN;
    private long lastActivityChange = 0;
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
        
        // Determine activity based on thresholds
        ActivityType detectedActivity = determineActivity(avgAccel, accelVariance, avgGyro, maxSpeed);
        
        // Update activity if changed
        if (detectedActivity != currentActivity) {
            ActivityType previousActivity = currentActivity;
            currentActivity = detectedActivity;
            lastActivityChange = currentTime;
            
            Log.i(TAG, "Activity changed: " + previousActivity + " -> " + currentActivity + 
                  " (Accel: " + String.format("%.2f", avgAccel) + 
                  ", Gyro: " + String.format("%.2f", avgGyro) + 
                  ", Speed: " + String.format("%.2f", maxSpeed) + " m/s)");
            
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
     * Determine activity type based on sensor data
     *
     * Priority order (based on real sensor data analysis):
     * 1. DRIVING - High speed (most reliable)
     * 2. RUNNING - High variance + high gyro + higher speed
     * 3. WALKING - Moderate variance + moderate gyro + low speed
     * 4. STATIONARY - Gyro ≤ 0.003 (sensor noise) + variance ≤ 0.005 + flat surface
     * 5. ON_PHONE - Gyro > 0.003 (hand tremors) OR held at angle with movement
     * 6. ON_PHONE (fallback) - Any other movement patterns
     */
    private ActivityType determineActivity(double avgAccel, double accelVariance, double avgGyro, double maxSpeed) {
        Log.d(TAG, String.format("Activity Analysis - Accel: %.2f, Variance: %.2f, Gyro: %.2f, Speed: %.2f m/s",
                avgAccel, accelVariance, avgGyro, maxSpeed));

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

        // 3. WALKING detection - Moderate impact, moderate rotation, low speed
        // Walking has rhythmic but moderate variance and gyro from natural gait
        // CRITICAL: Walking requires ACTUAL MOVEMENT (speed > 0.3 m/s) to distinguish from phone pickup
        boolean hasWalkingVariance = accelVariance >= WALKING_VARIANCE_MIN && accelVariance < WALKING_VARIANCE_MAX;
        boolean hasWalkingGyro = avgGyro >= WALKING_GYRO_MIN && avgGyro < WALKING_GYRO_MAX;
        boolean hasWalkingSpeed = maxSpeed >= WALKING_SPEED_MIN && maxSpeed < WALKING_SPEED_MAX; // Must be moving

        // Walking if: correct variance range AND correct gyro range AND actually moving
        // This prevents brief phone pickups (speed = 0) from being classified as walking
        if (hasWalkingVariance && hasWalkingGyro && hasWalkingSpeed) {
            Log.d(TAG, "Detected: WALKING (variance: " + String.format("%.2f", accelVariance) +
                       ", gyro: " + String.format("%.2f", avgGyro) +
                       ", speed: " + String.format("%.2f", maxSpeed) + " m/s)");
            return ActivityType.WALKING;
        }

        // 4. STATIONARY (check BEFORE on_phone) - Phone on flat surface, completely still
        // ONLY stationary if phone is on a flat surface with ZERO movement
        // Based on real sensor data: table = gyro ~0.0005-0.002, variance ~0.0000-0.003
        if (accelVariance <= 0.005 && // Phones on table show variance < 0.003
            avgGyro <= 0.003 && // Phones on table show gyro < 0.002 (sensor noise only)
            Math.abs(avgAccel - GRAVITY_ACCEL) <= PERFECT_FLAT_TOLERANCE) {
            Log.d(TAG, "Detected: STATIONARY (phone on surface - accel: " + String.format("%.2f", avgAccel) +
                       ", variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) + ")");
            return ActivityType.STATIONARY;
        }

        // 5. ON_PHONE - Phone is being held or used
        // Holding a phone (even steady) creates micro-movements greater than sensor noise
        // Check if phone is being held at an angle (not flat) AND has movement
        if (avgAccel >= HELD_PHONE_ACCEL_MIN && avgAccel <= HELD_PHONE_ACCEL_MAX &&
            (avgGyro > 0.003 || accelVariance > 0.005)) {
            Log.d(TAG, "Detected: ON_PHONE (phone held at angle - accel: " + String.format("%.2f", avgAccel) +
                       ", variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }

        // If there's gyro movement above sensor noise, it's likely being held
        // Hand tremors typically create gyro > 0.003 (above table noise of ~0.002)
        if (avgGyro > 0.003) {
            Log.d(TAG, "Detected: ON_PHONE (micro-movements detected - variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }

        // Default to ON_PHONE for any other low movement
        // This catches edge cases and ensures phone in hand is never classified as stationary
        if (accelVariance <= STATIONARY_VARIANCE_THRESHOLD || avgGyro <= 0.15) {
            Log.d(TAG, "Detected: ON_PHONE (holding or low movement - variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }

        // Any other movement should be ON_PHONE (phone handling, gestures, etc.)
        if (avgGyro > 0.15 || accelVariance > STATIONARY_VARIANCE_THRESHOLD) {
            Log.d(TAG, "Detected: ON_PHONE (device handling - variance: " + String.format("%.2f", accelVariance) +
                       ", gyro: " + String.format("%.2f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }

        Log.d(TAG, "Detected: UNKNOWN (variance: " + String.format("%.2f", accelVariance) +
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
     * Get current detected activity
     */
    public ActivityType getCurrentActivity() {
        return currentActivity;
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
