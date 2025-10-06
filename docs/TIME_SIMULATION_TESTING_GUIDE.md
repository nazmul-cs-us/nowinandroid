# Time Simulation Testing Guide

## Overview

This guide explains how to simulate different times to test the Islamic prayer notification system, especially for edge cases like cross-day periods, negative elapsed time scenarios, and prayer phase transitions.

## Table of Contents
- [Quick Start](#quick-start)
- [Simulation Methods](#simulation-methods)
- [Critical Test Scenarios](#critical-test-scenarios)
- [Implementation Details](#implementation-details)
- [Debugging & Monitoring](#debugging--monitoring)
- [Troubleshooting](#troubleshooting)

## Quick Start

### Method 1: Code-Based Time Simulation (Recommended)

**Step 1: Modify PrayerNotificationService.kt**
```kotlin
// In getCurrentPrayerData() method around line 692
val now = LocalTime.of(1, 0) // Simulate 1:00 AM
// val now = LocalTime.now() // Comment out real time
```

**Step 2: Build and Install**
```bash
./gradlew assembleDemoDebug
adb -s 4B221FDAP002T6 install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk
```

**Step 3: Launch and Monitor**
```bash
adb shell monkey -p com.starception.submission.demo.debug -c android.intent.category.LAUNCHER 1
adb logcat -s "PrayerNotificationService" | grep -E "🕐|🚨|✅|Cross-day"
```

## Simulation Methods

### Method 1: Direct Code Modification

**Locations to modify:**

1. **Main Prayer Data Calculation** (`getCurrentPrayerData()` method):
```kotlin
// Line ~692 in PrayerNotificationService.kt
val now = LocalTime.of(HOUR, MINUTE) // Replace with test time
// val now = LocalTime.now() // Comment out
```

2. **Prayer Progress Calculation** (`calculatePrayerProgress()` method):
```kotlin
// Line ~XXX in PrayerNotificationService.kt  
val now = LocalTime.of(HOUR, MINUTE) // Replace with test time
// val now = LocalTime.now() // Comment out
```

3. **Time Formatting** (`formatTimeRemaining()` method):
```kotlin
// Line ~XXX in PrayerNotificationService.kt
val now = LocalTime.of(HOUR, MINUTE) // Replace with test time
// val now = LocalTime.now() // Comment out
```

**Example Implementations:**

```kotlin
// Test 1:00 AM (cross-day Isha → Fajr)
val now = LocalTime.of(1, 0)

// Test 11:30 PM (late night Isha period)  
val now = LocalTime.of(23, 30)

// Test 4:00 AM (pre-Fajr period)
val now = LocalTime.of(4, 0)

// Test prayer transition (e.g., exactly at Asr time)
val now = LocalTime.of(15, 41) // If Asr is at 15:41

// Test 2-hour threshold crossing
val now = LocalTime.of(21, 43) // If Isha is at 19:43 + 2 hours
```

### Method 2: Centralized Time Provider (Advanced)

**Create a TimeProvider class:**

```kotlin
// Create app/src/main/kotlin/com/starception/submission/util/TimeProvider.kt
object TimeProvider {
    private var simulatedTime: LocalTime? = null
    
    fun setSimulatedTime(time: LocalTime?) {
        simulatedTime = time
    }
    
    fun now(): LocalTime {
        return simulatedTime ?: LocalTime.now()
    }
    
    fun isSimulating(): Boolean = simulatedTime != null
}
```

**Usage in PrayerNotificationService:**
```kotlin
// Replace all LocalTime.now() calls with:
val now = TimeProvider.now()

// To simulate:
TimeProvider.setSimulatedTime(LocalTime.of(1, 0))

// To restore normal time:
TimeProvider.setSimulatedTime(null)
```

## Critical Test Scenarios

### Scenario 1: Cross-Day Period (Isha → Fajr)

**Test Times:**
- `LocalTime.of(23, 0)` - 11:00 PM (same day, after Isha)
- `LocalTime.of(23, 30)` - 11:30 PM (late Isha period)
- `LocalTime.of(0, 30)` - 12:30 AM (past midnight)
- `LocalTime.of(1, 0)` - 1:00 AM (deep night)
- `LocalTime.of(4, 30)` - 4:30 AM (pre-Fajr)

**Expected Results:**
```
✅ Current Prayer: Isha
✅ Content: "X hours Xm since Isha" (positive values)
✅ Next Prayer: Fajr  
✅ Progress: Realistic percentage (20-80%)
✅ Phase: GO_TO_MOSQUE / BEST_TIME / MAKE_TIME
```

**What to Verify:**
- No negative elapsed times (e.g., "-111m since Isha")
- Correct prayer identification (Isha, not Fajr)
- Accurate progress calculation across midnight
- Proper phase progression

### Scenario 2: Prayer Time Boundaries

**Test Times:**
```kotlin
// Exactly at prayer times (if Fajr = 05:07)
LocalTime.of(5, 7)   // Exactly at Fajr
LocalTime.of(5, 6)   // 1 minute before Fajr
LocalTime.of(5, 8)   // 1 minute after Fajr

// 2-hour thresholds (if Isha = 19:43)
LocalTime.of(21, 43) // Exactly 2 hours after Isha
LocalTime.of(21, 44) // 1 minute past threshold
```

**Expected Results:**
- Smooth transitions between prayer periods
- Correct threshold behavior (2-hour rule for Isha)
- No negative elapsed times at boundaries

### Scenario 3: Edge Cases

**Problematic Times:**
```kotlin
// Times that historically caused issues
LocalTime.of(1, 0)    // Reported "-247m since Fajr" 
LocalTime.of(23, 10)  // Reported "-111m since Isha"
LocalTime.of(0, 0)    // Exactly midnight
LocalTime.of(12, 0)   // Exactly noon
```

**What to Test:**
- System behavior during these exact times
- Negative elapsed time prevention
- Cross-day calculation accuracy

## Implementation Details

### Step-by-Step Implementation

**1. Backup Original Code:**
```bash
git stash push -m "Backup before time simulation testing"
```

**2. Add Simulation Code:**
```kotlin
// In PrayerNotificationService.kt, around line 692
// SIMULATION: Test specific time scenario
val now = LocalTime.of(1, 0) // <-- Change this line
Log.d(TAG, "🕐 SIMULATING TIME: $now")

// Comment out the real time:
// val now = LocalTime.now()
```

**3. Add Debugging Logs:**
```kotlin
Log.d(TAG, "🔍 PRAYER DETECTION:")
Log.d(TAG, "   Current time: $now")
Log.d(TAG, "   Isha time: ${prayerTimes.isha}")
Log.d(TAG, "   Fajr time: ${prayerTimes.fajr}")
Log.d(TAG, "   Detected current prayer: ${currentPrayer?.name ?: "None"}")
```

**4. Build and Test:**
```bash
./gradlew assembleDemoDebug
adb -s 4B221FDAP002T6 install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk
adb shell monkey -p com.starception.submission.demo.debug -c android.intent.category.LAUNCHER 1
```

**5. Monitor Results:**
```bash
# Monitor simulation logs
adb logcat -s "PrayerNotificationService" | grep "🕐 SIMULATING"

# Monitor prayer detection
adb logcat -s "PrayerNotificationService" | grep -E "Current Prayer|Next Prayer"

# Monitor cross-day scenarios  
adb logcat -s "PrayerNotificationService" | grep -E "Cross-day|🌙|✅ OVERRIDE"

# Monitor negative time detection
adb logcat -s "PrayerNotificationService" | grep "🚨 NEGATIVE"
```

**6. Verify Notification:**
```bash
# Take screenshot to see notification
adb shell screencap -p > /tmp/prayer_test_screenshot.png

# Or check notification shade manually on device
```

**7. Restore Normal Time:**
```kotlin
// Uncomment real time:
val now = LocalTime.now()

// Comment out simulation:
// val now = LocalTime.of(1, 0) // SIMULATION DISABLED
```

## Debugging & Monitoring

### Essential Log Commands

**Monitor All Prayer Logs:**
```bash
adb logcat -s "PrayerNotificationService"
```

**Monitor Specific Scenarios:**
```bash
# Cross-day detection
adb logcat -s "PrayerNotificationService" | grep -E "🌙|Cross-day|OVERRIDE"

# Negative time detection
adb logcat -s "PrayerNotificationService" | grep -E "🚨|NEGATIVE|safe elapsed"

# Prayer phase changes
adb logcat -s "PrayerNotificationService" | grep -E "GO_TO_MOSQUE|BEST_TIME|MAKE_TIME"

# Time simulation verification
adb logcat -s "PrayerNotificationService" | grep "🕐 SIMULATING"
```

**Monitor Google Sample Notifications:**
```bash
adb logcat -s "GoogleSampleNotificationManager"
```

### Log Patterns to Look For

**✅ Successful Cross-Day Detection:**
```
🕐 SIMULATING TIME: 01:00
🚨 INCORRECT DETECTION: System thinks Fajr is current at 01:00 but Fajr is at 05:07:01
🔧 FIXING: Checking if we're in overnight Isha→Fajr period
Cross-day scenario: 01:00 is before Fajr (05:07:01)
✅ OVERRIDE: Treating this as Isha period, not Fajr
Current Prayer: Isha
```

**✅ Negative Time Protection:**
```
🚨 NEGATIVE ELAPSED TIME IN ACTIVE PRAYER!
Prayer start: 19:43:13
Current time: 01:00
Elapsed calculation: Duration.between(19:43:13, 01:00) = -XXXm
Using safe elapsed time: 0m instead of -XXXm
```

**❌ Issues to Fix:**
```
Current Prayer: Fajr  // Wrong at 1:00 AM
-247 minutes since Fajr  // Negative elapsed time
Next Prayer: None  // Missing next prayer
```

## Troubleshooting

### Common Issues

**1. Simulation Not Working**
```bash
# Check if you modified all LocalTime.now() calls
grep -n "LocalTime.now()" app/src/main/kotlin/com/starception/submission/services/PrayerNotificationService.kt

# Make sure you rebuilt and reinstalled
./gradlew assembleDemoDebug
adb -s 4B221FDAP002T6 install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk
```

**2. No Logs Appearing**
```bash
# Check if service is running
adb logcat -s "PrayerNotificationService" -d | tail -10

# Launch app to trigger service
adb shell monkey -p com.starception.submission.demo.debug -c android.intent.category.LAUNCHER 1

# Wait for service to start
sleep 10 && adb logcat -s "PrayerNotificationService" -d | tail -20
```

**3. Notification Not Updating**
```bash
# Check if GoogleSampleNotificationManager is being called
adb logcat -s "GoogleSampleNotificationManager" -d | tail -5

# Clear notification to force refresh
adb shell cmd notification clear_all

# Restart app
adb shell am force-stop com.starception.submission.demo.debug
adb shell monkey -p com.starception.submission.demo.debug -c android.intent.category.LAUNCHER 1
```

**4. Device Connection Issues**
```bash
# Check device connection
adb devices

# If no devices, reconnect
adb kill-server
adb start-server
adb devices
```

### Testing Checklist

**Before Testing:**
- [ ] Backup current code (`git stash`)
- [ ] Device connected (`adb devices`)
- [ ] Target specific test scenario
- [ ] Clear existing notifications

**During Testing:**
- [ ] Simulation time logged correctly
- [ ] Prayer detection shows expected results  
- [ ] No negative elapsed times
- [ ] Notification content matches expectations
- [ ] Progress percentage realistic (0-100%)

**After Testing:**
- [ ] Screenshot saved for verification
- [ ] Logs captured for analysis
- [ ] Simulation code removed/commented
- [ ] Normal time restored
- [ ] App tested with real time

### Test Automation Script

**Create a test script:**
```bash
#!/bin/bash
# prayer_time_test.sh

echo "🧪 Starting Prayer Time Simulation Test"

# Test scenarios
SCENARIOS=(
    "1:0"    # 1:00 AM - Cross-day
    "23:30"  # 11:30 PM - Late Isha  
    "4:30"   # 4:30 AM - Pre-Fajr
    "0:0"    # Midnight
)

for time in "${SCENARIOS[@]}"; do
    HOUR=$(echo $time | cut -d: -f1)
    MINUTE=$(echo $time | cut -d: -f2)
    
    echo "📱 Testing time: $HOUR:$MINUTE"
    
    # Update simulation time in code (requires manual edit)
    echo "   Manual step: Set LocalTime.of($HOUR, $MINUTE) in code"
    echo "   Press Enter when ready..."
    read
    
    # Build and install
    ./gradlew assembleDemoDebug
    adb -s 4B221FDAP002T6 install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk
    
    # Launch and wait
    adb shell monkey -p com.starception.submission.demo.debug -c android.intent.category.LAUNCHER 1
    sleep 15
    
    # Capture logs
    adb logcat -s "PrayerNotificationService" -d | grep -E "🕐|Current Prayer|🚨|✅" > "test_logs_${HOUR}_${MINUTE}.txt"
    
    # Take screenshot  
    adb shell screencap -p > "screenshot_${HOUR}_${MINUTE}.png"
    
    echo "   ✅ Test completed for $HOUR:$MINUTE"
    echo "   📋 Logs: test_logs_${HOUR}_${MINUTE}.txt"
    echo "   📱 Screenshot: screenshot_${HOUR}_${MINUTE}.png"
    echo ""
done

echo "🎉 All tests completed!"
```

## Best Practices

### Do's
- ✅ Test critical times (1 AM, 11:30 PM, midnight)
- ✅ Verify both notification content and logs
- ✅ Test cross-day scenarios thoroughly
- ✅ Check for negative elapsed times
- ✅ Save screenshots for visual verification
- ✅ Restore normal time after testing

### Don'ts  
- ❌ Don't commit simulation code to production
- ❌ Don't test only one scenario
- ❌ Don't skip log verification
- ❌ Don't forget to rebuild after code changes
- ❌ Don't ignore negative time protection logs

### Time Simulation Best Practices
1. **Always test edge cases** (midnight, prayer boundaries)
2. **Verify cross-day calculations** (Isha → Fajr overnight)
3. **Check negative time protection** works correctly
4. **Test prayer phase transitions** (GO_TO_MOSQUE → BEST_TIME → MAKE_TIME)
5. **Monitor both logs and UI** for consistency
6. **Clean up simulation code** before production builds

---

## Quick Reference

**Common Test Times:**
- `LocalTime.of(1, 0)` - 1:00 AM (cross-day test)
- `LocalTime.of(23, 30)` - 11:30 PM (late Isha)
- `LocalTime.of(0, 0)` - Midnight (boundary test)
- `LocalTime.of(4, 30)` - 4:30 AM (pre-Fajr)

**Key Log Patterns:**
- `🕐 SIMULATING TIME:` - Simulation active
- `🚨 INCORRECT DETECTION:` - Prayer override triggered
- `✅ OVERRIDE:` - Cross-day fix applied  
- `🚨 NEGATIVE ELAPSED TIME:` - Safety protection activated

**Essential Commands:**
```bash
# Build & Install
./gradlew assembleDemoDebug && adb -s 4B221FDAP002T6 install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk

# Launch & Monitor
adb shell monkey -p com.starception.submission.demo.debug -c android.intent.category.LAUNCHER 1 && adb logcat -s "PrayerNotificationService"

# Screenshot
adb shell screencap -p > /tmp/prayer_test.png
```

This guide provides everything needed to thoroughly test the prayer notification system across all scenarios and edge cases.