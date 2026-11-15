# iOS follow-up — 15 November 2026

This follow-up addresses the five gaps from the app comparison. It is not a
claim that every Android integration has an identical iOS implementation.

## Implemented

| Area | Result |
| --- | --- |
| Contact details from chat | Chat and the directory now use one full `FamilyContactProfileView`. It observes the current member, shows current presence, and offers the same message, call, invitation and removal actions. Actions run after profile dismissal so a profile does not cover a new call. A removed contact cannot retain stale call buttons. |
| Voice call screen | iOS now uses a patterned caller card and overlapping control shelf, with only the actual two participants. Incoming accept/decline, outgoing cancel, mute, speaker, hang-up and P2P/relay status remain connected to the existing call session. Portrait and landscape layouts use scrollable content for small screens and larger text. The pattern is the same artwork bundled on Android; surfaces and tint come from the selected theme. |
| Family navigation | iOS 16.1–25 now use classic native Home, Saved, Family, More and Voice tabs. iOS 26 keeps its modern native tab presentation. Both use the same selection, unread badge, deep-link, More navigation and voice-assistant state. The older floating Family button and modal entry were removed. Family's Back to app returns Home. |
| Quiet prayer time | iOS Notifications settings now include a Focus setup guide and a public link to Deenly's system settings. The guide explains allowed people/apps, manual activation, optional user-created time schedules, and keeping schedules current as prayer times change. It does not enable Android's DND preference or claim that Deenly can automatically change iOS Focus. |
| Incoming call readiness | Family settings show whether the build supports native background calls and whether this device has registered with Family. Registration is not presented as proof of APNs delivery. The temporary Home Screen helper remains available. A read-only VM checker validates the Apple identifiers and signing-key format without printing credentials. |

Home artwork settings, the removed Restricted mode control and the redesigned
Today's collection gallery are shared and already apply to both platforms.

## Native incoming calls still require Apple provisioning

PushKit and CallKit are implemented. A Firebase-only installation cannot receive
native iPhone call pushes after suspension or termination. The normal build
stays usable with a Personal Team; it does not claim that a local Live Activity
can discover incoming calls.

Once a paid Apple Developer account and APNs key are available:

1. Enable Push Notifications for the app identifier and configure the matching
   development team in the uncommitted `iosApp/Config/Local.xcconfig`. Keep the
   app, widget and Live Activity targets on the same team.
2. On the Mac, generate the push-enabled project and build for the iPhone:

   ```sh
   cd iosApp
   xcodegen generate --spec project-push.yml
   xcodebuild -project iosApp.xcodeproj -scheme iosApp \
     -configuration Debug -destination 'generic/platform=iOS' \
     -derivedDataPath ../work/ios-push-device \
     -allowProvisioningUpdates build
   ```

   Debug uses `APNS_ENVIRONMENT=development`. Distribution builds need
   `APNS_ENVIRONMENT=production` and matching provisioning. Do not run the
   default project-generation script afterward: it regenerates `project.yml`
   and restores the normal non-push build configuration.
3. Install the private `.p8` key on the VM and set `FAMILY_APNS_TEAM_ID`,
   `FAMILY_APNS_KEY_ID`, `FAMILY_APNS_KEY_FILE` and `FAMILY_APNS_BUNDLE_ID` in
   `/etc/mongooseim/family-push.env`. The bundle identifier must match the signed
   app. Keep provider keys on the server and out of Git/chat.
4. On the VM, check the local configuration, then restart the bridge:

   ```sh
   cd "$HOME/deenly-family-setup"
   sudo /opt/deenly-family-push/venv/bin/python3 server/family/push/apns_check.py
   sudo systemctl restart deenly-family-push
   sudo systemctl is-active deenly-family-push
   ```

   The checker validates local identifiers and the EC P-256 key; it cannot
   verify Apple account entitlements or real push delivery.
5. Install/open the signed app, sign in, and check Family → Settings → Incoming
   calls. Test calling the iPhone while open, backgrounded, locked and closed.
   Confirm CallKit answering, cancellation, expiry and bidirectional media.
   Then repeat in both directions and on mobile data.

The earlier confirmed Firebase-only account setup does not complete these Apple
steps. No Apple credentials were supplied or configured by this change.

## Verification

Linux can build shared Kotlin/Android, run focused Compose and APNs-checker
tests, inspect screenshot baselines and parse Swift syntax. Swift parsing is
not SwiftUI type checking, Xcode linking, signing or device validation.

Completed on this checkout: Android demo APK and shared metadata compilation;
26 image-module tests and 33 focused app tests (including Focus guidance and
artwork repository/UI regressions); 21 artwork-tool tests; five APNs-checker
tests; Swift syntax checks for 22 source/test files. All passed. The Focus guide
was also inspected at 150% system text size. Xcode and device checks below have
not been run in this Linux workspace.

On a Mac, use `bash scripts/build_ios_simulator.sh` for the normal build.
Run `AndroidParityUITests`, including Family navigation, and
`SettingsParityUITests/testPrayerFocusGuideExplainsUserControlledScheduling`.
The Debug-only `--classic-tabs` launch argument exercises the older tab shell
on a newer simulator; `--start-family` selects Family at launch.

With two accepted accounts, compare the chat and directory contact profiles,
start calls from each, and verify profile-back navigation preserves the chat
draft. Check voice-call controls, route labels, safe areas and scrolling in
portrait, landscape, light/dark themes and larger text on real devices.

The previous audit's shared-versus-Android Quran pagination and pinch differences
remain a separate reader task; this follow-up does not certify identical page
breaks across the two renderers.
