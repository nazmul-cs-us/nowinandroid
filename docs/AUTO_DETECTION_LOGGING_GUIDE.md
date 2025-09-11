# Auto-Detection System Logging Guide

This guide explains the comprehensive logging system implemented for the prayer times auto-detection feature, helping developers debug location-based calculation method and madhhab selection.

## Overview

The auto-detection system uses structured logging with emojis and clear categories to track the complete flow from GPS coordinates to final prayer settings. All logs use appropriate Android Log levels (DEBUG, INFO, WARN, ERROR) for proper filtering.

## Log Tags

### Primary Tags
- `CountryPrayerMethodService` - Core auto-detection logic
- `PrayerSettingsScreen` - UI interaction logging
- `PrayerTimesViewModel` - State management and coordination

### Log Categories

#### 🌍 Location Detection
- **Purpose**: Track GPS coordinate processing and reverse geocoding
- **Examples**:
  ```
  I/CountryPrayerMethodService: 🌍 Starting auto-detection for coordinates: 25.276987, 55.296249
  D/CountryPrayerMethodService: 🔍 Performing reverse geocoding...
  D/CountryPrayerMethodService: 🏳️ Geocoding successful: United Arab Emirates (AE)
  ```

#### 📦 Data Loading
- **Purpose**: Monitor JSON database loading and parsing
- **Examples**:
  ```
  D/CountryPrayerMethodService: 📦 Loading country prayer methods database...
  D/CountryPrayerMethodService: ✅ Country data loaded successfully in 45ms (80 countries, 25 methods)
  ```

#### 🔄 Method Mapping
- **Purpose**: Track calculation method and madhhab mapping
- **Examples**:
  ```
  D/CountryPrayerMethodService: 🔄 Mapping calculation method: Umm_al_Qura_University_Makkah → Umm al-Qura University, Makkah
  D/CountryPrayerMethodService: 🔄 Mapping madhhab: Maliki → STANDARD
  ```

#### 📊 Final Results
- **Purpose**: Show complete auto-detection results
- **Examples**:
  ```
  I/CountryPrayerMethodService: ✅ Auto-detection successful for United Arab Emirates
  D/CountryPrayerMethodService: 📊 Final Settings:
  D/CountryPrayerMethodService:    - Method: Umm al-Qura University, Makkah
  D/CountryPrayerMethodService:    - Madhhab: STANDARD
  D/CountryPrayerMethodService:    - Fajr Angle: 18.5°
  D/CountryPrayerMethodService:    - Isha: 90min offset
  ```

#### ⚠️ Warnings & Errors
- **Purpose**: Track issues and fallback scenarios
- **Examples**:
  ```
  W/CountryPrayerMethodService: ⚠️ Unable to detect country from coordinates
  E/CountryPrayerMethodService: ❌ Auto-detection failed - using ultimate fallback
  W/CountryPrayerMethodService: 🔧 Using Muslim World League method as safe default
  ```

#### 🔧 UI Interactions
- **Purpose**: Monitor settings screen auto-detection indicators
- **Examples**:
  ```
  I/PrayerSettingsScreen: 🔧 Auto-detection activated: Calculation Method for United Arab Emirates
  D/PrayerSettingsScreen: ⚙️ Setting changed: Calculation Method = Muslim World League → Umm al-Qura University, Makkah
  ```

## Debugging Common Issues

### Issue: Country Not Detected
**Look for these logs:**
```
W/CountryPrayerMethodService: ⚠️ Geocoder service not available on this device
W/CountryPrayerMethodService: ⚠️ Geocoding returned no results for location
E/CountryPrayerMethodService: ❌ Geocoding failed due to network/service issue
```

**Solutions:**
1. Check device has Google Play Services
2. Verify network connectivity
3. Ensure location permissions granted
4. Test with different coordinates

### Issue: Country Data Not Loading
**Look for these logs:**
```
E/CountryPrayerMethodService: ❌ Failed to read country_prayer_methods.json from assets
E/CountryPrayerMethodService: ❌ Failed to parse country data JSON
```

