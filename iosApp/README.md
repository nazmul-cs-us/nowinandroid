# iOS Push Configuration

No APNs provider key, certificate, or backend credential belongs in this app.
The app only registers with APNs, stores token snapshots in `UserDefaults` under
`ios_prayer_push_token_snapshot`, and optionally sends the snapshot to an HTTPS
endpoint.

## Local and simulator builds

Generate and build without signing:

```sh
cd iosApp
xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -arch arm64 CODE_SIGNING_ALLOWED=NO build
```

## Device configuration

Pass signing and backend values externally, either on the command line or in an
uncommitted `Config/Local.xcconfig`:

```xcconfig
DEVELOPMENT_TEAM = YOUR_TEAM_ID
APNS_ENVIRONMENT = development
SLASH = /
PRAYER_PUSH_BACKEND_URL = https:$(SLASH)$(SLASH)push.example.com/v1/apple-tokens
```

Use `APNS_ENVIRONMENT = production` with the production provisioning setup.
The Push Notifications capability, Live Activities capability, matching app and
extension identifiers, and provisioning profiles must exist in the Apple
Developer account. The backend separately requires an APNs provider key or
certificate and must route ordinary APNs, Live Activity update/end tokens, and
iOS 17.2 push-to-start tokens using Apple's required topics and payloads.

When `PRAYER_PUSH_BACKEND_URL` is a valid HTTPS URL, the app posts JSON containing
`bundleIdentifier`, `apnsDeviceToken`, `liveActivityTokens`, `pushToStartToken`,
and `updatedAt`. The endpoint should treat token replacement as an upsert. The
request intentionally has no embedded authorization header; add server-side
abuse controls or an app-attestation exchange if the endpoint is public.
