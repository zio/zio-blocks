#!/usr/bin/env bash
set -euo pipefail

if [[ "${RUN_WEBSEARCH_EXAMPLES:-}" != "1" ]]; then
  echo "[websearch-summary-local-repl] SKIP: set RUN_WEBSEARCH_EXAMPLES=1 to run (requires golem:web-search + golem:llm support)."
  exit 0
fi

cd "$(dirname "$0")/../.."

if ! command -v golem-cli >/dev/null 2>&1; then
  echo "[websearch-summary-local-repl] error: golem-cli not found on PATH" >&2
  exit 1
fi

GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
read -r -a flags <<<"$GOLEM_CLI_FLAGS"

is_cloud=0
for f in "${flags[@]}"; do
  [[ "$f" == "--cloud" ]] && is_cloud=1
done

if [[ "$is_cloud" -eq 0 ]]; then
  host="${GOLEM_ROUTER_HOST:-127.0.0.1}"
  port="${GOLEM_ROUTER_PORT:-9881}"
  if ! timeout 1 bash -lc "cat < /dev/null > /dev/tcp/$host/$port" 2>/dev/null; then
    echo "[websearch-summary-local-repl] Local router not reachable at $host:$port." >&2
    echo "[websearch-summary-local-repl] Start it in another terminal, then rerun:" >&2
    echo "  golem server run --clean --data-dir .golem-local --router-port $port" >&2
    exit 1
  fi
fi

app_dir="$PWD/golem/examples/app"
script_file="$PWD/golem/examples/repl-websearch-summary.rib"

# Build Scala.js up-front (no golem-cli needed). This also runs `golemPrepare` automatically,
# ensuring the base guest runtime wasm is present next to the app manifest.
( cd "$PWD" && sbt -batch -no-colors -Dsbt.supershell=false "zioGolemExamplesJS/fastLinkJS" >/dev/null )

out="$(
  cd "$app_dir"
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" deploy
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" \
    repl scala:examples --script-file "$script_file" --disable-stream < /dev/null 2>&1
)"

if echo "$out" | grep -F -q 'CustomError(' || \
   echo "$out" | grep -F -q 'JavaScript error:' || \
   echo "$out" | grep -F -q 'Exception during call' || \
   echo "$out" | grep -F -q '[ERROR'; then
  echo "[websearch-summary-local-repl] ERROR: repl output contains an error:" >&2
  echo "$out" >&2
  exit 1
fi

echo "$out"
echo "$out" | grep -F -q 'Finished research for topic'

