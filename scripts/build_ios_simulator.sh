#!/usr/bin/env bash
# Regenerate the Xcode project and retain complete diagnostics for a simulator build.
set -euo pipefail

if [[ "${1:-}" == "--help" ]]; then
    cat <<'HELP'
Usage: scripts/build_ios_simulator.sh

Requires macOS, Xcode, XcodeGen, JDK 21, and the project's build dependencies.
Regenerates iosApp.xcodeproj, then builds the iosApp Debug scheme for an arm64
simulator without signing. Keeps SwiftUI preview support enabled as configured.
Build products: work/ios-simulator-derived-data
Full build log: work/ios-simulator-build.log
HELP
    exit 0
fi
if [[ $# -ne 0 ]]; then
    echo "Usage: scripts/build_ios_simulator.sh [--help]" >&2
    exit 2
fi
for tool in xcodegen xcodebuild; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "Missing $tool. Install Xcode and XcodeGen on your Mac before running this script." >&2
        exit 1
    fi
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$repo_root/work"
cd "$repo_root/iosApp"
# Regeneration removes old/manual resource entries, including the obsolete
# AppIcon-1024.png reference in the Live Activity extension.
xcodegen generate --spec project.yml

build_log="$repo_root/work/ios-simulator-build.log"
if xcodebuild -project iosApp.xcodeproj -scheme iosApp \
    -configuration Debug -sdk iphonesimulator \
    -destination 'generic/platform=iOS Simulator' -arch arm64 \
    -derivedDataPath "$repo_root/work/ios-simulator-derived-data" \
    CODE_SIGNING_ALLOWED=NO build 2>&1 | tee "$build_log"; then
    echo "Simulator build succeeded. Full log: $build_log"
else
    echo "Simulator build failed. Full log: $build_log" >&2
    echo "Share the ld: diagnostic and surrounding lines, not only 'Command Ld failed'." >&2
    exit 1
fi
