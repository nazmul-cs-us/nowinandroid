package com.starception.submission.sensor;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.app.NotificationManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.starception.submission.ml.SalahDataSample;
import com.starception.submission.ml.SalahDetectionEngine;
import com.starception.submission.ml.SalahPosture;
import com.starception.submission.ml.SalahSequenceValidator;

/**
 * ENHANCED ACTIVITY DETECTION SERVICE: Multi-sensor activity recognition
 *
 * This service combines multiple sensors for accurate user activity detection:
 * - Accelerometer: Detects movement and vibration patterns
 * - Gyroscope: Detects rotational movement and orientation changes
 * - Step Counter: Detects walking/running steps (hardware-based, low power)
 * - Step Detector: Real-time step detection events
 * - Linear Acceleration: Movement without gravity (better activity classification)
 * - GPS: Detects speed and travel patterns
 *
 * IMPROVEMENTS (v2.0):
 * - Step counter integration for accurate walking/running detection
 * - Low-pass filter for noise reduction
 * - Moving average smoothing for sensor data
 * - Vehicle vibration pattern detection (high frequency, low amplitude)
 * - Confidence scoring for each activity
 * - Improved state machine with smarter transitions
 * - Better cycling/biking detection
 *
 * ACTIVITIES DETECTED:
 * - STATIONARY: User is at rest (sitting, standing still)
 * - ON_PHONE: User is actively using the phone
 * - WALKING: User is walking at normal pace
 * - RUNNING: User is running or jogging
 * - DRIVING: User is in a vehicle
 * - UNKNOWN: Activity cannot be determined
 */
public class ActivityDetectionService implements SensorEventListener, LocationListener {
    
    private static final String TAG = "ActivityDetection";
    private static final boolean ENABLE_DEBUG_LOGGING = true; // Set to true to enable debug logs
    
    // Helper method for conditional logging
    private void logDebug(String message) {
        if (ENABLE_DEBUG_LOGGING) {
            Log.d(TAG, message);
        }
    }
    
    // Sensor sampling frequencies
    private static final int SENSOR_DELAY_US = SensorManager.SENSOR_DELAY_UI; // ~60Hz
    private static final long LOCATION_UPDATE_INTERVAL = 1000; // 1 second
    private static final float LOCATION_UPDATE_DISTANCE = 1.0f; // 1 meter
    
    // Activity detection thresholds - Improved to distinguish walking from running and phone pickup
    // Widened variance range to catch more walking patterns (slow, normal, fast)
    private static final double WALKING_VARIANCE_MIN = 0.4; // Minimum variance for walking (reduced for slow/careful walking)
    private static final double WALKING_VARIANCE_MAX = 4.0; // Maximum variance for walking (increased from 2.5 - normal walking can reach 3.0+)
    private static final double WALKING_GYRO_MIN = 0.3; // Minimum gyro for walking (reduced for phone in pocket)
    private static final double WALKING_GYRO_MAX = 1.8; // Maximum gyro for walking
    private static final double WALKING_SPEED_MIN = 0.3; // m/s (1 km/h) - minimum speed to distinguish from phone pickup
    private static final double WALKING_SPEED_MAX = 2.5; // m/s (~9 km/h, typical walking speed is 1.4 m/s)

    private static final double RUNNING_VARIANCE_MIN = 6.0; // Minimum variance for running (much higher impact than walking)
    private static final double RUNNING_GYRO_MIN = 2.0; // Minimum gyro for running (more rotational movement)
    private static final double RUNNING_SPEED_MIN = 2.5; // m/s (~9 km/h, jogging/running speed)

    // Dynamic driving speed threshold - loaded from user settings
    // Default: 2.78 m/s (10 km/h) - can be adjusted in Travel Dua settings
    private double drivingSpeedThreshold = 2.78; // m/s (10 km/h) default
    private static final float DRIVING_EVIDENCE_MAX_ACCURACY_METERS = 25f;
    private static final int DRIVING_EVIDENCE_REQUIRED_SAMPLES = 3;
    private static final long DRIVING_EVIDENCE_WRITE_INTERVAL_MILLIS = 5_000L;
    private static final float ZERO_SPEED_EVIDENCE_MAX_MPS = 1.0f;
    private static final int ZERO_SPEED_EVIDENCE_REQUIRED_SAMPLES = 3;
    private static final long ZERO_SPEED_EVIDENCE_WRITE_INTERVAL_MILLIS = 5_000L;
    private int consecutiveReliableDrivingSpeedSamples = 0;
    private int consecutiveReliableZeroSpeedSamples = 0;
    private long lastDrivingEvidencePersistedElapsed = 0L;
    private long lastZeroSpeedEvidencePersistedElapsed = 0L;
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
    private Sensor stepCounter;        // NEW: Hardware step counter
    private Sensor stepDetector;       // NEW: Real-time step events
    private Sensor linearAccelerometer; // NEW: Acceleration without gravity
    private LocationManager locationManager;

    // Step counting for walking/running detection
    private AtomicInteger stepCountInWindow = new AtomicInteger(0);
    private long lastStepTime = 0;
    private int initialStepCount = -1;  // First step count reading
    private int lastStepCount = 0;      // Last known step count
    private float averageStepRate = 0f; // Steps per second (walking: 1.5-2.2, running: 2.5+)

    // Low-pass filter coefficients for noise reduction
    private static final float LOW_PASS_ALPHA = 0.2f; // Smoothing factor (0.1 = smoother, 0.5 = more responsive)
    private float[] filteredAccel = new float[3];     // Low-pass filtered accelerometer
    private float[] filteredGyro = new float[3];      // Low-pass filtered gyroscope

    // Moving average buffers for smoother readings
    private static final int MOVING_AVG_SIZE = 10;
    private Deque<Double> accelMagnitudeBuffer = new ArrayDeque<>(MOVING_AVG_SIZE);
    private Deque<Double> gyroMagnitudeBuffer = new ArrayDeque<>(MOVING_AVG_SIZE);
    private Deque<Double> linearAccelBuffer = new ArrayDeque<>(MOVING_AVG_SIZE);

    // Vehicle vibration detection (high frequency, low amplitude)
    private static final double VEHICLE_VIBRATION_FREQ_MIN = 15.0; // Hz - vehicle engine vibration
    private static final double VEHICLE_VIBRATION_AMP_MAX = 0.8;   // Low amplitude vibration
    private long lastVibrationSampleTime = 0;
    private int vibrationCrossings = 0; // Zero-crossings for frequency estimation
    private double lastAccelMagnitude = 0;

    // Confidence scoring (0.0 - 1.0)
    private float currentConfidence = 0f;
    private ActivityType lastConfidentActivity = ActivityType.UNKNOWN;

    // Salah (prayer) posture detection via TFLite ML model
    private SalahDetectionEngine salahDetectionEngine;
    private SalahSequenceValidator salahSequenceValidator;
    private boolean salahDetectionEnabled = false; // Disabled - ML model not accurate enough yet
    private static final int SALAH_WINDOW_SIZE = 5; // 5 samples per 100ms window (matching training)
    /**
     * Minimum spacing between samples fed to the salah model, so a window always spans the
     * ~100ms it does in training.
     *
     * SENSOR_DELAY_US is only a hint and the delivered rate rises when another listener
     * registers the same sensor faster — opening the training screen does exactly that.
     * Assembling a window from a fixed sample *count* then produced a 50ms window at
     * inference against 100ms windows in training: the same movement at twice the speed,
     * varying with whatever else happened to be listening. 18ms rather than 20ms so
     * ordinary delivery jitter is not mistaken for an extra sample.
     */
    private static final long MIN_SALAH_SAMPLE_INTERVAL_NS = 18_000_000L;
    private long lastSalahAccelSampleNs = 0L;
    private long lastSalahGyroSampleNs = 0L;
    private final List<float[]> salahAccelWindow = new ArrayList<>(); // [x,y,z] per sample
    private final List<float[]> salahGyroWindow = new ArrayList<>();  // [x,y,z] per sample
    private SalahPosture currentSalahPosture = null;
    private float currentSalahConfidence = 0f;
    private boolean isPrayerActive = false;
    private long lastSalahInferenceTime = 0;

