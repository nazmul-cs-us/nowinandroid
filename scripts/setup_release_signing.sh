#!/usr/bin/env bash
# ============================================================================
# Release signing setup — generates the release keystore and wires it up.
#
# Run once per machine (or once ever, then back up the keystore!):
#
#   ./scripts/setup_release_signing.sh
#
# What it does:
#   1. Generates app/release-keystore.jks (RSA 2048, 25-year validity)
#   2. Writes app/keystore.properties (read by app/build.gradle.kts at build
#      time; both files are gitignored)
#   3. Prints the base64 blob to store in the GitHub secret KEYSTORE_BASE64
#
# Required GitHub secrets for the release workflow (.github/workflows/Release.yml):
#   KEYSTORE_BASE64    base64 of app/release-keystore.jks (printed by this script)
#   KEYSTORE_PASSWORD  the store password
#   KEY_ALIAS          the key alias
#   KEY_PASSWORD       the key password
#
# WARNING: losing this keystore means you can never update the app on Play
# under the same listing. Back it up somewhere safe (password manager, offline
# storage). Keystore passwords live in the properties file — do NOT commit it.
# ============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="$REPO_ROOT/app/release-keystore.jks"
PROPERTIES="$REPO_ROOT/app/keystore.properties"

if [[ -f "$KEYSTORE" ]]; then
    echo "Keystore already exists: $KEYSTORE"
    echo "Delete it first if you really want to regenerate (updates signed with the"
    echo "old key can no longer be installed over the new one)."
    exit 0
fi

read -rp "Keystore store+key password (used for both): " -s PASSWORD
echo
if [[ -z "$PASSWORD" ]]; then
    echo "Password cannot be empty." >&2
    exit 1
fi
read -rp "Key alias [starception-release]: " ALIAS
ALIAS="${ALIAS:-starception-release}"

echo "Generating keystore..."
keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 9125 \
    -storepass "$PASSWORD" -keypass "$PASSWORD" \
    -dname "CN=Starception Submission, OU=Mobile, O=Starception, C=US"

cat > "$PROPERTIES" <<EOF
storeFile=release-keystore.jks
storePassword=$PASSWORD
keyAlias=$ALIAS
keyPassword=$PASSWORD
EOF
chmod 600 "$KEYSTORE" "$PROPERTIES"

echo
echo "Done. Files created (both gitignored):"
echo "  $KEYSTORE"
echo "  $PROPERTIES"
echo
echo "=== Add this value to the GitHub secret KEYSTORE_BASE64 ==="
base64 -i "$KEYSTORE" | pbcopy
echo "(copied to your clipboard — $(base64 -i "$KEYSTORE" | wc -c | tr -d ' ') chars)"
echo
echo "Also add these GitHub secrets:"
echo "  KEYSTORE_PASSWORD = <the password you entered>"
echo "  KEY_ALIAS          = $ALIAS"
echo "  KEY_PASSWORD       = <the password you entered>"
