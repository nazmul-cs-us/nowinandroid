# iOS / iPad / Mac Port Plan

Status as of 2026-08-26. Branch: `ios-port/phase-1-shared-scaffold`.

## Goal

Ship the app on iPhone, iPad and Apple Silicon Mac from one shared Kotlin
codebase, with CarPlay support where Apple grants the required entitlement.

The target is **product parity where the platform permits it**, not a literal
copy of Android APIs. Android DND, foreground services, exact alarms, promoted
live-update notifications and some passive sensor behaviours have no direct iOS
equivalent. Every such feature needs an explicit iOS experience or a documented
fallback before it can be called complete.

Mac is **not** a separate Kotlin/Native target. The iOS build is offered to
Apple Silicon Macs as "Designed for iPad" — one App Store listing, not a
Compose Desktop application. It runs the iOS device build natively; it does not
use `iosSimulatorArm64`. Intel Macs are out of scope. Mac still needs explicit
QA and graceful degradation because GPS quality, magnetometer, motion activity
and telephony capabilities are not guaranteed to match an iPhone.

## Approach

Share the Kotlin, including the UI, via Compose Multiplatform rather than
rewriting in SwiftUI. There are 866 `@Composable` functions; rewriting them is
not realistic.

```
core:model/                 converted to KMP in place
prayer-engine/              shared calculations and policies
feature:*/                  converted feature-by-feature where useful
shared/                     thin umbrella + shared app entry point
  commonMain                shared UI and platform-neutral presentation
  androidMain / iosMain     platform adapters and composition roots
      |                            |
 app/ (Android/Play Store)   iosApp/ (Xcode/App Store)
                                    |-- iPhone
                                    |-- iPad
                                    '-- Mac (Designed for iPad)
```

**Convert modules in place; do not relocate them.** Making an existing `core:*`
module multiplatform while keeping its Gradle path and package costs roughly zero
downstream edits. Moving sources into `shared/` would have rewritten 94 imports
and 11 build files for `:core:model` alone, for no benefit. `shared/` is an
umbrella framework and composition boundary; it must not become a second
monolith. It depends on selected KMP modules and re-exports only the API the iOS
host needs.

## The codebase, measured

| | |
|---|---|
| Kotlin | 192,205 lines across 634 files |
| `app` module | **156,029 lines — 81% of everything** |
| `@Composable` | 866 |

The headline risk is not iOS. It is that `app` is a monolith: the 25-module
structure described in `CLAUDE.md` covers the other 19%. Inside `app`:

| Package | Lines |
|---|---|
| `feature` | 66,141 |
| `prayer` | 15,264 |
| `widget` | 10,483 |
| `core` | 8,397 |
| `settings` | 7,322 |
| `ui` | 6,537 |
| `util` | 6,256 |
| `voice` | 4,334 |
| `services` | 4,330 |
| `islamic` | 3,154 |

Android `app` can consume declarations from a KMP module through that module's
Android target. Code that currently lives inside `app`, however, cannot itself
become `commonMain` until it is extracted into a KMP module. Do that one vertical
feature slice at a time; moving the entire monolith is not a prerequisite for the
first iOS build.

### Platform-bound surface

| Dependency | Files | Disposition |
|---|---|---|
| Hilt | see below | Keep on Android; shared classes use constructor injection and platform-neutral interfaces |
| Room | 71 | KMP supported, but pre-packaged DB APIs used here are Android-only; requires an import/copy design |
| Glance widgets | 36 | No iOS equivalent. WidgetKit rewrite in Swift |
| Sherpa ONNX (TTS) | 14 | Ships as an Android `.aar`. Needs an iOS replacement |
| WorkManager / exact alarms | 11+ | Local notifications for prayer times; `BGTaskScheduler` only for opportunistic refresh |
| Whisper TFLite | 5 | TFLite has iOS support; needs rebinding |
| `androidx.car` | 4 | Rewrite as CarPlay |
| WorldWind | 2 | iOS starts at WorldWind 1.14; current app is pinned to 1.9 and needs a toolchain/API migration |
| SceneView / Filament | 1 | Custom humanoid mesh; needs a native path |
| media3 / ExoPlayer | 1 | Map to `AVFoundation` |
| TensorFlow Lite | 1 | Salah posture model |

### Dependency injection boundary

