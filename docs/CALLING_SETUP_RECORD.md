# Calling setup: completed commands and remaining work

Updated **5 November 2026 (Asia/Dubai)**. This records commands and results supplied by the VM owner during setup, plus repository build checks. It is not a live inspection of the VM. Do not reinstall working services or reload database schemas just to follow this record.

## Status and documentation index

| Area | Confirmed status | Commands/details |
| --- | --- | --- |
| coturn | Running; UDP/TCP sample relay tests passed | [Detailed installation/runbook](COTURN_SETUP_RUNBOOK.md) |
| TURN/TLS | Certificate/hostname verified; relay-only browser data test passed 200/200/200 | Sections below and coturn runbook |
| MongooseIM | 6.10.0 built, deployed and running as a dedicated system user | Installed inventory and maintenance commands below |
| PostgreSQL | Local database/role/schema configured; authentication verified | Inventory/check commands below |
| Family policy | Installed; real XMPP policy smoke test passed | Module deployment commands below |
| Public XMPP access | Firewall rule created; TLS 1.3/hostname verification passed | Cloud Shell commands below |
| Android/iOS apps | User confirmed an iOS build and actual cross-platform call | Build commands and evidence below |
| New UI/native background code | Committed in `048dd850e`; Android build and 35 tests passed | [Family implementation](FAMILY_CALLING.md) |
| Background push delivery | **Not yet deployed or device-verified** | [Firebase/VM/APNs instructions](BACKGROUND_CALLING.md) |
| iOS VoIP push prerequisites | Firebase available; paid Apple/APNs setup **not yet available** | Optional iOS activation in background guide |

The foreground call confirmation predates the new screens/native background integration. Rebuild and retest that revision; do not transfer the earlier result to untested background behavior.

## Installed VM inventory

| Item | Recorded value |
| --- | --- |
| Compute project | `webrtc-512816` |
| VM / zone | `webrtc` / `asia-southeast1-a` |
| OS | Debian GNU/Linux 13.7 (trixie) |
| Machine | e2-medium, approximately 3.8 GiB usable RAM, 10 GB boot disk |
| Public / private IP | `34.143.204.245` / `10.148.0.2` |
| DNS | `xmpp.starception.com`, `turn.starception.com` |
| Firebase project used by Android | `dua-45e8ba51` (different from the Compute project) |
| Certificate lineage | `/etc/letsencrypt/live/webrtc-starception/` |
| Certificate names | Both XMPP and TURN hostnames; certificate reported valid through 30 January 2027 |
| Source checkout | `/home/nazmul086/mongooseim-6.10.0` |
| MongooseIM source commit | `872cfdb8bc8512aa495a10ff54e52a3c5e09b5db` |
| Installed release / user | `/opt/mongooseim-6.10.0` / `mongooseim` |
| Config / certificates | `/etc/mongooseim/mongooseim.toml` / `/etc/mongooseim/certs/` |
| Persistent state / logs | `/var/lib/mongooseim/` / `/var/log/mongooseim/` |
| SQL | PostgreSQL 17.11; local role/database `mongooseim` |
| Module checkout on VM | `/home/nazmul086/deenly-family-setup` |
| Active XMPP listener | `10.148.0.2:5222`, mandatory STARTTLS |
| TURN listeners / relay range | UDP/TCP 3478, TCP/TLS 5349; UDP 49160–49259 |

MongooseIM uses SQL authentication with SCRAM password storage, a three-worker local SQL pool, Mnesia internal storage, and SASL SCRAM-SHA-256/PLAIN over mandatory TLS. Management, PostgreSQL and Erlang distribution remain private. The release's log/var/Mnesia directories point into the service-owned persistent directories. The service uses a private Erlang cookie and loopback distribution; do not print the VM argument/config files into support logs.

Some original terminal pastes were truncated. This repository does **not** contain a complete sanitized PostgreSQL/base-MongooseIM provisioning script or the private live configuration. The Family installer updates an existing prepared release; it cannot bootstrap a blank VM. Restore/provision that base from protected backups and the pinned release documentation before using the update commands below. Passwords, TURN secrets and private keys are deliberately absent from this record.

## VM: inspect the completed base setup

Run on the **VM**, not Cloud Shell:

```bash
cat /etc/os-release | head -6
free -h
df -h /
sudo systemctl is-active coturn mongooseim postgresql
sudo ss -lntup
sudo systemctl list-timers --all --no-pager | grep -i certbot
sudo certbot certificates
git -C "$HOME/mongooseim-6.10.0" rev-parse HEAD
sudo -u mongooseim /usr/local/bin/mongooseimctl status
```

