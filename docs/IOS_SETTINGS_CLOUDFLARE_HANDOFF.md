# iOS Settings, Cloud Assets, and Content Handoff

Status: Active dirty worktree as of 2026-09-03. No commit has been created; do not discard the uncommitted changes.

## Goal

Bring iOS Settings and Now in Android content closer to Android parity while using the shared Kotlin Multiplatform Cloudflare R2 manifest, cache, integrity, and category-download pipeline.

## Implemented

### Settings and cloud assets

- [x] Rebuilt iOS Settings with persisted Appearance, Prayer Times, Notifications, Travel Dua, Voice Recognition, Text-to-Speech, Content & Storage, and About sections.
- [x] Added iOS category status, sizes, byte progress, cancellation, errors, Download, and Delete through shared `CloudAssetRepository` APIs.
- [x] Added the `:core:asset-cache` KMP module for manifest fallback, URL-safe paths, request serialization, bundled/cache/download resolution, size/SHA-256 validation, atomic promotion, category progress, and deletion.
- [x] Android `AssetDownloadManager` already delegates asset and category resolution to `CloudAssetRepository` while preserving its public Hilt and `StateFlow` APIs.
- [x] Remote manifest adoption now removes cached assets whose entries were removed or whose size/hash changed. Invalid remote manifests still preserve and fall back to cache/bundle.
- [x] Android downloads support persistent HTTP range resume with ETag/`If-Range`, validated `206`/`416` handling, full-response restart, aggregate progress, and partial/metadata cleanup.
- [x] iOS uses one reused delegate-backed `URLSession`, reports true byte-level progress, stores and consumes `NSURLSession` resume data, excludes `StarceptionAssets` from backup, and retains size/SHA verification before promotion.
- [x] Added `PrivacyInfo.xcprivacy` and corrected location copy to disclose Open-Meteo and Apple reverse-geocoding use.

### Voice and audio

- [x] Integrated Sherpa ONNX `1.13.7` for iOS KWS, streaming ASR, Kokoro TTS, and VITS VCTK TTS, with native microphone capture and generated audio playback.
- [x] Voice models now have explicit Download/Delete controls and neutral progress UI. Tests no longer implicitly download a missing model.
- [x] Kokoro exposes 11 speakers on Android and iOS; VCTK speaker selection remains available.
- [x] Apple Speech/TTS remains the fallback and supplies Arabic Travel Dua because the R2 Sherpa voices are English-only.
- [x] One `QuranAudioPlayer` is owned by the shared navigation host and reused by the dashboard and Quran detail instead of creating competing players.

### iOS Now in Android content parity

- [x] Added an iOS SQLite news repository backed by bundled `news.db` or R2 `databases/news.db` through the shared asset resolver.
- [x] For You now uses followed topics, onboarding, read state, bookmarks, topic chips, and database news cards.
- [x] Saved now reads canonical bookmarked news, supports removal/undo, and preserves saved Bukhari books.
- [x] Interests now uses database topics with follow/unfollow and persisted ordering.
- [x] Topic pages now use database news with retry and 100-item pagination; news detail has structured content, read state, and bookmarking.
- [x] Canonical followed-topic, bookmarked-news, viewed-news, onboarding, and topic-order state is persisted. Legacy Surah/topic bookmarks migrate to generated news IDs, with Surah bookmarks kept synchronized.
- [x] Topic URL/image metadata and bundled iOS news/topic artwork lookup are wired.

### Earlier completed foundation

- [x] iOS database fallback already covers Quran Arabic, English translation, Sahih Bukhari, topics, Quranic Duas, and Fortress lookup paths.
- [x] Prayer notification payloads and native triggers use the prayer schedule timezone.
- [x] Foreground best-effort driving detection and Arabic Travel Dua testing are present. The UI now states the foreground limitation explicitly.

## Verification

### Earlier passing checks

These passed before the latest dirty-worktree changes:

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

