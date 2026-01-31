#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../../.."

echo "[json-tasks-local-repl] 1) Build Scala.js"
( sbt -batch -no-colors -Dsbt.supershell=false "zioGolemExamplesJS/fastLinkJS" )

GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
read -r -a flags <<<"$GOLEM_CLI_FLAGS"

app_dir="$PWD/golem/examples"
script_file="$app_dir/samples/json-tasks/repl-json-tasks.rib"

echo "[json-tasks-local-repl] 2) Deploy app"
( cd "$app_dir" && env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" deploy )
echo "[json-tasks-local-repl] 3) Invoke via repl"
( cd "$app_dir" && env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" \
  repl scala:examples --script-file "$script_file" --disable-stream < /dev/null )
