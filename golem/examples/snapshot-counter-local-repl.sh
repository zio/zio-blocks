#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."

if ! command -v golem-cli >/dev/null 2>&1; then
  echo "[snapshot-counter-local-repl] error: golem-cli not found on PATH" >&2
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
    echo "[snapshot-counter-local-repl] Local router not reachable at $host:$port." >&2
    echo "[snapshot-counter-local-repl] Start it in another terminal, then rerun:" >&2
    echo "  golem server run --clean --data-dir .golem-local --router-port $port" >&2
    exit 1
  fi
fi

app_dir="$PWD/golem/examples"
script_file="$PWD/golem/examples/samples/snapshot-counter/repl-snapshot-counter.rib"

# Build Scala.js up-front (no golem-cli needed). This also runs `golemPrepare` automatically,
# ensuring the base guest runtime wasm is present next to the app manifest.
build_log="$(mktemp)"
trap 'rm -f "$build_log"' EXIT
if ! ( cd "$PWD" && sbt -batch -no-colors -Dsbt.supershell=false "zioGolemExamplesJS/fastLinkJS" ) >"$build_log" 2>&1; then
  cat "$build_log" >&2
  echo "[snapshot-counter-local-repl] sbt failed; see output above." >&2
  exit 1
fi

agent_id="demo-$(date +%s)-$RANDOM"
tmp_script="$(mktemp)"
trap 'rm -f "$tmp_script"' EXIT
sed -e "s/\"demo\"/\"$agent_id\"/g" "$script_file" > "$tmp_script"

out="$(
  cd "$app_dir"
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" deploy
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" \
    repl scala:examples --script-file "$tmp_script" --disable-stream < /dev/null 2>&1
)"

if echo "$out" | grep -F -q 'CustomError(' || \
   echo "$out" | grep -F -q 'JavaScript error:' || \
   echo "$out" | grep -F -q 'Exception during call' || \
   echo "$out" | grep -F -q '[ERROR'; then
  echo "[snapshot-counter-local-repl] ERROR: repl output contains an error:" >&2
  echo "$out" >&2
  exit 1
fi

echo "$out"
echo "$out" | grep -F -q 'a: 1'
echo "$out" | grep -F -q 'b: 2'

