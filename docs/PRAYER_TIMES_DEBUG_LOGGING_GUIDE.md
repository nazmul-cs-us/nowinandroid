# Prayer Times Debug Logging Guide

This comprehensive guide covers the enhanced logging system for debugging prayer time calculations, cache operations, and settings management in the Islamic Prayer Times application.

## 🎯 Overview

The prayer times system includes detailed logging across all major components to provide complete visibility into:
- Prayer time calculations and caching
- Settings storage and retrieval 
- Cache validation and performance
- User settings changes and persistence
- Error tracking and performance monitoring

## 📊 Enhanced Components

### 1. Cache Operations (`PrayerSettingsRepository.kt`)
**Location**: Lines 2024-2200+
**Enhanced Logging**:
- ✅ Detailed cache retrieval with timing (`getCachedPrayerTimes()`)
- ✅ Date validation and completeness checks
- ✅ Prayer time validation with error reporting  
- ✅ Cache storage logging with data verification (`cachePrayerTimes()`)

### 2. Settings Dialog Operations (`PrayerSettingsDialog.kt`)
**Location**: Lines 198-250
**Enhanced Logging**:
- ✅ User settings change detection with diff reporting
- ✅ Save operation timing and error tracking
- ✅ Haptic feedback confirmation
- ✅ Background operation monitoring

### 3. Repository Settings Update (`PrayerSettingsRepository.kt`)
**Location**: Lines 738-770
**Enhanced Logging**:
- ✅ Complete settings breakdown logging
- ✅ Separate preference structure conversion tracking
- ✅ Operation timing and success confirmation
- ✅ Auto-detection status reporting

## 🔍 Debug Commands

### Complete Prayer Calculation Flow
Monitor the entire prayer time calculation, caching, and settings flow:
```bash
adb logcat -s "PrayerTimesCalculator" "PrayerTimeCalculator" "PrayerSettingsRepository" "PrayerSettingsDialog"
```

### Cache Operations Only
Focus on cache storage, retrieval, and validation:
```bash
adb logcat -s "PrayerSettingsRepository" | grep "💾\|CACHE\|cached"
```

### Settings Changes Only
Track user settings modifications and persistence:
```bash
adb logcat -s "PrayerSettingsDialog" "PrayerSettingsRepository" | grep "📝\|SETTINGS\|UPDATE"
```

### Timing and Performance
Monitor operation performance and identify bottlenecks:
```bash
adb logcat -s "PrayerSettingsRepository" "PrayerSettingsDialog" | grep "⏱️\|ms\|time"
```

### Error Tracking
Identify failures and error conditions:
```bash
adb logcat -s "PrayerSettingsRepository" "PrayerSettingsDialog" | grep "❌\|ERROR\|Failed"
```

### Cache Health Monitoring
Check cache hit/miss rates and validation:
```bash
adb logcat -s "PrayerSettingsRepository" | grep "Cache\|cached\|💾" | grep -E "SUCCESS|FAILED|validation|stale"
```

### Settings Save Operations
Monitor all settings persistence operations:
```bash
adb logcat -s "PrayerSettingsRepository" | grep "WRITE\|SAVE\|💾.*settings"
```

### Prayer Calculation Performance
Track prayer calculation timing and success rates:
```bash
adb logcat -s "PrayerTimesCalculator" | grep -E "⏱️.*ms|COMPLETE|calculation.*time"
```

## 📋 Log Format Guide

### Emoji Prefixes
The logs use consistent emoji prefixes for easy identification:

| Emoji | Purpose | Example |
|-------|---------|---------|
| **🌅** | Prayer calculation operations | `🌅 COMPREHENSIVE PRAYER TIMES CALCULATION` |
| **💾** | Cache storage/retrieval | `💾 CACHE RETRIEVAL OPERATION` |
| **📝** | Settings changes | `📝 USER SETTINGS CHANGE DETECTED` |
| **🔄** | Processing operations | `🔄 Processing user settings modification...` |
| **✅** | Success confirmations | `✅ Cache date validation passed` |
| **❌** | Errors and failures | `❌ Cache data is incomplete` |
| **⏱️** | Timing information | `⏱️ Cache retrieval completed in 15ms` |
| **📊** | Data summaries | `📊 Cache completeness check:` |
| **🎯** | Operation completion | `🎯 Settings change processing completed` |
| **📍** | Location operations | `📍 Location data: lat=25.276, lon=55.296` |
| **⚙️** | Method changes | `⚙️ Calculation method changed: ISNA → MWL` |
| **⚖️** | Madhhab changes | `⚖️ Asr madhhab changed: STANDARD → HANAFI` |
| **📐** | Angle changes | `📐 Custom Isha angle changed: 18.0° → 15.0°` |
| **📳** | Haptic feedback | `📳 Haptic feedback triggered` |

### Log Structure
Each major operation follows this structure:
```
[EMOJI] OPERATION TITLE
=======================================================
🔄 [Context and operation description]
📊 [Data being processed]
⏱️ [Timing information]
✅/❌ [Result status]
```

## 🚀 Debugging Workflow

### 1. Start Monitoring
Choose the appropriate debug command based on what you want to investigate:
- **Full flow**: Use complete prayer calculation flow command
- **Specific issue**: Use targeted commands (cache, settings, etc.)

