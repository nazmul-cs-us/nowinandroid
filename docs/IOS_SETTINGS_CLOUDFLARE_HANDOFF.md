# iOS Settings and Cloudflare Asset Port Handoff

Status: Paused on 2026-09-01

The worktree contains uncommitted changes. Do not discard it before resuming.

## Goal

Finish the iOS Settings page with working features and move Android and iOS to a shared Kotlin Multiplatform asset pipeline for Cloudflare R2 databases, audio, and ML models.

## Completed

### iOS Settings page

- Rebuilt the iOS Settings page to follow the active Android Settings organization and card styling.
- Added the Android section grouping and order:
  - Prayer and personalization
  - Voice and Salah intelligence
  - App and support
- Added and wired these functional sections:
  - Appearance
  - Prayer Times
  - Notifications
  - Travel Dua
  - Voice Recognition
  - Text-to-Speech
  - About
- Settings remain single-expand accordions, matching Android behavior.
- Appearance, prayer, notification, Travel Dua, voice mode, TTS model, and TTS speaker choices persist through `NSUserDefaults`.
- Added iOS microphone and speech permission descriptions.
- Added foreground, best-effort driving-speed detection for automatic Travel Dua playback.
- Added a working Travel Dua audio test using the installed Arabic iOS voice.
- Added speaker selection for Kokoro and VCTK.

Primary files:

- `shared/src/commonMain/kotlin/com/starception/submission/shared/ui/PrayerSettingsScreen.kt`
- `shared/src/commonMain/kotlin/com/starception/submission/shared/ui/AudioSettingsSections.kt`
- `shared/src/commonMain/kotlin/com/starception/submission/shared/settings/UserAudioSettings.kt`
- `shared/src/iosMain/kotlin/com/starception/submission/shared/ui/IosComposeRoot.kt`
- `shared/src/iosMain/kotlin/com/starception/submission/shared/travel/IosTravelDuaMonitor.kt`

### Shared Cloudflare framework

- Added the new `:core:asset-cache` KMP module.
- Moved the Cloudflare manifest model and parser into shared code.
- Added common asset behavior for:
  - Remote manifest loading
  - Cached and bundled manifest fallback
  - URL-safe R2 object paths
  - Per-asset request serialization
  - Cache lookup
  - Download fallback
  - Expected-size validation
  - SHA-256 validation
  - Atomic cache promotion
  - Category download aggregation
  - Download progress models
  - Category and asset deletion
- Added common tests for manifest parsing, URL encoding, fallback order, corrupt cache handling, integrity validation, category progress, and deletion.
- Added Android and iOS filesystem/network adapters.
- Android now uses the shared manifest parser and shared manifest-loading policy while retaining the existing public `AssetDownloadManager` API.

Primary files:

- `core/asset-cache/`
- `app/src/main/kotlin/com/starception/submission/download/AndroidAssetPlatform.kt`
- `app/src/main/kotlin/com/starception/submission/download/AssetManifest.kt`
- `app/src/main/kotlin/com/starception/submission/download/AssetDownloadManager.kt`
- `shared/src/iosMain/kotlin/com/starception/submission/shared/assets/IosAssetPlatform.kt`
- `shared/src/iosMain/kotlin/com/starception/submission/core/assets/AssetPlatform.ios.kt`

### iOS databases

- Routed these iOS database lookups through the shared Cloudflare manifest/cache with bundled fallback:
  - Quran Arabic
  - Quran English translation
  - Sahih Bukhari
  - Topics
  - Quranic Duas
  - Fortress of the Muslim lookup path
- English translation download failure now falls back to Arabic Quran text instead of failing the entire Surah page.
- Added the shared `manifest.json` to the iOS app bundle.
- Cached assets are stored under Application Support in `StarceptionAssets/` while preserving R2 paths.

Primary files:

- `shared/src/iosMain/kotlin/com/starception/submission/shared/database/IosDatabaseAsset.kt`
- `shared/src/iosMain/kotlin/com/starception/submission/shared/quran/QuranVerseRepository.ios.kt`
- `shared/src/iosMain/kotlin/com/starception/submission/shared/hadith/SharedHadithRepository.ios.kt`
- `shared/src/iosMain/kotlin/com/starception/submission/shared/content/SharedTopicRepository.ios.kt`

### iOS offline voice recognition and TTS

- Added the official Sherpa ONNX Swift package at version `1.13.7`.
- Added ONNX Runtime through Sherpa's package dependency.
- Added a Kotlin-to-Swift runtime bridge.
- Added native Swift microphone capture and 16 kHz mono conversion.
- Added Sherpa keyword spotting using Cloudflare category `model_kws`.
- Added Sherpa streaming transcription using Cloudflare category `model_asr`.
- Added Kokoro TTS using Cloudflare category `model_tts_kokoro`.
- Added VITS VCTK TTS using Cloudflare category `model_tts_vits`.
- Added generated Float32 audio playback through `AVAudioEngine` and `AVAudioPlayerNode`.
- Voice and TTS models download on first test, are size/SHA verified, and are reused from the local cache.
- Apple Speech/TTS remains a fallback when no Sherpa service is injected.
- Arabic Travel Dua intentionally uses the Apple Arabic voice because the current R2 Kokoro/VCTK models are English-only.

Primary files:

- `iosApp/Sources/SherpaSpeechService.swift`
- `shared/src/iosMain/kotlin/com/starception/submission/shared/voice/IosSherpaService.kt`
- `shared/src/iosMain/kotlin/com/starception/submission/shared/voice/IosSherpaAssetResolver.kt`
- `iosApp/Sources/ComposePrayerTimesView.swift`
- `iosApp/project.yml`

### Notifications

