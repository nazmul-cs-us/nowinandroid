# Activity-Based Dua System

This system automatically detects user activities using multiple sensors and plays appropriate Islamic duas (prayers) based on the detected activity.

## Architecture Overview

The system consists of three main components:

1. **ActivityDetectionService.java** - Core sensor data collection and activity analysis
2. **DuaManager.java** - Manages dua selection and audio playback based on activity
3. **ActivityBasedDuaService.java** - Background service integration

## Features

### Sensor Integration
- **Accelerometer**: Detects movement patterns and vibration
- **Gyroscope**: Detects rotational movement and orientation changes  
- **GPS**: Detects speed and travel patterns for driving detection

### Activity Detection
The system can detect the following activities:
- **STATIONARY**: User is at rest (sitting, standing still)
- **WALKING**: User is walking at normal pace
- **RUNNING**: User is running or jogging
- **DRIVING**: User is in a vehicle
- **UNKNOWN**: Activity cannot be determined

### Dua Integration
Each activity type has associated duas:
- **Stationary**: General remembrance and contemplation duas
- **Walking**: Travel duas and protection prayers
- **Running**: Strength and endurance duas
- **Driving**: Travel safety and protection duas
- **Unknown**: General supplications

## Usage

### Basic Setup

1. **Add Permissions** (already added to AndroidManifest.xml):
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
```

2. **Start the Service**:
```kotlin
// In your Activity or Fragment
ActivityBasedDuaHelper.startActivityDetection(context)
```

3. **Stop the Service**:
```kotlin
ActivityBasedDuaHelper.stopActivityDetection(context)
```

### Advanced Usage

#### Direct Service Integration
```java
// Create and start activity detection directly
ActivityDetectionService detectionService = new ActivityDetectionService(context);

detectionService.startDetection(new ActivityDetectionService.ActivityChangeCallback() {
    @Override
    public void onActivityChanged(ActivityType newActivity, ActivityType previousActivity) {
        // Handle activity change
        Log.i("Activity", "Changed from " + previousActivity + " to " + newActivity);
    }
});
```

#### Custom Dua Management
```java
// Use DuaManager directly for custom dua handling
DuaManager duaManager = new DuaManager(context);
duaManager.start();
```

## Technical Details

### Sensor Parameters

- **Sampling Rate**: ~60Hz for accelerometer and gyroscope
- **Location Updates**: Every 1 second or 1 meter movement
- **Analysis Window**: 3 seconds of data for activity determination
- **Analysis Frequency**: Every 2 seconds

### Activity Detection Thresholds

```java
// Acceleration thresholds (m/s²)
WALKING_THRESHOLD = 2.0
RUNNING_THRESHOLD = 4.0
STATIONARY_ACCEL_THRESHOLD = 0.5

// Speed threshold (m/s)
DRIVING_SPEED_THRESHOLD = 5.0  // 18 km/h
```

### Data Analysis

The system uses machine learning-inspired algorithms:

1. **Acceleration Analysis**: Magnitude and variance of accelerometer data
2. **Movement Patterns**: Gyroscope data for rotation detection
3. **Speed Analysis**: GPS speed data for travel detection
4. **Temporal Filtering**: Multi-second windows for stable detection

## Adding Audio Files

To play actual dua audio files:

1. **Add Audio Files**: Place MP3/WAV files in `res/raw/` directory
2. **Update DuaManager**: Modify the `initializeDuaMap()` method:
```java
duaMap.put(ActivityDetectionService.ActivityType.WALKING,
    new DuaInfo(
        "Bismillahi tawakkaltu 'alallah",
        R.raw.walking_dua,  // Add your audio file
        "Travel dua for safe walking"
    )
);
```

## Troubleshooting

### Common Issues

1. **No Activity Detection**:
   - Check permissions in Android settings
   - Ensure location services are enabled
   - Verify sensor availability on device

2. **Inaccurate Detection**:
   - Device-specific sensor calibration may be needed
   - Adjust thresholds in `ActivityDetectionService.java`
   - Increase analysis window for more stable detection

3. **Audio Not Playing**:
   - Check audio file paths in `DuaManager.java`
   - Verify audio permissions
   - Test with shorter audio files first

### Debug Logging

The system provides extensive logging:
```bash
adb logcat | grep -E "(ActivityDetection|DuaManager|ActivityBasedDua)"
```

## Performance Considerations

- **Battery Usage**: Sensors are optimized for minimal battery drain
- **CPU Usage**: Data analysis runs on background threads
- **Memory**: Automatic cleanup of old sensor data prevents memory leaks

## Customization

### Adding New Activities
1. Add new enum value to `ActivityType`
2. Update detection algorithm in `determineActivity()`
3. Add corresponding dua in `DuaManager.initializeDuaMap()`

### Modifying Thresholds
Adjust detection sensitivity by changing threshold constants in `ActivityDetectionService.java`

### Custom Dua Selection
Override `DuaManager` to implement custom dua selection logic based on time, location, or user preferences.
