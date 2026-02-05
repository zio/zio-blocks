#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v golem-cli >/dev/null 2>&1; then
  echo "[gettingStarted/run.sh] error: golem-cli not found on PATH" >&2
  exit 1
fi

GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
read -r -a flags <<<"$GOLEM_CLI_FLAGS"

echo "[gettingStarted/run.sh] 1) Build Scala.js (ensures base guest runtime)"
( sbt -batch -no-colors -Dsbt.supershell=false "golemEnsureAgentGuestWasm" "compile" "fastLinkJS" )

echo "[gettingStarted/run.sh] 2) Deploy app"
env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$PWD/golem.yaml" deploy

echo "[gettingStarted/run.sh] 3) Invoke via repl"
env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$PWD/golem.yaml" \
  repl scala:demo --script-file repl-counter.rib --disable-stream < /dev/null

