# Qibla Compass Integration Guide

This guide shows how to integrate the newly created Qibla compass feature with your existing prayer times app.

## 📁 Files Created

The Qibla compass feature has been integrated into your app with the following structure:

```
app/src/main/kotlin/com/starception/submission/feature/qiblacompass/
├── QiblaCompassScreen.kt           # Main compass screen UI
├── QiblaCompassViewModel.kt        # State management and business logic
├── components/
│   ├── CompassRose.kt             # Custom-drawn compass UI component
│   └── QiblaCompassButton.kt      # Navigation buttons for accessing compass
├── data/
│   └── QiblaCalculator.kt         # Qibla direction calculation logic
├── navigation/
│   └── QiblaCompassNavigation.kt  # Navigation integration
├── sensors/
│   └── CompassSensorManager.kt    # Compass sensor management
└── utils/
    └── CompassUtils.kt            # Utility functions and helpers
```

## 🔧 Integration Steps

### Step 1: Add Navigation to Main Navigation Graph

Add the compass screen to your main navigation setup. Find your main navigation file (likely in `MainActivity.kt` or a navigation module) and add:

```kotlin
import com.starception.submission.feature.qiblacompass.navigation.qiblaCompassScreen

// In your NavHost setup:
NavHost(navController, startDestination = PrayerTimesRoute) {
    prayerTimesScreen()
    qiblaCompassScreen() // Add this line
}
```

### Step 2: Add Compass Button to Prayer Times Screen

Update your `PrayerTimesScreen.kt` to include the compass button. Add this import:

```kotlin
import com.starception.submission.feature.qiblacompass.components.QiblaCompassButton
import com.starception.submission.feature.qiblacompass.navigation.navigateToQiblaCompass
```

Then add the button to your prayer times screen layout:

```kotlin
// Add this after your existing prayer time cards
QiblaCompassButton(
    onClick = {
        // Navigate to compass - you'll need to pass navController or use a callback
        navController.navigateToQiblaCompass()
    },
    modifier = Modifier.padding(horizontal = 16.dp)
)
```

### Step 3: Add Required Permissions

Add compass and location permissions to your `AndroidManifest.xml`:

```xml
<!-- These may already exist from your prayer times feature -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Add these new permissions for compass functionality -->
<uses-feature 
    android:name="android.hardware.sensor.compass" 
    android:required="false" />
<uses-feature 
    android:name="android.hardware.sensor.accelerometer" 
    android:required="false" />
```

### Step 4: Add Hilt Dependencies

Ensure your dependency injection is set up. Add to your module that provides prayer-related dependencies:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object CompassModule {
    
    @Provides
    @Singleton
    fun provideCompassSensorManager(
        @ApplicationContext context: Context
    ): CompassSensorManager = CompassSensorManager(context)
    
    @Provides
    @Singleton
    fun provideQiblaCalculator(): QiblaCalculator = QiblaCalculator()
}
```

### Step 5: Update App-Level Dependencies (if needed)

If you don't already have these dependencies, add them to your `build.gradle.kts`:

```kotlin
dependencies {
    // Permissions handling (may already exist)
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    
    // Hilt for dependency injection (may already exist)
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    
    // Navigation (may already exist)
    implementation("androidx.navigation:navigation-compose:2.7.5")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
}
```

## 🎯 Features Overview

### What the Compass Provides

1. **Real-time Compass**: Shows magnetic north using device sensors
2. **Qibla Direction**: Calculates and displays direction to Mecca
3. **Location Integration**: Uses same location system as your prayer times
4. **Material 3 Design**: Consistent with your existing app design
5. **Sensor Management**: Handles compass calibration and accuracy
6. **Permission Handling**: Graceful location permission management

### Key Improvements Over Original Compass App

1. **Modern Sensors**: Uses accelerometer + magnetometer instead of deprecated orientation sensor
2. **Islamic Integration**: Shows Qibla direction, not just magnetic north
3. **Better UI**: Custom-drawn compass rose with Jetpack Compose
4. **Location Sharing**: Integrates with your existing location services
5. **Error Handling**: Comprehensive error handling and fallbacks
6. **Battery Optimized**: Proper sensor lifecycle management

## 🛠️ Customization Options

### Compass Colors

Edit colors in `CompassRose.kt`:

```kotlin
private data class CompassColors(
    val compassNeedle: Color,     // Change compass needle color
    val qiblaIndicator: Color,    // Change Qibla direction color
    val background: Color,        // Change compass background
    // ... other colors
)
```

### Default Location

Change the fallback location in `QiblaCalculator.kt`:

```kotlin
private const val KAABA_LATITUDE = 21.4225   // Kaaba coordinates (don't change)
private const val KAABA_LONGITUDE = 39.8262  // Kaaba coordinates (don't change)
```

### Compass Button Placement

Choose different button styles in your prayer times screen:

```kotlin
// Full-width button (recommended)
QiblaCompassButton(onClick = { ... })

// Compact button for smaller spaces
CompactQiblaCompassButton(onClick = { ... })

// Floating action button
FloatingQiblaCompassButton(onClick = { ... })
```

## 📱 User Experience Flow

1. **From Prayer Times**: User taps "Qibla Compass" button
2. **Permission Check**: App requests location permission if needed
3. **Compass Loads**: Shows compass with current heading and Qibla direction
4. **Real-time Updates**: Compass updates as user moves device
5. **Calibration**: App guides user through calibration if needed
6. **Location Updates**: Uses GPS from prayer times for accuracy

## 🔍 Troubleshooting

### Common Issues

1. **No Compass Sensors**: Some devices don't have magnetometer/accelerometer
   - Solution: App gracefully shows "sensors not available" message

2. **Poor Accuracy**: Compass needs calibration
   - Solution: App shows calibration instructions automatically

3. **Location Issues**: No GPS or permission denied
   - Solution: App uses cached location from prayer times system

4. **Build Errors**: Missing dependencies or imports
   - Solution: Follow integration steps above carefully

### Testing on Device

- **Emulator**: Compass won't work in emulator (no sensors)
- **Physical Device**: Test on real Android device with sensors
- **Calibration**: Test compass calibration by moving device in figure-8

## 🧭 Technical Details

### Calculation Accuracy

- Uses precise Kaaba coordinates: 21.4225°N, 39.8262°E
- Implements great circle bearing calculation
- Accounts for Earth's curvature for accuracy
- Handles edge cases like international date line crossing

### Sensor Fusion

- Combines accelerometer and magnetometer data
- Applies low-pass filtering for smooth readings
- Implements proper sensor lifecycle management
- Handles sensor availability gracefully

### Performance

- Updates at max 20 FPS to save battery
- Automatic sensor unregistration on screen exit
- Smart location updates only when user moves >100m
- Efficient UI recomposition with State management

## 📋 Next Steps

After integration, consider these enhancements:

1. **Settings Screen**: Add compass settings to your app settings
2. **Widget Support**: Create home screen widget for quick Qibla check
3. **Apple Watch**: Add complications for watchOS version
4. **Offline Maps**: Integrate with offline mapping for better location context
5. **Prayer Reminders**: Combine with notification system for prayer alerts

## 🤝 Integration Complete

Once you've followed these steps, your prayer times app will have a fully functional Qibla compass that:

- ✅ Shows accurate direction to Mecca
- ✅ Uses modern compass sensors
- ✅ Integrates with existing location system
- ✅ Follows Material 3 design guidelines
- ✅ Handles errors and edge cases gracefully
- ✅ Provides excellent user experience for Muslim users

The compass feature enhances your prayer times app by providing the essential tool Muslims need to face the correct direction during their daily prayers.