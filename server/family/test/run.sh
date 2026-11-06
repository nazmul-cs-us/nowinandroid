#!/usr/bin/env bash
set -euo pipefail
: "${MONGOOSE_SOURCE:?Set MONGOOSE_SOURCE to the pinned MongooseIM source checkout}"
: "${ERL_LIBS:?Set ERL_LIBS to the directory containing the exml and jid libraries from the release}"
root=$(cd "$(dirname "$0")/.." && pwd)
build=$(mktemp -d)
trap 'rm -rf "$build"' EXIT
# Exercise registration option lookup against the real pinned configuration API.
erlc -I "$MONGOOSE_SOURCE/include" -o "$build" "$MONGOOSE_SOURCE/src/gen_mod.erl" "$MONGOOSE_SOURCE/src/config/mongoose_config.erl"
erlc -DTEST -I "$MONGOOSE_SOURCE/include" -o "$build" "$root/mod_deenly_family"*.erl
# Source-only SDK fixtures may not yet have this pure XML query module compiled.
exml_dir=$(erl +S 2:2 -noshell -eval 'io:format("~s", [code:lib_dir(exml)]), halt().')
if [ -f "$exml_dir/src/exml_query.erl" ]; then
  erlc -I "$exml_dir/include" -o "$build" "$exml_dir/src/exml_query.erl"
fi
erlc -I "$MONGOOSE_SOURCE/include" -o "$build" "$root/test/"*.erl
erl +S 2:2 -noshell -pa "$build" -eval 'case eunit:test([deenly_family_tests, deenly_signup_tests], [verbose]) of ok -> halt(0); _ -> halt(1) end.'
