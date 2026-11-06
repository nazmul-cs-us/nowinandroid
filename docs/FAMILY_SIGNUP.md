# Family account signup

Android and iOS Family landing pages offer **New to Family? Create an account**.
A user chooses a local username and confirms a password. Successful registration
signs the user in automatically. Android then reconnects through the existing
login flow; iOS authenticates the same TLS stream. Saved credentials are written
only after successful authentication using the existing Keystore/Keychain stores.
If registration succeeds but authentication fails, the account still exists: sign
in with the chosen credentials instead of creating it again.

This creates an account on `xmpp.starception.com`, using the existing PostgreSQL
SCRAM authentication backend. It does not create a Firebase identity, verify an
email address, recover forgotten passwords, or automatically add family members.
Users must invite each other and the invitation recipient must accept before
calling. Existing account credentials and relationships are unaffected by the
new signup validation rules.

## Rules and protocol

- Username: normalized lowercase, 3–32 ASCII letters/numbers/dots/underscores/
  hyphens; first character must be a letter or number. The administrative names
  `admin`, `administrator`, `root`, `support`, `postmaster` and the existing pilot
  names `android`, `ios` cannot be claimed through signup.
- Password: 12–128 UTF-16 code units, nonblank, with no C0 control characters or
  DEL; confirmation must match exactly. Passwords are not trimmed or lowercased.
  Shared Kotlin policy and server validation use the same bounds.
- Registration uses create-only XEP-0077 `jabber:iq:register` on the existing
  TCP 5222 connection. Both clients require verified STARTTLS before sending
  credentials. The server independently rejects registration before TLS or
  outside the pre-authentication state.
- The server uses `ejabberd_auth:try_register`, never password replacement. A
  duplicate name is rejected; the existing account password remains unchanged.
  Registration queries cannot remove accounts or change passwords. The built-in
  general-purpose `mod_register` must remain disabled; the installer rejects a
  conflicting configuration before writing changes.
- Five signup attempts per source IP per minute and 100 attempts per domain per
  minute are allowed. Atomic counters are ephemeral and bounded; old windows are
  removed. Invalid credentials consume the same signup budget once submitted.
- Responses contain no submitted password/query data. Clients log exception
  classes, not registration XML or credential values. The server endpoint does
  not log credentials. The existing server's overall logging settings still
  need to avoid verbose raw-stanza logging.

`[modules.mod_deenly_family]` accepts `signup_enabled = false` to disable signup.
The module defaults to enabled after deploying this version. A disabled/older
server yields a signup-unavailable message instead of a fake local account.

## Deploy and test

On the existing VM, between calls:

```bash
cd "$HOME/deenly-family-setup"
git pull --ff-only origin main
sudo bash server/family/install.sh "$HOME/mongooseim-6.10.0" 10.148.0.2
sudo install -m 0644 server/family/smoke.py /tmp/smoke.py
sudo install -m 0644 server/family/signup_smoke.py /tmp/deenly-signup-smoke.py
sudo -u mongooseim python3 /tmp/deenly-signup-smoke.py --host 10.148.0.2
```

This installs `mod_deenly_family_signup.beam` alongside the existing Family
modules and restarts MongooseIM. It adds no network listener and needs no Firebase,
APNs, or web-helper credential. The signup smoke uses real TLS/XMPP registration,
accepts either an IQ denial or a stream rejection/closure for the pre-TLS test,
independently confirms that the plaintext account does not exist, then checks
duplicate/reserved/weak-password/rate denials, authenticates new accounts,
and verifies that family invitation acceptance is still required. It attempts to
remove its temporary relationships/accounts afterward and prints no passwords.
If the VM's source IP has just exhausted its signup allowance, wait for the next
minute before running it. It intentionally uses the same rate limits as a phone.

Rebuild Android/iOS from the updated checkout after deploying the server. On each
phone, open Family, switch to Create an account, and use a different new username.
Check matching/mismatched confirmation, a taken name, the successful automatic
sign-in, and invitation/acceptance before testing a call.

## Mobile data / public client reachability

The original pilot firewall allowed XMPP only from the test Wi-Fi public IP.
A phone on cellular data has a different public address. Account signup and sign-in
need reachable TCP 5222; the temporary alert helper also needs HTTPS TCP 8443 for
pairing/opening its page. A browser push reaching the phone is not proof that
these application endpoints are reachable.

For this application's mobile-client access, the user was supplied this dedicated
GCP rule (run in Cloud Shell):

```bash
gcloud compute firewall-rules create webrtc-family-client-access \
  --project=webrtc-512816 --network=default --direction=INGRESS \
  --action=ALLOW --rules=tcp:5222,tcp:8443 --target-tags=webrtc \
  --source-ranges=0.0.0.0/0
```

This intentionally makes those two client ports reachable from public IPv4
networks. Existing TLS, account authentication, signup limits and family policy
remain required. It does not enable a management listener. If the rule already
exists, inspect its targets/rules rather than deleting an existing rule. Apply
this change only to the intended `webrtc` VM tag on the `default` network.

Cloud Shell creation of this rule was confirmed in this chat on 6 November 2026.
Successful cellular login has not yet been confirmed. If it still fails, inspect the app's sanitized
`FamilyConnection` log to distinguish connect, TLS and authentication failures;
a carrier blocking TCP 5222 would need a separate transport investigation.

## Verification boundaries

Cloud checks cover shared validation, the real Android signup form, screenshots,
and server policy/duplicate/rate/response privacy tests using a test auth backend.
Server sources compile against the pinned MongooseIM 6.10.0 interfaces. These
checks do not establish a successful live PostgreSQL registration. Run the VM
signup smoke above for that evidence. Native Xcode compilation, iPhone signup,
and actual mobile network/firewall behavior require the Mac/phones/VM.

The first VM rollout on 6 November confirmed MongooseIM startup but stopped at the
plaintext negative test because it required an IQ error and instead received a
stream error. The corrected smoke accepts a rejected/closed stream only when a
backend `checkUser` query confirms no account was created; timeouts still fail.
Seven socket/XML regression tests cover this distinction and avoid printing raw
server error text. Copy **both** updated `smoke.py` and `signup_smoke.py` to `/tmp`
before retrying. Verified-TLS signup is still a required subsequent live step.
