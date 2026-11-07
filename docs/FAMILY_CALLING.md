# Family calling

For the actual completed VM/app setup, commands and reported live results, start with [Calling setup record](CALLING_SETUP_RECORD.md). Background push deployment is tracked separately below.

This implements one-to-one voice/video calls on Android and iOS, with Family pages, invitation acceptance/removal, native WebRTC, and MongooseIM authorization. The call UI uses full-screen, stacked video feeds, caller initials, a timer, and circular controls inspired by the supplied incoming-call reference. Background call delivery adds Android FCM/Telecom and optional iOS PushKit/CallKit; see [background deployment and testing](BACKGROUND_CALLING.md). This is a private versioned XMPP protocol, not Jingle interoperability. Deployment and real-device acceptance are separate from code/build validation.

## App workflow

Android has a **Family** navigation destination. iOS 26+ has **More → Family**; earlier supported versions have a Family button opening the page.

The contact screen uses the app's active theme containers, circular family shortcuts, a rounded full-height list panel, expandable account search and a compact Family/Invites control. Contact rows have one voice-call shortcut; tapping a row opens a sheet with both voice/video actions and full account details. The landing screen has a themed illustration and sign-in form. Names are derived from account IDs until profile integration is available; circles show initials; accepted contact rows now show live presence with a green Online dot. See [Family presence](FAMILY_PRESENCE.md) for privacy, lease behavior and deployment. Invitations are composed in a sheet, and removal requires confirmation. See [rendered UI review](FAMILY_UI_REVIEW.md).

Android takes its colors directly from `MaterialTheme.colorScheme`. Native iOS consumes `FamilyThemePalette` from the same `iosColorScheme` used by the shared app, including saved custom seeds and light/dark settings; theme values are not duplicated in Swift. Theme changes invalidate the native views. Voice-call surfaces use these roles, video overlays retain readable scrims, and answer/end controls use primary/error roles with their matching foregrounds. Call controls adapt to compact landscape height.

Family's canvas now uses the same gradient function/stops as Home on each platform, with translucent list panels rather than an independent Family gradient. Theme changes in Appearance update both Home and Family.

For debug device testing, Android prefills `android@xmpp.starception.com` and iOS prefills `ios@xmpp.starception.com`, along with the shared test password. Tap **Sign in** to connect; the password field clears after each attempt. Android keeps the test password in debug-only resources, and iOS includes it only under `#if DEBUG`. After sign-in, the opposite test account is suggested in the invitation field. Release builds keep the sign-in and invitation fields blank. Both apps use the shared `FamilyEndpoint` settings for `xmpp.starception.com:5222`, require STARTTLS and server certificate verification, and allow a short username (`android`/`ios`) or a full account ID. The Family service readiness message appears only after its authenticated snapshot succeeds.

Create a Family account from the landing page, or sign in with an existing Family/XMPP account. See [Family signup](FAMILY_SIGNUP.md) for server deployment and validation. For reconnecting after a call push, credentials are saved in Android Keystore-encrypted, backup-excluded preferences or iOS Keychain with AfterFirstUnlockThisDeviceOnly accessibility. Signing out clears them and unregisters that installation. Debug builds prefill the requested pilot credentials; release builds do not embed them. Send an invitation to another Family account ID. Only its recipient can accept. Voice/Video controls appear for accepted members. Either party can remove a relationship, ending calling access.

Incoming calls appear over other screens while signed in with the app open. Microphone capture and peer transmission start only after acceptance and permissions. Mute, speaker/earpiece, camera on/off, camera switching, decline/cancel and hang-up are supported. Other app audio yields; playback is not automatically resumed afterward.

Android call notifications reuse the current Deenly launcher artwork: the
monochrome crescent for the small notification icon and the full app icon for the
large image and CallStyle avatar fallback. They no longer reference the legacy
calligraphy notification logo. Rebuild and install Android to receive this change.

Outgoing video calls now show a local-only camera preview while ringing, with compact caller identity and a Flip control over the full-screen feed. The caller already granted camera permission when starting the video call. This preview does not create a microphone or peer connection; it is released on cancel/backgrounding and reused after acceptance. Incoming calls do not open the recipient's camera before answering. Remote media still requires server acceptance. Android ongoing-call notifications use a separate low-importance channel and show Calling/Connecting accurately; incoming notifications retain their high-priority channel. Retest preview, cancel, background/resume and answer on signed devices after updating the apps.

Preview startup follows the actual resumed UI lifecycle and handles screens that were already resumed when the call arrived. It no longer depends on the process's manually counted activities. Background-registration errors are displayed on the Family page separately from call errors; an older server that cannot register push devices must not turn a foreground call into a generic error banner. Camera-preview failures have a specific call message. Android logs only the camera exception type under `FamilyCalls`, without account credentials, to assist device diagnosis.

