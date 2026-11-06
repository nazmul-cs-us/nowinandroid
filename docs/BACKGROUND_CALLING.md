# Background Family calls

For the no-paid-Apple-account options and their limits, see [iPhone incoming-call R&D](IOS_INCOMING_CALL_RND.md).

Status as of 5 November 2026: code committed; provider/VM deployment and closed-app acceptance tests are pending. For the already completed TURN/XMPP/foreground-call setup, see [Calling setup record](CALLING_SETUP_RECORD.md).

The foreground Android-to-iOS call was confirmed on real devices before this change. The new native UI and background paths require a fresh signed-device test. A local build or provider mock test does not prove delivery to a locked/terminated phone.

For temporary visible alerts before native provider setup, see
[Home Screen call alerts](FAMILY_WEB_ALERTS.md). This optional route opens the app
for server validation; it does not provide automatic native iOS wake-up.

## Delivery flow

1. After authenticated XMPP login, each installation registers its FCM or VoIP APNs token. Installation IDs and XMPP resources survive process restarts. Sign-out clears saved credentials and unregisters the device.
2. Only an accepted family invitation can create a ringing call. In the same Mnesia transaction, the server creates one durable outbox item per registered callee installation. Repeated `invite_call` requests do not create duplicate jobs.
3. The server sweeps every five seconds, claims jobs, verifies that the call is still ringing and the registration is current, and sends to a loopback-only provider bridge. Network requests happen outside Mnesia locks. Retries have bounded backoff and stop at the 45-second ringing deadline. Provider-invalid tokens are removed; registrations expire after 30 days unless refreshed.
4. Android receives a high-priority, data-only FCM message and starts a call foreground service. It reconnects over verified STARTTLS using Keystore-protected credentials, fetches the authoritative call, and only then rings/shows Answer and Decline. Telecom tracks incoming/outgoing calls; active calls use microphone/camera foreground service types after user permission and acceptance. The dedicated incoming activity requests permissions before accepting.
5. iOS reports a VoIP push immediately to CallKit, then reconnects and verifies the current call, account, and expiry. Invalid, cancelled, answered-elsewhere, or expired invitations are ended. CallKit owns audio activation. Answer still requires authenticated server acceptance; the first selected installation wins. Camera capture is disabled while backgrounded; voice can continue.

The outbox is at-least-once; native call UUIDs deduplicate repeats. No push contains TURN secrets, user passwords, SDP, or media. Push payloads carry call ID, caller/callee account IDs, kind and expiry. Notifications use private lock-screen visibility on Android. CallKit displays the caller as required by iOS. This private server does not provide general user messaging.

## Firebase and VM configuration

The committed Android Firebase registration belongs to **`dua-45e8ba51`**. The TURN/XMPP VM belongs to **`webrtc-512816`**. Grant the VM's service account permission to send for the Firebase project; do not use the VM project ID as the FCM destination project.

In Cloud Shell, with an account allowed to manage the Firebase project:

```bash
gcloud services enable fcm.googleapis.com --project=dua-45e8ba51

FAMILY_VM_ACCOUNT="$(gcloud compute instances describe webrtc \
  --project=webrtc-512816 --zone=asia-southeast1-a \
  --format='value(serviceAccounts[0].email)')"

gcloud projects add-iam-policy-binding dua-45e8ba51 \
  --member="serviceAccount:${FAMILY_VM_ACCOUNT}" \
  --role=roles/firebasecloudmessaging.admin
```

Also inspect the VM service-account scopes:

```bash
gcloud compute instances describe webrtc \
  --project=webrtc-512816 --zone=asia-southeast1-a \
  --format='value(serviceAccounts[0].scopes)'
```

The attached VM identity needs the `https://www.googleapis.com/auth/cloud-platform` scope as well as IAM permission. Limited scopes can cause provider HTTP 403 even with the role. Changing VM identity/scopes can require stopping the VM; reserve/verify its static public IP and schedule that operation separately. Alternatively use a dedicated provider credential file installed locally with restricted permissions and set `GOOGLE_APPLICATION_CREDENTIALS` in the service environment. Never put that JSON/key in this repository or chat.

## Deploy on the existing VM

This restarts MongooseIM and interrupts existing sessions. Preserve the private listener IP:

```bash
cd "$HOME/deenly-family-setup"
git pull origin main
sudo bash server/family/install.sh "$HOME/mongooseim-6.10.0" 10.148.0.2

sudo apt-get install -y python3-venv
sudo bash server/family/push/install.sh
sudoedit /etc/mongooseim/family-push.env
```

Set this non-secret environment entry:

