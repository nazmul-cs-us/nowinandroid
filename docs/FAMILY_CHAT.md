# Family chat and inbox

Android and iOS support one-to-one text conversations between **accepted Family members**. The server derives the sender from authenticated XMPP; it checks the current relationship on every send, history request, and read cursor update. Pending invitations never grant chat access. Membership is invite-and-accept; Deenly does not independently verify mahram relationships.

Tap an accepted contact row to open chat. Avatar shortcuts still open contact details with voice/video actions. Rows show a short message preview, local timestamp, and unread count, ordered by the latest conversation. The conversation has earlier-message pagination, selectable text, a multiline composer, and voice/video shortcuts. Sending clears the draft only after server acknowledgement; retries of the same failed draft reuse the message UUID. An acknowledgement means the server stored the message, not that another person read it.

The Family bell opens an inbox for **invitations, unread messages, and missed calls**. Family navigation also shows an unread indicator. Opening the inbox acknowledges invitations and missed-call alerts; message counts clear only when the conversation is visible in the foreground. A ringing call becomes missed when its caller cancels or its ring timeout expires, not when the recipient declines or after an answered call. Repeated events do not multiply the same missed call.

These are **in-app alerts and badges**, synchronized immediately over the signed-in XMPP connection and reconciled after reconnect. This change does not add remote message/invitation pushes or make a closed iOS app wake without APNs. Existing call push and the optional paired Home Screen helper remain separate: see [background calling](BACKGROUND_CALLING.md) and [temporary call alerts](FAMILY_WEB_ALERTS.md).

## Storage and limits

- Messages and UUID references: disk-backed Mnesia `deenly_family_messages`, with a conversation index and ordered sequence keys. History returns ten messages per page, with `nextBefore` as an exclusive cursor (zero means no further page).
- Conversation summaries, read cursors, notification records and monotonically increasing inbox versions: disk-backed `deenly_family_inbox`, indexed by account. Old inbox replies cannot restore already-cleared unread counts.
- Messages accept valid UTF-8 text, up to 4,096 bytes, including Arabic, emoji, and line breaks. Empty/blank text and unsupported control characters are rejected.
- A sender can send ten messages per second and 300 per minute. Duplicate retries return the existing message without using the limit again or increasing unread counts.
- The installation caps message-table rows at 200,000 (two rows per message). It returns `chat_storage_full` instead of silently dropping history. Monitor storage and provide an operational retention policy before a wider rollout.
- Notifications coalesce by kind and peer. The inbox returns the latest 50 records; the badge includes unread counts across retained records. Message previews contain at most 30 Unicode characters to keep XMPP updates bounded.
- Removing a family relationship deletes its shared chat and associated alerts for both people; the confirmation explains this. Reaccepting an invitation starts a fresh conversation. Account removal also revokes its relationships and clears its chat data, preventing a recreated username from inheriting history.

Transport uses verified STARTTLS. Messages are stored on the server; this is **not end-to-end encryption**. Back up both Mnesia disk tables consistently alongside the existing Family table and PostgreSQL. No chat content, credentials, SDP, or candidate addresses are added to diagnostic logs.

## Protocol

All actions use the existing authenticated `urn:starception:family:1` IQ envelope. Raw peer-authored XMPP messages remain blocked.

| Action | Fields | Result |
| --- | --- | --- |
| `send_message` | `peer`, lowercase UUID `id`, `text` | Durable `message`; server-domain `chat` event to participants |
| `chat_history` | `peer`, optional integer `before` | Ascending `messages`, exclusive `nextBefore` |
| `read_messages` | `peer`, integer `through` sequence | Updated own inbox; old read cursors never increase unread counts |
| `ack_notifications` | — | Acknowledges non-message notices; does not mark conversation messages read |
| `snapshot` | existing fields | Also returns `conversations`, `notifications`, `unreadCount`, `inboxVersion` |

Server-domain `chat` and `inbox` events carry the same versioned inbox state. A `chat` event also includes the full new message. Durable tables provide reconnect recovery; transient headline events carry no-store/no-copy hints.

## Deploy this update

Run on the existing VM (MongooseIM restarts and signed-in clients reconnect):

```bash
cd "$HOME/deenly-family-setup" &&
git pull --ff-only origin main &&
sudo bash server/family/install.sh "$HOME/mongooseim-6.10.0" 10.148.0.2 &&
sudo install -m 0644 server/family/smoke.py /tmp/deenly-family-smoke.py &&
sudo -u mongooseim python3 /tmp/deenly-family-smoke.py --host 10.148.0.2
```

The installer compiles and installs `mod_deenly_family_chat.beam`; MongooseIM creates the indexed disk tables on startup. No extra firewall ports, credentials, or TOML module options are needed. The smoke test uses temporary accounts and verifies real TLS logins, accepted-family-only live chat, durable history, duplicate protection and unread cursors alongside presence and calling. Its execution on the deployed VM remains a rollout check; local tests do not establish deployment.

Pull and rebuild both apps afterward. Android: `./gradlew installDemoDebug`. On the Mac, regenerate after pulling to include the new Swift model and asset:

```bash
cd iosApp && xcodegen generate --spec project.yml && open iosApp.xcodeproj
```

Keep the existing local signing configuration. Do not disable extension signing. Run a signed iPhone build and test Android→iOS and iOS→Android chat/calls, both app foregrounds, a reconnect, pagination, invitation acceptance, missed-call badges and family removal.

## Related app changes

The signed-out page now uses original illustrated family homes beneath an arch and an olive branch, with mahram-family copy and the selected app theme. It does not use stock contact photos or imply verified eligibility.

Family restores the last successful login when the app opens/foregrounds, using the existing Android Keystore or iOS Keychain credential. A manual sign-out clears the credential; a rejected password suppresses repeated automatic attempts until manual sign-in. Debug pilot fields are not an automatic login source.

Active calls show **Direct (P2P)**, **TURN relay**, or **Checking connection…** using the actual selected ICE pair. Either endpoint being a relay classifies the path as TURN. Available/gathered TURN candidates alone do not. Stats refresh every five seconds and clear during disconnection. Direct means media avoids TURN forwarding; signalling/STUN and other backend traffic can still use server resources. This is a route indicator, not a billing meter.

Video calls use a large remote view, a small local preview and floating controls. Calls remain one-to-one; the group-call thumbnails in the reference are not implemented as fictitious participants.

For You is temporarily hidden in Android, shared navigation, and native iOS More. `FamilyRollout.showForYou()` is the single switch for restoring it. Existing destination IDs and implementation remain intact.