| Annotation | Files | Sites |
|---|---|---|
| `@Inject` | 92 | 131 |
| `@Module` | 75 | 108 |
| `@InstallIn` | 80 | 101 |
| `@HiltViewModel` | 41 | 63 |
| `@Provides` | 42 | 72 |
| `@Binds` | 38 | 65 |
| `hiltViewModel()` | 18 | 40 |
| `@AndroidEntryPoint` | 7 | 7 |

Hilt is JVM-only, but that does **not** require replacing every Hilt site before
iOS work can continue. The rule is narrower:

- Classes moved to `commonMain` have ordinary constructors and depend on
  platform-neutral interfaces.
- Android keeps Hilt and provides those shared types/adapters from its existing
  graph.
- iOS gets a small composition root using manual factories or Koin. Koin is an
  option for new shared wiring, not a mandatory whole-app migration.
- `@HiltViewModel` and `hiltViewModel()` are removed only from presentation code
  that is actually moved to shared UI.

This avoids a risky 131-site Android refactor that delivers no iOS screen by
itself.

## Platform parity contract

Before porting a feature, record its supported experience in this table. "Native
rewrite" means the domain policy may be shared while lifecycle, permissions and
UI integration remain platform code.

| Feature | iPhone | iPad | Mac (Designed for iPad) | Delivery |
|---|---|---|---|---|
| Prayer calculation and schedule | Full | Full | Full | Shared Kotlin |
| Prayer and pre-prayer alerts | Full with user permission | Full | Supported when notifications are enabled | iOS local notifications |
| Prayer Live Update | ActivityKit limits apply | ActivityKit limits apply | System presentation varies | SwiftUI WidgetKit/ActivityKit extension |
| Quran, Hadith and Dua reading | Full | Full/adaptive | Full/adaptive | Shared UI plus database import |
| Quran/Hadith audio | Full | Full | Full | Shared policy + AVFoundation/Now Playing |
| Qibla compass | Full with sensors | Device-dependent | Fallback bearing/map; no sensor assumption | Core Location/Core Motion adapter |
| Salah posture detection | Device and permission dependent | Device and permission dependent | Unsupported or explicit fallback | Core Motion + iOS TFLite/native rendering |
| Automatic Travel Dua | Best effort under iOS background rules | Best effort | Disabled unless reliable evidence exists | Core Motion/Core Location + native lifecycle |
| Android prayer DND | No direct parity assumed | No direct parity assumed | No direct parity assumed | Explain Focus setup; never claim automatic DND |
| Widgets | Native rewrite | Native rewrite | Native system presentation | SwiftUI WidgetKit |
| Car integration | CarPlay after entitlement approval | Not a parity target | Not a parity target | Native CarPlay templates |

## Phase 1 — Foundation (COMPLETE)

Seven commits, each verified by a full Android build and an on-device install.

| Commit | |
|---|---|
| `324abaaf7` | `shared/` KMP scaffold; `SharedLog` expect/actual |
| `061ca646b` | `:core:model` converted to multiplatform in place |
| `ec5586cdb` | `nowinandroid.kmp.library` convention plugin |
| `2ddccda7b` | App unit test suite restored; prayer behaviour pinned |
| `3a407afdb` | `:core:model` exported from the iOS framework |
| `9a77e487b` | Three prayer-status bugs fixed |
| `55cf980ad` | Gradle daemon pinned to JDK 21 |

**Proven:** `:shared:linkDebugFrameworkIosSimulatorArm64` links against the iOS
26.2 SDK and exports all 11 model types plus `kotlinx.datetime.Instant` to
Objective-C/Swift. Android is unaffected throughout.

### What phase 1 corrected in the original plan

- Of the three `core` modules booked as "portable as-is", only `core:model` was.
  `core:common` has Hilt modules in 2 of its 4 files; `core:domain` uses
  `javax.inject` in all 3 and depends on Room-backed `core:data`. The original
  survey grepped only for `android`/`androidx` imports, which misses JVM-only DI.
  **When auditing a module for `commonMain`, grep for `javax|dagger|jakarta|java\.`
  as well.**
- The prayer engine is not a `Log` swap. It uses `java.time` — absent in
  Kotlin/Native — at 29 real code lines, plus `String.format`, `SimpleDateFormat`,
  `Date` and `Locale`.
- The app's unit tests had not run for some time (three stacked causes; see
  `2ddccda7b`). 58 now pass.

## Phase 2 — Prayer engine portable

Independent of Hilt for the migration itself; the physical move is not.

