# ANR Prevention Guide for Now In Android

## Overview
This document outlines the measures implemented to prevent Application Not Responding (ANR) issues in the Now In Android project.

## What is ANR?
ANR (Application Not Responding) occurs when the main thread is blocked for more than 5 seconds, causing the system to show a dialog asking if the user wants to wait or force close the app.

## Root Causes Identified
1. **Service Startup Timeout**: Heavy initialization during service creation
2. **Main Thread Blocking**: Heavy operations on UI thread
3. **WorkManager Sync**: Automatic sync initialization
4. **Service Lifecycle**: Poor service management during app lifecycle changes

## Solutions Implemented

### 1. Background Thread Initialization
- Moved heavy initialization to background threads
- Set appropriate thread priorities to prevent main thread blocking
- Added proper error handling for background operations

### 2. Lazy Dependency Injection
- Used `dagger.Lazy<>` for heavy dependencies
- Implemented manual injection patterns for services
- Prevented blocking during service startup

### 3. Service Lifecycle Management
- Immediate `startForeground()` call to prevent timeout
- Automatic service shutdown after time limits
- Proper cleanup on service destruction
- Background service startup from MainActivity

### 4. Configuration Management
- Centralized ANR prevention settings in `AnrPreventionConfig`
- Easily configurable timeout values
- Performance monitoring options

## Key Configuration Values

```kotlin
// Service timeout settings
SERVICE_STARTUP_TIMEOUT_MS = 4000L // 4 seconds (below 5s ANR threshold)
MAX_SERVICE_RUNTIME_MS = 30 * 60 * 1000L // 30 minutes max service time
SERVICE_UPDATE_INTERVAL_MS = 60000L // 1 minute between updates

// Thread priority settings
BACKGROUND_THREAD_PRIORITY = Thread.NORM_PRIORITY - 1
SERVICE_THREAD_PRIORITY = Thread.NORM_PRIORITY - 2

// Initialization settings
ENABLE_LAZY_INITIALIZATION = true
ENABLE_BACKGROUND_SYNC = false // Disabled to prevent ANR
ENABLE_AUTO_SERVICE_START = true // Enabled for notifications
```

## Notification System

The notification system is now **ENABLED** and optimized for ANR prevention:

- **Prayer Notifications**: Live prayer time updates with high priority
- **Enhanced Features**: Sound, vibration, and notification lights
- **ANR Safe**: All notification operations run in background threads
- **Auto-cleanup**: Notifications automatically clean up after service stops
- **Test Helper**: `NotificationTestHelper` class for debugging notifications

## Files Modified

### Core Application
- `SubmissionApplication.kt` - Background initialization, thread management
- `MainActivity.kt` - Service lifecycle management
- `AnrPreventionConfig.kt` - Centralized configuration

### Service Layer
- `PrayerNotificationService.kt` - Immediate foreground start, timeout handling

## Best Practices

### 1. Always Use Background Threads
```kotlin
Thread {
    // Heavy initialization here
}.apply {
    priority = AnrPreventionConfig.getBackgroundThreadPriority()
    name = "BackgroundThread"
}.start()
```

### 2. Immediate Service Foreground
```kotlin
override fun onCreate() {
    super.onCreate()
    // Call startForeground() immediately to prevent ANR
    startForegroundImmediately()
}
```

### 3. Lazy Dependency Injection
```kotlin
@Inject
lateinit var lazyHeavyService: dagger.Lazy<HeavyService>

// Use only when needed
val service = lazyHeavyService.get()
```

### 4. Proper Service Cleanup
```kotlin
override fun onDestroy() {
    // Reset flags first
    isServiceRunning = false
    
    // Cancel coroutines
    serviceScope.cancel()
    
    // Stop foreground
    stopForeground(true)
    
    super.onDestroy()
}
```

## Testing ANR Prevention

### 1. Enable Strict Mode
```kotlin
if (isDebuggable()) {
    StrictMode.setThreadPolicy(
        Builder().detectAll().penaltyLog().build()
    )
}
```

### 2. Monitor Logs
Look for these log tags:
- `AnrPreventionConfig` - Configuration verification
- `PrayerNotificationService` - Service lifecycle
- `MainActivity` - Activity lifecycle
- `SubmissionApplication` - Application initialization

### 3. Performance Monitoring
- Check service startup times
- Monitor thread priorities
- Verify background operation completion

## Troubleshooting

### Common Issues

1. **Service Still Timing Out**
   - Check if `startForeground()` is called within 5 seconds
   - Verify heavy operations are moved to background threads
   - Ensure proper error handling

2. **Main Thread Blocking**
   - Use Strict Mode to detect violations
   - Move all heavy operations to background threads
   - Implement lazy initialization

3. **Service Not Starting**
   - Check service permissions in AndroidManifest
   - Verify service is started from appropriate lifecycle method
   - Ensure proper error handling in service creation

### Debug Commands
```bash
# Check for ANR in logs
adb logcat | grep -i anr

# Monitor service lifecycle
adb logcat | grep PrayerNotificationService

# Check thread priorities
adb shell ps -T | grep your.package.name
```

## Future Improvements

1. **Performance Metrics**: Add detailed timing measurements
2. **Automatic Recovery**: Implement automatic service restart on failure
3. **User Feedback**: Show progress indicators during heavy operations
4. **Configuration UI**: Allow users to adjust timeout values

## Conclusion

These ANR prevention measures ensure the app remains responsive during:
- App startup and initialization
- Service creation and management
- Heavy background operations
- App lifecycle changes

The implementation follows Android best practices and provides a robust foundation for preventing ANR issues.
