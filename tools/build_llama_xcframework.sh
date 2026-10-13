#!/usr/bin/env bash
#
# Builds the llama.xcframework used by the Deenly on-device suggestion runner.
#
# Pinned to llama.cpp v0.6.0 — the same release line as Android's
# libdeenly_completion.so — so both platforms run the same GGUFs with the
# same grammar-guided generation behaviour.
#
# Usage:
#   tools/build_llama_xcframework.sh            # clone + build + install
#   LLVM_BUILD_DIR=/some/dir tools/build_llama_xcframework.sh   # reuse a checkout
set -euo pipefail

LLAMA_VERSION="v0.6.0"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_ROOT="${LLAMA_BUILD_DIR:-$(mktemp -d)}"
DEST_DIR="$REPO_ROOT/iosApp/Frameworks"

mkdir -p "$BUILD_ROOT"
cd "$BUILD_ROOT"

if [ ! -d llama.cpp ]; then
    git clone --depth 1 --branch "$LLAMA_VERSION" https://github.com/ggml-org/llama.cpp
else
    echo "Reusing existing checkout at $BUILD_ROOT/llama.cpp"
fi

cd llama.cpp
ACTUAL="$(git describe --tags --exact-match 2>/dev/null || echo unknown)"
if [ "$ACTUAL" != "$LLAMA_VERSION" ]; then
    echo "Warning: checkout is $ACTUAL, expected $LLAMA_VERSION" >&2
fi

./build-xcframework.sh ios-sim ios-device

mkdir -p "$DEST_DIR"
rm -rf "$DEST_DIR/llama.xcframework"
cp -R build-apple/llama.xcframework "$DEST_DIR/"
echo "Installed $DEST_DIR/llama.xcframework"
