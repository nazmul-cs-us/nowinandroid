# Family UI review

The supplied contacts reference guides the deep theme-derived header, persistent search field, compact circular shortcut rail, solid asymmetric list panel, restrained rows and light/dark spacing. The call reference guides centered incoming identity, prominent accept/decline controls and an inset remote video card with centered caller identity and an overlapping control shelf/local thumbnail. **Colors follow the app's current theme**, including custom themes, rather than copying the reference's green palette.

The images below are actual Compose component renders from Robolectric/Roborazzi at 390 × 844, not design mockups or signed-device screenshots. Names are test fixtures; they are not seeded into the live Family list. Profile photos are not part of the current protocol, so these views use initials. These renders exclude the main app's floating navigation and native system call screens. iOS follows the same layout and shared theme roles; its final rendering still needs an Xcode/device check.

## Contacts

| App theme, light | App theme, dark |
| --- | --- |
| ![Family contacts light](../app/src/test/screenshots/Family/contacts-light.png) | ![Family contacts dark](../app/src/test/screenshots/Family/contacts-dark.png) |

Full account IDs and removal options live on the full-page contact profile. Rows retain a large voice-call touch target and open accepted-family chat when tapped. Avatar shortcuts open the contact profile. Message previews, timestamps and unread badges now accompany live presence; incoming/outgoing invitations remain available from Family settings → Invitations. The list panel fills the viewport even with few contacts.

## Chat and inbox

| Conversation | Message previews and badges | Notification inbox |
| --- | --- | --- |
| ![Family conversation](../app/src/test/screenshots/Family/chat-light.png) | ![Conversations dark](../app/src/test/screenshots/Family/conversations-dark.png) | ![Notification inbox](../app/src/test/screenshots/Family/notifications-light.png) |

Only accepted members can open chat. The composer remains above the keyboard and the main navigation is hidden during a conversation. Inbox content covers invitations, messages and missed calls. Screenshots use fixtures; delivery and policy checks are covered separately by server/client tests.

## Contact profile and sheets

| Full-page contact profile | Compact invitation sheet |
| --- | --- |
| ![Contact profile](../app/src/test/screenshots/Family/contact-profile-light.png) | ![Invitation](../app/src/test/screenshots/Family/invitation-light.png) |

The reference profile is a full page, so contact details no longer use a bottom sheet. A compact identity header, account details, call shortcuts and primary Message action replace the oversized sheet hero and padded action cards. The back control returns to Family. On iOS the profile is a navigation destination, so it does not compete with the incoming-call modal. Invite and notification sheets have clear close actions and shorter headers.

The reference video and stills also contain profile photos, shared media, voice messages and group participants. This update styles the supported one-to-one text/call flows; initials represent real accounts without profile photos. It adds no fictitious people, read receipts, gallery, or nonfunctional attachment controls. A single message check means saved to the server.

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

Appearance settings now offer [curated themes](APPEARANCE_THEMES.md) with System/Light/Dark modes. Existing saved palettes, including those used in these snapshots, continue to render until another theme is selected.

## Video call

![Active video layout](../app/src/test/screenshots/Family/active-video-light.png)

The media areas in this render are labeled fixtures, not camera frames. On a real call the remote feed fills the canvas and the local feed appears in the smaller thumbnail. The route pill reports the selected ICE path; voice renders show Direct (P2P), and this video fixture shows TURN relay. Neither fixture establishes a real device connection.

## Verification boundary

The Android demo-debug build, 33 shared Family policy/route tests, 53 targeted Android checks, 50 server tests with the real Jiffy codec and with the iodata fixture, and 13 installer/signup Python checks passed locally. The screenshot baselines were visually reviewed and then verified. Modified native iOS sources pass syntax parsing; this Linux environment cannot run Xcode or validate signed-device media. The VM smoke script now includes real chat delivery and unread checks, but needs execution on the deployed VM.

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

The suite covers light/dark landing and contacts, a second selected theme, incoming/active calls, search opening/closing and contact-sheet actions. Build the Android demo-debug APK and rebuild the iOS shared framework/app before device review. The chat/inbox update also requires the server deployment in [Family chat and inbox](FAMILY_CHAT.md).


### Reference refinement validation

After reviewing the uploaded 24-second reference video and stills, the contact list, chat bubbles/date groups, full-page profile, invite/inbox sheets and video control shelf were refined on Android and native iOS. Header and outgoing-bubble colors derive from the selected primary theme; no reference-green palette is hardcoded. Android system-bar contrast follows the header and framed video surfaces. The Android APK and shared metadata build pass; updated renders cover light/dark and Royal themes. Native iOS syntax passes; a signed Xcode/device check is still required. This refinement changes app UI only and requires no new VM installation beyond the existing chat/calling rollout.


### Foreground session recovery

The previous restoration path ran on app foreground only; a socket closing while the app stayed open could leave the sign-in form visible. Both native clients now retry saved credentials on transient foreground loss, back off to 30 seconds, probe on network restoration and detect half-open sockets through failed heartbeats. During recovery the Family list/chat stays visible; offline users are not marked online and call controls remain disabled. Explicit sign-out and rejected credentials suppress recovery. Shared policy tests cover these guards and bounded backoff; a Compose check keeps the reconnecting state out of the login form. Network handoff and long idle sessions still need real-device validation on Android and iPhone.

## Reference composition pass

The earlier pass retained too much of the original app structure. The reference uses a dedicated chat navigation bar and a floating video tray; matching its header color alone did not produce the same composition.

| Reference detail | Implementation in this pass |
| --- | --- |
| Dedicated chat footer with a raised center action | Signed-in Family replaces the app-wide navigation with Contacts, Chats, Invite, Notifications and Family settings. Back to app remains in settings/menu. Signed-out Family keeps the app navigation. |
| Compact unlabeled avatar strip | Smaller circles with explicit accessible contact labels; profile details remain one tap away. |
| Neutral compact typography | Android Family uses platform sans-serif with zero default tracking, while other app typography is preserved. Native iOS uses its existing system font. Colors continue to derive from the selected Deenly theme. |
| Narrow rounded conversation bubbles | Width is bounded relative to the phone, with bottom-aligned sender avatars, external timestamps, a slim composer and a functional conversation-actions menu. The additional menu offers supported call/contact actions. |
| End-call button above the translucent control tray | Hang-up floats over the tray edge. Real local and remote participant tiles replace the oversized Family-video-call text panel. The selected ICE route remains visible below the timer. |
| Tall contact poster and feature footer | The full-page contact profile uses the same feature navigation and a theme-derived poster fallback for accounts without a photo. |

The longer conversation screenshot uses realistic family-message fixtures to exercise multiple bubble lengths, sender groups and timestamps. These messages are test data, never inserted into real accounts. The reference includes photographs, voice recordings and multiple participants; the current private service supplies account initials, text messages and one-to-one calls. The implementation displays those real capabilities without fabricated profiles or group-call controls.

The renders include Family's feature footer. Android's app-wide bar is suppressed while signed into Family; native iOS hides its app tab bar on these pages. Navigation actions, contact opening/back, call controls, invite dismissal, notifications and screenshot baselines are checked with the real Compose UI. Native iOS syntax is checked separately; Xcode and a signed-device pass remain necessary.
