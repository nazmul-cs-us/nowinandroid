# iPhone incoming calls without a paid Apple push setup

Research date: 6 November 2026. This is an implementation review and source-based
feasibility assessment, not a claim that locked/terminated iPhone delivery has
passed. No VM configuration or phone installation was changed for this research.

## Result

An installed Home Screen web app using standard Web Push is the practical route
for a visible call alert with our current credentials. It needs a self-generated
VAPID key, HTTPS, iOS 16.4+, installation, and notification permission. It does not
need our own Apple Developer membership/APNs signing key. Apple's/browser push
infrastructure still carries the notification; VAPID does not mean delivery
avoids Apple services.

Our existing helper implements this route. It alerts the user, who opens the
helper and explicitly taps **Open Deenly**. The native app then fetches the
current authorized call. It cannot automatically launch the native incoming-call
screen, answer, or start microphone/camera capture.

Native app wake-up and system incoming-call presentation remain the job of
properly provisioned APNs VoIP / PushKit / CallKit. Native Firebase Messaging on
iOS also uses APNs; the Android Firebase configuration alone does not provide an
independent iPhone delivery channel.

## Why the prayer Live Activity survives

`iosApp/Sources/PrayerNotificationCoordinator.swift` requests the prayer activity
locally. Its `Activity.request` calls do not pass `pushType: .token`.
`PrayerLiveActivityWidget.swift` uses `Text(timerInterval:...)` and
`ProgressView(timerInterval:...)`; iOS advances those displays from dates already
provided. A ticking countdown is not evidence that Deenly is still executing.

The coordinator observes activity and push-to-start tokens, but that observer is
not a push transport or a continuous network listener. The default push-backend
URL is empty. The normal app configuration has `FAMILY_BACKGROUND_CALLS=NO` and
only the legitimate audio background mode. It does not install a background
polling task. Changing a Live Activity's layout, requesting frequent updates, or
keeping it visible cannot make it discover an unscheduled incoming XMPP call.

## Options assessed

| Approach | Can discover a new call while native Deenly is closed? | Assessment for Deenly |
| --- | --- | --- |
| Current prayer Live Activity / its countdown | No | Displays already-known schedule data; no call listener. |
| Push-updated or push-to-start Live Activity | Requires APNs delivery | Another APNs route, not a way around provisioning. An activity alone is not native call presentation. |
| Native FCM on iOS | Requires APNs delivery | Firebase can manage ordinary pushes after Apple configuration; it does not replace Apple's device transport. |
| APNs VoIP + PushKit + CallKit | Supported native call delivery mechanism | Production direction once the Apple account/key/provisioning are available; verify actual device/OS behavior. |
| Home Screen Web Push helper | Can receive a browser alert independently of native Deenly | Recommended temporary route. User must tap through to Deenly. Real-phone timing remains unverified. |
| Short `beginBackgroundTask` grace period | No cold launch | Can briefly finish work after leaving the app; execution expires. No guaranteed listening window. |
| Background refresh / processing | No prompt-call guarantee | OS chooses when work runs; unsuitable for our 45-second ringing deadline. Force-quit is a separate stopping condition. |
| iOS 26 continued-processing task | No closed-app listener guarantee | API exists for continuing processing; do not treat its existence as permission for an indefinite call-listening daemon. No device prototype was run. |
| Already-playing Quran audio / an already-active call | Only opportunistically while execution remains available | Our real audio feature may permit background execution. It cannot cold-launch a killed process or guarantee availability when playback stops. |
| Silent audio, a repeating timer, or an open XMPP socket alone | No reliable closed-app delivery | None provides a supported independent wake-up transport; a forced termination still removes the executing process. |
| Scheduled local notifications | No discovery by themselves | Can alert for known times or for a call already received while code is executing; cannot find an unknown new call. |

“Backgrounded” means the UI is out of sight, with execution controlled by iOS.
“Force-quit” means the user swiped away the native app. Android's task removal and
Settings Force stop also differ. Tests must record the exact state instead of
calling all of them “killed.” Web Push notification delivery remains subject to
OS/browser policy, permission, network, Focus, and device state; this is not a
guarantee of ringing in every state.

## A useful limited native experiment

A local call notification could be posted when an authorized XMPP invitation
actually reaches an executing background app, for example during genuine Quran
playback or a short allowed background window. Tapping would resume Deenly and
revalidate the call. This could improve **some background** cases without APNs.
It would not detect a call after the process stops and must not be presented as
closed-app incoming-call support. This experiment has not been implemented or
measured on a phone; there is no reason to delay the Web Push pilot for it.

## Existing helper audit and checks

The checked-in implementation already contains:

- Home Screen manifest with `display: standalone`, HTTPS registration, and an
  explicit notification-permission action.
- A service-worker push handler that displays a visible notification, including
  a generic alert for expired/malformed deliveries. It does not silently poll.
- VAPID / AES128GCM encryption, short push TTL, and high urgency.
- An authenticated, short-lived pairing code. Account passwords never enter the
  browser. The helper subscription expires after 24 hours and can be revoked.
