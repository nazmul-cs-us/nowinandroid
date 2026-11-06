# Temporary Family call alerts

Status: implementation and local checks are available; deployment and real
Android/iPhone Web Push delivery still require device acceptance testing.

This optional Home Screen web app receives a visible browser notification for an
authorized family call. It uses standard VAPID Web Push and needs no paid Apple
Developer account, native APNs key, or Firebase service-account credential. iPhone
requires iOS 16.4+, Safari Add to Home Screen, and notification permission granted
from the installed helper. Android Chrome also supports the helper.

An alert opens a helper page with **Open Deenly**. Tapping that link opens the
native app, signs in with its existing locally saved credentials, and fetches the
authoritative call over verified XMPP/TLS. Only a matching incoming, still-ringing
call is shown. Nothing auto-answers, captures audio, or bypasses family policy.
Expired/removed/answered calls cannot be accepted. Browser/OS delivery is best
effort; this is not native PushKit/CallKit wake-up. The existing local iOS Live
Activity does not run a continuous network fetch while the app is suspended.

## Deploy on the existing VM

The VM already has the `xmpp.starception.com` certificate and MongooseIM service
account. This helper serves HTTPS on **8443**, preserving port 80/certbot and the
existing XMPP/coturn listeners. Its TLS context reloads changed certificate files
on subsequent connections. Keep the existing certificate deployment hook active.

```bash
cd "$HOME/deenly-family-setup"
git pull --ff-only origin main
sudo bash server/family/install.sh "$HOME/mongooseim-6.10.0" 10.148.0.2
sudo bash server/family/push/web-deploy/install.sh https://xmpp.starception.com:8443
```

The second installer installs the existing private push bridge and the public
helper, generates a VAPID key once, and enables `deenly-family-push` and
`deenly-family-web`. Existing FCM/APNs settings are preserved; empty native
provider settings do not prevent the Web Push route. These restarts interrupt
current calling/server sessions, so deploy while no test call is active.

In **Google Cloud Shell**, allow the phones' current Wi-Fi public IPv4 and Cloud
Shell egress address (replace the placeholder):

```bash
FAMILY_WIFI_IP="YOUR_WIFI_PUBLIC_IPV4"
FAMILY_SHELL_IP="$(curl -4 -fsS https://api.ipify.org)"
gcloud compute firewall-rules create webrtc-family-web \
  --project=webrtc-512816 --direction=INGRESS --action=ALLOW \
  --rules=tcp:8443 --target-tags=webrtc \
  --source-ranges="${FAMILY_WIFI_IP}/32,${FAMILY_SHELL_IP}/32"
curl --fail --show-error https://xmpp.starception.com:8443/family-helper/api/key
```

If the firewall rule already exists, inspect/update it rather than creating a
second rule. For testing on cellular, explicitly add that phone's applicable
source range or review whether to expose this HTTPS helper more broadly. Public
pairing endpoints require an unexpired signed code and enforce origin, body-size,
rate, replay and subscription-count limits. Provider callback traffic does not
need an ingress rule: Web Push delivery is outbound HTTPS from the bridge.

The key response contains only the **public** VAPID key. Do not paste private keys,
pairing codes, browser subscription endpoints, or the bridge secret into chat.
Do not expose the private bridge port 8789.

## Set up each phone

1. Pull/rebuild the latest native app. On iPhone, resolve signing first using
   [the iOS signing instructions](../iosApp/README.md#embedded-binary-certificate-mismatch-on-iphone).
2. Sign in through Family. In its menu choose **Temporary call alerts**.
3. Copy the five-minute pairing code and open the helper link.
4. On iPhone, use Safari Share → Add to Home Screen, then open the new **Call
   Alerts** icon. Return to Deenly to copy/create a fresh code if necessary.
5. Paste the code into the installed helper and tap **Enable call alerts**.
6. Allow notifications. Alerts expire after 24 hours; re-pair to extend testing.
7. Background/close Deenly and call that account from an accepted family member.
8. Open the alert, tap **Open Deenly**, and answer before the 45-second ringing
   deadline. If the call ended, open Family to call back.

The native account password never goes into the helper. Browser pairing is
single-use and tied to an authenticated account and native installation. Signing
out while connected revokes subsequent dispatches for that installation; an alert already sent to
a provider may still arrive. The helper's **Disable call alerts** button revokes
its subscription with a separate random revocation secret. If the native phone
was offline during sign-out, use this helper button when connected or wait for the
24-hour expiry. Server-side cleanup
removes expired helper records. Notification text contains no caller/account name,
SDP, TURN credentials, or account password.

## Checks and remaining acceptance work

Server tests cover pairing scope, logout generations, idempotent outbox, family
revocation and existing native push policy. Python checks cover signature/expiry,
nonce replay, endpoint allowlisting, cross-account ownership, revocation, bounded
subscriptions, origin/path restrictions, minimal payload and verified local HTTPS.
The service-worker check requires visible notifications even for stale/malformed
deliveries, and confirms clicks open the same-origin helper page. Native URL
parsing accepts only a canonical call ID and cannot forward an auto-answer flag.

Xcode signing, actual Safari installation/permission behavior, provider delivery,
locked-phone timing, and native open/answer on both phones remain device tests.
A local build or a started systemd service does not establish those results.