**Solutions:**
1. Verify `country_prayer_methods.json` exists in `app/src/main/assets/`
2. Validate JSON syntax
3. Check file permissions

### Issue: No Prayer Settings for Country
**Look for these logs:**
```
W/CountryPrayerMethodService: ❌ No prayer settings found for country code: XX
D/CountryPrayerMethodService: 🌐 Falling back to regional defaults
```

**Solutions:**
1. Add country to JSON database
2. Verify country code mapping
3. Check regional fallback logic

### Issue: Auto-Detection Not Triggering in UI
**Look for these logs:**
```
D/PrayerSettingsScreen: 🔧 Auto-detection status: Calculation Method = manual
```

**Check:**
1. `isMethodAutoDetected`, `isMadhhabAutoDetected` flags
2. `autoDetectedCountryName` value
3. Settings persistence logic

## Log Filtering Commands

### View All Auto-Detection Logs
```bash
adb logcat -s "CountryPrayerMethodService" "PrayerSettingsScreen" "PrayerTimesViewModel"
```

### View Only Errors and Warnings
```bash
adb logcat "*:W" -s "CountryPrayerMethodService" "PrayerSettingsScreen"
```

### View Location Detection Only
```bash
adb logcat -s "CountryPrayerMethodService" | grep "🌍\|🔍\|🏳️"
```

### View Final Results Only
```bash
adb logcat -s "CountryPrayerMethodService" | grep "✅\|📊"
```

### View Errors Only
```bash
adb logcat -s "CountryPrayerMethodService" | grep "❌\|⚠️"
```

## Performance Monitoring

### Database Loading Performance
Monitor these logs for performance issues:
```
D/CountryPrayerMethodService: ✅ Country data loaded successfully in 45ms (80 countries, 25 methods)
```

Normal loading times:
- **Good**: < 50ms
- **Acceptable**: 50-100ms  
- **Slow**: > 100ms (investigate JSON size/parsing)

### Geocoding Performance
Look for delays in these log sequences:
```
D/CountryPrayerMethodService: 🔍 Performing reverse geocoding...
D/CountryPrayerMethodService: 🏳️ Geocoding successful: Country Name (CODE)
```

Normal geocoding times:
- **Fast**: < 500ms
- **Normal**: 500ms-2s
- **Slow**: > 2s (network/service issues)

## Logging Best Practices

### For Developers
1. **Always check logs** when debugging auto-detection issues
2. **Filter by emoji** to quickly find specific log categories
3. **Monitor performance** logs during development
4. **Test edge cases** and verify error logging

### For QA Testing
1. Test in different countries/regions
2. Test with network connectivity issues
3. Test with location services disabled
4. Verify fallback scenarios work correctly

### For Production Debugging
1. Use log levels appropriately:
   - `DEBUG`: Detailed flow information
   - `INFO`: Important events and results
   - `WARN`: Recoverable issues
   - `ERROR`: Failures requiring attention

2. Use structured logging format for easy parsing
3. Include relevant context (coordinates, country codes, etc.)
4. Log both successful and failed operations

## Integration with Crash Reporting

The logging system is designed to work with crash reporting tools:

```kotlin
// Example: Integrate with Firebase Crashlytics
if (countryCode == null) {
    FirebaseCrashlytics.getInstance().log("Auto-detection failed: No country detected")
    logWarning("⚠️ Unable to detect country from coordinates")
}
```

## Future Enhancements

### Planned Logging Improvements
1. **Analytics Integration**: Track auto-detection success rates
2. **Performance Metrics**: Detailed timing information
3. **User Journey Tracking**: Complete flow from location to prayer times
4. **Remote Logging**: Send critical issues to remote monitoring

### Configuration Options
Future versions may include:
```kotlin
object LoggingConfig {
    var enableEmojiLogs = true
    var enablePerformanceLogging = true
    var enableVerboseGeocoding = false
    var logLevel = Log.DEBUG
}
```

This comprehensive logging system ensures complete visibility into the auto-detection process, making debugging efficient and monitoring reliable.