For reference, the pinned source's documented release target is `make rel`; it assembles `_build/prod/rel/mongooseim`. The owner supplied the successful release-assembly output. Do not rebuild or copy over the running `/opt` release during an ordinary health check.

Inspect SQL ownership without revealing credentials:

```bash
sudo -u postgres psql -d mongooseim -c \
  "SELECT tablename, tableowner FROM pg_tables WHERE schemaname='public' AND tablename IN ('users','rosterusers');"
```

Both recorded tables were owned by `mongooseim`. The setup also verified a database login as `mongooseim`. Do not rerun `priv/pg.sql` against this live database; it was loaded during initial preparation under the application role.

### Working management wrapper

The initial direct CLI invocation failed because its working directory/root environment were wrong. The installed wrapper fixed this. The exact wrapper setup was:

```bash
sudo tee /usr/local/bin/mongooseimctl >/dev/null <<'EOF'
#!/bin/sh
cd /opt/mongooseim-6.10.0 || exit 1
exec env \
  ROOTDIR=/opt/mongooseim-6.10.0 \
  ERL_EPMD_ADDRESS=127.0.0.1 \
  ERL_FLAGS="+S 2:2" \
  ERL_CRASH_DUMP=/var/log/mongooseim/ctl-crash.dump \
  ./bin/mongooseimctl "$@"
EOF
sudo chmod 0755 /usr/local/bin/mongooseimctl
sudo -u mongooseim /usr/local/bin/mongooseimctl status
```

Use the wrapper from any directory. Reported status: Erlang VM/boot script started, version 6.10.0. The systemd unit uses `User=mongooseim`, `WorkingDirectory=/opt/mongooseim-6.10.0`, `EJABBERD_CONFIG_PATH=/etc/mongooseim/mongooseim.toml`, loopback EPMD, foreground execution, restart-on-failure, and filesystem/user protections. Inspect it with `sudo systemctl cat mongooseim`; keep its protected configuration in backups.

## VM: install/update and verify the Family module

The owner originally cloned the repository with:

```bash
git clone --branch main --single-branch \
  https://github.com/nazmul-cs-us/nowinandroid.git \
  "$HOME/deenly-family-setup"
```

For subsequent updates, use the existing checkout:

```bash
cd "$HOME/deenly-family-setup"
git pull origin main
sudo bash server/family/install.sh "$HOME/mongooseim-6.10.0" 10.148.0.2
sudo ss -lntp '( sport = :5222 )'
sudo install -m 0644 server/family/smoke.py /tmp/deenly-family-smoke.py
sudo -u mongooseim python3 /tmp/deenly-family-smoke.py
```

The installer restarts MongooseIM and interrupts XMPP sessions. Its default IP is loopback; explicitly retain `10.148.0.2` when updating this deployed server. It backs up configuration and compiles the Family modules against the pinned source. The earlier `behaviour gen_mod undefined` compiler warning did not prevent loading or the successful live smoke test.

Confirmed live smoke results: three verified-TLS logins; invitation-recipient-only acceptance; non-family calls denied; accepted-family calls and participant-scoped TURN credentials allowed; another installation cannot take over an answered call; authorized signalling delivered; family removal ends access. Temporary accounts were removed. The native test accounts `android@xmpp.starception.com` and `ios@xmpp.starception.com` were created separately and preserved.

## Cloud Shell: completed phone firewall and external TLS check

The owner found the pilot Wi-Fi's public IPv4 on a **computer on that Wi-Fi**:

```bash
curl -4 https://api.ipify.org
```

Recorded result: `94.206.87.170`. This is historical egress, not a permanent phone address; check it again if the network changes.

The following ran in **Cloud Shell**:

```bash
gcloud compute instances add-tags webrtc \
  --project=webrtc-512816 --zone=asia-southeast1-a --tags=webrtc

FAMILY_SHELL_IP="$(curl -4 -fsS https://api.ipify.org)"
gcloud compute firewall-rules create webrtc-family-xmpp \
  --project=webrtc-512816 --direction=INGRESS --action=ALLOW \
  --rules=tcp:5222 --target-tags=webrtc \
  --source-ranges="94.206.87.170/32,${FAMILY_SHELL_IP}/32"
```

The tag already existed; the firewall rule was created, enabled, on the default network. Do not recreate an existing rule just to check it:

```bash
gcloud compute firewall-rules describe webrtc-family-xmpp \
  --project=webrtc-512816 \
  --format='yaml(network,disabled,allowed,sourceRanges,targetTags)'
```

Then the owner ran:

