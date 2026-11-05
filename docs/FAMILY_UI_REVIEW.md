# Family UI review

The supplied contacts reference guides the compact header, circular shortcut rail, rounded list panel, segmented control, restrained rows and light/dark spacing. The call reference guides centered incoming identity, prominent accept/decline controls and edge-to-edge stacked video. **Colors follow the app's current theme**, including custom themes, rather than copying the reference's green palette.

The images below are actual Compose component renders from Robolectric/Roborazzi at 390 × 844, not design mockups or signed-device screenshots. Names are test fixtures; they are not seeded into the live Family list. Profile photos are not part of the current protocol, so these views use initials. These renders exclude the main app's floating navigation and native system call screens. iOS follows the same layout and shared theme roles; its final rendering still needs an Xcode/device check.

## Contacts

| App theme, light | App theme, dark |
| --- | --- |
| ![Family contacts light](../app/src/test/screenshots/Family/contacts-light.png) | ![Family contacts dark](../app/src/test/screenshots/Family/contacts-dark.png) |

Full account IDs and removal options live in the contact sheet. Rows retain a large voice-call touch target and open both call options when tapped. Real incoming/outgoing invitation states replace chat preview/unread UI from the reference. The list panel fills the viewport even with few contacts.

## Landing

| Light | Dark |
| --- | --- |
| ![Family landing light](../app/src/test/screenshots/Family/landing-light.png) | ![Family landing dark](../app/src/test/screenshots/Family/landing-dark.png) |

These fixtures show empty release-style fields and a disabled sign-in button. Debug builds retain the prefilled pilot account/password. The form supports password visibility, loading, error dismissal and keyboard scrolling. Product text does not expose debug/setup details.

## Calls and theme switching

| Incoming voice call | Active voice, dark | Different app theme |
| --- | --- | --- |
| ![Incoming call](../app/src/test/screenshots/Family/incoming-call.png) | ![Active voice call](../app/src/test/screenshots/Family/active-voice-dark.png) | ![Royal theme contacts](../app/src/test/screenshots/Family/contacts-royal.png) |

The contact page, illustration, avatar containers, fields and actions resolve theme roles. Native iOS reads the same shared palette and saved seed values. Mute/camera states have visible selected treatment and accessible labels. System-bar contrast changes for light voice surfaces versus video. Compact-height calls place hang-up beside the other controls.

## Reproduce

```bash
./gradlew :app:testDemoDebugUnitTest \
  --tests '*FamilyScreenScreenshotTest' \
  -Proborazzi.test.record=false -Proborazzi.test.verify=true
```

To deliberately regenerate baselines after a reviewed layout change:

```bash
./gradlew :app:testDemoDebugUnitTest \
  --tests '*FamilyScreenScreenshotTest' \
  -Proborazzi.test.record=true -Proborazzi.test.verify=false
```

The suite covers light/dark landing and contacts, a second selected theme, incoming/active calls, search opening/closing and contact-sheet actions. Build the Android demo-debug APK and rebuild the iOS shared framework/app before device review. App-side changes do not require a server reinstall.
