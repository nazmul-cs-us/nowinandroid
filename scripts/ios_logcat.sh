#!/usr/bin/env bash
# Streams the iOS app's own log output, the way `adb logcat -s TAG` does.
#
# The simulator's log is overwhelmingly system chatter — a plain stream is about
# 6,000 lines to every 1 of ours — so this keeps only lines emitted by the app
# binary itself, which is what SharedLog writes through NSLog.
#
# Usage:
#   scripts/ios_logcat.sh                 # everything the app logs
#   scripts/ios_logcat.sh AstronomicalCalculator LocationProvider
set -euo pipefail

DEVICE="${IOS_DEVICE:-E72AC4E1-E4F1-4CEF-931E-DC28D863BFBD}"

# UTF-8 must be forced or the emoji the app logs arrive mangled.
export LC_ALL=en_US.UTF-8
export LANG=en_US.UTF-8

if [ "$#" -gt 0 ]; then
  # Tags are matched as `I/Tag:` etc., mirroring adb logcat -s.
  filter=$(printf '%s\n' "$@" | paste -sd'|' -)
else
  filter='.'
fi

xcrun simctl spawn "$DEVICE" log stream --level debug \
  --predicate 'processImagePath CONTAINS "iosApp" AND senderImagePath CONTAINS "iosApp"' 2>/dev/null |
  # Only lines the app binary emitted; the rest is Previews and CFBundle noise.
  grep --line-buffered -F '(iosApp.debug.dylib)' |
  # Drop the process/thread preamble so the tag starts the line.
  sed -u 's/.*(iosApp\.debug\.dylib) //' |
  grep --line-buffered -E "$filter"
