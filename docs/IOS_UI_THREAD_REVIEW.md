# iOS UI-thread review

This is a targeted source review of reciter selection, content downloads, Home presentation and the Swift host. It is not an Instruments recording or a guarantee that every iOS code path is free of main-thread work.

## Confirmed issues corrected

| Path | Issue | Change |
|---|---|---|
| `shared/.../ui/ReciterSettingsSection.kt` | Cached catalog JSON was decoded in a composition initializer | Restore, parse, encode and persist the catalog on `Dispatchers.Default`; publish the resulting list on Main |
| Same picker | Storage counts/bytes were read synchronously and rescanned when completion counters changed | Background snapshots keyed to storage revision; live counts use download state; selection-only mode performs no storage scan |
| Same picker | Remove action could delete an entire reciter's files directly from a button callback | Background deletion, busy/error state and disabled conflicting controls |
| `shared/iosMain/.../media/IosReciterLibrary.kt` | Bulk download setup checks up to114 cached tracks from a Main-launched job | Run the bulk storage/download workflow on Default; progress is a thread-safe StateFlow |
| `shared/iosMain/.../assets/IosAssetPlatform.kt` | Suspended cache, file-size, checksum, commit and delete adapters inherited their caller's thread | These filesystem/checksum operations switch to Default inside the adapter |
| `core/asset-cache/.../CloudAssetRepository.kt` | Manifest parsing inherited its caller's dispatcher | Manifest loading/parsing runs on Default, retaining the existing mutex/cache |
| Android storage counterpart | Storage observation could scan disk during item composition | Collect snapshots on IO; use a concurrent status map for the new background access |

The reciter picker also reuses its loaded catalog when reopened, remembers search filtering and animates selection colours without changing row geometry. The reader picker excludes storage-management panels. Selection dismisses the dedicated sheet through its hide animation rather than immediately removing it.

## Paths inspected that already have appropriate dispatching

- iOS Quran verses/metadata and tafseer reads use Default.
- iOS Fortress and news repository database queries use Default.
- Large category downloads/verification in `IosComposeRoot.kt` already run on Default and coalesce per-chunk UI publications through `ContentDownloadProgressThrottle`.
- The speech service dispatches event notifications to Main; those blocks publish results rather than perform the inference itself.
- Swift Now Playing writes are intentionally dispatched to Main for platform UI integration. This is not evidence that all main-queue dispatches are a problem.

## Work that remains deliberate on Main

Compose/SwiftUI state publication, gestures, navigation, sheet animation and native audio-session/player ownership remain on their appropriate UI/lifecycle thread. Moving these indiscriminately to a worker would introduce correctness problems. Small preference reads and individual cache-existence checks are distinct from full-directory scans or hashing an audio/model file.

## Remaining measurement and verification

Native iOS compilation and Instruments are unavailable on this Linux host. Source review cannot establish frame rate, exact stall durations or thermal effects.

On a release build on a real iPhone:

1. Record Time Profiler plus Animation Hitches while opening, searching and scrolling the reciter picker; selecting a reciter; and dismissing the sheet.
2. Repeat with a populated offline library and a bulk download active.
3. Measure cancellation/removal and navigating between Home, reader and storage while the banner updates.
4. Confirm no filesystem enumeration, catalog parsing or large checksum routine appears on the Main Thread call tree during those actions.
5. Check Allocations for retained sheets/players and repeated large JSON allocations.
6. Test dark/light themes and larger text. Confirm gestures, status-bar contrast and sheet safe areas on device.

These measurements remain pending. Unit tests cover manifest/cache policy and reciter/download-state semantics; they do not prove an absence of iOS frame hitches.

## Screenshot contrast review

The Home profile action inherited dark foreground colours over dark artwork. It now uses a white foreground in artwork-overlay mode. Home feed section labels also inherited a light-theme dark foreground while sitting on a night image; the feed now uses a consistent light foreground and dark translucent backing.

The screenshot's dark native status-bar text needs a host-side/device check. The shared Compose foreground changes do not automatically change UIKit's status-bar appearance. No claim is made that this native contrast issue has been verified or fixed by the shared colour changes.