    // File-based salah detection logging (for testing with phone in pocket)
    private BufferedWriter salahLogWriter = null;
    private File salahLogFile = null;
    private SimpleDateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private SimpleDateFormat fileNameDateFormat = new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US);
    private long salahLogLineCount = 0;

    // Callback for prayer posture changes
    private SalahPostureCallback salahPostureCallback;

    // Do Not Disturb (DND) management during prayer
    private NotificationManager notificationManager;
    private int previousInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
    private boolean dndEnabledByPrayer = false;

    /**
     * Callback interface for salah posture changes
     */
    public interface SalahPostureCallback {
        void onPostureChanged(SalahPosture posture, float confidence,
                              SalahSequenceValidator.PrayerState prayerState, int rakahCount);
    }

    // Data collection
    private ConcurrentLinkedQueue<AccelerometerData> accelData;
    private ConcurrentLinkedQueue<GyroscopeData> gyroData;
    private ConcurrentLinkedQueue<LocationData> locationData;
    
    // Current phone orientation
    private PhoneOrientation currentOrientation = PhoneOrientation.UNKNOWN;
    
    // Current phone position (based on research paper method)
    private PhonePosition currentPosition = PhonePosition.UNKNOWN;
    
    // Current state
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private ActivityType currentActivity = ActivityType.UNKNOWN;
    private long lastActivityChange = 0;
    
    // Activity stability tracking - prevent false detections from brief movements
    private ActivityType pendingActivity = ActivityType.UNKNOWN;
    private long pendingActivityStartTime = 0;
    private static final long ACTIVITY_CONFIRMATION_TIME = 1500; // 1.5 seconds minimum
    
    // Hysteresis: Make activities "sticky" to prevent rapid flipping
    // Reduced from 3 to 2 for faster response while still preventing false positives
    private static final int STABLE_DETECTION_COUNT = 2; // Need 2 consecutive detections (4 seconds)
    private static final int DRIVING_STABLE_COUNT = 3; // Driving needs 3 consecutive detections (6 seconds) to prevent GPS noise false positives
    private int consecutiveDetectionCount = 0;
    private ActivityType lastDetectedActivity = ActivityType.UNKNOWN;
    private Handler analysisHandler;
    private final Runnable analysisRunnable = new Runnable() {
        @Override
        public void run() {
            Handler activeHandler = analysisHandler;
            if (!isRunning.get() || activeHandler == null) {
                return;
            }
            analyzeCurrentActivity();
            if (isRunning.get() && analysisHandler == activeHandler) {
                activeHandler.postDelayed(this, ANALYSIS_INTERVAL);
            }
        }
    };
    
    // Callbacks
    private ActivityChangeCallback callback;
    private Context context;

    // Handler for sensor callbacks (critical for background operation)
    private Handler sensorHandler;
    private HandlerThread sensorHandlerThread;
    private boolean useExternalHandler = false;

    // WakeLock to prevent CPU from sleeping during detection
    private PowerManager.WakeLock wakeLock;
    
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
        PRAYING,     // User is performing salah (detected via ML model)
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
     * Phone position/placement detection based on research paper findings
     * Uses accelerometer features + angular features (pitch/roll) for 85% accuracy
     */
    public enum PhonePosition {
        HAND,          // Phone in hand (active use, high variance in orientation)
        POCKET,        // Phone in pocket (stable orientation, rhythmic walking pattern)
        DESK,          // Phone on desk/bag (stationary, flat orientation, minimal movement)
        UNKNOWN        // Cannot determine position
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

        // NEW: Initialize additional sensors for improved detection
        this.stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        this.stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        this.linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        // Initialize data collections
        this.accelData = new ConcurrentLinkedQueue<>();
        this.gyroData = new ConcurrentLinkedQueue<>();
        this.locationData = new ConcurrentLinkedQueue<>();

        // Log sensor availability
        Log.i(TAG, "ActivityDetectionService initialized (Enhanced v2.0)");
        Log.i(TAG, "  Step Counter: " + (stepCounter != null ? "✓" : "✗"));
        Log.i(TAG, "  Step Detector: " + (stepDetector != null ? "✓" : "✗"));
        Log.i(TAG, "  Linear Accelerometer: " + (linearAccelerometer != null ? "✓" : "✗"));

        // Initialize salah posture detection ML engine
        try {
            salahDetectionEngine = new SalahDetectionEngine(context);
            salahSequenceValidator = new SalahSequenceValidator();
            Log.i(TAG, "  🕌 Salah Detection Engine: ✓");
        } catch (Exception e) {
            Log.e(TAG, "  🕌 Salah Detection Engine: ✗ (" + e.getMessage() + ")");
            salahDetectionEngine = null;
            salahSequenceValidator = null;
            salahDetectionEnabled = false;
        }

        // Initialize NotificationManager for DND control during prayer
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Load driving speed threshold from user settings
        loadDrivingSpeedThreshold();
    }

    /**
     * Load the driving speed threshold from Travel Dua settings
     * Call this to refresh the threshold when settings change
     */
    public void loadDrivingSpeedThreshold() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("travel_dua_settings", Context.MODE_PRIVATE);
            int thresholdKmh = prefs.getInt("travel_dua_driving_speed_threshold_kmh", 10); // Default 10 km/h
            drivingSpeedThreshold = thresholdKmh / 3.6; // Convert km/h to m/s
            Log.i(TAG, "🚗 Driving speed threshold loaded: " + thresholdKmh + " km/h (" +
                  String.format("%.2f", drivingSpeedThreshold) + " m/s)");
        } catch (Exception e) {
            Log.w(TAG, "Failed to load driving speed threshold, using default: " + e.getMessage());
            drivingSpeedThreshold = 2.78; // Default 10 km/h in m/s
        }
    }

    /**
     * Update the driving speed threshold directly (called from ActivityTracker when settings change)
     * @param thresholdKmh Speed threshold in km/h
     */
    public void updateDrivingSpeedThreshold(int thresholdKmh) {
        drivingSpeedThreshold = thresholdKmh / 3.6; // Convert km/h to m/s
        Log.i(TAG, "🚗 Driving speed threshold updated: " + thresholdKmh + " km/h (" +
              String.format("%.2f", drivingSpeedThreshold) + " m/s)");
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
     * Start activity detection (uses internal HandlerThread for background operation)
     */
    public void startDetection(ActivityChangeCallback callback) {
        startDetection(callback, null);
    }

    /**
     * Start activity detection with external Handler for foreground service integration
     *
     * CRITICAL FOR BACKGROUND OPERATION:
     * Android 9+ throttles sensor delivery for background apps. To receive continuous
     * sensor updates when the app is closed, sensors must be registered with a Handler
     * running on a thread associated with a foreground service.
     *
     * @param callback Callback for activity changes
     * @param externalHandler Handler from foreground service (null to use internal HandlerThread)
     */
    public void startDetection(ActivityChangeCallback callback, Handler externalHandler) {
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

        // Acquire partial wake lock to keep CPU running for sensor processing
        acquireWakeLock();

        // Setup sensor handler for background operation
        if (externalHandler != null) {
            // Use external handler from foreground service (best for background operation)
            sensorHandler = externalHandler;
            useExternalHandler = true;
            Log.i(TAG, "✅ Using EXTERNAL handler from foreground service for sensor callbacks");
        } else {
            // Create internal HandlerThread (fallback)
            sensorHandlerThread = new HandlerThread("ActivitySensorThread", Thread.MAX_PRIORITY);
            sensorHandlerThread.start();
            sensorHandler = new Handler(sensorHandlerThread.getLooper());
            useExternalHandler = false;
            Log.i(TAG, "⚠️ Using INTERNAL HandlerThread for sensor callbacks");
        }

        // Register sensor listeners with Handler for continuous background updates
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SENSOR_DELAY_US, sensorHandler);
            Log.i(TAG, "Accelerometer registered with Handler");
        }

        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SENSOR_DELAY_US, sensorHandler);
            Log.i(TAG, "Gyroscope registered with Handler");
        }

        // Register step counter for walking/running detection
        if (stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler);
            Log.i(TAG, "Step Counter registered with Handler");
        }

        // Register step detector for real-time step events
        if (stepDetector != null) {
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler);
            Log.i(TAG, "Step Detector registered with Handler");
        }

        // Register linear accelerometer (gravity-free acceleration)
        if (linearAccelerometer != null) {
            sensorManager.registerListener(this, linearAccelerometer, SENSOR_DELAY_US, sensorHandler);
            Log.i(TAG, "Linear Accelerometer registered with Handler");
        }

        // Register location listener with Looper for background updates
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_UPDATE_INTERVAL,
                LOCATION_UPDATE_DISTANCE,
                this,
                sensorHandler.getLooper()
            );
            Log.i(TAG, "GPS location updates registered with Handler Looper");
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
        }

        // Start periodic analysis
        startPeriodicAnalysis();

        Log.i(TAG, "✅ Activity detection started (background-ready with Handler)");
    }

    /**
     * Acquire partial wake lock to keep CPU running for sensor processing
     */
    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                if (wakeLock == null) {
                    wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "ActivityDetection::SensorWakeLock"
                    );
                }
                if (!wakeLock.isHeld()) {
                    // Keep the sensor fallback bounded. Google Activity Transition is the
                    // wake-up-capable authority once the device sleeps.
                    wakeLock.acquire(10 * 60 * 1000L);
                    Log.i(TAG, "✅ WakeLock acquired for sensor processing");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire WakeLock", e);
        }
    }

    /**
     * Release wake lock
     */
    private void releaseWakeLock() {
        try {
            if (wakeLock != null) {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
                wakeLock = null;
                Log.i(TAG, "WakeLock released");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing WakeLock", e);
        }
    }
    
    /**
     * Stop activity detection
     */
    public void stopDetection() {
        if (!isRunning.get()) {
            return;
        }

        isRunning.set(false);

        // Moving detection from its fallback thread to the foreground-service thread
        // must remove the previous analysis runnable. Otherwise every restart creates
        // another permanent loop that races the current activity state.
        if (analysisHandler != null) {
            analysisHandler.removeCallbacks(analysisRunnable);
            analysisHandler = null;
        }

        // Unregister sensor listeners
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);

        // Release wake lock
        releaseWakeLock();

        // Clean up internal HandlerThread if we created one
        if (!useExternalHandler && sensorHandlerThread != null) {
            sensorHandlerThread.quitSafely();
            sensorHandlerThread = null;
            Log.i(TAG, "Internal HandlerThread stopped");
        }
        sensorHandler = null;
        useExternalHandler = false;

        // Clear data
        accelData.clear();
        gyroData.clear();
        locationData.clear();

        // Restore DND if prayer was active
        disablePrayerDnd();

        // Close salah log file before cleanup
        closeSalahLogFile();

        // Clean up salah detection
        synchronized (salahAccelWindow) { salahAccelWindow.clear(); lastSalahAccelSampleNs = 0L; }
        synchronized (salahGyroWindow) { salahGyroWindow.clear(); lastSalahGyroSampleNs = 0L; }
        isPrayerActive = false;
        currentSalahPosture = null;
        currentSalahConfidence = 0f;
        if (salahSequenceValidator != null) salahSequenceValidator.reset();
        if (salahDetectionEngine != null) salahDetectionEngine.reset();

        Log.i(TAG, "Activity detection stopped");
    }
    
    /**
     * Start periodic analysis of collected data
     */
    private void startPeriodicAnalysis() {
        if (analysisHandler != null) {
            analysisHandler.removeCallbacks(analysisRunnable);
        }
        analysisHandler = sensorHandler != null
                ? sensorHandler
                : new Handler(Looper.getMainLooper());
        analysisHandler.postDelayed(analysisRunnable, ANALYSIS_INTERVAL);
    }
    
    /**
     * Analyze collected sensor data to determine current activity
     * ENHANCED: Uses step counter, moving averages, and confidence scoring
     */
    private void analyzeCurrentActivity() {
        long currentTime = System.currentTimeMillis();

        // Salah is classified by the dedicated posture model. Once its sequence validator has
        // confirmed prayer, do not let the generic movement classifier replace PRAYING with
        // STATIONARY/ON_PHONE on its next two-second pass. Salah processing clears this flag when
        // the prayer session ends, after which normal activity classification resumes.
        if (isPrayerActive) {
            logDebug("🕌 Prayer active - preserving PRAYING over generic activity analysis");
            return;
        }

        long windowStart = currentTime - (DATA_WINDOW_SIZE * 1000);

        // Filter recent accelerometer data (use moving average for smoother values)
        double avgAccel = calculateAverageAcceleration(windowStart);
        double accelVariance = calculateAccelerationVariance(windowStart);
        double smoothedAccel = getMovingAverage(accelMagnitudeBuffer);
        if (smoothedAccel > 0) {
            avgAccel = (avgAccel + smoothedAccel) / 2.0; // Blend with moving average
        }

        // Filter recent gyroscope data
        double avgGyro = calculateAverageGyroscope(windowStart);
        double smoothedGyro = getMovingAverage(gyroMagnitudeBuffer);
        if (smoothedGyro > 0) {
            avgGyro = (avgGyro + smoothedGyro) / 2.0; // Blend with moving average
        }

        // Get recent location data
        double maxSpeed = getMaxSpeedInWindow(windowStart);

        // NEW: Get step data for walking/running detection
        int stepsInWindow = stepCountInWindow.getAndSet(0); // Reset for next window
        float stepRate = averageStepRate;

        // NEW: Get linear acceleration (gravity-free) for better motion detection
        double linearAccel = getMovingAverage(linearAccelBuffer);

        // Detect phone orientation for better activity classification
        PhoneOrientation orientation = detectPhoneOrientation(windowStart);
        currentOrientation = orientation;

        // Detect phone position using pitch/roll + basic features (research paper method)
        PhonePosition position = detectPhonePosition(windowStart, accelVariance, avgGyro);
        currentPosition = position;

        // Determine activity with enhanced algorithm (includes step data)
        ActivityType detectedActivity = determineActivityEnhanced(
                avgAccel, accelVariance, avgGyro, maxSpeed,
                stepsInWindow, stepRate, linearAccel,
                orientation, position);
        
        // HYSTERESIS: Make activities sticky to prevent rapid flipping
        // Only change if we get 3 consecutive different detections
        if (detectedActivity == lastDetectedActivity) {
            consecutiveDetectionCount++;
        } else {
            consecutiveDetectionCount = 1;
            lastDetectedActivity = detectedActivity;
        }
        
        // Don't change activity unless we have stable detections OR immediate transitions
        // IMPROVED: Driving needs more consecutive detections to prevent GPS noise false positives
        int requiredCount = (detectedActivity == ActivityType.DRIVING) ? DRIVING_STABLE_COUNT : STABLE_DETECTION_COUNT;
        boolean isStableDetection = (consecutiveDetectionCount >= requiredCount);
        // IMPROVED: Driving now also requires stable detection to prevent GPS noise false positives
        // Only STATIONARY transitions are immediate (user clearly stopped moving)
        boolean isImmediateTransition = (detectedActivity == ActivityType.STATIONARY);
        
        // IMPROVED: Require confirmation for WALKING to prevent false positives
        boolean needsConfirmation = (detectedActivity == ActivityType.WALKING && maxSpeed < 0.1);
        
        if (needsConfirmation && !isStableDetection) {
            // Walking needs both time confirmation AND stable detections
            if (detectedActivity != pendingActivity) {
                pendingActivity = detectedActivity;
                pendingActivityStartTime = currentTime;
                logDebug("Pending activity: " + detectedActivity + " (count: " + consecutiveDetectionCount + 
                      "/" + STABLE_DETECTION_COUNT + ")");
                return;
            } else {
                long timePending = currentTime - pendingActivityStartTime;
                if (timePending < ACTIVITY_CONFIRMATION_TIME) {
                    logDebug( "Confirming: " + detectedActivity + " (time: " + timePending + "ms, count: " + 
                          consecutiveDetectionCount + "/" + STABLE_DETECTION_COUNT + ")");
                    return;
                }
            }
        } else if (!isStableDetection && !isImmediateTransition) {
            // Not stable yet, wait for more consistent detections
            logDebug( "Waiting for stable detection: " + detectedActivity + " (count: " + 
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
     * Determine activity type based on sensor data, phone orientation AND phone position
     * ENHANCED with position-aware detection (research paper method)
     *
     * Priority order (based on real sensor data analysis):
     * 1. DRIVING - High speed (most reliable)
     * 2. RUNNING - High variance + high gyro + higher speed
     * 3. WALKING - Moderate variance + moderate gyro + low speed + orientation + position check
     * 4. STATIONARY - Gyro ≤ 0.003 (sensor noise) + variance ≤ 0.005 + flat surface + DESK position
     * 5. ON_PHONE - Gyro > 0.003 (hand tremors) OR held at angle with movement + portrait/landscape orientation + HAND position
     * 6. ON_PHONE (fallback) - Any other movement patterns
     */
    private ActivityType determineActivity(double avgAccel, double accelVariance, double avgGyro, double maxSpeed, PhoneOrientation orientation, PhonePosition position) {
        // Logging disabled for performance
        // Log.d(TAG, "═══════════════════════════════════════════════════════");
        // Log.d(TAG, String.format("📊 ACTIVITY ANALYSIS:"));
        // Log.d(TAG, String.format("  • Accel Avg: %.3f (gravity: %.1f)", avgAccel, GRAVITY_ACCEL));
        // Log.d(TAG, String.format("  • Accel Variance: %.3f", accelVariance));
        // Log.d(TAG, String.format("  • Gyro Avg: %.3f", avgGyro));
        // Log.d(TAG, String.format("  • Max Speed: %.2f m/s (%.1f km/h)", maxSpeed, maxSpeed * 3.6));
        // Log.d(TAG, String.format("  • Orientation: %s", orientation));
        // Log.d(TAG, String.format("  • Position: %s", position));

        // 1. High speed indicates DRIVING (most reliable indicator)
        if (maxSpeed > drivingSpeedThreshold) {
            logDebug( "Detected: DRIVING (speed: " + String.format("%.2f", maxSpeed) + " m/s / " + String.format("%.1f", maxSpeed * 3.6) + " km/h)");
            return ActivityType.DRIVING;
        }

        // 2. RUNNING detection - High impact, high rotation, faster speed
        // Running has higher variance (more impact) and higher gyro (more body rotation)
        // Must meet ALL criteria: high variance AND high gyro (OR high speed if GPS available)
        boolean hasRunningVariance = accelVariance >= RUNNING_VARIANCE_MIN;
        boolean hasRunningGyro = avgGyro >= RUNNING_GYRO_MIN;
        boolean hasRunningSpeed = maxSpeed >= RUNNING_SPEED_MIN && maxSpeed < drivingSpeedThreshold;

        // Running if: (high variance AND high gyro) OR (high speed with significant movement)
        if ((hasRunningVariance && hasRunningGyro) || (hasRunningSpeed && accelVariance >= RUNNING_VARIANCE_MIN)) {
            logDebug( "Detected: RUNNING (variance: " + String.format("%.2f", accelVariance) +
                       ", gyro: " + String.format("%.2f", avgGyro) +
                       ", speed: " + String.format("%.2f", maxSpeed) + " m/s)");
            return ActivityType.RUNNING;
        }

        // 3. WALKING detection - Moderate impact, moderate rotation, low speed + orientation + position check
        // Walking has rhythmic but moderate variance and gyro from natural gait
        // Works with or without GPS - uses variance pattern as primary indicator
        // ENHANCED with position detection: Use both orientation and position for better accuracy
        boolean hasWalkingVariance = accelVariance >= WALKING_VARIANCE_MIN && accelVariance < WALKING_VARIANCE_MAX;
        boolean hasWalkingGyro = avgGyro >= WALKING_GYRO_MIN && avgGyro < WALKING_GYRO_MAX;
        boolean hasWalkingSpeed = maxSpeed >= WALKING_SPEED_MIN && maxSpeed < WALKING_SPEED_MAX;
        boolean hasGPSData = maxSpeed > 0.1; // GPS is providing data
        
        // Relaxed gyro check for walking - allow lower gyro values (phone might be held still while walking)
        // This is important for when someone is walking while looking at their phone or phone in pocket
        boolean hasMinimalGyro = avgGyro >= 0.15; // Very low threshold - just needs some movement (even phone in pocket)
        
        // ENHANCED: Use POSITION for walking detection (research paper findings)
        // POCKET position + rhythmic variance = high confidence walking
        // HAND position + walking variance = walking while using phone
        boolean positionSuggestsWalking = (position == PhonePosition.POCKET || position == PhonePosition.HAND);
        
        // Check if phone orientation suggests walking (in pocket or in hand while moving)
        // Phone in pocket: typically portrait orientation with less gyro rotation
        // Phone being used while walking: portrait/landscape with more gyro
        boolean orientationSuggestsWalking = (orientation == PhoneOrientation.PORTRAIT || 
                                              orientation == PhoneOrientation.IN_POCKET ||
                                              orientation == PhoneOrientation.UNKNOWN); // Unknown could be pocket

        // Logging disabled for performance
        // Log.d(TAG, "🚶 WALKING DETECTION CHECKS:");
        // Log.d(TAG, String.format("  • Walking Variance: %.3f (range: %.1f-%.1f) - %s", 
        //         accelVariance, WALKING_VARIANCE_MIN, WALKING_VARIANCE_MAX, 
        //         hasWalkingVariance ? "✓ PASS" : "✗ FAIL"));
        // Log.d(TAG, String.format("  • Walking Gyro: %.3f (range: %.1f-%.1f) - %s", 
        //         avgGyro, WALKING_GYRO_MIN, WALKING_GYRO_MAX, 
        //         hasWalkingGyro ? "✓ PASS" : "✗ FAIL"));
        // Log.d(TAG, String.format("  • Walking Speed: %.2f m/s (range: %.1f-%.1f) - %s", 
        //         maxSpeed, WALKING_SPEED_MIN, WALKING_SPEED_MAX, 
        //         hasWalkingSpeed ? "✓ PASS" : "✗ FAIL"));
        // Log.d(TAG, String.format("  • Minimal Gyro: %.3f (min: %.2f) - %s", 
        //         avgGyro, 0.15, 
        //         hasMinimalGyro ? "✓ PASS" : "✗ FAIL"));
        // Log.d(TAG, String.format("  • GPS Available: %s", hasGPSData ? "YES" : "NO"));
        // Log.d(TAG, String.format("  • Position Suggests Walking: %s (%s)", 
        //         positionSuggestsWalking ? "YES" : "NO", position));
        // Log.d(TAG, String.format("  • Orientation Suggests Walking: %s (%s)", 
        //         orientationSuggestsWalking ? "YES" : "NO", orientation));
        
        // ENHANCED Walking detection with position awareness:
        // HIGH CONFIDENCE: POCKET position + rhythmic variance = definitely walking
        if (position == PhonePosition.POCKET && hasWalkingVariance) {
            logDebug( "✅ Detected: WALKING (POCKET + rhythmic variance)");
            return ActivityType.WALKING;
        }
        
        // Walking detection with or without GPS:
        // WITH GPS: Use speed + variance (most reliable) - require variance AND (gyro OR speed)
        // WITHOUT GPS: Use variance pattern as primary indicator with relaxed gyro requirement
        // BOOST confidence if position matches walking patterns
        if (hasWalkingVariance && (orientationSuggestsWalking || positionSuggestsWalking)) {
            if (hasGPSData) {
                // GPS available - variance + (gyro OR speed) for confidence
                // This allows walking detection even with low gyro if speed confirms it
                if (hasWalkingSpeed || hasWalkingGyro) {
                    logDebug( "✅ Detected: WALKING (GPS confirmed)");
                    return ActivityType.WALKING;
                } else {
                    logDebug( "⚠️ Walking variance detected but speed/gyro don't confirm");
                }
            } else {
                // No GPS - trust variance pattern more!
                // Walking has very distinctive rhythmic variance (0.4-2.5) from footsteps
                // Even with minimal gyro (looking at phone while walking), variance shows walking
                if (hasMinimalGyro) {
                    logDebug( "✅ Detected: WALKING (sensor-based, no GPS)");
                    return ActivityType.WALKING;
                } else {
                    logDebug( String.format("⚠️ Walking variance detected but gyro too low (%.3f < 0.15)", avgGyro));
                }
            }
        } else {
            if (!hasWalkingVariance) {
                logDebug( "⚠️ Walking NOT detected: Variance out of range");
            } else if (!orientationSuggestsWalking && !positionSuggestsWalking) {
                logDebug( "⚠️ Walking NOT detected: Orientation and position don't suggest walking");
            }
        }

        // 4. STATIONARY (check BEFORE on_phone) - Phone on flat surface, completely still
        // ONLY stationary if phone is on a flat surface with ZERO movement
        // Based on real sensor data: table = gyro ~0.0005-0.002, variance ~0.0000-0.003
        // ENHANCED: Use POSITION for high confidence stationary detection
        boolean meetsStationarySensorCriteria = (accelVariance <= 0.005 && 
                                                  avgGyro <= 0.003 && 
                                                  Math.abs(avgAccel - GRAVITY_ACCEL) <= PERFECT_FLAT_TOLERANCE);
        
        // IMPROVED: If phone is PERFECTLY still, it's on a surface (handles camera bumps!)
        // Camera bumps cause phone to tilt, so we can't rely on orientation alone
        // Instead, use VERY strict stillness criteria: variance < 0.0015, gyro < 0.0015
        boolean isPerfectlyStill = (accelVariance <= 0.0015 && avgGyro <= 0.0015);
        
        // HIGH CONFIDENCE: DESK position + perfectly still = definitely stationary
        if (position == PhonePosition.DESK && isPerfectlyStill) {
            logDebug( "Detected: STATIONARY (DESK position + perfectly still - accel: " + String.format("%.2f", avgAccel) +
                       ", variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) + ")");
            return ActivityType.STATIONARY;
        }
        
        if (isPerfectlyStill && meetsStationarySensorCriteria) {
            logDebug( "Detected: STATIONARY (perfectly still on surface - accel: " + String.format("%.2f", avgAccel) +
                       ", variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) +
                       ", orientation: " + orientation +
                       ", position: " + position + ")");
            return ActivityType.STATIONARY;
        }
        
        // If sensors show some movement (even tiny), it's likely being held
        if (meetsStationarySensorCriteria && !isPerfectlyStill) {
            logDebug( "Detected: ON_PHONE (slight movement detected - orientation: " + orientation +
                       ", variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }

        // 5. ON_PHONE - Phone is being held or used
        // ENHANCED: Use both orientation AND position for confident detection
        // Portrait/Landscape/FlatUp orientation strongly suggests phone is being held and used
        // FLAT_UP added: When reading while holding phone flat (common use case)
        boolean orientationSuggestsPhoneUse = (orientation == PhoneOrientation.PORTRAIT || 
                                               orientation == PhoneOrientation.LANDSCAPE ||
                                               orientation == PhoneOrientation.FLAT_UP);
        
        // HIGH CONFIDENCE: HAND position = definitely being used (research paper finding)
        if (position == PhonePosition.HAND) {
            logDebug( "Detected: ON_PHONE (HAND position - variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }
        
        // Holding a phone (even steady) creates micro-movements greater than sensor noise
        // Check if phone is being held at an angle (not flat) AND has movement
        if (avgAccel >= HELD_PHONE_ACCEL_MIN && avgAccel <= HELD_PHONE_ACCEL_MAX &&
            (avgGyro > 0.003 || accelVariance > 0.005)) {
            // If orientation confirms phone use, definitely ON_PHONE
            if (orientationSuggestsPhoneUse) {
                logDebug( "Detected: ON_PHONE (held + " + orientation + " - accel: " + String.format("%.2f", avgAccel) +
                           ", variance: " + String.format("%.4f", accelVariance) +
                           ", gyro: " + String.format("%.4f", avgGyro) +
                           ", position: " + position + ")");
                return ActivityType.ON_PHONE;
            }
            // Otherwise might be walking with phone, continue checking
        }

        // If there's gyro movement above sensor noise AND portrait/landscape orientation
        // Hand tremors typically create gyro > 0.003 (above table noise of ~0.002)
        if (avgGyro > 0.003 && orientationSuggestsPhoneUse) {
            logDebug( "Detected: ON_PHONE (" + orientation + " with micro-movements - variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }

        // Default to ON_PHONE for any other low movement WITH phone-use orientation
        // This catches edge cases like reading while phone is held still
        if ((accelVariance <= STATIONARY_VARIANCE_THRESHOLD || avgGyro <= 0.15) && orientationSuggestsPhoneUse) {
            logDebug( "Detected: ON_PHONE (" + orientation + " holding or low movement - variance: " + String.format("%.4f", accelVariance) +
                       ", gyro: " + String.format("%.4f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }

        // Any other movement should be ON_PHONE (phone handling, gestures, etc.)
        if (avgGyro > 0.15 || accelVariance > STATIONARY_VARIANCE_THRESHOLD) {
            logDebug( "Detected: ON_PHONE (device handling - variance: " + String.format("%.2f", accelVariance) +
                       ", gyro: " + String.format("%.2f", avgGyro) + ")");
            return ActivityType.ON_PHONE;
        }

        // ROBUST FALLBACK: If we reach here, phone is likely being held/used
        // It's not walking, running, driving, or truly stationary
        // Better to show ON_PHONE than UNKNOWN (more useful feedback)
        if (avgGyro > 0 || accelVariance > 0) {
            logDebug( "Detected: ON_PHONE (fallback - has movement but unclear pattern - variance: " + 
                       String.format("%.2f", accelVariance) + ", gyro: " + String.format("%.2f", avgGyro) + 
                       ", orientation: " + orientation + ")");
            return ActivityType.ON_PHONE;
        }
        
        // Final fallback - should almost never reach here
        logDebug( "Detected: UNKNOWN (no movement detected - variance: " + String.format("%.2f", accelVariance) +
                   ", gyro: " + String.format("%.2f", avgGyro) + ")");
        return ActivityType.UNKNOWN;
    }

    /**
     * ENHANCED Activity Detection Algorithm (v2.0)
     * Uses step counter, linear acceleration, and confidence scoring
     *
     * Step Rate Reference:
     * - Walking: 1.5 - 2.2 steps/second (90-132 steps/min)
     * - Running: 2.5 - 4.0 steps/second (150-240 steps/min)
     *
     * @param avgAccel Average acceleration magnitude
     * @param accelVariance Variance in acceleration (motion intensity)
     * @param avgGyro Average gyroscope magnitude (rotation)
     * @param maxSpeed Maximum GPS speed in window
     * @param stepsInWindow Steps detected in analysis window
     * @param stepRate Average steps per second
     * @param linearAccel Linear acceleration (without gravity)
     * @param orientation Phone orientation
     * @param position Phone position (HAND/POCKET/DESK)
     * @return Detected activity type with confidence
     */
    private ActivityType determineActivityEnhanced(
            double avgAccel, double accelVariance, double avgGyro, double maxSpeed,
            int stepsInWindow, float stepRate, double linearAccel,
            PhoneOrientation orientation, PhonePosition position) {

        // Calculate confidence scores for each activity
        float drivingConfidence = calculateDrivingConfidence(maxSpeed, accelVariance, avgGyro, linearAccel);
        float runningConfidence = calculateRunningConfidence(accelVariance, avgGyro, maxSpeed, stepsInWindow, stepRate);
        float walkingConfidence = calculateWalkingConfidence(accelVariance, avgGyro, maxSpeed, stepsInWindow, stepRate, position);
        float stationaryConfidence = calculateStationaryConfidence(accelVariance, avgGyro, linearAccel, position);
        float onPhoneConfidence = calculateOnPhoneConfidence(accelVariance, avgGyro, orientation, position);

        // Log confidence scores for debugging
        logDebug(String.format("📊 Confidence: Drive=%.2f, Run=%.2f, Walk=%.2f, Still=%.2f, Phone=%.2f (steps=%d, rate=%.1f)",
                drivingConfidence, runningConfidence, walkingConfidence, stationaryConfidence, onPhoneConfidence,
                stepsInWindow, stepRate));

        // Find highest confidence activity
        ActivityType bestActivity = ActivityType.UNKNOWN;
        float bestConfidence = 0.3f; // Minimum threshold to make a decision

        if (drivingConfidence > bestConfidence) {
            bestActivity = ActivityType.DRIVING;
            bestConfidence = drivingConfidence;
        }
        if (runningConfidence > bestConfidence) {
            bestActivity = ActivityType.RUNNING;
            bestConfidence = runningConfidence;
        }
        if (walkingConfidence > bestConfidence) {
            bestActivity = ActivityType.WALKING;
            bestConfidence = walkingConfidence;
        }
        if (stationaryConfidence > bestConfidence) {
            bestActivity = ActivityType.STATIONARY;
            bestConfidence = stationaryConfidence;
        }
        if (onPhoneConfidence > bestConfidence) {
            bestActivity = ActivityType.ON_PHONE;
            bestConfidence = onPhoneConfidence;
        }

        // Store confidence for external access
        currentConfidence = bestConfidence;

        // If no clear winner, fall back to original algorithm
        if (bestActivity == ActivityType.UNKNOWN) {
            return determineActivity(avgAccel, accelVariance, avgGyro, maxSpeed, orientation, position);
        }

        logDebug("🎯 Enhanced detection: " + bestActivity + " (confidence: " + String.format("%.2f", bestConfidence) + ")");
        return bestActivity;
    }

    /**
     * Calculate confidence score for DRIVING
     * High speed is the strongest indicator, but also check for vehicle vibration patterns
     * IMPROVED: Avoid false positives from GPS noise when variance suggests walking or phone handling
     */
    private float calculateDrivingConfidence(double maxSpeed, double accelVariance, double avgGyro, double linearAccel) {
        float confidence = 0f;

        // Check if variance suggests walking (should NOT be driving)
        boolean hasWalkingVariance = accelVariance >= WALKING_VARIANCE_MIN && accelVariance < WALKING_VARIANCE_MAX;

        // Check if variance suggests phone handling (moderate movement, not passive vehicle)
        boolean hasPhoneHandlingVariance = accelVariance > 0.1 && accelVariance < WALKING_VARIANCE_MIN;

        // Check for high gyro indicating active phone use (tilting, rotating)
        boolean hasActivePhoneUse = avgGyro > 0.3;

        // Speed is primary indicator (most reliable) - but ONLY if other sensors agree
        if (maxSpeed > drivingSpeedThreshold) {
            // High speed detected, but check if it makes sense with other sensors
            if (hasWalkingVariance) {
                // Walking variance + high GPS speed = GPS is wrong (e.g., GPS drift)
                confidence = 0.2f;
                logDebug("Driving: High GPS speed but walking variance detected - likely GPS error");
            } else if (hasActivePhoneUse && hasPhoneHandlingVariance) {
                // Active phone use + high GPS speed = GPS error while using phone
                confidence = 0.3f;
                logDebug("Driving: High GPS speed but active phone handling detected");
            } else {
                // Low variance + high speed = actually driving
                confidence = 0.9f;
            }
        } else if (maxSpeed > 4.0) { // 14 km/h - could be biking or slow driving
            // But if variance suggests walking or phone use, GPS is probably wrong
            if (hasWalkingVariance || hasActivePhoneUse) {
                confidence = 0.05f; // Very low confidence - likely GPS noise
                logDebug("Driving: Moderate GPS speed " + String.format("%.1f", maxSpeed) + " but variance/gyro suggests not driving");
            } else {
                confidence = 0.3f;
            }
        }

        // Vehicle vibration pattern: low amplitude, consistent vibration
        // Vehicles produce steady micro-vibrations from engine/road
        // Walking has HIGH variance, driving has LOW variance
        if (accelVariance < 0.3 && avgGyro < 0.2 && linearAccel < 0.5) {
            confidence += 0.15f; // Boost if low-variance motion (passive in vehicle)
        }

        // If variance is in walking range, reduce driving confidence significantly
        if (hasWalkingVariance) {
            confidence -= 0.4f; // Increased from 0.3 to 0.4
        }

        // Reduce confidence if high gyro (vehicles have minimal rotation)
        // Phone in hand typically has higher gyro than phone in car mount
        if (avgGyro > 0.5) {
            confidence -= 0.3f; // Increased from 0.2 to 0.3
        } else if (avgGyro > 1.0) {
            confidence -= 0.4f;
        }

        return Math.max(0f, Math.min(1f, confidence));
    }

    /**
     * Calculate confidence score for RUNNING
     * High variance, high gyro, fast step rate
     * Made more conservative to avoid false positives during fast walking
     * IMPROVED: Requires GPS speed confirmation - can't run at 0 speed!
     */
    private float calculateRunningConfidence(double accelVariance, double avgGyro, double maxSpeed, int stepsInWindow, float stepRate) {
        float confidence = 0f;

        // CRITICAL: If GPS shows 0 or very low speed, user is NOT running
        // This prevents false positives when lying in bed with phone
        if (maxSpeed < 0.5) {
            logDebug("Running: GPS shows near-zero speed (" + String.format("%.2f", maxSpeed) + ") - not running");
            return 0f; // Can't be running if not moving
        }

        // Step rate must be realistic (max 5.0 steps/sec for sprinting)
        // If step rate is impossibly high, ignore it
        if (stepRate > 5.0f) {
            logDebug("Running: Step rate too high (" + String.format("%.1f", stepRate) + ") - ignoring");
            stepRate = 0f;
        }

        // Step rate is excellent indicator (3.0+ steps/sec = definitely running)
        // Normal walking is 1.5-2.2 steps/sec, fast walking up to 2.5
        if (stepRate >= 3.0f) {
            confidence = 0.5f; // Reduced from 0.7 - need GPS confirmation
        } else if (stepRate >= 2.5f) {
            confidence = 0.2f; // Could be fast walking or slow jog - need more evidence
        }
        // Below 2.5 step rate = not running

        // High variance indicates high-impact activity (running has MUCH higher impact)
        if (accelVariance >= RUNNING_VARIANCE_MIN) {
            confidence += 0.2f;
        }

        // Running speed confirmation (if GPS available) - REQUIRED for high confidence
        if (maxSpeed >= RUNNING_SPEED_MIN && maxSpeed < drivingSpeedThreshold) {
            confidence += 0.3f; // GPS confirms running speed
        } else if (maxSpeed > 0 && maxSpeed < RUNNING_SPEED_MIN) {
            // GPS shows walking speed, significantly reduce running confidence
            confidence -= 0.3f;
        }

        // High gyro rotation indicates running gait
        if (avgGyro >= RUNNING_GYRO_MIN) {
            confidence += 0.1f;
        }

        // Recent steps boost confidence only if many steps AND GPS confirms movement
        if (stepsInWindow >= 6 && maxSpeed >= 1.0) {
            confidence += 0.1f;
        }

        return Math.max(0f, Math.min(1f, confidence));
    }

    /**
     * Calculate confidence score for WALKING
     * Moderate variance, step detection, position awareness
     * IMPROVED: Works even without step counter by using variance patterns
     */
    private float calculateWalkingConfidence(double accelVariance, double avgGyro, double maxSpeed, int stepsInWindow, float stepRate, PhonePosition position) {
        float confidence = 0f;

        // Walking variance pattern is the PRIMARY indicator (step counter may not work on all devices)
        boolean hasWalkingVariance = accelVariance >= WALKING_VARIANCE_MIN && accelVariance < WALKING_VARIANCE_MAX;
        boolean hasWalkingGyro = avgGyro >= WALKING_GYRO_MIN && avgGyro < WALKING_GYRO_MAX;

        // Variance pattern is reliable even without step counter
        if (hasWalkingVariance) {
            confidence = 0.5f; // Base confidence from variance pattern
            logDebug("Walking: variance in range (" + String.format("%.2f", accelVariance) + ")");
        }

        // Step rate boosts confidence if available (1.5-2.2 steps/sec = walking)
        if (stepRate >= 1.5f && stepRate < 2.5f) {
            confidence += 0.3f;
        } else if (stepRate >= 1.0f && stepRate < 1.5f) {
            confidence += 0.2f; // Slow walking
        } else if (stepsInWindow >= 2) { // At least some steps detected
            confidence += 0.15f;
        }

        // Gyro pattern suggests walking gait
        if (hasWalkingGyro && hasWalkingVariance) {
            confidence += 0.1f;
        }

        // Position boost: POCKET strongly suggests walking
        if (position == PhonePosition.POCKET) {
            confidence += 0.2f;
        } else if (position == PhonePosition.HAND && hasWalkingVariance) {
            confidence += 0.1f; // Walking while using phone
        }

        // Speed confirmation - but GPS can be unreliable, so don't over-weight
        if (maxSpeed >= WALKING_SPEED_MIN && maxSpeed < WALKING_SPEED_MAX) {
            confidence += 0.05f;
        }
        // If GPS shows very high speed but variance suggests walking, likely GPS error
        if (maxSpeed > 4.0 && hasWalkingVariance) {
            logDebug("Walking: GPS speed high (" + String.format("%.1f", maxSpeed) + ") but variance suggests walking");
            // Don't reduce confidence, just ignore GPS
        }

        // Reduce if too much gyro rotation (suggests phone handling, not walking)
        if (avgGyro > WALKING_GYRO_MAX && position == PhonePosition.HAND) {
            confidence -= 0.15f;
        }

        return Math.max(0f, Math.min(1f, confidence));
    }

    /**
     * Calculate confidence score for STATIONARY
     * Very low variance, very low gyro, phone on desk
     */
    private float calculateStationaryConfidence(double accelVariance, double avgGyro, double linearAccel, PhonePosition position) {
        float confidence = 0f;

        // Very still sensors indicate stationary
        boolean isPerfectlyStill = (accelVariance <= 0.0015 && avgGyro <= 0.0015);
        boolean isVeryStill = (accelVariance <= 0.005 && avgGyro <= 0.003);

        if (isPerfectlyStill) {
            confidence = 0.9f;
        } else if (isVeryStill) {
            confidence = 0.7f;
        } else if (accelVariance <= STATIONARY_VARIANCE_THRESHOLD && avgGyro <= 0.01) {
            confidence = 0.4f;
        }

        // DESK position strongly suggests stationary
        if (position == PhonePosition.DESK) {
            confidence += 0.2f;
        }

        // Linear acceleration should be near zero if truly stationary
        if (linearAccel < 0.1) {
            confidence += 0.1f;
        }

        return Math.max(0f, Math.min(1f, confidence));
    }

    /**
     * Calculate confidence score for ON_PHONE (actively using phone)
     * Moderate movement, HAND position, portrait/landscape orientation
     */
    private float calculateOnPhoneConfidence(double accelVariance, double avgGyro, PhoneOrientation orientation, PhonePosition position) {
        float confidence = 0f;

        // HAND position is strong indicator
        if (position == PhonePosition.HAND) {
            confidence = 0.6f;
        }

        // Portrait/landscape orientation suggests phone use
        boolean orientationSuggestsUse = (orientation == PhoneOrientation.PORTRAIT ||
                orientation == PhoneOrientation.LANDSCAPE ||
                orientation == PhoneOrientation.FLAT_UP);

        if (orientationSuggestsUse) {
            confidence += 0.15f;
        }

        // Hand tremor detection (small but measurable gyro)
        if (avgGyro > 0.003 && avgGyro < 0.3) {
            confidence += 0.1f;
        }

        // Some variance but not too much (phone handling, not walking)
        if (accelVariance > 0.005 && accelVariance < WALKING_VARIANCE_MIN) {
            confidence += 0.15f;
        }

        // Reduce if walking-like variance detected
        if (accelVariance >= WALKING_VARIANCE_MIN) {
            confidence -= 0.2f;
        }

        return Math.max(0f, Math.min(1f, confidence));
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
                // Apply low-pass filter for noise reduction
                filteredAccel[0] = applyLowPassFilter(event.values[0], filteredAccel[0]);
                filteredAccel[1] = applyLowPassFilter(event.values[1], filteredAccel[1]);
                filteredAccel[2] = applyLowPassFilter(event.values[2], filteredAccel[2]);

                accelData.offer(new AccelerometerData(
                        filteredAccel[0], filteredAccel[1], filteredAccel[2], timestamp));

                // Track vehicle vibration (zero-crossing detection for frequency)
                double currentMag = Math.sqrt(
                        filteredAccel[0] * filteredAccel[0] +
                                filteredAccel[1] * filteredAccel[1] +
                                filteredAccel[2] * filteredAccel[2]);
                updateVibrationTracking(currentMag, timestamp);

                // Update moving average buffer
                updateMovingAverage(accelMagnitudeBuffer, currentMag);
                break;

            case Sensor.TYPE_GYROSCOPE:
                // Apply low-pass filter for noise reduction
                filteredGyro[0] = applyLowPassFilter(event.values[0], filteredGyro[0]);
                filteredGyro[1] = applyLowPassFilter(event.values[1], filteredGyro[1]);
                filteredGyro[2] = applyLowPassFilter(event.values[2], filteredGyro[2]);

                gyroData.offer(new GyroscopeData(
                        filteredGyro[0], filteredGyro[1], filteredGyro[2], timestamp));

                // Update moving average buffer
                double gyroMag = Math.sqrt(
                        filteredGyro[0] * filteredGyro[0] +
                                filteredGyro[1] * filteredGyro[1] +
                                filteredGyro[2] * filteredGyro[2]);
                updateMovingAverage(gyroMagnitudeBuffer, gyroMag);
                break;

            case Sensor.TYPE_STEP_COUNTER:
                // Hardware step counter - tracks total steps since boot
                int totalSteps = (int) event.values[0];
                if (initialStepCount < 0) {
                    initialStepCount = totalSteps; // First reading
                }
                int stepsSinceStart = totalSteps - initialStepCount;
                int newSteps = totalSteps - lastStepCount;
                if (newSteps > 0 && lastStepCount > 0) {
                    stepCountInWindow.addAndGet(newSteps);
                }
                lastStepCount = totalSteps;
                logDebug("Step counter: total=" + totalSteps + ", window=" + stepCountInWindow.get());
                break;

            case Sensor.TYPE_STEP_DETECTOR:
                // Real-time step event - fires when a step is detected
                long timeSinceLastStep = timestamp - lastStepTime;
                if (lastStepTime > 0 && timeSinceLastStep > 200) { // Minimum 200ms between steps (max 5 steps/sec)
                    // Calculate instantaneous step rate
                    float instantRate = 1000f / timeSinceLastStep; // Steps per second
                    // Cap to realistic values (walking: 1.5-2.2, running: 2.5-4.0, max sprint: 5.0)
                    instantRate = Math.min(instantRate, 5.0f);
                    // Smooth with exponential average
                    averageStepRate = (averageStepRate * 0.7f) + (instantRate * 0.3f);
                } else if (timeSinceLastStep <= 200) {
                    // Too fast - likely false step detection, ignore
                    logDebug("Step ignored - too fast (" + timeSinceLastStep + "ms)");
                    break;
                }
                lastStepTime = timestamp;
                stepCountInWindow.incrementAndGet();
                logDebug("Step detected! Rate: " + String.format("%.2f", averageStepRate) + " steps/sec");
                break;

            case Sensor.TYPE_LINEAR_ACCELERATION:
                // Acceleration without gravity - better for motion detection
                double linearMag = Math.sqrt(
                        event.values[0] * event.values[0] +
                                event.values[1] * event.values[1] +
                                event.values[2] * event.values[2]);
                updateMovingAverage(linearAccelBuffer, linearMag);
                break;
        }

        // Feed raw sensor data to salah posture detection (uses unfiltered values for ML)
        if (salahDetectionEnabled && salahDetectionEngine != null) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    synchronized (salahAccelWindow) {
                        if (acceptSalahSample(event.timestamp, lastSalahAccelSampleNs)) {
                            lastSalahAccelSampleNs = event.timestamp;
                            salahAccelWindow.add(new float[]{event.values[0], event.values[1], event.values[2]});
                        }
                    }
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    synchronized (salahGyroWindow) {
                        if (acceptSalahSample(event.timestamp, lastSalahGyroSampleNs)) {
                            lastSalahGyroSampleNs = event.timestamp;
                            salahGyroWindow.add(new float[]{event.values[0], event.values[1], event.values[2]});
                        }
                    }
                    break;
            }
            // Check if we have a full salah window (5 accel + 5 gyro samples)
            boolean salahAccelReady, salahGyroReady;
            synchronized (salahAccelWindow) { salahAccelReady = salahAccelWindow.size() >= SALAH_WINDOW_SIZE; }
            synchronized (salahGyroWindow) { salahGyroReady = salahGyroWindow.size() >= SALAH_WINDOW_SIZE; }
            if (salahAccelReady && salahGyroReady) {
                processSalahWindow();
            }
        }

        // Clean old data periodically
        if (accelData.size() > 300) { // ~5 seconds at 60Hz
            cleanOldData();
        }
    }

    /**
     * Low-pass filter to smooth sensor data and reduce noise
     * Formula: output = alpha * input + (1 - alpha) * previousOutput
     */
    private float applyLowPassFilter(float input, float previousOutput) {
        return LOW_PASS_ALPHA * input + (1 - LOW_PASS_ALPHA) * previousOutput;
    }

    /**
     * Update moving average buffer with new value
     * Note: Synchronized to prevent ConcurrentModificationException
     */
    private void updateMovingAverage(Deque<Double> buffer, double value) {
        synchronized (buffer) {
            if (buffer.size() >= MOVING_AVG_SIZE) {
                buffer.removeFirst();
            }
            buffer.addLast(value);
        }
    }

    /**
     * Calculate moving average from buffer
     * Note: Creates a copy to avoid ConcurrentModificationException when buffer
     * is being updated by sensor callbacks while we iterate
     */
    private double getMovingAverage(Deque<Double> buffer) {
        if (buffer.isEmpty()) return 0;

        // Create a snapshot to avoid ConcurrentModificationException
        Double[] snapshot;
        synchronized (buffer) {
            snapshot = buffer.toArray(new Double[0]);
        }

        if (snapshot.length == 0) return 0;

        double sum = 0;
        for (Double val : snapshot) {
            if (val != null) {
                sum += val;
            }
        }
        return sum / snapshot.length;
    }

    /**
     * Track vibration patterns for vehicle detection
     * Vehicles produce high-frequency, low-amplitude vibrations (engine, road)
     */
    private void updateVibrationTracking(double currentMag, long timestamp) {
        // Zero-crossing detection (for frequency estimation)
        double normalized = currentMag - GRAVITY_ACCEL;
        if ((lastAccelMagnitude < 0 && normalized >= 0) ||
                (lastAccelMagnitude >= 0 && normalized < 0)) {
            vibrationCrossings++;
        }
        lastAccelMagnitude = normalized;

        // Reset every second to calculate frequency
        if (timestamp - lastVibrationSampleTime >= 1000) {
            // Frequency = crossings / 2 (full cycle = 2 crossings)
            double estimatedFreq = vibrationCrossings / 2.0;
            logDebug("Vibration frequency: " + String.format("%.1f", estimatedFreq) + " Hz");
            vibrationCrossings = 0;
            lastVibrationSampleTime = timestamp;
        }
    }

    /**
     * Get steps detected in the analysis window
     */
    public int getStepsInWindow() {
        return stepCountInWindow.get();
    }

    /**
     * Get average step rate (steps per second)
     */
    public float getAverageStepRate() {
        return averageStepRate;
    }

    /**
     * Get detection confidence (0.0 - 1.0)
     */
    public float getConfidence() {
        return currentConfidence;
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        logDebug( "Sensor accuracy changed: " + sensor.getName() + " = " + accuracy);
    }
    
    // LocationListener implementation
    @Override
    public void onLocationChanged(Location location) {
        if (!isRunning.get()) return;

        // IMPROVED: Only trust GPS speed if accuracy is good
        // Poor GPS accuracy can cause false high speed readings
        float accuracy = location.getAccuracy();
        float speed = location.getSpeed();

        // Filter out unreliable GPS readings
        if (accuracy > 50) {
            logDebug("GPS accuracy poor (" + String.format("%.0f", accuracy) + "m) - ignoring speed");
            speed = 0; // Don't trust speed from inaccurate GPS
        }

        // Also filter if speed is reported but location.hasSpeed() is false
        if (!location.hasSpeed()) {
            speed = 0;
        }

        // A wake-up alarm is allowed to play the Travel Dua only when the device has
        // continued to produce reliable driving-speed samples. Requiring three samples
        // prevents one stationary GPS spike from becoming proof of an actual journey.
        boolean hasReliableSpeed = location.hasSpeed()
                && accuracy <= DRIVING_EVIDENCE_MAX_ACCURACY_METERS;
        if (hasReliableSpeed && speed > drivingSpeedThreshold) {
            consecutiveReliableDrivingSpeedSamples++;
            consecutiveReliableZeroSpeedSamples = 0;
            if (consecutiveReliableDrivingSpeedSamples >= DRIVING_EVIDENCE_REQUIRED_SAMPLES) {
                long nowElapsed = SystemClock.elapsedRealtime();
                if (nowElapsed - lastDrivingEvidencePersistedElapsed
                        >= DRIVING_EVIDENCE_WRITE_INTERVAL_MILLIS) {
                    context.getSharedPreferences("travel_dua_settings", Context.MODE_PRIVATE)
                            .edit()
                            .putLong("travel_dua_last_reliable_speed_elapsed", nowElapsed)
                            .putFloat("travel_dua_last_reliable_speed_mps", speed)
                            .apply();
                    lastDrivingEvidencePersistedElapsed = nowElapsed;
                    logDebug(String.format(
                            "Reliable driving evidence persisted: %.1f km/h, accuracy=%.0fm",
                            speed * 3.6,
                            accuracy));
                }
            }
        } else if (hasReliableSpeed && speed <= ZERO_SPEED_EVIDENCE_MAX_MPS) {
            consecutiveReliableDrivingSpeedSamples = 0;
            consecutiveReliableZeroSpeedSamples++;
            if (consecutiveReliableZeroSpeedSamples >= ZERO_SPEED_EVIDENCE_REQUIRED_SAMPLES) {
                long nowElapsed = SystemClock.elapsedRealtime();
                if (nowElapsed - lastZeroSpeedEvidencePersistedElapsed
                        >= ZERO_SPEED_EVIDENCE_WRITE_INTERVAL_MILLIS) {
                    context.getSharedPreferences("travel_dua_settings", Context.MODE_PRIVATE)
                            .edit()
                            .putLong("travel_dua_last_reliable_zero_speed_elapsed", nowElapsed)
                            .apply();
                    lastZeroSpeedEvidencePersistedElapsed = nowElapsed;
                    logDebug(String.format(
                            "Reliable zero-speed evidence persisted: %.1f km/h, accuracy=%.0fm",
                            speed * 3.6,
                            accuracy));
                }
            }
        } else {
            consecutiveReliableDrivingSpeedSamples = 0;
            consecutiveReliableZeroSpeedSamples = 0;
        }

        // Log for debugging
        if (speed > 0) {
            logDebug(String.format("GPS: speed=%.1f m/s (%.0f km/h), accuracy=%.0fm",
                    speed, speed * 3.6, accuracy));
        }

        long timestamp = System.currentTimeMillis();
        locationData.offer(new LocationData(
            location.getLatitude(),
            location.getLongitude(),
            speed,
            timestamp
        ));
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        logDebug( "Location provider status changed: " + provider + " = " + status);
    }
    
    @Override
    public void onProviderEnabled(String provider) {
        logDebug( "Location provider enabled: " + provider);
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        logDebug( "Location provider disabled: " + provider);
    }
    
    /**
     * Calculate pitch angle from accelerometer data
     * Based on research paper: θ = (180/π) * atan2(y/g, z/g)
     * Pitch represents rotation around X-axis (forward/backward tilt)
     * Range: -180 to +180 degrees
     */
    private double calculatePitch(float y, float z) {
        final double GRAVITY = 9.81;
        return (180.0 / Math.PI) * Math.atan2(y / GRAVITY, z / GRAVITY);
    }
    
    /**
     * Calculate roll angle from accelerometer data
     * Based on research paper: φ = (180/π) * atan2(x/g, z/g)
     * Roll represents rotation around Y-axis (left/right tilt)
     * Range: -180 to +180 degrees
     */
    private double calculateRoll(float x, float z) {
        final double GRAVITY = 9.81;
        return (180.0 / Math.PI) * Math.atan2(x / GRAVITY, z / GRAVITY);
    }
    
    /**
     * Detect phone position (HAND, POCKET, DESK) using accelerometer features
     * Based on research paper achieving 85% accuracy with basic + angular features
     * 
     * Key findings from paper:
     * - HAND: High variance in orientation angles, frequent pitch/roll changes, moderate variance
     * - POCKET: Stable orientation (mostly vertical), rhythmic variance from walking, limited pitch/roll changes
     * - DESK: Flat orientation (pitch/roll near 0 or 90), very low variance, minimal angle changes
     */
    private PhonePosition detectPhonePosition(long windowStart, double accelVariance, double avgGyro) {
        if (accelData.isEmpty()) {
            return PhonePosition.UNKNOWN;
        }
        
        // Calculate pitch and roll for all data points in window
        double sumPitch = 0, sumRoll = 0;
        double sumPitchSq = 0, sumRollSq = 0;
        int count = 0;
        
        for (AccelerometerData data : accelData) {
            if (data.timestamp >= windowStart) {
                double pitch = calculatePitch(data.y, data.z);
                double roll = calculateRoll(data.x, data.z);
                
                sumPitch += pitch;
                sumRoll += roll;
                sumPitchSq += pitch * pitch;
                sumRollSq += roll * roll;
                count++;
            }
        }
        
        if (count == 0) {
            return PhonePosition.UNKNOWN;
        }
        
        // Calculate statistics
        double avgPitch = sumPitch / count;
        double avgRoll = sumRoll / count;
        double pitchVariance = (sumPitchSq / count) - (avgPitch * avgPitch);
        double rollVariance = (sumRollSq / count) - (avgRoll * avgRoll);
        double orientationVariance = pitchVariance + rollVariance; // Combined orientation change
        
        logDebug( String.format("Position Detection - Pitch: %.1f°(var:%.1f), Roll: %.1f°(var:%.1f), AccelVar: %.3f, Gyro: %.3f",
              avgPitch, pitchVariance, avgRoll, rollVariance, accelVariance, avgGyro));
        
        // DESK detection: Flat orientation + minimal movement + VERY stable orientation
        // Paper findings: Desk/Bag has very low variance and stable angles
        // Phone on desk: pitch/roll near 0° (flat) or 90° (standing), very low variance
        // CRITICAL: Must have FLAT orientation - if angled (like 55°), it's being HELD
        boolean isFlatOrientation = (Math.abs(avgPitch) < 15 || Math.abs(avgPitch - 90) < 15 || Math.abs(avgPitch + 90) < 15);
        boolean isVeryStableOrientation = orientationVariance < 5; // VERY stable - desk doesn't move at all
        boolean isMinimalMovement = accelVariance < 0.01 && avgGyro < 0.01;
        
        if (isFlatOrientation && isVeryStableOrientation && isMinimalMovement) {
            logDebug( "Position: DESK (flat + very stable + minimal movement)");
            return PhonePosition.DESK;
        }
        
        // POCKET detection: Stable vertical orientation + rhythmic movement from walking
        // Paper findings: Pocket has stable orientation but moderate variance from walking
        // Phone in pocket: mostly vertical (pitch ~70-90°), stable angles, rhythmic variance
        boolean isVerticalOrientation = Math.abs(avgPitch) > 60 && Math.abs(avgPitch) < 100;
        boolean hasRhythmicMovement = accelVariance >= 0.3 && accelVariance < 3.0; // Walking pattern
        boolean isStableInPocket = orientationVariance < 200; // Angles don't change much
        boolean hasLimitedRotation = avgGyro < 0.8; // Less rotation than active use
        
        if (isVerticalOrientation && hasRhythmicMovement && isStableInPocket && hasLimitedRotation) {
            logDebug( "Position: POCKET (vertical + rhythmic + stable angles)");
            return PhonePosition.POCKET;
        }
        
        // HAND detection: Frequent orientation changes + moderate to high variance
        // Paper findings: Hand has highest orientation variance due to active use
        // Phone in hand: frequently changing angles, higher gyro from manipulation
        boolean hasFrequentOrientationChanges = orientationVariance > 150; // Angles change frequently
        boolean hasActiveMovement = (accelVariance > 0.1 || avgGyro > 0.2); // Active handling
        
        if (hasFrequentOrientationChanges && hasActiveMovement) {
            logDebug( "Position: HAND (frequent orientation changes + active movement)");
            return PhonePosition.HAND;
        }
        
        // IMPROVED HAND detection: Held still but NOT flat orientation
        // When holding phone steady (reading, looking at it), it's at an angle (not flat like desk)
        // Key insight: avgPitch between 30-80° = typical viewing angle when held
        boolean isViewingAngle = Math.abs(avgPitch) > 30 && Math.abs(avgPitch) < 80;
        boolean hasLowButNonZeroMovement = (accelVariance >= 0.001 && accelVariance < 0.1) || 
                                           (avgGyro >= 0.01 && avgGyro < 0.2);
        boolean hasSmallOrientationChanges = orientationVariance >= 5 && orientationVariance < 150; // Some micro-movements
        
        if (isViewingAngle && (hasLowButNonZeroMovement || hasSmallOrientationChanges)) {
            logDebug( "Position: HAND (viewing angle " + String.format("%.1f", avgPitch) + 
                  "° + steady holding)");
            return PhonePosition.HAND;
        }
        
        // Fallback: Use simple heuristics
        // High variance + high gyro = likely HAND
        if (accelVariance > 0.4 && avgGyro > 0.4) {
            logDebug( "Position: HAND (fallback - high variance + gyro)");
            return PhonePosition.HAND;
        }
        
        // Very low movement + angled = likely HAND (held still)
        if (isViewingAngle && accelVariance < 0.05 && avgGyro < 0.05) {
            logDebug( "Position: HAND (fallback - angled + very low movement = held still)");
            return PhonePosition.HAND;
        }
        
        // Very low movement + flat orientation = likely DESK
        if (isFlatOrientation && accelVariance < 0.05 && avgGyro < 0.05) {
            logDebug( "Position: DESK (fallback - flat + very low movement)");
            return PhonePosition.DESK;
        }
        
        logDebug( "Position: UNKNOWN (doesn't match clear patterns)");
        return PhonePosition.UNKNOWN;
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
        logDebug( String.format("Orientation Axes - X:%.2f, Y:%.2f, Z:%.2f (abs: X:%.2f, Y:%.2f, Z:%.2f)", 
              avgX, avgY, avgZ, absX, absY, absZ));
        
        // Determine dominant axis (check FLAT first - most reliable)
        // Y-axis dominant: Phone flat (on table)
        // Z-axis dominant: Phone vertical (portrait)
        // X-axis dominant: Phone horizontal (landscape)
        
        // FLAT detection - Y-axis dominant (RELAXED threshold for better detection)
        if (absY > 7.0 && absY > absX * 1.2 && absY > absZ * 1.2) {
            logDebug( "Orientation: FLAT (Y-axis dominant: " + String.format("%.2f", absY) + ")");
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
     * Get current phone position (HAND, POCKET, DESK) - Research paper method
     */
    public PhonePosition getCurrentPosition() {
        return currentPosition;
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

    // ═══════════════════════════════════════════════════════
    // SALAH (PRAYER) POSTURE DETECTION
    // ═══════════════════════════════════════════════════════

    /**
     * Process a complete 100ms sensor window through the salah ML model.
     * Called from onSensorChanged when both accel and gyro buffers have 5+ samples.
     */
    /**
     * Whether a sensor event is far enough from the last one fed to the salah model.
     * Mirrors SalahDataCollectionService.shouldAcceptSample so inference and training see
     * windows of the same duration. See {@link #MIN_SALAH_SAMPLE_INTERVAL_NS}.
     */
    private boolean acceptSalahSample(long eventTimestampNs, long lastAcceptedNs) {
        if (lastAcceptedNs == 0L) return true;
        long elapsed = eventTimestampNs - lastAcceptedNs;
        // Some devices restart event timestamps (e.g. across a suspend); never stall.
        if (elapsed < 0L) return true;
        return elapsed >= MIN_SALAH_SAMPLE_INTERVAL_NS;
    }

    private void processSalahWindow() {
        float[] ax, ay, az, gx, gy, gz;

        synchronized (salahAccelWindow) {
            if (salahAccelWindow.size() < SALAH_WINDOW_SIZE) return;
            ax = new float[SALAH_WINDOW_SIZE];
            ay = new float[SALAH_WINDOW_SIZE];
            az = new float[SALAH_WINDOW_SIZE];
            for (int i = 0; i < SALAH_WINDOW_SIZE; i++) {
                float[] sample = salahAccelWindow.get(i);
                ax[i] = sample[0];
                ay[i] = sample[1];
                az[i] = sample[2];
            }
            // Remove consumed samples (keep overflow for next window)
            for (int i = 0; i < SALAH_WINDOW_SIZE; i++) {
                salahAccelWindow.remove(0);
            }
        }

        synchronized (salahGyroWindow) {
            if (salahGyroWindow.size() < SALAH_WINDOW_SIZE) return;
            gx = new float[SALAH_WINDOW_SIZE];
            gy = new float[SALAH_WINDOW_SIZE];
            gz = new float[SALAH_WINDOW_SIZE];
            for (int i = 0; i < SALAH_WINDOW_SIZE; i++) {
                float[] sample = salahGyroWindow.get(i);
                gx[i] = sample[0];
                gy[i] = sample[1];
                gz[i] = sample[2];
            }
            for (int i = 0; i < SALAH_WINDOW_SIZE; i++) {
                salahGyroWindow.remove(0);
            }
        }

        // Compute summary features for the sample
        float avgAx = mean(ax), avgAy = mean(ay), avgAz = mean(az);
        float avgGx = mean(gx), avgGy = mean(gy), avgGz = mean(gz);
        float pitch = (float) Math.toDegrees(Math.atan2(avgAy, avgAz));
        float roll = (float) Math.toDegrees(Math.atan2(avgAx, avgAz));
        float accelMag = (float) Math.sqrt(avgAx * avgAx + avgAy * avgAy + avgAz * avgAz);
        float gyroMag = (float) Math.sqrt(avgGx * avgGx + avgGy * avgGy + avgGz * avgGz);

        // Create sample and run inference
        SalahDataSample sample = new SalahDataSample(
                System.currentTimeMillis(), "live", SalahPosture.QIYAM,
                ax, ay, az, gx, gy, gz,
                pitch, roll, accelMag, gyroMag
        );

        SalahDetectionEngine.ClassificationResult result = salahDetectionEngine.addSampleAndClassify(sample);
        if (result == null) return;

        // Open log file on first inference if not already open
        if (salahLogWriter == null) {
            openSalahLogFile();
        }

        long now = System.currentTimeMillis();
        SalahPosture detectedPosture = result.getPosture();
        float confidence = result.getConfidence();

        // Run through sequence validator
        SalahSequenceValidator.ValidationResult validation =
                salahSequenceValidator.processDetection(detectedPosture, confidence, now);

        // Log EVERY detection to file (accepted or not) for analysis
        writeSalahLog(detectedPosture, confidence,
                validation.getAccepted(),
                validation.getConfirmedPosture(),
                validation.getPrayerState(),
                validation.getRakahCount(),
                pitch, roll, accelMag, gyroMag);

        if (validation.getAccepted() && validation.getConfirmedPosture() != null) {
            SalahPosture confirmed = validation.getConfirmedPosture();
            boolean wasActive = isPrayerActive;
            currentSalahPosture = confirmed;
            currentSalahConfidence = confidence;

            // Override activity type to PRAYING when prayer is confirmed
            if (validation.getPrayerState() == SalahSequenceValidator.PrayerState.CONFIRMED ||
                validation.getPrayerState() == SalahSequenceValidator.PrayerState.DETECTING) {
                isPrayerActive = true;

                // Only change activity type if confirmed (not just detecting)
                if (validation.getPrayerState() == SalahSequenceValidator.PrayerState.CONFIRMED) {
                    // Enable DND when prayer is confirmed
                    enablePrayerDnd();

                    if (currentActivity != ActivityType.PRAYING) {
                        ActivityType previousActivity = currentActivity;
                        currentActivity = ActivityType.PRAYING;
                        lastActivityChange = now;
                        Log.i(TAG, "🕌 Activity changed: " + previousActivity + " -> PRAYING (" +
                                confirmed.getDisplayName() + " " + String.format("%.0f%%", confidence * 100) + ")");
                        writeSalahLogEvent("ACTIVITY CHANGED: " + previousActivity + " -> PRAYING");
                        if (callback != null) {
                            callback.onActivityChanged(ActivityType.PRAYING, previousActivity);
                        }
                    }
                }
            }

            // Notify posture callback
            if (salahPostureCallback != null) {
                salahPostureCallback.onPostureChanged(
                        confirmed, confidence,
                        validation.getPrayerState(), validation.getRakahCount());
            }

            // Log significant posture changes
            if (now - lastSalahInferenceTime > 500) { // Throttle logging to every 500ms
                Log.d(TAG, "🕌 Posture: " + confirmed.getDisplayName() +
                        " (" + String.format("%.0f%%", confidence * 100) + ")" +
                        " | Prayer: " + validation.getPrayerState() +
                        " | Rak'ah: " + validation.getRakahCount());
                lastSalahInferenceTime = now;
            }
        }

        // Check if prayer ended (was active, now not getting valid detections for a while)
        if (isPrayerActive && currentSalahPosture != null) {
            // If no confirmed posture in last 10 seconds, prayer likely ended
            if (now - lastSalahInferenceTime > 10000 && lastSalahInferenceTime > 0) {
                isPrayerActive = false;
                // Disable DND when prayer ends
                disablePrayerDnd();
                salahSequenceValidator.completePrayer();
                String summary = salahSequenceValidator.getSessionSummary();
                Log.i(TAG, "🕌 Prayer ended. " + summary);
                writeSalahLogEvent("PRAYER ENDED. " + summary);
                closeSalahLogFile(); // Close log file when prayer ends
                salahSequenceValidator.reset();
                salahDetectionEngine.reset();
                currentSalahPosture = null;
                currentSalahConfidence = 0f;
            }
        }
    }

    private float mean(float[] arr) {
        float sum = 0;
        for (float v : arr) sum += v;
        return sum / arr.length;
    }

    // ═══════════════════════════════════════════════════════
    // SALAH DETECTION FILE LOGGING (for testing)
    // ═══════════════════════════════════════════════════════

    /**
     * Open a log file for salah detection events.
     * File is stored in app's external files directory for easy retrieval.
     * Path: /sdcard/Android/data/com.starception.submission/files/salah_detection_logs/
     */
    private void openSalahLogFile() {
        try {
            File logDir = new File(context.getExternalFilesDir(null), "salah_detection_logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            String timestamp = fileNameDateFormat.format(new Date());
            salahLogFile = new File(logDir, "salah_detection_" + timestamp + ".log");
            salahLogWriter = new BufferedWriter(new FileWriter(salahLogFile, true));
            salahLogLineCount = 0;

            // Write header
            salahLogWriter.write("=== SALAH DETECTION LOG ===\n");
            salahLogWriter.write("Started: " + logDateFormat.format(new Date()) + "\n");
            salahLogWriter.write("Format: timestamp | detected_posture | confidence | accepted | confirmed_posture | prayer_state | rakah_count | pitch | roll | accel_mag | gyro_mag\n");
            salahLogWriter.write("==========================================\n");
            salahLogWriter.flush();

            Log.i(TAG, "🕌 Salah log file opened: " + salahLogFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "🕌 Failed to open salah log file: " + e.getMessage());
            salahLogWriter = null;
        }
    }

    /**
     * Write a salah detection event to the log file.
     */
    private void writeSalahLog(SalahPosture detected, float confidence, boolean accepted,
                                SalahPosture confirmed, SalahSequenceValidator.PrayerState prayerState,
                                int rakahCount, float pitch, float roll, float accelMag, float gyroMag) {
        if (salahLogWriter == null) return;
        try {
            String line = String.format(Locale.US,
                    "%s | %-14s | %.3f | %s | %-14s | %-10s | %d | %7.1f | %7.1f | %5.2f | %5.3f\n",
                    logDateFormat.format(new Date()),
                    detected != null ? detected.name() : "null",
                    confidence,
                    accepted ? "YES" : "NO ",
                    confirmed != null ? confirmed.name() : "-",
                    prayerState != null ? prayerState.name() : "-",
                    rakahCount,
                    pitch, roll, accelMag, gyroMag);
            salahLogWriter.write(line);
            salahLogLineCount++;

            // Flush every 10 lines to avoid data loss if app crashes
            if (salahLogLineCount % 10 == 0) {
                salahLogWriter.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "🕌 Failed to write salah log: " + e.getMessage());
        }
    }

    /**
     * Write a general event/message to the salah log file.
     */
    private void writeSalahLogEvent(String event) {
        if (salahLogWriter == null) return;
        try {
            salahLogWriter.write(logDateFormat.format(new Date()) + " | EVENT: " + event + "\n");
            salahLogWriter.flush();
        } catch (IOException e) {
            Log.e(TAG, "🕌 Failed to write salah log event: " + e.getMessage());
        }
    }

    /**
     * Close the salah log file.
     */
    private void closeSalahLogFile() {
        if (salahLogWriter != null) {
            try {
                salahLogWriter.write("==========================================\n");
                salahLogWriter.write("Ended: " + logDateFormat.format(new Date()) + "\n");
                salahLogWriter.write("Total lines: " + salahLogLineCount + "\n");
                salahLogWriter.flush();
                salahLogWriter.close();
                Log.i(TAG, "🕌 Salah log file closed: " + salahLogFile.getAbsolutePath() +
                        " (" + salahLogLineCount + " lines)");
            } catch (IOException e) {
                Log.e(TAG, "🕌 Failed to close salah log file: " + e.getMessage());
            }
            salahLogWriter = null;
            salahLogFile = null;
        }
    }

    /**
     * Set the callback for salah posture changes
     */
    public void setSalahPostureCallback(SalahPostureCallback callback) {
        this.salahPostureCallback = callback;
    }

    /**
     * Enable or disable salah detection
     */
    public void setSalahDetectionEnabled(boolean enabled) {
        this.salahDetectionEnabled = enabled && salahDetectionEngine != null;
        // Start the next window from a clean slate; a timestamp left over from a previous
        // enable would otherwise gate the first sample against a stale reference.
        synchronized (salahAccelWindow) { lastSalahAccelSampleNs = 0L; }
        synchronized (salahGyroWindow) { lastSalahGyroSampleNs = 0L; }
        if (!enabled) {
            // Restore DND if disabling salah detection during active prayer
            disablePrayerDnd();
            isPrayerActive = false;
            currentSalahPosture = null;
            currentSalahConfidence = 0f;
            if (salahSequenceValidator != null) salahSequenceValidator.reset();
            if (salahDetectionEngine != null) salahDetectionEngine.reset();
        }
        Log.i(TAG, "🕌 Salah detection " + (this.salahDetectionEnabled ? "enabled" : "disabled"));
    }

    // ═══════════════════════════════════════════════════════
    // DO NOT DISTURB (DND) CONTROL DURING PRAYER
    // ═══════════════════════════════════════════════════════

    /**
     * Enable Do Not Disturb mode when prayer is detected.
     * Saves the current DND state so it can be restored after prayer ends.
     */
    private void enablePrayerDnd() {
        if (dndEnabledByPrayer) return; // Already enabled by prayer
        if (notificationManager == null) return;

        try {
            // Check if we have DND policy access
            if (!notificationManager.isNotificationPolicyAccessGranted()) {
                Log.w(TAG, "🔇 DND: Cannot enable - notification policy access not granted");
                return;
            }

            // Save current interruption filter to restore later
            previousInterruptionFilter = notificationManager.getCurrentInterruptionFilter();

            // Enable DND - allow only alarms (so prayer alarm can still ring if needed)
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS);
            dndEnabledByPrayer = true;
            Log.i(TAG, "🔇 DND ENABLED for prayer (previous filter: " + previousInterruptionFilter + ")");
        } catch (SecurityException e) {
            Log.e(TAG, "🔇 DND: SecurityException - policy access denied", e);
        } catch (Exception e) {
            Log.e(TAG, "🔇 DND: Failed to enable", e);
        }
    }

    /**
     * Disable Do Not Disturb mode when prayer ends.
     * Restores the previous DND state that was saved when prayer started.
     */
    private void disablePrayerDnd() {
        if (!dndEnabledByPrayer) return; // DND was not enabled by prayer
        if (notificationManager == null) return;

        try {
            if (!notificationManager.isNotificationPolicyAccessGranted()) {
                Log.w(TAG, "🔇 DND: Cannot restore - notification policy access not granted");
                dndEnabledByPrayer = false;
                return;
            }

            // Restore previous interruption filter
            notificationManager.setInterruptionFilter(previousInterruptionFilter);
            dndEnabledByPrayer = false;
            Log.i(TAG, "🔇 DND DISABLED - restored to previous filter: " + previousInterruptionFilter);
        } catch (SecurityException e) {
            Log.e(TAG, "🔇 DND: SecurityException - policy access denied", e);
        } catch (Exception e) {
            Log.e(TAG, "🔇 DND: Failed to restore", e);
        }
    }

    /** Whether salah detection is enabled */
    public boolean isSalahDetectionEnabled() {
        return salahDetectionEnabled;
    }

    /** Whether a prayer is currently being detected */
    public boolean isPrayerActive() {
        return isPrayerActive;
    }

    /** Get current detected salah posture (null if not praying) */
    public SalahPosture getCurrentSalahPosture() {
        return currentSalahPosture;
    }

    /** Get confidence of current salah posture detection */
    public float getCurrentSalahConfidence() {
        return currentSalahConfidence;
    }

    /** Get the current rak'ah count from the sequence validator */
    public int getCurrentRakahCount() {
        return salahSequenceValidator != null ? salahSequenceValidator.getCompletedRakahs() : 0;
    }

    /** Get the sequence validator for prayer state info */
    public SalahSequenceValidator getSalahSequenceValidator() {
        return salahSequenceValidator;
    }
}
