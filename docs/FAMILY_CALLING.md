# Family calling

This change implements foreground, one-to-one voice/video calls on Android and iOS. It includes Family pages, invitation acceptance/removal, native WebRTC capture/rendering, and a MongooseIM authorization module. This is a private versioned XMPP protocol, not Jingle interoperability. Deployment and real-device acceptance are separate from code/build validation.

## App workflow

Android has a **Family** navigation destination. iOS 26+ has **More → Family**; earlier supported versions have a Family button opening the page.

For debug device testing, Android prefills `android@xmpp.starception.com` and iOS prefills `ios@xmpp.starception.com`. Enter the passwords created on the VM; neither platform stores or embeds them. After sign-in, the opposite test account is suggested in the invitation field. Release builds keep both fields blank. Both apps use the shared `FamilyEndpoint` settings for `xmpp.starception.com:5222`, require STARTTLS and server certificate verification, and allow a short username (`android`/`ios`) or a full account ID. The Family service readiness message appears only after its authenticated snapshot succeeds.

Sign in using a provisioned Family/XMPP account, such as the two pilot accounts requested for device testing. Passwords are entered locally and retained only for the connection; they are not bundled or saved in preferences. Send an invitation to another full Family account ID. Only its recipient can accept. Voice/Video controls appear for accepted members. Either party can remove a relationship, ending calling access.

Incoming calls appear over other screens while signed in with the app open. Capture starts only after acceptance and permissions. Mute, speaker/earpiece, camera on/off, camera switching, decline/cancel and hang-up are supported. Other app audio yields; playback is not automatically resumed afterward.

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

Relationships and authoritative calls live in the Mnesia **disk** table `deenly_family`. Eligibility and transitions are transactional and serialized by domain. Back up Mnesia consistently along with PostgreSQL; copying live files is not a consistent backup.

Ringing expires after 45 seconds. Clients reconcile every 20 seconds. Active installations have 75-second leases and a 55-minute maximum call duration, within one-hour TURN credentials. Ended calls are retained for one day, then pruned. Native clients reject terminal/stale call versions. Each user has at most 100 relationships, including invitations.

Events are accepted only from the authenticated server domain. Direct user-to-user message/IQ routing is rejected on this private calling server, ensuring call signalling goes through policy-controlled IQs. This is not a general chat configuration.

Removing membership stops further authorized signalling and credential issuance. Normal clients release media on terminal events/reconciliation. A server cannot forcibly terminate a direct media connection between two modified peers that both ignore termination.

The issuer reads `/etc/mongooseim/turn-secret`, never sends that static secret to phones, and returns temporary HMAC credentials. Keep coturn's existing quotas. Rotate its shared secret and the protected issuer file together.

## Deploy on the VM

Use a checkout containing this change, or transfer `server/family`. Workspace changes are not automatically on GitHub.

```bash
# From that checkout on the VM; validate locally first.
sudo bash server/family/install.sh "$HOME/mongooseim-6.10.0"
sudo install -m 0644 server/family/smoke.py /tmp/deenly-family-smoke.py
sudo -u mongooseim python3 /tmp/deenly-family-smoke.py
```

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

This is a **foreground** implementation. Backgrounding ends a call; closed/force-stopped apps do not ring. FCM/PushKit, CallKit/Android Telecom integration, and a durable push outbox remain separate work requiring platform/provider configuration. Production Firebase bootstrap is also pending.

Before production, validate backups/restore, monitoring, abuse/load limits, certificate renewal deployment to MongooseIM, restrictive-network TURN/TLS on 443, and the signed-device acceptance matrix. A successful build is not a production-readiness claim.
