# iOS / iPad / Mac Port Plan

Status as of 2026-08-26. Branch: `ios-port/phase-1-shared-scaffold`.

## Goal

Ship the app on iPhone, iPad and Mac from one codebase, at full feature parity
with Android including CarPlay.

Mac is **not** a separate target. The iOS build is shipped to Apple Silicon Macs
via the "Designed for iPad" checkbox — one App Store listing, no extra code.
Consequences: Intel Macs are out of scope, the UI is iPad-shaped rather than
Mac-shaped, and there is no non-Xcode path to a Mac build. A Compose Desktop
(JVM) target was considered and rejected.

## Approach

Share the Kotlin, including the UI, via Compose Multiplatform rather than
rewriting in SwiftUI. There are 866 `@Composable` functions; rewriting them is
not realistic.

```
shared/                     Kotlin Multiplatform
  commonMain                models, prayer engine, Compose UI
  androidMain / iosMain     expect/actual for sensors, location, media
      |                            |
 androidApp/  (Play Store)   iosApp/  (App Store)
                                    |-- iPhone
                                    |-- iPad
                                    '-- Mac (Apple Silicon)
```

**Convert modules in place; do not relocate them.** Making an existing `core:*`
module multiplatform while keeping its Gradle path and package costs roughly zero
downstream edits. Moving sources into `shared/` would have rewritten 94 imports
and 11 build files for `:core:model` alone, for no benefit. `shared/` depends on
those modules and re-exports them.

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

Nothing in `app` can reach `commonMain` without first being extracted into a
module. That extraction, not Swift interop, is the bulk of the work.

### Platform-bound surface

| Dependency | Files | Disposition |
|---|---|---|
| Hilt | see below | Replace with Koin |
| Room | 71 | Room 2.7 supports KMP; migration needed but a path exists |
| Glance widgets | 36 | No iOS equivalent. WidgetKit rewrite in Swift |
| Sherpa ONNX (TTS) | 14 | Ships as an Android `.aar`. Needs an iOS replacement |
| WorkManager | 11 | Map to `BGTaskScheduler` |
| Whisper TFLite | 5 | TFLite has iOS support; needs rebinding |
| `androidx.car` | 4 | Rewrite as CarPlay |
| WorldWind | 2 | **Already publishes iOS artifacts.** Lowest-risk 3D piece |
| SceneView / Filament | 1 | Custom humanoid mesh; needs a native path |
| media3 / ExoPlayer | 1 | Map to `AVFoundation` |
| TensorFlow Lite | 1 | Salah posture model |

### Hilt, the gate

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

Hilt is JVM-only. Every one of these must become Koin before the affected code
reaches `commonMain`. This is the single largest blocker and it gates almost
everything past the model layer.

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
3. Move `prayer/model` and `prayer/calculator` into `shared/commonMain` — blocked
   until `AstronomicalCalculator` stops being `@Singleton`/`@Inject`.

**Verification is non-negotiable here.** These 2,378 lines produce every time on
the dashboard. `DayPrayerTimesTest` must stay green, `everyMinuteOfTheDayHasAtMostOneCurrentPrayer`
in particular, and computed times must be compared on-device before and after. A
clean compile proves nothing. Note that a daytime screenshot cannot catch a
midnight bug — that is exactly how the late-Isha bug survived.

## Phase 3 — Hilt to Koin

The gate. ~131 `@Inject` sites, 108 `@Module`s, 63 `@HiltViewModel`s.

Do it on Android first, with the app still shipping, and verify at each step.
Koin works on both platforms, so this is a same-behaviour refactor rather than a
port — which makes it verifiable against the running app instead of against a
simulator that does not exist yet.

## Phase 4 — Extract `app` into modules

The 156k-line monolith. Work outward from what the iOS app needs first:
`prayer` (15k) → `islamic` (3k) → `feature` (66k). Each extraction is
independently verifiable on Android.

## Phase 5 — `iosApp/`

An Xcode project embedding `Shared.framework`. First milestone should be small
and real: prayer times rendering on the simulator, not a full app.

Deferred decision: the framework `baseName` prefixes every exported class, so
`SharedLog` currently surfaces as `SharedSharedLog`. Settle the naming once Swift
call sites exist.

## Phase 6 — Platform rewrites

WidgetKit, CarPlay, `AVFoundation` media, `BGTaskScheduler`, TTS replacement,
Filament/SceneView humanoid. Each is a genuine rewrite with no shared code.

**Request CarPlay entitlements from Apple early** — they are granted by app
category and the approval runs independently of code readiness.

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
- **Additive changes only in the prayer flow.** The auto-detection path is
  fragile. Prefer defaulted parameters over changed signatures.
