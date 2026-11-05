#!/usr/bin/env bash
set -euo pipefail
: "${MONGOOSE_SOURCE:?Set MONGOOSE_SOURCE to the pinned MongooseIM source checkout}"
: "${ERL_LIBS:?Set ERL_LIBS to the directory containing the exml and jid libraries from the release}"
root=$(cd "$(dirname "$0")/.." && pwd)
build=$(mktemp -d)
trap 'rm -rf "$build"' EXIT
erlc -DTEST -I "$MONGOOSE_SOURCE/include" -o "$build" "$root/mod_deenly_family.erl"
erlc -o "$build" "$root/test/"*.erl
erl +S 2:2 -noshell -pa "$build" -eval 'case eunit:test(deenly_family_tests, [verbose]) of ok -> halt(0); _ -> halt(1) end.'