Foreground tracking keeps activity instances rather than an integer count. A splash activity that started before the Family singleton must not decrement the foreground state when it stops after MainActivity has started. Regression coverage includes that startup order, initial-resumed preview startup, pause/resume, and call-error isolation. If a rebuilt device still cannot open preview, capture only the filtered `FamilyCalls` diagnostic with `adb logcat -s FamilyCalls`; permission alone does not validate camera opening or frame delivery.

The existing Firebase profile login is **not automatically mapped** to these accounts. Production identity/bootstrap from the design remains necessary before replacing the pilot login. Matching email strings do not establish linked accounts.

## Server protocol and enforcement

`server/family/mod_deenly_family.erl` targets MongooseIM 6.10.0 at source commit `872cfdb8bc8512aa495a10ff54e52a3c5e09b5db`.

Authenticated IQ `set` requests go to `xmpp.starception.com`, with JSON inside a `family` element in `urn:starception:family:1`. The server derives the user and installation from the authenticated full JID. IQ IDs correlate replies. JSON version is 1; replies contain `ok` or an error code.

| Action | Fields | Rule |
| --- | --- | --- |
| `snapshot` | — | Own members and current calls; refreshes selected installation's lease |
| `invite` | `peer` | Different existing account on this domain |
| `accept_member` | `peer` | Invitation recipient only |
| `remove_member` | `peer` | Either participant; ends their current call |
| `invite_call` | `peer`, `callId`, `kind` | Accepted family; two distinct users; neither busy |
| `accept_call` | `callId` | Invited user; first full JID wins |
| `end_call` | `callId` | Participant/selected installation |
| `turn` | `callId` | Active, selected, still-family participant |
| `signal` | `callId`, `payload` | Active selected participant; bounded SDP/ICE goes to the other installation |
| `register_device` | `deviceId`, `platform`, `token`, `environment` | Authenticated owner; bounded to eight installations; token ownership moves on account changes |
| `unregister_device` | `deviceId` | Removes only the current account's installation |

Relationships and authoritative calls live in the Mnesia **disk** table `deenly_family`. Eligibility and transitions are transactional and serialized by domain. Back up Mnesia consistently along with PostgreSQL; copying live files is not a consistent backup.

Ringing expires after 45 seconds. Clients reconcile every 20 seconds. Active installations have 75-second leases and a 55-minute maximum call duration, within one-hour TURN credentials. Ended calls are retained for one day, then pruned. Native clients reject terminal/stale call versions. Each user has at most 100 relationships, including invitations.

Events are accepted only from the authenticated server domain. Direct user-to-user message/IQ routing is rejected on this private calling server, ensuring call signalling goes through policy-controlled IQs. This is not a general chat configuration.

Removing membership stops further authorized signalling and credential issuance. Normal clients release media on terminal events/reconciliation. A server cannot forcibly terminate a direct media connection between two modified peers that both ignore termination.

The issuer reads `/etc/mongooseim/turn-secret`, never sends that static secret to phones, and returns temporary HMAC credentials. Keep coturn's existing quotas. Rotate its shared secret and the protected issuer file together.

For the optional, explicitly paired browser alert workaround, see [temporary call alerts](FAMILY_WEB_ALERTS.md).

## Deploy on the VM

Pull the latest `main` checkout on the VM before installing.

```bash
# From that checkout on the VM; validate locally first.
sudo bash server/family/install.sh "$HOME/mongooseim-6.10.0"
sudo install -m 0644 server/family/smoke.py /tmp/deenly-family-smoke.py
sudo -u mongooseim python3 /tmp/deenly-family-smoke.py
```

The smoke script reads the configured local C2s listener; `--host` and `--port` override detection. TLS always verifies `xmpp.starception.com`.

When the listener-IP argument is omitted, the installer preserves the current
C2s bind address. Pass `10.148.0.2` explicitly to restore this VM's private listener
if an older update moved it to loopback. The installer prints the chosen address.

The installer validates the source commit, compiles against release dependencies, backs up XMPP configuration, enables the module, and copies the existing coturn secret into a protected service-readable file. It restarts **MongooseIM**, interrupting its sessions. It does not restart/change coturn.

Once the smoke test passes, bind to the documented VM private interface behind GCP NAT:

```bash
sudo bash server/family/install.sh "$HOME/mongooseim-6.10.0" 10.148.0.2
```

In Cloud Shell, permit TCP 5222 from the pilot devices' actual public egress IPs. Replace the source-range placeholder; preserve existing rules:

