#!/usr/bin/env bash
set -euo pipefail

RUN_WEBSEARCH_EXAMPLES="${RUN_WEBSEARCH_EXAMPLES:-1}"
if [[ "${RUN_WEBSEARCH_EXAMPLES}" != "1" ]]; then
  echo "[websearch-summary-local-repl] SKIP: set RUN_WEBSEARCH_EXAMPLES=1 to run (requires golem:web-search + golem:llm support)."
  exit 0
fi

cd "$(dirname "$0")/../../.."

echo "[websearch-summary-local-repl] 1) Build Scala.js"
( sbt -batch -no-colors -Dsbt.supershell=false "++3.3.7!" "zioGolemExamples/fastLinkJS" )

GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
read -r -a flags <<<"$GOLEM_CLI_FLAGS"

app_dir="$PWD/golem/examples"
script_file="$app_dir/samples/websearch-summary/repl-websearch-summary.rib"
run_id="run-$(date +%s)"
tmp_script="$(mktemp)"
trap 'rm -f "$tmp_script"' EXIT

sed -e "s/demo2/$run_id/g" -e "s/demo/$run_id/g" "$script_file" > "$tmp_script"

echo "[websearch-summary-local-repl] 2) Deploy app"
( cd "$app_dir" && env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" deploy )
echo "[websearch-summary-local-repl] 3) Invoke via repl"
( cd "$app_dir" && env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" \
  repl scala:examples --script-file "$tmp_script" --disable-stream < /dev/null )