### 2. Trigger Actions
Perform the actions you want to debug:
- Open prayer times screen
- Change calculation method
- Modify custom angles
- Pull to refresh
- Open/close settings dialog

### 3. Analyze Output
Look for the log patterns:
- **Operation start**: Look for titled sections with `=` separators
- **Progress**: Follow 🔄 processing steps
- **Data**: Check 📊 data summaries for values
- **Results**: Verify ✅ success or ❌ failure outcomes
- **Timing**: Monitor ⏱️ performance metrics

### 4. Common Issues to Check

#### Cache Issues
```bash
# Check for stale cache
adb logcat -s "PrayerSettingsRepository" | grep "stale\|validation failed"

# Check for incomplete cache
adb logcat -s "PrayerSettingsRepository" | grep "incomplete\|missing required"
```

#### Settings Issues
```bash
# Check for settings save failures
adb logcat -s "PrayerSettingsDialog" | grep "save failed\|ERROR"

# Check for settings changes not persisting
adb logcat -s "PrayerSettingsRepository" | grep "WRITE.*settings\|UPDATE.*COMPLETE"
```

#### Performance Issues
```bash
# Check for slow operations (>100ms)
adb logcat -s "PrayerSettingsRepository" "PrayerSettingsDialog" | grep "ms" | grep -E "1[0-9][0-9]ms|[2-9][0-9][0-9]ms"
```

## 📖 Sample Log Outputs

### Successful Cache Retrieval
```
💾 CACHE RETRIEVAL OPERATION
================================================================================
🔍 Checking SharedPreferences for cached prayer times
📅 Found cached date: 2025-10-10
📅 Today's date: 2025-10-10
🔄 Date comparison: cached=2025-10-10, today=2025-10-10, isToday=true
✅ Cache date validation passed - data is for today
🔍 Checking data completeness...
📊 Cache completeness check:
  - Prayer times: ✅
  - Location data: ✅
  - All required data: ✅
🕐 Reading cached prayer times (as minutes since midnight):
✅ All prayer times are valid
⏱️ Cache retrieval completed in 12ms
```

### Settings Change Operation
```
📝 USER SETTINGS CHANGE DETECTED
======================================================================
🔄 Processing user settings modification...
⚙️ Calculation method changed: MUSLIM_WORLD_LEAGUE → ISNA
📳 Haptic feedback triggered
💾 Starting background save operation...
🔄 Calling repository.updateSettings()...
✅ Repository update completed successfully
✅ Local UI state updated
⏱️ Complete save operation took 45ms
🎯 Settings change processing completed successfully
```

### Cache Miss (Stale Data)
```
💾 CACHE RETRIEVAL OPERATION
================================================================================
🔍 Checking SharedPreferences for cached prayer times
📅 Found cached date: 2025-10-09
📅 Today's date: 2025-10-10
🔄 Date comparison: cached=2025-10-09, today=2025-10-10, isToday=false
❌ Cached data is stale - cached date is 2025-10-09 but today is 2025-10-10
🗑️ Cache validation failed - will need fresh calculation
⏱️ Cache retrieval completed in 8ms
```

## 🛠️ Advanced Debugging

### Filter by Log Level
```bash
# Info level and above
adb logcat "*:I" -s "PrayerSettingsRepository"

# Warning level and above (errors only)
adb logcat "*:W" -s "PrayerSettingsRepository"
```

### Continuous Monitoring
```bash
# Monitor with timestamps
adb logcat -s "PrayerSettingsRepository" "PrayerSettingsDialog" -v time

# Monitor with thread info
adb logcat -s "PrayerSettingsRepository" "PrayerSettingsDialog" -v threadtime
```

### Export Logs for Analysis
```bash
# Save to file
adb logcat -s "PrayerSettingsRepository" "PrayerSettingsDialog" > prayer_debug.log

# Filter and save specific operations
adb logcat -s "PrayerSettingsRepository" | grep "💾\|📝" > cache_and_settings.log
```

## 📚 Related Documentation

- **[Prayer Times Technical Guide](PRAYER_TIMES_TECHNICAL_GUIDE.md)** - Overall architecture
- **[Prayer Calculation Methodology](PRAYER_CALCULATION_METHODOLOGY.md)** - Calculation algorithms
- **[Interactive Prayer Dial Guide](INTERACTIVE_PRAYER_DIAL_GUIDE.md)** - UI component debugging
- **[Auto-Detection Logging Guide](AUTO_DETECTION_LOGGING_GUIDE.md)** - Country detection debugging

## 🔧 Troubleshooting

### Common Log Patterns

| Issue | Log Pattern | Solution |
|-------|-------------|----------|
| Cache always misses | `❌ No cached prayer date found` | Check SharedPreferences persistence |
| Settings not saving | `❌ Settings save failed` | Check file permissions and storage |
| Slow performance | `⏱️ operation took 500ms` | Check database/network operations |
| Invalid prayer times | `❌ Invalid prayer times found` | Check calculation algorithm |

### Performance Benchmarks
- **Cache retrieval**: < 50ms (optimal: < 20ms)
- **Settings save**: < 100ms (optimal: < 50ms)
- **Prayer calculation**: < 3000ms (with timeout)
- **UI update**: < 16ms (60fps target)

---

**Last Updated**: October 2025  
**Version**: Enhanced Prayer Times Logging System v1.0