```bash
gcloud compute firewall-rules create webrtc-family-xmpp \
  --project=webrtc-512816 --direction=INGRESS --action=ALLOW \
  --rules=tcp:5222 --target-tags=webrtc \
  --source-ranges=PHONE_PUBLIC_IP/32
```

Keep PostgreSQL, Erlang distribution/EPMD, and management endpoints private. Verify DNS, the VM tag, and static-IP reservation (previously unconfirmed). Check externally:

```bash
timeout 15 openssl s_client -starttls xmpp \
  -xmpphost xmpp.starception.com -connect xmpp.starception.com:5222 \
  -servername xmpp.starception.com -verify_hostname xmpp.starception.com \
  -verify_return_error -brief </dev/null
```

STARTTLS remains mandatory. Server `verify_mode = "none"` means clients authenticate with SASL instead of client certificates; both apps still verify the server certificate and hostname.

## Build and validate

Android from the repository root:

```bash
source /workspace/.environment/env.sh  # managed cloud environment
./gradlew :core:family:testDebugUnitTest :app:assembleDemoDebug --no-daemon
```

On a Mac with the existing iOS prerequisites, run `xcodegen generate` inside `iosApp/`, resolve Swift packages, and build/run the `iosApp` scheme. Rebuild the shared framework, which now exports `core:family`. Linux cannot validate an Xcode build or signed-device behavior.

Pinned dependencies: Android WebRTC 150.7871.04 (BSD 3-Clause), iOS `stasel/WebRTC` 150.0.0 with SwiftPM binary checksum verification, Smack 4.4.8 (Apache 2.0), and XMPPFramework commit `7d4dd1f215f18e9db5bbf1739403583c78afb4e7` (BSD-style). Retain their and their transitive dependencies' notices.

Device acceptance, with both apps open:

1. Sign in with different pilot accounts; invite and accept. Verify pending invitations cannot call.
2. Verify voice in both directions, mute/unmute, speaker/headset routing, and hang-up from each device.
3. Verify video previews/remote frames, camera toggle and switching.
4. Cancel, decline, expire ringing, and deny capture permissions. No capture may survive terminal paths.
5. Remove membership during a call; verify it ends and new calls fail. The live smoke test separately exercises server denials.
6. Repeat on Wi-Fi/mobile data and a forced-relay/restrictive network; inspect selected ICE candidates. Direct calls and the earlier browser data test do not prove native TURN media quality.
7. Test audio conflicts, network loss, backgrounding and server restart.

The EUnit tests use real Mnesia with small external-directory test doubles. `smoke.py` authenticates real clients, exercises policy/event delivery and TURN issuance, and removes its temporary accounts without printing passwords. It does not test native media.

## Remaining production gates

Background support requires deploying the push bridge, granting its FCM provider access, and rebuilding the apps. iOS incoming wake requires paid Apple Developer/APNs configuration and the optional push-enabled project spec; the regular project remains usable without those credentials. The new paths require locked/background/terminated-device acceptance tests before treating them as working in production. Explicit Android force stop prevents push delivery until the app is reopened. Production Firebase identity bootstrap is also pending.

Before production, validate backups/restore, monitoring, abuse/load limits, certificate renewal deployment to MongooseIM, restrictive-network TURN/TLS on 443, and the signed-device acceptance matrix. A successful build is not a production-readiness claim.


## Android sign-in diagnostics

The public endpoint remains `xmpp.starception.com:5222`, with mandatory verified
STARTTLS. Optional Firebase initialization/token failure is reported separately
and must not undo successful XMPP sign-in. Failure stages and exception class names
are logged without exception messages, stanzas, passwords or tokens:

```bash
adb logcat -d -s FamilyConnection
```

On the VM, check `sudo systemctl is-active mongooseim` and
`sudo ss -lntp '( sport = :5222 )'`. A private-interface deployment must listen on
`10.148.0.2:5222`; binding only `127.0.0.1` prevents phone connections. If the
listener is correct, verify that the phone's current network egress IP is included
in the existing GCP `webrtc-family-xmpp` firewall rule. Membership/push deployment
success alone does not verify network access from a phone.

## Incoming screen immediately disappears (6 November 2026 fix)

Both clients now attach a local call-state revision to each snapshot request.
An empty response may clear a call only if no call transition arrived while the
request was in flight. This prevents a pre-call snapshot from erasing a newly
received invitation; a current empty snapshot still recovers missed end events.