1. `java.time` → `kotlinx-datetime` across 29 sites: `LocalTime.now/of/MIN/MAX/
   MIDNIGHT`, `isAfter`/`isBefore` (→ `Comparable`), `Duration.between`,
   `plusHours`, `String.format`, and the four `SimpleDateFormat` log helpers.
2. `android.util.Log` → `SharedLog` across 112 call sites in
   `AstronomicalCalculator`.
3. Extract `prayer/model` and `prayer/calculator` into a KMP module in place (or
   a narrowly named new prayer-engine module). Remove `@Singleton`/`@Inject`
   from the shared classes and let Android Hilt construct them externally.

**Verification is non-negotiable here.** These 2,378 lines produce every time on
the dashboard. `DayPrayerTimesTest` must stay green, `everyMinuteOfTheDayHasAtMostOneCurrentPrayer`
in particular, and computed times must be compared on-device before and after. A
clean compile proves nothing. Note that a daytime screenshot cannot catch a
midnight bug — that is exactly how the late-Isha bug survived.

## Phase 3 — First real `iosApp/` vertical slice

Create the Xcode project immediately after the prayer engine is portable. Do not
wait for the app monolith to be extracted.

1. Choose and document the minimum deployment target. Compose Multiplatform
   currently supports iOS 14+, but features such as ActivityKit may justify a
   higher product minimum.
2. Create `iosApp/` with a small Swift/SwiftUI lifecycle shell hosting the
   Compose root.
3. Use direct local integration via
   `:shared:embedAndSignAppleFrameworkForXcode`; do not manually copy a stale
   framework into Xcode.
4. Render today's real prayer schedule using the shared calculator on an Apple
   Silicon simulator.
5. Verify launch, rotation, dark mode, dynamic type, VoiceOver basics and the
   iPad layout before adding another feature.

Settle the framework `baseName` here, when real Swift call sites exist. It
currently makes `SharedLog` surface as `SharedSharedLog`.

## Phase 4 — Extract `app` into modules

Work in user-visible vertical slices rather than moving whole package trees:

1. Prayer dashboard and settings.
2. Quran reader and audio.
3. Hadith/Dua reader and audio.
4. Qibla compass and globe fallback.
5. Salah and travel features.

For each slice, separate domain/presentation policy from platform adapters,
convert only the required module(s) to KMP, expose the slice through the umbrella
framework, and verify both Android and iOS before continuing. Keep Hilt in
Android code that remains Android-only.

## Phase 5 — Database and bundled-content migration

Room 2.7+ supports KMP, but this repository relies on Android-only APIs including
`createFromAsset`, `createFromFile`, Room callbacks using
`SupportSQLiteDatabase`, and asset-backed Quran/Hadith/Dua databases. A clean KMP
compile does not solve content delivery.

1. Inventory each database, schema, asset size, write behaviour and migration.
2. Decide per database between Room KMP and a read-only SQLite abstraction.
3. On first launch, copy bundled databases into the iOS Application Support
   directory with an atomic/versioned import before opening them.
4. Replace `SupportSQLiteDatabase` migrations/callbacks with KMP SQLite driver
   APIs where Room KMP is used.
5. Test first install, upgrade, interrupted copy, low-storage failure, language
   database switching and data integrity on a real iPhone/iPad.

## Phase 6 — Native Apple services

These integrations keep shared domain state where useful but have native Apple
lifecycle and permission handling:

- **Prayer alerts:** pre-calculate and register future
  `UNCalendarNotificationTrigger` requests. Rebuild the schedule on settings,
  location, timezone and significant clock changes. `BGTaskScheduler` is only a
  best-effort refresh path because iOS chooses its run time.
- **Live Update:** use a SwiftUI WidgetKit/ActivityKit extension. Design around
  ActivityKit duration, update and availability limits; it is not the Android
  ongoing notification copied verbatim.
- **Audio:** AVFoundation, AVAudioSession, MPNowPlayingInfoCenter and remote
  commands. Notification sounds are short alerts, not the long-form playback
  engine.
- **Location and motion:** Core Location/Core Motion with explicit permission,
  background-mode and power policies. Never assume a callback cadence equal to
  the Android foreground service.
- **Widgets:** SwiftUI WidgetKit.
- **TTS/STT/ML:** choose and benchmark the iOS engines before promising offline
  model parity.
- **3D:** upgrade WorldWind/tooling first and use its iOS Compose API, or ship a
  simpler native Qibla visualization initially. The existing Android
  `WorldWindow(context)`/`GLSurfaceView` code is not portable.
