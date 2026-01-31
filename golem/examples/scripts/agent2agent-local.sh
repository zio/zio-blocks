#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../../.."

echo "[agent2agent-local] 1) Build Scala.js"
( sbt -batch -no-colors -Dsbt.supershell=false "zioGolemExamplesJS/fastLinkJS" )

GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
read -r -a flags <<<"$GOLEM_CLI_FLAGS"

app_dir="$PWD/golem/examples"
script_file="$app_dir/samples/agent-to-agent/repl-minimal-agent-to-agent.rib"

echo "[agent2agent-local] 2) Deploy app"
env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" deploy
echo "[agent2agent-local] 3) Invoke via repl"
env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" \
  repl scala:examples --script-file "$script_file" --disable-stream < /dev/null