The iOS personal-team configuration (`FAMILY_BACKGROUND_CALLS=NO`) uses in-app
Answer/Decline and owns audio activation. A CallKit report/transaction failure
must not hang up the shared server call, and provider reset/end callbacks must
belong to a tracked system call. The full-screen view retains its presented call
while SwiftUI dismisses it, preventing an empty white dismissal frame. CallKit
failures log only their error domain/code in the `FamilyCalls` category.

After rebuilding both apps, verify Android → iOS and iOS → Android voice/video
with the receiving app on Family and on Home. Ringing should remain until an
explicit answer/decline, caller cancellation, server termination or timeout.
Answer must deliver two-way audio/video; hang up and immediately try a second
call to verify audio/session cleanup. Repeat presence tests with two iPhones on
the same account, one active and one backgrounded. Cloud unit/build checks do not
replace these signed-device checks.

## Read a short call diagnostic capture

The 6 November uploaded log recorded 21 calls disconnecting before reaching
Telecom Active, with incoming Ringing → Disconnected intervals of about 156–220 ms.
No Android fatal exception appeared. `LOCAL` in Telecom is not proof of a user
hang-up: our cleanup reports that cause even after a remote/server end. The
`Cannot find conference` audio-route warnings follow cleanup; they do not establish
the initiating failure.

That capture records installation/process start at 14:57 Dubai time, before fix
`dfdd0aa00` was committed at 15:21 Dubai time. No later installation/restart is
recorded, and its app logs contain none of that fix's `FamilyCalls` finish reasons.
Rebuilding alone does not replace the installed APK.

Diagnostics v2 adds a startup marker and traces session loss/reset, authorized
call stages, remaining ringing time, known server end reasons, Telecom callbacks,
and foreground-service cleanup. It logs no account IDs, passwords, tokens, SDP,
ICE payloads, or arbitrary server/exception text. iOS logs use the same
`FamilyCalls` category, including its system-calling configuration.

From the latest repository checkout, install and capture Android:

```bash
git pull --ff-only origin main
./gradlew :app:installDemoDebug
adb shell am force-stop com.starception.submission.demo.debug
adb logcat -c
adb logcat -v time FamilyCalls:V FamilyConnection:V AndroidRuntime:E '*:S' > family-call-log.txt
```

Leave that capture running, open Deenly manually, sign in if needed, and reproduce
one call. Press Ctrl+C. The file should start with `Diagnostics v2 loaded` and
remain small. Capture both directions separately if behavior differs. An iOS
console capture filtered to `FamilyCalls` can then identify a peer-side hang-up.

The 7 November Android capture reached authorized `active` revision 2, then ran
the local media/signalling failure handler about 600 ms later. The displayed
"Could not start this call" message narrows that attempt to relay-credential
fetching, native media startup, or draining queued signalling; it does not
identify which operation failed. This is a different failure from the earlier
immediate dismissal before acceptance.

Android startup tracing now prints fixed step labels for relay credentials,
native initialization, audio focus/routing, ICE configuration, peer creation,
microphone/camera, offer creation/application and queued signalling. A startup
exception logs its class and an allowlisted server error code, without exception
messages, account/call IDs, credentials, SDP or ICE candidates. Confirm the
startup marker includes `startup tracing enabled` after installing the updated
APK. These traces improve diagnosis; they are not evidence that the call failure
has been fixed. The existing VM Family smoke separately checks accepted calls,
TURN credential issuance and authorized signalling against the deployed server.
The user reran that VM smoke on 7 November: every check passed, including TLS
logins, call acceptance, participant-scoped TURN issuance and signalling delivery.
Native media startup for the failing phone attempt remains unresolved; this
server result does not establish native relay delivery or audio/video capture.

The next 7 November capture identified `step=audio-focus`, with an
`IllegalStateException`, immediately after successful relay credential retrieval.
Android required an immediate audio-focus grant and ended the call when the
system denied it. Telecom also remained ringing/dialling until ICE connected,
even after the server had authorized acceptance.

The Android fix marks the matching Telecom call active on authorized server
acceptance, including connections attached afterward. Media requests transient
voice-communication focus, accepts a delayed grant, and allows a bounded
three-second handoff before reporting an audio-specific failure. No microphone
or peer starts until focus is granted. Ending/cancelling a call abandons queued
focus, and late callbacks from an old request cannot start a new call. Genuine
focus loss after acquisition still interrupts the current call.

Robolectric regressions cover immediate/delayed grants, a brief denial followed
by a grant, permanent denial, cancellation/cleanup, stale focus callbacks, and
Telecom acceptance before media connectivity for both directions. Verify the fix
on the phones after rebuilding Android; the existing iOS/server deployment does
not require a change for this failure. Look for `Audio focus granted` followed
by the remaining media startup steps, then confirm bidirectional audio/video.