- **Salah humanoid:** native rendering path for SceneView/Filament content.
- **CarPlay:** native templates and audio integration after entitlement approval.

**Request CarPlay entitlements from Apple early** — they are granted by app
category and the approval runs independently of code readiness. Approval is a
product dependency, not an implementation detail.

## Phase 7 — Parity, quality and release

1. Maintain a golden prayer-time corpus spanning locations, calculation methods,
   DST transitions, midnight, high latitudes and every minute of representative
   days. Run it on JVM and Kotlin/Native.
2. Add common tests plus Android and iOS adapter tests. A framework link is a
   foundation check, not an end-to-end test.
3. Test physical iPhone and iPad devices for sensors, background transitions,
   notification delivery, audio interruptions and database installation.
4. Test "Designed for iPad" separately on Apple Silicon Mac and disable or
   explain hardware-dependent features.
5. Add privacy manifests, permission-purpose strings, App Store privacy answers,
   signing, TestFlight and release CI.
6. Record every accepted Android/iOS behaviour difference in the parity table.

## Environment

| | |
|---|---|
| Xcode | 26.3 at `/Applications/Xcode-26.3.0.app`, iOS 26.2 SDK, active |
| Xcode ceiling | **26.3.** 26.4+ requires macOS 26.2; this Mac runs 15.7.7. Never `xcodes install --latest` |
| Gradle daemon | JDK 21, pinned in `gradle/gradle-daemon-jvm.properties`. On JDK 24 Gradle cannot create test tasks |
| Homebrew | Owned by another account on this Mac; `brew install` fails. Never `chown` it. Use standalone binaries |
| Test device | Galaxy S26 Ultra, `R5GYC5FPXWT` (`SM-S948U`) |

## Working rules

- **Convert in place.** Keep the Gradle path and package; only the source set
  moves. Downstream code should not need to change.
- **Keep `shared/` thin.** It is the umbrella framework and composition boundary,
  not the destination for all 156k lines from `app`.
- **`export()` is required in the framework block.** `api(projects.x)` makes
  types available to Kotlin, but the Obj-C header only exports declarations from
  the module itself. Without it the header was 216 lines of runtime base classes;
  with it, 900 lines with every model type.
- **Multiplatform modules cannot use the `nowinandroid.*` convention plugins.**
  `configureKotlin()` dispatches on extension type and hits `TODO()` for
  `KotlinMultiplatformExtension`. Use `nowinandroid.kmp.library`.
- **Declare the KMP plugin `apply false` in the root build file.** `build-logic`
  is an included build, so a versioned request from a subproject cannot resolve.
- **Verify on device, not just in CI.** Every phase-1 commit was installed on the
  S26 and checked against a recorded prayer-times baseline.
- **Verify both platforms after Phase 3.** Every shared feature change must pass
  Android checks and run through the real Xcode host before the next slice.
- **Do not model Apple background work as Android work with renamed classes.**
  Exact user-visible events use local notifications; system-scheduled background
  work is only a refresh/fallback mechanism.
- **Keep Apple targets distinct.** `iosArm64` is the device build,
  `iosSimulatorArm64` is the Apple Silicon simulator, and "Designed for iPad" is
  the iOS device app running natively on an Apple Silicon Mac.
- **Version-gate WorldWind and Compose.** The current WorldWind 1.9 pin cannot
  provide the iOS globe. Upgrade Kotlin/AGP/Compose and WorldWind as one tested
  toolchain change rather than allowing transitive metadata changes mid-feature.
- **Additive changes only in the prayer flow.** The auto-detection path is
  fragile. Prefer defaulted parameters over changed signatures.

## Primary platform references

- [Compose Multiplatform compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [Kotlin direct Xcode integration](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html)
- [Room Kotlin Multiplatform setup and limitations](https://developer.android.com/kotlin/multiplatform/room)
- [Apple local notification scheduling](https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app)
- [Apple background strategy selection](https://developer.apple.com/documentation/backgroundtasks/choosing-background-strategies-for-your-app)
- [Apple ActivityKit](https://developer.apple.com/documentation/activitykit)
- [Apple Designed for iPad configuration](https://developer.apple.com/documentation/Xcode/configuring-a-multiplatform-app-target)
- [Apple CarPlay entitlement request](https://developer.apple.com/documentation/carplay/requesting-carplay-entitlements)
- [WorldWind Kotlin releases](https://github.com/WorldWindEarth/WorldWindKotlin/releases)