```ini
FAMILY_FCM_PROJECT=dua-45e8ba51
```

Then:

```bash
sudo systemctl enable --now deenly-family-push
sudo systemctl status deenly-family-push --no-pager
sudo ss -lntp '( sport = :8789 )'
sudo install -m 0644 server/family/smoke.py /tmp/deenly-family-smoke.py
sudo -u mongooseim python3 /tmp/deenly-family-smoke.py
```

Port 8789 must remain bound to `127.0.0.1`; do not create a public firewall rule. The installer generates a root/service-readable bridge secret, separate from coturn. Provider keys and tokens are not printed. Provider failures are retried until expiry; systemd/service health alone does not verify FCM credentials. Rebuild and sign in to the apps after deploying the new server module so device registration succeeds.

## Android build and device setup

```bash
git pull origin main
./gradlew installDemoDebug
```

Sign in through Family, grant microphone/camera permissions and notification permission. Android 14+ may require allowing full-screen notifications under the app's **Full-screen notifications/Full-screen intents** special access setting; if denied, an incoming-call notification still provides Answer/Decline. OEM battery restrictions and FCM priority downgrades can affect wake delivery. A high-priority message is required for background foreground-service startup.

Keep the phone on a network allowed to reach XMPP TCP 5222. The previous pilot firewall allows only the configured Wi-Fi public IP and Cloud Shell egress, so switching to mobile data needs the corresponding network policy update. TURN ports and relay ranges must also remain reachable.

## iOS activation (paid Apple account required)

The normal `project.yml` keeps remote VoIP delivery disabled and preserves personal-team builds. With `FAMILY_BACKGROUND_CALLS=NO`, foreground calls use the in-app screen and activate/release their own audio session; they do not depend on CallKit transactions. Setting it to `YES` enables CallKit/PushKit and CallKit audio ownership. A rejected system-call report falls back to the authenticated in-app call instead of sending a hang-up to the server. The existing app identifiers and all extensions must be signed by the same team.

Once a paid Apple Developer account and APNs key are available:

1. Enable Push Notifications for the app ID, create/download an APNs `.p8` key locally, and update provisioning profiles.
2. On the VM, install the key in a root/service-readable protected file and set `FAMILY_APNS_TEAM_ID`, `FAMILY_APNS_KEY_ID`, `FAMILY_APNS_KEY_FILE` and `FAMILY_APNS_BUNDLE_ID=com.starception.submission` in `/etc/mongooseim/family-push.env`. Restart only the push bridge after updating its environment.
3. In the uncommitted `iosApp/Config/Local.xcconfig`, set `DEVELOPMENT_TEAM` to the paid team and `APNS_ENVIRONMENT = development` for development provisioning or `production` for distribution. This value must match the provisioning profile's `aps-environment` entitlement.
4. Generate the opt-in project and build:

```bash
cd iosApp
xcodegen generate --spec project-push.yml
xcodebuild -resolvePackageDependencies -project iosApp.xcodeproj -scheme iosApp
open iosApp.xcodeproj
```

The overlay enables `voip` background mode, APNs entitlement, and PushKit registration. Provider routing uses the matching sandbox/production environment and `<bundle-id>.voip` topic. Sign in after rebuilding so the token is registered. To return to the non-push project, run `xcodegen generate --spec project.yml`.

After a restart, unlock the iPhone once before expecting access to the saved Keychain login. iOS may suppress delivery in some force-quit, network, power, or policy states; test the specific supported device/OS behavior. Never send VoIP pushes for chat, cancellation-only updates, or non-call work.

## Acceptance matrix

Test Android first with the existing iOS foreground caller. iPhone terminated/background delivery stays blocked until paid Apple/APNs configuration is completed.

- Incoming voice/video while foregrounded, backgrounded, locked and process-killed by the OS; distinguish these from Android Settings **Force stop**.
- Answer/decline from notification and native call UI; no microphone/camera starts before user acceptance and permission.
- Cancel before delivery, expiry while offline, duplicate pushes, other-device answer and membership removal; no stale call may connect.
- Both directions of audio, speaker/headset route, mute, video toggle/switching, background voice continuity and camera-off behavior.
- Sign-out/account switch, token rotation, device reinstall, provider outage, worker restart and server restart. Confirm durable jobs do not deliver expired calls.
- Reopen the apps after Android force stop; confirm successful re-registration and no old invitations.
- Run the existing authenticated server smoke test and native TURN relay-only media tests after deployment.

Monitoring, call-rate limits, provider failure metrics, credential rotation and production identity bootstrap remain deployment hardening tasks. Background-call delivery has not been verified from this cloud workspace.
