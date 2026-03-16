# Driving Mode & Voice Features Guide

Technical guide for the driving mode audio system and voice integration features.

## Overview

The app includes a driving mode that plays Islamic audio content (Travel Dua, Hadith, Quran recitation) through a foreground service with full media controls. Voice features include Sherpa ONNX TTS for text-to-speech and Whisper for speech recognition.

---

## Driving Audio Service

### Location
`app/src/main/kotlin/com/starception/submission/services/DrivingAudioService.kt`

### Architecture
- **Foreground Service** with persistent notification
- **MediaSession** integration for system media controls
- **Audio chain**: Travel Dua -> Hadith (audio or TTS) -> Quran -> Voice Completion Prompt
- **Wake Lock** to prevent CPU sleep during playback
- **Hilt dependency injection** for TTS and voice services

### Features
- Lock screen media controls
- Notification playback controls (play/pause/skip)
- Bluetooth audio support
- Audio focus management
- Automatic progression through audio chain
- Configurable content selection

### Debug Receiver
`app/src/main/kotlin/com/starception/submission/util/DebugDrivingReceiver.kt`
- Broadcast receiver for testing driving mode without actual driving detection
- Allows triggering driving mode from ADB commands

---

## Voice System

### Location
`app/src/main/kotlin/com/starception/submission/voice/`

### Components

| File | Purpose |
|------|---------|
| `VoiceModule.kt` | Hilt DI module for voice services |
| `SherpaVoiceService.kt` | Sherpa ONNX text-to-speech engine |
| `WhisperVoiceService.kt` | Whisper speech recognition service |
| `VoiceCompletionManager.kt` | Manages voice interaction flows |

### Sherpa ONNX TTS
- On-device text-to-speech using Sherpa ONNX models
- Used for reading Hadith text when no audio recording is available
- Integrated into the driving audio chain

### Whisper Voice Recognition
- On-device speech recognition using Whisper TFLite model
- Model: `app/src/main/assets/whisper/whisper-tiny.en.tflite`
- English language recognition
- Used for voice commands and interaction

### Voice Recording
`app/src/main/kotlin/com/starception/submission/feature/course/VoiceRecordingManager.kt`
- Voice recording management for course features

### Voice Settings
`app/src/main/kotlin/com/starception/submission/settings/components/VoiceSettingsSection.kt`
- UI settings for voice features (enable/disable, volume, voice selection)

---

## Activity Detection Integration

### Activity Tracker
- Detects user activities (walking, driving, stationary)
- Triggers driving mode when vehicle motion detected
- Uses accelerometer + gyroscope for orientation-based detection

### Related Documentation
- `docs/ACTIVITY_DETECTION_IMPROVEMENTS.md` - Gyroscope-based detection improvements
- `docs/ACTIVITY_DETECTION_TECHNICAL_GUIDE.md` - Full technical guide
- `docs/PICKUP_FALSE_DETECTION_FIX.md` - Fix for false walking detection on phone pickup
- `docs/ACTIVITY_NOTIFICATION_PERSISTENCE.md` - Notification mode persistence

---

## Debug Logging

```bash
# View driving audio service logs
adb logcat -s "DrivingAudioService" "VoiceCompletionManager" -v time

# View TTS logs
adb logcat -s "SherpaOnnxTts" -v time

# View voice recognition logs
adb logcat -s "WhisperVoice" -v time

# View activity detection logs
adb logcat -s "ActivityTracker" "DebugDrivingReceiver" -v time

# View all driving/voice logs
adb logcat -s "DebugDrivingReceiver" "DrivingAudioService" "VoiceCompletionManager" "ActivityTracker" "SherpaOnnxTts" "SherpaOnnxKws" "WhisperVoice" -v time
```