```bash
timeout 15 openssl s_client \
  -starttls xmpp -xmpphost xmpp.starception.com \
  -connect xmpp.starception.com:5222 \
  -servername xmpp.starception.com \
  -verify_hostname xmpp.starception.com \
  -verify_return_error -brief </dev/null
```

Confirmed output: `CONNECTION ESTABLISHED`, TLS 1.3, `Verification: OK`, and `Verified peername: xmpp.starception.com`. This validates the Cloud Shell path; phone-network reachability was subsequently supported by the successful device call.

## TURN/TLS verification evidence

Cloud Shell certificate/listener check:

```bash
timeout 15 openssl s_client -tls1_2 \
  -connect turn.starception.com:5349 \
  -servername turn.starception.com \
  -verify_hostname turn.starception.com \
  -verify_return_error -brief </dev/null
```

Confirmed: TLS 1.2 and successful verification of `turn.starception.com`. The certificate's common name was XMPP, but its SAN covered TURN; explicit hostname verification passed.

The owner then used [the browser relay test](../tools/turn-relay-test.html) with temporary credentials generated locally, relay-only ICE, and `turns:turn.starception.com:5349?transport=tcp`:

```text
Sent=200; received=200; echoed back=200
Peer 1: candidate=relay, relay transport=tls, address=34.143.204.245
Peer 2: candidate=relay, relay transport=tls, address=34.143.204.245
PASS: bidirectional data delivered through relay-only WebRTC.
```

This resolved whether reliable TLS-relayed data could be delivered. It did not explain the old utility's reported loss, measure raw packet loss, or prove that the later native call selected TURN/TLS.

## Local computer: app build/install and completed device test

Android, from the repository root with USB debugging enabled:

```bash
git pull origin main
./gradlew installDemoDebug
```

iOS, on the owner's Mac:

```bash
cd /Users/smarterai/Downloads/nowinandroid
git pull origin main
cd iosApp
xcodegen generate --spec project.yml
xcodebuild -resolvePackageDependencies -project iosApp.xcodeproj -scheme iosApp
open iosApp.xcodeproj
```

Choose the same development Team for `iosApp`, `PrayerLiveActivityExtension` and `PrayerWidgetExtension`, enable automatic signing, select the iPhone, and run. To preserve signing across regeneration, set `DEVELOPMENT_TEAM = YOUR_TEAM_ID` in the ignored `iosApp/Config/Local.xcconfig`.

For a logged device build, the command used was:

```bash
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'generic/platform=iOS' -derivedDataPath .build/family-device \
  -allowProvisioningUpdates build 2>&1 | tee /tmp/deenly-ios-build.log
```

Observed and corrected: stale generated project omitted new Swift files; missing development teams; unresolved Swift packages; CocoaLumberjack/XMPP deployment-target conflict (pinned logging package to 3.8.5); iOS XML lookup API mismatch (fixed in `227aad41c`). The owner supplied `BUILD SUCCEEDED` and then explicitly confirmed that the cross-platform call worked.

The device workflow was Family sign-in on Android/iOS, invite, accept, then call with both apps open on the allowed Wi-Fi. The report confirms a call worked; it did not independently enumerate every mute/camera/headset scenario or establish a selected relay protocol.

## Still pending: do not mark these complete

- Deploy the new Family push module/bridge and authorize its VM identity for Firebase project `dua-45e8ba51`; full commands are in [Background calling](BACKGROUND_CALLING.md).
- Obtain paid Apple Developer/APNs credentials and enable the optional `project-push.yml` configuration for iPhone wake delivery.
- Rebuild both apps and test the new UI, native call integration and foreground regression, then locked/background/OS-terminated delivery.
- Confirm static external-IP reservation, certificate-renewal dry run/deployment for both daemons, native forced-relay media quality, monitoring and consistent backup/restore.

Repository verification for `048dd850e`: Android demo-debug build, 8 shared tests, 20 Erlang policy/outbox tests and 7 push-bridge tests passed; Swift syntax checked on Linux. The new native iOS integration still needs Xcode compilation/device acceptance. These checks do not substitute for live provider setup or production readiness.


### Video confirmed; Family chat update prepared

The user subsequently confirmed the rebuilt video call worked after the Jiffy iodata fix. Local code verification is separate from this reported real-device result.

The next app/server update adds accepted-family text chat and an in-app notification inbox, secure saved-login restoration, selected-ICE route labels, revised landing/video layouts and a temporary For You switch. Full deployment commands and operational limits are in [Family chat and inbox](FAMILY_CHAT.md). It has not been remotely deployed by the coding environment; run its VM installation and smoke check before testing the rebuilt apps.
