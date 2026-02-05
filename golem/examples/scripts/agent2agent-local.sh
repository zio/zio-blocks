#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../../.."

echo "[agent2agent-local] 1) Build Scala.js"
( sbt -batch -no-colors -Dsbt.supershell=false "++3.3.7!" "zioGolemExamples/fastLinkJS" )

GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
read -r -a flags <<<"$GOLEM_CLI_FLAGS"

app_dir="$PWD/golem/examples"
script_file="$app_dir/samples/agent-to-agent/repl-minimal-agent-to-agent.rib"
run_id="run-$(date +%s)"
tmp_script="$(mktemp)"
trap 'rm -f "$tmp_script"' EXIT

sed -e "s/demo2/$run_id/g" -e "s/demo/$run_id/g" "$script_file" > "$tmp_script"

echo "[agent2agent-local] 2) Deploy app"
env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" deploy
echo "[agent2agent-local] 3) Invoke via repl"
env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" \
  repl scala:examples --script-file "$tmp_script" --disable-stream < /dev/null