That earlier Debug app launched on an iPhone 16 Pro iOS 18.6 simulator and rendered the voice settings.

### Checks still to run

No passing result is recorded for the latest asset-resume, privacy, voice-storage, shared-player, or news-parity changes. No tests were run while updating this handoff. Next verification should include:

```bash
./gradlew :core:asset-cache:allTests :shared:iosSimulatorArm64Test
./gradlew :app:testDemoDebugUnitTest :app:compileDemoDebugKotlin
cd iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator -arch arm64 \
  CODE_SIGNING_ALLOWED=NO build
```

The added but not yet verified tests cover stale-manifest cleanup, canonical content state/migration, and Android HTTP range resume. A Release build was started but interrupted, so it has no result and must be rerun.

## Remaining Blockers

1. Validate voice, TTS, audio routes, and lifecycle behavior on a physical iPhone. No physical iPhone is currently connected. Cover real model downloads, KWS yes/no, streaming transcription, all 11 Kokoro speakers, representative VCTK speakers, cancel/resume, microphone permissions, Bluetooth route changes, Travel Dua driving detection, interruption handling, and foreground/background behavior.
2. Publish Apple-compatible Quran audio (`AAC`, `M4A`, or `MP3`) to R2 and add it to `manifest.json`. iOS still uses the direct QuranicAudio MP3 endpoint because the current R2 Quran audio is OGG and is not a safe `AVPlayer` replacement.
3. Complete signed physical-device and Release verification, including offline bundled/cache fallback, corrupt-cache redownload, low-storage/interrupted downloads, iPad/landscape, Dynamic Type, and VoiceOver.

## Asset Coverage Gaps

- `fortress_of_the_muslim_v2.db` is bundled but has no matching R2 manifest object; publish it before enabling cloud-first replacement.
- Shared/topic artwork remains bundled because those images are not represented in the Cloudflare manifest.
- WorldWind JavaScript, imagery, WMS, and elevation still use third-party endpoints.
- Weather still sends coordinates directly to Open-Meteo.

## Follow-ups

- Add an iOS-specific SHA-256 test for the streaming Kotlin implementation.
- Decide whether Salah Training stays hidden or receives an iOS Core Motion/TFLite flow. Keep Android-only developer database refresh controls off iOS unless an iOS-safe implementation is added.

## Key Files

- `core/asset-cache/`
- `app/src/main/kotlin/com/starception/submission/download/AndroidAssetPlatform.kt`
- `app/src/main/kotlin/com/starception/submission/download/AssetDownloadManager.kt`
- `shared/src/iosMain/kotlin/com/starception/submission/core/assets/AssetPlatform.ios.kt`
- `shared/src/iosMain/kotlin/com/starception/submission/shared/ui/IosComposeRoot.kt`
- `shared/src/commonMain/kotlin/com/starception/submission/shared/ui/AudioSettingsSections.kt`
- `shared/src/commonMain/kotlin/com/starception/submission/shared/content/SharedContentStore.kt`
- `shared/src/iosMain/kotlin/com/starception/submission/shared/content/SharedNewsRepository.ios.kt`
- `shared/src/commonMain/kotlin/com/starception/submission/shared/ui/SharedContentScreens.kt`
- `shared/src/commonMain/kotlin/com/starception/submission/shared/ui/SharedNavHost.kt`
- `iosApp/Sources/PrivacyInfo.xcprivacy`
- `iosApp/project.yml` (source of truth; generated `iosApp.xcodeproj` is ignored)

## Constraints

- Shared KMP code owns asset manifest, cache, integrity, and category policy; platform code owns filesystem, HTTP transport, microphone, inference, and playback.
- Android uses the Sherpa `.aar`; iOS uses Sherpa's XCFramework through Swift Package Manager.
- Keep Apple speech support until Arabic Travel Dua and fallback requirements are replaced deliberately.