- Added the prayer location's timezone offset to the native iOS notification payload.
- Native notification dates and triggers now use the prayer schedule timezone instead of always using the device timezone.

Primary files:

- `shared/src/iosMain/kotlin/com/starception/submission/shared/notifications/IosPrayerSchedulePublisher.kt`
- `iosApp/Sources/PrayerNotificationCoordinator.swift`

## Verification Completed

The following completed successfully during this session:

```bash
./gradlew :core:asset-cache:allTests
./gradlew :shared:iosSimulatorArm64Test
./gradlew :app:compileDemoDebugKotlin
xcodegen generate
xcodebuild -resolvePackageDependencies -project iosApp.xcodeproj -scheme iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator -arch arm64 \
  CODE_SIGNING_ALLOWED=NO build
```

The generated iOS app was installed and launched on the iPhone 16 Pro iOS 18.6 simulator. The Settings page rendered with the new Voice Recognition and Text-to-Speech sections.

The last small Kotlin lifecycle/copy changes were compiled and covered by `:shared:iosSimulatorArm64Test`. Run the full Xcode build once more after resuming.

## Remaining Work

### Highest priority

1. Exercise actual model downloads and inference on a physical iPhone.
   - Test KWS with yes/no.
   - Test full Sherpa transcription.
   - Test Kokoro generation and all exposed speaker IDs.
   - Test VCTK generation and representative speaker IDs.
   - Test stopping while a model is still downloading.
   - Test microphone and Bluetooth route changes.

2. Finish Android delegation to `CloudAssetRepository`.
   - Android uses the shared manifest parser and manifest loader now.
   - Android's per-file/category download loops still contain the older implementation.
   - Replace those internals with the shared resolver while preserving existing `StateFlow` UI state and Hilt APIs.

3. Add iOS Content and Storage settings.
   - Show Cloudflare categories, sizes, progress, downloaded state, Download, and Delete.
   - Reuse the common category APIs rather than porting Android's ViewModel.
   - Show download errors and cancellation.

4. Finish Cloudflare Quran audio migration.
   - Current iOS Quran playback still uses the direct QuranicAudio MP3 endpoint.
   - Existing R2 Quran Arabic files are OGG, which AVPlayer does not reliably support.
   - Publish AAC, M4A, or MP3 variants to R2 and add them to `manifest.json`.
   - Then resolve local cached audio through `CloudAssetRepository` before playback.
   - Consolidate the two duplicate shared Quran audio player implementations.

### Asset coverage gaps

- `fortress_of_the_muslim_v2.db` is bundled but not present as a matching R2 manifest object. Publish the v2 database before enabling cloud-first replacement.
- Shared/topic artwork is still bundled because these image files are not represented in the current Cloudflare manifest.
- WorldWind JavaScript, imagery, WMS, and elevation still use third-party endpoints.
- Weather still calls Open-Meteo directly.
- Add compatible asset entries before routing these through Cloudflare.

### iOS cache follow-ups

- Restore the `NSURLIsExcludedFromBackupKey` setting with an API that compiles cleanly under Kotlin/Native.
- Add true byte-level URLSession download progress. The current iOS adapter reports completion per file, so a single large model may stay at the previous percentage until that file completes.
- Reuse/invalidate URLSession instances instead of creating one for every file.
- Add an iOS-specific SHA-256 test for the streaming pure-Kotlin implementation.
- Add resumable/range downloads for large audio and model assets.
- Add cache-version cleanup when a manifest replaces old hashes.

### Settings polish

- Show model download progress as neutral progress UI instead of using the narration error text area.
- Add explicit model Download/Delete cards before the user taps Test.
- Confirm the Kokoro model's actual speaker count. Android currently exposes 10; Sherpa documentation may report 11 for this model revision.
- Add iOS Content and Storage, as noted above.
- Decide whether Salah Training should be hidden or replaced with an iOS Core Motion/TFLite training flow.
- Developer database refresh remains Android-only and should not be shown on iOS without an iOS-safe implementation.

### Runtime and release verification

- Run a signed physical-device build.
- Test offline launch with cached and bundled database fallback.
- Corrupt a downloaded file and verify automatic deletion/redownload.
- Test low-storage and interrupted-download behavior.
- Test iPad, landscape, Dynamic Type, and VoiceOver.
- Run Release simulator and physical-device builds.
- Add an iOS privacy manifest.
- Correct the location privacy copy: coordinates are currently sent to Open-Meteo and may be used by Apple's reverse geocoder, so "never sent anywhere" is too strong.

## Known Constraints

- Model files are portable data, but inference libraries are platform-specific.
- Android uses its Sherpa `.aar`; iOS uses Sherpa's official XCFramework through Swift Package Manager.
- The shared KMP module owns manifest, cache, integrity, and category policy. Platform adapters own filesystem, HTTP transport, microphone, and audio playback.
- The current R2 OGG Quran files cannot safely replace iOS MP3 playback until an Apple-supported encoding is published.

## Resume Commands

Start with:

```bash
git status --short
./gradlew :core:asset-cache:allTests :shared:iosSimulatorArm64Test
./gradlew :app:compileDemoDebugKotlin
cd iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator -arch arm64 \
  CODE_SIGNING_ALLOWED=NO build
```

Then prioritize a physical iPhone voice/TTS test before expanding the migration further.

## Worktree Notes

- No commit was created.
- The generated `iosApp.xcodeproj` is ignored; `iosApp/project.yml` is the source of truth.
- Swift package resolution downloaded Sherpa ONNX `1.13.7` and ONNX Runtime `1.28.1` into Xcode DerivedData.
- Do not remove the Apple speech implementation yet; it is the fallback and provides Arabic Travel Dua narration.
