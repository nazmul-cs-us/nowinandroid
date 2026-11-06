#!/usr/bin/env bash
# Read-only Mac diagnostic: compare the exact signing certificate, not just Team.
set -euo pipefail
app=${1:?Usage: bash iosApp/tools/check-embedded-signing.sh /path/to/Deenly.app}
[ -d "$app" ] || { echo 'App bundle not found.' >&2; exit 2; }
command -v codesign >/dev/null || { echo 'Run this on the Mac with Xcode.' >&2; exit 2; }
task_dir=$(mktemp -d)
trap 'rm -rf "$task_dir"' EXIT
certificate() {
  rm -f "$task_dir"/cert*
  codesign -d --extract-certificates "$task_dir/cert" "$1" >/dev/null 2>&1
  [ -f "$task_dir/cert0" ] || return 1
  shasum -a 256 "$task_dir/cert0" | awk '{print $1}'
}
parent=$(certificate "$app") || { echo 'Parent app has no development/distribution certificate.' >&2; exit 1; }
failed=0
while IFS= read -r -d '' binary; do
  current=$(certificate "$binary") || current=unsigned
  if [ "$current" != "$parent" ]; then
    echo "MISMATCH: $binary"
    codesign -dv --verbose=4 "$binary" 2>&1 | awk '/^(Authority|TeamIdentifier)=/'
    failed=1
  fi
done < <(find "$app" -type d \( -name '*.appex' -o -name '*.framework' \) -print0)
if [ "$failed" -eq 0 ]; then echo 'All embedded extensions/frameworks use the parent certificate.'; fi
exit "$failed"
