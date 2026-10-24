# iOS and Apple Silicon

## Generate the project after cloning

Install Xcode, JDK 21, and XcodeGen, then run from the repository root:

```sh
brew install xcodegen git-lfs
git lfs install
git lfs pull
cd iosApp
xcodegen generate && open iosApp.xcodeproj
```

The database copies under `app/src/main/assets/databases/` are excluded from
Git. Their resource entries are optional so a fresh clone can generate the
project. If genuine local copies exist, XcodeGen includes them; regenerate the
project after adding them. Do not create empty database placeholders.

Quran and Quranic Duas have manifest-backed runtime downloads; first use
requires internet when no bundled/cache copy exists. Fortress v2 currently has
no matching published manifest entry. Restore
`app/src/main/assets/databases/fortress_of_the_muslim_v2.db` from your asset
backup, or rebuild it from the repository root with:

```sh
mkdir -p app/src/main/assets/databases
python3 scripts/build_fortress_v2.py
cd iosApp
xcodegen generate
```

The Fortress builder requires internet and prints any failed chapter downloads;
check its output before using the resulting database. Project generation alone
does not verify content availability or complete an Xcode build.

## Apple Silicon Macs

Deenly uses the existing iOS app on Apple Silicon Macs through Apple's
"Designed for iPad" support. The app target explicitly enables
`SUPPORTS_MAC_DESIGNED_FOR_IPHONE_IPAD` and includes the iPad device family.
It uses the iOS arm64 frameworks, including Shared and llama; no macOS or
Mac Catalyst framework slices are needed for this route. Intel Macs are not
supported.

On an Apple Silicon Mac with Xcode, the project's JDK, and XcodeGen installed:

```sh
cd iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp -showdestinations
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=macOS,variant=Designed for [iPad,iPhone]' \
  DEVELOPMENT_TEAM=YOUR_TEAM_ID build
```

Alternatively, select **My Mac (Designed for iPad)** in Xcode and run the app
with your development signing team. Test window resizing, mouse and keyboard
navigation, Quran playback, location permission and manual city selection,
notifications, and on-device voice/AI features before releasing. Macs do not
provide the iPhone motion sensors or compass; posture recording and compass
heading need to handle unavailable hardware. Live Activities are subject to
the host's availability checks.

For distribution, upload the signed iOS archive and enable Apple Silicon Mac
availability under **Pricing and Availability → iPhone and iPad Apps on Apple
Silicon Macs** in App Store Connect. This account setting must be checked
separately from the Xcode build setting. Validate the release on a real Mac
before claiming Mac compatibility in the store.

## Live Activity design and verification

The Live Activity follows Apple's guidance for concise, related content across
Lock Screen, expanded, compact, and minimal presentations:
https://developer.apple.com/design/human-interface-guidelines/live-activities

It shows one prayer, its adhan time, and a countdown labelled **Window ends in**.
The countdown targets the end of the configured prayer notification window
(`activeMinutes`), not the adhan itself or a religious ruling about a prayer's
latest permissible time. The progress indicator represents that same configured
window. Compact and minimal presentations use the same symbol, teal tint, and
prayer-window information. All presentations deep-link to the prayer screen.
There is no full app icon, precise location, full-day schedule, or percentage-based
claim about the best time to pray. Timer text uses a bounded system timer and a
reserved width instead of shrinking to tiny text or counting upward after expiry.

The app reconciles its current prayer on foreground entry even if the schedule
payload hasn't changed. It ends expired activities when it can execute and uses
`staleDate` to show an explicit outdated/ended state while suspended. A stale date
**does not end** a Live Activity, and a sleeping local task is not a reliable
background scheduler. Guaranteed ending while suspended or terminated requires
ActivityKit APNs end events; this local-only implementation doesn't configure a
per-activity push token when creating an activity.

On a Mac, generate the project and open `PrayerLiveActivityWidget.swift` in Xcode.
Its preview group covers upcoming, active-window, expired, stale, large-text, and
right-to-left Lock Screen layouts. Before release, build and test on iPhone:

- Verify compact, minimal (with another Live Activity), expanded, and Lock Screen
  presentations, including long prayer names and all supported languages.
- Check light/dark appearances, Dynamic Type, VoiceOver, StandBy, and Always-On.
- Background the app across adhan and window expiry: labels must stay factual,
  timers must stop at zero, and stale content must not look like a fresh event.
- Resume with an unchanged schedule, change location, disable Live Activities,
  and confirm that the old activity is updated or ended appropriately.
- Tap every presentation and confirm it opens the prayer screen.

The source changes have not been built with Xcode in the Linux workspace.

## Push configuration

No APNs provider key, certificate, or backend credential belongs in this app.
The app only registers with APNs, stores token snapshots in `UserDefaults` under
`ios_prayer_push_token_snapshot`, and optionally sends the snapshot to an HTTPS
endpoint.

## Local and simulator builds

Generate and build without signing:

```sh
cd iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -arch arm64 CODE_SIGNING_ALLOWED=NO build
```

## Device configuration

Pass signing and backend values externally, either on the command line or in an
uncommitted `Config/Local.xcconfig`:

```xcconfig
DEVELOPMENT_TEAM = YOUR_TEAM_ID
APNS_ENVIRONMENT = development
SLASH = /
PRAYER_PUSH_BACKEND_URL = https:$(SLASH)$(SLASH)push.example.com/v1/apple-tokens
```

Use `APNS_ENVIRONMENT = production` with the production provisioning setup.
The Push Notifications capability, Live Activities capability, matching app and
extension identifiers, and provisioning profiles must exist in the Apple
Developer account. The backend separately requires an APNs provider key or
certificate and must route ordinary APNs, Live Activity update/end tokens, and
iOS 17.2 push-to-start tokens using Apple's required topics and payloads.

When `PRAYER_PUSH_BACKEND_URL` is a valid HTTPS URL, the app posts JSON containing
`bundleIdentifier`, `apnsDeviceToken`, `liveActivityTokens`, `pushToStartToken`,
and `updatedAt`. The endpoint should treat token replacement as an upsert. The
request intentionally has no embedded authorization header; add server-side
abuse controls or an app-attestation exchange if the endpoint is public.