- Family-authorized dispatch and a native deep link that cannot auto-answer.
- A final native XMPP check: only the matching, still-ringing incoming call can
  be accepted. A late notification does not revive an ended call.

Research checks run against the current repository:

```bash
PYTHONPATH=/workspace/.environment/family-push-python:server/family/push \
  python3 -m unittest -v \
  test_web_helper.ProviderTests.test_generated_pem_key_encrypts_with_real_webpush_library \
  test_web_helper.StoreTests.test_delivery_uses_authorized_targets_and_keeps_payload_minimal \
  test_web_helper.StoreTests.test_logout_generation_revokes_old_helpers \
  test_web_helper.TLSTests.test_public_helper_serves_over_verified_tls
node server/family/push/web/test_worker.cjs
```

All four Python tests and the service-worker check passed. The encryption test
uses the real Web Push library with a local HTTP adapter, not Apple's provider.
The HTTPS test uses a local test server. These establish local implementation
behavior, not provider acceptance or iPhone notification delivery.

## Recommended next experiment

Use [Temporary Family call alerts](FAMILY_WEB_ALERTS.md) for the existing VM
installation and firewall commands. Deployment has not been verified in this
research. Deploy between test calls because the installers restart services.

1. Install the helper from Safari on one iPhone running iOS 16.4+; open its Home
   Screen icon, pair from Family, and explicitly allow notifications.
2. With the native app open, place one authorized Android-to-iPhone call and
   verify helper notification delivery and native open/answer.
3. Repeat with native Deenly backgrounded, the screen locked, and native Deenly
   explicitly swiped away. Also test with the helper UI closed.
4. Measure call-send, alert-arrival, user-open, and answer times. The full path
   must fit inside the server's existing 45-second ringing deadline.
5. Cancel before tapping and deliver after expiry: Deenly must refuse to answer
   and offer calling back. Verify sign-out/revocation and denied notification
   permission as well.

Use an observer/manual timing record without recording account passwords,
pairing codes, push subscription endpoints, or media payloads. If the user reaches
the app after expiry, show a call-back state; do not extend call authorization or
pretend a missed call is still ringing.

## Sources and verification limits

Sources actually fetched/read during this research:

1. [MDN PushManager compatibility data](https://github.com/mdn/browser-compat-data/blob/main/api/PushManager.json): Safari iOS 16.4; notifications supported in web apps saved to the Home Screen.
2. [MDN Notification compatibility data](https://github.com/mdn/browser-compat-data/blob/main/api/Notification.json): iOS Home Screen/non-default display requirement; notifications shown from a service worker.
3. [MDN Push API documentation source](https://github.com/mdn/content/blob/main/files/en-us/web/api/push_api/index.md): the browser starts a service worker as necessary for incoming push.
4. [Firebase iOS quickstart](https://github.com/firebase/quickstart-ios/blob/main/messaging/README.md) and [Firebase Messaging implementation](https://github.com/firebase/firebase-ios-sdk/blob/main/FirebaseMessaging/Sources/FIRMessaging.m): APNs configuration/token handling in the native iOS route. The quickstart contains legacy setup wording; the current SDK source was also inspected.
5. [Expo background-task documentation source](https://github.com/expo/expo/blob/main/docs/pages/versions/unversioned/sdk/background-task.mdx): BGTaskScheduler is deferred/system-scheduled; distinguishes iOS swipe-to-terminate from Android task removal.
6. [Microsoft UIKit documentation source](https://github.com/dotnet/macios/blob/main/docs/api/UIKit/UIApplication.xml) and [BackgroundTasks bindings](https://github.com/dotnet/macios/blob/main/src/backgroundtasks.cs): background fetch is OS scheduled; iOS 26 continued-processing API availability. Binding declarations establish API presence, not that an indefinite call listener is supported.
7. [WebKit push daemon implementation](https://github.com/WebKit/WebKit/blob/main/Source/WebKit/webpushd/WebPushDaemon.mm) and [service-worker push subscription implementation](https://github.com/WebKit/WebKit/blob/main/Source/WebKit/NetworkProcess/ServiceWorker/WebSWServerConnection.cpp): browser-owned push handling, permissions, active registration and application-server key use.
8. Deenly's ActivityKit, FamilyStore/SystemCalls, service-worker, VAPID sender,
   pairing, deep-link, and deployment sources, reviewed at this repository state.

Direct HTTPS access to developer.apple.com, webkit.org, firebase.google.com and
the GitHub REST API was blocked by the cloud environment's outbound policy.
No policy was changed and no alternate proxy was used. Consequently the newest
Apple documentation, membership terms and WWDC guidance were not independently
re-read. Useful official follow-up links are Apple's
[Live Activities](https://developer.apple.com/documentation/activitykit/displaying-live-data-with-live-activities),
[PushKit incoming VoIP handling](https://developer.apple.com/documentation/pushkit/responding-to-voip-notifications-from-pushkit),
and WebKit's [iOS Web Push announcement](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/).
These are follow-up references, not pages successfully retrieved in this session.
