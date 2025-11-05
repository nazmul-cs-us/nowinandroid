#!/bin/bash

echo "📍 Location Debug Log Capture"
echo "=============================="
echo ""
echo "This script will capture logs related to location fetching."
echo "Please pull to refresh on your device now, then press Ctrl+C to stop."
echo ""
echo "Starting log capture in 3 seconds..."
sleep 3

adb logcat -c
echo "Logcat cleared. Now pull to refresh on your device..."
echo ""

adb logcat | grep -E "(PullToRefresh|LocationDisplay|PrayerCalculation|EnhancedLocationService|LOCATION|getLocationDetails|Current Location|city|City|CITY)" | tee location_debug.log

