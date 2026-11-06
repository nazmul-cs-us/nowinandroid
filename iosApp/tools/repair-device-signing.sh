#!/usr/bin/env bash
# Share the existing app's development team with its embedded extensions.
# Run on the Mac with Xcode closed; no certificate/key/profile is modified.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
for tool in xcodebuild xcodegen python3; do
  command -v "$tool" >/dev/null || { echo "Required Mac tool not found: $tool" >&2; exit 1; }
done
[ "$#" -le 1 ] || { echo 'Usage: bash tools/repair-device-signing.sh [APPLE_TEAM_ID]' >&2; exit 1; }
task_dir=$(mktemp -d)
trap 'rm -rf "$task_dir"' EXIT
team=${1:-}
if [ -z "$team" ]; then
  [ -d "$root/iosApp.xcodeproj" ] || {
    echo 'No generated project to read. Pass your actual Apple development Team ID as the argument.' >&2; exit 1;
  }
  if ! xcodebuild -showBuildSettings -project "$root/iosApp.xcodeproj" \
      -target iosApp -configuration Debug -sdk iphoneos -json \
      >"$task_dir/settings.json" 2>"$task_dir/settings.log"; then
    echo 'Could not read the app signing team. Pass your actual Apple development Team ID as the argument.' >&2
    exit 1
  fi
  team=$(python3 - "$task_dir/settings.json" <<'PY'
import json, sys
try:
    rows = json.load(open(sys.argv[1]))
    team = next(row['buildSettings'].get('DEVELOPMENT_TEAM', '')
                for row in rows if row.get('target') == 'iosApp')
    print(team.strip())
except (ValueError, KeyError, StopIteration, TypeError):
    raise SystemExit('Could not identify the app signing team. Pass your Apple development Team ID explicitly.')
PY
  )
fi
python3 - "$root/Config/Local.xcconfig" "$team" <<'PY'
import os, pathlib, re, sys, tempfile
path, team = pathlib.Path(sys.argv[1]), sys.argv[2]
if not re.fullmatch(r'[A-Z0-9]{10}', team):
    raise SystemExit('A ten-character Apple development Team ID is required. Do not use the certificate name or infer it from its suffix.')
start, end = '// BEGIN DEENLY DEBUG DEVICE SIGNING', '// END DEENLY DEBUG DEVICE SIGNING'
text = path.read_text() if path.exists() else ''
if text.count(start) != text.count(end) or text.count(start) > 1:
    raise SystemExit('The local signing block needs manual repair; no settings were changed.')
text = re.sub(re.escape(start) + r'\n.*?' + re.escape(end) + r'\n?', '', text, flags=re.S)
qualifier = '[config=Debug][sdk=iphoneos*]'
settings = {'DEVELOPMENT_TEAM': team, 'CODE_SIGN_STYLE': 'Automatic',
            'CODE_SIGN_IDENTITY': 'Apple Development', 'CODE_SIGNING_ALLOWED': 'YES',
            'CODE_SIGNING_REQUIRED': 'YES', 'PROVISIONING_PROFILE': '',
            'PROVISIONING_PROFILE_SPECIFIER': ''}
block = '\n'.join([start, *(f'{key}{qualifier} = {value}' for key, value in settings.items()), end, ''])
text = text.rstrip('\n') + '\n\n' + block
path.parent.mkdir(parents=True, exist_ok=True)
fd, tmp = tempfile.mkstemp(dir=path.parent)
try:
    with os.fdopen(fd, 'w') as out:
        out.write(text)
    os.replace(tmp, path)
finally:
    if os.path.exists(tmp):
        os.unlink(tmp)
print('Shared Debug device signing configured for the app and both extensions; other local settings preserved.')
PY
cd "$root"
xcodegen generate --spec project.yml
if ! xcodebuild -showBuildSettings -project "$root/iosApp.xcodeproj" \
    -alltargets -configuration Debug -sdk iphoneos -json \
    >"$task_dir/resolved.json" 2>"$task_dir/resolved.log"; then
  echo 'Project generated, but Xcode could not resolve signing settings. No signing verification succeeded.' >&2
  exit 1
fi
python3 - "$task_dir/resolved.json" "$team" <<'PY'
import json, sys
try:
    rows = json.load(open(sys.argv[1]))
    settings = {row['target']: row['buildSettings'] for row in rows}
except (ValueError, KeyError, TypeError):
    raise SystemExit('Xcode returned unreadable build settings; signing verification did not pass.')
failed = False
for target in ('iosApp', 'PrayerLiveActivityExtension', 'PrayerWidgetExtension'):
    value = settings.get(target)
    if value is None:
        print(f'FAIL: {target}: missing from resolved Xcode build settings.')
        failed = True
        continue
    identity = value.get('CODE_SIGN_IDENTITY', '')
    development = identity == 'Apple Development' or identity.startswith('Apple Development:')
    same_team = value.get('DEVELOPMENT_TEAM') == sys.argv[2]
    allowed = value.get('CODE_SIGNING_ALLOWED') == 'YES'
    required = value.get('CODE_SIGNING_REQUIRED') == 'YES'
    automatic = value.get('CODE_SIGN_STYLE') == 'Automatic'
    passed = development and same_team and allowed and required and automatic
    label = 'Apple Development' if development else 'ad hoc' if identity == '-' else 'unexpected/missing'
    print(f'{"PASS" if passed else "FAIL"}: {target}: identity={label}; '
          f'team matches={same_team}; signing allowed={allowed}; required={required}; automatic={automatic}.')
    failed |= not passed
if failed:
    raise SystemExit('Resolved signing settings still differ. Share only the PASS/FAIL lines above for diagnosis.')
PY
echo 'Project regenerated and resolved Debug signing settings verified for all three targets.'
echo 'Reopen iosApp.xcodeproj, select your iPhone, then Product > Clean Build Folder and Run.'
echo 'A device build on this Mac is still needed to verify signing and provisioning.'
