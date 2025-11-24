#!/bin/bash

# Install and run script for Starception Submission app
# This script builds, installs, and automatically opens the app on device

echo "🔨 Building and installing app..."
./gradlew installDemoDebug

if [ $? -eq 0 ]; then
    echo "✅ App installed successfully"
    echo "🚀 Launching app on device..."
    # Using monkey command for more reliable app launch
    adb shell monkey -p com.starception.submission.demo.debug -c android.intent.category.LAUNCHER 1
    echo "📱 App is now open on your device"
else
    echo "❌ Build or installation failed"
    exit 1
fi