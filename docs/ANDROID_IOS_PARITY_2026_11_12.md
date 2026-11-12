# Android / iOS parity review — 12 November 2026

Reviewed the 18 commits after `bcd5b63cd` through `e8175ede4`, covering changes
from 9 November 2026 00:00 Asia/Dubai through the latest pulled main. This is a
source audit; the simulator validation recorded in the 4 November review does
not validate these later changes.

Android Home and Family have separate UI implementations from iOS. Sharing
images, settings, protocol policies, and models does not automatically update
`InsightPager.kt` or the SwiftUI Family screens when Android Compose changes.

## Coverage and corrections

| Area | Finding / current change |
| --- | --- |
| Home artwork rotation, Refresh now, deadline and foreground refresh | Already shared in `core:images`, with Android and iOS image loaders. Shared Appearance renders the same artwork settings. No duplicate implementation added. |
| Home Quran reader | Android embeds its Surah Mushaf pager; iOS still used a separate continuous text rendering. Home now embeds the shared Surah `MushafPagerView`, displays a page counter, preserves reading preferences, and aligns tajweed offsets after removing a separately displayed Bismillah. It no longer measures the whole chapter to size the reader tile. |
| Home Hadith reading language | The shared tile already handled Arabic/English and machine translation, but could treat Bengali collection text as English. Local Bengali is now used directly; it is not sent to the English-source translator or labeled as English. |
| Grounded suggestion language | Android's recent translation path was absent from the shared suggestion renderer. Shared suggestions now resolve the selected Quran translation, use verified Arabic sources when available, and label machine translations and unavailable-translation fallbacks. Source identity and the verified-source action remain unchanged. |
| Family navigation | Android offers Family directly; iOS 26 kept it under More. Family now has a native tab and unread badge. Interests remains available under More. Call links select the Family tab; Back to app returns Home. The voice assistant overlay is excluded from Family. |
| Family messaging, inbox, presence, call history and video previews | iOS already has implementations in `FamilyView.swift`, `FamilyStore.swift`, `FamilyChatModels.swift`, and `FamilyWebRTC.swift`; these features were not wholly missing. Their presence is source coverage, not proof of device behavior or visual parity. |
| Shared Back handling | The pulled splash and Home reveal imported a Compose native-only BackHandler API in common code, failing Android compilation. An expect/actual wrapper now selects Android Activity handling or the iOS Compose back dispatcher. |

## Remaining differences to verify or port

- Android's Mushaf fits text into viewport-sized pages. The existing shared
  renderer groups ayahs by canonical Mushaf page and scrolls within a page.
  Reusing it brings Home and Surah details into agreement on iOS; exact page
  breaks and pinch interactions still differ from Android.
- iOS 17–25 retains the older Family toolbar-sheet entry. The new direct tab
  applies to the iOS 26 native tab host.
- Family chat's Contact details action still opens a basic account alert on
  iOS, while Android opens the full profile. The iOS directory already has a
  full contact profile, which should be reused for that chat action.
- Android's recent patterned voice-call card and overlapping control shelf do
  not yet have the same visual composition in the iOS voice-call screen.
- Incoming/background calls, CallKit, media handoff, video safe areas, keyboard
  behavior and status/tab-bar transitions require simulator and physical-device
  checks. Android Telecom and iOS CallKit remain native implementations.

## Validation

Linux validation covers shared Android compilation, relevant common unit tests,
Family policy tests and Android compilation. It cannot validate SwiftUI types,
Xcode linking, simulator screenshots or device calling behavior.

Completed on this checkout:

- `:app:compileDemoDebugKotlin` and `:shared:compileDebugKotlinAndroid` passed.
- Shared Mushaf flow/opening, Home reading translation and reflection tests:
  **9 passed**, zero failures.
- `:core:family:testDebugUnitTest`: **39 passed**, zero failures.
- `git diff --check` passed.

`AndroidParityUITests` now checks that the expanded Home Quran reader exposes a
page counter and that Family is reachable directly from the native tab bar.
These tests are added for the next Mac run; they have not been run here.

On a Mac, build from the repository root:

```sh
bash scripts/build_ios_simulator.sh
```

Then run `iosAppUITests/AndroidParityUITests` on an available iOS 26 simulator,
followed by Family chat/call checks on paired devices. The build script retains
diagnostics in `work/ios-simulator-build.log`.
