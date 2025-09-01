#!/bin/bash

# Install and run script for Starception Submission app
# This script builds, installs, and automatically opens the app on device

echo "🔨 Building and installing app..."
./gradlew installDemoDebug

if [ $? -eq 0 ]; then
    echo "✅ App installed successfully"
    echo "🚀 Launching app on device..."
    adb shell am start -n com.starception.submission.demo.debug/com.starception.submission.MainActivity
    echo "📱 App should now be opening on your device"
else
    echo "❌ Build or installation failed"
    exit 1
fi