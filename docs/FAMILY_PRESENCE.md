# Family presence

Accepted Family contacts show **Online**, **Offline**, or **Status unavailable**
on Android and iOS. A green dot accompanies Online. Calling stays available for
accepted members regardless of presence: background incoming-call push is separate
from foreground availability. Invitations never expose availability. No last-seen
timestamps are collected or displayed.

Both clients publish foreground/background changes over their authenticated Family
XMPP connection. The server derives the account and resource from that connection;
a client cannot publish presence for another person. Each connection has a unique
resource and a monotonically increasing activity version. Delayed foreground
heartbeats cannot override a newer background transition. Multiple devices combine:
one active device keeps the account online.

MongooseIM keeps presence and an accepted-family subscription index in an indexed
RAM-only Mnesia table. Status lookup is keyed by account; an update reads only that
account's devices and accepted recipients. The server emits an event only when the
aggregate status changes. Headline events carry no-store/no-copy hints. Raw
peer-to-peer presence/probes are blocked by the private Family module.

The existing 20-second snapshot heartbeat renews a 75-second device lease, without
adding a separate presence polling loop. Normal foreground/background updates and
clean disconnects emit immediately. A broken network/process kill can leave a ghost
online status until the lease expires and the existing five-second server sweep
runs (approximately 80 seconds). This fallback bound is not a live-event latency
guarantee. Snapshots recover missed updates after reconnection. Memory caches use
server epochs and per-account revisions to reject older snapshots/events. Cached
presence is not displayed when the local service connection is unavailable.

Family acceptance/removal updates the subscription index in the same transaction
as membership. A server restart drops availability and reconstructs subscriptions
from durable family relationships; fresh app snapshots restore active devices.

## Deploy on the existing VM

Deploy the server before rebuilding both apps. From the existing repository clone:

```bash
cd "$HOME/deenly-family-setup"
git pull --ff-only origin main
sudo bash server/family/install.sh "$HOME/mongooseim-6.10.0" 10.148.0.2
sudo install -m 0644 server/family/smoke.py /tmp/deenly-family-smoke.py
sudo -u mongooseim python3 /tmp/deenly-family-smoke.py
```

The smoke script reads the configured C2s listener from
`/etc/mongooseim/mongooseim.toml`, so private-interface deployments do not connect
to an unbound loopback socket. To override it explicitly, use:

```bash
sudo -u mongooseim python3 /tmp/deenly-family-smoke.py --host 10.148.0.2 --port 5222
```

The network destination does not change certificate verification: TLS still
verifies `xmpp.starception.com`.

The installer now installs `mod_deenly_family_presence.beam` alongside the existing
modules and restarts MongooseIM. The smoke script uses temporary accounts and checks
live availability events, background transitions and stale activity rejection,
alongside the existing family/call policy tests. It removes temporary accounts.
Existing apps keep calling; their peers' presence can remain unknown until both
apps are updated. No new firewall ports, provider credentials or coturn changes
are required.

## Verify on phones

1. Update Android and iOS and sign in to two accepted family accounts.
2. Keep both apps active: each contact should show Online.
3. Background one app: its contact should become Offline on the other device.
4. Reopen it: Online should return from a live event.
5. Try two devices on one account: backgrounding one must preserve Online while
   the other remains active.
6. Disconnect the observing phone: stale Online must become Status unavailable.
7. Remove the family relationship: subsequent presence must not appear.

Cloud checks cover server privacy/aggregation/expiry, shared cache ordering and
Android UI states. Xcode compilation and two-device timing require a Mac/phones;
they are not established by Swift syntax checks or server unit tests.
