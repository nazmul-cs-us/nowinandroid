#!/usr/bin/env bash
set -euo pipefail
[ "$(id -u)" -eq 0 ] || { echo 'Run with sudo.' >&2; exit 1; }
root=$(cd "$(dirname "$0")" && pwd)
install -d -o root -g mongooseim -m 0750 /opt/deenly-family-push
python3 -m venv /opt/deenly-family-push/venv
/opt/deenly-family-push/venv/bin/pip install -r "$root/requirements.txt"
install -o root -g mongooseim -m 0640 "$root/bridge.py" /opt/deenly-family-push/bridge.py
if [ ! -f /etc/mongooseim/push-bridge-secret ]; then
  python3 - <<'PY'
import grp, os, secrets
path = '/etc/mongooseim/push-bridge-secret'
fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o640)
with os.fdopen(fd, 'w') as out: out.write(secrets.token_urlsafe(48) + '\n')
os.chown(path, 0, grp.getgrnam('mongooseim').gr_gid)
PY
fi
if [ ! -f /etc/mongooseim/family-push.env ]; then
  install -o root -g mongooseim -m 0640 /dev/null /etc/mongooseim/family-push.env
  cat > /etc/mongooseim/family-push.env <<'EOF'
# Firebase project that owns the Android app registration.
FAMILY_FCM_PROJECT=
# Prefer the VM service account with Firebase Cloud Messaging API permissions.
# For a separate service account file, set GOOGLE_APPLICATION_CREDENTIALS here.
# Enable these only after obtaining a paid Apple Developer account/APNs key.
# FAMILY_APNS_TEAM_ID=
# FAMILY_APNS_KEY_ID=
# FAMILY_APNS_KEY_FILE=/etc/mongooseim/apns-key.p8
# FAMILY_APNS_BUNDLE_ID=com.starception.submission
EOF
fi
install -m 0644 "$root/deenly-family-push.service" /etc/systemd/system/
systemctl daemon-reload
echo 'Configure /etc/mongooseim/family-push.env, then: sudo systemctl enable --now deenly-family-push'
